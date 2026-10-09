package com.gestion.proyectos.modelo;

public record CitaEvento(Tipo tipo, String citaId) {

    public enum Tipo { CREADA, REPROGRAMADA, CANCELADA }
}
