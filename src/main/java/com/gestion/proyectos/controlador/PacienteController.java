package com.gestion.proyectos.controlador;

import com.gestion.proyectos.modelo.Antecedentes;
import com.gestion.proyectos.modelo.Paciente;
import com.gestion.proyectos.servicio.HistorialClinicoService;
import com.gestion.proyectos.servicio.PacienteService;
import com.gestion.proyectos.servicio.CitaService;
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
    private final CitaService citaService;
    private final HistorialClinicoService historialClinicoService;

    public PacienteController(PacienteService pacienteService, ReservaService reservaService, CitaService citaService,
            HistorialClinicoService historialClinicoService) {
        this.pacienteService = pacienteService;
        this.reservaService = reservaService;
        this.citaService = citaService;
        this.historialClinicoService = historialClinicoService;
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

    @InitBinder("historialPrevio")
    void camposHistorialPrevio(WebDataBinder binder) {
        binder.setAllowedFields("patologicos", "quirurgicos", "farmacologicos", "alergicos", "familiares");
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
                var misCitas = pacienteService.citasDelPaciente(paciente.getId());
                model.addAttribute("misCitas", misCitas);
                // Citas con abono a las que aún les puede pagar el resto en línea
                model.addAttribute("saldosPorPagar", reservaService.saldosPorPagar(misCitas));
                // Turnos apartados que aún esperan el pago del abono
                model.addAttribute("reservasPendientes", reservaService.pendientesDe(paciente.getId()));
                // Citas a las que debe responder si asistirá
                model.addAttribute("citasPorConfirmar", citaService.porConfirmarDe(paciente.getId()));
                model.addAttribute("doctoresMap", pacienteService.mapDoctores());
            });
        }

        return "pacientes/catalogo";
    }

    /** Historial de pagos del paciente: abonos pagados, rechazados, devueltos y en proceso. */
    @GetMapping("/pagos")
    public String pagos(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        var paciente = pacienteService.buscarPorEmail(auth.getName()).orElseThrow();
        model.addAttribute("historial", reservaService.historialDe(paciente.getId()));
        return "pacientes/pagos";
    }

    /** Historial clínico del paciente: todos sus dictámenes y el historial previo que declaró (editable). */
    @GetMapping("/historial")
    public String historialClinico(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        var paciente = pacienteService.buscarPorEmail(auth.getName()).orElseThrow();
        model.addAttribute("historialClinico", historialClinicoService.historialDe(paciente.getId()).orElseThrow());
        model.addAttribute("vistaMedico", false);
        return "historial/clinico";
    }

    @PostMapping("/historial/antecedentes")
    public String guardarHistorialPrevio(@ModelAttribute("historialPrevio") Antecedentes datos,
            @RequestParam(required = false) String notas) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        var paciente = pacienteService.buscarPorEmail(auth.getName()).orElseThrow();
        String error = historialClinicoService.guardarDeclarado(paciente, datos, notas);
        return error == null ? "redirect:/pacientes/historial?success=historial_guardado#mi-historial-previo"
                : "redirect:/pacientes/historial?error=historial_largo#mi-historial-previo";
    }
}
