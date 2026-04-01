package com.gestion.proyectos.modelo;

public class Dictamen {

    private String id;

    private String observaciones;

    public Dictamen() {
    }

    public Dictamen(String id, String observaciones) {
        this.id = id;
        this.observaciones = observaciones;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }

}
