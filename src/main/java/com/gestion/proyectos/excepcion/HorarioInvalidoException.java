package com.gestion.proyectos.excepcion;

/**
 * Excepción lanzada cuando se intenta agendar una cita en un horario inválido
 */
public class HorarioInvalidoException extends CitaMedicaException {

    public HorarioInvalidoException(String message) {
        super(message, "HORARIO_INVALIDO");
    }

    public HorarioInvalidoException() {
        super("El horario seleccionado no está disponible para este médico", "HORARIO_INVALIDO");
    }
}