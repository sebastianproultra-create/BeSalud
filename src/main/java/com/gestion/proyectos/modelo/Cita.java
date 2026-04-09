package com.gestion.proyectos.modelo;

import java.time.LocalDate;
import java.time.LocalTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "citas")
public class Cita {

    @Id
    private String id;
    private String doctorId;
    private String pacienteId;
    private LocalTime Hora;
    private LocalDate fecha;
    private String motivo;

    private EstadoCita estado = EstadoCita.PENDIENTE;
    private Dictamen dictamen;

    public Cita() {
    }

    public Cita(String doctorId, String pacienteId, LocalTime hora, LocalDate fecha, String motivo, EstadoCita estado) {
        this.doctorId = doctorId;
        this.pacienteId = pacienteId;
        Hora = hora;
        this.fecha = fecha;
        this.motivo = motivo;
        this.estado = estado;
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

    public String getPacienteId() {
        return pacienteId;
    }

    public void setPacienteId(String pacienteId) {
        this.pacienteId = pacienteId;
    }

    public LocalTime getHora() {
        return Hora;
    }

    public void setHora(LocalTime hora) {
        Hora = hora;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }

    public EstadoCita getEstado() {
        return estado;
    }

    public void setEstado(EstadoCita estado) {
        this.estado = estado;
    }

    public Dictamen getDictamen() {
        return dictamen;
    }

    public void setDictamen(Dictamen dictamen) {
        this.dictamen = dictamen;
    }

}
