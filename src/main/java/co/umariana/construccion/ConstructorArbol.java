package co.umariana.construccion;

import co.umariana.modelo.ArbolGenealogico;
import co.umariana.modelo.ArbolInvalidoException;
import co.umariana.modelo.Persona;
import co.umariana.modelo.Posicion;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Construye un árbol genealógico a partir de un texto: cada línea es una persona.
 *
 *     posicion;nombre;nacimiento;fallecimiento
 *
 * Ejemplo (el fallecimiento puede quedar vacío):
 *
 *     1;John Doe;2002;
 *     2;Henry Doe;1920;1990
 *     3;Maria Doe;1921;
 *     1.1;Timmy Doe;2021;
 *     1.2;Amy Doe;2026;
 */

public final class ConstructorArbol {

    private ConstructorArbol() {
    }

    /**
     * Convierte el texto en un árbol (ver las reglas arriba).
     *
     * @throws ArbolInvalidoException si el texto no cumple las reglas
     */
    public static ArbolGenealogico construir(String texto) {
        if (texto == null) {
            throw new ArbolInvalidoException("El texto está vacío...");
        }

        String[] lineas = texto.split("\\r?\\n");
        List<Persona> listaPersonas = new ArrayList<>();
        Set<String> posicionesVistas = new HashSet<>();
        boolean tieneLineasConContenido = false;

        for (int i = 0; i < lineas.length; i++) {
            int numLinea = i + 1;
            String linea = lineas[i];
            if (linea.isBlank()) {
                continue;
            }
            tieneLineasConContenido = true;

            // Separar campos manteniendo los vacíos al final si los hubiera
            String[] campos = linea.split(";", -1);
            if (campos.length < 3 || campos.length > 4) {
                throw new ArbolInvalidoException("Línea " + numLinea + ": número de campos inválido (deben ser 3 o 4).");
            }

            String posStr = campos[0].trim();
            String nombre = campos[1].trim();
            String nacStr = campos[2].trim();
            String falStr = (campos.length == 4) ? campos[3].trim() : "";

            // Validar año de nacimiento
            int nacimiento;
            try {
                nacimiento = Integer.parseInt(nacStr);
            } catch (NumberFormatException e) {
                throw new ArbolInvalidoException("Línea " + numLinea + ": el año de nacimiento no es un número entero válido.");
            }

            // Validar año de fallecimiento (opcional)
            Integer fallecimiento = null;
            if (!falStr.isEmpty()) {
                try {
                    fallecimiento = Integer.parseInt(falStr);
                } catch (NumberFormatException e) {
                    throw new ArbolInvalidoException("Línea " + numLinea + ": el año de fallecimiento no es un número entero válido.");
                }
            }

            Persona persona;
            try {
                persona = new Persona(posStr, nombre, nacimiento, fallecimiento);
                persona.validar(); // Lanza IllegalArgumentException si falla
            } catch (IllegalArgumentException e) {
                throw new ArbolInvalidoException("Línea " + numLinea + ": " + e.getMessage());
            }

            // Verificar posiciones repetidas
            if (posicionesVistas.contains(posStr)) {
                throw new ArbolInvalidoException("Línea " + numLinea + ": la posición " + posStr + " está repetida.");
            }
            posicionesVistas.add(posStr);
            listaPersonas.add(persona);
        }

        if (!tieneLineasConContenido || listaPersonas.isEmpty()) {
            throw new ArbolInvalidoException("El texto está vacío...");
        }

        // El sujeto ("1") debe existir obligatoriamente
        if (!posicionesVistas.contains("1")) {
            throw new ArbolInvalidoException("El texto no contiene al sujeto (posición 1).");
        }

        // Ordenar las personas para garantizar que los progenitores se agreguen antes que sus dependientes
        listaPersonas.sort((p1, p2) -> {
            String pos1 = p1.getPosicion();
            String pos2 = p2.getPosicion();

            if (pos1.equals("1")) return -1;
            if (pos2.equals("1")) return 1;

            boolean asc1 = Posicion.esAscendiente(pos1);
            boolean asc2 = Posicion.esAscendiente(pos2);

            if (asc1 && !asc2) return -1;
            if (!asc1 && asc2) return 1;

            if (asc1 && asc2) {
                return Long.compare(Long.parseLong(pos1), Long.parseLong(pos2));
            }

            // Descendientes (d'Aboville): ordenar por profundidad (cantidad de segmentos) y luego componente a componente
            String[] parts1 = pos1.split("\\.");
            String[] parts2 = pos2.split("\\.");

            if (parts1.length != parts2.length) {
                return Integer.compare(parts1.length, parts2.length);
            }

            for (int j = 0; j < parts1.length; j++) {
                int v1 = Integer.parseInt(parts1[j]);
                int v2 = Integer.parseInt(parts2[j]);
                if (v1 != v2) {
                    return Integer.compare(v1, v2);
                }
            }
            return 0;
        });

        // Encontrar al sujeto y crear el árbol
        Persona sujeto = null;
        for (Persona p : listaPersonas) {
            if (p.getPosicion().equals("1")) {
                sujeto = p;
                break;
            }
        }

        ArbolGenealogico arbol = new ArbolGenealogico(sujeto);

        // Agregar el resto de los miembros ordenadamente
        for (Persona p : listaPersonas) {
            if (!p.getPosicion().equals("1")) {
                arbol.agregar(p);
            }
        }

        return arbol;
    }
}