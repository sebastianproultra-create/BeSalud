package com.gestion.proyectos.modelo;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Archivo que el paciente adjunta a su historial (dictámenes de otros médicos, exámenes, fórmulas). Lo ven él
 * y los médicos con los que tiene o tuvo cita. Se guarda en MongoDB: como máximo 5 MB, muy por debajo del límite
 * de 16 MB por documento.
 */
@Document(collection = "documentosClinicos")
public class DocumentoClinico {

    @Id
    private String id;
    @Indexed
    private String pacienteId;
    /** Nombre del archivo, ya limpio, para mostrarlo y al descargarlo. */
    private String nombre;
    /** application/pdf, image/jpeg o image/png, según el contenido real del archivo. */
    private String tipo;
    private long tamano;
    private byte[] contenido;
    private LocalDateTime subidoEn;

    public boolean isPdf() {
        return "application/pdf".equals(tipo);
    }

    /** Tamaño legible: "850 KB", "2,4 MB". */
    public String getTamanoLegible() {
        if (tamano < 1024 * 1024)
            return Math.max(1, Math.round(tamano / 1024.0)) + " KB";
        return String.format(java.util.Locale.forLanguageTag("es-CO"), "%.1f MB", tamano / (1024.0 * 1024));
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPacienteId() {
        return pacienteId;
    }

    public void setPacienteId(String pacienteId) {
        this.pacienteId = pacienteId;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public long getTamano() {
        return tamano;
    }

    public void setTamano(long tamano) {
        this.tamano = tamano;
    }

    public byte[] getContenido() {
        return contenido;
    }

    public void setContenido(byte[] contenido) {
        this.contenido = contenido;
    }

    public LocalDateTime getSubidoEn() {
        return subidoEn;
    }

    public void setSubidoEn(LocalDateTime subidoEn) {
        this.subidoEn = subidoEn;
    }
}
