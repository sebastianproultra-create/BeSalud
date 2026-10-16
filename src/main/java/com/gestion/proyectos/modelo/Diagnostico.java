package com.gestion.proyectos.modelo;

import lombok.Getter;
import lombok.Setter;

/** Diagnóstico codificado con CIE-10 (sección A). */
@Getter
@Setter
public class Diagnostico {

    private String codigo;      // CIE-10, ej. J039
    private String descripcion; // ej. Amigdalitis aguda
    private TipoDiagnostico tipo;

    public boolean isVacio() {
        return (codigo == null || codigo.isBlank()) && (descripcion == null || descripcion.isBlank()) && tipo == null;
    }
}
