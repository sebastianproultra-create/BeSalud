package com.gestion.proyectos.oauth2;

import com.gestion.proyectos.modelo.Doctor;
import com.gestion.proyectos.repositorio.AdminRepositorio;
import com.gestion.proyectos.repositorio.PersonaRepositorio;
import com.gestion.proyectos.seguridad.JwtCookieService;
import com.gestion.proyectos.seguridad.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final PersonaRepositorio personaRepositorio;
    private final AdminRepositorio adminRepositorio;
    private final JwtService jwtService;
    private final JwtCookieService jwtCookieService;

    public OAuth2LoginSuccessHandler(PersonaRepositorio personaRepositorio,
                                     AdminRepositorio adminRepositorio,
                                     JwtService jwtService,
                                     JwtCookieService jwtCookieService) {
        this.personaRepositorio = personaRepositorio;
        this.adminRepositorio = adminRepositorio;
        this.jwtService = jwtService;
        this.jwtCookieService = jwtCookieService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
        String email = oauth2User.getAttribute("email");

        if (email == null) {
            response.sendRedirect("/login?error=oauth_no_email");
            return;
        }
        email = email.toLowerCase().trim();

        // Check admin first
        final String emailFinal = email;
        var admin = adminRepositorio.findByEmail(emailFinal).orElse(null);
        if (admin != null) {
            var ud = new User(emailFinal, "", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
            String token = jwtService.generateToken(ud);
            jwtCookieService.addJwtCookie(response, token);
            HttpSession adminSession = request.getSession(false);
            if (adminSession != null) {
                adminSession.removeAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
            }
            SecurityContextHolder.clearContext();
            response.sendRedirect("/admin");
            return;
        }

        var persona = personaRepositorio.findByEmail(emailFinal).orElse(null);
        if (persona != null) {
            if (persona instanceof Doctor doctor && !"ACTIVO".equals(doctor.getEstado())) {
                HttpSession pendiente = request.getSession(false);
                if (pendiente != null) {
                    pendiente.removeAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
                }
                SecurityContextHolder.clearContext();
                response.sendRedirect("/login?pendiente=true");
                return;
            }
            String role = "ROLE_" + persona.getRole();
            var ud = new User(emailFinal, "", List.of(new SimpleGrantedAuthority(role)));
            String token = jwtService.generateToken(ud);
            jwtCookieService.addJwtCookie(response, token);
            HttpSession personaSession = request.getSession(false);
            if (personaSession != null) {
                personaSession.removeAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
            }
            SecurityContextHolder.clearContext();
            if ("DOCTOR".equals(persona.getRole())) {
                response.sendRedirect("/doctores");
            } else {
                response.sendRedirect("/pacientes/landing");
            }
            return;
        }

        // New user — store email in session, redirect to role selection
        HttpSession session = request.getSession();
        session.setAttribute("oauth2Email", email);
        String nombre = oauth2User.getAttribute("given_name");
        String apellido = oauth2User.getAttribute("family_name");
        String picture = oauth2User.getAttribute("picture");
        if (nombre != null) session.setAttribute("oauth2Nombre", nombre);
        if (apellido != null) session.setAttribute("oauth2Apellido", apellido);
        if (picture != null) session.setAttribute("oauth2Picture", picture);

        response.sendRedirect("/elegir-rol");
    }
}
