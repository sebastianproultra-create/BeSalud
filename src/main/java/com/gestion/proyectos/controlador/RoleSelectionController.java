package com.gestion.proyectos.controlador;

import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping
public class RoleSelectionController {

    @GetMapping("/seleccionar-rol")
    public String seleccionarRol(Model model, Authentication authentication) {
        boolean isDoctor = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> "ROLE_DOCTOR".equals(a));
        boolean isPaciente = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> "ROLE_PACIENTE".equals(a));

        model.addAttribute("isDoctor", isDoctor);
        model.addAttribute("isPaciente", isPaciente);
        return "seleccionar_rol";
    }

    @PostMapping("/seleccionar-rol")
    public String guardarSeleccion(@RequestParam("rol") String rol, HttpSession session) {
        if (!"ROLE_DOCTOR".equals(rol) && !"ROLE_PACIENTE".equals(rol)) {
            return "redirect:/login?error=true";
        }
        session.setAttribute("selectedRole", rol);
        if ("ROLE_DOCTOR".equals(rol)) {
            return "redirect:/doctores";
        }
        return "redirect:/pacientes/landing";
    }
}
