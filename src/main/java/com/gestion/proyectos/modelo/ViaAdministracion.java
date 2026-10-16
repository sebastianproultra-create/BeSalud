package com.gestion.proyectos.modelo;

public enum ViaAdministracion {
    ORAL("Oral"),
    INTRAVENOSA("Intravenosa"),
    INTRAMUSCULAR("Intramuscular"),
    SUBCUTANEA("Subcutánea"),
    TOPICA("Tópica"),
    INHALADA("Inhalada"),
    OFTALMICA("Oftálmica"),
    OTRA("Otra");

    private final String descripcion;

    ViaAdministracion(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
