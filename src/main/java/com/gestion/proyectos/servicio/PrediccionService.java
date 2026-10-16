package com.gestion.proyectos.servicio;

import java.net.URI;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/** Cliente de la API predictiva en Python (FastAPI + Prophet). */
@Service
public class PrediccionService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String pythonApiUrl;

    public PrediccionService(@Value("${api.python.url}") String pythonApiUrl) {
        this.pythonApiUrl = pythonApiUrl;
    }

    /**
     * Proyección de citas de una especialidad; con medicoId, la de ese médico.
     * Devuelve el JSON tal cual lo entrega la API (fecha, volumenProyectado, margenMinimo, margenMaximo).
     */
    public String obtenerProyeccion(String especialidad, String medicoId, int dias) {
        URI uri = UriComponentsBuilder.fromUriString(pythonApiUrl)
                .pathSegment("prediccion", especialidad)
                .queryParam("dias", dias)
                .queryParamIfPresent("medico_id", Optional.ofNullable(medicoId).filter(s -> !s.isBlank()))
                .encode()
                .build()
                .toUri();
        return restTemplate.getForObject(uri, String.class);
    }
}
