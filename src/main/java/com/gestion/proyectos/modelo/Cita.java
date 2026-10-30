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
    private String medioPago;
    private EstadoAbono estadoAbono;
    /** Cuándo el profesional registró que devolvió el abono. */
    private LocalDateTime abonoDevueltoEn;
    /** Horas antes de la cita en que vence el pago del resto: después la cita se cancela y se conserva el abono. */
    public static final int HORAS_LIMITE_SALDO = 24;

    /**
     * Resto de la consulta pagado en línea (en centavos); null mientras falte. Con él la cita queda pagada por
     * completo y el estado del abono aplica a todo lo pagado.
     */
    private Long saldoCentavos;
    /**
     * true en las citas reservadas con la regla de pagar todo antes de la cita: si el resto no se paga antes del
     * plazo, la cita se cancela. Las citas anteriores (null) siguen pagando el resto en el consultorio.
     */
    private Boolean saldoObligatorio;
    private boolean recordatorioSaldoEnviado;
    private String referenciaSaldo;
    private String medioPagoSaldo;
    private LocalDateTime saldoPagadoEn;

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

    public String getMedioPago() {
        return medioPago;
    }

    public void setMedioPago(String medioPago) {
        this.medioPago = medioPago;
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

    public LocalDateTime getAbonoDevueltoEn() {
        return abonoDevueltoEn;
    }

    public void setAbonoDevueltoEn(LocalDateTime abonoDevueltoEn) {
        this.abonoDevueltoEn = abonoDevueltoEn;
    }

    public Long getSaldoCentavos() {
        return saldoCentavos;
    }

    public void setSaldoCentavos(Long saldoCentavos) {
        this.saldoCentavos = saldoCentavos;
    }

    public Long getSaldoPesos() {
        return saldoCentavos == null ? null : saldoCentavos / 100;
    }

    public boolean isSaldoObligatorio() {
        return Boolean.TRUE.equals(saldoObligatorio);
    }

    public void setSaldoObligatorio(Boolean saldoObligatorio) {
        this.saldoObligatorio = saldoObligatorio;
    }

    public boolean isRecordatorioSaldoEnviado() {
        return recordatorioSaldoEnviado;
    }

    public void setRecordatorioSaldoEnviado(boolean recordatorioSaldoEnviado) {
        this.recordatorioSaldoEnviado = recordatorioSaldoEnviado;
    }

    /** Hasta cuándo se puede pagar el resto de una cita en la fecha y hora dadas. */
    public static LocalDateTime limitePagoSaldo(LocalDate fecha, LocalTime hora) {
        return fecha == null || hora == null ? null : fecha.atTime(hora).minusHours(HORAS_LIMITE_SALDO);
    }

    public LocalDateTime getLimitePagoSaldo() {
        return limitePagoSaldo(fecha, hora);
    }

    /** ¿Le falta pagar el resto y ya pasó el plazo? Entonces la cita se cancela. */
    public boolean saldoVencido(LocalDateTime ahora) {
        LocalDateTime limite = getLimitePagoSaldo();
        return isSaldoObligatorio() && !isPagadaCompleta() && estado == EstadoCita.PENDIENTE
                && limite != null && !ahora.isBefore(limite);
    }

    /** ¿El paciente ya pagó en línea el valor total de la consulta? */
    public boolean isPagadaCompleta() {
        return saldoCentavos != null;
    }

    /** Todo lo que el paciente pagó en línea (abono más saldo), en pesos; null si no pagó nada. */
    public Long getPagadoPesos() {
        if (abonoCentavos == null && saldoCentavos == null)
            return null;
        return ((abonoCentavos == null ? 0 : abonoCentavos) + (saldoCentavos == null ? 0 : saldoCentavos)) / 100;
    }

    public String getReferenciaSaldo() {
        return referenciaSaldo;
    }

    public void setReferenciaSaldo(String referenciaSaldo) {
        this.referenciaSaldo = referenciaSaldo;
    }

    public String getMedioPagoSaldo() {
        return medioPagoSaldo;
    }

    public void setMedioPagoSaldo(String medioPagoSaldo) {
        this.medioPagoSaldo = medioPagoSaldo;
    }

    public LocalDateTime getSaldoPagadoEn() {
        return saldoPagadoEn;
    }

    public void setSaldoPagadoEn(LocalDateTime saldoPagadoEn) {
        this.saldoPagadoEn = saldoPagadoEn;
    }

    public LocalDateTime getInicioAtencion() {
        return inicioAtencion;
    }

    public void setInicioAtencion(LocalDateTime inicioAtencion) {
        this.inicioAtencion = inicioAtencion;
    }

}
