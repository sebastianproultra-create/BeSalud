package com.gestion.proyectos.modelo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Turno apartado mientras el paciente paga el abono. La cita solo se crea cuando la pasarela
 * confirma el pago; si no paga a tiempo, el turno se libera.
 */
@Document(collection = "reservas")
public class Reserva {

    public enum Estado { ESPERANDO_PAGO, PROCESANDO, APROBADA, RECHAZADA, EXPIRADA, CONFLICTO }

    @Id
    private String id;
    @Indexed(unique = true)
    private String referencia;
    @Indexed
    private String doctorId;
    @Indexed
    private String pacienteId;
    private LocalDate fecha;
    private LocalTime hora;
    private String motivo;
    private long montoCentavos;
    private Estado estado = Estado.ESPERANDO_PAGO;
    private LocalDateTime creadaEn;
    private LocalDateTime expiraEn;
    private String transaccionId;
    private String citaId;
    /** Cómo pagó, para mostrarlo en el comprobante: "Visa •••• 4242", "PSE · Bancolombia", "Nequi •••• 1111". */
    private String medioPago;
    /** CARD, PSE, NEQUI o BANCOLOMBIA_TRANSFER, como los nombra Wompi. */
    private String tipoMedio;
    /** Solo en la pasarela de pruebas: cómo termina un pago que queda en proceso y desde cuándo. */
    private String simulacionEstado;
    private LocalDateTime simulacionListaEn;
    /** Igual que en la cita: "doctorId|fecha|hora" solo mientras espera el pago, con índice único. */
    @Indexed(unique = true, sparse = true)
    private String turno;

    public void actualizarTurno() {
        turno = estado == Estado.ESPERANDO_PAGO && doctorId != null && fecha != null && hora != null
                ? Cita.claveTurno(doctorId, fecha, hora) : null;
    }

    public boolean vencida(LocalDateTime ahora) {
        return expiraEn != null && expiraEn.isBefore(ahora);
    }

    public String getMedioPago() {
        return medioPago;
    }

    public void setMedioPago(String medioPago) {
        this.medioPago = medioPago;
    }

    public String getTipoMedio() {
        return tipoMedio;
    }

    public void setTipoMedio(String tipoMedio) {
        this.tipoMedio = tipoMedio;
    }

    public String getSimulacionEstado() {
        return simulacionEstado;
    }

    public void setSimulacionEstado(String simulacionEstado) {
        this.simulacionEstado = simulacionEstado;
    }

    public LocalDateTime getSimulacionListaEn() {
        return simulacionListaEn;
    }

    public void setSimulacionListaEn(LocalDateTime simulacionListaEn) {
        this.simulacionListaEn = simulacionListaEn;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getReferencia() {
        return referencia;
    }

    public void setReferencia(String referencia) {
        this.referencia = referencia;
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

    public LocalDate getFecha() {
        return fecha;
    }

    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }

    public LocalTime getHora() {
        return hora;
    }

    public void setHora(LocalTime hora) {
        this.hora = hora;
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }

    public long getMontoCentavos() {
        return montoCentavos;
    }

    public void setMontoCentavos(long montoCentavos) {
        this.montoCentavos = montoCentavos;
    }

    public long getMontoPesos() {
        return montoCentavos / 100;
    }

    public Estado getEstado() {
        return estado;
    }

    public void setEstado(Estado estado) {
        this.estado = estado;
    }

    public LocalDateTime getCreadaEn() {
        return creadaEn;
    }

    public void setCreadaEn(LocalDateTime creadaEn) {
        this.creadaEn = creadaEn;
    }

    public LocalDateTime getExpiraEn() {
        return expiraEn;
    }

    public void setExpiraEn(LocalDateTime expiraEn) {
        this.expiraEn = expiraEn;
    }

    public String getTransaccionId() {
        return transaccionId;
    }

    public void setTransaccionId(String transaccionId) {
        this.transaccionId = transaccionId;
    }

    public String getCitaId() {
        return citaId;
    }

    public void setCitaId(String citaId) {
        this.citaId = citaId;
    }

    public String getTurno() {
        return turno;
    }
}
