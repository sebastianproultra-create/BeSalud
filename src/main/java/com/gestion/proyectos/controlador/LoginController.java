package com.gestion.proyectos.controlador;

import java.time.Duration;

import com.gestion.proyectos.modelo.UserRegistrationDTO;
import com.gestion.proyectos.modelo.Doctor;
import com.gestion.proyectos.seguridad.JwtCookieService;
import com.gestion.proyectos.seguridad.JwtService;
import com.gestion.proyectos.seguridad.RateLimiter;
import com.gestion.proyectos.servicio.RegistroService;
import com.gestion.proyectos.servicio.DoctorService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping
public class LoginController {

    private static final String VIEW_REGISTER = "register";
    private static final String ATTR_ERROR = "error";

    private final RegistroService registroService;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final JwtCookieService jwtCookieService;
    private final DoctorService doctorService;
    private final RateLimiter rateLimiter;

    private static final int MAX_FALLOS_LOGIN = 5;
    private static final Duration VENTANA_LOGIN = Duration.ofMinutes(15);

    public LoginController(RegistroService registroService,
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            JwtCookieService jwtCookieService,
            DoctorService doctorService,
            RateLimiter rateLimiter) {
        this.registroService = registroService;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.jwtCookieService = jwtCookieService;
        this.doctorService = doctorService;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @PostMapping("/login")
    public String loginPost(@RequestParam String email, @RequestParam String password,
            HttpServletRequest request, HttpServletResponse response, Model model) {
        String claveLogin = "login:" + request.getRemoteAddr() + ":" + email.trim().toLowerCase();
        if (rateLimiter.bloqueado(claveLogin, MAX_FALLOS_LOGIN, VENTANA_LOGIN))
            return "redirect:/login?bloqueado=true";
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, password));
            rateLimiter.reiniciar(claveLogin);

            // Validar si es doctor inactivo
            boolean isDoctor = authentication.getAuthorities().stream()
                    .anyMatch(a -> "ROLE_DOCTOR".equals(a.getAuthority()));

            if (isDoctor) {
                Doctor doctor = doctorService.buscarPorEmail(email).orElseThrow();
                if ("INACTIVO".equals(doctor.getEstado())) {
                    return "cuenta_pendiente_activacion";
                }
            }

            String token = jwtService.generateToken((UserDetails) authentication.getPrincipal());
            jwtCookieService.addJwtCookie(response, token);

            boolean isAdmin = authentication.getAuthorities().stream()
                    .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));

            if (isAdmin)
                return "redirect:/admin";
            if (isDoctor)
                return "redirect:/doctores";
            return "redirect:/pacientes/landing";
        } catch (AuthenticationException e) {
            rateLimiter.registrar(claveLogin, VENTANA_LOGIN);
            return "redirect:/login?error=true";
        } catch (Exception e) {
            return "redirect:/login?error=true";
        }
    }

    @GetMapping("/register")
    public String register(Model model) {
        model.addAttribute("user", new UserRegistrationDTO());
        return VIEW_REGISTER;
    }

    @PostMapping("/register/save")
    public String registerSave(@Valid UserRegistrationDTO user, BindingResult binding, Model model) {
        if (binding.hasErrors()) {
            String msg = binding.getAllErrors().get(0).getDefaultMessage();
            model.addAttribute("user", user);
            model.addAttribute(ATTR_ERROR, msg);
            return VIEW_REGISTER;
        }

        String error = registroService.validar(user);
        if (error != null) {
            model.addAttribute("user", user);
            model.addAttribute(ATTR_ERROR, error);
            return VIEW_REGISTER;
        }

        error = registroService.verificarDuplicado(user);
        if (error != null) {
            model.addAttribute("user", user);
            model.addAttribute(ATTR_ERROR, error);
            return VIEW_REGISTER;
        }

        error = registroService.registrar(user);
        if (error != null) {
            model.addAttribute("user", user);
            model.addAttribute(ATTR_ERROR, error);
            return VIEW_REGISTER;
        }

        return "redirect:/login?registerSuccess";
    }
}
