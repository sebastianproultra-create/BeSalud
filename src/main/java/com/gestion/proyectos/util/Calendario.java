package com.gestion.proyectos.util;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/** Arma un evento iCalendar (.ics, RFC 5545) con la hora de Bogotá. */
public final class Calendario {

    private static final DateTimeFormatter LOCAL = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");

    private Calendario() {
    }

    public static String evento(String uid, LocalDateTime inicio, int minutos, String titulo, String lugar,
            String descripcion) {
        String tz = Reloj.ZONA.getId();
        StringBuilder sb = new StringBuilder()
                .append("BEGIN:VCALENDAR\r\n")
                .append("VERSION:2.0\r\n")
                .append("PRODID:-//BeSalud//Citas//ES\r\n")
                .append("CALSCALE:GREGORIAN\r\n")
                .append("METHOD:PUBLISH\r\n")
                .append("BEGIN:VEVENT\r\n")
                .append("UID:").append(texto(uid)).append("\r\n")
                .append("DTSTAMP:").append(LocalDateTime.now(ZoneOffset.UTC).format(LOCAL)).append("Z\r\n")
                .append("DTSTART;TZID=").append(tz).append(':').append(inicio.format(LOCAL)).append("\r\n")
                .append("DTEND;TZID=").append(tz).append(':').append(inicio.plusMinutes(minutos).format(LOCAL)).append("\r\n")
                .append("SUMMARY:").append(texto(titulo)).append("\r\n");
        if (lugar != null && !lugar.isBlank())
            sb.append("LOCATION:").append(texto(lugar)).append("\r\n");
        if (descripcion != null && !descripcion.isBlank())
            sb.append("DESCRIPTION:").append(texto(descripcion)).append("\r\n");
        return sb.append("BEGIN:VALARM\r\n")
                .append("ACTION:DISPLAY\r\n")
                .append("DESCRIPTION:Recordatorio de tu cita\r\n")
                .append("TRIGGER:-PT2H\r\n")
                .append("END:VALARM\r\n")
                .append("END:VEVENT\r\n")
                .append("END:VCALENDAR\r\n")
                .toString();
    }

    /** Escapa los caracteres especiales de un texto iCalendar. */
    static String texto(String s) {
        return s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,")
                .replace("\r\n", "\\n").replace("\n", "\\n").replace("\r", "");
    }
}
