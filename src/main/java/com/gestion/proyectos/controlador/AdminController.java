package com.gestion.proyectos.controlador;

import com.gestion.proyectos.servicio.AdminService;
import com.gestion.proyectos.servicio.EstadisticasService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final AdminService adminService;
    private final EstadisticasService estadisticasService;

    public AdminController(AdminService adminService, EstadisticasService estadisticasService) {
        this.adminService = adminService;
        this.estadisticasService = estadisticasService;
    }

    @GetMapping
    public String dashboard(Model model,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String especialidad,
            @RequestParam(required = false) String search) {

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

    @GetMapping("/estadisticas")
    public String estadisticas(Model model) {
        model.addAttribute("est", estadisticasService.calcular());
        return "estadisticas";
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