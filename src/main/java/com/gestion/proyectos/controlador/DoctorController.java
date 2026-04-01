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

import java.time.LocalDateTime;
import java.util.List;

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
    public String guardarHorario(@RequestParam String inicio,
            @RequestParam String fin,
            @RequestParam int duracionCitaMinutos) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        Doctor doctor = doctorRepo.findByEmail(email).orElseThrow();

        LocalDateTime inicioDate = LocalDateTime.parse(inicio);
        LocalDateTime finDate = LocalDateTime.parse(fin);
        HorarioAtencion horario = new HorarioAtencion(doctor.getId(), inicioDate, finDate);
        horario.setDuracionCitaMinutos(duracionCitaMinutos);
        horarioRepo.save(horario);
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