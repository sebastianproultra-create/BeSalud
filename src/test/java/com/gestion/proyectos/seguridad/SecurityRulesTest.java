package com.gestion.proyectos.seguridad;

import com.gestion.proyectos.controlador.LoginController;
import com.gestion.proyectos.repositorio.AdminRepositorio;
import com.gestion.proyectos.repositorio.DoctorRepositorio;
import com.gestion.proyectos.repositorio.PacienteRepositorio;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LoginController.class)
@Import(SecurityConfig.class)
class SecurityRulesTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PacienteRepositorio pacienteRepositorio;

    @MockBean
    private DoctorRepositorio doctorRepositorio;

    @MockBean
    private AdminRepositorio adminRepositorio;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

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
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void doctoresSinAutenticar_redirigeTLogin() throws Exception {
        mockMvc.perform(get("/doctores"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void citasSinAutenticar_redirigeTLogin() throws Exception {
        mockMvc.perform(get("/citas"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void pacientesSinAutenticar_redirigeTLogin() throws Exception {
        mockMvc.perform(get("/pacientes/landing"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
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
}
