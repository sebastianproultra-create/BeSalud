package com.gestion.proyectos.api;

// JSON que envía la web del consultorio a POST /api/v1/pacientes.
public record RegistroApiRequest(
        String nombre,
        String apellido,
        String identificacion,
        String telefono,
        String email,
        String password,
        boolean aceptaPrivacidad,
        String doctorId,
        String consultorioId) {
}
