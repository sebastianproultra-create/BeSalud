package com.gestion.proyectos.modelo;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Constancia de que BeSalud le devolvió a un paciente lo que pagó por una cita cancelada: quién la hizo, por qué
 * medio, con qué número de operación y el comprobante del banco o de la pasarela. El paciente confirma que le llegó
 * o avisa que no; en ese caso vuelve a quedar pendiente. Una por origen: la cita cancelada o el pago que no pudo aplicarse.
 */
@Document(collection = "devoluciones")
public class Devolucion {

    public enum Estado {
        REGISTRADA("Por confirmar", "warning"),
        CONFIRMADA("Confirmada por el paciente", "success"),
        RECLAMADA("El paciente dice que no le llegó", "danger");

        private final String texto;
        private final String tono;

        Estado(String texto, String tono) {
            this.texto = texto;
            this.tono = tono;
        }

        public String getTexto() {
            return texto;
        }

        public String getTono() {
            return tono;
        }
    }

    /** Una línea de la trazabilidad: cuándo pasó, quién lo hizo y qué pasó. */
    public record Evento(LocalDateTime fecha, String quien, String texto) {
    }

    @Id
    private String id;
    @Indexed(unique = true)
    private String codigo;
    /** Qué se devuelve: "cita-ID" o "reserva-REFERENCIA". */
    @Indexed(unique = true)
    private String origen;
    /** La cita cancelada; null si el pago nunca llegó a tener cita. */
    private String citaId;
    @Indexed
    private String pacienteId;
    private String doctorId;
    /** Referencias de los pagos que se devuelven: el abono y, si lo pagó, el saldo. */
    private List<String> referencias = new ArrayList<>();
    private long montoCentavos;
    private String medio;
    /** Número que da el banco o la pasarela. Único: el mismo pago no sirve de prueba para dos devoluciones. */
    @Indexed(unique = true)
    private String numeroOperacion;
    private byte[] comprobante;
    private String comprobanteNombre;
    private String comprobanteTipo;
    /** SHA-256 del comprobante: delata el mismo archivo usado en otra devolución o cambiado después. */
    @Indexed
    private String comprobanteHash;
    private String registradaPor;
    private LocalDateTime registradaEn;
    private Estado estado = Estado.REGISTRADA;
    private LocalDateTime confirmadaEn;
    private String notaReclamo;
    private List<Evento> eventos = new ArrayList<>();

    public long getMontoPesos() {
        return montoCentavos / 100;
    }

    public boolean isComprobantePdf() {
        return "application/pdf".equals(comprobanteTipo);
    }

    public boolean isPorConfirmar() {
        return estado == Estado.REGISTRADA;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public String getOrigen() {
        return origen;
    }

    public void setOrigen(String origen) {
        this.origen = origen;
    }

    public String getCitaId() {
        return citaId;
    }

    public void setCitaId(String citaId) {
        this.citaId = citaId;
    }

    public String getPacienteId() {
        return pacienteId;
    }

    public void setPacienteId(String pacienteId) {
        this.pacienteId = pacienteId;
    }

    public String getDoctorId() {
        return doctorId;
    }

    public void setDoctorId(String doctorId) {
        this.doctorId = doctorId;
    }

    public List<String> getReferencias() {
        return referencias;
    }

    public void setReferencias(List<String> referencias) {
        this.referencias = referencias;
    }

    public long getMontoCentavos() {
        return montoCentavos;
    }

    public void setMontoCentavos(long montoCentavos) {
        this.montoCentavos = montoCentavos;
    }

    public String getMedio() {
        return medio;
    }

    public void setMedio(String medio) {
        this.medio = medio;
    }

    public String getNumeroOperacion() {
        return numeroOperacion;
    }

    public void setNumeroOperacion(String numeroOperacion) {
        this.numeroOperacion = numeroOperacion;
    }

    public byte[] getComprobante() {
        return comprobante;
    }

    public void setComprobante(byte[] comprobante) {
        this.comprobante = comprobante;
    }

    public String getComprobanteNombre() {
        return comprobanteNombre;
    }

    public void setComprobanteNombre(String comprobanteNombre) {
        this.comprobanteNombre = comprobanteNombre;
    }

    public String getComprobanteTipo() {
        return comprobanteTipo;
    }

    public void setComprobanteTipo(String comprobanteTipo) {
        this.comprobanteTipo = comprobanteTipo;
    }

    public String getComprobanteHash() {
        return comprobanteHash;
    }

    public void setComprobanteHash(String comprobanteHash) {
        this.comprobanteHash = comprobanteHash;
    }

    public String getRegistradaPor() {
        return registradaPor;
    }

    public void setRegistradaPor(String registradaPor) {
        this.registradaPor = registradaPor;
    }

    public LocalDateTime getRegistradaEn() {
        return registradaEn;
    }

    public void setRegistradaEn(LocalDateTime registradaEn) {
        this.registradaEn = registradaEn;
    }

    public Estado getEstado() {
        return estado;
    }

    public void setEstado(Estado estado) {
        this.estado = estado;
    }

    public LocalDateTime getConfirmadaEn() {
        return confirmadaEn;
    }

    public void setConfirmadaEn(LocalDateTime confirmadaEn) {
        this.confirmadaEn = confirmadaEn;
    }

    public String getNotaReclamo() {
        return notaReclamo;
    }

    public void setNotaReclamo(String notaReclamo) {
        this.notaReclamo = notaReclamo;
    }

    public List<Evento> getEventos() {
        return eventos;
    }

    public void setEventos(List<Evento> eventos) {
        this.eventos = eventos;
    }
}
