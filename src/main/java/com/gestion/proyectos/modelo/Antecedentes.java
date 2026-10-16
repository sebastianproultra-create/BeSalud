package com.gestion.proyectos.modelo;

import java.util.regex.Pattern;

import lombok.Getter;
import lombok.Setter;

/** Antecedentes del paciente (sección S del dictamen). Todos son opcionales. */
@Getter
@Setter
public class Antecedentes {

    /** Respuestas que significan "sin alergias" y no deben resaltarse. */
    private static final Pattern SIN_ALERGIAS = Pattern.compile(
            "(?iu)^\\s*(no|ninguna?s?|niega|negativo|no refiere|no conocidas?|n/?a|-)\\s*\\.?\\s*$");

    private String patologicos;
    private String quirurgicos;
    private String farmacologicos;
    private String alergicos;
    private String familiares;

    /** true si el campo de alergias tiene contenido real (se resalta en rojo). */
    public boolean isTieneAlergias() {
        return alergicos != null && !alergicos.isBlank() && !SIN_ALERGIAS.matcher(alergicos).matches();
    }

    public boolean isVacio() {
        return vacio(patologicos) && vacio(quirurgicos) && vacio(farmacologicos)
                && vacio(alergicos) && vacio(familiares);
    }

    private static boolean vacio(String s) {
        return s == null || s.isBlank();
    }
}
