package com.gestion.proyectos.modelo;

import java.time.LocalDate;

import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Getter;
import lombok.Setter;

@Document(collection = "personas")
@Getter
@Setter
public class Doctor extends Persona {

    private String especialidad;
    private LocalDate fechaNacimiento;
    private String foto;
    private String biografia;
    private String estado = "INACTIVO"; // ACTIVO o INACTIVO

    public Doctor() {
        setRole("DOCTOR");
    }

    public Doctor(String nombre, String apellido, String telefono, String identificacion, String email, String password,
            String especialidad, LocalDate fechaNacimiento, String foto, String biografia) {
        setNombre(nombre);
        setApellido(apellido);
        setTelefono(telefono);
        setIdentificacion(identificacion);
        setEmail(email);
        setPassword(password);
        setRole("DOCTOR");
        this.especialidad = especialidad;
        this.fechaNacimiento = fechaNacimiento;
        this.foto = foto;
        this.biografia = biografia;
    }
}
