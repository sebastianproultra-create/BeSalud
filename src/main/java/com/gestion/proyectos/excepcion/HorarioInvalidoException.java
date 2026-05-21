package com.gestion.proyectos.excepcion;

/**
 * Excepción lanzada cuando se intenta agendar una cita en un horario inválido
 */
public class HorarioInvalidoException extends RuntimeException {

    private final String codigo;

    public HorarioInvalidoException(String message) {
        super(message);
        this.codigo = "HORARIO_INVALIDO";
    }

    public HorarioInvalidoException() {
        super("El horario seleccionado no está disponible para este médico");
        this.codigo = "HORARIO_INVALIDO";
    }

    public String getCodigo() {
        return codigo;
    }
}
