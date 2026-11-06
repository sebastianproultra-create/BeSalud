package com.gestion.proyectos.servicio;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class CorreoServiceTest {

    @Test
    void puedeEnviarA_soloConCorreoConfiguradoYDireccionReal() {
        CorreoService correo = new CorreoService("clave", "hola@besalud.co", "BeSalud");
        assertThat(correo.puedeEnviarA("alguien@gmail.com")).isTrue();
        assertThat(correo.puedeEnviarA("laura.martinez@sim.besalud.com")).isFalse();
        assertThat(correo.puedeEnviarA("ana@demo.besalud.com")).isFalse();
        assertThat(correo.puedeEnviarA("ana@clinica.test")).isFalse();
        assertThat(correo.puedeEnviarA(null)).isFalse();
        assertThat(new CorreoService("", "", "BeSalud").puedeEnviarA("alguien@gmail.com")).isFalse();
    }

    @Test
    void sinApiKey_noEstaConfiguradoYNoFalla() {
        CorreoService correo = new CorreoService("", "", "BeSalud");
        assertThat(correo.configurado()).isFalse();
        assertThatCode(() -> correo.enviar("alguien@gmail.com", "Alguien", "Asunto", "<p>hola</p>"))
                .doesNotThrowAnyException();
    }
}
