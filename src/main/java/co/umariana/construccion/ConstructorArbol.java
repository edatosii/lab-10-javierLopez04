package co.umariana.construccion;

import co.umariana.modelo.ArbolGenealogico;
import co.umariana.modelo.ArbolInvalidoException;
import co.umariana.modelo.Persona;


import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;



public final class ConstructorArbol {

    private ConstructorArbol() {
    }


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

            String[] campos = linea.split(";", -1);
            if (campos.length < 3 || campos.length > 4) {
                throw new ArbolInvalidoException("Línea " + numLinea + ": número de campos inválido (deben ser 3 o 4).");
            }

            String posStr = campos[0].trim();
            String nombre = campos[1].trim();
            String nacStr = campos[2].trim();
            String falStr = (campos.length == 4) ? campos[3].trim() : "";

            int nacimiento;
            try {
                nacimiento = Integer.parseInt(nacStr);
            } catch (NumberFormatException e) {
                throw new ArbolInvalidoException("Línea " + numLinea + ": el año de nacimiento no es un número entero válido.");
            }

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
                persona.validar();
            } catch (IllegalArgumentException e) {
                throw new ArbolInvalidoException("Línea " + numLinea + ": " + e.getMessage());
            }

            if (posicionesVistas.contains(posStr)) {
                throw new ArbolInvalidoException("Línea " + numLinea + ": la posición " + posStr + " está repetida.");
            }
            posicionesVistas.add(posStr);
            listaPersonas.add(persona);
        }

        if (!tieneLineasConContenido || listaPersonas.isEmpty()) {
            throw new ArbolInvalidoException("El texto está vacío...");
        }

        
        if (!posicionesVistas.contains("1")) {
            throw new ArbolInvalidoException("El texto no contiene al sujeto (posición 1).");
        }

        // Encontrar al sujeto
        Persona sujeto = null;
        List<Persona> pendientes = new ArrayList<>();
        for (Persona p : listaPersonas) {
            if (p.getPosicion().equals("1")) {
                sujeto = p;
            } else {
                pendientes.add(p);
            }
        }

        ArbolGenealogico arbol = new ArbolGenealogico(sujeto);

        // Insertar pendientes manejando el orden de dependencia de forma iterativa
        // Insertar pendientes manejando el orden de dependencia de forma iterativa
        int intentosPrevios = -1;
        while (!pendientes.isEmpty() && pendientes.size() != intentosPrevios) {
            intentosPrevios = pendientes.size();
            List<Persona> porReintentar = new ArrayList<>();

            for (Persona p : pendientes) {
                try {
                    arbol.agregar(p);
                } catch (ArbolInvalidoException e) {
                    // Si aún no se puede agregar porque falta su progenitor/hijo, lo posponemos
                    porReintentar.add(p);
                } catch (IllegalArgumentException e) {
                    porReintentar.add(p);
                }
            }
            pendientes = porReintentar;
        }

        // Si quedaron pendientes sin poderse agregar, lanzamos la excepción con la regla correspondiente
        if (!pendientes.isEmpty()) {
            Persona p = pendientes.get(0);
            // Validar la causa exacta para lanzar el mensaje con formato de línea si es necesario
            try {
                arbol.agregar(p);
            } catch (ArbolInvalidoException e) {
                throw new ArbolInvalidoException(e.getMessage());
            }
        }

        return arbol;
    }
}