package com.gestion.proyectos.util;

public final class ValidacionUtil {

    private ValidacionUtil() {}

    /** Retorna true si el string es null o solo espacios en blanco. */
    public static boolean esVacio(String s) {
        return s == null || s.isBlank();
    }

    public static final String ERROR_TELEFONO = "El teléfono debe tener 10 dígitos y empezar por 3 (ej: 3001234567)";
    public static final String ERROR_IDENTIFICACION = "La identificación debe tener entre 6 y 10 dígitos (solo números)";

    public static boolean telefonoValido(String t) {
        return t != null && t.trim().matches("^3\\d{9}$");
    }

    public static boolean identificacionValida(String s) {
        return s != null && s.trim().matches("^\\d{6,10}$");
    }

    /** Regla única de contraseña. Retorna el mensaje de error o null si es válida. */
    public static String errorClave(String p) {
        if (esVacio(p)) return "La contraseña es obligatoria";
        if (p.length() < 8) return "La contraseña debe tener al menos 8 caracteres";
        if (p.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
            return "La contraseña no puede superar los 72 caracteres";
        if (p.chars().noneMatch(Character::isLetter) || p.chars().noneMatch(Character::isDigit))
            return "La contraseña debe incluir al menos una letra y un número";
        return null;
    }

    /** Escapa el texto para usarlo como literal dentro de un $regex de MongoDB ("(" no rompe la consulta). */
    public static String literalRegex(String s) {
        return s == null ? "" : s.replaceAll("[\\\\^$.|?*+()\\[\\]{}]", "\\\\$0");
    }
}
