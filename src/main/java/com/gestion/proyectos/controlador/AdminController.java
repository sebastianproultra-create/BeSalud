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
    public String dashboard(Model model,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "10") int size,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String especialidad,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String search) {

        var doctoresPage = adminService.listarDoctoresPaginated(page, size, especialidad, search);
        var pacientesPage = adminService.listarPacientesPaginated(page, size);

        model.addAttribute("doctores", doctoresPage);
        model.addAttribute("pacientes", pacientesPage);
        model.addAttribute("admins", adminService.listarAdmins());
        model.addAttribute("page", page);
        model.addAttribute("size", size);
        model.addAttribute("search", search == null ? "" : search);
        model.addAttribute("especialidad", especialidad == null ? "" : especialidad);
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