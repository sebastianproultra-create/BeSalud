package com.gestion.proyectos.modelo;

/** Qué pasa con lo pagado por una cita. La devolución la hace BeSalud y queda probada en Devolucion. */
public enum EstadoAbono {
    PAGADO("Abono pagado"),
    A_REEMBOLSAR("Abono a reembolsar"),
    RETENIDO("Abono retenido"),
    DEVUELTO("Abono devuelto");

    private final String descripcion;

    EstadoAbono(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
