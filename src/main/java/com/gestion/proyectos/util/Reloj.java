package com.gestion.proyectos.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Fecha y hora actuales de Colombia, sin importar la zona del servidor (Railway corre en UTC,
 * cinco horas adelante: sin esto los turnos de la tarde aparecían como pasados y "hoy" cambiaba
 * a las 7 p. m.). No se cambia la zona de la JVM porque Mongo convierte las fechas con ella y
 * las citas ya guardadas se correrían un día.
 */
public final class Reloj {

    public static final ZoneId ZONA = ZoneId.of("America/Bogota");

    private Reloj() {
    }

    public static LocalDate hoy() {
        return LocalDate.now(ZONA);
    }

    public static LocalDateTime ahora() {
        return LocalDateTime.now(ZONA);
    }
}
