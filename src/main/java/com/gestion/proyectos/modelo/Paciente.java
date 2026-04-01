package com.gestion.proyectos.modelo;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "pacientes")
public class Paciente {
    @Id
    private String id;
    private String nombre;
    private String apellido;
    private String telefono;
    private String identificacion;

    private String email;
    private String password;
    private String role = "PACIENTE"; // Default role

    public Paciente() {
    }

    public Paciente(String nombre, String apellido, String telefono, String identificacion, String email,
            String password) {
        this.nombre = nombre;
        this.apellido = apellido;
        this.telefono = telefono;
        this.identificacion = identificacion;
        this.email = email;
        this.password = password;
    }

    // Getters and Setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getApellido() {
        return apellido;
    }

    public void setApellido(String apellido) {
        this.apellido = apellido;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public String getIdentificacion() {
        return identificacion;
    }

    public void setIdentificacion(String identificacion) {
        this.identificacion = identificacion;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

}
