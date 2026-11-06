package com.gestion.proyectos.api;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Idempotency-Key: una sola ejecución por clave, error si cambian los datos, y la clave se libera si falla. */
class IdempotenciaTest {

    private final AtomicInteger llamadas = new AtomicInteger();

    private String accion() {
        return "reserva-" + llamadas.incrementAndGet();
    }

    @Test
    void sinClave_ejecutaSiempre() {
        Idempotencia idem = new Idempotencia();
        idem.ejecutar("c|p", null, "x", this::accion);
        idem.ejecutar("c|p", " ", "x", this::accion);
        assertThat(llamadas).hasValue(2);
    }

    @Test
    void mismaClave_devuelveLaMismaRespuesta() {
        Idempotencia idem = new Idempotencia();
        var primera = idem.ejecutar("c|p", "clave-12345", "x", this::accion);
        var segunda = idem.ejecutar("c|p", "clave-12345", "x", this::accion);

        assertThat(primera.repetido()).isFalse();
        assertThat(segunda.repetido()).isTrue();
        assertThat(segunda.valor()).isEqualTo(primera.valor());
        assertThat(llamadas).hasValue(1);
    }

    @Test
    void laClaveEsPorPaciente() {
        Idempotencia idem = new Idempotencia();
        idem.ejecutar("c|p1", "clave-12345", "x", this::accion);
        var otro = idem.ejecutar("c|p2", "clave-12345", "x", this::accion);
        assertThat(otro.repetido()).isFalse();
        assertThat(llamadas).hasValue(2);
    }

    @Test
    void mismaClaveConOtrosDatos_esUnError() {
        Idempotencia idem = new Idempotencia();
        idem.ejecutar("c|p", "clave-12345", "08:00", this::accion);
        assertThatThrownBy(() -> idem.ejecutar("c|p", "clave-12345", "09:00", this::accion))
                .isInstanceOf(ConsultorioApiController.ApiError.class).hasMessageContaining("otros datos");
    }

    @Test
    void siFalla_laClaveSeLiberaYElReintentoSeEjecuta() {
        Idempotencia idem = new Idempotencia();
        assertThatThrownBy(() -> idem.ejecutar("c|p", "clave-12345", "x", () -> {
            throw new IllegalStateException("turno ocupado");
        })).hasMessage("turno ocupado");

        var reintento = idem.ejecutar("c|p", "clave-12345", "x", this::accion);
        assertThat(reintento.repetido()).isFalse();
        assertThat(llamadas).hasValue(1);
    }

    @Test
    void claveInvalida_400() {
        Idempotencia idem = new Idempotencia();
        assertThatThrownBy(() -> idem.ejecutar("c|p", "corta", "x", this::accion))
                .isInstanceOf(ConsultorioApiController.ApiError.class);
        assertThatThrownBy(() -> idem.ejecutar("c|p", "con espacios y ñ", "x", this::accion))
                .isInstanceOf(ConsultorioApiController.ApiError.class);
    }

    @Test
    void pasadas24Horas_laClaveVence() {
        MutableClock reloj = new MutableClock(Instant.parse("2026-10-30T10:00:00Z"));
        Idempotencia idem = new Idempotencia(reloj);
        idem.ejecutar("c|p", "clave-12345", "x", this::accion);

        reloj.avanzar(Idempotencia.VIGENCIA.plus(Duration.ofMinutes(1)));
        var despues = idem.ejecutar("c|p", "clave-12345", "x", this::accion);
        assertThat(despues.repetido()).isFalse();
        assertThat(llamadas).hasValue(2);
    }

    private static final class MutableClock extends Clock {
        private Instant ahora;

        MutableClock(Instant ahora) {
            this.ahora = ahora;
        }

        void avanzar(Duration d) {
            ahora = ahora.plus(d);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }
}
