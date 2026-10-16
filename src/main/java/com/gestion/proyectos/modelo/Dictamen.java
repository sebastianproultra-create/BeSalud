package com.gestion.proyectos.modelo;

import java.time.LocalDateTime;

/**
 * Dictamen médico de una cita. Estructura:
 * 1. Encabezado (objeto, fecha de emisión) · 2. Alcance y criterios ·
 * 3. Hallazgos (incluye diagnóstico) · 4. Veredicto ·
 * 5. Recomendaciones (incluye tratamiento) · 6. Firma del médico.
 * Los campos añadidos son opcionales: los dictámenes antiguos solo tienen
 * diagnóstico, tratamiento y observaciones.
 */
public class Dictamen {

    private String objeto;
    private String alcance;
    private String hallazgos;
    private String diagnostico;
    private Veredicto veredicto;
    private String tratamiento;
    private String recomendaciones;
    private String observaciones;
    private LocalDateTime fechaEmision;
    private String firmadoPor;
    private String especialidadFirmante;

    public String getObjeto() {
        return objeto;
    }

    public void setObjeto(String objeto) {
        this.objeto = objeto;
    }

    public String getAlcance() {
        return alcance;
    }

    public void setAlcance(String alcance) {
        this.alcance = alcance;
    }

    public String getHallazgos() {
        return hallazgos;
    }

    public void setHallazgos(String hallazgos) {
        this.hallazgos = hallazgos;
    }

    public String getDiagnostico() {
        return diagnostico;
    }

    public void setDiagnostico(String diagnostico) {
        this.diagnostico = diagnostico;
    }

    public Veredicto getVeredicto() {
        return veredicto;
    }

    public void setVeredicto(Veredicto veredicto) {
        this.veredicto = veredicto;
    }

    public String getTratamiento() {
        return tratamiento;
    }

    public void setTratamiento(String tratamiento) {
        this.tratamiento = tratamiento;
    }

    public String getRecomendaciones() {
        return recomendaciones;
    }

    public void setRecomendaciones(String recomendaciones) {
        this.recomendaciones = recomendaciones;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }

    public LocalDateTime getFechaEmision() {
        return fechaEmision;
    }

    public void setFechaEmision(LocalDateTime fechaEmision) {
        this.fechaEmision = fechaEmision;
    }

    public String getFirmadoPor() {
        return firmadoPor;
    }

    public void setFirmadoPor(String firmadoPor) {
        this.firmadoPor = firmadoPor;
    }

    public String getEspecialidadFirmante() {
        return especialidadFirmante;
    }

    public void setEspecialidadFirmante(String especialidadFirmante) {
        this.especialidadFirmante = especialidadFirmante;
    }

}
