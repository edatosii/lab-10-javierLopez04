package co.umariana.dao;

import co.umariana.conexion.ConexionBD;
import co.umariana.modelo.ArbolGenealogico;
import co.umariana.modelo.Persona;
import co.umariana.modelo.ResumenArbol;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO (Data Access Object): la única clase que habla con la base de datos. Guarda los árboles genealógicos
 * en las tablas "arboles" y "miembros" (ver db/schema.sql). Se construye igual que EstudianteDAO del laboratorio 9.
 */
public class ArbolDAO {

    // =====================================================================
    // ETAPA 4.1. Guardar un árbol nuevo (INSERT en dos tablas, con transacción)
    // =====================================================================

    /**
     * Guarda un árbol nuevo: una fila en "arboles" (con el nombre) y una fila en "miembros" por cada persona.
     *
     * @return el id que la base de datos le dio al árbol; -1 si el nombre es nulo, está en blanco o tiene más
     *         de 100 caracteres, si el árbol es null, o si hubo un error (en ese caso NO queda nada guardado)
     */
    public long guardar(String nombre, ArbolGenealogico arbol) {
        if (nombre == null || nombre.trim().isEmpty() || nombre.length() > 100 || arbol == null) {
            return -1;
        }

        String sqlArbol = "INSERT INTO arboles (nombre) VALUES (?)";
        String sqlMiembro = "INSERT INTO miembros (arbol_id, posicion, nombre, nacimiento, fallecimiento) VALUES (?, ?, ?, ?, ?)";

        try (Connection con = ConexionBD.conectar()) {
            if (con == null) {
                return -1;
            }

            con.setAutoCommit(false); // Iniciar transacción

            long arbolId = -1;
            try (PreparedStatement psArbol = con.prepareStatement(sqlArbol, Statement.RETURN_GENERATED_KEYS)) {
                psArbol.setString(1, nombre);
                int filasAfectadas = psArbol.executeUpdate();
                if (filasAfectadas == 0) {
                    con.rollback();
                    return -1;
                }

                try (ResultSet rsKeys = psArbol.getGeneratedKeys()) {
                    if (rsKeys.next()) {
                        arbolId = rsKeys.getLong(1);
                    } else {
                        con.rollback();
                        return -1;
                    }
                }
            }

            List<Persona> miembros = arbol.miembros();
            try (PreparedStatement psMiembro = con.prepareStatement(sqlMiembro)) {
                for (Persona p : miembros) {
                    psMiembro.setLong(1, arbolId);
                    psMiembro.setString(2, p.getPosicion());
                    psMiembro.setString(3, p.getNombre());
                    psMiembro.setInt(4, p.getNacimiento());
                    if (p.getFallecimiento() != null) {
                        psMiembro.setInt(5, p.getFallecimiento());
                    } else {
                        psMiembro.setNull(5, java.sql.Types.INTEGER);
                    }
                    psMiembro.addBatch();
                }
                psMiembro.executeBatch();
            }

            con.commit(); // Confirmar transacción
            con.setAutoCommit(true);
            return arbolId;

        } catch (SQLException e) {
            System.out.println("Error al guardar el árbol: " + e.getMessage());
            return -1;
        }
    }

    // =====================================================================
    // ETAPA 4.2. Listar (SELECT con JOIN y COUNT)
    // =====================================================================

    /**
     * @return un resumen por cada árbol guardado (id, nombre, cuántos miembros tiene y cuándo se creó),
     *         ordenados por id; lista vacía si no hay árboles o si hay un error
     */
    public List<ResumenArbol> listar() {
        List<ResumenArbol> resumenes = new ArrayList<>();
        String sql = "SELECT a.id, a.nombre, a.creado, COUNT(m.id) AS total " +
                     "FROM arboles a LEFT JOIN miembros m ON m.arbol_id = a.id " +
                     "GROUP BY a.id, a.nombre, a.creado ORDER BY a.id";

        try (Connection con = ConexionBD.conectar();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                long id = rs.getLong("id");
                String nombre = rs.getString("nombre");
                Timestamp timestamp = rs.getTimestamp("creado");
                java.time.LocalDateTime creado = (timestamp != null) ? timestamp.toLocalDateTime() : null;
                int totalMiembros = rs.getInt("total");

                ResumenArbol resumen = new ResumenArbol(id, nombre, totalMiembros, creado);
                resumenes.add(resumen);
            }

        } catch (SQLException e) {
            System.out.println("Error al listar los árboles: " + e.getMessage());
        }

        return resumenes;
    }

    // =====================================================================
    // ETAPA 4.3. Cargar un árbol (SELECT y reconstrucción del árbol)
    // =====================================================================

    /**
     * @return el árbol con ese id, reconstruido con todos sus miembros; null si no existe ese id o si hay un error
     */
    public ArbolGenealogico cargar(long id) {
        String sql = "SELECT posicion, nombre, nacimiento, fallecimiento FROM miembros WHERE arbol_id = ? ORDER BY id";
        ArbolGenealogico arbol = null;

        try (Connection con = ConexionBD.conectar();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                boolean primerMiembro = true;
                while (rs.next()) {
                    String posicion = rs.getString("posicion");
                    String nombre = rs.getString("nombre");
                    int nacimiento = rs.getInt("nacimiento");
                    int fallecimientoVal = rs.getInt("fallecimiento");
                    Integer fallecimiento = rs.wasNull() ? null : fallecimientoVal;

                    Persona persona = new Persona(posicion, nombre, nacimiento, fallecimiento);

                    if (primerMiembro) {
                        arbol = new ArbolGenealogico(persona);
                        primerMiembro = false;
                    } else {
                        arbol.agregar(persona);
                    }
                }
            }

        } catch (SQLException e) {
            System.out.println("Error al cargar el árbol con id " + id + ": " + e.getMessage());
            return null;
        }

        return arbol;
    }

    // =====================================================================
    // ETAPA 4.4. Actualizar (reemplazar los miembros de un árbol, con transacción)
    // =====================================================================

    /**
     * Guarda los cambios de un árbol ya existente (por ejemplo, después de agregar o eliminar miembros):
     * deja en la base de datos exactamente los miembros de {@code arbol}. El nombre del árbol NO cambia.
     *
     * @return true si se actualizó; false si no existe un árbol con ese id, si el árbol es null o si hubo un
     *         error (en ese caso NO se modifica nada: ni se borran los miembros anteriores)
     */
    public boolean actualizar(long id, ArbolGenealogico arbol) {
        if (arbol == null) {
            return false;
        }

        String sqlCheck = "SELECT id FROM arboles WHERE id = ?";
        String sqlDelete = "DELETE FROM miembros WHERE arbol_id = ?";
        String sqlInsert = "INSERT INTO miembros (arbol_id, posicion, nombre, nacimiento, fallecimiento) VALUES (?, ?, ?, ?, ?)";

        try (Connection con = ConexionBD.conectar()) {
            if (con == null) {
                return false;
            }

            con.setAutoCommit(false);

            // Verificar si el árbol existe
            try (PreparedStatement psCheck = con.prepareStatement(sqlCheck)) {
                psCheck.setLong(1, id);
                try (ResultSet rs = psCheck.executeQuery()) {
                    if (!rs.next()) {
                        con.rollback();
                        return false;
                    }
                }
            }

            // Borrar miembros anteriores
            try (PreparedStatement psDelete = con.prepareStatement(sqlDelete)) {
                psDelete.setLong(1, id);
                psDelete.executeUpdate();
            }

            // Insertar los nuevos miembros actuales del árbol
            List<Persona> miembros = arbol.miembros();
            try (PreparedStatement psInsert = con.prepareStatement(sqlInsert)) {
                for (Persona p : miembros) {
                    psInsert.setLong(1, id);
                    psInsert.setString(2, p.getPosicion());
                    psInsert.setString(3, p.getNombre());
                    psInsert.setInt(4, p.getNacimiento());
                    if (p.getFallecimiento() != null) {
                        psInsert.setInt(5, p.getFallecimiento());
                    } else {
                        psInsert.setNull(5, java.sql.Types.INTEGER);
                    }
                    psInsert.addBatch();
                }
                psInsert.executeBatch();
            }

            con.commit();
            con.setAutoCommit(true);
            return true;

        } catch (SQLException e) {
            System.out.println("Error al actualizar el árbol con id " + id + ": " + e.getMessage());
            return false;
        }
    }

    // =====================================================================
    // ETAPA 4.5. Eliminar (DELETE)
    // =====================================================================

    /**
     * Elimina el árbol y todos sus miembros (la base de datos borra los miembros sola: ON DELETE CASCADE).
     *
     * @return true si se eliminó; false si no existía ese id o si hubo un error
     */
    public boolean eliminar(long id) {
        String sql = "DELETE FROM arboles WHERE id = ?";

        try (Connection con = ConexionBD.conectar();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setLong(1, id);
            int filasAfectadas = ps.executeUpdate();
            return filasAfectadas > 0;

        } catch (SQLException e) {
            System.out.println("Error al eliminar el árbol con id " + id + ": " + e.getMessage());
            return false;
        }
    }
}