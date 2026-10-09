package com.gestion.proyectos.controlador;

import com.gestion.proyectos.modelo.Doctor;
import com.gestion.proyectos.modelo.Paciente;
import com.gestion.proyectos.oauth2.OAuth2LoginSuccessHandler;
import com.gestion.proyectos.repositorio.AdminRepositorio;
import com.gestion.proyectos.repositorio.PersonaRepositorio;
import com.gestion.proyectos.seguridad.CustomUserDetailsService;
import com.gestion.proyectos.seguridad.JwtCookieService;
import com.gestion.proyectos.seguridad.JwtService;
import com.gestion.proyectos.seguridad.RateLimiter;
import com.gestion.proyectos.seguridad.SecurityConfig;
import com.gestion.proyectos.servicio.AdminService;
import com.gestion.proyectos.servicio.EstadisticasService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminController.class)
@Import(SecurityConfig.class)
class AdminControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private AdminService adminService;
    @MockBean private EstadisticasService estadisticasService;
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

    @Test
    void dashboard_cadaTablaPaginaPorSuCuentaYLosTotalesNoDependenDelFiltro() throws Exception {
        List<Doctor> doctores = Collections.nCopies(10, nuevoDoctor());
        List<Paciente> pacientes = Collections.nCopies(10, new Paciente("A", "B", "300", "1", "a@b.co", "x"));
        when(adminService.listarDoctoresPaginated(eq(0), eq(10), any(), eq("perez")))
                .thenReturn(new PageImpl<>(doctores, PageRequest.of(0, 10), 25));
        when(adminService.listarPacientesPaginated(eq(1), eq(10)))
                .thenReturn(new PageImpl<>(pacientes, PageRequest.of(1, 10), 35));
        when(adminService.contarDoctores()).thenReturn(77L);
        when(adminService.contarPacientes()).thenReturn(35L);
        when(adminService.listarAdmins()).thenReturn(List.of());

        mockMvc.perform(get("/admin").param("search", "perez").param("pagePacientes", "1")
                        .with(user("admin@test.com").roles("ADMIN")))
                .andExpect(status().isOk())
                // total real, no el del resultado filtrado (25)
                .andExpect(content().string(containsString(">77<")))
                // paginar doctores conserva la página de pacientes y el filtro
                .andExpect(content().string(containsString("/admin?size=10&amp;pagePacientes=1&amp;search=perez&amp;pageDoctores=1")))
                // paginar pacientes conserva la página de doctores y el filtro
                .andExpect(content().string(containsString("/admin?size=10&amp;pageDoctores=0&amp;search=perez&amp;pagePacientes=2")));
    }

    private static Doctor nuevoDoctor() {
        Doctor d = new Doctor();
        d.setNombre("Ana");
        d.setApellido("Perez");
        d.setEmail("ana@test.com");
        d.setEspecialidad("Cardiología");
        d.setEstado("ACTIVO");
        d.setId("d1");
        return d;
    }
}
