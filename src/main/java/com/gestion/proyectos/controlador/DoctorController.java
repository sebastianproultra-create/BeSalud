package com.gestion.proyectos.controlador;

import com.gestion.proyectos.modelo.Cita;
import com.gestion.proyectos.modelo.Dictamen;
import com.gestion.proyectos.modelo.Doctor;
import com.gestion.proyectos.modelo.EstadoCita;
import com.gestion.proyectos.modelo.HorarioAtencion;
import com.gestion.proyectos.modelo.Paciente;
import com.gestion.proyectos.repositorio.CitaRepositorio;
import com.gestion.proyectos.repositorio.DoctorRepositorio;
import com.gestion.proyectos.repositorio.HorarioAtencionRepositorio;
import com.gestion.proyectos.repositorio.PacienteRepositorio;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/doctores")
public class DoctorController {

    private final DoctorRepositorio doctorRepo;
    private final HorarioAtencionRepositorio horarioRepo;
    private final CitaRepositorio citaRepo;
    private final PacienteRepositorio pacienteRepo;

    public DoctorController(DoctorRepositorio doctorRepo, HorarioAtencionRepositorio horarioRepo,
                            CitaRepositorio citaRepo, PacienteRepositorio pacienteRepo) {
        this.doctorRepo = doctorRepo;
        this.horarioRepo = horarioRepo;
        this.citaRepo = citaRepo;
        this.pacienteRepo = pacienteRepo;
    }

    @GetMapping
    public String listar(Model model,
                         @RequestParam(required = false) String especialidad,
                         @RequestParam(required = false) String estado) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String role = auth.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .findFirst()
                .orElse("");

        if ("ROLE_DOCTOR".equals(role)) {
            // Dashboard del doctor
            String email = auth.getName();
            Doctor doctor = doctorRepo.findByEmail(email).orElseThrow();
            List<HorarioAtencion> horarios = horarioRepo.findByDoctorId(doctor.getId());
            model.addAttribute("doctor", doctor);
            model.addAttribute("horarios", horarios);
            model.addAttribute("horario", new HorarioAtencion());

            // Citas del doctor filtradas por estado
            String filtroEstado = (estado != null) ? estado : "";
            List<Cita> citas;
            if (filtroEstado.isEmpty() || "TODAS".equals(filtroEstado)) {
                citas = citaRepo.findByDoctorId(doctor.getId());
            } else {
                citas = citaRepo.findByDoctorIdAndEstado(doctor.getId(), EstadoCita.valueOf(filtroEstado));
            }
            // Mapear pacienteId -> nombre completo para mostrar en la tabla
            Map<String, String> pacienteNombres = new java.util.HashMap<>();
            for (Cita c : citas) {
                if (c.getPacienteId() != null && !pacienteNombres.containsKey(c.getPacienteId())) {
                    pacienteRepo.findById(c.getPacienteId()).ifPresent(p ->
                        pacienteNombres.put(p.getId(), p.getNombre() + " " + p.getApellido())
                    );
                }
            }
            model.addAttribute("citas", citas);
            model.addAttribute("pacienteNombres", pacienteNombres);
            model.addAttribute("filtroEstado", filtroEstado);
            model.addAttribute("hoy", LocalDate.now());

            // Pacientes del doctor (los que tienen cita con este doctor)
            List<String> pacienteIds = citaRepo.findByDoctorId(doctor.getId()).stream()
                    .map(Cita::getPacienteId)
                    .distinct()
                    .collect(Collectors.toList());
            List<Paciente> misPacientes = new ArrayList<>();
            for (String pid : pacienteIds) {
                pacienteRepo.findById(pid).ifPresent(misPacientes::add);
            }
            model.addAttribute("misPacientes", misPacientes);

            // Calcular slots disponibles para los próximos 7 días (conteo por día)
            Map<LocalDate, Integer> dailySlotCounts = new LinkedHashMap<>();
            List<Map<String, Object>> weeklyAvailability = new ArrayList<>();
            Locale localeEs = Locale.forLanguageTag("es-CO");
            int weeklyTotal = 0;
            LocalDate today = LocalDate.now();
            for (int i = 0; i < 7; i++) {
                LocalDate date = today.plusDays(i);
                DayOfWeek dow = date.getDayOfWeek();
                int countForDay = 0;
                for (HorarioAtencion h : horarios) {
                    if (h.getDiaSemana() != null && h.getHoraInicio() != null && h.getHoraFin() != null &&
                            h.getDiaSemana() == dow && h.getDuracionCitaMinutos() > 0) {
                        LocalTime start = h.getHoraInicio();
                        LocalTime end = h.getHoraFin();
                        while (start.isBefore(end)) {
                            countForDay++;
                            start = start.plusMinutes(h.getDuracionCitaMinutos());
                        }
                    }
                }
                dailySlotCounts.put(date, countForDay);
                String diaSemanaEs = date.getDayOfWeek().getDisplayName(TextStyle.FULL, localeEs);
                diaSemanaEs = diaSemanaEs.substring(0, 1).toUpperCase(localeEs) + diaSemanaEs.substring(1);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("fecha", date);
                row.put("diaSemana", diaSemanaEs);
                row.put("cantidad", countForDay);
                weeklyAvailability.add(row);
                weeklyTotal += countForDay;
            }
            model.addAttribute("dailySlotCounts", dailySlotCounts);
            model.addAttribute("weeklyAvailability", weeklyAvailability);
            model.addAttribute("weeklySlotTotal", weeklyTotal);

            return "doctor_dashboard";
        } else {
            // Vista admin: lista todos los doctores
            List<Doctor> doctores;
            if (especialidad != null && !especialidad.isEmpty()) {
                doctores = doctorRepo.findByEspecialidadContainingIgnoreCase(especialidad);
            } else {
                doctores = doctorRepo.findAll();
            }
            model.addAttribute("doctores", doctores);
            model.addAttribute("filtroEspecialidad", especialidad == null ? "" : especialidad);
            model.addAttribute("doctor", new Doctor());
            return "doctores";
        }
    }

    @PostMapping("/guardar")
    public String guardar(@ModelAttribute Doctor doctor) {
        if (esVacio(doctor.getNombre())) return "redirect:/doctores?error=nombre_requerido";
        if (esVacio(doctor.getApellido())) return "redirect:/doctores?error=apellido_requerido";
        if (esVacio(doctor.getEmail())) return "redirect:/doctores?error=email_requerido";
        if (!doctor.getEmail().trim().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))
            return "redirect:/doctores?error=email_invalido";
        if (esVacio(doctor.getEspecialidad())) return "redirect:/doctores?error=especialidad_requerida";
        boolean esNuevo = esVacio(doctor.getId());
        if (esNuevo && doctorRepo.findByEmail(doctor.getEmail().trim()).isPresent())
            return "redirect:/doctores?error=email_duplicado";
        doctorRepo.save(doctor);
        return "redirect:/doctores";
    }

    private boolean esVacio(String s) {
        return s == null || s.trim().isEmpty();
    }

    @GetMapping("/{id}/horarios")
    public String verHorarios(@PathVariable String id, Model model) {
        Doctor doctor = doctorRepo.findById(id).orElseThrow();
        model.addAttribute("doctor", doctor);
        model.addAttribute("horarios",
                horarioRepo.findAll().stream().filter(h -> doctor.getId().equals(h.getDoctorId())).toList());
        model.addAttribute("horario", new HorarioAtencion());
        return "horarios";
    }

    @PreAuthorize("hasRole('DOCTOR')")
    @PostMapping("/horarios/guardar")
    public String guardarHorario(@RequestParam(required = false) List<String> days,
            @RequestParam Map<String, String> allParams,
            @RequestParam int duracionCitaMinutos) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        Doctor doctor = doctorRepo.findByEmail(email).orElseThrow();

        if (days == null || days.isEmpty()) {
            return "redirect:/doctores?error=no_days_selected";
        }

        for (String day : days) {
            String startStr = allParams.get("startTimes[" + day + "]");
            String endStr = allParams.get("endTimes[" + day + "]");
            if (startStr == null || startStr.isEmpty() || endStr == null || endStr.isEmpty()) {
                return "redirect:/doctores?error=missing_time_" + day;
            }

            DayOfWeek diaSemana = DayOfWeek.valueOf(day);
            LocalTime horaInicio = LocalTime.parse(startStr);
            LocalTime horaFin = LocalTime.parse(endStr);

            if (horaInicio.isAfter(horaFin) || horaInicio.equals(horaFin)) {
                return "redirect:/doctores?error=invalid_time_" + day;
            }

            HorarioAtencion horario = new HorarioAtencion(doctor.getId(), diaSemana, horaInicio, horaFin);
            horario.setDuracionCitaMinutos(duracionCitaMinutos);
            horarioRepo.save(horario);

            // Segundo intervalo si existe
            String start2Str = allParams.get("startTimes2[" + day + "]");
            String end2Str = allParams.get("endTimes2[" + day + "]");
            if ((start2Str != null && !start2Str.isEmpty()) || (end2Str != null && !end2Str.isEmpty())) {
                if (start2Str == null || start2Str.isEmpty() || end2Str == null || end2Str.isEmpty()) {
                    return "redirect:/doctores?error=missing_time_" + day;
                }
                LocalTime horaInicio2 = LocalTime.parse(start2Str);
                LocalTime horaFin2 = LocalTime.parse(end2Str);

                if (horaInicio2.isAfter(horaFin2) || horaInicio2.equals(horaFin2)) {
                    return "redirect:/doctores?error=invalid_time2_" + day;
                }

                HorarioAtencion horario2 = new HorarioAtencion(doctor.getId(), diaSemana, horaInicio2, horaFin2);
                horario2.setDuracionCitaMinutos(duracionCitaMinutos);
                horarioRepo.save(horario2);
            }
        }
        return "redirect:/doctores";
    }

    @PreAuthorize("hasRole('DOCTOR')")
    @PostMapping("/horarios/{id}/eliminar")
    public String eliminarHorario(@PathVariable String id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        Doctor doctor = doctorRepo.findByEmail(email).orElseThrow();

        HorarioAtencion horario = horarioRepo.findById(id).orElseThrow();
        if (!horario.getDoctorId().equals(doctor.getId())) {
            throw new RuntimeException("No autorizado");
        }

        horarioRepo.deleteById(id);
        return "redirect:/doctores";
    }

    @PreAuthorize("hasRole('DOCTOR')")
    @PostMapping("/citas/{id}/asistio")
    public String marcarAsistio(@PathVariable String id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        Doctor doctor = doctorRepo.findByEmail(email).orElseThrow();

        Cita cita = citaRepo.findById(id).orElseThrow();
        if (!cita.getDoctorId().equals(doctor.getId())) {
            throw new RuntimeException("No autorizado");
        }
        cita.setEstado(EstadoCita.ASISTIO);
        citaRepo.save(cita);
        return "redirect:/doctores";
    }

    @PreAuthorize("hasRole('DOCTOR')")
    @PostMapping("/citas/{id}/no-asistio")
    public String marcarNoAsistio(@PathVariable String id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        Doctor doctor = doctorRepo.findByEmail(email).orElseThrow();

        Cita cita = citaRepo.findById(id).orElseThrow();
        if (!cita.getDoctorId().equals(doctor.getId())) {
            throw new RuntimeException("No autorizado");
        }
        cita.setEstado(EstadoCita.NO_ASISTIO);
        citaRepo.save(cita);
        return "redirect:/doctores";
    }

    @PreAuthorize("hasRole('DOCTOR')")
    @PostMapping("/citas/{id}/dictamen")
    public String guardarDictamen(@PathVariable String id,
                                   @RequestParam String diagnostico,
                                   @RequestParam String tratamiento,
                                   @RequestParam(required = false) String observaciones) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        Doctor doctor = doctorRepo.findByEmail(email).orElseThrow();

        Cita cita = citaRepo.findById(id).orElseThrow();
        if (!cita.getDoctorId().equals(doctor.getId())) {
            throw new RuntimeException("No autorizado");
        }

        Dictamen dictamen = new Dictamen();
        dictamen.setDiagnostico(diagnostico);
        dictamen.setTratamiento(tratamiento);
        dictamen.setObservaciones(observaciones);
        cita.setDictamen(dictamen);
        cita.setEstado(EstadoCita.COMPLETADA);
        citaRepo.save(cita);
        return "redirect:/doctores";
    }

    @PreAuthorize("hasRole('DOCTOR')")
    @PostMapping("/citas/{id}/cancelar")
    public String cancelarCita(@PathVariable String id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        Doctor doctor = doctorRepo.findByEmail(email).orElseThrow();

        Cita cita = citaRepo.findById(id).orElseThrow();
        if (!cita.getDoctorId().equals(doctor.getId())) {
            throw new RuntimeException("No autorizado");
        }
        cita.setEstado(EstadoCita.CANCELADA);
        citaRepo.save(cita);
        return "redirect:/doctores";
    }
}