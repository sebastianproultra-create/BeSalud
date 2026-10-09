package com.gestion.proyectos.servicio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

// Envía por la API HTTP de Brevo: Railway bloquea SMTP salvo en el plan Pro.
@Service
public class CorreoService {

    private static final Logger log = LoggerFactory.getLogger(CorreoService.class);

    // Cuentas de prueba con correos inventados: enviarles rebota y daña la reputación del remitente.
    private static final List<String> DOMINIOS_FICTICIOS = List.of("@demo.besalud.com", "@besalud.com", "@example.com");

    private final RestClient http;
    private final String apiKey;
    private final String remitente;
    private final String nombreRemitente;

    public CorreoService(@Value("${app.correo.brevo-api-key:}") String apiKey,
            @Value("${app.correo.remitente:}") String remitente,
            @Value("${app.correo.nombre:BeSalud}") String nombreRemitente) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.remitente = remitente == null ? "" : remitente.trim();
        this.nombreRemitente = nombreRemitente;
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(5_000);
        timeouts.setReadTimeout(10_000);
        this.http = RestClient.builder().requestFactory(timeouts).build();
    }

    public boolean configurado() {
        return !apiKey.isBlank() && !remitente.isBlank();
    }

    public void enviar(String destinatario, String nombre, String asunto, String html) {
        if (destinatario == null || destinatario.isBlank()) return;
        String email = destinatario.trim().toLowerCase();
        if (DOMINIOS_FICTICIOS.stream().anyMatch(email::endsWith)) {
            log.debug("Correo omitido (cuenta de prueba): {}", email);
            return;
        }
        if (!configurado()) {
            log.info("Correo no enviado (falta BREVO_API_KEY o MAIL_REMITENTE): '{}' a {}", asunto, email);
            return;
        }
        try {
            http.post()
                    .uri("https://api.brevo.com/v3/smtp/email")
                    .header("api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "sender", Map.of("name", nombreRemitente, "email", remitente),
                            "to", List.of(Map.of("email", email, "name", nombre == null ? email : nombre)),
                            "subject", asunto,
                            "htmlContent", html))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Correo enviado: '{}' a {}", asunto, email);
        } catch (Exception e) {
            log.warn("No se pudo enviar el correo '{}' a {}: {}", asunto, email, e.getMessage());
        }
    }
}
