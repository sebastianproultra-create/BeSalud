package com.gestion.proyectos.seguridad;

import com.gestion.proyectos.oauth2.OAuth2LoginSuccessHandler;
import com.gestion.proyectos.servicio.DoctorService;
import com.gestion.proyectos.controlador.LoginController;
import com.gestion.proyectos.repositorio.AdminRepositorio;
import com.gestion.proyectos.repositorio.PersonaRepositorio;
import com.gestion.proyectos.seguridad.JwtCookieService;
import com.gestion.proyectos.seguridad.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LoginController.class)
@Import(SecurityConfig.class)
class SecurityRulesTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RateLimiter rateLimiter;

    @BeforeEach
    void permitirPeticiones() {
        when(rateLimiter.permitir(anyString(), anyInt(), any())).thenReturn(true);
    }

    @MockBean
    private PersonaRepositorio personaRepositorio;

    @MockBean
    private AdminRepositorio adminRepositorio;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @MockBean
    private com.gestion.proyectos.servicio.RegistroService registroService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private JwtCookieService jwtCookieService;

    @MockBean
    private DoctorService doctorService;

    @MockBean
    private OAuth2LoginSuccessHandler oauth2LoginSuccessHandler;

    @MockBean
    private org.springframework.security.authentication.AuthenticationManager authenticationManager;

    // --- Rutas públicas ---

    @Test
    void loginSinAutenticar_esAccesible() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk());
    }

    @Test
    void registerSinAutenticar_esAccesible() throws Exception {
        mockMvc.perform(get("/register"))
                .andExpect(status().isOk());
    }

    // --- Rutas protegidas sin autenticación → redirige a login ---

    @Test
    void adminSinAutenticar_redirigeTLogin() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(result -> {
                    String url = result.getResponse().getRedirectedUrl();
                    org.junit.jupiter.api.Assertions.assertNotNull(url, "Debe redirigir");
                    org.junit.jupiter.api.Assertions.assertTrue(url.contains("/login"), "Debe redirigir a login, fue: " + url);
                });
    }

    @Test
    void doctoresSinAutenticar_redirigeTLogin() throws Exception {
        mockMvc.perform(get("/doctores"))
                .andExpect(status().is3xxRedirection())
                .andExpect(result -> {
                    String url = result.getResponse().getRedirectedUrl();
                    org.junit.jupiter.api.Assertions.assertNotNull(url, "Debe redirigir");
                    org.junit.jupiter.api.Assertions.assertTrue(url.contains("/login"), "Debe redirigir a login, fue: " + url);
                });
    }

    @Test
    void citasSinAutenticar_redirigeTLogin() throws Exception {
        mockMvc.perform(get("/citas"))
                .andExpect(status().is3xxRedirection())
                .andExpect(result -> {
                    String url = result.getResponse().getRedirectedUrl();
                    org.junit.jupiter.api.Assertions.assertNotNull(url, "Debe redirigir");
                    org.junit.jupiter.api.Assertions.assertTrue(url.contains("/login"), "Debe redirigir a login, fue: " + url);
                });
    }

    @Test
    void pacientesSinAutenticar_redirigeTLogin() throws Exception {
        mockMvc.perform(get("/pacientes/landing"))
                .andExpect(status().is3xxRedirection())
                .andExpect(result -> {
                    String url = result.getResponse().getRedirectedUrl();
                    org.junit.jupiter.api.Assertions.assertNotNull(url, "Debe redirigir");
                    org.junit.jupiter.api.Assertions.assertTrue(url.contains("/login"), "Debe redirigir a login, fue: " + url);
                });
    }

    // --- Control de roles: PACIENTE y DOCTOR no pueden entrar a /admin ---

    @Test
    @WithMockUser(roles = "PACIENTE")
    void adminConRolPaciente_esForbidden() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void adminConRolDoctor_esForbidden() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().isForbidden());
    }

    // --- ADMIN pasa el filtro de seguridad (no 302 ni 403) ---

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminConRolAdmin_pasaFiltroSeguridad() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertNotEquals(302, status, "No debe redirigir al login");
                    assertNotEquals(403, status, "No debe ser prohibido para ADMIN");
                });
    }

    // --- Usuarios autenticados sin rol ADMIN pueden acceder a sus rutas ---

    @Test
    @WithMockUser(roles = "DOCTOR")
    void doctoresConRolDoctor_pasaFiltroSeguridad() throws Exception {
        mockMvc.perform(get("/doctores"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertNotEquals(302, status, "No debe redirigir al login");
                    assertNotEquals(403, status, "No debe ser prohibido para DOCTOR");
                });
    }

    @Test
    @WithMockUser(roles = "PACIENTE")
    void pacientesConRolPaciente_pasaFiltroSeguridad() throws Exception {
        mockMvc.perform(get("/pacientes/landing"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertNotEquals(302, status, "No debe redirigir al login");
                    assertNotEquals(403, status, "No debe ser prohibido para PACIENTE");
                });
    }

    // --- Rutas de gestión exclusivas por rol ---

    @Test
    @WithMockUser(roles = "PACIENTE")
    void listaPacientesConRolPaciente_esForbidden() throws Exception {
        mockMvc.perform(get("/pacientes"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void guardarPacienteConRolDoctor_esForbidden() throws Exception {
        mockMvc.perform(post("/pacientes/guardar").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "PACIENTE")
    void eliminarDoctorConRolPaciente_esForbidden() throws Exception {
        mockMvc.perform(post("/doctores/123/eliminar").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void guardarDoctorConRolDoctor_esForbidden() throws Exception {
        mockMvc.perform(post("/doctores/guardar").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void triageConRolDoctor_esForbidden() throws Exception {
        mockMvc.perform(get("/triage"))
                .andExpect(status().isForbidden());
    }

    @Test
    void triageSinAutenticar_redirigeALogin() throws Exception {
        mockMvc.perform(get("/triage"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(roles = "PACIENTE")
    void doctoresConRolPaciente_esForbidden() throws Exception {
        mockMvc.perform(get("/doctores"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "PACIENTE")
    void citaAdminConRolPaciente_esForbidden() throws Exception {
        mockMvc.perform(post("/citas/guardar").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void landingPacienteConRolDoctor_esForbidden() throws Exception {
        mockMvc.perform(get("/pacientes/landing"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void agendarComoPacienteConRolAdmin_esForbidden() throws Exception {
        mockMvc.perform(post("/citas/guardar-paciente").with(csrf()))
                .andExpect(status().isForbidden());
    }
}
