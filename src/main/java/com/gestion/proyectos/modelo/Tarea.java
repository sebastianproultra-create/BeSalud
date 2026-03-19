package com.gestion.proyectos.modelo;

// Esta clase representa una tarea dentro de un proyecto
// En MongoDB se guarda como documento anidado (no necesita su propia colección)
public class Tarea {

    private String titulo;
    private String estado; // pendiente, en progreso, completada

    public Tarea() {
    }

    public Tarea(String titulo, String estado) {
        this.titulo = titulo;
        this.estado = estado;
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }
}
