package com.gestion.proyectos.modelo;

public enum TipoDiagnostico {
    IMPRESION_DIAGNOSTICA("Impresión diagnóstica (presuntivo)"),
    CONFIRMADO_NUEVO("Confirmado nuevo"),
    CONFIRMADO_REPETIDO("Confirmado repetido");

    private final String descripcion;

    TipoDiagnostico(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
