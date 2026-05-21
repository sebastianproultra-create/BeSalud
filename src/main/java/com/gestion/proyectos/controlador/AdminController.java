package com.gestion.proyectos.controlador;

import com.gestion.proyectos.servicio.AdminService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping
    public String dashboard(Model model) {
        model.addAttribute("doctores", adminService.listarDoctores());
        model.addAttribute("pacientes", adminService.listarPacientes());
        model.addAttribute("admins", adminService.listarAdmins());
        return "admin_dashboard";
    }

    @PostMapping("/doctores/{id}/activar")
    public String activarDoctor(@PathVariable String id) {
        adminService.activarDoctor(id);
        return "redirect:/admin";
    }

    @PostMapping("/doctores/{id}/desactivar")
    public String desactivarDoctor(@PathVariable String id) {
        adminService.desactivarDoctor(id);
        return "redirect:/admin";
    }
}