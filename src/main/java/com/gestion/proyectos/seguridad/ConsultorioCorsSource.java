package com.gestion.proyectos.seguridad;

import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import com.gestion.proyectos.repositorio.ConsultorioRepositorio;

import jakarta.servlet.http.HttpServletRequest;

// CORS dinámico: solo se aceptan peticiones del dominio de un consultorio activo (colección consultorios).
// Agregar un cliente nuevo = insertar su documento en Mongo, sin reiniciar ni tocar properties.
// La usa solo la cadena de seguridad de la API (api/ApiSecurityConfig); con la API apagada no existe.
@ConditionalOnProperty(name = "app.api-consultorios.habilitada", havingValue = "true")
@Component
public class ConsultorioCorsSource implements CorsConfigurationSource {

    static final Set<String> RUTAS_API = Set.of("/api/v1/pacientes", "/api/v1/sesion");
    /** API sin interfaz por consultorio (ConsultorioApiController). */
    static final String PREFIJO_API_CONSULTORIO = "/api/v1/c/";

    private final ConsultorioRepositorio consultorioRepo;
    private final boolean habilitada;

    public ConsultorioCorsSource(ConsultorioRepositorio consultorioRepo,
            @Value("${app.api-consultorios.habilitada:false}") boolean habilitada) {
        this.consultorioRepo = consultorioRepo;
        this.habilitada = habilitada;
    }

    public static boolean esRutaApi(String ruta) {
        return ruta != null && (RUTAS_API.contains(ruta) || ruta.startsWith(PREFIJO_API_CONSULTORIO));
    }

    @Override
    public CorsConfiguration getCorsConfiguration(HttpServletRequest request) {
        if (!habilitada || !esRutaApi(request.getRequestURI()))
            return null;
        String origen = request.getHeader("Origin");
        if (origen == null || !consultorioRepo.existeDominioActivo(origen))
            return null; // sin cabeceras CORS el navegador bloquea la respuesta

        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(List.of(origen));
        cfg.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        // Authorization: token del paciente en la web del consultorio (entre dominios no viajan cookies).
        // ngrok-skip-browser-warning: evita la página de aviso de ngrok (plan gratis) en los fetch de la web.
        // Idempotency-Key: reintentos seguros de POST /citas (ver api/Idempotencia).
        cfg.setAllowedHeaders(List.of("Content-Type", "Authorization", "Idempotency-Key", "ngrok-skip-browser-warning"));
        cfg.setExposedHeaders(List.of("Idempotent-Replayed"));
        cfg.setAllowCredentials(false);
        cfg.setMaxAge(3600L);
        return cfg;
    }
}
