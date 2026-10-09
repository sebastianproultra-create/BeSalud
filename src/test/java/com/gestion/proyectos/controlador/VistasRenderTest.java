package com.gestion.proyectos.controlador;

import com.gestion.proyectos.modelo.Cita;
import com.gestion.proyectos.modelo.Doctor;
import com.gestion.proyectos.modelo.EstadoCita;
import com.gestion.proyectos.modelo.Paciente;
import com.gestion.proyectos.oauth2.OAuth2LoginSuccessHandler;
import com.gestion.proyectos.repositorio.AdminRepositorio;
import com.gestion.proyectos.repositorio.PersonaRepositorio;
import com.gestion.proyectos.seguridad.CustomUserDetailsService;
import com.gestion.proyectos.seguridad.JwtCookieService;
import com.gestion.proyectos.seguridad.JwtService;
import com.gestion.proyectos.seguridad.RateLimiter;
import com.gestion.proyectos.seguridad.SecurityConfig;
import com.gestion.proyectos.servicio.CitaService;
import com.gestion.proyectos.servicio.DoctorService;
import com.gestion.proyectos.servicio.PacienteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Comprueba que las plantillas con mensajes de error y botones nuevos renderizan sin fallar. */
@WebMvcTest({DoctorController.class, PacienteController.class, CitaController.class, LoginController.class,
        RolSelectionController.class, TriageController.class})
@Import(SecurityConfig.class)
class VistasRenderTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private DoctorService doctorService;
    @MockBean private PacienteService pacienteService;
    @MockBean private CitaService citaService;
    @MockBean private RateLimiter rateLimiter;
    @MockBean private com.gestion.proyectos.servicio.RegistroService registroService;
    @MockBean private com.gestion.proyectos.servicio.TriageService triageService;
    @MockBean private org.springframework.security.authentication.AuthenticationManager authenticationManager;
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
    void dashboardDoctor_errorDeHorarioMuestraDiaEnEspanol() throws Exception {
        Doctor d = new Doctor();
        d.setId("d1");
        d.setNombre("Ana");
        d.setApellido("Ruiz");
        when(doctorService.buscarPorEmail("doc@test.com")).thenReturn(Optional.of(d));
        when(doctorService.dailySlotCounts(any())).thenReturn(Map.of());

        mockMvc.perform(get("/doctores").param("error", "missing_time_MONDAY")
                        .with(user("doc@test.com").roles("DOCTOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Falta la hora para el día Lunes")));
        mockMvc.perform(get("/doctores").param("error", "duracion_excesiva")
                        .with(user("doc@test.com").roles("DOCTOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("480 minutos")));
    }

    @Test
    void pacientesAdmin_muestraErrorYConservaDatos() throws Exception {
        when(pacienteService.validarYGuardar(any())).thenReturn("Ya existe un paciente registrado con ese correo");
        when(pacienteService.listarPaginado(anyInt(), anyInt())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(post("/pacientes/guardar").with(csrf()).with(user("a@test.com").roles("ADMIN"))
                        .param("nombre", "Luis").param("apellido", "Gil").param("email", "luis@test.com"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ya existe un paciente registrado con ese correo")))
                .andExpect(content().string(containsString("value=\"Luis\"")));
    }

    @Test
    void pacientesAdmin_ignoraIdYRolEnviadosAMano() throws Exception {
        when(pacienteService.validarYGuardar(any())).thenReturn(null);

        mockMvc.perform(post("/pacientes/guardar").with(csrf()).with(user("a@test.com").roles("ADMIN"))
                        .param("nombre", "Luis").param("apellido", "Gil").param("email", "luis@test.com")
                        .param("id", "id-de-otro-usuario").param("role", "ADMIN"))
                .andExpect(status().is3xxRedirection());

        ArgumentCaptor<Paciente> guardado = ArgumentCaptor.forClass(Paciente.class);
        verify(pacienteService).validarYGuardar(guardado.capture());
        assertThat(guardado.getValue().getId()).isNull();
        assertThat(guardado.getValue().getRole()).isEqualTo("PACIENTE");
        assertThat(guardado.getValue().getNombre()).isEqualTo("Luis");
    }

    @Test
    void landingPaciente_tieneCancelarYMuestraErrores() throws Exception {
        Paciente p = new Paciente();
        p.setId("p1");
        Cita c = new Cita();
        c.setId("c1");
        c.setDoctorId("d1");
        c.setFecha(LocalDate.now().plusDays(1));
        c.setHora(LocalTime.of(9, 0));
        c.setEstado(EstadoCita.PENDIENTE);
        when(pacienteService.listarDoctoresPaginated(anyInt(), anyInt(), any(), any())).thenReturn(new PageImpl<>(List.of()));
        when(pacienteService.especialidadesDisponibles()).thenReturn(List.of());
        when(pacienteService.buscarPorEmail("pac@test.com")).thenReturn(Optional.of(p));
        when(pacienteService.citasDelPaciente("p1")).thenReturn(List.of(c));
        when(pacienteService.mapDoctores()).thenReturn(Map.of("d1", "Dr. X"));

        mockMvc.perform(get("/pacientes/landing").param("error", "doctor_inactivo")
                        .with(user("pac@test.com").roles("PACIENTE")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/citas/c1/cancelar")))
                .andExpect(content().string(containsString("Ese doctor no está disponible")));
    }

    @Test
    void reprogramar_noPideMotivoYMuestraError() throws Exception {
        Paciente p = new Paciente();
        p.setId("p1");
        Doctor d = new Doctor();
        d.setId("d1");
        d.setNombre("Ana");
        d.setApellido("Ruiz");
        Cita c = new Cita();
        c.setId("c1");
        c.setPacienteId("p1");
        c.setDoctorId("d1");
        c.setFecha(LocalDate.now().plusDays(1));
        c.setHora(LocalTime.of(9, 0));
        when(citaService.buscarPorId("c1")).thenReturn(Optional.of(c));
        when(citaService.buscarPacientePorEmail("pac@test.com")).thenReturn(Optional.of(p));
        when(citaService.buscarDoctorPorId("d1")).thenReturn(Optional.of(d));
        Map<String, List<String>> slots = new LinkedHashMap<>();
        slots.put(LocalDate.now().plusDays(2).toString(), List.of("10:00"));
        when(citaService.slotsDisponibles(any(), any(), any(), any())).thenReturn(new LinkedHashMap<>(slots));

        mockMvc.perform(get("/citas/c1/reprogramar").param("error", "fecha_pasada")
                        .with(user("pac@test.com").roles("PACIENTE")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ya pasaron")))
                .andExpect(content().string(not(containsString("id=\"motivo\""))));
    }

    @Test
    void registro_usaLaListaUnicaDeEspecialidades() throws Exception {
        mockMvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Selecciona una especialidad")))
                .andExpect(content().string(containsString("<optgroup label=\"Salud Mental y Rehabilitación\"")))
                .andExpect(content().string(containsString("value=\"Cardiología\"")))
                .andExpect(content().string(containsString("minlength=\"8\"")));
    }

    @Test
    void elegirRol_usaLaListaUnicaDeEspecialidades() throws Exception {
        mockMvc.perform(get("/elegir-rol").sessionAttr("oauth2Email", "g@test.com"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"Pediatría\"")));
    }

    @Test
    void altaDoctorAdmin_esUnSelectConLaListaUnica() throws Exception {
        when(doctorService.listarDoctoresPaginated(anyInt(), anyInt(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/doctores").with(user("a@test.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<select id=\"especialidad\"")))
                .andExpect(content().string(containsString("value=\"Neurología\"")));
    }

    @Test
    void citasAdmin_paginaYMuestraNombres() throws Exception {
        Cita c = new Cita();
        c.setId("c1");
        c.setDoctorId("d1");
        c.setPacienteId("p1");
        c.setFecha(LocalDate.now());
        c.setHora(LocalTime.of(9, 0));
        c.setEstado(EstadoCita.ASISTIO);
        List<Cita> filas = new java.util.ArrayList<>(java.util.Collections.nCopies(10, c));
        when(citaService.listarPaginado(any(), any(), eq(0), eq(10)))
                .thenReturn(new PageImpl<>(filas, org.springframework.data.domain.PageRequest.of(0, 10), 25));
        when(citaService.nombresPorId(any())).thenReturn(Map.of("d1", "Dr. Ruiz", "p1", "Ana Gil"));

        mockMvc.perform(get("/citas").with(user("a@test.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Dr. Ruiz")))
                .andExpect(content().string(containsString("estado-asistio")))
                .andExpect(content().string(containsString("/citas?size=10&amp;page=1")));
    }

    @Test
    void citasListado_soloAdmin() throws Exception {
        mockMvc.perform(get("/citas").with(user("pac@test.com").roles("PACIENTE"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/citas").with(user("doc@test.com").roles("DOCTOR"))).andExpect(status().isForbidden());
    }

    @Test
    void horariosDeUnDoctor_soloAdmin() throws Exception {
        mockMvc.perform(get("/doctores/d9/horarios").with(user("doc@test.com").roles("DOCTOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void triageAgendar_noPoneSintomasEnLaUrl() throws Exception {
        mockMvc.perform(post("/triage/agendar").with(csrf()).with(user("pac@test.com").roles("PACIENTE"))
                        .param("doctorId", "d1").param("motivo", "dolor de pecho desde ayer"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/citas/nueva?doctorId=d1"));
    }

    @Test
    void eliminarDoctorConCitas_noLoElimina() throws Exception {
        when(doctorService.eliminar("d1")).thenReturn("tiene_citas");

        mockMvc.perform(post("/doctores/d1/eliminar").with(csrf()).with(user("a@test.com").roles("ADMIN")))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("errorGeneral"));
    }

    @Test
    void rutaInexistenteYParametroFaltante_noSonError500() throws Exception {
        mockMvc.perform(get("/no-existe").with(user("a@test.com").roles("ADMIN")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/citas/nueva").with(user("p@test.com").roles("PACIENTE")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/doctores").param("page", "abc").with(user("d@test.com").roles("DOCTOR")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/citas/nueva").param("doctorId", "x").with(csrf()).with(user("p@test.com").roles("PACIENTE")))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().exists("Allow"));
    }
}
