package com.gestion.proyectos.controlador;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.gestion.proyectos.modelo.SitioWeb;
import com.gestion.proyectos.repositorio.PersonaRepositorio;
import com.gestion.proyectos.servicio.ConsultorioService;
import com.gestion.proyectos.servicio.SitioWebService;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Tarjeta "Mi página web" del panel del médico. Primero, la página que BeSalud le aloja (crearla o editarla);
 * después, plegado, el código del widget para quien ya tiene web propia. Va aparte de DoctorController para no
 * tocarlo y para que, con la API apagada, el panel quede exactamente igual.
 */
@ConditionalOnProperty(name = "app.api-consultorios.habilitada", havingValue = "true")
@ControllerAdvice(assignableTypes = DoctorController.class)
public class IntegracionWebAdvice {

    private final ConsultorioService consultorioService;
    private final SitioWebService sitioService;
    private final PersonaRepositorio personaRepo;
    private final String urlApp;

    public IntegracionWebAdvice(ConsultorioService consultorioService, SitioWebService sitioService,
            PersonaRepositorio personaRepo, @Value("${app.url:http://localhost:8080}") String urlApp) {
        this.consultorioService = consultorioService;
        this.sitioService = sitioService;
        this.personaRepo = personaRepo;
        this.urlApp = urlApp.replaceAll("/+$", "");
    }

    @ModelAttribute
    public void integracionWeb(Model model, HttpServletRequest request) {
        if (!"GET".equals(request.getMethod()) || !(request.getContextPath() + "/doctores").equals(request.getRequestURI()))
            return;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities().stream().noneMatch(a -> "ROLE_DOCTOR".equals(a.getAuthority())))
            return;
        model.addAttribute("integracionWebDisponible", true);
        model.addAttribute("urlApi", urlApp);
        model.addAttribute("plantillasSitio", SitioWeb.PLANTILLAS);
        personaRepo.findDoctorByEmail(auth.getName()).ifPresent(doctor -> {
            sitioService.deDoctor(doctor.getId()).ifPresent(sitio -> {
                model.addAttribute("sitioWeb", sitio);
                model.addAttribute("urlSitio", urlApp + "/sitio/" + sitio.getSlug());
            });
            consultorioService.integracionDe(doctor.getId()).ifPresent(i -> {
                model.addAttribute("integracionWeb", i);
                // El origen de BeSalud está ahí por la página alojada; no es una web propia del médico.
                String origenApp = ConsultorioService.normalizarOrigen(urlApp);
                String origenPeticion = ConsultorioService.normalizarOrigen(
                        ServletUriComponentsBuilder.fromCurrentContextPath().toUriString());
                model.addAttribute("dominiosPropios", i.dominios().stream()
                        .filter(d -> !d.equals(origenApp) && !d.equals(origenPeticion)).toList());
            });
        });
    }
}
