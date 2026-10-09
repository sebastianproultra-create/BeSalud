package com.gestion.proyectos.util;

public final class ValidacionUtil {

    private ValidacionUtil() {}

    /** Retorna true si el string es null o solo espacios en blanco. */
    public static boolean esVacio(String s) {
        return s == null || s.isBlank();
    }

    /** Escapa el texto para usarlo como literal dentro de un $regex de MongoDB ("(" no rompe la consulta). */
    public static String literalRegex(String s) {
        return s == null ? "" : s.replaceAll("[\\\\^$.|?*+()\\[\\]{}]", "\\\\$0");
    }
}
