package co.umariana.modelo;

import java.util.ArrayList;
import java.util.List;

/**
 * El arbol genealogico: un SUJETO (posicion "1") con sus ASCENDIENTES (padres,
 * abuelos...) en Ahnentafel y sus DESCENDIENTES (hijos, nietos...) en d'Aboville.
 *
 * Son dos arboles unidos en la raiz:
 *
 * ascendientes: arbol BINARIO. Cada persona tiene como mucho un padre (2n) y
 * una madre (2n + 1).
 * descendientes: arbol GENERAL. Cada persona tiene una lista de hijos ordenada
 * por numero de posicion.
 *
 * El arbol solo guarda los miembros que EXISTEN, sin posiciones vacias.
 */
public class ArbolGenealogico {

    /**
     * Clase interna Nodo para representar cada persona en el arbol.
     */
    private static class Nodo {
        Persona persona;
        Nodo padreNodo;
        Nodo madreNodo;
        List<Nodo> hijos;

        public Nodo(Persona persona) {
            this.persona = persona;
            this.hijos = new ArrayList<>();
        }
    }

    private Nodo raiz;
    private int cantidadMiembros;

    /**
     * Crea un arbol que solo tiene al sujeto.
     */
    public ArbolGenealogico(Persona sujeto) {
        if (sujeto == null) {
            throw new IllegalArgumentException("El sujeto no puede ser nulo.");
        }

        sujeto.validar();

        if (!Posicion.esSujeto(sujeto.getPosicion())) {
            throw new ArbolInvalidoException("La posicion del sujeto debe ser exactamente '1'.");
        }

        this.raiz = new Nodo(sujeto);
        this.cantidadMiembros = 1;
    }

    /** @return el sujeto del arbol */
    public Persona sujeto() {
        return raiz != null ? raiz.persona : null;
    }

    /** @return la cantidad de miembros del arbol */
    public int tamano() {
        return cantidadMiembros;
    }

    /**
     * Busca una persona por su posicion.
     */
    public Persona buscar(String posicion) {
        Nodo nodo = buscarNodo(posicion);
        return nodo != null ? nodo.persona : null;
    }

    /**
     * Busca un nodo por su posicion Ahnentafel o d'Aboville.
     */
    private Nodo buscarNodo(String posicion) {
        if (posicion == null || !Posicion.esValida(posicion) || raiz == null) {
            return null;
        }

        if (Posicion.esSujeto(posicion)) {
            return raiz;
        }

        // Buscar ascendientes mediante el recorrido binario
        if (Posicion.esAhnentafel(posicion)) {
            long n = Long.parseLong(posicion);

            String binario = Long.toBinaryString(n);
            Nodo actual = raiz;

            for (int i = 1; i < binario.length(); i++) {
                if (binario.charAt(i) == '0') {
                    actual = actual.padreNodo;
                } else {
                    actual = actual.madreNodo;
                }

                if (actual == null) {
                    return null;
                }
            }

            return actual;
        }

        // Buscar descendientes utilizando su posicion real
        String[] partes = posicion.split("\\.");
        Nodo actual = raiz;
        String posicionActual = "1";

        for (int i = 1; i < partes.length; i++) {
            posicionActual += "." + partes[i];

            Nodo siguiente = null;

            for (Nodo hijo : actual.hijos) {
                if (hijo.persona.getPosicion().equals(posicionActual)) {
                    siguiente = hijo;
                    break;
                }
            }

            if (siguiente == null) {
                return null;
            }

            actual = siguiente;
        }

        return actual;
    }

    /**
     * @return true si existe una persona en esa posicion.
     */
    public boolean contiene(String posicion) {
        return buscarNodo(posicion) != null;
    }

    /**
     * Agrega una persona utilizando la posicion que trae.
     */
    public Persona agregar(Persona persona) {
        if (persona == null) {
            throw new IllegalArgumentException("La persona no puede ser nula.");
        }

        persona.validar();

        String pos = persona.getPosicion();

        if (!Posicion.esValida(pos)) {
            throw new ArbolInvalidoException("La posicion de la persona no es valida: " + pos);
        }

        if (contiene(pos)) {
            throw new ArbolInvalidoException("La posicion " + pos + " ya existe en el arbol.");
        }

        if (Posicion.esSujeto(pos)) {
            throw new ArbolInvalidoException("La posicion '1' ya esta ocupada por el sujeto.");
        }

        // Agregar ascendientes
        if (Posicion.esAscendiente(pos)) {
            long n = Long.parseLong(pos);
            long padrePos = n / 2;

            Nodo nodoPadre = buscarNodo(String.valueOf(padrePos));

            if (nodoPadre == null) {
                throw new ArbolInvalidoException(
                        "Falta la persona de la que depende (posicion " + padrePos + ").");
            }

            if (persona.getNacimiento() >= nodoPadre.persona.getNacimiento()) {
                throw new ArbolInvalidoException(
                        "El ascendiente debe haber nacido antes que su descendiente.");
            }

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

            // Agregar descendientes
            String posProgenitor = Posicion.posicionDelProgenitor(pos);
            Nodo nodoProgenitor = buscarNodo(posProgenitor);

            if (nodoProgenitor == null) {
                throw new ArbolInvalidoException(
                        "Falta el progenitor en la posicion " + posProgenitor);
            }

            if (persona.getNacimiento() <= nodoProgenitor.persona.getNacimiento()) {
                throw new ArbolInvalidoException(
                        "El descendiente debe haber nacido despues de su progenitor.");
            }

            Nodo nuevoNodo = new Nodo(persona);

            // Insertar el hijo conservando el orden de su posicion
            int ordenNuevo = Posicion.orden(pos);
            int indice = 0;

            while (indice < nodoProgenitor.hijos.size()
                    && Posicion.orden(nodoProgenitor.hijos.get(indice).persona.getPosicion()) < ordenNuevo) {
                indice++;
            }

            nodoProgenitor.hijos.add(indice, nuevoNodo);
        }

        cantidadMiembros++;
        return persona;
    }

    /**
     * Agrega el padre de una persona en la posicion 2n.
     */
    public Persona agregarPadre(String posicion, String nombre, int nacimiento, Integer fallecimiento) {
        Nodo nodoHijo = buscarNodo(posicion);

        if (nodoHijo == null) {
            throw new ArbolInvalidoException("La posicion " + posicion + " no existe.");
        }

        if (!Posicion.esAhnentafel(posicion)) {
            throw new ArbolInvalidoException(
                    "Solo se pueden agregar padres al sujeto y a sus ascendientes.");
        }

        String posPadre = Posicion.posicionDelPadre(posicion);

        if (!Posicion.esAhnentafel(posPadre)) {
            throw new ArbolInvalidoException("Se alcanzo el limite de posiciones Ahnentafel.");
        }

        if (buscarNodo(posPadre) != null) {
            throw new ArbolInvalidoException("El padre en la posicion " + posPadre + " ya existe.");
        }

        Persona padre = new Persona(posPadre, nombre, nacimiento, fallecimiento);
        return agregar(padre);
    }

    /**
     * Agrega la madre de una persona en la posicion 2n + 1.
     */
    public Persona agregarMadre(String posicion, String nombre, int nacimiento, Integer fallecimiento) {
        Nodo nodoHijo = buscarNodo(posicion);

        if (nodoHijo == null) {
            throw new ArbolInvalidoException("La posicion " + posicion + " no existe.");
        }

        if (!Posicion.esAhnentafel(posicion)) {
            throw new ArbolInvalidoException(
                    "Solo se pueden agregar madres al sujeto y a sus ascendientes.");
        }

        String posMadre = Posicion.posicionDeLaMadre(posicion);

        if (!Posicion.esAhnentafel(posMadre)) {
            throw new ArbolInvalidoException("Se alcanzo el limite de posiciones Ahnentafel.");
        }

        if (buscarNodo(posMadre) != null) {
            throw new ArbolInvalidoException("La madre en la posicion " + posMadre + " ya existe.");
        }

        Persona madre = new Persona(posMadre, nombre, nacimiento, fallecimiento);
        return agregar(madre);
    }

    /**
     * Agrega un hijo a la persona indicada.
     */
    public Persona agregarHijo(String posicion, String nombre, int nacimiento, Integer fallecimiento) {
        Nodo nodoProgenitor = buscarNodo(posicion);

        if (nodoProgenitor == null) {
            throw new ArbolInvalidoException("La posicion " + posicion + " no existe.");
        }

        if (!Posicion.esSujeto(posicion) && !Posicion.esDescendiente(posicion)) {
            throw new ArbolInvalidoException(
                    "Solo se pueden agregar hijos al sujeto y a sus descendientes.");
        }

        // Encontrar el mayor numero de hijo existente
        int maxOrden = 0;

        for (Nodo hijo : nodoProgenitor.hijos) {
            int ordenHijo = Posicion.orden(hijo.persona.getPosicion());

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
     * Elimina una persona y todos los miembros que dependen de ella.
     */
    public int eliminar(String posicion) {
        if (posicion == null || Posicion.esSujeto(posicion)) {
            throw new ArbolInvalidoException(
                    "No se puede eliminar la posicion '1' (el sujeto).");
        }

        Nodo nodoAEliminar = buscarNodo(posicion);

        if (nodoAEliminar == null) {
            throw new ArbolInvalidoException(
                    "La posicion " + posicion + " no existe en el arbol.");
        }

        // Desvincular ascendientes
        if (Posicion.esAscendiente(posicion)) {
            long n = Long.parseLong(posicion);
            long padrePos = n / 2;

            Nodo nodoPadre = buscarNodo(String.valueOf(padrePos));

            if (nodoPadre != null) {
                if (n % 2 == 0) {
                    nodoPadre.padreNodo = null;
                } else {
                    nodoPadre.madreNodo = null;
                }
            }

        } else {

            // Desvincular descendientes
            String posProgenitor = Posicion.posicionDelProgenitor(posicion);
            Nodo nodoProgenitor = buscarNodo(posProgenitor);

            if (nodoProgenitor != null) {
                nodoProgenitor.hijos.remove(nodoAEliminar);
            }
        }

        // Contar y limpiar todos los miembros dependientes
        int eliminados = contarYLimpiarSubarbol(nodoAEliminar);

        cantidadMiembros -= eliminados;

        return eliminados;
    }

    /**
     * Cuenta y desconecta todos los nodos que dependen del nodo indicado.
     */
    private int contarYLimpiarSubarbol(Nodo nodo) {
        if (nodo == null) {
            return 0;
        }

        int cuenta = 1;

        // Contar los ascendientes conectados
        cuenta += contarYLimpiarSubarbol(nodo.padreNodo);
        cuenta += contarYLimpiarSubarbol(nodo.madreNodo);

        // Contar los descendientes
        for (Nodo hijo : nodo.hijos) {
            cuenta += contarYLimpiarSubarbol(hijo);
        }

        // Desconectar el nodo y sus relaciones
        nodo.padreNodo = null;
        nodo.madreNodo = null;
        nodo.hijos.clear();

        return cuenta;
    }

    /**
     * Devuelve todos los miembros en este orden:
     * sujeto, ascendientes por Ahnentafel y descendientes en preorden.
     */
    public List<Persona> miembros() {
        List<Persona> lista = new ArrayList<>();

        if (raiz == null) {
            return lista;
        }

        // Agregar el sujeto
        lista.add(raiz.persona);

        // Agregar ascendientes por numero Ahnentafel ascendente
        for (int n = 2; n <= Posicion.MAX_AHNENTAFEL; n++) {
            Nodo asc = buscarNodo(String.valueOf(n));

            if (asc != null) {
                lista.add(asc.persona);
            }
        }

        // Agregar descendientes en preorden
        for (Nodo hijo : raiz.hijos) {
            recolectarPreorden(hijo, lista);
        }

        return lista;
    }

    /**
     * Recorre los descendientes en preorden.
     */
    private void recolectarPreorden(Nodo nodo, List<Persona> lista) {
        if (nodo == null) {
            return;
        }

        lista.add(nodo.persona);

        for (Nodo hijo : nodo.hijos) {
            recolectarPreorden(hijo, lista);
        }
    }

    /**
     * Devuelve solo los ascendientes por numero Ahnentafel ascendente.
     */
    public List<Persona> ascendientes() {
        List<Persona> lista = new ArrayList<>();

        for (int n = 2; n <= Posicion.MAX_AHNENTAFEL; n++) {
            Nodo asc = buscarNodo(String.valueOf(n));

            if (asc != null) {
                lista.add(asc.persona);
            }
        }

        return lista;
    }

    /**
     * Devuelve solo los descendientes en preorden.
     */
    public List<Persona> descendientes() {
        List<Persona> lista = new ArrayList<>();

        if (raiz == null) {
            return lista;
        }

        for (Nodo hijo : raiz.hijos) {
            recolectarPreorden(hijo, lista);
        }

        return lista;
    }
}
