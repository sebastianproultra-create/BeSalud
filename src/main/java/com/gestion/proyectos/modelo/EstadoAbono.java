package com.gestion.proyectos.modelo;

/** Qué pasa con el abono de una cita. El reembolso lo hace el profesional; aquí solo queda indicado. */
public enum EstadoAbono {
    PAGADO("Abono pagado"),
    A_REEMBOLSAR("Abono a reembolsar"),
    RETENIDO("Abono retenido");

    private final String descripcion;

    EstadoAbono(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
