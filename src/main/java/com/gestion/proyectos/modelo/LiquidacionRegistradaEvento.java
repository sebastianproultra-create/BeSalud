package com.gestion.proyectos.modelo;

/** BeSalud registró un pago a un médico. */
public record LiquidacionRegistradaEvento(String doctorId, String codigo, long netoPesos, int citas, String medio,
        String numeroOperacion) {
}
