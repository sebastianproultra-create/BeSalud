package com.gestion.proyectos.modelo;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "horariosAtencion")
public class HorarioAtencion {

    @Id
    private String id;
    private String doctorId;
    private LocalDateTime inicio;
    private LocalDateTime fin;
    private int duracionCitaMinutos = 30; // Default 30 minutes

    public HorarioAtencion() {
    }

    public HorarioAtencion(String doctorId, LocalDateTime inicio, LocalDateTime fin) {
        this.doctorId = doctorId;
        this.inicio = inicio;
        this.fin = fin;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDoctorId() {
        return doctorId;
    }

    public void setDoctorId(String doctorId) {
        this.doctorId = doctorId;
    }

    public LocalDateTime getInicio() {
        return inicio;
    }

    public void setInicio(LocalDateTime inicio) {
        this.inicio = inicio;
    }

    public LocalDateTime getFin() {
        return fin;
    }

    public void setFin(LocalDateTime fin) {
        this.fin = fin;
    }

    public int getDuracionCitaMinutos() {
        return duracionCitaMinutos;
    }

    public void setDuracionCitaMinutos(int duracionCitaMinutos) {
        this.duracionCitaMinutos = duracionCitaMinutos;
    }

}