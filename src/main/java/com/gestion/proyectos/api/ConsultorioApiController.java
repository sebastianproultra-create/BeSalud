package com.gestion.proyectos.api;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gestion.proyectos.controlador.PagoController;
import com.gestion.proyectos.modelo.Cita;
import com.gestion.proyectos.modelo.Consultorio;
import com.gestion.proyectos.modelo.Doctor;
import com.gestion.proyectos.modelo.EstadoCita;
import com.gestion.proyectos.modelo.Paciente;
import com.gestion.proyectos.modelo.Reserva;
import com.gestion.proyectos.modelo.UserRegistrationDTO;
import com.gestion.proyectos.repositorio.ConsultorioRepositorio;
import com.gestion.proyectos.repositorio.PersonaRepositorio;
import com.gestion.proyectos.seguridad.RateLimiter;
import com.gestion.proyectos.servicio.CitaService;
import com.gestion.proyectos.servicio.ConsultorioService;
import com.gestion.proyectos.servicio.PacienteService;
import com.gestion.proyectos.servicio.PasarelaPagos;
import com.gestion.proyectos.servicio.RegistroService;
import com.gestion.proyectos.servicio.ReservaService;
import com.gestion.proyectos.util.Reloj;
import com.gestion.proyectos.util.ValidacionUtil;

import jakarta.servlet.http.HttpServletRequest;

/**
 * API "sin interfaz" para la web de un consultorio: el paciente se registra, agenda, paga y ve o cancela sus
 * citas sin entrar nunca al gestor de BeSalud. Todo queda limitado a los médicos de ese consultorio, y el
 * token que entrega solo sirve aquí (ver TokenConsultorioService).
 */
@ConditionalOnProperty(name = "app.api-consultorios.habilitada", havingValue = "true")
@RestController
@RequestMapping("/api/v1/c/{consultorioId}")
public class ConsultorioApiController {

    private static final Logger log = LoggerFactory.getLogger(ConsultorioApiController.class);
    private static final int MAX_FALLOS_LOGIN = 5;
    private static final int MAX_REGISTROS = 10;
    private static final Duration VENTANA_REGISTRO = Duration.ofMinutes(10);
    private static final Duration VENTANA_LOGIN = Duration.ofMinutes(15);
    private static final int MAX_MOTIVO = 500;

    public record MedicoJson(String id, String nombre, String especialidad, int precio, int abono, String direccion,
            int horasCancelacion) {
    }

    public record ConsultorioJson(String id, String nombre, String pago, List<MedicoJson> medicos) {
    }

    public record PacienteJson(String nombre, String apellido, String email) {
    }

    public record SesionJson(String token, long expiraEnSegundos, PacienteJson paciente) {
    }

    public record ReservaRequest(String doctorId, String fecha, String hora, String motivo, String retorno) {
    }

    public record PagoJson(String tipo, String url) {
    }

    public record ReservaJson(String referencia, String medico, String fecha, String hora, long abono, long saldo,
            long segundosParaPagar, PagoJson pago) {
    }

    public record EstadoPagoJson(String estado, String referencia, String medico, String fecha, String hora,
            long abono, long saldo, String medioPago, String cancelarHasta) {
    }

    public record SimuladoRequest(boolean aprobar) {
    }

    public record CitaJson(String id, String medico, String especialidad, String fecha, String hora, String estado,
            String motivo, Long abono, boolean puedeCancelar, boolean devolucionSiCancela) {
    }

    /** Error con su código HTTP; la web lo muestra tal cual al paciente. */
    static class ApiError extends RuntimeException {
        final HttpStatus status;

        ApiError(HttpStatus status, String mensaje) {
            super(mensaje);
            this.status = status;
        }
    }

    private final ConsultorioRepositorio consultorioRepo;
    private final CitaService citaService;
    private final ReservaService reservaService;
    private final PasarelaPagos pasarela;
    private final RegistroService registroService;
    private final PacienteService pacienteService;
    private final PersonaRepositorio personaRepo;
    private final AuthenticationManager authenticationManager;
    private final RateLimiter rateLimiter;
    private final Idempotencia idempotencia;
    private final TokenConsultorioService tokens;

    public ConsultorioApiController(ConsultorioRepositorio consultorioRepo, CitaService citaService,
            ReservaService reservaService, PasarelaPagos pasarela, RegistroService registroService,
            PacienteService pacienteService, PersonaRepositorio personaRepo,
            AuthenticationManager authenticationManager, RateLimiter rateLimiter, TokenConsultorioService tokens,
            Idempotencia idempotencia) {
        this.consultorioRepo = consultorioRepo;
        this.citaService = citaService;
        this.reservaService = reservaService;
        this.pasarela = pasarela;
        this.registroService = registroService;
        this.pacienteService = pacienteService;
        this.personaRepo = personaRepo;
        this.authenticationManager = authenticationManager;
        this.rateLimiter = rateLimiter;
        this.tokens = tokens;
        this.idempotencia = idempotencia;
    }

    @ExceptionHandler(ApiError.class)
    ResponseEntity<Map<String, String>> manejar(ApiError e) {
        return ResponseEntity.status(e.status).body(Map.of("error", e.getMessage()));
    }

    // ── Datos públicos del consultorio ──────────────────────────────────────

    @GetMapping
    public ConsultorioJson consultorio(@PathVariable String consultorioId,
            @RequestHeader(name = "Origin", required = false) String origen) {
        Consultorio c = tenant(consultorioId, origen);
        List<MedicoJson> medicos = c.getDoctorIds().stream()
                .map(id -> citaService.buscarDoctorPorId(id).orElse(null))
                .filter(d -> d != null && "ACTIVO".equals(d.getEstado()))
                .map(ConsultorioApiController::medicoJson)
                .toList();
        String pago = pasarela.usaSimulada() ? "simulado" : pasarela.wompiConfigurado() ? "wompi" : "no_disponible";
        return new ConsultorioJson(c.getId(), c.getNombre(), pago, medicos);
    }

    @GetMapping("/disponibilidad")
    public Map<String, Object> disponibilidad(@PathVariable String consultorioId,
            @RequestParam(required = false) String doctorId,
            @RequestHeader(name = "Origin", required = false) String origen,
            @RequestHeader(name = "Authorization", required = false) String autorizacion) {
        Consultorio c = tenant(consultorioId, origen);
        Doctor doctor = doctorDe(c, doctorId);
        // Con sesión, sus propias reservas en pago no le ocultan el turno (puede volver a elegirlo).
        String pacienteId = pacienteOpcional(autorizacion, c).map(Paciente::getId).orElse(null);
        return Map.of("doctorId", doctor.getId(),
                "dias", citaService.slotsDisponibles(doctor.getId(), null, null, null, pacienteId));
    }

    // ── Cuenta del paciente ─────────────────────────────────────────────────

    @PostMapping("/pacientes")
    public ResponseEntity<SesionJson> registrar(@PathVariable String consultorioId, @RequestBody RegistroApiRequest req,
            @RequestHeader(name = "Origin", required = false) String origen, HttpServletRequest request) {
        Consultorio c = tenant(consultorioId, origen);
        // Mismo límite que el registro del portal (RateLimitFilter), pero aquí responde JSON con CORS.
        if (!rateLimiter.permitir("registro:" + request.getRemoteAddr(), MAX_REGISTROS, VENTANA_REGISTRO))
            throw new ApiError(HttpStatus.TOO_MANY_REQUESTS,
                    "Demasiados registros desde tu conexión. Espera unos minutos e intenta de nuevo.");
        if (!req.aceptaPrivacidad())
            throw new ApiError(HttpStatus.BAD_REQUEST, "Debes aceptar la política de privacidad");
        if (ValidacionUtil.esVacio(req.email()) || ValidacionUtil.esVacio(req.password()))
            throw new ApiError(HttpStatus.BAD_REQUEST, "Correo y contraseña son obligatorios");

        UserRegistrationDTO user = new UserRegistrationDTO();
        user.setNombre(req.nombre());
        user.setApellido(req.apellido());
        user.setIdentificacion(req.identificacion());
        user.setTelefono(req.telefono());
        user.setEmail(req.email());
        user.setPassword(req.password());
        user.setRole("PACIENTE"); // la web externa nunca puede crear doctores ni admins

        String msg = registroService.validar(user);
        if (msg == null) msg = ValidacionUtil.errorClave(user.getPassword());
        if (msg != null) throw new ApiError(HttpStatus.BAD_REQUEST, msg);
        if (registroService.verificarDuplicado(user) != null)
            throw new ApiError(HttpStatus.CONFLICT, "Ya tienes una cuenta con ese correo o documento. Inicia sesión.");
        msg = registroService.validarEmail(user.getEmail());
        if (msg == null) msg = registroService.registrar(user);
        if (msg != null) throw new ApiError(HttpStatus.BAD_REQUEST, msg);

        String email = user.getEmail().trim().toLowerCase();
        personaRepo.findByEmail(email).ifPresent(p -> {
            if (p instanceof Paciente paciente) {
                paciente.setConsultorioOrigen(c.getId());
                personaRepo.save(paciente);
            }
        });
        log.info("Paciente registrado en la web del consultorio {}: {}", c.getId(), email);
        return ResponseEntity.status(HttpStatus.CREATED).body(sesion(email, c));
    }

    @PostMapping("/sesion")
    public SesionJson iniciarSesion(@PathVariable String consultorioId, @RequestBody SesionApiRequest req,
            @RequestHeader(name = "Origin", required = false) String origen, HttpServletRequest request) {
        Consultorio c = tenant(consultorioId, origen);
        if (ValidacionUtil.esVacio(req.email()) || ValidacionUtil.esVacio(req.password()))
            throw new ApiError(HttpStatus.BAD_REQUEST, "Escribe tu correo y tu contraseña");

        String email = req.email().trim().toLowerCase();
        String claveLogin = "login:" + request.getRemoteAddr() + ":" + email;
        if (rateLimiter.bloqueado(claveLogin, MAX_FALLOS_LOGIN, VENTANA_LOGIN))
            throw new ApiError(HttpStatus.TOO_MANY_REQUESTS,
                    "Demasiados intentos fallidos. Espera 15 minutos e intenta de nuevo.");
        Authentication auth;
        try {
            auth = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, req.password()));
        } catch (AuthenticationException e) {
            rateLimiter.registrar(claveLogin, VENTANA_LOGIN);
            throw new ApiError(HttpStatus.UNAUTHORIZED, "Correo o contraseña incorrectos");
        }
        rateLimiter.reiniciar(claveLogin);
        if (auth.getAuthorities().stream().noneMatch(a -> "ROLE_PACIENTE".equals(a.getAuthority())))
            throw new ApiError(HttpStatus.FORBIDDEN, "Esta cuenta no es de paciente.");
        return sesion(email, c);
    }

    @GetMapping("/yo")
    public PacienteJson yo(@PathVariable String consultorioId,
            @RequestHeader(name = "Origin", required = false) String origen,
            @RequestHeader(name = "Authorization", required = false) String autorizacion) {
        Paciente p = paciente(autorizacion, tenant(consultorioId, origen));
        return new PacienteJson(p.getNombre(), p.getApellido(), p.getEmail());
    }

    // ── Agendar y pagar ─────────────────────────────────────────────────────

    /**
     * Aparta el turno y devuelve cómo pagar el abono: la cita nace cuando el pago se aprueba.
     * Con Idempotency-Key, un reintento con la misma clave devuelve la misma reserva (cabecera
     * Idempotent-Replayed: true) en vez de apartar otro turno.
     */
    @PostMapping("/citas")
    public ResponseEntity<ReservaJson> reservar(@PathVariable String consultorioId, @RequestBody ReservaRequest req,
            @RequestHeader(name = "Origin", required = false) String origen,
            @RequestHeader(name = "Authorization", required = false) String autorizacion,
            @RequestHeader(name = "Idempotency-Key", required = false) String claveIdempotencia) {
        Consultorio c = tenant(consultorioId, origen);
        Paciente paciente = paciente(autorizacion, c);
        String huella = String.join("|", String.valueOf(req.doctorId()), String.valueOf(req.fecha()),
                String.valueOf(req.hora()), String.valueOf(req.motivo()));
        Idempotencia.Resultado<ReservaJson> r = idempotencia.ejecutar(c.getId() + "|" + paciente.getId(),
                claveIdempotencia, huella, () -> apartar(c, paciente, req, origen));
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Idempotent-Replayed", String.valueOf(r.repetido()))
                .body(r.valor());
    }

    private ReservaJson apartar(Consultorio c, Paciente paciente, ReservaRequest req, String origen) {
        Doctor doctor = doctorDe(c, req.doctorId());

        if (ValidacionUtil.esVacio(req.motivo()))
            throw new ApiError(HttpStatus.BAD_REQUEST, "Cuéntanos brevemente el motivo de la consulta");
        if (req.motivo().length() > MAX_MOTIVO)
            throw new ApiError(HttpStatus.BAD_REQUEST, "El motivo es muy largo (máximo " + MAX_MOTIVO + " caracteres)");
        LocalDate fecha;
        LocalTime hora;
        try {
            fecha = LocalDate.parse(req.fecha().trim());
            hora = LocalTime.parse(req.hora().trim());
        } catch (RuntimeException e) {
            throw new ApiError(HttpStatus.BAD_REQUEST, "Elige un día y una hora");
        }
        if (citaService.esFechaHoraPasada(fecha, hora))
            throw new ApiError(HttpStatus.BAD_REQUEST, "Ese horario ya pasó");
        if (!pasarela.pagosDisponibles())
            throw new ApiError(HttpStatus.SERVICE_UNAVAILABLE, "Las reservas en línea están pausadas por ahora");
        if (doctor.getEmail() != null && doctor.getEmail().trim().equalsIgnoreCase(paciente.getEmail()))
            throw new ApiError(HttpStatus.BAD_REQUEST, "No puedes agendar contigo mismo");
        if (!citaService.esHorarioValido(doctor.getId(), fecha, hora))
            throw new ApiError(HttpStatus.BAD_REQUEST, "Ese horario no está en la agenda del médico");
        if (citaService.hayConflicto(doctor.getId(), fecha, hora, null, paciente.getId()))
            throw new ApiError(HttpStatus.CONFLICT, "Alguien acaba de tomar ese horario. Elige otro.");
        if (citaService.pacienteTieneConflicto(paciente.getId(), doctor.getId(), fecha, hora, null))
            throw new ApiError(HttpStatus.CONFLICT, "Ya tienes otra cita a esa hora");

        Reserva reserva;
        try {
            reserva = reservaService.apartar(doctor, paciente.getId(), fecha, hora, req.motivo());
        } catch (CitaService.TurnoOcupadoException e) {
            throw new ApiError(HttpStatus.CONFLICT, "Alguien acaba de tomar ese horario. Elige otro.");
        }
        pacienteService.asignarDoctor(paciente.getEmail(), doctor.getId());

        PagoJson pago = pasarela.usaSimulada() ? new PagoJson("simulado", null)
                : new PagoJson("wompi", pasarela.urlPago(reserva, paciente.getEmail(),
                        paciente.getNombre() + " " + paciente.getApellido(),
                        urlRetorno(req.retorno(), origen, reserva.getReferencia())));
        return new ReservaJson(reserva.getReferencia(), nombreMedico(doctor), fecha.toString(), hora.toString(),
                reserva.getMontoPesos(), saldo(doctor, reserva), segundosRestantes(reserva), pago);
    }

    /** Pasarela de pruebas dentro de la web (solo sin llaves de Wompi): aprobar o rechazar el abono. */
    @PostMapping("/pagos/{referencia}/simulado")
    public EstadoPagoJson pagarSimulado(@PathVariable String consultorioId, @PathVariable String referencia,
            @RequestBody SimuladoRequest req,
            @RequestHeader(name = "Origin", required = false) String origen,
            @RequestHeader(name = "Authorization", required = false) String autorizacion) {
        Consultorio c = tenant(consultorioId, origen);
        Reserva reserva = reservaDe(referencia, paciente(autorizacion, c), c);
        if (!pasarela.usaSimulada())
            throw new ApiError(HttpStatus.NOT_FOUND, "La pasarela de pruebas no está activa");
        reservaService.pagoSimulado(referencia, "CARD", "Tarjeta de prueba •••• 4242",
                req.aprobar() ? "APPROVED" : "DECLINED");
        return estadoPago(reservaService.buscarPorReferencia(reserva.getReferencia()).orElse(reserva));
    }

    /**
     * Estado del abono. Al volver de Wompi la web manda el id de la transacción; no se confía en él: se le
     * pregunta a Wompi y solo se aplica si la transacción es de esta reserva.
     */
    @GetMapping("/pagos/{referencia}")
    public EstadoPagoJson estado(@PathVariable String consultorioId, @PathVariable String referencia,
            @RequestParam(name = "id", required = false) String transaccionId,
            @RequestHeader(name = "Origin", required = false) String origen,
            @RequestHeader(name = "Authorization", required = false) String autorizacion) {
        Consultorio c = tenant(consultorioId, origen);
        Reserva reserva = reservaDe(referencia, paciente(autorizacion, c), c);
        if (transaccionId != null && pasarela.wompiConfigurado())
            pasarela.consultar(transaccionId).filter(t -> referencia.equals(t.referencia()))
                    .ifPresent(t -> reservaService.confirmarPago(t.referencia(), t.id(), t.estado(), t.montoCentavos(),
                            t.moneda()));
        return estadoPago(actualizar(reservaService.buscarPorReferencia(referencia).orElse(reserva)));
    }

    // ── Mis citas ───────────────────────────────────────────────────────────

    /** Solo las citas con médicos de este consultorio, aunque el paciente tenga otras en BeSalud. */
    @GetMapping("/citas")
    public List<CitaJson> misCitas(@PathVariable String consultorioId,
            @RequestHeader(name = "Origin", required = false) String origen,
            @RequestHeader(name = "Authorization", required = false) String autorizacion) {
        Consultorio c = tenant(consultorioId, origen);
        Paciente paciente = paciente(autorizacion, c);
        Map<String, Doctor> doctores = new HashMap<>();
        return citaService.listarPaginado(paciente.getId(), null, 0, 100).getContent().stream()
                .filter(cita -> c.getDoctorIds().contains(cita.getDoctorId()))
                .map(cita -> {
                    Doctor d = doctores.computeIfAbsent(cita.getDoctorId(),
                            id -> citaService.buscarDoctorPorId(id).orElse(null));
                    boolean futura = cita.getFecha() != null && cita.getHora() != null
                            && LocalDateTime.of(cita.getFecha(), cita.getHora()).isAfter(Reloj.ahora());
                    boolean puedeCancelar = cita.getEstado() == EstadoCita.PENDIENTE && futura;
                    return new CitaJson(cita.getId(), d == null ? "Profesional" : nombreMedico(d),
                            d == null ? null : d.getEspecialidad(), String.valueOf(cita.getFecha()),
                            String.valueOf(cita.getHora()), cita.getEstado().getDescripcion(), cita.getMotivo(),
                            cita.getAbonoCentavos() == null ? null : cita.getAbonoCentavos() / 100,
                            puedeCancelar, puedeCancelar && citaService.cancelaConAnticipacion(cita));
                })
                .toList();
    }

    @PostMapping("/citas/{citaId}/cancelar")
    public Map<String, Object> cancelar(@PathVariable String consultorioId, @PathVariable String citaId,
            @RequestHeader(name = "Origin", required = false) String origen,
            @RequestHeader(name = "Authorization", required = false) String autorizacion) {
        Consultorio c = tenant(consultorioId, origen);
        Paciente paciente = paciente(autorizacion, c);
        Cita cita = citaService.buscarPorId(citaId)
                .filter(x -> paciente.getId().equals(x.getPacienteId()) && c.getDoctorIds().contains(x.getDoctorId()))
                .orElseThrow(() -> new ApiError(HttpStatus.NOT_FOUND, "Cita no encontrada"));
        boolean devolucion = cita.getAbonoCentavos() != null && citaService.cancelaConAnticipacion(cita);
        if (!citaService.cancelar(cita, true))
            throw new ApiError(HttpStatus.CONFLICT, "Esta cita ya no se puede cancelar");
        return Map.of("cancelada", true, "devolucion", devolucion);
    }

    // ── Apoyo ───────────────────────────────────────────────────────────────

    /** El consultorio existe, está activo y la petición viene de uno de sus dominios. */
    private Consultorio tenant(String consultorioId, String origen) {
        String efectivo = origen != null ? origen : origenMismoSitio();
        return consultorioRepo.findByIdAndActivoTrue(consultorioId)
                .filter(c -> efectivo != null && c.getDominios().contains(efectivo))
                .orElseThrow(() -> new ApiError(HttpStatus.FORBIDDEN, "Consultorio no autorizado"));
    }

    /**
     * Un GET del mismo sitio (la demo de /desarrolladores) no lleva cabecera Origin; el navegador sí manda
     * Sec-Fetch-Site. En ese caso el origen es el de BeSalud, que solo sirve si el consultorio lo autoriza.
     */
    private static String origenMismoSitio() {
        var atributos = (org.springframework.web.context.request.ServletRequestAttributes)
                org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if (atributos == null || !"same-origin".equals(atributos.getRequest().getHeader("Sec-Fetch-Site")))
            return null;
        return ConsultorioService.normalizarOrigen(
                org.springframework.web.servlet.support.ServletUriComponentsBuilder.fromCurrentContextPath().toUriString());
    }

    private Doctor doctorDe(Consultorio c, String doctorId) {
        if (ValidacionUtil.esVacio(doctorId) || !c.getDoctorIds().contains(doctorId))
            throw new ApiError(HttpStatus.NOT_FOUND, "Ese médico no atiende en este consultorio");
        return citaService.buscarDoctorPorId(doctorId).filter(d -> "ACTIVO".equals(d.getEstado()))
                .orElseThrow(() -> new ApiError(HttpStatus.NOT_FOUND, "Ese médico no está disponible"));
    }

    private Optional<Paciente> pacienteOpcional(String autorizacion, Consultorio c) {
        if (autorizacion == null || !autorizacion.startsWith("Bearer "))
            return Optional.empty();
        return tokens.emailDe(autorizacion.substring(7), c.getId()).flatMap(citaService::buscarPacientePorEmail);
    }

    private Paciente paciente(String autorizacion, Consultorio c) {
        return pacienteOpcional(autorizacion, c)
                .orElseThrow(() -> new ApiError(HttpStatus.UNAUTHORIZED, "Tu sesión expiró. Vuelve a iniciar sesión."));
    }

    /** Solo el dueño de la reserva y solo si el médico es de este consultorio. */
    private Reserva reservaDe(String referencia, Paciente paciente, Consultorio c) {
        return reservaService.buscarPorReferencia(referencia)
                .filter(r -> paciente.getId().equals(r.getPacienteId()) && c.getDoctorIds().contains(r.getDoctorId()))
                .orElseThrow(() -> new ApiError(HttpStatus.NOT_FOUND, "Reserva no encontrada"));
    }

    private SesionJson sesion(String email, Consultorio c) {
        Paciente p = citaService.buscarPacientePorEmail(email)
                .orElseThrow(() -> new ApiError(HttpStatus.FORBIDDEN, "Esta cuenta no es de paciente."));
        return new SesionJson(tokens.emitir(email, c.getId()), TokenConsultorioService.VIGENCIA.toSeconds(),
                new PacienteJson(p.getNombre(), p.getApellido(), p.getEmail()));
    }

    /** Igual que PagoController: en local Wompi no puede avisar por webhook, así que se le pregunta. */
    private Reserva actualizar(Reserva r) {
        boolean pendiente = r.getTransaccionId() != null
                && (r.getEstado() == Reserva.Estado.ESPERANDO_PAGO || r.getEstado() == Reserva.Estado.EXPIRADA);
        if (!pendiente)
            return r;
        if (pasarela.usaSimulada())
            return reservaService.resolverSimulacion(r);
        if (pasarela.wompiConfigurado())
            pasarela.consultar(r.getTransaccionId()).ifPresent(t -> reservaService.confirmarPago(t.referencia(),
                    t.id(), t.estado(), t.montoCentavos(), t.moneda()));
        return reservaService.buscarPorReferencia(r.getReferencia()).orElse(r);
    }

    private EstadoPagoJson estadoPago(Reserva r) {
        Doctor doctor = citaService.buscarDoctorPorId(r.getDoctorId())
                .orElseThrow(() -> new ApiError(HttpStatus.NOT_FOUND, "Reserva no encontrada"));
        PagoController.Vista vista = PagoController.vistaDe(r);
        if (vista == PagoController.Vista.EXPIRADA && r.getEstado() == Reserva.Estado.ESPERANDO_PAGO
                && !r.vencida(Reloj.ahora()))
            vista = null; // todavía puede pagar
        return new EstadoPagoJson(vista == null ? "ESPERANDO_PAGO" : vista.name(), r.getReferencia(),
                nombreMedico(doctor), r.getFecha().toString(), r.getHora().toString(), r.getMontoPesos(),
                saldo(doctor, r), r.getMedioPago(),
                r.getFecha().atTime(r.getHora()).minusHours(doctor.horasCancelacionEfectivas()).toString());
    }

    /** Wompi devuelve al paciente a la web del consultorio, nunca a BeSalud; solo se acepta su propio dominio. */
    static String urlRetorno(String retorno, String origen, String referencia) {
        String base = origen + "/";
        if (retorno != null && retorno.length() < 500 && origen.equals(ConsultorioService.normalizarOrigen(retorno)))
            base = retorno.split("#")[0];
        return base + (base.contains("?") ? "&" : "?") + "bs_ref=" + referencia;
    }

    private static MedicoJson medicoJson(Doctor d) {
        return new MedicoJson(d.getId(), nombreMedico(d), d.getEspecialidad(), d.getPrecioEfectivo(), d.getAbono(),
                d.getDireccionConsultorio(), d.horasCancelacionEfectivas());
    }

    private static String nombreMedico(Doctor d) {
        return "Dr(a). " + d.getNombre() + " " + d.getApellido();
    }

    private static long saldo(Doctor doctor, Reserva reserva) {
        return Math.max(0, (reserva.getPrecioCentavos() != null ? reserva.getPrecioCentavos() / 100 : doctor.getPrecioEfectivo())
                - reserva.getMontoPesos());
    }

    private static long segundosRestantes(Reserva reserva) {
        return Math.max(0, Duration.between(Reloj.ahora(), reserva.getExpiraEn()).getSeconds());
    }
}
