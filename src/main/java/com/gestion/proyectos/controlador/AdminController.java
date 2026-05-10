package com.gestion.proyectos.controlador;

import com.gestion.proyectos.modelo.Admin;
import com.gestion.proyectos.modelo.Doctor;
import com.gestion.proyectos.modelo.Paciente;
import com.gestion.proyectos.repositorio.AdminRepositorio;
import com.gestion.proyectos.repositorio.DoctorRepositorio;
import com.gestion.proyectos.repositorio.PacienteRepositorio;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final AdminRepositorio adminRepo;
    private final DoctorRepositorio doctorRepo;
    private final PacienteRepositorio pacienteRepo;

    public AdminController(AdminRepositorio adminRepo, DoctorRepositorio doctorRepo, PacienteRepositorio pacienteRepo) {
        this.adminRepo = adminRepo;
        this.doctorRepo = doctorRepo;
        this.pacienteRepo = pacienteRepo;
    }

    @GetMapping
    public String dashboard(Model model) {
        List<Doctor> doctores = doctorRepo.findAllDoctores();
        List<Paciente> pacientes = pacienteRepo.findAll();
        List<Admin> admins = adminRepo.findAll();

        model.addAttribute("doctores", doctores);
        model.addAttribute("pacientes", pacientes);
        model.addAttribute("admins", admins);

        return "admin_dashboard";
    }
}