package com.gestion.proyectos.controlador;

import com.gestion.proyectos.modelo.Paciente;
import com.gestion.proyectos.servicio.PacienteService;
import com.gestion.proyectos.servicio.ReservaService;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/pacientes")
public class PacienteController {

    private final PacienteService pacienteService;
    private final ReservaService reservaService;

    public PacienteController(PacienteService pacienteService, ReservaService reservaService) {
        this.pacienteService = pacienteService;
        this.reservaService = reservaService;
    }

    @GetMapping
    public String listar(@RequestParam(defaultValue = "0") int page,
                         @RequestParam(defaultValue = "10") int size,
                         Model model) {
        var pagina = pacienteService.listarPaginado(page, size);
        model.addAttribute("pacientes", pagina);
        model.addAttribute("queryPacientes", "size=" + pagina.getSize());
        model.addAttribute("paciente", new Paciente());
        return "admin/pacientes";
    }

    // Solo los campos del formulario: sin esto se podría enviar id (sobrescribir a otro usuario) o role.
    @InitBinder("paciente")
    void camposPermitidos(WebDataBinder binder) {
        binder.setAllowedFields("nombre", "apellido", "telefono", "identificacion", "email", "password");
    }

    @PostMapping("/guardar")
    public String guardar(@ModelAttribute Paciente paciente, Model model) {
        String error = pacienteService.validarYGuardar(paciente);
        if (error != null) {
            model.addAttribute("error", error);
            var pagina = pacienteService.listarPaginado(0, 10);
            model.addAttribute("pacientes", pagina);
            model.addAttribute("queryPacientes", "size=" + pagina.getSize());
            model.addAttribute("paciente", paciente);
            return "admin/pacientes";
        }
        return "redirect:/pacientes";
    }

    @GetMapping("/landing")
    public String landing(@RequestParam(value = "especialidad", required = false) String especialidad,
                          @RequestParam(value = "search", required = false) String search,
                          @RequestParam(value = "page", defaultValue = "0") int page,
                         @RequestParam(value = "size", defaultValue = "5") int size,
                          Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String currentEmail = auth != null ? auth.getName() : null;
        int pageSize = Math.max(1, Math.min(size, 10));

        model.addAttribute("doctores", pacienteService.listarDoctoresPaginated(page, pageSize, especialidad, search));
        model.addAttribute("especialidades", pacienteService.especialidadesDisponibles());
        model.addAttribute("especialidad", especialidad);
        model.addAttribute("search", search);
        model.addAttribute("size", pageSize);

        if (auth != null && auth.isAuthenticated()) {
            pacienteService.buscarPorEmail(currentEmail).ifPresent(paciente -> {
                String nombre = (paciente.getNombre() == null ? "" : paciente.getNombre())
                        + " " + (paciente.getApellido() == null ? "" : paciente.getApellido());
                model.addAttribute("pacienteNombre", nombre.isBlank() ? "Paciente" : nombre.trim());
                model.addAttribute("misCitas", pacienteService.citasDelPaciente(paciente.getId()));
                // Turnos apartados que aún esperan el pago del abono
                model.addAttribute("reservasPendientes", reservaService.pendientesDe(paciente.getId()));
                model.addAttribute("doctoresMap", pacienteService.mapDoctores());
            });
        }

        return "pacientes/catalogo";
    }
}
