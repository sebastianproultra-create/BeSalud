package com.gestion.proyectos.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.gestion.proyectos.modelo.Consultorio;
import com.gestion.proyectos.modelo.Paciente;
import com.gestion.proyectos.modelo.PaseAcceso;
import com.gestion.proyectos.modelo.UserRegistrationDTO;
import com.gestion.proyectos.repositorio.ConsultorioRepositorio;
import com.gestion.proyectos.repositorio.PersonaRepositorio;
import com.gestion.proyectos.seguridad.CustomUserDetailsService;
import com.gestion.proyectos.seguridad.JwtCookieService;
import com.gestion.proyectos.seguridad.JwtService;
import com.gestion.proyectos.seguridad.RateLimiter;
import com.gestion.proyectos.servicio.CitaService;
import com.gestion.proyectos.servicio.PacienteService;
import com.gestion.proyectos.servicio.RegistroService;
import com.gestion.proyectos.util.ValidacionUtil;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// API pública para las webs de consultorios (multi-tenant).
// Reutiliza las mismas validaciones que LoginController#registerSave.
// Solo existe con app.api-consultorios.habilitada=true; apagada, /api/v1/** responde 404.
@ConditionalOnProperty(name = "app.api-consultorios.habilitada", havingValue = "true")
@RestController
@RequestMapping("/api/v1")
public class ApiRegistroController {

    private static final Logger log = LoggerFactory.getLogger(ApiRegistroController.class);
    // Mismo límite anti fuerza bruta que LoginController.
    private static final int MAX_FALLOS_LOGIN = 5;
    private static final int MAX_REGISTROS = 10;
    private static final Duration VENTANA_REGISTRO = Duration.ofMinutes(10);
    private static final Duration VENTANA_LOGIN = Duration.ofMinutes(15);

    private final RegistroService registroService;
    private final CitaService citaService;
    private final PaseAccesoService paseService;
    private final ConsultorioRepositorio consultorioRepo;
    private final PersonaRepositorio personaRepo;
    private final CustomUserDetailsService userDetailsService;
    private final JwtService jwtService;
    private final JwtCookieService jwtCookieService;
    private final AuthenticationManager authenticationManager;
    private final RateLimiter rateLimiter;
    private final PacienteService pacienteService;

    public ApiRegistroController(RegistroService registroService, CitaService citaService,
            PaseAccesoService paseService, ConsultorioRepositorio consultorioRepo, PersonaRepositorio personaRepo,
            CustomUserDetailsService userDetailsService, JwtService jwtService, JwtCookieService jwtCookieService,
            AuthenticationManager authenticationManager, RateLimiter rateLimiter, PacienteService pacienteService) {
        this.registroService = registroService;
        this.citaService = citaService;
        this.paseService = paseService;
        this.consultorioRepo = consultorioRepo;
        this.personaRepo = personaRepo;
        this.userDetailsService = userDetailsService;
        this.jwtService = jwtService;
        this.jwtCookieService = jwtCookieService;
        this.authenticationManager = authenticationManager;
        this.rateLimiter = rateLimiter;
        this.pacienteService = pacienteService;
    }

    // 201 { redirectUrl } | 400 { error } | 403 { error } (tenant inválido) | 409 { error, loginUrl }
    @PostMapping("/pacientes")
    public ResponseEntity<Map<String, String>> registrar(@RequestBody RegistroApiRequest req,
            @RequestHeader(name = "Origin", required = false) String origen, HttpServletRequest request) {
        ResponseEntity<Map<String, String>> rechazo = validarTenant(req.consultorioId(), req.doctorId(), origen);
        if (rechazo != null) return rechazo;
        if (!rateLimiter.permitir("registro:" + request.getRemoteAddr(), MAX_REGISTROS, VENTANA_REGISTRO))
            return error(HttpStatus.TOO_MANY_REQUESTS, "Demasiados registros desde tu conexión. Espera unos minutos e intenta de nuevo.");

        if (!req.aceptaPrivacidad())
            return error(HttpStatus.BAD_REQUEST, "Debes aceptar la política de privacidad");
        if (ValidacionUtil.esVacio(req.email()) || ValidacionUtil.esVacio(req.password()))
            return error(HttpStatus.BAD_REQUEST, "Correo y contraseña son obligatorios");

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
        if (msg != null) return error(HttpStatus.BAD_REQUEST, msg);

        msg = registroService.verificarDuplicado(user);
        if (msg != null) {
            // Ya tiene cuenta: que inicie sesión y vuelva directo a la agenda de este doctor.
            String continuar = URLEncoder.encode("/citas/nueva?doctorId=" + req.doctorId(), StandardCharsets.UTF_8);
            String loginUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                    .path("/login").query("continuar=" + continuar).build(true).toUriString();
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", msg, "loginUrl", loginUrl));
        }

        msg = registroService.validarEmail(user.getEmail());
        if (msg == null) msg = registroService.registrar(user);
        if (msg != null) return error(HttpStatus.BAD_REQUEST, msg);

        String email = user.getEmail().trim().toLowerCase();
        marcarOrigen(email, req.consultorioId());
        log.info("Paciente registrado desde consultorio {}: {}", req.consultorioId(), email);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("redirectUrl", urlAcceso(email, req.doctorId())));
    }

    // "Ya tengo cuenta" dentro del modal del consultorio.
    // 200 { redirectUrl } | 400 | 401 credenciales | 403 tenant o no es paciente | 429 bloqueado
    @PostMapping("/sesion")
    public ResponseEntity<Map<String, String>> iniciarSesion(@RequestBody SesionApiRequest req,
            @RequestHeader(name = "Origin", required = false) String origen, HttpServletRequest request) {
        ResponseEntity<Map<String, String>> rechazo = validarTenant(req.consultorioId(), req.doctorId(), origen);
        if (rechazo != null) return rechazo;
        if (ValidacionUtil.esVacio(req.email()) || ValidacionUtil.esVacio(req.password()))
            return error(HttpStatus.BAD_REQUEST, "Escribe tu correo y tu contraseña");

        String email = req.email().trim().toLowerCase();
        String claveLogin = "login:" + request.getRemoteAddr() + ":" + email;
        if (rateLimiter.bloqueado(claveLogin, MAX_FALLOS_LOGIN, VENTANA_LOGIN))
            return error(HttpStatus.TOO_MANY_REQUESTS,
                    "Demasiados intentos fallidos. Espera 15 minutos e intenta de nuevo.");

        Authentication auth;
        try {
            auth = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, req.password()));
        } catch (AuthenticationException e) {
            rateLimiter.registrar(claveLogin, VENTANA_LOGIN);
            return error(HttpStatus.UNAUTHORIZED, "Correo o contraseña incorrectos");
        }
        rateLimiter.reiniciar(claveLogin);

        boolean esPaciente = auth.getAuthorities().stream().anyMatch(a -> "ROLE_PACIENTE".equals(a.getAuthority()));
        if (!esPaciente)
            return error(HttpStatus.FORBIDDEN, "Esta cuenta no es de paciente. Ingresa desde el portal de BeSalud.");

        log.info("Paciente inició sesión desde consultorio {}: {}", req.consultorioId(), email);
        return ResponseEntity.ok(Map.of("redirectUrl", urlAcceso(email, req.doctorId())));
    }

    // Navegación normal del navegador (no fetch): aquí sí se puede fijar la cookie JWT.
    @GetMapping("/acceso")
    public void acceso(@RequestParam(name = "t", required = false) String codigo,
            HttpServletResponse response) throws IOException {
        PaseAcceso pase = paseService.consumir(codigo).orElse(null);
        if (pase == null) {
            response.sendRedirect("/login?enlaceVencido=true");
            return;
        }
        UserDetails user = userDetailsService.loadUserByUsername(pase.getEmail());
        jwtCookieService.addJwtCookie(response, jwtService.generateToken(user));
        pacienteService.asignarDoctor(pase.getEmail(), pase.getDoctorId());
        response.sendRedirect("/citas/nueva?doctorId=" + pase.getDoctorId());
    }

    // Botón "Ya tengo cuenta" de la web del consultorio (navegación normal, la cookie JWT sí viaja):
    // con sesión de paciente va directo a la agenda; sin sesión, al login y de vuelta a la agenda.
    @GetMapping("/agendar")
    public void agendar(@RequestParam(required = false) String doctorId, HttpServletResponse response)
            throws IOException {
        if (doctorId == null || !doctorId.matches("[A-Za-z0-9]+") || !citaService.doctorActivo(doctorId)) {
            response.sendRedirect("/");
            return;
        }
        String agenda = "/citas/nueva?doctorId=" + doctorId;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean esPaciente = auth != null && auth.isAuthenticated() && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_PACIENTE".equals(a.getAuthority()));
        if (esPaciente) {
            pacienteService.asignarDoctor(auth.getName(), doctorId);
            response.sendRedirect(agenda);
        }
        else
            response.sendRedirect("/login?continuar=" + URLEncoder.encode(agenda, StandardCharsets.UTF_8));
    }

    // Tenant: el consultorio existe, la petición viene de su dominio y el doctor es suyo. null = todo bien.
    private ResponseEntity<Map<String, String>> validarTenant(String consultorioId, String doctorId, String origen) {
        Consultorio consultorio = ValidacionUtil.esVacio(consultorioId) ? null
                : consultorioRepo.findByIdAndActivoTrue(consultorioId).orElse(null);
        if (consultorio == null || origen == null || !consultorio.getDominios().contains(origen))
            return error(HttpStatus.FORBIDDEN, "Consultorio no autorizado");
        if (ValidacionUtil.esVacio(doctorId) || !consultorio.getDoctorIds().contains(doctorId)
                || !citaService.doctorActivo(doctorId))
            return error(HttpStatus.BAD_REQUEST, "El doctor de este consultorio no está disponible");
        return null;
    }

    // Pase de un solo uso: la cookie JWT se fija cuando el navegador entra a /api/v1/acceso.
    private String urlAcceso(String email, String doctorId) {
        String codigo = paseService.emitir(email, doctorId);
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/acceso").queryParam("t", codigo).toUriString();
    }

    // Para métricas por cliente: cuántos pacientes trae la web de cada consultorio.
    private void marcarOrigen(String email, String consultorioId) {
        personaRepo.findByEmail(email).ifPresent(p -> {
            if (p instanceof Paciente paciente) {
                paciente.setConsultorioOrigen(consultorioId);
                personaRepo.save(paciente);
            }
        });
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String msg) {
        return ResponseEntity.status(status).body(Map.of("error", msg));
    }
}
