package com.gestion.proyectos.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class CalendarioTest {

    @Test
    void eventoConHoraDeBogotaYTextoEscapado() {
        String ics = Calendario.evento("c1@besalud", LocalDateTime.of(2026, 10, 27, 9, 35), 30,
                "Cita con Dr(a). Ana", "Cra 45 # 72-10, Barranquilla; piso 3", "Línea 1\nLínea 2");

        assertThat(ics).startsWith("BEGIN:VCALENDAR\r\n").endsWith("END:VCALENDAR\r\n")
                .contains("DTSTART;TZID=America/Bogota:20261027T093500\r\n")
                .contains("DTEND;TZID=America/Bogota:20261027T100500\r\n")
                .contains("LOCATION:Cra 45 # 72-10\\, Barranquilla\\; piso 3\r\n")
                .contains("DESCRIPTION:Línea 1\\nLínea 2\r\n");
    }
}
