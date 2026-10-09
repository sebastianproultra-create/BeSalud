package com.gestion.proyectos.seguridad;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterTest {

    private final RateLimiter limiter = new RateLimiter();
    private final Duration ventana = Duration.ofMinutes(1);

    @Test
    void permitir_hastaElMaximo_luegoRechaza() {
        for (int i = 0; i < 3; i++) assertThat(limiter.permitir("k", 3, ventana)).isTrue();
        assertThat(limiter.permitir("k", 3, ventana)).isFalse();
    }

    @Test
    void permitir_clavesDistintasSonIndependientes() {
        for (int i = 0; i < 3; i++) limiter.permitir("a", 3, ventana);
        assertThat(limiter.permitir("a", 3, ventana)).isFalse();
        assertThat(limiter.permitir("b", 3, ventana)).isTrue();
    }

    @Test
    void permitir_eventosFueraDeLaVentanaNoCuentan() throws InterruptedException {
        Duration corta = Duration.ofMillis(50);
        limiter.permitir("k", 1, corta);
        assertThat(limiter.permitir("k", 1, corta)).isFalse();
        Thread.sleep(80);
        assertThat(limiter.permitir("k", 1, corta)).isTrue();
    }

    @Test
    void bloqueado_alcanzaElMaximoDeFallos_yReiniciarLoLibera() {
        assertThat(limiter.bloqueado("login", 2, ventana)).isFalse();
        limiter.registrar("login", ventana);
        limiter.registrar("login", ventana);
        assertThat(limiter.bloqueado("login", 2, ventana)).isTrue();
        limiter.reiniciar("login");
        assertThat(limiter.bloqueado("login", 2, ventana)).isFalse();
    }
}
