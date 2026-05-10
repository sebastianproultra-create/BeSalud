package com.gestion.proyectos.modelo;

import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Getter;
import lombok.Setter;

@Document(collection = "personas")
@Getter
@Setter
public class Paciente extends Persona {

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
