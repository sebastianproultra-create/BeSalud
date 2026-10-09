package com.gestion.proyectos.servicio;

import com.gestion.proyectos.modelo.Cita;
import com.gestion.proyectos.modelo.CitaEvento;
import com.gestion.proyectos.modelo.Doctor;
import com.gestion.proyectos.modelo.EstadoCita;
import com.gestion.proyectos.modelo.HorarioAtencion;
import com.gestion.proyectos.modelo.Paciente;
import com.gestion.proyectos.repositorio.CitaRepositorio;
import com.gestion.proyectos.repositorio.HorarioAtencionRepositorio;
import com.gestion.proyectos.repositorio.PersonaRepositorio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class CitaService {

    private static final Logger log = LoggerFactory.getLogger(CitaService.class);

    private final CitaRepositorio citaRepo;
    private final PersonaRepositorio personaRepo;
    private final HorarioAtencionRepositorio horarioRepo;
    private final ApplicationEventPublisher eventos;

    public CitaService(CitaRepositorio citaRepo, PersonaRepositorio personaRepo,
                       HorarioAtencionRepositorio horarioRepo, ApplicationEventPublisher eventos) {
        this.citaRepo = citaRepo;
        this.personaRepo = personaRepo;
        this.horarioRepo = horarioRepo;
        this.eventos = eventos;
    }

    public Page<Cita> listarPaginado(String pacienteId, String doctorId, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)),
                Sort.by(Sort.Direction.DESC, "fecha", "hora"));
        if (pacienteId != null && !pacienteId.isBlank()) return citaRepo.findByPacienteId(pacienteId, pageable);
        if (doctorId != null && !doctorId.isBlank()) return citaRepo.findByDoctorId(doctorId, pageable);
        return citaRepo.findAll(pageable);
    }

    /** Nombres de las personas pedidas (solo las de la página, no toda la colección). */
    public Map<String, String> nombresPorId(Collection<String> ids) {
        Map<String, String> nombres = new HashMap<>();
        personaRepo.findAllById(ids).forEach(p -> nombres.put(p.getId(), p.getNombre() + " " + p.getApellido()));
        return nombres;
    }

    public LinkedHashMap<String, List<String>> slotsDisponibles(String doctorId, String excludeCitaId,
                                                                  LocalDate fechaActual, LocalTime horaActual) {
        LinkedHashMap<String, List<String>> slots = new LinkedHashMap<>();
        LocalDate today = LocalDate.now();
        for (int i = 0; i < 30; i++) {
            LocalDate fecha = today.plusDays(i);
            List<LocalTime> tiempos = calcularSlotsDisponibles(doctorId, fecha, excludeCitaId, fechaActual, horaActual);
            if (!tiempos.isEmpty()) {
                slots.put(fecha.toString(), tiempos.stream().map(t -> t.toString().substring(0, 5)).toList());
            }
        }
        return slots;
    }

    public Cita crearCita(String doctorId, String pacienteId, LocalDate fecha, LocalTime hora, String motivo) {
        if (!doctorActivo(doctorId)) {
            throw new IllegalStateException("Doctor no activo: " + doctorId);
        }
        Cita cita = new Cita();
        cita.setDoctorId(doctorId);
        cita.setPacienteId(pacienteId);
        cita.setFecha(fecha);
        cita.setHora(hora);
        cita.setMotivo(motivo.trim());
        Cita guardada = citaRepo.save(cita);
        log.info("Cita creada: id={} doctor={} paciente={} fecha={} hora={}", guardada.getId(), doctorId, pacienteId, fecha, hora);
        eventos.publishEvent(new CitaEvento(CitaEvento.Tipo.CREADA, guardada.getId()));
        return guardada;
    }

    public void reprogramar(Cita cita, LocalDate fecha, LocalTime hora) {
        if (cita.getEstado() != EstadoCita.PENDIENTE) {
            throw new IllegalStateException("Solo citas PENDIENTE pueden reprogramarse: " + cita.getId());
        }
        cita.setFecha(fecha);
        cita.setHora(hora);
        cita.setRecordatorioEnviado(false);
        citaRepo.save(cita);
        log.info("Cita reprogramada: id={} nueva fecha={} hora={}", cita.getId(), fecha, hora);
        eventos.publishEvent(new CitaEvento(CitaEvento.Tipo.REPROGRAMADA, cita.getId()));
    }

    public void cancelar(Cita cita) {
        if (cita.getEstado() == EstadoCita.CANCELADA) {
            log.warn("Intento de cancelar cita ya cancelada: {}", cita.getId());
            return;
        }
        cita.setEstado(EstadoCita.CANCELADA);
        citaRepo.save(cita);
        log.info("Cita cancelada: id={}", cita.getId());
        eventos.publishEvent(new CitaEvento(CitaEvento.Tipo.CANCELADA, cita.getId()));
    }

    public Optional<Cita> buscarPorId(String id) {
        return citaRepo.findById(id);
    }

    public Optional<Paciente> buscarPacientePorEmail(String email) {
        return personaRepo.findPacienteByEmail(email);
    }

    public Optional<Doctor> buscarDoctorPorEmail(String email) {
        return personaRepo.findDoctorByEmail(email);
    }

    public Optional<Doctor> buscarDoctorPorId(String id) {
        return personaRepo.findById(id).filter(p -> p instanceof Doctor).map(p -> (Doctor) p);
    }

    public boolean doctorActivo(String doctorId) {
        return buscarDoctorPorId(doctorId).map(d -> "ACTIVO".equals(d.getEstado())).orElse(false);
    }

    public List<Doctor> listarDoctores() {
        return personaRepo.findAllDoctores();
    }

    public List<Doctor> listarDoctoresActivos() {
        return personaRepo.findDoctoresActivos();
    }

    public List<Paciente> listarPacientes() {
        return personaRepo.findAllPacientes();
    }

    public boolean doctorExiste(String doctorId) {
        return personaRepo.existsById(doctorId);
    }

    public boolean pacienteExiste(String pacienteId) {
        return personaRepo.existsById(pacienteId);
    }

    public boolean esFechaHoraPasada(LocalDate fecha, LocalTime hora) {
        return LocalDateTime.of(fecha, hora).isBefore(LocalDateTime.now());
    }

    public boolean esHorarioValido(String doctorId, LocalDate fecha, LocalTime hora) {
        return horarioRepo.findByDoctorId(doctorId).stream()
                .anyMatch(h -> h.getDiaSemana() != null && h.getHoraInicio() != null && h.getHoraFin() != null
                        && h.getDiaSemana().equals(fecha.getDayOfWeek())
                        && !hora.isBefore(h.getHoraInicio())
                        && hora.isBefore(h.getHoraFin()));
    }

    public boolean hayConflicto(String doctorId, LocalDate fecha, LocalTime hora, String excludeCitaId) {
        List<HorarioAtencion> horarios = horarioRepo.findByDoctorId(doctorId);
        List<Cita> existentes = citaRepo.findByDoctorIdAndFecha(doctorId, fecha).stream()
                .filter(c -> (excludeCitaId == null || !excludeCitaId.equals(c.getId()))
                        && c.getEstado() != EstadoCita.CANCELADA
                        && c.getEstado() != EstadoCita.NO_ASISTIO)
                .toList();

        LocalDateTime inicioNuevo = LocalDateTime.of(fecha, hora);
        LocalDateTime finNuevo = inicioNuevo.plusMinutes(duracionDesdeHorarios(horarios, fecha, hora));

        return existentes.stream().anyMatch(c -> {
            LocalDateTime inicio = LocalDateTime.of(c.getFecha(), c.getHora());
            LocalDateTime fin = inicio.plusMinutes(duracionDesdeHorarios(horarios, c.getFecha(), c.getHora()));
            return inicioNuevo.isBefore(fin) && finNuevo.isAfter(inicio);
        });
    }

    public boolean pacienteTieneConflicto(String pacienteId, String doctorId, LocalDate fecha, LocalTime hora,
                                          String excludeCitaId) {
        LocalDateTime inicioNuevo = LocalDateTime.of(fecha, hora);
        LocalDateTime finNuevo = inicioNuevo.plusMinutes(
                duracionDesdeHorarios(horarioRepo.findByDoctorId(doctorId), fecha, hora));

        return citaRepo.findByPacienteIdAndFecha(pacienteId, fecha).stream()
                .filter(c -> (excludeCitaId == null || !excludeCitaId.equals(c.getId()))
                        && c.getEstado() != EstadoCita.CANCELADA
                        && c.getEstado() != EstadoCita.NO_ASISTIO)
                .anyMatch(c -> {
                    LocalDateTime inicio = LocalDateTime.of(c.getFecha(), c.getHora());
                    LocalDateTime fin = inicio.plusMinutes(duracionDesdeHorarios(
                            horarioRepo.findByDoctorId(c.getDoctorId()), c.getFecha(), c.getHora()));
                    return inicioNuevo.isBefore(fin) && finNuevo.isAfter(inicio);
                });
    }

    private List<LocalTime> calcularSlotsDisponibles(String doctorId, LocalDate fecha, String excludeCitaId,
                                                      LocalDate fechaActual, LocalTime horaActual) {
        List<HorarioAtencion> horarios = horarioRepo.findByDoctorId(doctorId);
        List<LocalTime> slots = new ArrayList<>();
        java.time.DayOfWeek dia = fecha.getDayOfWeek();
        LocalDateTime ahora = LocalDateTime.now();

        List<Cita> citasDelDia = citaRepo.findByDoctorIdAndFecha(doctorId, fecha).stream()
                .filter(c -> (excludeCitaId == null || !excludeCitaId.equals(c.getId()))
                        && c.getEstado() != EstadoCita.CANCELADA
                        && c.getEstado() != EstadoCita.NO_ASISTIO)
                .toList();

        for (HorarioAtencion horario : horarios) {
            if (horario.getDiaSemana() == null || horario.getHoraInicio() == null || horario.getHoraFin() == null) continue;
            if (!horario.getDiaSemana().equals(dia)) continue;

            int duracion = horario.getDuracionCitaMinutos() > 0 ? horario.getDuracionCitaMinutos() : 30;
            LocalTime current = horario.getHoraInicio();

            while (!current.plusMinutes(duracion).isAfter(horario.getHoraFin())) {
                final LocalTime slot = current;
                LocalDateTime slotStart = LocalDateTime.of(fecha, slot);
                LocalDateTime slotEnd = slotStart.plusMinutes(duracion);

                boolean esPasado = slotStart.isBefore(ahora);
                boolean esSlotActual = fechaActual != null && horaActual != null
                        && fecha.equals(fechaActual) && slot.equals(horaActual);

                boolean disponible = !esPasado && !esSlotActual && citasDelDia.stream().noneMatch(c -> {
                    LocalDateTime citaStart = LocalDateTime.of(c.getFecha(), c.getHora());
                    LocalDateTime citaEnd = citaStart.plusMinutes(duracion);
                    return slotStart.isBefore(citaEnd) && slotEnd.isAfter(citaStart);
                });

                if (disponible) slots.add(current);
                current = current.plusMinutes(duracion);
            }
        }
        return slots;
    }

    private int duracionDesdeHorarios(List<HorarioAtencion> horarios, LocalDate fecha, LocalTime hora) {
        return horarios.stream()
                .filter(h -> h.getDiaSemana() != null && h.getHoraInicio() != null && h.getHoraFin() != null
                        && h.getDiaSemana().equals(fecha.getDayOfWeek())
                        && !hora.isBefore(h.getHoraInicio())
                        && hora.isBefore(h.getHoraFin()))
                .findFirst()
                .map(HorarioAtencion::getDuracionCitaMinutos)
                .orElse(30);
    }
}
