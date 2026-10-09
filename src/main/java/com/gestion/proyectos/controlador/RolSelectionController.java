package com.gestion.proyectos.controlador;

import com.gestion.proyectos.modelo.UserRegistrationDTO;
import com.gestion.proyectos.seguridad.JwtCookieService;
import com.gestion.proyectos.seguridad.JwtService;
import com.gestion.proyectos.servicio.RegistroService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/elegir-rol")
public class RolSelectionController {

    private final RegistroService registroService;
    private final JwtService jwtService;
    private final JwtCookieService jwtCookieService;

    public RolSelectionController(RegistroService registroService,
                                   JwtService jwtService,
                                   JwtCookieService jwtCookieService) {
        this.registroService = registroService;
        this.jwtService = jwtService;
        this.jwtCookieService = jwtCookieService;
    }

    @GetMapping
    public String mostrarFormulario(HttpSession session, Model model) {
        String email = (String) session.getAttribute("oauth2Email");
        if (email == null) {
            return "redirect:/login";
        }
        model.addAttribute("email", email);
        model.addAttribute("nombre", session.getAttribute("oauth2Nombre"));
        model.addAttribute("apellido", session.getAttribute("oauth2Apellido"));
        return "elegir_rol";
    }

    @PostMapping
    public String guardarRol(@RequestParam String role,
                              @RequestParam(required = false) String nombre,
                              @RequestParam(required = false) String apellido,
                              @RequestParam(required = false) String telefono,
                              @RequestParam(required = false) String identificacion,
                              @RequestParam(required = false) String especialidad,
                              @RequestParam(required = false) String fechaNacimiento,
                              @RequestParam(required = false) String biografia,
                              HttpSession session,
                              HttpServletResponse response,
                              Model model) {
        String email = (String) session.getAttribute("oauth2Email");
        if (email == null) {
            return "redirect:/login";
        }

        String picture = (String) session.getAttribute("oauth2Picture");

        UserRegistrationDTO dto = new UserRegistrationDTO();
        dto.setEmail(email);
        dto.setNombre(nombre);
        dto.setApellido(apellido);
        dto.setTelefono(telefono);
        dto.setIdentificacion(identificacion);
        dto.setPassword(UUID.randomUUID().toString());
        dto.setRole(role);
        dto.setEspecialidad(especialidad);
        dto.setFechaNacimiento(fechaNacimiento);
        dto.setBiografia(biografia);
        dto.setFotoUrl(picture);

        String error = registroService.validar(dto);
        if (error != null) {
            model.addAttribute("error", error);
            model.addAttribute("email", email);
            model.addAttribute("nombre", nombre);
            model.addAttribute("apellido", apellido);
            return "elegir_rol";
        }

        String duplicado = registroService.verificarDuplicado(dto);
        if (duplicado != null) {
            model.addAttribute("error", duplicado);
            model.addAttribute("email", email);
            model.addAttribute("nombre", nombre);
            model.addAttribute("apellido", apellido);
            return "elegir_rol";
        }

        String resultado = registroService.registrar(dto);
        if (resultado != null) {
            model.addAttribute("error", resultado);
            model.addAttribute("email", email);
            model.addAttribute("nombre", nombre);
            model.addAttribute("apellido", apellido);
            return "elegir_rol";
        }

        session.removeAttribute("oauth2Email");
        session.removeAttribute("oauth2Nombre");
        session.removeAttribute("oauth2Apellido");
        session.removeAttribute("oauth2Picture");

        if ("DOCTOR".equals(role)) {
            // El doctor nace INACTIVO: sin token hasta que un administrador lo active
            return "redirect:/login?pendiente=true";
        }

        var ud = new User(email, "", List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        String token = jwtService.generateToken(ud);
        jwtCookieService.addJwtCookie(response, token);

        return "redirect:/pacientes/landing";
    }
}
