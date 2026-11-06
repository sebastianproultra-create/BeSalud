package com.gestion.proyectos.api;

// JSON que envía la web del consultorio a POST /api/v1/sesion ("Ya tengo cuenta").
public record SesionApiRequest(
        String email,
        String password,
        String doctorId,
        String consultorioId) {
}
