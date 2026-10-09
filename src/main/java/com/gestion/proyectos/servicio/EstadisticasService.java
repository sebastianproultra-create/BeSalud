package com.gestion.proyectos.servicio;

import com.gestion.proyectos.modelo.Cita;
import com.gestion.proyectos.modelo.Doctor;
import com.gestion.proyectos.modelo.EstadoCita;
import com.gestion.proyectos.modelo.HorarioAtencion;
import com.gestion.proyectos.repositorio.CitaRepositorio;
import com.gestion.proyectos.repositorio.HorarioAtencionRepositorio;
import com.gestion.proyectos.repositorio.PersonaRepositorio;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class EstadisticasService {

    static final int DIAS_ATRAS = 30;
    static final int DIAS_ADELANTE = 14;
    static final int DIAS_OCUPACION = 7;
    private static final int MAX_DOCTORES_RANKING = 8;
    private static final Locale ES = Locale.forLanguageTag("es-CO");

    public record Dato(String etiqueta, long valor) {
    }

    public record OcupacionDoctor(String nombre, String especialidad, long citas, long capacidad, int porcentaje) {
    }

    public record Resumen(
            long totalCitas, long proximasSemana, int tasaAsistencia, int tasaCancelacion,
            long doctoresActivos, long doctoresPendientes, long pacientes,
            List<Dato> porDia, int indiceHoy, List<Dato> porEspecialidad, List<Dato> porEstado,
            List<Dato> porHora, List<OcupacionDoctor> ocupacion) {
    }

    private static final Map<EstadoCita, String> NOMBRE_ESTADO = new EnumMap<>(Map.of(
            EstadoCita.PENDIENTE, "Pendientes",
            EstadoCita.COMPLETADA, "Completadas",
            EstadoCita.ASISTIO, "Asistió",
            EstadoCita.NO_ASISTIO, "No asistió",
            EstadoCita.CANCELADA, "Canceladas"));

    private final CitaRepositorio citaRepo;
    private final PersonaRepositorio personaRepo;
    private final HorarioAtencionRepositorio horarioRepo;

    public EstadisticasService(CitaRepositorio citaRepo, PersonaRepositorio personaRepo,
            HorarioAtencionRepositorio horarioRepo) {
        this.citaRepo = citaRepo;
        this.personaRepo = personaRepo;
        this.horarioRepo = horarioRepo;
    }

    public Resumen calcular() {
        List<Cita> citas = citaRepo.findAll().stream()
                .filter(c -> c.getFecha() != null && c.getEstado() != null).toList();
        List<Doctor> doctores = personaRepo.findAllDoctores();
        Map<String, Doctor> doctorPorId = doctores.stream()
                .collect(Collectors.toMap(Doctor::getId, Function.identity(), (a, b) -> a));
        LocalDate hoy = LocalDate.now();

        long atendidas = citas.stream().filter(c -> c.getEstado() == EstadoCita.ASISTIO
                || c.getEstado() == EstadoCita.COMPLETADA).count();
        long noAsistio = citas.stream().filter(c -> c.getEstado() == EstadoCita.NO_ASISTIO).count();
        long canceladas = citas.stream().filter(c -> c.getEstado() == EstadoCita.CANCELADA).count();
        long proximasSemana = citas.stream().filter(c -> c.getEstado() == EstadoCita.PENDIENTE
                && !c.getFecha().isBefore(hoy) && c.getFecha().isBefore(hoy.plusDays(DIAS_OCUPACION))).count();

        return new Resumen(
                citas.size(), proximasSemana,
                porcentaje(atendidas, atendidas + noAsistio),
                porcentaje(canceladas, citas.size()),
                doctores.stream().filter(d -> "ACTIVO".equals(d.getEstado())).count(),
                doctores.stream().filter(d -> !"ACTIVO".equals(d.getEstado())).count(),
                personaRepo.findAllPacientes().size(),
                porDia(citas, hoy), DIAS_ATRAS,
                porEspecialidad(citas, doctorPorId),
                porEstado(citas),
                porHora(citas),
                ocupacion(citas, doctores, hoy));
    }

    // Citas no canceladas por día, desde hace DIAS_ATRAS hasta dentro de DIAS_ADELANTE.
    private List<Dato> porDia(List<Cita> citas, LocalDate hoy) {
        Map<LocalDate, Long> conteo = citas.stream().filter(c -> c.getEstado() != EstadoCita.CANCELADA)
                .collect(Collectors.groupingBy(Cita::getFecha, Collectors.counting()));
        DateTimeFormatter formato = DateTimeFormatter.ofPattern("d MMM", ES);
        List<Dato> serie = new ArrayList<>();
        for (LocalDate d = hoy.minusDays(DIAS_ATRAS); !d.isAfter(hoy.plusDays(DIAS_ADELANTE)); d = d.plusDays(1))
            serie.add(new Dato(d.format(formato), conteo.getOrDefault(d, 0L)));
        return serie;
    }

    private List<Dato> porEspecialidad(List<Cita> citas, Map<String, Doctor> doctorPorId) {
        return citas.stream().filter(c -> c.getEstado() != EstadoCita.CANCELADA)
                .map(c -> doctorPorId.get(c.getDoctorId()))
                .filter(d -> d != null && d.getEspecialidad() != null)
                .collect(Collectors.groupingBy(Doctor::getEspecialidad, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> new Dato(e.getKey(), e.getValue()))
                .toList();
    }

    private List<Dato> porEstado(List<Cita> citas) {
        Map<EstadoCita, Long> conteo = citas.stream()
                .collect(Collectors.groupingBy(Cita::getEstado, () -> new EnumMap<>(EstadoCita.class), Collectors.counting()));
        return NOMBRE_ESTADO.entrySet().stream()
                .map(e -> new Dato(e.getValue(), conteo.getOrDefault(e.getKey(), 0L)))
                .sorted(Comparator.comparingLong(Dato::valor).reversed())
                .toList();
    }

    private List<Dato> porHora(List<Cita> citas) {
        TreeMap<Integer, Long> conteo = new TreeMap<>(citas.stream()
                .filter(c -> c.getHora() != null && c.getEstado() != EstadoCita.CANCELADA)
                .collect(Collectors.groupingBy(c -> c.getHora().getHour(), Collectors.counting())));
        if (conteo.isEmpty()) return List.of();
        List<Dato> serie = new ArrayList<>();
        for (int h = conteo.firstKey(); h <= conteo.lastKey(); h++)
            serie.add(new Dato(String.format("%02d:00", h), conteo.getOrDefault(h, 0L)));
        return serie;
    }

    // Ocupación de la próxima semana: citas pendientes / turnos que ofrece el doctor en esos días.
    private List<OcupacionDoctor> ocupacion(List<Cita> citas, List<Doctor> doctores, LocalDate hoy) {
        Map<String, List<HorarioAtencion>> horariosPorDoctor = horarioRepo.findAll().stream()
                .filter(h -> h.getDoctorId() != null)
                .collect(Collectors.groupingBy(HorarioAtencion::getDoctorId));
        LocalDate fin = hoy.plusDays(DIAS_OCUPACION);
        Map<String, Long> citasPorDoctor = citas.stream()
                .filter(c -> c.getEstado() == EstadoCita.PENDIENTE && !c.getFecha().isBefore(hoy) && c.getFecha().isBefore(fin))
                .collect(Collectors.groupingBy(Cita::getDoctorId, Collectors.counting()));

        List<OcupacionDoctor> lista = new ArrayList<>();
        for (Doctor d : doctores) {
            if (!"ACTIVO".equals(d.getEstado())) continue;
            long capacidad = 0;
            for (LocalDate dia = hoy; dia.isBefore(fin); dia = dia.plusDays(1)) {
                for (HorarioAtencion h : horariosPorDoctor.getOrDefault(d.getId(), List.of())) {
                    if (h.getDiaSemana() != dia.getDayOfWeek() || h.getHoraInicio() == null || h.getHoraFin() == null) continue;
                    int duracion = h.getDuracionCitaMinutos() > 0 ? h.getDuracionCitaMinutos() : 30;
                    capacidad += Duration.between(h.getHoraInicio(), h.getHoraFin()).toMinutes() / duracion;
                }
            }
            if (capacidad == 0) continue;
            long agendadas = citasPorDoctor.getOrDefault(d.getId(), 0L);
            lista.add(new OcupacionDoctor(d.getNombre() + " " + d.getApellido(), d.getEspecialidad(),
                    agendadas, capacidad, porcentaje(agendadas, capacidad)));
        }
        lista.sort(Comparator.comparingInt(OcupacionDoctor::porcentaje).reversed()
                .thenComparing(Comparator.comparingLong(OcupacionDoctor::citas).reversed()));
        return lista.subList(0, Math.min(MAX_DOCTORES_RANKING, lista.size()));
    }

    static int porcentaje(long parte, long total) {
        return total == 0 ? 0 : (int) Math.round(100.0 * parte / total);
    }
}
