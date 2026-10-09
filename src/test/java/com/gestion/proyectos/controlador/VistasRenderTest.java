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
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Comprueba que las plantillas con mensajes de error y botones nuevos renderizan sin fallar. */
@WebMvcTest({DoctorController.class, PacienteController.class, CitaController.class})
@Import(SecurityConfig.class)
class VistasRenderTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private DoctorService doctorService;
    @MockBean private PacienteService pacienteService;
    @MockBean private CitaService citaService;
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
        when(pacienteService.listarTodos()).thenReturn(List.of());

        mockMvc.perform(post("/pacientes/guardar").with(csrf()).with(user("a@test.com").roles("ADMIN"))
                        .param("nombre", "Luis").param("apellido", "Gil").param("email", "luis@test.com"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ya existe un paciente registrado con ese correo")))
                .andExpect(content().string(containsString("value=\"Luis\"")));
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
}
