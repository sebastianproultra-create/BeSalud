package com.gestion.proyectos.servicio;

import com.gestion.proyectos.modelo.Antecedentes;
import com.gestion.proyectos.modelo.Cita;
import com.gestion.proyectos.modelo.Diagnostico;
import com.gestion.proyectos.modelo.Dictamen;
import com.gestion.proyectos.modelo.Doctor;
import com.gestion.proyectos.modelo.EstadoCita;
import com.gestion.proyectos.modelo.Medicamento;
import com.gestion.proyectos.modelo.Paciente;
import com.gestion.proyectos.modelo.Persona;
import com.gestion.proyectos.repositorio.CitaRepositorio;
import com.gestion.proyectos.repositorio.PersonaRepositorio;
import com.gestion.proyectos.util.Reloj;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static com.gestion.proyectos.util.ValidacionUtil.esVacio;

/**
 * Historial clínico de un paciente: une los dictámenes de todas sus citas (con cualquier médico) en una
 * línea de tiempo y resume lo que el médico necesita ver de un vistazo: alergias, antecedentes vigentes,
 * diagnósticos y la última fórmula. Incluye también el historial previo que el paciente declaró.
 */
@Service
public class HistorialClinicoService {

    static final int MAX_TEXTO = 1000;

    /** Estados en que el médico tiene relación de atención con el paciente y puede consultar su historial. */
    private static final List<EstadoCita> ESTADOS_CON_ATENCION = List.of(
            EstadoCita.PENDIENTE, EstadoCita.ASISTIO, EstadoCita.COMPLETADA);

    private final CitaRepositorio citaRepo;
    private final PersonaRepositorio personaRepo;

    public HistorialClinicoService(CitaRepositorio citaRepo, PersonaRepositorio personaRepo) {
        this.citaRepo = citaRepo;
        this.personaRepo = personaRepo;
    }

    /** Un dictamen dentro del historial, con quién lo firmó. */
    public record Entrada(Cita cita, Dictamen dictamen, String medico, String especialidad, LocalDateTime fecha) {}

    /** Una fila para mostrar: "Alérgicos" → "Penicilina", con la fecha del dictamen de donde sale (si aplica). */
    public record Fila(String clave, String etiqueta, String valor, LocalDateTime fecha) {}

    /** Un diagnóstico registrado, con la fecha y el médico que lo dio. */
    public record DiagnosticoRegistrado(Diagnostico diagnostico, boolean principal, LocalDateTime fecha, String medico) {}

    /** Un mismo diagnóstico a lo largo de las consultas: cuántas veces se registró, la primera y la última. */
    public record DiagnosticoAgrupado(Diagnostico diagnostico, int veces, LocalDateTime primera, LocalDateTime ultima,
            String ultimoMedico) {}

    public record HistorialClinico(
            Paciente paciente,
            List<Entrada> entradas,
            /** Antecedentes vigentes: por cada campo, lo más reciente que registró un médico. */
            Antecedentes antecedentes,
            /** Fecha del dictamen del que sale cada antecedente vigente ("patologicos" → fecha). */
            Map<String, LocalDateTime> antecedentesFecha,
            List<DiagnosticoRegistrado> diagnosticos,
            /** Fórmula del dictamen más reciente que tenga medicamentos. */
            List<Medicamento> ultimaFormula,
            LocalDateTime ultimaFormulaFecha,
            /** Alergias a resaltar: las del médico o, si no hay, las que declaró el paciente. */
            String alergias) {

        public boolean isVacio() {
            return entradas.isEmpty();
        }

        public boolean isTieneDeclarado() {
            return !getFilasDeclarado().isEmpty();
        }

        /**
         * Diagnósticos sin repetir (por código CIE-10, o por texto en dictámenes viejos), del más reciente al más
         * antiguo: un paciente crónico con 20 controles ve "Hipertensión · 12 veces" y no 12 filas iguales.
         */
        public List<DiagnosticoAgrupado> getDiagnosticosAgrupados() {
            Map<String, DiagnosticoAgrupado> grupos = new java.util.LinkedHashMap<>();
            for (DiagnosticoRegistrado r : diagnosticos) { // ya vienen del más reciente al más antiguo
                Diagnostico dx = r.diagnostico();
                String clave = !esVacio(dx.getCodigo()) ? dx.getCodigo().trim().toUpperCase()
                        : String.valueOf(dx.getDescripcion()).trim().toLowerCase();
                grupos.merge(clave, new DiagnosticoAgrupado(dx, 1, r.fecha(), r.fecha(), r.medico()),
                        (actual, nuevo) -> new DiagnosticoAgrupado(actual.diagnostico(), actual.veces() + 1,
                                nuevo.primera() != null ? nuevo.primera() : actual.primera(), actual.ultima(),
                                actual.ultimoMedico()));
            }
            return new ArrayList<>(grupos.values());
        }

        /** Antecedentes vigentes que tienen texto, en el orden de la historia clínica. */
        public List<Fila> getFilasAntecedentes() {
            List<Fila> filas = new ArrayList<>();
            for (Campo c : CAMPOS) {
                String v = c.leer.apply(antecedentes);
                if (!esVacio(v))
                    filas.add(new Fila(c.clave, c.etiqueta, v, antecedentesFecha.get(c.clave)));
            }
            return filas;
        }

        /** Lo que declaró el paciente, con etiquetas en lenguaje sencillo. */
        public List<Fila> getFilasDeclarado() {
            List<Fila> filas = new ArrayList<>();
            Antecedentes a = paciente.getHistorialPrevio();
            if (a != null)
                for (Campo c : CAMPOS) {
                    String v = c.leer.apply(a);
                    if (!esVacio(v))
                        filas.add(new Fila(c.clave, c.etiquetaPaciente, v, null));
                }
            if (!esVacio(paciente.getHistorialPrevioNotas()))
                filas.add(new Fila("notas", "Otros datos", paciente.getHistorialPrevioNotas(), null));
            return filas;
        }
    }

    public Optional<HistorialClinico> historialDe(String pacienteId) {
        return personaRepo.findById(pacienteId)
                .filter(Paciente.class::isInstance)
                .map(p -> armar((Paciente) p));
    }

    /** El médico solo ve el historial de pacientes con los que tiene o tuvo una cita activa o atendida. */
    public boolean medicoPuedeVer(String doctorId, String pacienteId) {
        if (esVacio(doctorId) || esVacio(pacienteId))
            return false;
        return citaRepo.findByPacienteId(pacienteId).stream()
                .anyMatch(c -> doctorId.equals(c.getDoctorId()) && ESTADOS_CON_ATENCION.contains(c.getEstado()));
    }

    /**
     * Antecedentes con que se precarga el dictamen de un paciente: lo vigente del historial y, en los campos
     * que ningún médico ha llenado, lo que declaró el paciente. Así el médico no los vuelve a escribir.
     */
    public Antecedentes antecedentesParaDictamen(String pacienteId) {
        return historialDe(pacienteId).map(h -> {
            Antecedentes base = copia(h.antecedentes());
            Antecedentes declarado = h.paciente().getHistorialPrevio();
            if (declarado != null)
                for (Campo c : CAMPOS)
                    if (esVacio(c.leer.apply(base)) && !esVacio(c.leer.apply(declarado)))
                        c.escribir.accept(base, c.leer.apply(declarado).trim());
            return base;
        }).orElseGet(Antecedentes::new);
    }

    /** Guarda el historial previo que declara el paciente. Devuelve un mensaje de error o null si está bien. */
    public String guardarDeclarado(Paciente paciente, Antecedentes datos, String notas) {
        Antecedentes limpio = new Antecedentes();
        for (Campo c : CAMPOS) {
            String valor = c.leer.apply(datos);
            if (valor != null && valor.trim().length() > MAX_TEXTO)
                return c.nombre + " no puede superar " + MAX_TEXTO + " caracteres.";
            c.escribir.accept(limpio, esVacio(valor) ? null : valor.trim());
        }
        if (notas != null && notas.trim().length() > MAX_TEXTO)
            return "Las notas no pueden superar " + MAX_TEXTO + " caracteres.";
        String notasLimpias = esVacio(notas) ? null : notas.trim();

        if (iguales(paciente.getHistorialPrevio(), limpio)
                && java.util.Objects.equals(paciente.getHistorialPrevioNotas(), notasLimpias))
            return null; // sin cambios: no se toca la fecha de actualización
        paciente.setHistorialPrevio(limpio.isVacio() ? null : limpio);
        paciente.setHistorialPrevioNotas(notasLimpias);
        paciente.setHistorialPrevioActualizado(Reloj.ahora());
        personaRepo.save(paciente);
        return null;
    }

    // ── Construcción del historial ──────────────────────────────────────────

    private HistorialClinico armar(Paciente paciente) {
        List<Cita> conDictamen = citaRepo.findByPacienteId(paciente.getId()).stream()
                .filter(c -> c.getEstado() == EstadoCita.COMPLETADA && c.getDictamen() != null)
                .toList();

        Map<String, Doctor> medicos = new HashMap<>();
        List<String> ids = conDictamen.stream().map(Cita::getDoctorId).filter(id -> id != null).distinct().toList();
        for (Persona p : personaRepo.findAllById(ids))
            if (p instanceof Doctor d)
                medicos.put(d.getId(), d);

        List<Entrada> entradas = new ArrayList<>();
        for (Cita c : conDictamen) {
            Dictamen d = c.getDictamen();
            Doctor doc = medicos.get(c.getDoctorId());
            String medico = !esVacio(d.getFirmadoPor()) ? d.getFirmadoPor()
                    : doc != null ? "Dr(a). " + doc.getNombre() + " " + doc.getApellido() : "Profesional";
            String especialidad = !esVacio(d.getEspecialidadFirmante()) ? d.getEspecialidadFirmante()
                    : doc != null ? doc.getEspecialidad() : null;
            entradas.add(new Entrada(c, d, medico, especialidad, fechaDe(c)));
        }
        entradas.sort(Comparator.comparing(Entrada::fecha, Comparator.nullsLast(Comparator.reverseOrder())));

        // Antecedentes vigentes: se recorre de lo más reciente a lo más antiguo y se toma el primer valor de cada campo
        Antecedentes vigentes = new Antecedentes();
        Map<String, LocalDateTime> fechas = new HashMap<>();
        for (Entrada e : entradas) {
            Antecedentes a = e.dictamen().getAntecedentes();
            if (a == null)
                continue;
            for (Campo c : CAMPOS)
                if (esVacio(c.leer.apply(vigentes)) && !esVacio(c.leer.apply(a))) {
                    c.escribir.accept(vigentes, c.leer.apply(a).trim());
                    fechas.put(c.clave, e.fecha());
                }
        }

        List<DiagnosticoRegistrado> diagnosticos = new ArrayList<>();
        for (Entrada e : entradas) {
            Dictamen d = e.dictamen();
            if (d.getDiagnosticoPrincipal() != null)
                diagnosticos.add(new DiagnosticoRegistrado(d.getDiagnosticoPrincipal(), true, e.fecha(), e.medico()));
            else if (!esVacio(d.getDiagnostico())) { // dictamen del formato anterior (texto libre)
                Diagnostico libre = new Diagnostico();
                libre.setDescripcion(d.getDiagnostico());
                diagnosticos.add(new DiagnosticoRegistrado(libre, true, e.fecha(), e.medico()));
            }
            if (d.getDiagnosticosRelacionados() != null)
                d.getDiagnosticosRelacionados().forEach(dx ->
                        diagnosticos.add(new DiagnosticoRegistrado(dx, false, e.fecha(), e.medico())));
        }

        Entrada conFormula = entradas.stream()
                .filter(e -> e.dictamen().getMedicamentos() != null && !e.dictamen().getMedicamentos().isEmpty())
                .findFirst().orElse(null);

        String alergias = vigentes.isTieneAlergias() ? vigentes.getAlergicos()
                : paciente.getHistorialPrevio() != null && paciente.getHistorialPrevio().isTieneAlergias()
                        ? paciente.getHistorialPrevio().getAlergicos() : null;

        return new HistorialClinico(paciente, entradas, vigentes, fechas, diagnosticos,
                conFormula != null ? conFormula.dictamen().getMedicamentos() : List.of(),
                conFormula != null ? conFormula.fecha() : null, alergias);
    }

    /** Fecha de la atención: la que registró el dictamen o, en dictámenes viejos, la de la cita. */
    private static LocalDateTime fechaDe(Cita c) {
        Dictamen d = c.getDictamen();
        if (d.getFechaAtencion() != null)
            return d.getFechaAtencion();
        if (c.getInicioAtencion() != null)
            return c.getInicioAtencion();
        if (c.getFecha() != null)
            return c.getHora() != null ? c.getFecha().atTime(c.getHora()) : c.getFecha().atStartOfDay();
        return d.getFechaEmision();
    }

    // ── Campos de los antecedentes ──────────────────────────────────────────

    private record Campo(String clave, String etiqueta, String etiquetaPaciente, String nombre,
            Function<Antecedentes, String> leer, BiConsumer<Antecedentes, String> escribir) {}

    private static final List<Campo> CAMPOS = List.of(
            new Campo("patologicos", "Patológicos", "Enfermedades", "Los antecedentes patológicos",
                    Antecedentes::getPatologicos, Antecedentes::setPatologicos),
            new Campo("quirurgicos", "Quirúrgicos", "Cirugías", "Los antecedentes quirúrgicos",
                    Antecedentes::getQuirurgicos, Antecedentes::setQuirurgicos),
            new Campo("farmacologicos", "Farmacológicos", "Medicamentos que toma", "Los medicamentos que toma",
                    Antecedentes::getFarmacologicos, Antecedentes::setFarmacologicos),
            new Campo("alergicos", "Alérgicos", "Alergias", "Las alergias",
                    Antecedentes::getAlergicos, Antecedentes::setAlergicos),
            new Campo("familiares", "Familiares", "Enfermedades en la familia", "Los antecedentes familiares",
                    Antecedentes::getFamiliares, Antecedentes::setFamiliares));

    private static Antecedentes copia(Antecedentes a) {
        Antecedentes c = new Antecedentes();
        if (a != null)
            for (Campo campo : CAMPOS)
                campo.escribir.accept(c, campo.leer.apply(a));
        return c;
    }

    private static boolean iguales(Antecedentes a, Antecedentes b) {
        Antecedentes x = a != null ? a : new Antecedentes();
        Antecedentes y = b != null ? b : new Antecedentes();
        for (Campo c : CAMPOS) {
            String vx = esVacio(c.leer.apply(x)) ? null : c.leer.apply(x);
            String vy = esVacio(c.leer.apply(y)) ? null : c.leer.apply(y);
            if (!java.util.Objects.equals(vx, vy))
                return false;
        }
        return true;
    }
}
