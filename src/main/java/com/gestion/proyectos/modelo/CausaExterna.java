package com.gestion.proyectos.modelo;

/** Causa externa de la consulta (requerida por la normativa colombiana de RIPS). */
public enum CausaExterna {
    ENFERMEDAD_GENERAL("Enfermedad general"),
    ACCIDENTE_TRABAJO("Accidente de trabajo"),
    ACCIDENTE_TRANSITO("Accidente de tránsito"),
    OTRA("Otra");

    private final String descripcion;

    CausaExterna(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
