package com.gestion.proyectos.util;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Enlaces de calendario para las citas, con la hora de Bogotá. */
public final class Calendario {

    private static final DateTimeFormatter LOCAL = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");

    private Calendario() {
    }

    /** Enlace que abre Google Calendar con el evento listo para guardar (no necesita descargar nada). */
    public static String enlaceGoogle(LocalDateTime inicio, int minutos, String titulo, String lugar,
            String descripcion) {
        StringBuilder sb = new StringBuilder("https://calendar.google.com/calendar/render?action=TEMPLATE")
                .append("&text=").append(url(titulo))
                .append("&dates=").append(inicio.format(LOCAL)).append('/').append(inicio.plusMinutes(minutos).format(LOCAL))
                .append("&ctz=").append(url(Reloj.ZONA.getId()));
        if (lugar != null && !lugar.isBlank())
            sb.append("&location=").append(url(lugar));
        if (descripcion != null && !descripcion.isBlank())
            sb.append("&details=").append(url(descripcion));
        return sb.toString();
    }

    private static String url(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
