package com.gestion.proyectos.controlador;

import static com.gestion.proyectos.util.ValidacionUtil.esVacio;

import com.gestion.proyectos.modelo.Cita;
import com.gestion.proyectos.modelo.Doctor;
import com.gestion.proyectos.modelo.Paciente;
import com.gestion.proyectos.servicio.CitaService;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.time.LocalTime;

@Controller
@RequestMapping("/citas")
public class CitaController {

    private static final String REDIRECT_PACIENTES_LANDING = "redirect:/pacientes/landing";
    private static final String REDIRECT_LOGIN = "redirect:/login";
    private static final String REDIRECT_CITAS = "redirect:/citas";

    private final CitaService citaService;

    public CitaController(CitaService citaService) {
        this.citaService = citaService;
    }

    @GetMapping
    public String listar(@RequestParam(required = false) String pacienteId,
                         @RequestParam(required = false) String doctorId,
                         Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String role = auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).findFirst().orElse("");
        String email = auth.getName();

        model.addAttribute("citas", citaService.listarPorRol(role, email, pacienteId, doctorId));
        model.addAttribute("doctoresMap", citaService.mapDoctores());
        model.addAttribute("pacientesMap", citaService.mapPacientes());
        return "citas";
    }

    @GetMapping("/crear")
    public String crear(Model model) {
        model.addAttribute("cita", new Cita());
        model.addAttribute("doctores", citaService.listarDoctores());
        model.addAttribute("pacientes", citaService.listarPacientes());
        return "cita_form";
    }

    @GetMapping("/nueva")
    public String nueva(@RequestParam String doctorId, Model model) {
        Doctor doctor = citaService.buscarDoctorPorId(doctorId).orElse(null);
        if (doctor == null) return REDIRECT_PACIENTES_LANDING;

        model.addAttribute("doctorSeleccionado", doctor);
        model.addAttribute("cita", null);
        model.addAttribute("actionUrl", "/citas/guardar-paciente");
        model.addAttribute("pageTitle", "Agendar Cita Médica");
        model.addAttribute("submitLabel", "Confirmar Cita");
        model.addAttribute("backLink", "/pacientes/landing");
        model.addAttribute("slotsDisponibles", citaService.slotsDisponibles(doctorId, null, null, null));
        return "cita_form_paciente";
    }

    @GetMapping("/{id}/reprogramar")
    public String reprogramar(@PathVariable String id, Model model) {
        Cita cita = citaService.buscarPorId(id).orElse(null);
        if (cita == null) return REDIRECT_PACIENTES_LANDING;

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return REDIRECT_LOGIN;

        Paciente paciente = citaService.buscarPacientePorEmail(auth.getName()).orElse(null);
        if (paciente == null || !paciente.getId().equals(cita.getPacienteId())) return REDIRECT_PACIENTES_LANDING;

        Doctor doctor = citaService.buscarDoctorPorId(cita.getDoctorId()).orElse(null);
        if (doctor == null) return REDIRECT_PACIENTES_LANDING;

        model.addAttribute("doctorSeleccionado", doctor);
        model.addAttribute("cita", cita);
        model.addAttribute("actionUrl", "/citas/" + cita.getId() + "/reprogramar");
        model.addAttribute("pageTitle", "Reprogramar Cita Médica");
        model.addAttribute("submitLabel", "Reprogramar Cita");
        model.addAttribute("backLink", "/pacientes/landing");
        model.addAttribute("slotsDisponibles",
                citaService.slotsDisponibles(doctor.getId(), cita.getId(), cita.getFecha(), cita.getHora()));
        return "cita_form_paciente";
    }

    @PostMapping("/{id}/reprogramar")
    public String guardarReprogramacion(@PathVariable String id,
            @RequestParam String fecha,
            @RequestParam String hora) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return REDIRECT_LOGIN;

        Paciente paciente = citaService.buscarPacientePorEmail(auth.getName()).orElse(null);
        Cita cita = citaService.buscarPorId(id).orElse(null);
        if (paciente == null || cita == null || !paciente.getId().equals(cita.getPacienteId()))
            return REDIRECT_PACIENTES_LANDING;

        if (esVacio(fecha)) return "redirect:/citas/" + id + "/reprogramar?error=fecha_requerida";
        if (esVacio(hora)) return "redirect:/citas/" + id + "/reprogramar?error=hora_requerida";

        LocalDate fechaCita;
        LocalTime horaCita;
        try { fechaCita = LocalDate.parse(fecha.trim()); }
        catch (Exception e) { return "redirect:/citas/" + id + "/reprogramar?error=fecha_invalida"; }
        try { horaCita = LocalTime.parse(hora.trim()); }
        catch (Exception e) { return "redirect:/citas/" + id + "/reprogramar?error=hora_invalida"; }

        if (citaService.esFechaHoraPasada(fechaCita, horaCita))
            return "redirect:/citas/" + id + "/reprogramar?error=fecha_pasada";
        if (!citaService.esHorarioValido(cita.getDoctorId(), fechaCita, horaCita))
            return "redirect:/citas/" + id + "/reprogramar?error=horario_invalido";
        if (citaService.hayConflicto(cita.getDoctorId(), fechaCita, horaCita, cita.getId()))
            return "redirect:/citas/" + id + "/reprogramar?error=conflicto_cita";

        citaService.reprogramar(cita, fechaCita, horaCita);
        return "redirect:/pacientes/landing?success=cita_reprogramada";
    }

    @PostMapping("/guardar")
    public String guardar(@RequestParam(required = false) String doctorId,
            @RequestParam(required = false) String pacienteId,
            @RequestParam(required = false) String fecha,
            @RequestParam(required = false) String hora,
            @RequestParam(required = false) String motivo,
            Model model) {
        if (esVacio(doctorId)) return "redirect:/citas/crear?error=doctor_requerido";
        if (esVacio(pacienteId)) return "redirect:/citas/crear?error=paciente_requerido";
        if (esVacio(fecha)) return "redirect:/citas/crear?error=fecha_requerida";
        if (esVacio(hora)) return "redirect:/citas/crear?error=hora_requerida";
        if (esVacio(motivo)) return "redirect:/citas/crear?error=motivo_requerido";

        LocalDate fechaCita;
        LocalTime horaCita;
        try { fechaCita = LocalDate.parse(fecha.trim()); }
        catch (Exception e) { return "redirect:/citas/crear?error=fecha_invalida"; }
        try { horaCita = LocalTime.parse(hora.trim()); }
        catch (Exception e) { return "redirect:/citas/crear?error=hora_invalida"; }

        if (citaService.esFechaHoraPasada(fechaCita, horaCita)) return "redirect:/citas/crear?error=fecha_pasada";
        if (!citaService.doctorExiste(doctorId)) return "redirect:/citas/crear?error=doctor_no_existe";
        if (!citaService.pacienteExiste(pacienteId)) return "redirect:/citas/crear?error=paciente_no_existe";
        if (!citaService.esHorarioValido(doctorId, fechaCita, horaCita)) return "redirect:/citas/crear?error=horario_invalido";
        if (citaService.hayConflicto(doctorId, fechaCita, horaCita, null)) return "redirect:/citas/crear?error=conflicto_cita";

        citaService.crearCita(doctorId, pacienteId, fechaCita, horaCita, motivo);
        return REDIRECT_CITAS;
    }

    @PostMapping("/guardar-paciente")
    public String guardarPaciente(@RequestParam(required = false) String doctorId,
            @RequestParam(required = false) String fecha,
            @RequestParam(required = false) String hora,
            @RequestParam(required = false) String motivo) {
        if (esVacio(doctorId)) return "redirect:/pacientes/landing?error=doctor_requerido";
        if (esVacio(fecha)) return "redirect:/citas/nueva?doctorId=" + doctorId + "&error=fecha_requerida";
        if (esVacio(hora)) return "redirect:/citas/nueva?doctorId=" + doctorId + "&error=hora_requerida";
        if (esVacio(motivo)) return "redirect:/citas/nueva?doctorId=" + doctorId + "&error=motivo_requerido";

        LocalDate fechaCita;
        LocalTime horaCita;
        try { fechaCita = LocalDate.parse(fecha.trim()); }
        catch (Exception e) { return "redirect:/citas/nueva?doctorId=" + doctorId + "&error=fecha_invalida"; }
        try { horaCita = LocalTime.parse(hora.trim()); }
        catch (Exception e) { return "redirect:/citas/nueva?doctorId=" + doctorId + "&error=hora_invalida"; }

        if (citaService.esFechaHoraPasada(fechaCita, horaCita))
            return "redirect:/citas/nueva?doctorId=" + doctorId + "&error=fecha_pasada";

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Paciente paciente = citaService.buscarPacientePorEmail(auth.getName()).orElse(null);
        if (paciente == null) return "redirect:/pacientes/landing?error=paciente_no_encontrado";

        if (!citaService.esHorarioValido(doctorId, fechaCita, horaCita))
            return "redirect:/citas/nueva?doctorId=" + doctorId + "&error=horario_invalido";
        if (citaService.hayConflicto(doctorId, fechaCita, horaCita, null))
            return "redirect:/citas/nueva?doctorId=" + doctorId + "&error=conflicto_cita";

        citaService.crearCita(doctorId, paciente.getId(), fechaCita, horaCita, motivo);
        return "redirect:/pacientes/landing?success=cita_agendada";
    }

    @PostMapping("/{id}/cancelar")
    public String cancelar(@PathVariable String id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return REDIRECT_LOGIN;

        String role = auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).findFirst().orElse("");
        String email = auth.getName();

        Cita cita = citaService.buscarPorId(id).orElse(null);
        if (cita == null) return REDIRECT_CITAS;

        if ("ROLE_PACIENTE".equals(role)) {
            Paciente paciente = citaService.buscarPacientePorEmail(email).orElse(null);
            if (paciente == null || !paciente.getId().equals(cita.getPacienteId()))
                return "redirect:/pacientes/landing?error=no_autorizado";
            citaService.cancelar(cita);
            return REDIRECT_PACIENTES_LANDING;
        }
        if ("ROLE_DOCTOR".equals(role)) {
            Doctor doctor = citaService.buscarDoctorPorEmail(email).orElse(null);
            if (doctor == null || !doctor.getId().equals(cita.getDoctorId()))
                return "redirect:/doctores?error=no_autorizado";
            citaService.cancelar(cita);
            return "redirect:/doctores";
        }
        if ("ROLE_ADMIN".equals(role)) {
            citaService.cancelar(cita);
            return REDIRECT_CITAS;
        }
        return "redirect:/citas?error=no_autorizado";
    }
}
