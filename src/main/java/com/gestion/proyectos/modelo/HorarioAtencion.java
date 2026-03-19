package com.gestion.proyectos.modelo;

import java.time.LocalDate;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "HorariosAtencion")
public class HorarioAtencion {

    @Id
    private String id;
    private LocalDate horarioInicio;
    private LocalDate horarioFin;

    public HorarioAtencion() {

    }

    public HorarioAtencion(LocalDate horarioInicio, LocalDate horarioFin) {

        this.horarioInicio = horarioInicio;

        this.horarioFin = horarioFin;

    }

    // Getters and Setters

    public String getId() {

        return id;

    }

    public void setId(String id) {

        this.id = id;

    }

    public LocalDate getHorarioInicio() {

        return horarioInicio;

    }

    public void setHorarioInicio(LocalDate horarioInicio) {

        this.horarioInicio = horarioInicio;

    }

    public LocalDate getHorarioFin() {

        return horarioFin;

    }

    public void setHorarioFin(LocalDate horarioFin) {

        this.horarioFin = horarioFin;

    }

}