package com.gestion.proyectos.servicio;

import com.gestion.proyectos.servicio.AsistenteService.Franja;
import com.gestion.proyectos.servicio.AsistenteService.Intencion;
import com.gestion.proyectos.servicio.AsistenteService.Interpretacion;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class AsistenteServiceTest {

    // Viernes
    private static final LocalDate HOY = LocalDate.of(2026, 10, 23);

    private static Interpretacion leer(String texto) {
        return AsistenteService.interpretarConReglas(texto, HOY);
    }

    @Test
    void diaDeLaSemanaConFranja() {
        Interpretacion i = leer("¿Tienes algo el jueves en la tarde?");
        assertThat(i.intencion()).isEqualTo(Intencion.AGENDAR);
        assertThat(i.fecha()).isEqualTo(LocalDate.of(2026, 10, 29));
        assertThat(i.franja()).isEqualTo(Franja.TARDE);
    }

    @Test
    void mananaEnLaManana_noConfundeElDiaConLaFranja() {
        Interpretacion i = leer("¿Qué tienes mañana en la mañana?");
        assertThat(i.fecha()).isEqualTo(HOY.plusDays(1));
        assertThat(i.franja()).isEqualTo(Franja.MANANA);
    }

    @Test
    void soloFranja_buscaDesdeHoy() {
        Interpretacion i = leer("en la mañana");
        assertThat(i.intencion()).isEqualTo(Intencion.AGENDAR);
        assertThat(i.fecha()).isNull();
        assertThat(i.franja()).isEqualTo(Franja.MANANA);
    }

    @Test
    void pasadoMananaYHoy() {
        assertThat(leer("pasado mañana").fecha()).isEqualTo(HOY.plusDays(2));
        assertThat(leer("¿atiende hoy?").fecha()).isEqualTo(HOY);
        assertThat(leer("el viernes").fecha()).isEqualTo(HOY);
    }

    @Test
    void fechasEscritas() {
        assertThat(leer("el 25 de octubre").fecha()).isEqualTo(LocalDate.of(2026, 10, 25));
        assertThat(leer("para el 2/11").fecha()).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(leer("el 5").fecha()).isEqualTo(LocalDate.of(2026, 11, 5));
    }

    @Test
    void fechaMuyLejana_seIgnora() {
        assertThat(leer("el 20 de mayo").fecha()).isNull();
    }

    private static com.gestion.proyectos.modelo.Doctor doctor(String apellido, String especialidad) {
        com.gestion.proyectos.modelo.Doctor d = new com.gestion.proyectos.modelo.Doctor();
        d.setApellido(apellido);
        d.setEspecialidad(especialidad);
        return d;
    }

    @Test
    void portal_filtraPorEspecialidadOApellido() {
        var fontalvo = doctor("Fontalvo", "Medicina General");
        var ruiz = doctor("Ruiz", "Psicología");
        var todos = java.util.List.of(fontalvo, ruiz);

        assertThat(AsistenteService.mencionados(todos, AsistenteService.normalizar("Quiero un psicólogo el lunes")))
                .containsExactly(ruiz);
        assertThat(AsistenteService.mencionados(todos, AsistenteService.normalizar("cita de medicina general")))
                .containsExactly(fontalvo);
        assertThat(AsistenteService.mencionados(todos, AsistenteService.normalizar("con el doctor Fontalvo")))
                .containsExactly(fontalvo);
        assertThat(AsistenteService.mencionados(todos, AsistenteService.normalizar("algo mañana en la tarde"))).isEmpty();
    }

    @Test
    void preguntasSinFecha() {
        assertThat(leer("¿Cuánto cuesta la consulta?").intencion()).isEqualTo(Intencion.PRECIO);
        assertThat(leer("¿Cómo funciona el abono?").intencion()).isEqualTo(Intencion.ABONO);
        assertThat(leer("¿Dónde queda el consultorio?").intencion()).isEqualTo(Intencion.UBICACION);
        assertThat(leer("me duele la cabeza, qué tomo").intencion()).isEqualTo(Intencion.SINTOMAS);
        assertThat(leer("quiero una cita").intencion()).isEqualTo(Intencion.AGENDAR);
        assertThat(leer("hola").intencion()).isEqualTo(Intencion.SALUDO);
        assertThat(leer("asdf").intencion()).isEqualTo(Intencion.OTRO);
    }
}
