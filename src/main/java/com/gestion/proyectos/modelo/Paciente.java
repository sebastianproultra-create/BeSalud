package com.gestion.proyectos.modelo;

import org.springframework.data.mongodb.core.index.Indexed;
import java.time.LocalDateTime;

import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Getter;
import lombok.Setter;

@Document(collection = "personas")
@Getter
@Setter
public class Paciente extends Persona {

    /** Historial previo que el propio paciente declara (al agendar o en "Mi historial"); el médico lo ve. */
    private Antecedentes historialPrevio;
    /** Otros datos que quiera contar: enfermedades crónicas, exámenes recientes, tratamientos en curso. */
    private String historialPrevioNotas;
    private LocalDateTime historialPrevioActualizado;

    // id del consultorio cuya web lo registró (null si se registró en el portal de BeSalud).
    @Indexed(sparse = true)
    private String consultorioOrigen;

    // Médico del consultorio por el que entró. El paciente solo ve el perfil de este médico.
    private String doctorAsignado;

    public Paciente() {
        setRole("PACIENTE");
    }

    public Paciente(String nombre, String apellido, String telefono, String identificacion, String email,
            String password) {
        setNombre(nombre);
        setApellido(apellido);
        setTelefono(telefono);
        setIdentificacion(identificacion);
        setEmail(email);
        setPassword(password);
        setRole("PACIENTE");
    }
}
