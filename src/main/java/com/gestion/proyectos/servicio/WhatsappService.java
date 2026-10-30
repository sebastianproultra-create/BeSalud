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

// Envía por la WhatsApp Cloud API de Meta. Las respuestas a los botones llegan al bot (servicio besalud-whatsapp),
// que las reenvía a /whatsapp/asistencia para que confirmar o cancelar use la misma lógica que el portal.
@Service
public class WhatsappService {

    private static final Logger log = LoggerFactory.getLogger(WhatsappService.class);

    public record Boton(String id, String titulo) {}

    private final RestClient http;
    private final String token;
    private final String numeroId;
    private final String plantillaAviso;
    private final String plantillaAsistencia;
    private final String graph;

    public WhatsappService(@Value("${app.whatsapp.token:}") String token,
            @Value("${app.whatsapp.phone-number-id:}") String numeroId,
            @Value("${app.whatsapp.plantilla-aviso:aviso_cita_besalud}") String plantillaAviso,
            @Value("${app.whatsapp.plantilla-asistencia:asistencia_cita_besalud}") String plantillaAsistencia,
            @Value("${app.whatsapp.graph-version:v25.0}") String version) {
        this.token = token == null ? "" : token.trim();
        this.numeroId = numeroId == null ? "" : numeroId.trim();
        this.plantillaAviso = plantillaAviso;
        this.plantillaAsistencia = plantillaAsistencia;
        this.graph = "https://graph.facebook.com/" + version;
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(5_000);
        timeouts.setReadTimeout(10_000);
        this.http = RestClient.builder().requestFactory(timeouts).build();
    }

    public boolean configurado() {
        return !token.isBlank() && !numeroId.isBlank();
    }

    /** Celular colombiano en formato internacional (57 + 10 dígitos), o null si no es un celular válido. */
    public static String numero(String telefono) {
        String digitos = telefono == null ? "" : telefono.replaceAll("\\D", "");
        if (digitos.length() == 10 && digitos.startsWith("3")) return "57" + digitos;
        if (digitos.length() == 12 && digitos.startsWith("573")) return digitos;
        return null;
    }

    /**
     * Primero intenta la plantilla aprobada (llega aunque el paciente no haya escrito); si Meta la rechaza,
     * manda el mismo contenido como mensaje normal, que solo llega si el paciente escribió en las últimas 24 h.
     * parametros: nombre, título, cuándo, con quién y mensaje, en el orden de la plantilla.
     */
    public void enviar(String telefono, List<String> parametros, String texto, List<Boton> botones) {
        String para = numero(telefono);
        if (para == null) return;
        if (!configurado()) {
            log.info("WhatsApp no enviado (falta WHATSAPP_TOKEN o WHATSAPP_PHONE_NUMBER_ID) a ...{}", para.substring(8));
            return;
        }
        if (publicar(plantilla(para, parametros, botones)) || publicar(mensajeLibre(para, texto, botones)))
            log.info("WhatsApp enviado a ...{}", para.substring(8));
    }

    private Map<String, Object> plantilla(String para, List<String> parametros, List<Boton> botones) {
        var componentes = new java.util.ArrayList<Map<String, Object>>();
        componentes.add(Map.of("type", "body", "parameters",
                parametros.stream().map(p -> Map.of("type", "text", "text", p.isBlank() ? "-" : p)).toList()));
        for (int i = 0; i < botones.size(); i++)
            componentes.add(Map.of("type", "button", "sub_type", "quick_reply", "index", String.valueOf(i),
                    "parameters", List.of(Map.of("type", "payload", "payload", botones.get(i).id()))));
        return Map.of("messaging_product", "whatsapp", "to", para, "type", "template",
                "template", Map.of("name", botones.isEmpty() ? plantillaAviso : plantillaAsistencia,
                        "language", Map.of("code", "es"), "components", componentes));
    }

    private static Map<String, Object> mensajeLibre(String para, String texto, List<Boton> botones) {
        if (botones.isEmpty())
            return Map.of("messaging_product", "whatsapp", "to", para, "type", "text",
                    "text", Map.of("body", texto, "preview_url", true));
        return Map.of("messaging_product", "whatsapp", "to", para, "type", "interactive",
                "interactive", Map.of("type", "button", "body", Map.of("text", texto),
                        "footer", Map.of("text", "BeSalud"),
                        "action", Map.of("buttons", botones.stream()
                                .map(b -> Map.of("type", "reply", "reply", Map.of("id", b.id(), "title", b.titulo())))
                                .toList())));
    }

    private boolean publicar(Map<String, Object> cuerpo) {
        try {
            http.post().uri(graph + "/" + numeroId + "/messages")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(cuerpo)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.warn("WhatsApp rechazado ({}): {}", cuerpo.get("type"), e.getMessage());
            return false;
        }
    }
}
