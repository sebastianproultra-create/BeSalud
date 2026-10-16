package com.gestion.proyectos.controlador;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.gestion.proyectos.modelo.MensajeAyuda;
import com.gestion.proyectos.servicio.AyudaService;

/** Centro de ayuda público: preguntas frecuentes y formulario de contacto. */
@Controller
public class AyudaController {

    private final AyudaService ayudaService;

    public AyudaController(AyudaService ayudaService) {
        this.ayudaService = ayudaService;
    }

    @GetMapping("/ayuda")
    public String ayuda(Model model, CsrfToken csrf) {
        // El formulario está al final de una página larga: si el token se genera al
        // renderizarlo, la respuesta ya empezó a enviarse y no se puede crear la sesión.
        csrf.getToken();
        model.addAttribute("temas", AyudaService.TEMAS);
        return "publico/ayuda";
    }

    @PostMapping("/ayuda/contacto")
    public String contacto(@RequestParam(required = false) String nombre,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String tema,
            @RequestParam(required = false) String mensaje,
            @RequestParam(required = false) String sitioWeb,
            RedirectAttributes redirect) {
        // Campo trampa invisible: si viene lleno es un bot; se responde como si se hubiera enviado
        if (sitioWeb != null && !sitioWeb.isBlank())
            return "redirect:/ayuda?enviado#contacto";

        MensajeAyuda m = new MensajeAyuda();
        m.setNombre(nombre);
        m.setEmail(email);
        m.setTema(tema);
        m.setMensaje(mensaje);
        String error = ayudaService.validar(m);
        if (error != null) {
            redirect.addFlashAttribute("errorContacto", error);
            redirect.addFlashAttribute("contacto", m);
            return "redirect:/ayuda#contacto";
        }
        ayudaService.avisarAdministradores(ayudaService.guardar(m));
        return "redirect:/ayuda?enviado#contacto";
    }
}
