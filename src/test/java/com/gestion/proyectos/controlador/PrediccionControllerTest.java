package com.gestion.proyectos.controlador;

import com.gestion.proyectos.modelo.Doctor;
import com.gestion.proyectos.oauth2.OAuth2LoginSuccessHandler;
import com.gestion.proyectos.repositorio.AdminRepositorio;
import com.gestion.proyectos.repositorio.PersonaRepositorio;
import com.gestion.proyectos.seguridad.CustomUserDetailsService;
import com.gestion.proyectos.seguridad.JwtCookieService;
import com.gestion.proyectos.seguridad.JwtService;
import com.gestion.proyectos.seguridad.RateLimiter;
import com.gestion.proyectos.seguridad.SecurityConfig;
import com.gestion.proyectos.servicio.DoctorService;
import com.gestion.proyectos.servicio.PrediccionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PrediccionController.class)
@Import(SecurityConfig.class)
class PrediccionControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private PrediccionService prediccionService;
    @MockBean private DoctorService doctorService;
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
    }

    private Doctor doctor() {
        Doctor d = new Doctor();
        d.setId("d1");
        d.setEspecialidad("Pediatría");
        return d;
    }

    @Test
    void mia_usaLaEspecialidadYElIdDelMedicoConSesion() throws Exception {
        when(doctorService.buscarPorEmail("doc@test.com")).thenReturn(Optional.of(doctor()));
        when(prediccionService.obtenerProyeccion("Pediatría", "d1", 14)).thenReturn("{\"predicciones\":[]}");

        mockMvc.perform(get("/api/predicciones/mia").param("dias", "14").with(user("doc@test.com").roles("DOCTOR")))
                .andExpect(status().isOk())
                .andExpect(content().string("{\"predicciones\":[]}"));
    }

    @Test
    void mia_rechazaAQuienNoEsMedico() throws Exception {
        when(doctorService.buscarPorEmail("pac@test.com")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/predicciones/mia").with(user("pac@test.com").roles("PACIENTE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void mia_pasaEl404DeLaApiCuandoNoHayHistorial() throws Exception {
        when(doctorService.buscarPorEmail("doc@test.com")).thenReturn(Optional.of(doctor()));
        when(prediccionService.obtenerProyeccion(any(), any(), anyInt())).thenThrow(HttpClientErrorException.create(
                HttpStatus.NOT_FOUND, "Not Found", null,
                "{\"detail\":\"Médico sin historial de citas.\"}".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));

        mockMvc.perform(get("/api/predicciones/mia").with(user("doc@test.com").roles("DOCTOR")))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("sin historial")));
    }

    @Test
    void especialidad_devuelve500SiLaApiNoResponde() throws Exception {
        when(prediccionService.obtenerProyeccion(any(), any(), anyInt()))
                .thenThrow(new ResourceAccessException("Connection refused"));

        mockMvc.perform(get("/api/predicciones/Pediatría").with(user("doc@test.com").roles("DOCTOR")))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(containsString("modelo predictivo")));
    }
}
