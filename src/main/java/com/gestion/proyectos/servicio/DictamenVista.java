package com.gestion.proyectos.servicio;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.gestion.proyectos.modelo.Antecedentes;
import com.gestion.proyectos.modelo.Diagnostico;
import com.gestion.proyectos.modelo.Dictamen;
import com.gestion.proyectos.modelo.Medicamento;
import com.gestion.proyectos.modelo.SignosVitales;

/**
 * Convierte un dictamen en un mapa plano (solo textos, números, listas y booleanos)
 * para pasarlo al JavaScript del panel del médico: el modal "Ver dictamen" lo pinta
 * y, si el guardado falla, el formulario se vuelve a llenar con él.
 * Los enums van con su clave (para los select) y con su texto (para mostrar).
 */
public final class DictamenVista {

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private DictamenVista() {
    }

    public static Map<String, Object> de(Dictamen d) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("formatoAnterior", d.isFormatoAnterior());
        v.put("fechaAtencion", fecha(d.getFechaAtencion()));
        v.put("tipoConsulta", d.getTipoConsulta() != null ? d.getTipoConsulta().name() : "");
        v.put("tipoConsultaTexto", d.getTipoConsulta() != null ? d.getTipoConsulta().getDescripcion() : "");
        v.put("causaExterna", d.getCausaExterna() != null ? d.getCausaExterna().name() : "");
        v.put("causaExternaTexto", d.getCausaExterna() != null ? d.getCausaExterna().getDescripcion() : "");

        v.put("motivoConsulta", texto(d.getMotivoConsulta()));
        v.put("enfermedadActual", texto(d.getEnfermedadActual()));
        Antecedentes a = d.getAntecedentes() != null ? d.getAntecedentes() : new Antecedentes();
        Map<String, Object> ant = new LinkedHashMap<>();
        ant.put("patologicos", texto(a.getPatologicos()));
        ant.put("quirurgicos", texto(a.getQuirurgicos()));
        ant.put("farmacologicos", texto(a.getFarmacologicos()));
        ant.put("alergicos", texto(a.getAlergicos()));
        ant.put("familiares", texto(a.getFamiliares()));
        v.put("antecedentes", ant);
        v.put("tieneAlergias", d.isTieneAlergias());

        SignosVitales s = d.getSignosVitales() != null ? d.getSignosVitales() : new SignosVitales();
        Map<String, Object> sv = new LinkedHashMap<>();
        sv.put("tensionSistolica", s.getTensionSistolica());
        sv.put("tensionDiastolica", s.getTensionDiastolica());
        sv.put("frecuenciaCardiaca", s.getFrecuenciaCardiaca());
        sv.put("frecuenciaRespiratoria", s.getFrecuenciaRespiratoria());
        sv.put("temperatura", s.getTemperatura());
        sv.put("saturacionOxigeno", s.getSaturacionOxigeno());
        sv.put("peso", s.getPeso());
        sv.put("talla", s.getTalla());
        sv.put("imc", s.getImc());
        v.put("signosVitales", sv);
        v.put("examenFisico", texto(d.getExamenFisico()));

        v.put("diagnosticoPrincipal", d.getDiagnosticoPrincipal() != null ? diagnostico(d.getDiagnosticoPrincipal()) : null);
        List<Map<String, Object>> relacionados = new ArrayList<>();
        if (d.getDiagnosticosRelacionados() != null)
            d.getDiagnosticosRelacionados().forEach(dx -> relacionados.add(diagnostico(dx)));
        v.put("diagnosticosRelacionados", relacionados);

        List<Map<String, Object>> medicamentos = new ArrayList<>();
        if (d.getMedicamentos() != null)
            for (Medicamento m : d.getMedicamentos()) {
                Map<String, Object> mm = new LinkedHashMap<>();
                mm.put("nombre", texto(m.getNombre()));
                mm.put("via", m.getVia() != null ? m.getVia().name() : "");
                mm.put("viaTexto", m.getVia() != null ? m.getVia().getDescripcion() : "");
                mm.put("dosisFrecuencia", texto(m.getDosisFrecuencia()));
                mm.put("duracion", texto(m.getDuracion()));
                mm.put("cantidad", m.getCantidad());
                medicamentos.add(mm);
            }
        v.put("medicamentos", medicamentos);
        v.put("ordenesMedicas", texto(d.getOrdenesMedicas()));
        v.put("recomendaciones", texto(d.getRecomendaciones()));
        v.put("incapacidadDias", d.getIncapacidadDias());
        v.put("incapacidadJustificacion", texto(d.getIncapacidadJustificacion()));

        v.put("fechaEmision", fecha(d.getFechaEmision()));
        v.put("firmadoPor", texto(d.getFirmadoPor()));
        v.put("especialidadFirmante", texto(d.getEspecialidadFirmante()));

        v.put("diagnostico", texto(d.getDiagnostico()));
        v.put("tratamiento", texto(d.getTratamiento()));
        v.put("observaciones", texto(d.getObservaciones()));
        return v;
    }

    private static Map<String, Object> diagnostico(Diagnostico dx) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("codigo", texto(dx.getCodigo()));
        m.put("descripcion", texto(dx.getDescripcion()));
        m.put("tipo", dx.getTipo() != null ? dx.getTipo().name() : "");
        m.put("tipoTexto", dx.getTipo() != null ? dx.getTipo().getDescripcion() : "");
        return m;
    }

    private static String fecha(LocalDateTime f) {
        return f != null ? f.format(FORMATO) : "";
    }

    private static String texto(String s) {
        return s != null ? s : "";
    }
}
