package com.gestion.proyectos.controlador;

import com.gestion.proyectos.modelo.Doctor;
import com.gestion.proyectos.modelo.HorarioAtencion;
import com.gestion.proyectos.repositorio.DoctorRepositorio;
import com.gestion.proyectos.repositorio.HorarioAtencionRepositorio;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/doctores")
public class DoctorController {

    private final DoctorRepositorio doctorRepo;
    private final HorarioAtencionRepositorio horarioRepo;

    public DoctorController(DoctorRepositorio doctorRepo, HorarioAtencionRepositorio horarioRepo) {
        this.doctorRepo = doctorRepo;
        this.horarioRepo = horarioRepo;
    }

    @GetMapping
    public String listar(Model model, @RequestParam(required = false) String especialidad) {
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

            // Calcular slots disponibles para los próximos 7 días (conteo por día)
            Map<LocalDate, Integer> dailySlotCounts = new LinkedHashMap<>();
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
                weeklyTotal += countForDay;
            }
            model.addAttribute("dailySlotCounts", dailySlotCounts);
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
        doctorRepo.save(doctor);
        return "redirect:/doctores";
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
}