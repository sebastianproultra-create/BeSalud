package com.gestion.proyectos.modelo;

/** Se le devolvió al paciente un pago que no pudo aplicarse a una cita (por ejemplo, el turno ya no estaba). */
public record DevolucionSinCitaEvento(Reserva reserva, String medio, String numeroOperacion) {
}
