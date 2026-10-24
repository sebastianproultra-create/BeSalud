package com.gestion.proyectos.modelo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "citas")
public class Cita {

    @Id
    private String id;
    @Indexed
    private String doctorId;
    @Indexed
    private String pacienteId;
    private LocalTime hora;
    private LocalDate fecha;
    private String motivo;

    private EstadoCita estado = EstadoCita.PENDIENTE;
    private Dictamen dictamen;
    /** Momento en que el médico marcó la asistencia (inicio real de la atención). */
    private LocalDateTime inicioAtencion;
    private boolean recordatorioEnviado;
    /** Respuesta del paciente a "¿Vas a ir a tu cita?": true asistirá, null aún no responde. */
    private Boolean asistenciaConfirmada;
    private boolean confirmacionSolicitada;

    /**
     * "doctorId|fecha|hora" mientras la cita ocupa el turno (pendiente, en atención o completada); vacío si
     * se canceló o el paciente no asistió. El índice único hace que Mongo rechace dos citas en el mismo
     * turno aunque lleguen al mismo tiempo. Lo calcula {@link #actualizarTurno()} antes de cada guardado.
     */
    @Indexed(unique = true, sparse = true)
    private String turno;

    /** Abono pagado al reservar (en centavos, como lo maneja la pasarela); null si la cita no tuvo abono. */
    private Long abonoCentavos;
    private String referenciaPago;
    private EstadoAbono estadoAbono;

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
        return hora;
    }

    public void setHora(LocalTime hora) {
        this.hora = hora;
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

    public boolean isRecordatorioEnviado() {
        return recordatorioEnviado;
    }

    public void setRecordatorioEnviado(boolean recordatorioEnviado) {
        this.recordatorioEnviado = recordatorioEnviado;
    }

    public Boolean getAsistenciaConfirmada() {
        return asistenciaConfirmada;
    }

    public void setAsistenciaConfirmada(Boolean asistenciaConfirmada) {
        this.asistenciaConfirmada = asistenciaConfirmada;
    }

    public boolean isConfirmacionSolicitada() {
        return confirmacionSolicitada;
    }

    public void setConfirmacionSolicitada(boolean confirmacionSolicitada) {
        this.confirmacionSolicitada = confirmacionSolicitada;
    }

    public static String claveTurno(String doctorId, LocalDate fecha, LocalTime hora) {
        return doctorId + "|" + fecha + "|" + hora.withSecond(0).withNano(0);
    }

    public boolean ocupaTurno() {
        return estado == EstadoCita.PENDIENTE || estado == EstadoCita.ASISTIO || estado == EstadoCita.COMPLETADA;
    }

    public void actualizarTurno() {
        turno = ocupaTurno() && doctorId != null && fecha != null && hora != null
                ? claveTurno(doctorId, fecha, hora) : null;
    }

    public String getTurno() {
        return turno;
    }

    public Long getAbonoCentavos() {
        return abonoCentavos;
    }

    public void setAbonoCentavos(Long abonoCentavos) {
        this.abonoCentavos = abonoCentavos;
    }

    /** Abono en pesos para mostrar en las vistas. */
    public Long getAbonoPesos() {
        return abonoCentavos == null ? null : abonoCentavos / 100;
    }

    public String getReferenciaPago() {
        return referenciaPago;
    }

    public void setReferenciaPago(String referenciaPago) {
        this.referenciaPago = referenciaPago;
    }

    public EstadoAbono getEstadoAbono() {
        return estadoAbono;
    }

    public void setEstadoAbono(EstadoAbono estadoAbono) {
        this.estadoAbono = estadoAbono;
    }

    public LocalDateTime getInicioAtencion() {
        return inicioAtencion;
    }

    public void setInicioAtencion(LocalDateTime inicioAtencion) {
        this.inicioAtencion = inicioAtencion;
    }

}
