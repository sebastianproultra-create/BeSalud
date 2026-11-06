package com.gestion.proyectos.controlador;

import com.gestion.proyectos.modelo.Consultorio;
import com.gestion.proyectos.modelo.Doctor;
import com.gestion.proyectos.modelo.SitioWeb;
import com.gestion.proyectos.oauth2.OAuth2LoginSuccessHandler;
import com.gestion.proyectos.repositorio.AdminRepositorio;
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
import com.gestion.proyectos.servicio.SitioWebService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Página web alojada del médico: sitio público, editor del admin con vista previa y tarjeta del panel. */
@WebMvcTest(controllers = {SitioPublicoController.class, AdminSitiosController.class, DoctorController.class},
        properties = {"app.api-consultorios.habilitada=true", "app.url=http://localhost:8080"})
@Import({SecurityConfig.class, com.gestion.proyectos.api.ApiSecurityConfig.class})
class SitioWebTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private SitioWebService sitioService;
    @MockBean private ConsultorioService consultorioService;
    @MockBean private com.gestion.proyectos.repositorio.ConsultorioRepositorio consultorioRepo;
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
    private SitioWeb sitio;

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
        doctor.setEspecialidad("Pediatría");
        doctor.setEmail("doc@test.com");
        doctor.setEstado("ACTIVO");
        when(doctorService.buscarPorEmail("doc@test.com")).thenReturn(Optional.of(doctor));
        when(personaRepositorio.findDoctorByEmail("doc@test.com")).thenReturn(Optional.of(doctor));
        when(personaRepositorio.findById("d1")).thenReturn(Optional.of(doctor));
        when(doctorService.dailySlotCounts(any())).thenReturn(Map.of());

        sitio = new SitioWeb();
        sitio.setSlug("dr-leo-ruiz");
        sitio.setDoctorId("d1");
        sitio.setConsultorioId("dr-leo-ruiz");
        sitio.setPlantilla("infantil");
        sitio.setColor("coral");
        sitio.setTitulo("Consultorio Pequeños Gigantes");
        sitio.setFrase("Cuidamos a tus hijos como a los nuestros");
        sitio.setPresentacion("Pediatra con 10 años de experiencia.");
        sitio.setServicios(new ArrayList<>(List.of("Control de niño sano", "Vacunación")));
        sitio.setWhatsapp("3001234567");
        when(sitioService.pagina(any(), any())).thenAnswer(i ->
                new SitioWebService.Pagina(i.getArgument(0), i.getArgument(1), "https://wa.me/573001234567", null));
    }

    // ── Sitio público ───────────────────────────────────────────────────────

    @Test
    void sitioPublicado_esPublicoSinLaBarraDeBesaludYConElWidget() throws Exception {
        sitio.setPublicado(true);
        when(sitioService.publica("dr-leo-ruiz")).thenReturn(Optional.of(
                new SitioWebService.Pagina(sitio, doctor, "https://wa.me/573001234567", null)));

        mockMvc.perform(get("/sitio/dr-leo-ruiz"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Consultorio Pequeños Gigantes")))
                .andExpect(content().string(containsString("sitio sitio--infantil sitio--color-coral")))
                .andExpect(content().string(containsString("Control de niño sano")))
                .andExpect(content().string(containsString("data-consultorio=\"dr-leo-ruiz\"")))
                .andExpect(content().string(containsString("data-doctor=\"d1\"")))
                .andExpect(content().string(containsString("/widget/besalud-widget.js")))
                .andExpect(content().string(containsString("data-besalud=\"mis-citas\"")))
                .andExpect(content().string(not(containsString("bs-navbar"))))
                .andExpect(content().string(not(containsString("Vista previa"))));
    }

    @Test
    void sitioInexistenteONoPublicado_da404() throws Exception {
        when(sitioService.publica("nadie")).thenReturn(Optional.empty());
        mockMvc.perform(get("/sitio/nadie")).andExpect(status().isNotFound());
    }

    // ── Editor (solo admin) ─────────────────────────────────────────────────

    private static final org.springframework.test.web.servlet.request.RequestPostProcessor ADMIN =
            user("admin@test.com").roles("ADMIN");

    @Test
    void editor_soloParaElAdmin_elMedicoNoPuedeEditarSuPagina() throws Exception {
        mockMvc.perform(get("/admin/sitios")).andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/admin/sitios/d1").with(user("doc@test.com").roles("DOCTOR"))).andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/sitios/d1").with(csrf()).with(user("doc@test.com").roles("DOCTOR"))
                .param("titulo", "Hackeado")).andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/sitios/d1/vista-previa").with(user("p@test.com").roles("PACIENTE"))).andExpect(status().isForbidden());
        verify(sitioService, never()).guardar(any(), any());
    }

    @Test
    void lista_muestraElEstadoDeLaPaginaDeCadaMedico() throws Exception {
        Doctor sinPagina = new Doctor();
        sinPagina.setId("d2");
        sinPagina.setNombre("Ana");
        sinPagina.setApellido("Mora");
        sinPagina.setEstado("INACTIVO");
        sitio.setPublicado(true);
        when(personaRepositorio.findAllDoctores()).thenReturn(List.of(sinPagina, doctor));
        when(sitioService.porDoctor()).thenReturn(Map.of("d1", sitio));

        mockMvc.perform(get("/admin/sitios").with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Dr. Leo Ruiz")))
                .andExpect(content().string(containsString(">Publicada<")))
                .andExpect(content().string(containsString("http://localhost:8080/sitio/dr-leo-ruiz")))
                .andExpect(content().string(containsString("href=\"/admin/sitios/d2\"")))
                .andExpect(content().string(containsString("Crear página")))
                .andExpect(content().string(containsString("Sin página")));
    }

    @Test
    void editor_muestraPlantillasColoresYVistaPreviaPorSrcdoc() throws Exception {
        when(sitioService.borrador(doctor)).thenReturn(sitio);

        mockMvc.perform(get("/admin/sitios/d1").with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(content().string(containsString("Página de Dr. Leo Ruiz")))
                .andExpect(content().string(containsString("name=\"plantilla\" value=\"familiar\"")))
                .andExpect(content().string(containsString("data-color-defecto=\"terracota\"")))
                .andExpect(content().string(containsString("name=\"color\" value=\"coral\" checked")))
                .andExpect(content().string(containsString("value=\"Consultorio Pequeños Gigantes\"")))
                .andExpect(content().string(containsString("data-base=\"/admin/sitios/d1/vista-previa\"")))
                .andExpect(content().string(containsString("action=\"/admin/sitios/d1\"")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
    }

    @Test
    void guardar_conErrores_vuelveAlEditorConLoEscrito() throws Exception {
        when(sitioService.validar(any())).thenReturn(Map.of("titulo", "Escribe el nombre de tu consultorio."));
        when(sitioService.vistaPrevia(eq(doctor), any())).thenReturn(sitio);

        mockMvc.perform(post("/admin/sitios/d1").with(csrf()).with(ADMIN).param("titulo", "A"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Escribe el nombre de tu consultorio.")));
        verify(sitioService, never()).guardar(any(), any());
    }

    @Test
    void guardarYPublicar_publicaConElOrigenDeBesalud() throws Exception {
        when(sitioService.validar(any())).thenReturn(Map.of());

        mockMvc.perform(post("/admin/sitios/d1").with(csrf()).with(ADMIN)
                        .param("titulo", "Consultorio Sol").param("plantilla", "familiar").param("accion", "publicar"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/sitios/d1"))
                .andExpect(flash().attribute("exito", "Página publicada."));

        verify(sitioService).guardar(eq(doctor), argThat(f -> "familiar".equals(f.plantilla()) && "Consultorio Sol".equals(f.titulo())));
        verify(sitioService).publicar(doctor, "http://localhost");
    }

    @Test
    void publicar_medicoInactivo_guardaPeroAvisa() throws Exception {
        when(sitioService.validar(any())).thenReturn(Map.of());
        when(sitioService.publicar(any(), any())).thenThrow(new IllegalStateException("Tu cuenta aún no está activa."));

        mockMvc.perform(post("/admin/sitios/d1").with(csrf()).with(ADMIN)
                        .param("titulo", "Consultorio Sol").param("accion", "publicar"))
                .andExpect(flash().attribute("error", "Se guardó, pero no se publicó: Tu cuenta aún no está activa."));
    }

    @Test
    void medicoInexistente_404() throws Exception {
        when(personaRepositorio.findById("nadie")).thenReturn(Optional.empty());
        mockMvc.perform(get("/admin/sitios/nadie").with(ADMIN)).andExpect(status().isNotFound());
    }

    @Test
    void vistaPrevia_noCargaElWidget() throws Exception {
        when(sitioService.vistaPrevia(eq(doctor), any())).thenReturn(sitio);

        mockMvc.perform(get("/admin/sitios/d1/vista-previa").with(ADMIN).param("titulo", "Consultorio Pequeños Gigantes"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Vista previa")))
                .andExpect(content().string(containsString("data-vista-previa=\"true\"")))
                .andExpect(content().string(not(containsString("besalud-widget.js"))));
    }

    // ── Tarjeta del panel ───────────────────────────────────────────────────

    @Test
    void tarjeta_sinPagina_invitaASolicitarlaYDejaElCodigoComoOpcionSecundaria() throws Exception {
        when(sitioService.deDoctor("d1")).thenReturn(Optional.empty());
        when(consultorioService.integracionDe("d1")).thenReturn(Optional.empty());

        mockMvc.perform(get("/doctores").with(user("doc@test.com").roles("DOCTOR")))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(content().string(containsString("Solicitar mi página")))
                .andExpect(content().string(containsString("/ayuda?tema=pagina-web#contacto")))
                .andExpect(content().string(not(containsString("/doctores/sitio"))))
                .andExpect(content().string(containsString("¿Ya tienes tu propia web?")))
                .andExpect(content().string(not(containsString("id=\"codigoMiWeb\""))));
    }

    @Test
    void tarjeta_conPaginaPublicada_muestraSuEnlaceYNoConfundeElOrigenDeBesaludConUnaWebPropia() throws Exception {
        sitio.setId("s1");
        sitio.setPublicado(true);
        when(sitioService.deDoctor("d1")).thenReturn(Optional.of(sitio));
        Consultorio c = new Consultorio();
        c.setId("dr-leo-ruiz");
        when(consultorioService.integracionDe("d1")).thenReturn(Optional.of(
                new ConsultorioService.Integracion(c, List.of("http://localhost:8080"), 4)));

        mockMvc.perform(get("/doctores").with(user("doc@test.com").roles("DOCTOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(">Publicada<")))
                .andExpect(content().string(containsString("http://localhost:8080/sitio/dr-leo-ruiz")))
                .andExpect(content().string(containsString("Pedir cambios")))
                .andExpect(content().string(not(containsString("Editar mi página"))))
                .andExpect(content().string(containsString("<strong>4</strong>")))
                .andExpect(content().string(not(containsString("id=\"codigoMiWeb\""))));
    }
}
