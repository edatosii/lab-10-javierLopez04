
package co.umariana.construccion;

import co.umariana.modelo.ArbolGenealogico;
import co.umariana.modelo.ArbolInvalidoException;
import co.umariana.modelo.Persona;
import co.umariana.modelo.Posicion;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;



public final class ConstructorArbol {

    private ConstructorArbol() {
    }

    // Clase para conservar la persona y su numero de linea original
    private static class PersonaConLinea {

        private Persona persona;
        private int numeroLinea;

        public PersonaConLinea(Persona persona, int numeroLinea) {
            this.persona = persona;
            this.numeroLinea = numeroLinea;
        }
    }

    public static ArbolGenealogico construir(String texto) {

        if (texto == null) {
            throw new ArbolInvalidoException("El texto está vacío...");
        }

        String[] lineas = texto.split("\\r?\\n");

        List<PersonaConLinea> listaPersonas = new ArrayList<>();
        Set<String> posicionesVistas = new HashSet<>();

        boolean tieneLineasConContenido = false;

        for (int i = 0; i < lineas.length; i++) {

            int numLinea = i + 1;
            String linea = lineas[i];

            if (linea.isBlank()) {
                continue;
            }

            tieneLineasConContenido = true;

            String[] campos = linea.split(";", -1);

            if (campos.length < 3 || campos.length > 4) {
                throw new ArbolInvalidoException(
                    "Línea " + numLinea + ": número de campos inválido (deben ser 3 o 4)."
                );
            }

            String posStr = campos[0].trim();
            String nombre = campos[1].trim();
            String nacStr = campos[2].trim();
            String falStr = (campos.length == 4) ? campos[3].trim() : "";

            int nacimiento;

            try {
                nacimiento = Integer.parseInt(nacStr);
            } catch (NumberFormatException e) {
                throw new ArbolInvalidoException(
                    "Línea " + numLinea + ": el año de nacimiento no es un número entero válido."
                );
            }

            Integer fallecimiento = null;

            if (!falStr.isEmpty()) {
                try {
                    fallecimiento = Integer.parseInt(falStr);
                } catch (NumberFormatException e) {
                    throw new ArbolInvalidoException(
                        "Línea " + numLinea + ": el año de fallecimiento no es un número entero válido."
                    );
                }
            }

            Persona persona;

            try {
                persona = new Persona(posStr, nombre, nacimiento, fallecimiento);
                persona.validar();
            } catch (IllegalArgumentException e) {
                throw new ArbolInvalidoException(
                    "Línea " + numLinea + ": " + e.getMessage()
                );
            }

            if (posicionesVistas.contains(posStr)) {
                throw new ArbolInvalidoException(
                    "Línea " + numLinea + ": la posición " + posStr + " está repetida."
                );
            }

            posicionesVistas.add(posStr);

            listaPersonas.add(new PersonaConLinea(persona, numLinea));
        }

        if (!tieneLineasConContenido || listaPersonas.isEmpty()) {
            throw new ArbolInvalidoException("El texto está vacío...");
        }

        if (!posicionesVistas.contains("1")) {
            throw new ArbolInvalidoException(
                "El texto no contiene al sujeto (posición 1)."
            );
        }

        // Encontrar al sujeto y separar las personas pendientes
        Persona sujeto = null;
        List<PersonaConLinea> pendientes = new ArrayList<>();

        for (PersonaConLinea registro : listaPersonas) {

            if (registro.persona.getPosicion().equals("1")) {
                sujeto = registro.persona;
            } else {
                pendientes.add(registro);
            }
        }

        ArbolGenealogico arbol = new ArbolGenealogico(sujeto);

        // Agregar las personas respetando sus dependencias
        boolean huboCambios = true;

        while (!pendientes.isEmpty() && huboCambios) {

            huboCambios = false;

            List<PersonaConLinea> porReintentar = new ArrayList<>();

            for (PersonaConLinea registro : pendientes) {

                try {
                    arbol.agregar(registro.persona);
                    huboCambios = true;

                } catch (ArbolInvalidoException e) {
                    porReintentar.add(registro);

                } catch (IllegalArgumentException e) {
                    porReintentar.add(registro);
                }
            }

            pendientes = porReintentar;
        }

        // Revisar los errores de las personas que no pudieron agregarse
        if (!pendientes.isEmpty()) {

            PersonaConLinea registroError = null;
            RuntimeException errorEncontrado = null;

            // Primero buscamos errores cuya dependencia ya existe en el arbol
            for (PersonaConLinea registro : pendientes) {

                String dependencia = obtenerDependencia(
                    registro.persona.getPosicion()
                );

                if (dependencia != null && arbol.contiene(dependencia)) {

                    try {
                        arbol.agregar(registro.persona);

                    } catch (ArbolInvalidoException e) {
                        registroError = registro;
                        errorEncontrado = e;
                        break;

                    } catch (IllegalArgumentException e) {
                        registroError = registro;
                        errorEncontrado = e;
                        break;
                    }
                }
            }

            // Si no encontramos un error especifico, informamos la primera dependencia pendiente
            if (registroError == null) {

                registroError = pendientes.get(0);

                try {
                    arbol.agregar(registroError.persona);

                } catch (ArbolInvalidoException e) {
                    errorEncontrado = e;

                } catch (IllegalArgumentException e) {
                    errorEncontrado = e;
                }
            }

            if (errorEncontrado != null) {
                throw new ArbolInvalidoException(
                    "Línea " + registroError.numeroLinea + ": "
                    + errorEncontrado.getMessage()
                );
            }

            throw new ArbolInvalidoException(
                "Línea " + registroError.numeroLinea
                + ": no se pudo agregar la persona por una dependencia inexistente."
            );
        }

        return arbol;
    }

    // Obtener la posicion del progenitor o del hijo necesario
    private static String obtenerDependencia(String posicion) {

        if (Posicion.esAhnentafel(posicion)) {

            long numero = Long.parseLong(posicion);

            return String.valueOf(numero / 2);
        }

        int ultimoPunto = posicion.lastIndexOf('.');

        if (ultimoPunto > 0) {
            return posicion.substring(0, ultimoPunto);
        }

        return null;
    }
}
