package com.gestion.proyectos.util;

public final class ValidacionUtil {

    private ValidacionUtil() {}

    /** Retorna true si el string es null o solo espacios en blanco. */
    public static boolean esVacio(String s) {
        return s == null || s.isBlank();
    }
}
