package com.gestion.proyectos.controlador;

import com.gestion.proyectos.servicio.AdminService;
import com.gestion.proyectos.servicio.AyudaService;
import com.gestion.proyectos.servicio.ConsultorioService;
import com.gestion.proyectos.servicio.DevolucionService;
import com.gestion.proyectos.servicio.EstadisticasService;
import com.gestion.proyectos.util.ValidacionUtil;
import org.springframework.beans.factory.annotation.Value;
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
    private final AyudaService ayudaService;
    private final DevolucionService devolucionService;
    private final ConsultorioService consultorioService;
    // Apagada: el panel no muestra la columna "Sitio web" y no se vinculan webs de consultorios.
    private final boolean apiConsultorios;

    public AdminController(AdminService adminService, EstadisticasService estadisticasService,
            AyudaService ayudaService, DevolucionService devolucionService, ConsultorioService consultorioService,
            @Value("${app.api-consultorios.habilitada:false}") boolean apiConsultorios) {
        this.adminService = adminService;
        this.estadisticasService = estadisticasService;
        this.ayudaService = ayudaService;
        this.devolucionService = devolucionService;
        this.consultorioService = consultorioService;
        this.apiConsultorios = apiConsultorios;
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
        model.addAttribute("apiConsultorios", apiConsultorios);
        if (apiConsultorios)
            model.addAttribute("consultorios", consultorioService.porDoctor(
                    doctoresPage.getContent().stream().map(d -> d.getId()).toList()));
        model.addAttribute("pacientes", pacientesPage);
        model.addAttribute("totalDoctores", adminService.contarDoctores());
        model.addAttribute("totalPacientes", adminService.contarPacientes());
        model.addAttribute("admins", adminService.listarAdmins());
        model.addAttribute("mensajesAyuda", ayudaService.pendientes());
        model.addAttribute("temasAyuda", AyudaService.TEMAS);
        model.addAttribute("devolucionesPendientes", devolucionService.contarPendientes());
        model.addAttribute("size", size);
        model.addAttribute("search", search == null ? "" : search);
        model.addAttribute("especialidad", especialidad == null ? "" : especialidad);
        // Cada tabla pagina por su cuenta: el enlace de una conserva la página de la otra y los filtros
        model.addAttribute("queryDoctores", query(size, search, especialidad, "pagePacientes", pagePacientes));
        model.addAttribute("queryPacientes", query(size, search, especialidad, "pageDoctores", pageDoctores));
        return "admin/dashboard";
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
        return "admin/estadisticas";
    }

    // sitioWeb es opcional: si viene, la web del consultorio del médico queda autorizada para usar la API.
    @PostMapping("/doctores/{id}/activar")
    public String activarDoctor(@PathVariable String id, @RequestParam(required = false) String sitioWeb) {
        boolean conSitio = apiConsultorios && !ValidacionUtil.esVacio(sitioWeb);
        if (conSitio && ConsultorioService.normalizarOrigen(sitioWeb) == null)
            return "redirect:/admin?error=urlInvalida";
        adminService.activarDoctor(id);
        return conSitio ? vincular(id, sitioWeb) : "redirect:/admin";
    }

    @PostMapping("/doctores/{id}/sitio")
    public String vincularSitio(@PathVariable String id, @RequestParam(required = false) String sitioWeb) {
        if (!apiConsultorios)
            return "redirect:/admin";
        if (ConsultorioService.normalizarOrigen(sitioWeb) == null)
            return "redirect:/admin?error=urlInvalida";
        return vincular(id, sitioWeb);
    }

    private String vincular(String doctorId, String sitioWeb) {
        return adminService.obtenerDoctor(doctorId)
                .flatMap(d -> consultorioService.vincularSitio(d, sitioWeb))
                // El id del consultorio es un slug (a-z, 0-9, -): no hace falta codificarlo.
                .map(c -> "redirect:/admin?sitioVinculado=" + c.getId())
                .orElse("redirect:/admin?error=urlInvalida");
    }

    @PostMapping("/ayuda/{id}/atendido")
    public String marcarMensajeAtendido(@PathVariable String id) {
        ayudaService.marcarAtendido(id);
        return "redirect:/admin#ayuda";
    }

    @PostMapping("/doctores/{id}/desactivar")
    public String desactivarDoctor(@PathVariable String id) {
        adminService.desactivarDoctor(id);
        return "redirect:/admin";
    }
}
