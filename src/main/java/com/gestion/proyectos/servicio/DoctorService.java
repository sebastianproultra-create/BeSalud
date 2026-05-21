package com.gestion.proyectos.servicio;

import com.gestion.proyectos.modelo.Cita;
import com.gestion.proyectos.modelo.Dictamen;
import com.gestion.proyectos.modelo.Doctor;
import com.gestion.proyectos.modelo.EstadoCita;
import com.gestion.proyectos.modelo.HorarioAtencion;
import com.gestion.proyectos.modelo.Paciente;
import com.gestion.proyectos.repositorio.CitaRepositorio;
import com.gestion.proyectos.repositorio.HorarioAtencionRepositorio;
import com.gestion.proyectos.repositorio.PersonaRepositorio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class DoctorService {

    private static final Logger log = LoggerFactory.getLogger(DoctorService.class);

    private final PersonaRepositorio personaRepo;
    private final HorarioAtencionRepositorio horarioRepo;
    private final CitaRepositorio citaRepo;

    public DoctorService(PersonaRepositorio personaRepo, HorarioAtencionRepositorio horarioRepo,
            CitaRepositorio citaRepo) {
        this.personaRepo = personaRepo;
        this.horarioRepo = horarioRepo;
        this.citaRepo = citaRepo;
    }

    // ── Consultas ────────────────────────────────────────────────────────────

    public Optional<Doctor> buscarPorEmail(String email) {
        return personaRepo.findDoctorByEmail(email);
    }

    public Optional<Doctor> buscarPorId(String id) {
        return personaRepo.findById(id).map(p -> (Doctor) p);
    }

    public List<Doctor> listarTodos() {
        return personaRepo.findAllDoctores();
    }

    public List<Doctor> listarPorEspecialidad(String especialidad) {
        return personaRepo.findDoctoresByEspecialidadContainingIgnoreCase(especialidad);
    }

    public Page<Doctor> listarDoctoresPaginated(int page, int size, String especialidad, String search) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size));
        boolean hasSearch = search != null && !search.isBlank();
        boolean hasEspecialidad = especialidad != null && !especialidad.isBlank();

        if (hasSearch && hasEspecialidad) {
            return personaRepo.searchDoctoresCombinado(search.trim(), especialidad.trim(), pageable);
        } else if (hasSearch) {
            return personaRepo.searchDoctoresByNombreApellidoEmail(search.trim(), pageable);
        } else if (hasEspecialidad) {
            return personaRepo.findDoctoresByEspecialidadContainingIgnoreCase(especialidad.trim(), pageable);
        } else {
            return personaRepo.findAllDoctores(pageable);
        }
    }

    public List<HorarioAtencion> horariosDelDoctor(String doctorId) {
        return horarioRepo.findByDoctorId(doctorId);
    }

    // ── Dashboard data ───────────────────────────────────────────────────────

    public List<Cita> citasDelDoctor(String doctorId, String filtroEstado) {
        if (filtroEstado == null || filtroEstado.isEmpty() || "TODAS".equals(filtroEstado)) {
            return citaRepo.findByDoctorId(doctorId);
        }
        return citaRepo.findByDoctorIdAndEstado(doctorId, EstadoCita.valueOf(filtroEstado));
    }

    public Map<String, String> mapPacienteNombres(List<Cita> citas) {
        Map<String, String> nombres = new HashMap<>();
        for (Cita c : citas) {
            if (c.getPacienteId() != null && !nombres.containsKey(c.getPacienteId())) {
                personaRepo.findById(c.getPacienteId())
                        .ifPresent(p -> nombres.put(p.getId(), p.getNombre() + " " + p.getApellido()));
            }
        }
        return nombres;
    }

    public Map<String, Map<String, String>> dictamenDataMap(List<Cita> citas) {
        Map<String, Map<String, String>> result = new HashMap<>();
        for (Cita c : citas) {
            if (c.getDictamen() != null) {
                Map<String, String> d = new HashMap<>();
                d.put("diagnostico", c.getDictamen().getDiagnostico() != null ? c.getDictamen().getDiagnostico() : "");
                d.put("tratamiento", c.getDictamen().getTratamiento() != null ? c.getDictamen().getTratamiento() : "");
                d.put("observaciones",
                        c.getDictamen().getObservaciones() != null ? c.getDictamen().getObservaciones() : "");
                result.put(c.getId(), d);
            }
        }
        return result;
    }

    public List<Paciente> pacientesDelDoctor(String doctorId) {
        List<String> ids = citaRepo.findByDoctorId(doctorId).stream()
                .map(Cita::getPacienteId)
                .distinct()
                .toList();
        List<Paciente> pacientes = new ArrayList<>();
        for (String pid : ids) {
            personaRepo.findById(pid).map(p -> (Paciente) p).ifPresent(pacientes::add);
        }
        return pacientes;
    }

    public List<Map<String, Object>> weeklyAvailability(String doctorId) {
        List<HorarioAtencion> horarios = horarioRepo.findByDoctorId(doctorId);
        List<Map<String, Object>> result = new ArrayList<>();
        Locale localeEs = Locale.forLanguageTag("es-CO");
        LocalDate today = LocalDate.now();
        for (int i = 0; i < 7; i++) {
            LocalDate date = today.plusDays(i);
            DayOfWeek dow = date.getDayOfWeek();
            int count = 0;
            for (HorarioAtencion h : horarios) {
                if (h.getDiaSemana() != null && h.getHoraInicio() != null && h.getHoraFin() != null
                        && h.getDiaSemana() == dow && h.getDuracionCitaMinutos() > 0) {
                    LocalTime t = h.getHoraInicio();
                    while (t.isBefore(h.getHoraFin())) {
                        count++;
                        t = t.plusMinutes(h.getDuracionCitaMinutos());
                    }
                }
            }
            String diaNombre = dow.getDisplayName(TextStyle.FULL, localeEs);
            diaNombre = diaNombre.substring(0, 1).toUpperCase(localeEs) + diaNombre.substring(1);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("fecha", date);
            row.put("diaSemana", diaNombre);
            row.put("cantidad", count);
            result.add(row);
        }
        return result;
    }

    public int weeklySlotTotal(List<Map<String, Object>> weeklyAvailability) {
        return weeklyAvailability.stream().mapToInt(r -> (int) r.get("cantidad")).sum();
    }

    public Map<LocalDate, Integer> dailySlotCounts(List<Map<String, Object>> weeklyAvailability) {
        Map<LocalDate, Integer> map = new LinkedHashMap<>();
        for (Map<String, Object> row : weeklyAvailability) {
            map.put((LocalDate) row.get("fecha"), (int) row.get("cantidad"));
        }
        return map;
    }

    // ── Doctor CRUD ──────────────────────────────────────────────────────────

    public boolean emailDuplicado(String email) {
        return personaRepo.findDoctorByEmail(email.trim()).isPresent();
    }

    public void guardar(Doctor doctor) {
        personaRepo.save(doctor);
        log.info("Doctor guardado: {}", doctor.getEmail());
    }

    public void eliminar(String id) {
        personaRepo.deleteById(id);
        log.info("Doctor eliminado: id={}", id);
    }

    // ── Horarios ─────────────────────────────────────────────────────────────

    /**
     * Reemplaza el horario de un día para el doctor y guarda uno o dos intervalos.
     * Retorna null si OK, o el código de error si hay validación fallida.
     */
    public String guardarHorarioDia(String doctorId, String day, Map<String, String> allParams,
            int duracionCitaMinutos) {
        String startStr = allParams.get("startTimes[" + day + "]");
        String endStr = allParams.get("endTimes[" + day + "]");
        if (startStr == null || startStr.isEmpty() || endStr == null || endStr.isEmpty()) {
            return "missing_time_" + day;
        }

        DayOfWeek diaSemana;
        try {
            diaSemana = DayOfWeek.valueOf(day);
        } catch (IllegalArgumentException e) {
            return "dia_invalido_" + day;
        }

        LocalTime horaInicio;
        LocalTime horaFin;
        try {
            horaInicio = LocalTime.parse(startStr);
            horaFin = LocalTime.parse(endStr);
        } catch (Exception e) {
            return "formato_hora_" + day;
        }

        if (!horaInicio.isBefore(horaFin))
            return "invalid_time_" + day;

        horarioRepo.findByDoctorIdAndDiaSemana(doctorId, diaSemana)
                .forEach(h -> horarioRepo.deleteById(h.getId()));

        HorarioAtencion horario = new HorarioAtencion(doctorId, diaSemana, horaInicio, horaFin);
        horario.setDuracionCitaMinutos(duracionCitaMinutos);
        horarioRepo.save(horario);

        String start2 = allParams.get("startTimes2[" + day + "]");
        String end2 = allParams.get("endTimes2[" + day + "]");
        if ((start2 != null && !start2.isEmpty()) || (end2 != null && !end2.isEmpty())) {
            if (start2 == null || start2.isEmpty() || end2 == null || end2.isEmpty()) {
                return "missing_time2_" + day;
            }
            LocalTime horaInicio2;
            LocalTime horaFin2;
            try {
                horaInicio2 = LocalTime.parse(start2);
                horaFin2 = LocalTime.parse(end2);
            } catch (Exception e) {
                return "formato_hora2_" + day;
            }
            if (!horaInicio2.isBefore(horaFin2))
                return "invalid_time2_" + day;
            if (horaInicio2.isBefore(horaFin) && horaFin2.isAfter(horaInicio)) {
                return "solapamiento_intervalos_" + day;
            }
            HorarioAtencion horario2 = new HorarioAtencion(doctorId, diaSemana, horaInicio2, horaFin2);
            horario2.setDuracionCitaMinutos(duracionCitaMinutos);
            horarioRepo.save(horario2);
        }
        return null;
    }

    public void eliminarHorario(String horarioId, String doctorId) {
        HorarioAtencion horario = horarioRepo.findById(horarioId).orElseThrow();
        if (!horario.getDoctorId().equals(doctorId))
            throw new AccessDeniedException("No autorizado");
        horarioRepo.deleteById(horarioId);
    }

    // ── Estado de citas ──────────────────────────────────────────────────────

    public void marcarAsistio(String citaId, String doctorId) {
        Cita cita = citaRepo.findById(citaId).orElseThrow();
        if (!cita.getDoctorId().equals(doctorId))
            throw new AccessDeniedException("No autorizado");
        cita.setEstado(EstadoCita.ASISTIO);
        citaRepo.save(cita);
        log.info("Cita {} marcada ASISTIO por doctor {}", citaId, doctorId);
    }

    public void marcarNoAsistio(String citaId, String doctorId) {
        Cita cita = citaRepo.findById(citaId).orElseThrow();
        if (!cita.getDoctorId().equals(doctorId))
            throw new AccessDeniedException("No autorizado");
        cita.setEstado(EstadoCita.NO_ASISTIO);
        citaRepo.save(cita);
        log.info("Cita {} marcada NO_ASISTIO por doctor {}", citaId, doctorId);
    }

    public void cancelarCita(String citaId, String doctorId) {
        Cita cita = citaRepo.findById(citaId).orElseThrow();
        if (!cita.getDoctorId().equals(doctorId))
            throw new AccessDeniedException("No autorizado");
        cita.setEstado(EstadoCita.CANCELADA);
        citaRepo.save(cita);
        log.info("Cita {} CANCELADA por doctor {}", citaId, doctorId);
    }

    public void guardarDictamen(String citaId, String doctorId,
            String diagnostico, String tratamiento, String observaciones) {
        Cita cita = citaRepo.findById(citaId).orElseThrow();
        if (!cita.getDoctorId().equals(doctorId))
            throw new AccessDeniedException("No autorizado");
        Dictamen dictamen = new Dictamen();
        dictamen.setDiagnostico(diagnostico);
        dictamen.setTratamiento(tratamiento);
        dictamen.setObservaciones(observaciones);
        cita.setDictamen(dictamen);
        cita.setEstado(EstadoCita.COMPLETADA);
        citaRepo.save(cita);
        log.info("Dictamen guardado para cita {} por doctor {}", citaId, doctorId);
    }
}
