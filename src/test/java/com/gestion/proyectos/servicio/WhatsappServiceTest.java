package com.gestion.proyectos.servicio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class WhatsappServiceTest {

    @ParameterizedTest
    @CsvSource({
            "3001112233,573001112233",
            "300 111 2233,573001112233",
            "300-111-2233,573001112233",
            "+57 300 111 2233,573001112233",
            "573001112233,573001112233",
            "(300) 111-22-33,573001112233"
    })
    void numero_normalizaCelularesColombianos(String entrada, String esperado) {
        assertThat(WhatsappService.numero(entrada)).isEqualTo(esperado);
    }

    @ParameterizedTest
    @CsvSource({"6011234567", "2001112233", "301111223", "30011122334", "5712345678901", "abc", "571001112233"})
    void numero_rechazaLoQueNoEsCelular(String entrada) {
        assertThat(WhatsappService.numero(entrada)).isNull();
    }

    @Test
    void numero_vacioONulo_esNull() {
        assertThat(WhatsappService.numero("")).isNull();
        assertThat(WhatsappService.numero("   ")).isNull();
        assertThat(WhatsappService.numero(null)).isNull();
    }

    @Test
    void configurado_requiereTokenYNumeroId() {
        assertThat(new WhatsappService("", "", "a", "b", "v25.0", "", "").configurado()).isFalse();
        assertThat(new WhatsappService("tok", "", "a", "b", "v25.0", "", "").configurado()).isFalse();
        assertThat(new WhatsappService(null, "123", "a", "b", "v25.0", "", "").configurado()).isFalse();
        assertThat(new WhatsappService("tok", "123", "a", "b", "v25.0", "", "").configurado()).isTrue();
    }

    @Test
    void enviar_sinConfigurarOConTelefonoInvalido_noHaceNadaNiFalla() {
        var sinConfig = new WhatsappService("", "", "a", "b", "v25.0", "", "");
        var configurado = new WhatsappService("tok", "123", "a", "b", "v25.0", "", "");
        List<String> p = List.of("Ana", "T", "C", "D", "M");
        assertThatCode(() -> sinConfig.enviar("3001112233", p, "t", List.of())).doesNotThrowAnyException();
        // teléfono inválido: sale antes de intentar cualquier llamada de red
        assertThatCode(() -> configurado.enviar("6011234567", p, "t", List.of())).doesNotThrowAnyException();
        assertThatCode(() -> configurado.enviar(null, p, "t", List.of())).doesNotThrowAnyException();
    }
}
