package com.gestion.proyectos.controlador;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** El parámetro continuar del login solo acepta rutas internas de reserva (sin open redirect). */
class ContinuarSeguroTest {

    @Test
    void aceptaRutasDeReserva() {
        assertThat(LoginController.continuarSeguro("/citas/nueva?doctorId=abc123&fecha=2026-11-02&hora=09:00"))
                .isEqualTo("/citas/nueva?doctorId=abc123&fecha=2026-11-02&hora=09:00");
        assertThat(LoginController.continuarSeguro("/p/maria-perez")).isEqualTo("/p/maria-perez");
    }

    @Test
    void rechazaDestinosExternosOAjenos() {
        assertThat(LoginController.continuarSeguro("https://evil.com")).isNull();
        assertThat(LoginController.continuarSeguro("//evil.com")).isNull();
        assertThat(LoginController.continuarSeguro("/p//evil.com")).isNull();
        assertThat(LoginController.continuarSeguro("/admin/doctores")).isNull();
        assertThat(LoginController.continuarSeguro("/citas/nueva?x=<script>")).isNull();
        assertThat(LoginController.continuarSeguro("/p/x\r\nSet-Cookie:a")).isNull();
        assertThat(LoginController.continuarSeguro(null)).isNull();
    }
}
