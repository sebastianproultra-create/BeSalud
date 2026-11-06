package com.gestion.proyectos.controlador;

import com.gestion.proyectos.modelo.Consultorio;
import com.gestion.proyectos.modelo.Doctor;
import com.gestion.proyectos.oauth2.OAuth2LoginSuccessHandler;
import com.gestion.proyectos.repositorio.AdminRepositorio;
import com.gestion.proyectos.repositorio.ConsultorioRepositorio;
import com.gestion.proyectos.repositorio.PersonaRepositorio;
import com.gestion.proyectos.seguridad.ConsultorioCorsSource;
import com.gestion.proyectos.seguridad.CustomUserDetailsService;
import com.gestion.proyectos.seguridad.JwtCookieService;
import com.gestion.proyectos.seguridad.JwtService;
import com.gestion.proyectos.seguridad.RateLimiter;
import com.gestion.proyectos.seguridad.SecurityConfig;
import com.gestion.proyectos.servicio.ConsultorioService;
import com.gestion.proyectos.servicio.DoctorService;
import com.gestion.proyectos.servicio.PanelDoctorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Con la API de consultorios encendida: portal /desarrolladores y la tarjeta "Mi página web" del médico. */
@WebMvcTest(controllers = {DesarrolladoresController.class, DoctorController.class},
        properties = {"app.api-consultorios.habilitada=true", "app.api-consultorios.demo=demo",
                "app.url=http://localhost:8080"})
@Import({SecurityConfig.class, com.gestion.proyectos.api.ApiSecurityConfig.class})
class PortalDesarrolladoresTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private ConsultorioRepositorio consultorioRepo;
    @MockBean private ConsultorioService consultorioService;
    @MockBean private com.gestion.proyectos.servicio.SitioWebService sitioWebService;
    @MockBean private DoctorService doctorService;
    @MockBean private PanelDoctorService panelDoctorService;
    @MockBean private com.gestion.proyectos.servicio.PerfilPublicoService perfilPublicoService;
    @MockBean private com.gestion.proyectos.servicio.HistorialClinicoService historialClinicoService;
    @MockBean private com.gestion.proyectos.servicio.DocumentoClinicoService documentoClinicoService;
    @MockBean private RateLimiter rateLimiter;
    @MockBean private CustomUserDetailsService customUserDetailsService;
    @MockBean private PersonaRepositorio personaRepositorio;
    @MockBean private AdminRepositorio adminRepositorio;
    @MockBean private JwtService jwtService;
    @MockBean private JwtCookieService jwtCookieService;
    @MockBean private OAuth2LoginSuccessHandler oauth2LoginSuccessHandler;
    @MockBean private ConsultorioCorsSource consultorioCorsSource;

    private Doctor doctor;

    @BeforeEach
    void preparar() {
        when(rateLimiter.permitir(anyString(), anyInt(), any())).thenReturn(true);
        Map<String, Long> conteos = new LinkedHashMap<>();
        PanelDoctorService.FILTROS.forEach(f -> conteos.put(f, 0L));
        conteos.put("TODAS", 0L);
        when(panelDoctorService.citas(any(), any(), any(), anyInt(), anyInt(), any()))
                .thenReturn(new PanelDoctorService.PanelCitas(new PageImpl<>(List.of()), conteos, Map.of(), Map.of()));
        when(panelDoctorService.pacientes(any(), any(), anyInt(), anyInt()))
                .thenReturn(new PanelDoctorService.PanelPacientes(new PageImpl<>(List.of()), 0));

        doctor = new Doctor();
        doctor.setId("d1");
        doctor.setNombre("Leo");
        doctor.setApellido("Ruiz");
        doctor.setEmail("doc@test.com");
        when(doctorService.buscarPorEmail("doc@test.com")).thenReturn(Optional.of(doctor));
        when(personaRepositorio.findDoctorByEmail("doc@test.com")).thenReturn(Optional.of(doctor));
        when(doctorService.dailySlotCounts(any())).thenReturn(Map.of());
    }

    // ── Portal ──────────────────────────────────────────────────────────────

    @Test
    void portal_esPublicoYMuestraReferenciaSinDatosReales() throws Exception {
        mockMvc.perform(get("/desarrolladores"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Referencia de la API")))
                .andExpect(content().string(containsString("http://localhost:8080</span>/api/v1/c/{consultorio}")))
                .andExpect(content().string(containsString("data-consultorio=\"mi-consultorio\"")))
                .andExpect(content().string(containsString("tema=integracion")))
                // Sin consultorio de demostración no se carga el widget
                .andExpect(content().string(not(containsString("besalud-widget.js\" data-consultorio"))))
                .andExpect(content().string(not(containsString("id=\"demo\""))));
    }

    @Test
    void portal_conConsultorioDemo_cargaElWidgetEnVivo() throws Exception {
        Consultorio demo = new Consultorio();
        demo.setId("demo");
        demo.setDominios(List.of("http://localhost:8080"));
        when(consultorioRepo.findByIdAndActivoTrue("demo")).thenReturn(Optional.of(demo));

        mockMvc.perform(get("/desarrolladores"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"demo\"")))
                .andExpect(content().string(containsString("data-consultorio=\"demo\"")))
                .andExpect(content().string(containsString("data-besalud=\"agendar\"")));
    }

    @Test
    void portal_demoQueNoAutorizaEsteDominio_noSeMuestra() throws Exception {
        Consultorio demo = new Consultorio();
        demo.setId("demo");
        demo.setDominios(List.of("https://otro.com"));
        when(consultorioRepo.findByIdAndActivoTrue("demo")).thenReturn(Optional.of(demo));

        mockMvc.perform(get("/desarrolladores"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("id=\"demo\""))));
    }

    @Test
    void pieDePagina_enlazaDiscretamenteAlPortal() throws Exception {
        mockMvc.perform(get("/doctores").with(user("doc@test.com").roles("DOCTOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/desarrolladores\">Desarrolladores</a>")));
    }

    // ── Mi página web (panel del médico) ────────────────────────────────────

    @Test
    void miPaginaWeb_sinSitio_invitaASolicitarActivacion() throws Exception {
        when(consultorioService.integracionDe("d1")).thenReturn(Optional.empty());

        mockMvc.perform(get("/doctores").with(user("doc@test.com").roles("DOCTOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"mi-web\"")))
                .andExpect(content().string(containsString("Solicitar activación")))
                .andExpect(content().string(containsString("/ayuda?tema=integracion#contacto")))
                .andExpect(content().string(not(containsString("id=\"codigoMiWeb\""))));
    }

    @Test
    void miPaginaWeb_conSitio_muestraSuCodigoYCuantosPacientesLlegaron() throws Exception {
        Consultorio c = new Consultorio();
        c.setId("pequenos-pasos");
        c.setDominios(List.of("https://pequenospasos.com"));
        c.setDoctorIds(List.of("d1"));
        when(consultorioService.integracionDe("d1")).thenReturn(Optional.of(
                new ConsultorioService.Integracion(c, List.of("https://pequenospasos.com"), 23)));

        mockMvc.perform(get("/doctores").with(user("doc@test.com").roles("DOCTOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(">Activa<")))
                .andExpect(content().string(containsString("id=\"codigoMiWeb\"")))
                .andExpect(content().string(containsString("data-consultorio=\"<span>pequenos-pasos</span>\"")))
                .andExpect(content().string(containsString("data-doctor=\"<span>d1</span>\"")))
                .andExpect(content().string(containsString("https://pequenospasos.com")))
                .andExpect(content().string(containsString("<strong>23</strong>")))
                .andExpect(content().string(containsString("/js/copiar-codigo.js")));
    }
}
