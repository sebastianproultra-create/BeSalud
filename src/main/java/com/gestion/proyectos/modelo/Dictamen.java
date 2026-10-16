package com.gestion.proyectos.modelo;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * Dictamen médico de una cita en formato SOAP:
 * datos generales · S (subjetivo) · O (objetivo) · A (análisis, CIE-10) · P (plan).
 * La firma (médico, especialidad y fecha de emisión) la pone el sistema.
 */
@Getter
@Setter
public class Dictamen {

    // ── Datos generales ──
    private LocalDateTime fechaAtencion;
    private TipoConsulta tipoConsulta;
    private CausaExterna causaExterna;

    // ── S: Subjetivo ──
    private String motivoConsulta;
    private String enfermedadActual;
    private Antecedentes antecedentes;

    // ── O: Objetivo ──
    private SignosVitales signosVitales;
    private String examenFisico;

    // ── A: Análisis ──
    private Diagnostico diagnosticoPrincipal;
    private List<Diagnostico> diagnosticosRelacionados = new ArrayList<>();

    // ── P: Plan ──
    private List<Medicamento> medicamentos = new ArrayList<>();
    private String ordenesMedicas;
    private String recomendaciones;
    private Integer incapacidadDias;
    private String incapacidadJustificacion;

    // ── Firma ──
    private LocalDateTime fechaEmision;
    private String firmadoPor;
    private String especialidadFirmante;

    // ── Formato anterior (solo lectura de dictámenes antiguos) ──
    private String diagnostico;
    private String tratamiento;
    private String observaciones;

    /** true si es un dictamen del formato anterior (texto libre, sin SOAP). */
    public boolean isFormatoAnterior() {
        return diagnosticoPrincipal == null;
    }

    /** Texto corto para listados: "J039 · Amigdalitis aguda" o el diagnóstico antiguo. */
    public String getResumenDiagnostico() {
        if (diagnosticoPrincipal == null)
            return diagnostico;
        return diagnosticoPrincipal.getCodigo() + " · " + diagnosticoPrincipal.getDescripcion();
    }

    public boolean isTieneAlergias() {
        return antecedentes != null && antecedentes.isTieneAlergias();
    }
}
