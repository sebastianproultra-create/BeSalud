package com.gestion.proyectos.modelo;

public record ResultadoTriage(
        String especialidad,
        String prioridad,
        boolean emergencia,
        String resumen,
        String recomendacion,
        boolean generadoPorIa) {
}
