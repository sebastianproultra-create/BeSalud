package com.gestion.proyectos.api;

import com.gestion.proyectos.controlador.LoginController;
import com.gestion.proyectos.oauth2.OAuth2LoginSuccessHandler;
import com.gestion.proyectos.repositorio.AdminRepositorio;
import com.gestion.proyectos.repositorio.PersonaRepositorio;
import com.gestion.proyectos.seguridad.CustomUserDetailsService;
import com.gestion.proyectos.seguridad.JwtCookieService;
import com.gestion.proyectos.seguridad.JwtService;
import com.gestion.proyectos.seguridad.RateLimiter;
import com.gestion.proyectos.seguridad.SecurityConfig;
import com.gestion.proyectos.servicio.DoctorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Con la API de consultorios apagada (el valor por defecto) BeSalud es el del equipo: no existe la cadena de
 * seguridad de la API, ni sus beans, y sus rutas no se atienden (caen en las reglas normales del gestor).
 * Se importa ApiSecurityConfig a propósito: aun así no debe registrarse.
 */
@WebMvcTest(LoginController.class)
@Import({SecurityConfig.class, ApiSecurityConfig.class})
class ApiApagadaTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ApplicationContext contexto;
    @MockBean private RateLimiter rateLimiter;
    @MockBean private PersonaRepositorio personaRepositorio;
    @MockBean private AdminRepositorio adminRepositorio;
    @MockBean private CustomUserDetailsService customUserDetailsService;
    @MockBean private com.gestion.proyectos.servicio.RegistroService registroService;
    @MockBean private JwtService jwtService;
    @MockBean private JwtCookieService jwtCookieService;
    @MockBean private DoctorService doctorService;
    @MockBean private OAuth2LoginSuccessHandler oauth2LoginSuccessHandler;
    @MockBean private org.springframework.security.authentication.AuthenticationManager authenticationManager;

    @BeforeEach
    void preparar() {
        when(rateLimiter.permitir(anyString(), anyInt(), any())).thenReturn(true);
    }

    @Test
    void sinCadenaNiBeansDeLaApi() {
        assertThat(contexto.getBeanNamesForType(ApiSecurityConfig.class)).isEmpty();
        assertThat(contexto.containsBean("apiConsultoriosFilterChain")).isFalse();
        assertThat(contexto.getBeanNamesForType(com.gestion.proyectos.seguridad.ConsultorioCorsSource.class)).isEmpty();
    }

    @Test
    void lasRutasDeLaApiNoSeAtienden() throws Exception {
        // Sin sesión van al login, como cualquier ruta privada del gestor
        mockMvc.perform(get("/api/v1/c/demo")).andExpect(redirectedUrl("/login?sessionExpired=true"));
        mockMvc.perform(get("/sitio/dr-leo-ruiz")).andExpect(redirectedUrl("/login?sessionExpired=true"));
        mockMvc.perform(get("/widget/besalud-widget.js")).andExpect(redirectedUrl("/login?sessionExpired=true"));
        mockMvc.perform(get("/desarrolladores")).andExpect(redirectedUrl("/login?sessionExpired=true"));
        // Un POST de otra web no pasa el CSRF del gestor
        mockMvc.perform(post("/api/v1/c/demo/sesion").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        // Y con sesión no hay controlador que responda
        mockMvc.perform(get("/admin/sitios").with(user("admin@test.com").roles("ADMIN"))).andExpect(status().isNotFound());
    }
}
