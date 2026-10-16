package com.gestion.proyectos.modelo;

public enum Veredicto {
    FAVORABLE("Favorable"),
    FAVORABLE_CON_SALVEDADES("Favorable con salvedades"),
    DESFAVORABLE("Desfavorable");

    private final String descripcion;

    Veredicto(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
