package com.gestion.proyectos.controlador;

import com.gestion.proyectos.modelo.MensajeAyuda;
import com.gestion.proyectos.oauth2.OAuth2LoginSuccessHandler;
import com.gestion.proyectos.repositorio.AdminRepositorio;
import com.gestion.proyectos.repositorio.MensajeAyudaRepositorio;
import com.gestion.proyectos.repositorio.PersonaRepositorio;
import com.gestion.proyectos.seguridad.CustomUserDetailsService;
import com.gestion.proyectos.seguridad.JwtCookieService;
import com.gestion.proyectos.seguridad.JwtService;
import com.gestion.proyectos.seguridad.RateLimiter;
import com.gestion.proyectos.seguridad.SecurityConfig;
import com.gestion.proyectos.servicio.AyudaService;
import com.gestion.proyectos.servicio.CorreoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Centro de ayuda: página pública y formulario de contacto (con la validación real del servicio). */
@WebMvcTest(AyudaController.class)
@Import({SecurityConfig.class, AyudaService.class})
class AyudaControllerTest {

    @Autowired private MockMvc mockMvc;
    @SpyBean private AyudaService ayudaService;
    @MockBean private MensajeAyudaRepositorio mensajeRepo;
    @MockBean private CorreoService correoService;
    @MockBean private RateLimiter rateLimiter;
    @MockBean private CustomUserDetailsService customUserDetailsService;
    @MockBean private PersonaRepositorio personaRepositorio;
    @MockBean private AdminRepositorio adminRepositorio;
    @MockBean private JwtService jwtService;
    @MockBean private JwtCookieService jwtCookieService;
    @MockBean private OAuth2LoginSuccessHandler oauth2LoginSuccessHandler;

    @BeforeEach
    void preparar() {
        when(rateLimiter.permitir(anyString(), anyInt(), any())).thenReturn(true);
        when(mensajeRepo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void ayuda_esPublicaYMuestraPreguntasYFormulario() throws Exception {
        mockMvc.perform(get("/ayuda"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Me registré como médico y no puedo entrar")))
                .andExpect(content().string(containsString("/ayuda/contacto")))
                .andExpect(content().string(containsString("Instituciones y clínicas asociadas")));
    }

    @Test
    void ayuda_preseleccionaElTemaDelEnlace() throws Exception {
        mockMvc.perform(get("/ayuda").param("tema", "instituciones"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"instituciones\" selected")));
    }

    @Test
    void contacto_valido_guardaYAvisaAAdministradores() throws Exception {
        mockMvc.perform(post("/ayuda/contacto").with(csrf())
                        .param("nombre", "  Ana   Ruiz ")
                        .param("email", "Ana@Correo.com")
                        .param("tema", "registro")
                        .param("mensaje", "No me llega el correo de activación"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/ayuda?enviado#contacto"));

        verify(mensajeRepo).save(argThat((MensajeAyuda m) -> "Ana Ruiz".equals(m.getNombre())
                && "ana@correo.com".equals(m.getEmail()) && m.getFecha() != null && !m.isAtendido()));
        verify(ayudaService).avisarAdministradores(any());
    }

    @Test
    void contacto_invalido_noGuardaYDevuelveLoEscrito() throws Exception {
        mockMvc.perform(post("/ayuda/contacto").with(csrf())
                        .param("nombre", "Ana")
                        .param("email", "ana@correo.com")
                        .param("tema", "inventado")
                        .param("mensaje", "Necesito ayuda con algo"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/ayuda#contacto"))
                .andExpect(flash().attribute("errorContacto", "Selecciona el tema de tu consulta."))
                .andExpect(flash().attributeExists("contacto"));

        verify(mensajeRepo, never()).save(any());
    }

    @Test
    void contacto_mensajeCorto_daError() throws Exception {
        mockMvc.perform(post("/ayuda/contacto").with(csrf())
                        .param("nombre", "Ana").param("email", "ana@correo.com")
                        .param("tema", "otro").param("mensaje", "hola"))
                .andExpect(flash().attribute("errorContacto", "Cuéntanos tu duda en al menos 10 caracteres."));
        verify(mensajeRepo, never()).save(any());
    }

    @Test
    void contacto_campoTrampaLleno_noGuardaPeroResponde() throws Exception {
        mockMvc.perform(post("/ayuda/contacto").with(csrf())
                        .param("nombre", "Bot").param("email", "bot@spam.com")
                        .param("tema", "otro").param("mensaje", "Compra seguidores baratos")
                        .param("sitioWeb", "http://spam.example"))
                .andExpect(redirectedUrl("/ayuda?enviado#contacto"));
        verify(mensajeRepo, never()).save(any());
    }

    @Test
    void contacto_sinCsrf_esRechazado() throws Exception {
        mockMvc.perform(post("/ayuda/contacto").param("nombre", "Ana"))
                .andExpect(status().isForbidden());
        verify(mensajeRepo, never()).save(any());
    }
}
