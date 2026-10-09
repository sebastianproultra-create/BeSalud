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
import org.springframework.web.util.UriComponentsBuilder;

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
            @RequestParam(defaultValue = "0") int pageDoctores,
            @RequestParam(defaultValue = "0") int pagePacientes,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String especialidad,
            @RequestParam(required = false) String search) {

        var doctoresPage = adminService.listarDoctoresPaginated(pageDoctores, size, especialidad, search);
        var pacientesPage = adminService.listarPacientesPaginated(pagePacientes, size);

        model.addAttribute("doctores", doctoresPage);
        model.addAttribute("pacientes", pacientesPage);
        model.addAttribute("totalDoctores", adminService.contarDoctores());
        model.addAttribute("totalPacientes", adminService.contarPacientes());
        model.addAttribute("admins", adminService.listarAdmins());
        model.addAttribute("size", size);
        model.addAttribute("search", search == null ? "" : search);
        model.addAttribute("especialidad", especialidad == null ? "" : especialidad);
        // Cada tabla pagina por su cuenta: el enlace de una conserva la página de la otra y los filtros
        model.addAttribute("queryDoctores", query(size, search, especialidad, "pagePacientes", pagePacientes));
        model.addAttribute("queryPacientes", query(size, search, especialidad, "pageDoctores", pageDoctores));
        return "admin_dashboard";
    }

    private static String query(int size, String search, String especialidad, String otraPagina, int valorOtra) {
        UriComponentsBuilder b = UriComponentsBuilder.newInstance()
                .queryParam("size", size)
                .queryParam(otraPagina, valorOtra);
        if (search != null && !search.isBlank())
            b.queryParam("search", search.trim());
        if (especialidad != null && !especialidad.isBlank())
            b.queryParam("especialidad", especialidad.trim());
        return b.build().encode().getQuery();
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
