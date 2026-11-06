package com.gestion.proyectos.controlador;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.gestion.proyectos.repositorio.ConsultorioRepositorio;
import com.gestion.proyectos.servicio.ConsultorioService;

/**
 * Portal público para desarrolladores: qué es la API de consultorios, cómo pegar el widget, la referencia de
 * endpoints y cómo pedir acceso. No muestra datos de consultorios reales; solo el de demostración, si existe.
 */
@ConditionalOnProperty(name = "app.api-consultorios.habilitada", havingValue = "true")
@Controller
public class DesarrolladoresController {

    private final ConsultorioRepositorio consultorioRepo;
    private final String urlApp;
    private final String consultorioDemo;

    public DesarrolladoresController(ConsultorioRepositorio consultorioRepo,
            @Value("${app.url:http://localhost:8080}") String urlApp,
            @Value("${app.api-consultorios.demo:}") String consultorioDemo) {
        this.consultorioRepo = consultorioRepo;
        this.urlApp = urlApp.replaceAll("/+$", "");
        this.consultorioDemo = consultorioDemo.trim();
    }

    @GetMapping("/desarrolladores")
    public String portal(Model model) {
        model.addAttribute("urlApi", urlApp);
        // La demo en vivo solo aparece si el consultorio de demostración existe y autoriza este mismo dominio.
        String origenPortal = ConsultorioService.normalizarOrigen(urlApp);
        if (!consultorioDemo.isEmpty())
            consultorioRepo.findByIdAndActivoTrue(consultorioDemo)
                    .filter(c -> c.getDominios().contains(origenPortal))
                    .ifPresent(c -> model.addAttribute("consultorioDemo", c.getId()));
        return "publico/desarrolladores";
    }
}
