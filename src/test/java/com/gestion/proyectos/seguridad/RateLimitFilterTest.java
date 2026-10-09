package com.gestion.proyectos.seguridad;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTest {

    private final RateLimitFilter filter = new RateLimitFilter(new RateLimiter());

    private int enviar(String metodo, String ruta, String ip) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(metodo, ruta);
        request.setServletPath(ruta);
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response.getStatus();
    }

    @Test
    void registro_masDe10EnLaVentana_retorna429() throws Exception {
        for (int i = 0; i < 10; i++) assertThat(enviar("POST", "/register/save", "1.1.1.1")).isEqualTo(200);
        assertThat(enviar("POST", "/register/save", "1.1.1.1")).isEqualTo(429);
    }

    @Test
    void registroConGoogle_comparteElMismoLimite() throws Exception {
        for (int i = 0; i < 10; i++) enviar("POST", "/register/save", "2.2.2.2");
        assertThat(enviar("POST", "/elegir-rol", "2.2.2.2")).isEqualTo(429);
    }

    @Test
    void otraIp_noSeVeAfectada() throws Exception {
        for (int i = 0; i < 11; i++) enviar("POST", "/register/save", "3.3.3.3");
        assertThat(enviar("POST", "/register/save", "4.4.4.4")).isEqualTo(200);
    }

    @Test
    void postGenerales_masDe60PorMinuto_retorna429() throws Exception {
        for (int i = 0; i < 60; i++) assertThat(enviar("POST", "/citas/guardar-paciente", "5.5.5.5")).isEqualTo(200);
        assertThat(enviar("POST", "/citas/guardar-paciente", "5.5.5.5")).isEqualTo(429);
    }

    @Test
    void triage_masDe5PorMinuto_retorna429() throws Exception {
        for (int i = 0; i < 5; i++) assertThat(enviar("POST", "/triage", "7.7.7.7")).isEqualTo(200);
        assertThat(enviar("POST", "/triage", "7.7.7.7")).isEqualTo(429);
    }

    @Test
    void get_noSeLimita() throws Exception {
        for (int i = 0; i < 100; i++) assertThat(enviar("GET", "/register", "6.6.6.6")).isEqualTo(200);
    }
}
