package com.gestion.proyectos.controlador;

import com.gestion.proyectos.modelo.Doctor;
import com.gestion.proyectos.modelo.Paciente;
import com.gestion.proyectos.repositorio.DoctorRepositorio;
import com.gestion.proyectos.repositorio.PacienteRepositorio;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/pacientes")
public class PacienteController {

    private final PacienteRepositorio pacienteRepo;
    private final DoctorRepositorio doctorRepo;

    public PacienteController(PacienteRepositorio pacienteRepo, DoctorRepositorio doctorRepo) {
        this.pacienteRepo = pacienteRepo;
        this.doctorRepo = doctorRepo;
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
                doctorRepo.findAll().stream().map(Doctor::getEspecialidad).distinct().toList());
        return "paciente_landing";
    }
}