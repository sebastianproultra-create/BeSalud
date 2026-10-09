package com.gestion.proyectos.servicio;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class CorreoServiceTest {

    @Test
    void sinApiKey_noEstaConfiguradoYNoFalla() {
        CorreoService correo = new CorreoService("", "", "BeSalud");
        assertThat(correo.configurado()).isFalse();
        assertThatCode(() -> correo.enviar("alguien@gmail.com", "Alguien", "Asunto", "<p>hola</p>"))
                .doesNotThrowAnyException();
    }
}
