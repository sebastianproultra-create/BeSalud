package com.gestion.proyectos.controlador;

import static com.gestion.proyectos.util.ValidacionUtil.esVacio;

import com.gestion.proyectos.modelo.Cita;
import com.gestion.proyectos.modelo.Doctor;
import com.gestion.proyectos.modelo.EstadoCita;
import com.gestion.proyectos.modelo.Paciente;
import com.gestion.proyectos.modelo.Reserva;
import com.gestion.proyectos.servicio.CitaService;
import com.gestion.proyectos.servicio.PasarelaPagos;
import com.gestion.proyectos.servicio.ReservaService;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.web.util.UriComponentsBuilder;
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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.time.LocalTime;

@Controller
@RequestMapping("/citas")
public class CitaController {

    private static final String REDIRECT_PACIENTES_LANDING = "redirect:/pacientes/landing";
    private static final String REDIRECT_LOGIN = "redirect:/login";
    private static final String REDIRECT_CITAS = "redirect:/citas";

    private final CitaService citaService;
    private final ReservaService reservaService;
    private final PasarelaPagos pasarela;

    public CitaController(CitaService citaService, ReservaService reservaService, PasarelaPagos pasarela) {
        this.citaService = citaService;
        this.reservaService = reservaService;
        this.pasarela = pasarela;
    }

    @GetMapping
    public String listar(@RequestParam(required = false) String pacienteId,
                         @RequestParam(required = false) String doctorId,
                         @RequestParam(defaultValue = "0") int page,
                         @RequestParam(defaultValue = "10") int size,
                         Model model) {
        Page<Cita> citas = citaService.listarPaginado(pacienteId, doctorId, page, size);
        Set<String> ids = new HashSet<>();
        citas.forEach(c -> {
            if (c.getDoctorId() != null) ids.add(c.getDoctorId());
            if (c.getPacienteId() != null) ids.add(c.getPacienteId());
        });
        Map<String, String> nombres = citaService.nombresPorId(ids);

        UriComponentsBuilder query = UriComponentsBuilder.newInstance().queryParam("size", citas.getSize());
        if (!esVacio(pacienteId)) query.queryParam("pacienteId", pacienteId.trim());
        if (!esVacio(doctorId)) query.queryParam("doctorId", doctorId.trim());

        model.addAttribute("citas", citas);
        model.addAttribute("queryCitas", query.build().encode().getQuery());
        model.addAttribute("doctoresMap", nombres);
        model.addAttribute("pacientesMap", nombres);
        return "citas/listado";
    }

    @GetMapping("/crear")
    public String crear(Model model) {
        model.addAttribute("cita", new Cita());
        model.addAttribute("doctores", citaService.listarDoctoresActivos());
        model.addAttribute("pacientes", citaService.listarPacientes());
        return "citas/formulario";
    }

    /** fecha y hora (opcionales) llegan desde la página del profesional o su asistente para dejar el turno elegido. */
    @GetMapping("/nueva")
    public String nueva(@RequestParam String doctorId,
                        @RequestParam(required = false) String fecha,
                        @RequestParam(required = false) String hora,
                        Model model) {
        Doctor doctor = citaService.buscarDoctorPorId(doctorId).orElse(null);
        if (doctor == null) return REDIRECT_PACIENTES_LANDING;

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String pacienteId = auth == null ? null
                : citaService.buscarPacientePorEmail(auth.getName()).map(Paciente::getId).orElse(null);

        model.addAttribute("doctorSeleccionado", doctor);
        model.addAttribute("cita", null);
        model.addAttribute("actionUrl", "/citas/guardar-paciente");
        model.addAttribute("pageTitle", "Agendar cita médica");
        model.addAttribute("submitLabel", "Continuar al pago");
        model.addAttribute("backLink", "/pacientes/landing");
        model.addAttribute("slotsDisponibles", citaService.slotsDisponibles(doctorId, null, null, null, pacienteId));
        model.addAttribute("fechaElegida", fecha);
        model.addAttribute("horaElegida", hora);
        model.addAttribute("minutosParaPagar", reservaService.getMinutosParaPagar());
        model.addAttribute("pagoSimulado", !pasarela.wompiConfigurado());
        return "citas/formulario-paciente";
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
        model.addAttribute("pageTitle", "Reprogramar cita médica");
        model.addAttribute("submitLabel", "Reprogramar cita");
        model.addAttribute("backLink", "/pacientes/landing");
        model.addAttribute("slotsDisponibles",
                citaService.slotsDisponibles(doctor.getId(), cita.getId(), cita.getFecha(), cita.getHora()));
        return "citas/formulario-paciente";
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

        if (cita.getEstado() != EstadoCita.PENDIENTE)
            return "redirect:/citas/" + id + "/reprogramar?error=estado_invalido";

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
        if (citaService.pacienteTieneConflicto(cita.getPacienteId(), cita.getDoctorId(), fechaCita, horaCita, cita.getId()))
            return "redirect:/citas/" + id + "/reprogramar?error=conflicto_paciente";

        try {
            citaService.reprogramar(cita, fechaCita, horaCita);
        } catch (CitaService.TurnoOcupadoException e) {
            return "redirect:/citas/" + id + "/reprogramar?error=conflicto_cita";
        }
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
        if (!citaService.doctorActivo(doctorId)) return "redirect:/citas/crear?error=doctor_inactivo";
        if (!citaService.pacienteExiste(pacienteId)) return "redirect:/citas/crear?error=paciente_no_existe";
        if (!citaService.esHorarioValido(doctorId, fechaCita, horaCita)) return "redirect:/citas/crear?error=horario_invalido";
        if (citaService.hayConflicto(doctorId, fechaCita, horaCita, null)) return "redirect:/citas/crear?error=conflicto_cita";
        if (citaService.pacienteTieneConflicto(pacienteId, doctorId, fechaCita, horaCita, null)) return "redirect:/citas/crear?error=conflicto_paciente";

        try {
            citaService.crearCita(doctorId, pacienteId, fechaCita, horaCita, motivo);
        } catch (CitaService.TurnoOcupadoException e) {
            return "redirect:/citas/crear?error=conflicto_cita";
        }
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

        Doctor doctor = citaService.buscarDoctorPorId(doctorId).orElse(null);
        if (doctor == null)
            return "redirect:/pacientes/landing?error=doctor_no_existe";
        if (!"ACTIVO".equals(doctor.getEstado()))
            return "redirect:/pacientes/landing?error=doctor_inactivo";
        if (!pasarela.pagosDisponibles())
            return "redirect:/pacientes/landing?error=pagos_no_disponibles";

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Paciente paciente = citaService.buscarPacientePorEmail(auth.getName()).orElse(null);
        if (paciente == null) return "redirect:/pacientes/landing?error=paciente_no_encontrado";

        if (paciente.getEmail() != null && doctor.getEmail() != null
                && doctor.getEmail().trim().equalsIgnoreCase(paciente.getEmail().trim())) {
            return "redirect:/citas/nueva?doctorId=" + doctorId + "&error=doctor_invalido";
        }

        if (!citaService.esHorarioValido(doctorId, fechaCita, horaCita))
            return "redirect:/citas/nueva?doctorId=" + doctorId + "&error=horario_invalido";
        // Sus propias reservas sin pagar no cuentan como choque: al apartar de nuevo se anulan
        if (citaService.hayConflicto(doctorId, fechaCita, horaCita, null, paciente.getId()))
            return "redirect:/citas/nueva?doctorId=" + doctorId + "&error=conflicto_cita";
        if (citaService.pacienteTieneConflicto(paciente.getId(), doctorId, fechaCita, horaCita, null))
            return "redirect:/citas/nueva?doctorId=" + doctorId + "&error=conflicto_paciente";

        try {
            // Toda reserva en línea se paga: la cita no existe hasta que se aprueba el abono
            Reserva reserva = reservaService.apartar(doctor, paciente.getId(), fechaCita, horaCita, motivo);
            return "redirect:/pagos/" + reserva.getReferencia();
        } catch (CitaService.TurnoOcupadoException e) {
            return "redirect:/citas/nueva?doctorId=" + doctorId + "&error=conflicto_cita";
        }
    }

    /** El paciente responde si asistirá. Si no, la cita se cancela (con devolución del abono si está a tiempo). */
    @PostMapping("/{id}/asistencia")
    public String responderAsistencia(@PathVariable String id, @RequestParam boolean asistira) {
        Cita cita = citaDelPaciente(id);
        if (cita == null)
            return "redirect:/pacientes/landing?error=no_autorizado";
        boolean devolucion = !asistira && cita.getAbonoCentavos() != null && citaService.cancelaConAnticipacion(cita);
        if (!citaService.responderAsistencia(cita, asistira))
            return "redirect:/pacientes/landing?error=cancelacion_no_permitida";
        if (asistira)
            return "redirect:/pacientes/landing?success=asistencia_confirmada&cita=" + cita.getId();
        return "redirect:/pacientes/landing?success=" + (devolucion ? "cita_cancelada_devolucion" : "cita_cancelada");
    }

    /** Abre Google Calendar con la cita lista para guardar. */
    @GetMapping("/{id}/google-calendar")
    public String googleCalendar(@PathVariable String id) {
        Cita cita = citaDelPaciente(id);
        if (cita == null || cita.getEstado() != EstadoCita.PENDIENTE)
            return "redirect:/pacientes/landing?error=no_autorizado";
        Doctor doctor = citaService.buscarDoctorPorId(cita.getDoctorId()).orElse(null);
        String quien = doctor == null ? "tu profesional" : "Dr(a). " + doctor.getNombre() + " " + doctor.getApellido();
        return "redirect:" + com.gestion.proyectos.util.Calendario.enlaceGoogle(cita.getFecha().atTime(cita.getHora()), 30,
                "Cita con " + quien, doctor == null ? null : doctor.getDireccionConsultorio(), "Cita agendada en BeSalud.");
    }

    private Cita citaDelPaciente(String id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Paciente paciente = auth == null ? null : citaService.buscarPacientePorEmail(auth.getName()).orElse(null);
        Cita cita = citaService.buscarPorId(id).orElse(null);
        return paciente != null && cita != null && paciente.getId().equals(cita.getPacienteId()) ? cita : null;
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
            // Solo se cancelan citas pendientes; una atendida o completada conserva su estado y su dictamen
            if (!citaService.cancelar(cita, true))
                return "redirect:/pacientes/landing?error=cancelacion_no_permitida";
            return "redirect:/pacientes/landing?success=cita_cancelada";
        }
        if ("ROLE_ADMIN".equals(role)) {
            if (!citaService.cancelar(cita))
                return "redirect:/citas?error=cancelacion_no_permitida";
            return REDIRECT_CITAS;
        }
        return "redirect:/citas?error=no_autorizado";
    }
}
