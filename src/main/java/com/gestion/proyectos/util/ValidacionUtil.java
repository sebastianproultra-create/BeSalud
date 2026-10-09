package com.gestion.proyectos.util;

import java.util.Map;
import java.util.regex.Pattern;

public final class ValidacionUtil {

    private ValidacionUtil() {
    }

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

    /**
     * Regla única de contraseña. Retorna el mensaje de error o null si es válida.
     */
    public static String errorClave(String p) {
        if (esVacio(p))
            return "La contraseña es obligatoria";
        if (p.length() < 8)
            return "La contraseña debe tener al menos 8 caracteres";
        if (p.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
            return "La contraseña no puede superar los 72 caracteres";
        if (p.chars().noneMatch(Character::isLetter) || p.chars().noneMatch(Character::isDigit))
            return "La contraseña debe incluir al menos una letra y un número";
        return null;
    }

    /**
     * Escapa el texto para usarlo como literal dentro de un $regex de MongoDB ("("
     * no rompe la consulta).
     */
    public static String literalRegex(String s) {
        return s == null ? "" : s.replaceAll("[\\\\^$.|?*+()\\[\\]{}]", "\\\\$0");
    }

    // ── Nombres y correos ────────────────────────────────────────────────────

    /**
     * Letras (con tildes/ñ) separadas por UN espacio, apóstrofo o guion: "De la
     * Cruz", "O'Brien", "Ana-María".
     */
    private static final Pattern NOMBRE = Pattern.compile("^\\p{L}+(?:[ '\\-]\\p{L}+)*$");
    private static final Pattern EMAIL = Pattern.compile(
            "^[A-Za-z0-9]+(?:[._%+\\-][A-Za-z0-9]+)*@"
                    + "(?:[A-Za-z0-9](?:[A-Za-z0-9\\-]{0,61}[A-Za-z0-9])?\\.)+[A-Za-z]{2,}$");
    private static final Pattern GMAIL_USUARIO = Pattern.compile("^[a-z0-9]+(?:\\.[a-z0-9]+)*$");

    /** Errores de tipeo frecuentes → dominio correcto (se sugiere al usuario). */
    private static final Map<String, String> DOMINIOS_ERRADOS = Map.ofEntries(
            Map.entry("gmial.com", "gmail.com"), Map.entry("gmai.com", "gmail.com"),
            Map.entry("gamil.com", "gmail.com"), Map.entry("gmaill.com", "gmail.com"),
            Map.entry("gmail.con", "gmail.com"), Map.entry("gmail.cm", "gmail.com"),
            Map.entry("hotmial.com", "hotmail.com"), Map.entry("hotmal.com", "hotmail.com"),
            Map.entry("hotmail.con", "hotmail.com"), Map.entry("outlok.com", "outlook.com"),
            Map.entry("outlook.con", "outlook.com"), Map.entry("yahooo.com", "yahoo.com"),
            Map.entry("yahoo.con", "yahoo.com"));

    /** Recorta y unifica espacios internos. */
    public static String limpiarEspacios(String s) {
        return s == null ? null : s.trim().replaceAll("\\s+", " ");
    }

    /**
     * @param campo "nombre" o "apellido" (se usa en el mensaje). Retorna el error o
     *              null.
     */
    public static String errorNombre(String valor, String campo) {
        if (esVacio(valor))
            return "El " + campo + " es obligatorio";
        if (valor.length() < 2 || valor.length() > 50 || !NOMBRE.matcher(valor).matches())
            return "El " + campo + " debe tener entre 2 y 50 letras (sin números ni símbolos)";
        return null;
    }

    /**
     * Formato, longitud, dominio mal escrito y reglas de Gmail. Retorna el error o
     * null.
     */
    public static String errorEmail(String valor) {
        if (esVacio(valor))
            return "El correo electrónico es obligatorio";
        if (valor.length() > 100)
            return "El correo no puede superar los 100 caracteres";
        if (valor.indexOf('@') > 64 || !EMAIL.matcher(valor).matches())
            return "El correo electrónico no tiene un formato válido";

        int arroba = valor.indexOf('@');
        String local = valor.substring(0, arroba).toLowerCase();
        String dominio = valor.substring(arroba + 1).toLowerCase();

        if (local.chars().noneMatch(Character::isLetter))
            return "El correo no puede estar formado solo por números";

        String[] etiquetas = dominio.split("\\.");
        if (etiquetas[etiquetas.length - 2].chars().allMatch(Character::isDigit))
            return "El dominio del correo no es válido";

        String sugerido = DOMINIOS_ERRADOS.get(dominio);
        if (sugerido != null)
            return "El dominio del correo parece mal escrito. ¿Quisiste decir " + sugerido + "?";

        if (dominio.equals("gmail.com") || dominio.equals("googlemail.com")) {
            int mas = local.indexOf('+');
            String usuario = mas >= 0 ? local.substring(0, mas) : local;
            int largo = usuario.replace(".", "").length();
            if (!GMAIL_USUARIO.matcher(usuario).matches() || largo < 6 || largo > 30)
                return "Un correo de Gmail debe tener entre 6 y 30 letras o números antes del @ (se permiten puntos)";
        }
        return null;
    }
}
