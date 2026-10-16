package com.gestion.proyectos.servicio;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import com.gestion.proyectos.modelo.Antecedentes;
import com.gestion.proyectos.modelo.Diagnostico;
import com.gestion.proyectos.modelo.Dictamen;
import com.gestion.proyectos.modelo.Medicamento;
import com.gestion.proyectos.modelo.SignosVitales;
import com.gestion.proyectos.util.ValidacionUtil;

/**
 * Normaliza y valida el dictamen SOAP que envía el médico.
 *
 * Obligatorios: tipo de consulta, causa externa, motivo de consulta, enfermedad actual,
 * examen físico y diagnóstico principal (código CIE-10, descripción y tipo).
 * Opcionales: antecedentes, signos vitales, diagnósticos relacionados, fórmula médica,
 * órdenes, recomendaciones e incapacidad; pero si se llenan deben ser coherentes
 * (rangos fisiológicos, filas de la fórmula completas, incapacidad con justificación…).
 */
public final class DictamenValidador {

    /** Letra + 2 dígitos + carácter opcional (ej. J039, A09X, R51). Sin punto. */
    private static final Pattern CIE10 = Pattern.compile("^[A-Z]\\d{2}[0-9A-Z]?$");

    static final int MAX_RELACIONADOS = 5;
    static final int MAX_MEDICAMENTOS = 20;
    /** Máximo de días por certificado de incapacidad (las prórrogas van en otro certificado). */
    static final int MAX_DIAS_INCAPACIDAD = 30;

    private DictamenValidador() {
    }

    /**
     * Copia solo los campos clínicos que puede enviar el formulario (nunca firma ni fechas),
     * recortando espacios, quitando filas vacías y calculando el IMC.
     */
    public static Dictamen normalizar(Dictamen d) {
        Dictamen n = new Dictamen();
        n.setTipoConsulta(d.getTipoConsulta());
        n.setCausaExterna(d.getCausaExterna());
        n.setMotivoConsulta(limpiar(d.getMotivoConsulta()));
        n.setEnfermedadActual(limpiar(d.getEnfermedadActual()));

        Antecedentes a = d.getAntecedentes();
        if (a != null) {
            Antecedentes an = new Antecedentes();
            an.setPatologicos(limpiar(a.getPatologicos()));
            an.setQuirurgicos(limpiar(a.getQuirurgicos()));
            an.setFarmacologicos(limpiar(a.getFarmacologicos()));
            an.setAlergicos(limpiar(a.getAlergicos()));
            an.setFamiliares(limpiar(a.getFamiliares()));
            if (!an.isVacio())
                n.setAntecedentes(an);
        }

        SignosVitales s = d.getSignosVitales();
        if (s != null && !s.isVacio()) {
            SignosVitales sn = new SignosVitales();
            sn.setTensionSistolica(s.getTensionSistolica());
            sn.setTensionDiastolica(s.getTensionDiastolica());
            sn.setFrecuenciaCardiaca(s.getFrecuenciaCardiaca());
            sn.setFrecuenciaRespiratoria(s.getFrecuenciaRespiratoria());
            sn.setTemperatura(s.getTemperatura());
            sn.setSaturacionOxigeno(s.getSaturacionOxigeno());
            sn.setPeso(s.getPeso());
            sn.setTalla(s.getTalla());
            sn.setImc(SignosVitales.calcularImc(s.getPeso(), s.getTalla()));
            n.setSignosVitales(sn);
        }
        n.setExamenFisico(limpiar(d.getExamenFisico()));

        n.setDiagnosticoPrincipal(d.getDiagnosticoPrincipal() == null || d.getDiagnosticoPrincipal().isVacio()
                ? null : limpiar(d.getDiagnosticoPrincipal()));
        List<Diagnostico> relacionados = new ArrayList<>();
        if (d.getDiagnosticosRelacionados() != null)
            for (Diagnostico dx : d.getDiagnosticosRelacionados())
                if (dx != null && !dx.isVacio())
                    relacionados.add(limpiar(dx));
        n.setDiagnosticosRelacionados(relacionados);

        List<Medicamento> medicamentos = new ArrayList<>();
        if (d.getMedicamentos() != null)
            for (Medicamento m : d.getMedicamentos())
                if (m != null && !m.isVacio()) {
                    Medicamento mn = new Medicamento();
                    mn.setNombre(limpiar(m.getNombre()));
                    mn.setVia(m.getVia());
                    mn.setDosisFrecuencia(limpiar(m.getDosisFrecuencia()));
                    mn.setDuracion(limpiar(m.getDuracion()));
                    mn.setCantidad(m.getCantidad());
                    medicamentos.add(mn);
                }
        n.setMedicamentos(medicamentos);

        n.setOrdenesMedicas(limpiar(d.getOrdenesMedicas()));
        n.setRecomendaciones(limpiar(d.getRecomendaciones()));
        n.setIncapacidadDias(d.getIncapacidadDias());
        n.setIncapacidadJustificacion(limpiar(d.getIncapacidadJustificacion()));
        return n;
    }

    /** Devuelve el primer error encontrado o null si el dictamen (ya normalizado) es válido. */
    public static String validar(Dictamen d) {
        // ── Datos generales ──
        if (d.getTipoConsulta() == null)
            return "Seleccione el tipo de consulta.";
        if (d.getCausaExterna() == null)
            return "Seleccione la causa externa.";

        // ── S ──
        String error = texto(d.getMotivoConsulta(), "El motivo de consulta", true, 500);
        if (error == null)
            error = texto(d.getEnfermedadActual(), "La enfermedad actual", true, 4000);
        if (error == null && d.getAntecedentes() != null) {
            Antecedentes a = d.getAntecedentes();
            error = primero(
                    texto(a.getPatologicos(), "Los antecedentes patológicos", false, 1000),
                    texto(a.getQuirurgicos(), "Los antecedentes quirúrgicos", false, 1000),
                    texto(a.getFarmacologicos(), "Los antecedentes farmacológicos", false, 1000),
                    texto(a.getAlergicos(), "Los antecedentes alérgicos", false, 1000),
                    texto(a.getFamiliares(), "Los antecedentes familiares", false, 1000));
        }
        if (error != null)
            return error;

        // ── O ──
        if (d.getSignosVitales() != null && (error = validarSignos(d.getSignosVitales())) != null)
            return error;
        if ((error = texto(d.getExamenFisico(), "El examen físico", true, 4000)) != null)
            return error;

        // ── A ──
        if (d.getDiagnosticoPrincipal() == null)
            return "El diagnóstico principal es obligatorio.";
        if ((error = validarDiagnostico(d.getDiagnosticoPrincipal(), "El diagnóstico principal")) != null)
            return error;
        if (d.getDiagnosticosRelacionados().size() > MAX_RELACIONADOS)
            return "Puede registrar máximo " + MAX_RELACIONADOS + " diagnósticos relacionados.";
        Set<String> codigos = new HashSet<>();
        codigos.add(d.getDiagnosticoPrincipal().getCodigo());
        for (int i = 0; i < d.getDiagnosticosRelacionados().size(); i++) {
            Diagnostico dx = d.getDiagnosticosRelacionados().get(i);
            if ((error = validarDiagnostico(dx, "El diagnóstico relacionado " + (i + 1))) != null)
                return error;
            if (!codigos.add(dx.getCodigo()))
                return "El código " + dx.getCodigo() + " está repetido en los diagnósticos.";
        }

        // ── P ──
        if (d.getMedicamentos().size() > MAX_MEDICAMENTOS)
            return "La fórmula médica admite máximo " + MAX_MEDICAMENTOS + " medicamentos.";
        for (int i = 0; i < d.getMedicamentos().size(); i++)
            if ((error = validarMedicamento(d.getMedicamentos().get(i), i + 1)) != null)
                return error;
        error = primero(
                texto(d.getOrdenesMedicas(), "Las órdenes médicas", false, 2000),
                texto(d.getRecomendaciones(), "Las recomendaciones", false, 2000),
                texto(d.getIncapacidadJustificacion(), "La justificación de la incapacidad", false, 1000));
        if (error != null)
            return error;
        Integer dias = d.getIncapacidadDias();
        if (dias != null) {
            if (dias < 1 || dias > MAX_DIAS_INCAPACIDAD)
                return "Los días de incapacidad deben estar entre 1 y " + MAX_DIAS_INCAPACIDAD + ".";
            if (d.getIncapacidadJustificacion() == null)
                return "Si otorga incapacidad debe escribir la justificación.";
        } else if (d.getIncapacidadJustificacion() != null) {
            return "Indique los días de incapacidad o borre la justificación.";
        }
        return null;
    }

    private static String validarSignos(SignosVitales s) {
        Integer sis = s.getTensionSistolica();
        Integer dia = s.getTensionDiastolica();
        if ((sis == null) != (dia == null))
            return "Registre la tensión arterial completa (sistólica y diastólica).";
        if (sis != null) {
            String error = primero(
                    rango(sis, 50, 260, "La tensión sistólica", "mmHg"),
                    rango(dia, 30, 160, "La tensión diastólica", "mmHg"));
            if (error != null)
                return error;
            if (sis <= dia)
                return "La tensión sistólica debe ser mayor que la diastólica.";
        }
        return primero(
                rango(s.getFrecuenciaCardiaca(), 20, 250, "La frecuencia cardíaca", "lpm"),
                rango(s.getFrecuenciaRespiratoria(), 5, 60, "La frecuencia respiratoria", "rpm"),
                rango(s.getTemperatura(), 30, 45, "La temperatura", "°C"),
                rango(s.getSaturacionOxigeno(), 50, 100, "La saturación de oxígeno", "%"),
                rango(s.getPeso(), 0.5, 400, "El peso", "kg"),
                rango(s.getTalla(), 30, 250, "La talla", "cm"));
    }

    private static String validarDiagnostico(Diagnostico dx, String nombre) {
        if (ValidacionUtil.esVacio(dx.getCodigo()))
            return nombre + " necesita el código CIE-10.";
        if (!CIE10.matcher(dx.getCodigo()).matches())
            return nombre + ": el código CIE-10 \"" + dx.getCodigo() + "\" no es válido (ej. J039).";
        String error = texto(dx.getDescripcion(), nombre + " (descripción)", true, 250);
        if (error != null)
            return error;
        if (dx.getTipo() == null)
            return nombre + " necesita el tipo de diagnóstico.";
        return null;
    }

    private static String validarMedicamento(Medicamento m, int fila) {
        String p = "Medicamento " + fila + ": ";
        if (ValidacionUtil.esVacio(m.getNombre()))
            return p + "falta el nombre genérico y la concentración.";
        if (m.getVia() == null)
            return p + "seleccione la vía de administración.";
        if (ValidacionUtil.esVacio(m.getDosisFrecuencia()))
            return p + "falta la dosis y frecuencia.";
        if (ValidacionUtil.esVacio(m.getDuracion()))
            return p + "falta la duración.";
        if (m.getCantidad() == null)
            return p + "falta la cantidad total a dispensar.";
        if (m.getCantidad() < 1 || m.getCantidad() > 9999)
            return p + "la cantidad debe estar entre 1 y 9999.";
        return primero(
                texto(m.getNombre(), p + "el nombre", false, 150),
                texto(m.getDosisFrecuencia(), p + "la dosis", false, 150),
                texto(m.getDuracion(), p + "la duración", false, 100));
    }

    private static Diagnostico limpiar(Diagnostico dx) {
        Diagnostico n = new Diagnostico();
        String codigo = limpiar(dx.getCodigo());
        n.setCodigo(codigo == null ? null : codigo.replace(".", "").replace(" ", "").toUpperCase());
        n.setDescripcion(limpiar(dx.getDescripcion()));
        n.setTipo(dx.getTipo());
        return n;
    }

    private static String limpiar(String s) {
        return ValidacionUtil.esVacio(s) ? null : s.trim();
    }

    private static String texto(String valor, String nombre, boolean obligatorio, int max) {
        if (valor == null)
            return obligatorio ? "Falta " + Character.toLowerCase(nombre.charAt(0)) + nombre.substring(1) + "." : null;
        if (valor.length() > max)
            return nombre + " no puede superar " + max + " caracteres.";
        return null;
    }

    private static String rango(Number valor, double min, double max, String nombre, String unidad) {
        if (valor == null)
            return null;
        double v = valor.doubleValue();
        if (Double.isNaN(v) || v < min || v > max)
            return nombre + " debe estar entre " + formato(min) + " y " + formato(max) + " " + unidad + ".";
        return null;
    }

    private static String formato(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    private static String primero(String... errores) {
        for (String e : errores)
            if (e != null)
                return e;
        return null;
    }
}
