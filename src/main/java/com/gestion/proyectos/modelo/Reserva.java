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
 *
 * Una reserva de tipo SALDO es el pago del resto de la consulta para una cita que ya existe (citaId):
 * no aparta turno y, si no se paga, la cita sigue igual con su abono.
 */
@Document(collection = "reservas")
public class Reserva {

    public enum Estado { ESPERANDO_PAGO, PROCESANDO, APROBADA, RECHAZADA, EXPIRADA, CONFLICTO }

    public enum Tipo { ABONO, SALDO }

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
    /**
     * Al reservar: el abono y el valor total de la consulta, para que el paciente elija cuánto paga. null en las
     * reservas anteriores (pagaban solo el abono, que es montoCentavos).
     */
    private Long abonoCentavos;
    private Long precioCentavos;
    /** El paciente eligió pagar todo al reservar (o la cita es tan pronto que no hay tiempo de pagar después). */
    private boolean pagoTotal;
    private Estado estado = Estado.ESPERANDO_PAGO;
    /** null en las reservas anteriores a este campo: eran todas de abono. */
    private Tipo tipo;
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
    /** Código de autorización del banco y momento en que se aprobó el pago, para el comprobante. */
    private String codigoAutorizacion;
    private LocalDateTime pagadaEn;
    /** Igual que en la cita: "doctorId|fecha|hora" solo mientras espera el pago, con índice único. */
    @Indexed(unique = true, sparse = true)
    private String turno;

    public void actualizarTurno() {
        turno = !isSaldo() && estado == Estado.ESPERANDO_PAGO && doctorId != null && fecha != null && hora != null
                ? Cita.claveTurno(doctorId, fecha, hora) : null;
    }

    public Long getAbonoCentavos() {
        return abonoCentavos;
    }

    public void setAbonoCentavos(Long abonoCentavos) {
        this.abonoCentavos = abonoCentavos;
    }

    public Long getPrecioCentavos() {
        return precioCentavos;
    }

    public void setPrecioCentavos(Long precioCentavos) {
        this.precioCentavos = precioCentavos;
    }

    public boolean isPagoTotal() {
        return pagoTotal;
    }

    public void setPagoTotal(boolean pagoTotal) {
        this.pagoTotal = pagoTotal;
    }

    /** ¿Se puede elegir entre abono y total? Solo al reservar y si el total es mayor que el abono. */
    public boolean admiteElegirMonto() {
        return !isSaldo() && abonoCentavos != null && precioCentavos != null && precioCentavos > abonoCentavos;
    }

    /** ¿La cita es tan pronto que el resto ya no se podría pagar a tiempo? Entonces se paga todo al reservar. */
    public boolean debePagarTotal(LocalDateTime ahora) {
        LocalDateTime limite = Cita.limitePagoSaldo(fecha, hora);
        return limite != null && !ahora.plusHours(HORAS_MARGEN_TOTAL).isBefore(limite);
    }

    /** Margen antes del plazo del resto: si quedan menos horas que esto, se cobra todo al reservar. */
    public static final int HORAS_MARGEN_TOTAL = 2;

    public Tipo getTipo() {
        return tipo == null ? Tipo.ABONO : tipo;
    }

    public void setTipo(Tipo tipo) {
        this.tipo = tipo;
    }

    public boolean isSaldo() {
        return tipo == Tipo.SALDO;
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

    public String getCodigoAutorizacion() {
        return codigoAutorizacion;
    }

    public void setCodigoAutorizacion(String codigoAutorizacion) {
        this.codigoAutorizacion = codigoAutorizacion;
    }

    public LocalDateTime getPagadaEn() {
        return pagadaEn;
    }

    public void setPagadaEn(LocalDateTime pagadaEn) {
        this.pagadaEn = pagadaEn;
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
