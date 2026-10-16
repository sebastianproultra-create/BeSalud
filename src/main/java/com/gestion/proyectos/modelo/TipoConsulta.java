package com.gestion.proyectos.modelo;

public enum TipoConsulta {
    PRIMERA_VEZ("Primera vez"),
    CONTROL("Control"),
    URGENCIA("Urgencia"),
    LECTURA_EXAMENES("Lectura de exámenes");

    private final String descripcion;

    TipoConsulta(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
