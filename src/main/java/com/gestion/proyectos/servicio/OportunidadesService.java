package com.gestion.proyectos.servicio;

import com.gestion.proyectos.modelo.Cita;
import com.gestion.proyectos.modelo.Doctor;
import com.gestion.proyectos.modelo.EstadoCita;
import com.gestion.proyectos.modelo.HorarioAtencion;
import com.gestion.proyectos.repositorio.CitaRepositorio;
import com.gestion.proyectos.repositorio.HorarioAtencionRepositorio;
import com.gestion.proyectos.repositorio.PersonaRepositorio;
import com.gestion.proyectos.util.Reloj;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Identifica qué especialidades y médicos se usan más y menos, y quién necesita algo. Cada uno se compara con los
 * demás: una especialidad con el promedio de la plataforma y un médico con el de su especialidad. Solo sugiere: la
 * decisión es del administrador. Una ocupación baja no dice nada de la calidad de un médico.
 */
@Service
public class OportunidadesService {

    static final int DIAS_ANALISIS = 60;
    /** Desde aquí casi no quedan cupos, sin importar cómo esté el resto de la plataforma. */
    static final int OCUPACION_ALTA = 80;
    static final double FACTOR_MAS_USADA = 1.25;
    static final double FACTOR_MENOS_USADA = 0.75;
    static final double FACTOR_MEDICO_BAJO = 0.6;
    static final int CANCELACION_ALTA = 25;
    /** Con menos citas que esto no se saca ninguna conclusión del médico. */
    static final int MINIMO_CITAS = 5;
    private static final int MAX_MAS_USADOS = 5;

    public record Senal(String texto, String tono, String accion) {
        boolean urgente() {
            return "danger".equals(tono) || "warning".equals(tono);
        }
    }

    public record Especialidad(String nombre, long medicos, long citas, long capacidad, int ocupacion, Senal senal) {
    }

    public record Medico(String nombre, String especialidad, String email, String telefono, long citas, long capacidad,
            int ocupacion, int cancelacion, List<Senal> senales) {
        Medico conSenales(List<Senal> nuevas) {
            return new Medico(nombre, especialidad, email, telefono, citas, capacidad, ocupacion, cancelacion, nuevas);
        }

        long urgentes() {
            return senales.stream().filter(Senal::urgente).count();
        }
    }

    public record Panel(LocalDate desde, LocalDate hasta, int ocupacionGlobal, Especialidad masUsada, Especialidad menosUsada,
            List<Especialidad> especialidades, List<Medico> masUsados, List<Medico> porRevisar, long sinNovedad) {
    }

    private record Fila(Doctor doctor, int totalCitas, Medico medico) {
    }

    private final CitaRepositorio citaRepo;
    private final PersonaRepositorio personaRepo;
    private final HorarioAtencionRepositorio horarioRepo;

    public OportunidadesService(CitaRepositorio citaRepo, PersonaRepositorio personaRepo,
            HorarioAtencionRepositorio horarioRepo) {
        this.citaRepo = citaRepo;
        this.personaRepo = personaRepo;
        this.horarioRepo = horarioRepo;
    }

    public Panel calcular() {
        LocalDate hoy = Reloj.hoy();
        // Termina en la última cita ya ocurrida: si no ha habido actividad reciente, esos días no cuentan como cupos vacíos
        LocalDate hasta = citaRepo.findFirstByEstadoInAndFechaLessThanEqualOrderByFechaDesc(
                        List.of(EstadoCita.ASISTIO, EstadoCita.COMPLETADA, EstadoCita.NO_ASISTIO), hoy)
                .map(Cita::getFecha).orElse(hoy);
        LocalDate desde = hasta.minusDays(DIAS_ANALISIS - 1L);
        LocalDate fin = hasta.plusDays(1);
        // Between de Mongo excluye los extremos: se corre un día a cada lado
        Map<String, List<Cita>> citasPorDoctor = citaRepo.findByFechaBetween(desde.minusDays(1), fin).stream()
                .filter(c -> c.getDoctorId() != null)
                .collect(Collectors.groupingBy(Cita::getDoctorId));
        Map<String, List<HorarioAtencion>> horarios = horarioRepo.findAll().stream()
                .filter(h -> h.getDoctorId() != null)
                .collect(Collectors.groupingBy(HorarioAtencion::getDoctorId));

        List<Fila> filas = new ArrayList<>();
        for (Doctor d : personaRepo.findAllDoctores()) {
            if (!"ACTIVO".equals(d.getEstado()))
                continue;
            List<Cita> citas = citasPorDoctor.getOrDefault(d.getId(), List.of());
            long canceladas = citas.stream().filter(c -> c.getEstado() == EstadoCita.CANCELADA).count();
            long faltas = citas.stream().filter(c -> c.getEstado() == EstadoCita.NO_ASISTIO).count();
            long ocupadas = citas.size() - canceladas;
            long capacidad = GananciasService.turnos(horarios.getOrDefault(d.getId(), List.of()), desde, fin);
            filas.add(new Fila(d, citas.size(), new Medico(d.getNombre() + " " + d.getApellido(), d.getEspecialidad(),
                    d.getEmail(), d.getTelefono(), ocupadas, capacidad,
                    EstadisticasService.porcentaje(Math.min(ocupadas, capacidad), capacidad),
                    EstadisticasService.porcentaje(canceladas + faltas, citas.size()), List.of())));
        }

        List<Medico> todos = filas.stream().map(Fila::medico).toList();
        int global = ocupacion(todos);
        Map<String, List<Medico>> porEspecialidad = todos.stream().filter(m -> m.especialidad() != null)
                .collect(Collectors.groupingBy(Medico::especialidad));
        List<Especialidad> especialidades = porEspecialidad.entrySet().stream()
                .map(e -> especialidad(e.getKey(), e.getValue(), global))
                .sorted(Comparator.comparingInt(Especialidad::ocupacion).reversed())
                .toList();
        List<Medico> conSenales = filas.stream().map(f -> {
            List<Medico> colegas = porEspecialidad.getOrDefault(String.valueOf(f.medico().especialidad()), List.of());
            int referencia = colegas.size() > 1 ? ocupacion(colegas) : global;
            Medico m = f.medico();
            return m.conSenales(senales(f.doctor(), f.totalCitas(), m.capacidad(), m.ocupacion(), m.cancelacion(), referencia));
        }).toList();
        List<Medico> porRevisar = conSenales.stream().filter(m -> !m.senales().isEmpty())
                .sorted(Comparator.comparingLong(Medico::urgentes).reversed().thenComparingInt(Medico::ocupacion))
                .toList();
        List<Medico> masUsados = conSenales.stream().filter(m -> m.citas() > 0)
                .sorted(Comparator.comparingInt(Medico::ocupacion).reversed().thenComparing(Comparator.comparingLong(Medico::citas).reversed()))
                .limit(MAX_MAS_USADOS).toList();
        List<Especialidad> conHorarios = especialidades.stream().filter(e -> e.capacidad() > 0).toList();
        return new Panel(desde, hasta, global, conHorarios.isEmpty() ? null : conHorarios.get(0),
                conHorarios.isEmpty() ? null : conHorarios.get(conHorarios.size() - 1), especialidades, masUsados, porRevisar,
                conSenales.size() - porRevisar.size());
    }

    private static int ocupacion(List<Medico> medicos) {
        long citas = medicos.stream().mapToLong(Medico::citas).sum();
        long capacidad = medicos.stream().mapToLong(Medico::capacidad).sum();
        return EstadisticasService.porcentaje(Math.min(citas, capacidad), capacidad);
    }

    static Especialidad especialidad(String nombre, List<Medico> medicos, int promedio) {
        long citas = medicos.stream().mapToLong(Medico::citas).sum();
        long capacidad = medicos.stream().mapToLong(Medico::capacidad).sum();
        int ocupacion = ocupacion(medicos);
        Senal senal;
        if (capacidad == 0)
            senal = new Senal("Sin horarios", "danger", "Pedirles a sus médicos que publiquen horarios: hoy no se puede agendar.");
        else if (citas == 0)
            senal = new Senal("Sin citas", "warning", "Revisar que se vea en el catálogo y darla a conocer entre los pacientes.");
        else if (ocupacion >= OCUPACION_ALTA)
            senal = new Senal("Faltan médicos", "warning", "Atraer médicos de esta especialidad: casi no quedan cupos.");
        else if (ocupacion >= promedio * FACTOR_MAS_USADA)
            senal = new Senal("Más usada", "success", "Se llena más rápido que el resto: vigilarla por si hacen falta más médicos.");
        else if (ocupacion < promedio * FACTOR_MENOS_USADA)
            senal = new Senal("Menos usada", "info", "Darla a conocer entre los pacientes: tiene cupos de sobra frente al resto.");
        else
            senal = new Senal("En el promedio", "success", "Sin acción por ahora.");
        return new Especialidad(nombre, medicos.size(), citas, capacidad, ocupacion, senal);
    }

    /** referencia: la ocupación de su especialidad o, si es el único, la de la plataforma. */
    static List<Senal> senales(Doctor d, int citas, long capacidad, int ocupacion, int cancelacion, int referencia) {
        List<Senal> lista = new ArrayList<>();
        if (capacidad == 0)
            lista.add(new Senal("Sin horarios", "danger", "Pedirle que publique sus horarios: sin ellos no puede recibir citas."));
        if (citas < MINIMO_CITAS) {
            // Puede ser nuevo: no se mide todavía
            if (capacidad > 0)
                lista.add(new Senal("Pocos datos", "info", "Todavía tiene muy pocas citas para medir su ocupación: conviene volver a mirarlo más adelante."));
        } else {
            if (capacidad > 0 && ocupacion < referencia * FACTOR_MEDICO_BAJO)
                lista.add(new Senal("Baja ocupación", "warning", "Tiene " + ocupacion + " % frente a " + referencia
                        + " % de su grupo. Hablar con el médico para revisar la causa: horarios, precio o perfil."));
            if (cancelacion >= CANCELACION_ALTA)
                lista.add(new Senal("Muchas cancelaciones", "danger", "Hablar con el médico para revisar el caso: se pierden ingresos y cupos."));
        }
        List<String> faltantes = faltantes(d);
        if (!faltantes.isEmpty())
            lista.add(new Senal("Perfil incompleto", "info", "Le falta: " + String.join(", ", faltantes) + "."));
        return lista;
    }

    private static List<String> faltantes(Doctor d) {
        List<String> lista = new ArrayList<>();
        if (d.getFoto() == null || d.getFoto().isBlank())
            lista.add("foto");
        if (d.getBiografia() == null || d.getBiografia().isBlank())
            lista.add("biografía");
        if (!d.tienePrecioPropio())
            lista.add("precio de la consulta");
        if (d.getDireccionConsultorio() == null || d.getDireccionConsultorio().isBlank())
            lista.add("dirección del consultorio");
        return lista;
    }
}
