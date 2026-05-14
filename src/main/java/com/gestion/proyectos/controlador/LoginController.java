package com.gestion.proyectos.controlador;

import com.gestion.proyectos.modelo.UserRegistrationDTO;
import com.gestion.proyectos.servicio.RegistroService;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping
public class LoginController {

    private static final String VIEW_REGISTER = "register";
    private static final String ATTR_ERROR = "error";

    private final RegistroService registroService;

    public LoginController(RegistroService registroService) {
        this.registroService = registroService;
    }

    @GetMapping("/login")
    public String login() {
        return "login";
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
