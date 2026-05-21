package com.gestion.proyectos.servicio;

import com.gestion.proyectos.modelo.Cita;
import com.gestion.proyectos.modelo.Doctor;
import com.gestion.proyectos.modelo.EstadoCita;
import com.gestion.proyectos.modelo.HorarioAtencion;
import com.gestion.proyectos.modelo.Paciente;
import com.gestion.proyectos.repositorio.CitaRepositorio;
import com.gestion.proyectos.repositorio.HorarioAtencionRepositorio;
import com.gestion.proyectos.repositorio.PersonaRepositorio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
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

    public CitaService(CitaRepositorio citaRepo, PersonaRepositorio personaRepo,
                       HorarioAtencionRepositorio horarioRepo) {
        this.citaRepo = citaRepo;
        this.personaRepo = personaRepo;
        this.horarioRepo = horarioRepo;
    }

    public List<Cita> listarPorRol(String role, String email, String pacienteId, String doctorId) {
        if ("ROLE_DOCTOR".equals(role)) {
            Doctor doctor = personaRepo.findDoctorByEmail(email).orElse(null);
            return doctor != null ? citaRepo.findByDoctorId(doctor.getId()) : List.of();
        }
        if ("ROLE_PACIENTE".equals(role)) {
            Paciente paciente = personaRepo.findPacienteByEmail(email).orElse(null);
            return paciente != null ? citaRepo.findByPacienteId(paciente.getId()) : List.of();
        }
        if (pacienteId != null && !pacienteId.isBlank()) return citaRepo.findByPacienteId(pacienteId);
        if (doctorId != null && !doctorId.isBlank()) return citaRepo.findByDoctorId(doctorId);
        return citaRepo.findAll();
    }

    public Map<String, String> mapDoctores() {
        return personaRepo.findAllDoctores().stream()
                .collect(Collectors.toMap(Doctor::getId, d -> d.getNombre() + " " + d.getApellido()));
    }

    public Map<String, String> mapPacientes() {
        return personaRepo.findAllPacientes().stream()
                .collect(Collectors.toMap(Paciente::getId, p -> p.getNombre() + " " + p.getApellido()));
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
        Cita cita = new Cita();
        cita.setDoctorId(doctorId);
        cita.setPacienteId(pacienteId);
        cita.setFecha(fecha);
        cita.setHora(hora);
        cita.setMotivo(motivo.trim());
        Cita guardada = citaRepo.save(cita);
        log.info("Cita creada: id={} doctor={} paciente={} fecha={} hora={}", guardada.getId(), doctorId, pacienteId, fecha, hora);
        return guardada;
    }

    public void reprogramar(Cita cita, LocalDate fecha, LocalTime hora) {
        cita.setFecha(fecha);
        cita.setHora(hora);
        citaRepo.save(cita);
        log.info("Cita reprogramada: id={} nueva fecha={} hora={}", cita.getId(), fecha, hora);
    }

    public void cancelar(Cita cita) {
        cita.setEstado(EstadoCita.CANCELADA);
        citaRepo.save(cita);
        log.info("Cita cancelada: id={}", cita.getId());
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
        return personaRepo.findById(id).map(p -> (Doctor) p);
    }

    public List<Doctor> listarDoctores() {
        return personaRepo.findAllDoctores();
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
        List<Cita> existentes = citaRepo.findByDoctorIdAndFecha(doctorId, fecha).stream()
                .filter(c -> (excludeCitaId == null || !excludeCitaId.equals(c.getId()))
                        && c.getEstado() != EstadoCita.CANCELADA
                        && c.getEstado() != EstadoCita.NO_ASISTIO)
                .toList();

        LocalDateTime inicioNuevo = LocalDateTime.of(fecha, hora);
        LocalDateTime finNuevo = inicioNuevo.plusMinutes(obtenerDuracion(doctorId, fecha, hora));

        return existentes.stream().anyMatch(c -> {
            LocalDateTime inicio = LocalDateTime.of(c.getFecha(), c.getHora());
            LocalDateTime fin = inicio.plusMinutes(obtenerDuracion(doctorId, c.getFecha(), c.getHora()));
            return inicioNuevo.isBefore(fin) && finNuevo.isAfter(inicio);
        });
    }

    private List<LocalTime> calcularSlotsDisponibles(String doctorId, LocalDate fecha, String excludeCitaId,
                                                      LocalDate fechaActual, LocalTime horaActual) {
        List<HorarioAtencion> horarios = horarioRepo.findByDoctorId(doctorId);
        List<LocalTime> slots = new ArrayList<>();
        java.time.DayOfWeek dia = fecha.getDayOfWeek();

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

            while (current.isBefore(horario.getHoraFin())) {
                final LocalTime slot = current;
                LocalDateTime slotStart = LocalDateTime.of(fecha, slot);
                LocalDateTime slotEnd = slotStart.plusMinutes(duracion);

                boolean esSlotActual = fechaActual != null && horaActual != null
                        && fecha.equals(fechaActual) && slot.equals(horaActual);

                boolean disponible = !esSlotActual && citasDelDia.stream().noneMatch(c -> {
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

    private int obtenerDuracion(String doctorId, LocalDate fecha, LocalTime hora) {
        return horarioRepo.findByDoctorId(doctorId).stream()
                .filter(h -> h.getDiaSemana() != null && h.getHoraInicio() != null && h.getHoraFin() != null
                        && h.getDiaSemana().equals(fecha.getDayOfWeek())
                        && !hora.isBefore(h.getHoraInicio())
                        && hora.isBefore(h.getHoraFin()))
                .findFirst()
                .map(HorarioAtencion::getDuracionCitaMinutos)
                .orElse(30);
    }
}
