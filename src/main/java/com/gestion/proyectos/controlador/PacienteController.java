package com.gestion.proyectos.controlador;

import com.gestion.proyectos.modelo.Cita;
import com.gestion.proyectos.modelo.Doctor;
import com.gestion.proyectos.modelo.Paciente;
import com.gestion.proyectos.repositorio.CitaRepositorio;
import com.gestion.proyectos.repositorio.DoctorRepositorio;
import com.gestion.proyectos.repositorio.PacienteRepositorio;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/pacientes")
public class PacienteController {

    private final PacienteRepositorio pacienteRepo;
    private final DoctorRepositorio doctorRepo;
    private final CitaRepositorio citaRepo;

    public PacienteController(PacienteRepositorio pacienteRepo, DoctorRepositorio doctorRepo, CitaRepositorio citaRepo) {
        this.pacienteRepo = pacienteRepo;
        this.doctorRepo = doctorRepo;
        this.citaRepo = citaRepo;
    }

    @GetMapping
    public String listar(Model model) {
        List<Paciente> pacientes = pacienteRepo.findAll();
        model.addAttribute("pacientes", pacientes);
        model.addAttribute("paciente", new Paciente());
        return "pacientes";
    }

    @PostMapping("/guardar")
    public String guardar(@ModelAttribute Paciente paciente) {
        pacienteRepo.save(paciente);
        return "redirect:/pacientes";
    }

    @GetMapping("/landing")
    public String landing(@RequestParam(value = "especialidad", required = false) String especialidad, Model model) {
        List<Doctor> doctores;
        if (especialidad != null && !especialidad.isEmpty()) {
            doctores = doctorRepo.findByEspecialidadContainingIgnoreCase(especialidad);
        } else {
            doctores = doctorRepo.findAll();
        }
        model.addAttribute("doctores", doctores);
        model.addAttribute("especialidades",
                doctorRepo.findAll().stream().map(Doctor::getEspecialidad).filter(e -> e != null).distinct().toList());

        // Mis citas
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            String email = auth.getName();
            Paciente paciente = pacienteRepo.findByEmail(email).orElse(null);
            if (paciente != null) {
                List<Cita> misCitas = citaRepo.findByPacienteId(paciente.getId());
                Map<String, String> doctoresMap = doctorRepo.findAll().stream()
                        .collect(Collectors.toMap(Doctor::getId, d -> "Dr. " + d.getNombre() + " " + d.getApellido()));
                model.addAttribute("misCitas", misCitas);
                model.addAttribute("doctoresMap", doctoresMap);
            }
        }

        return "paciente_landing";
    }
}
