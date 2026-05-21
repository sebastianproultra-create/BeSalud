package com.gestion.proyectos.modelo;

import java.time.DayOfWeek;
import java.time.LocalTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "horariosAtencion")
public class HorarioAtencion {

    @Id
    private String id;
    @Indexed
    private String doctorId;
    private DayOfWeek diaSemana;
    private LocalTime horaInicio;
    private LocalTime horaFin;
    private int duracionCitaMinutos = 30; // Default 30 minutes

    public HorarioAtencion() {
    }

    public HorarioAtencion(String doctorId, DayOfWeek diaSemana, LocalTime horaInicio, LocalTime horaFin) {
        this.doctorId = doctorId;
        this.diaSemana = diaSemana;
        this.horaInicio = horaInicio;
        this.horaFin = horaFin;
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

    public DayOfWeek getDiaSemana() {
        return diaSemana;
    }

    public void setDiaSemana(DayOfWeek diaSemana) {
        this.diaSemana = diaSemana;
    }

    public LocalTime getHoraInicio() {
        return horaInicio;
    }

    public void setHoraInicio(LocalTime horaInicio) {
        this.horaInicio = horaInicio;
    }

    public LocalTime getHoraFin() {
        return horaFin;
    }

    public void setHoraFin(LocalTime horaFin) {
        this.horaFin = horaFin;
    }

    public int getDuracionCitaMinutos() {
        return duracionCitaMinutos;
    }

    public void setDuracionCitaMinutos(int duracionCitaMinutos) {
        this.duracionCitaMinutos = duracionCitaMinutos;
    }

}