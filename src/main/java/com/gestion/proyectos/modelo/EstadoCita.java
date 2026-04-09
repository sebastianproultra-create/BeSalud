package com.gestion.proyectos.modelo;

public enum EstadoCita {
    PENDIENTE("Pendiente"),
    ASISTIO("Asistió"),
    COMPLETADA("Completada"),
    CANCELADA("Cancelada"),
    NO_ASISTIO("No Asistió");

    private final String descripcion;

    EstadoCita(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
