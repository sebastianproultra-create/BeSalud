
package com.gestion.proyectos.modelo;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

// Esta clase representa la colección proyecto en MongoDB
@Document(collection = "proyectos")
public class Proyecto {

    // Identificador único del proyecto
    @Id
    private String id;

    // Nombre del proyecto
    private String nombre;

    // Descripción del proyecto
    private String descripcion;

    // Fecha en la que inicia el proyecto
    private LocalDate fechaInicio;

    // Lista de tareas anidadas dentro del proyecto (ventaja de MongoDB)
    private List<Tarea> tareas = new ArrayList<>();

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

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public LocalDate getFechaInicio() {
        return fechaInicio;
    }

    public void setFechaInicio(LocalDate fechaInicio) {
        this.fechaInicio = fechaInicio;
    }

    public List<Tarea> getTareas() {
        return tareas;
    }

    public void setTareas(List<Tarea> tareas) {
        this.tareas = tareas;
    }
}
