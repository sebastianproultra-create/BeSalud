package com.gestion.proyectos.controlador;

import java.util.NoSuchElementException;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.gestion.proyectos.servicio.SitioWebService;

/**
 * Página web alojada del médico (/sitio/{slug}). No lleva la barra ni el pie de BeSalud: es la web del
 * consultorio. El paciente agenda, paga y ve sus citas con el widget, sin entrar al gestor.
 */
@ConditionalOnProperty(name = "app.api-consultorios.habilitada", havingValue = "true")
@Controller
public class SitioPublicoController {

    private final SitioWebService sitioService;

    public SitioPublicoController(SitioWebService sitioService) {
        this.sitioService = sitioService;
    }

    @GetMapping("/sitio/{slug}")
    public String sitio(@PathVariable String slug, Model model) {
        model.addAttribute("pagina", sitioService.publica(slug).orElseThrow(NoSuchElementException::new));
        model.addAttribute("vistaPrevia", false);
        return "sitio/pagina";
    }
}
