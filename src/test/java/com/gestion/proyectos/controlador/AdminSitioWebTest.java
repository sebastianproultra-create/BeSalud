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
import com.gestion.proyectos.servicio.AyudaService;
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
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminController.class, properties = "app.api-consultorios.habilitada=true")
@Import({SecurityConfig.class, com.gestion.proyectos.api.ApiSecurityConfig.class})
/** Panel de admin con la API para webs de consultorios encendida. */
class AdminSitioWebTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private AdminService adminService;
    @MockBean private EstadisticasService estadisticasService;
    @MockBean private AyudaService ayudaService;
    @MockBean private com.gestion.proyectos.servicio.ConsultorioService consultorioService;
    @MockBean private com.gestion.proyectos.servicio.DevolucionService devolucionService;
    @MockBean private com.gestion.proyectos.servicio.SitioWebService sitioWebService; // IntegracionWebAdvice
    @MockBean private RateLimiter rateLimiter;
    @MockBean private CustomUserDetailsService customUserDetailsService;
    @MockBean private PersonaRepositorio personaRepositorio;
    @MockBean private AdminRepositorio adminRepositorio;
    @MockBean private JwtService jwtService;
    @MockBean private JwtCookieService jwtCookieService;
    @MockBean private OAuth2LoginSuccessHandler oauth2LoginSuccessHandler;
    @MockBean private com.gestion.proyectos.seguridad.ConsultorioCorsSource consultorioCorsSource;

    @BeforeEach
    void preparar() {
        when(rateLimiter.permitir(anyString(), anyInt(), any())).thenReturn(true);
    }

    @Test
    void activar_conSitioWeb_activaYVinculaElConsultorio() throws Exception {
        Doctor doctor = new Doctor();
        com.gestion.proyectos.modelo.Consultorio c = new com.gestion.proyectos.modelo.Consultorio();
        c.setId("dr-ana-ruiz");
        when(adminService.obtenerDoctor("d1")).thenReturn(Optional.of(doctor));
        when(consultorioService.vincularSitio(doctor, "https://anaruiz.com")).thenReturn(Optional.of(c));

        mockMvc.perform(post("/admin/doctores/d1/activar").param("sitioWeb", "https://anaruiz.com")
                        .with(csrf()).with(user("admin@test.com").roles("ADMIN")))
                .andExpect(redirectedUrl("/admin?sitioVinculado=dr-ana-ruiz"));
        org.mockito.Mockito.verify(adminService).activarDoctor("d1");
    }

    @Test
    void activar_sinSitioWeb_soloActiva() throws Exception {
        mockMvc.perform(post("/admin/doctores/d1/activar")
                        .with(csrf()).with(user("admin@test.com").roles("ADMIN")))
                .andExpect(redirectedUrl("/admin"));
        org.mockito.Mockito.verify(adminService).activarDoctor("d1");
        org.mockito.Mockito.verify(consultorioService, org.mockito.Mockito.never()).vincularSitio(any(), any());
    }

    @Test
    void activar_conUrlInvalida_noActivaYAvisa() throws Exception {
        mockMvc.perform(post("/admin/doctores/d1/activar").param("sitioWeb", "no es una url")
                        .with(csrf()).with(user("admin@test.com").roles("ADMIN")))
                .andExpect(redirectedUrl("/admin?error=urlInvalida"));
        org.mockito.Mockito.verify(adminService, org.mockito.Mockito.never()).activarDoctor(any());
    }

    @Test
    void vincularSitio_soloAdmin() throws Exception {
        mockMvc.perform(post("/admin/doctores/d1/sitio").param("sitioWeb", "https://x.com")
                        .with(csrf()).with(user("doc@test.com").roles("DOCTOR")))
                .andExpect(status().isForbidden());
        org.mockito.Mockito.verify(consultorioService, org.mockito.Mockito.never()).vincularSitio(any(), any());
    }

    @Test
    void dashboard_muestraElSitioYLosDatosParaConfigJs() throws Exception {
        Doctor doctor = new Doctor();
        doctor.setId("d1");
        doctor.setNombre("Ana");
        doctor.setApellido("Ruiz");
        doctor.setEstado("ACTIVO");
        when(adminService.listarDoctoresPaginated(anyInt(), anyInt(), any(), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(doctor)));
        when(adminService.listarPacientesPaginated(anyInt(), anyInt()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.<com.gestion.proyectos.modelo.Paciente>of()));
        when(adminService.listarAdmins()).thenReturn(List.of());
        com.gestion.proyectos.modelo.Consultorio c = new com.gestion.proyectos.modelo.Consultorio();
        c.setId("dr-ana-ruiz");
        c.setDominios(List.of("https://anaruiz.com"));
        c.setActivo(true);
        when(consultorioService.porDoctor(any())).thenReturn(java.util.Map.of("d1", c));

        mockMvc.perform(get("/admin").with(user("admin@test.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(">Sitio web<")))
                .andExpect(content().string(containsString("https://anaruiz.com")))
                .andExpect(content().string(containsString("<code>dr-ana-ruiz</code>")));
    }
}
