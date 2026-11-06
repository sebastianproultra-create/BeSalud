package com.gestion.proyectos.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.gestion.proyectos.servicio.AyudaService;

import jakarta.annotation.PostConstruct;

/**
 * Temas del Centro de ayuda que solo existen con la API de consultorios encendida. Se agregan aquí y no en
 * AyudaService para que, con la API apagada, el Centro de ayuda quede exactamente como el del equipo.
 */
@ConditionalOnProperty(name = "app.api-consultorios.habilitada", havingValue = "true")
@Component
public class TemasAyudaApi {

    @PostConstruct
    void agregarTemas() {
        // "Otro tema" sigue de último
        String otro = AyudaService.TEMAS.remove("otro");
        AyudaService.TEMAS.putIfAbsent("pagina-web", "Mi página web (solicitarla o pedir cambios)");
        AyudaService.TEMAS.putIfAbsent("integracion", "Conectar mi página web (desarrolladores)");
        if (otro != null) AyudaService.TEMAS.put("otro", otro);
    }
}
