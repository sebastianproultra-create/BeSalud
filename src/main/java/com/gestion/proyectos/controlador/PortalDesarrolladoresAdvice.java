package com.gestion.proyectos.controlador;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Con la API de consultorios encendida, el pie de página muestra el enlace discreto a "Desarrolladores". */
@ConditionalOnProperty(name = "app.api-consultorios.habilitada", havingValue = "true")
@ControllerAdvice
public class PortalDesarrolladoresAdvice {

    @ModelAttribute("portalDesarrolladores")
    public boolean portalDesarrolladores() {
        return true;
    }
}
