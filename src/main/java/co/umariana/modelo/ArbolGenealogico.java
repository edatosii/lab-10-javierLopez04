package co.umariana.modelo;

import java.util.ArrayList;
import java.util.List;

/**
 * El árbol genealógico: un SUJETO (posición "1") con sus ASCENDIENTES (padres,
 * abuelos... en Ahnentafel)
 * y sus DESCENDIENTES (hijos, nietos... en d'Aboville). Ver la clase Posicion.
 *
 * Son en realidad dos árboles unidos en la raíz:
 *
 * ascendientes: árbol BINARIO. Cada persona tiene como mucho un padre (2n) y
 * una madre (2n + 1).
 * descendientes: árbol GENERAL. Cada persona tiene una lista de hijos (1.1,
 * 1.2, ...) ordenada por número.
 *
 * El árbol solo guarda a los miembros que EXISTEN: no hay "huecos" ni personas
 * vacías. Puede faltar el padre
 * de alguien, o el hijo 1.1 aunque exista el 1.2.
 */

public class ArbolGenealogico {

    /**
     * Clase interna Nodo para representar cada persona en el árbol.
     */
    private static class Nodo {
        Persona persona;
        Nodo padreNodo; // Padre en el árbol binario de ascendientes
        Nodo madreNodo; // Madre en el árbol binario de ascendientes
        List<Nodo> hijos; // Lista de hijos para el árbol general de descendientes

        public Nodo(Persona persona) {
            this.persona = persona;
            this.hijos = new ArrayList<>();
        }
    }

    private Nodo raiz;
    private int cantidadMiembros;

    /**
     * Crea un árbol que solo tiene al sujeto.
     *
     * @throws IllegalArgumentException si la persona no es válida (Persona.validar)
     * @throws ArbolInvalidoException   si su posición no es "1"
     */
    public ArbolGenealogico(Persona sujeto) {
        if (sujeto == null) {
            throw new IllegalArgumentException("El sujeto no puede ser nulo.");
        }
        // Validar datos de la persona (lanza IllegalArgumentException si falla)
        sujeto.validar();

        if (sujeto.getPosicion() == null || !sujeto.getPosicion().equals("1")) {
            throw new ArbolInvalidoException("La posición del sujeto debe ser exactamente '1'.");
        }

        this.raiz = new Nodo(sujeto);
        this.cantidadMiembros = 1;
    }

    /** @return el sujeto (la persona de la posición "1") */
    public Persona sujeto() {
        return raiz != null ? raiz.persona : null;
    }

    /**
     * @return cuántos miembros tiene el árbol (el sujeto cuenta; un árbol nuevo
     *         tiene 1)
     */
    public int tamano() {
        return this.cantidadMiembros;
    }

    /**
     * @return la persona de esa posición, o null si no existe en el árbol o si la
     *         posición no es válida
     */
    public Persona buscar(String posicion) {
        Nodo nodo = buscarNodo(posicion);
        return (nodo != null) ? nodo.persona : null;
    }

    /**
     * Método auxiliar para buscar un nodo dado su string de posición (Ahnentafel o
     * D'Aboville).
     */
    private Nodo buscarNodo(String posicion) {
        if (posicion == null || !Posicion.esValida(posicion)) {
            return null;
        }
        if (posicion.equals("1")) {
            return raiz;
        }

        // Si es puramente numérico, es un número Ahnentafel (Ascendientes)
        if (esNumeroEntero(posicion)) {
            long n = Long.parseLong(posicion);
            if (n <= 1)
                return null;

            // Reconstruir camino binario desde la raíz (1) hasta n
            // Los bits después del primer 1: 0 = padre, 1 = madre
            String binario = Long.toBinaryString(n);
            Nodo actual = raiz;
            for (int i = 1; i < binario.length(); i++) {
                char bit = binario.charAt(i);
                if (bit == '0') {
                    actual = actual.padreNodo;
                } else {
                    actual = actual.madreNodo;
                }
                if (actual == null)
                    return null;
            }
            return actual;
        } else {
            // Es un número D'Aboville (Descendientes), formato ej: 1.2.3
            String[] partes = posicion.split("\\.");
            if (partes.length == 0 || !partes[0].equals("1"))
                return null;

            Nodo actual = raiz;
            for (int i = 1; i < partes.length; i++) {
                int indiceHijo;
                try {
                    indiceHijo = Integer.parseInt(partes[i]);
                } catch (NumberFormatException e) {
                    return null;
                }

                // Buscar el hijo cuyo número o índice coincida
                // Nota: D'Aboville empieza típicamente en 1 (hijo 1, 2...)
                if (indiceHijo <= 0 || indiceHijo > actual.hijos.size()) {
                    return null;
                }
                actual = actual.hijos.get(indiceHijo - 1);
                if (actual == null)
                    return null;
            }
            return actual;
        }
    }

    private boolean esNumeroEntero(String str) {
        if (str == null || str.isEmpty())
            return false;
        for (char c : str.toCharArray()) {
            if (!Character.isDigit(c))
                return false;
        }
        return true;
    }

    /**
     * @return true si hay alguien en esa posición (false también si la posición no
     *         es válida)
     */
    public boolean contiene(String posicion) {
        return buscarNodo(posicion) != null;
    }

    /**
     * Agrega una persona en la posición que ella misma trae.
     */
    public Persona agregar(Persona persona) {
        if (persona == null) {
            throw new IllegalArgumentException("La persona no puede ser nula.");
        }
        persona.validar(); // Lanza IllegalArgumentException si no es válida

        String pos = persona.getPosicion();
        if (pos == null || !Posicion.esValida(pos)) {
            throw new ArbolInvalidoException("La posición de la persona no es válida: " + pos);
        }

        if (contiene(pos)) {
            throw new ArbolInvalidoException("La posición " + pos + " ya existe en el árbol.");
        }

        if (pos.equals("1")) {
            throw new ArbolInvalidoException("La posición '1' ya está ocupada por el sujeto.");
        }

        // Determinar si es ascendiente (número puro) o descendiente (con puntos)
        if (esNumeroEntero(pos)) {
            long n = Long.parseLong(pos);
            long padrePos = n / 2;
            Nodo nodoPadre = buscarNodo(String.valueOf(padrePos));

            if (nodoPadre == null) {
                throw new ArbolInvalidoException("Falta la persona de la que depende (posición " + padrePos + ").");
            }

            // Regla: año de nacimiento menor que el hijo/progenitor
            if (persona.getNacimiento() >= nodoPadre.persona.getNacimiento()) {
                throw new ArbolInvalidoException("El ascendiente debe haber nacido antes que su descendiente.");
            }

            // Insertar nodo en el padre binario
            Nodo nuevoNodo = new Nodo(persona);
            if (n % 2 == 0) {
                if (nodoPadre.padreNodo != null) {
                    throw new ArbolInvalidoException("El padre ya existe.");
                }
                nodoPadre.padreNodo = nuevoNodo;
            } else {
                if (nodoPadre.madreNodo != null) {
                    throw new ArbolInvalidoException("La madre ya existe.");
                }
                nodoPadre.madreNodo = nuevoNodo;
            }
        } else {
            // Es descendiente (D'Aboville)
            int ultimoPunto = pos.lastIndexOf('.');
            String posProgenitor = pos.substring(0, ultimoPunto);
            Nodo nodoProgenitor = buscarNodo(posProgenitor);

            if (nodoProgenitor == null) {
                throw new ArbolInvalidoException("Falta el progenitor en la posición " + posProgenitor);
            }

            // Regla: año de nacimiento mayor que el progenitor
            if (persona.getNacimiento() <= nodoProgenitor.persona.getNacimiento()) {
                throw new ArbolInvalidoException("El descendiente debe haber nacido después de su progenitor.");
            }

            // Agregar a la lista de hijos del progenitor
            Nodo nuevoNodo = new Nodo(persona);
            nodoProgenitor.hijos.add(nuevoNodo);
        }

        cantidadMiembros++;
        return persona;
    }

    /**
     * Agrega el PADRE de la persona de esa posición, en la posición 2n.
     */
    public Persona agregarPadre(String posicion, String nombre, int nacimiento, Integer fallecimiento) {
        Nodo nodoHijo = buscarNodo(posicion);
        if (nodoHijo == null) {
            throw new ArbolInvalidoException("La posición " + posicion + " no existe.");
        }

        if (!esNumeroEntero(posicion)) {
            throw new ArbolInvalidoException("Solo se pueden agregar padres al sujeto y a sus ascendientes.");
        }

        long n = Long.parseLong(posicion);

        // CORRECCIÓN: Convertir n a String usando String.valueOf(n)
        String posPadre = Posicion.posicionDelPadre(String.valueOf(n));

        if (buscarNodo(posPadre) != null) {
            throw new ArbolInvalidoException("El padre en la posición " + posPadre + " ya existe.");
        }

        Persona padre = new Persona(posPadre, nombre, nacimiento, fallecimiento);
        return agregar(padre);
    }

    /**
     * Agrega la MADRE de la persona de esa posición, en la posición 2n + 1.
     */
    public Persona agregarMadre(String posicion, String nombre, int nacimiento, Integer fallecimiento) {
        Nodo nodoHijo = buscarNodo(posicion);
        if (nodoHijo == null) {
            throw new ArbolInvalidoException("La posición " + posicion + " no existe.");
        }

        if (!esNumeroEntero(posicion)) {
            throw new ArbolInvalidoException("Solo se pueden agregar madres al sujeto y a sus ascendientes.");
        }

        long n = Long.parseLong(posicion);

        // CORRECCIÓN: Convertir n a String usando String.valueOf(n)
        String posMadre = Posicion.posicionDeLaMadre(String.valueOf(n));

        if (buscarNodo(posMadre) != null) {
            throw new ArbolInvalidoException("La madre en la posición " + posMadre + " ya existe.");
        }

        Persona madre = new Persona(posMadre, nombre, nacimiento, fallecimiento);
        return agregar(madre);
    }

    /**
     * Agrega un HIJO a la persona de esa posición.
     */
    public Persona agregarHijo(String posicion, String nombre, int nacimiento, Integer fallecimiento) {
        Nodo nodoProgenitor = buscarNodo(posicion);
        if (nodoProgenitor == null) {
            throw new ArbolInvalidoException("La posición " + posicion + " no existe.");
        }

        if (!posicion.equals("1") && esNumeroEntero(posicion)) {
            throw new ArbolInvalidoException("Solo se pueden agregar hijos al sujeto y a sus descendientes.");
        }

        // Encontrar el mayor número de hijo existente para soportar huecos (ej: si
        // existe 1.2, el siguiente es 1.3)
        int maxOrden = 0;
        for (Nodo hijo : nodoProgenitor.hijos) {
            String posHijo = hijo.persona.getPosicion();
            int ordenHijo = Posicion.orden(posHijo);
            if (ordenHijo > maxOrden) {
                maxOrden = ordenHijo;
            }
        }
        int siguienteNumeroHijo = maxOrden + 1;
        String posHijo = posicion + "." + siguienteNumeroHijo;

        Persona hijo = new Persona(posHijo, nombre, nacimiento, fallecimiento);
        return agregar(hijo);
    }

    /**
     * Elimina a la persona de esa posición y TOD0 lo que depende de ella.
     */
    public int eliminar(String posicion) {
        if (posicion == null || posicion.equals("1")) {
            throw new ArbolInvalidoException("No se puede eliminar la posición '1' (el sujeto).");
        }

        Nodo nodoAEliminar = buscarNodo(posicion);
        if (nodoAEliminar == null) {
            throw new ArbolInvalidoException("La posición " + posicion + " no existe en el árbol.");
        }

        // Desvincular el nodo de su padre/progenitor
        if (esNumeroEntero(posicion)) {
            long n = Long.parseLong(posicion);
            long padrePos = n / 2;
            Nodo nodoPadre = buscarNodo(String.valueOf(padrePos));
            if (nodoPadre != null) {
                if (nodoPadre.padreNodo == nodoAEliminar) {
                    nodoPadre.padreNodo = null;
                } else if (nodoPadre.madreNodo == nodoAEliminar) {
                    nodoPadre.madreNodo = null;
                }
            }
        } else {
            int ultimoPunto = posicion.lastIndexOf('.');
            String posProgenitor = posicion.substring(0, ultimoPunto);
            Nodo nodoProgenitor = buscarNodo(posProgenitor);
            if (nodoProgenitor != null) {
                nodoProgenitor.hijos.remove(nodoAEliminar);
            }
        }

        // Contar cuántos miembros se eliminan en total y limpiar
        int eliminados = contarYLimpiarSubarbol(nodoAEliminar);
        this.cantidadMiembros -= eliminados;
        return eliminados;
    }

    private int contarYLimpiarSubarbol(Nodo nodo) {
        if (nodo == null)
            return 0;
        int cuenta = 1; // Cuenta al nodo actual

        // Contar ascendientes conectados si los tuviera
        cuenta += contarYLimpiarSubarbol(nodo.padreNodo);
        cuenta += contarYLimpiarSubarbol(nodo.madreNodo);

        // Contar descendientes
        for (Nodo hijo : nodo.hijos) {
            cuenta += contarYLimpiarSubarbol(hijo);
        }

        // Aislar el nodo
        nodo.padreNodo = null;
        nodo.madreNodo = null;
        nodo.hijos.clear();
        return cuenta;
    }

    /**
     * @return todos los miembros en este orden: sujeto, ascendientes (Ahnentafel
     *         ascendente), descendientes (Preorden).
     */
    public List<Persona> miembros() {
        List<Persona> lista = new ArrayList<>();
        if (raiz == null)
            return lista;

        // 1. Sujeto
        lista.add(raiz.persona);

        // 2. Ascendientes por número Ahnentafel ascendente (2, 3, 4, 5...)

        long n = 2;
        while (n <= 4095) { // Cubre hasta más de 10 generaciones
            Nodo asc = buscarNodo(String.valueOf(n));
            if (asc != null) {
                lista.add(asc.persona);
            }
            n++;

        }


        // 3. Descendientes en PREORDEN
        for (Nodo hijo : raiz.hijos) {
            recolectarPreorden(hijo, lista);
        }

        return lista;
    }

  

    private void recolectarPreorden(Nodo nodo, List<Persona> lista) {
        if (nodo == null)
            return;
        lista.add(nodo.persona);
        for (Nodo hijo : nodo.hijos) {
            recolectarPreorden(hijo, lista);
        }
    }

    /**
     * @return solo los ascendientes, por número Ahnentafel ascendente (sin el
     *         sujeto)
     */
    public List<Persona> ascendientes() {
        List<Persona> lista = new ArrayList<>();
        long n = 2;
        // Determinamos un límite basándonos en si la posición es alcanzable o buscamos
        // secuencialmente
        // Recorremos hasta que no encontremos más nodos consecutivos o evaluamos por
        // existencia
        while (n < 100000) {
            Nodo asc = buscarNodo(String.valueOf(n));
            if (asc != null) {
                lista.add(asc.persona);
            } else {
                // Si hay un salto muy grande sin ascendientes, podemos parar si ya superamos la
                // profundidad lógica del árbol
                // Una verificación sencilla es ver si el nodo raíz o los primeros niveles
                // tienen ramas
                if (n > 100 && lista.isEmpty())
                    break;
            }
            n++;
            // Para evitar ciclos infinitos largos si no hay elementos altos, podemos poner
            // una condición de parada si llevamos muchos n vacíos seguidos
        }

        // Forma más limpia filtrando desde miembros()
        List<Persona> todos = miembros();
        List<Persona> soloAsc = new ArrayList<>();
        for (Persona p : todos) {
            String pos = p.getPosicion();
            if (esNumeroEntero(pos) && Long.parseLong(pos) > 1) {
                soloAsc.add(p);
            }
        }
        return soloAsc;
    }

    /** @return solo los descendientes, en preorden (sin el sujeto) */
    public List<Persona> descendientes() {
        List<Persona> lista = new ArrayList<>();
        for (Nodo hijo : raiz.hijos) {
            recolectarPreorden(hijo, lista);
        }
        return lista;
    }
}
