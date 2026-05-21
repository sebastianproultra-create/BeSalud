package com.gestion.proyectos.controlador;

import com.gestion.proyectos.modelo.Paciente;
import com.gestion.proyectos.servicio.PacienteService;

import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/pacientes")
public class PacienteController {

    private final PacienteService pacienteService;

    public PacienteController(PacienteService pacienteService) {
        this.pacienteService = pacienteService;
    }

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("pacientes", pacienteService.listarTodos());
        model.addAttribute("paciente", new Paciente());
        return "pacientes";
    }

    @PostMapping("/guardar")
    public String guardar(@ModelAttribute Paciente paciente, Model model) {
        String error = pacienteService.validarYGuardar(paciente);
        if (error != null) {
            model.addAttribute("error", error);
            model.addAttribute("pacientes", pacienteService.listarTodos());
            model.addAttribute("paciente", paciente);
            return "pacientes";
        }
        return "redirect:/pacientes";
    }

    @GetMapping("/landing")
    public String landing(@RequestParam(value = "especialidad", required = false) String especialidad,
                          @RequestParam(value = "search", required = false) String search,
                          @RequestParam(value = "page", defaultValue = "0") int page,
                          @RequestParam(value = "size", defaultValue = "6") int size,
                          Model model, HttpSession session) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String currentEmail = auth != null ? auth.getName() : null;
        boolean modoPaciente = "ROLE_PACIENTE".equals(session.getAttribute("selectedRole"));

        model.addAttribute("doctores", pacienteService.listarDoctoresPaginated(page, size, especialidad, search));
        model.addAttribute("especialidades", pacienteService.especialidadesDisponibles());
        model.addAttribute("especialidad", especialidad);
        model.addAttribute("search", search);
        model.addAttribute("size", size);
        model.addAttribute("modoPaciente", modoPaciente);

        if (auth != null && auth.isAuthenticated()) {
            pacienteService.buscarPorEmail(currentEmail).ifPresent(paciente -> {
                model.addAttribute("misCitas", pacienteService.citasDelPaciente(paciente.getId()));
                model.addAttribute("doctoresMap", pacienteService.mapDoctores());
            });
        }

        return "paciente_landing";
    }
}
