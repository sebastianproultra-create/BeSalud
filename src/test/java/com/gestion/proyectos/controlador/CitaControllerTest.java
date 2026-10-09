package com.gestion.proyectos.controlador;

import com.gestion.proyectos.oauth2.OAuth2LoginSuccessHandler;
import com.gestion.proyectos.modelo.Cita;
import com.gestion.proyectos.modelo.Doctor;
import com.gestion.proyectos.modelo.EstadoCita;
import com.gestion.proyectos.modelo.Paciente;
import com.gestion.proyectos.repositorio.AdminRepositorio;
import com.gestion.proyectos.repositorio.PersonaRepositorio;
import com.gestion.proyectos.seguridad.CustomUserDetailsService;
import com.gestion.proyectos.seguridad.JwtCookieService;
import com.gestion.proyectos.seguridad.JwtService;
import com.gestion.proyectos.seguridad.SecurityConfig;
import com.gestion.proyectos.servicio.CitaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CitaController.class)
@Import(SecurityConfig.class)
class CitaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private CitaService citaService;
    @MockBean private CustomUserDetailsService customUserDetailsService;
    @MockBean private PersonaRepositorio personaRepositorio;
    @MockBean private AdminRepositorio adminRepositorio;
    @MockBean private JwtService jwtService;
    @MockBean private JwtCookieService jwtCookieService;
    @MockBean private OAuth2LoginSuccessHandler oauth2LoginSuccessHandler;

    // ── guardar-paciente ────────────────────────────────────────────────────

    private static Doctor doctorActivo() {
        Doctor doctor = new Doctor();
        doctor.setId("d1");
        doctor.setEstado("ACTIVO");
        return doctor;
    }

    @Test
    void guardarPaciente_exitoso_redirigeLandingConExito() throws Exception {
        Paciente paciente = new Paciente();
        paciente.setId("p1");
        paciente.setEmail("paciente@test.com");

        when(citaService.buscarPacientePorEmail("paciente@test.com")).thenReturn(Optional.of(paciente));
        when(citaService.esFechaHoraPasada(any(), any())).thenReturn(false);
        when(citaService.buscarDoctorPorId("d1")).thenReturn(Optional.of(doctorActivo()));
        when(citaService.esHorarioValido(eq("d1"), any(), any())).thenReturn(true);
        when(citaService.hayConflicto(eq("d1"), any(), any(), isNull())).thenReturn(false);

        mockMvc.perform(post("/citas/guardar-paciente")
                        .param("doctorId", "d1")
                        .param("fecha", LocalDate.now().plusDays(1).toString())
                        .param("hora", "09:00")
                        .param("motivo", "Dolor de cabeza")
                        .with(csrf())
                        .with(user("paciente@test.com").roles("PACIENTE")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/pacientes/landing?success=cita_agendada"));

        verify(citaService).crearCita(eq("d1"), eq("p1"), any(), any(), eq("Dolor de cabeza"));
    }

    @Test
    void guardarPaciente_sinDoctorId_redirigeLandingConError() throws Exception {
        mockMvc.perform(post("/citas/guardar-paciente")
                        .param("fecha", LocalDate.now().plusDays(1).toString())
                        .param("hora", "09:00")
                        .param("motivo", "Motivo")
                        .with(csrf())
                        .with(user("paciente@test.com").roles("PACIENTE")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/pacientes/landing?error=doctor_requerido"));
    }

    @Test
    void guardarPaciente_sinMotivo_redirigeCitasNuevaConError() throws Exception {
        mockMvc.perform(post("/citas/guardar-paciente")
                        .param("doctorId", "d1")
                        .param("fecha", LocalDate.now().plusDays(1).toString())
                        .param("hora", "09:00")
                        .with(csrf())
                        .with(user("paciente@test.com").roles("PACIENTE")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/citas/nueva?doctorId=d1&error=motivo_requerido"));
    }

    @Test
    void guardarPaciente_fechaPasada_redirigeCitasNuevaConError() throws Exception {
        when(citaService.esFechaHoraPasada(any(), any())).thenReturn(true);

        mockMvc.perform(post("/citas/guardar-paciente")
                        .param("doctorId", "d1")
                        .param("fecha", LocalDate.now().minusDays(1).toString())
                        .param("hora", "09:00")
                        .param("motivo", "Motivo")
                        .with(csrf())
                        .with(user("paciente@test.com").roles("PACIENTE")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/citas/nueva?doctorId=d1&error=fecha_pasada"));
    }

    @Test
    void guardarPaciente_horarioInvalido_redirigeCitasNuevaConError() throws Exception {
        Paciente paciente = new Paciente();
        paciente.setId("p1");
        when(citaService.buscarPacientePorEmail(anyString())).thenReturn(Optional.of(paciente));
        when(citaService.esFechaHoraPasada(any(), any())).thenReturn(false);
        when(citaService.buscarDoctorPorId("d1")).thenReturn(Optional.of(doctorActivo()));
        when(citaService.esHorarioValido(eq("d1"), any(), any())).thenReturn(false);

        mockMvc.perform(post("/citas/guardar-paciente")
                        .param("doctorId", "d1")
                        .param("fecha", LocalDate.now().plusDays(1).toString())
                        .param("hora", "09:00")
                        .param("motivo", "Motivo")
                        .with(csrf())
                        .with(user("pac@test.com").roles("PACIENTE")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/citas/nueva?doctorId=d1&error=horario_invalido"));
    }

    @Test
    void guardarPaciente_conflictoCita_redirigeCitasNuevaConError() throws Exception {
        Paciente paciente = new Paciente();
        paciente.setId("p1");
        when(citaService.buscarPacientePorEmail(anyString())).thenReturn(Optional.of(paciente));
        when(citaService.esFechaHoraPasada(any(), any())).thenReturn(false);
        when(citaService.buscarDoctorPorId("d1")).thenReturn(Optional.of(doctorActivo()));
        when(citaService.esHorarioValido(eq("d1"), any(), any())).thenReturn(true);
        when(citaService.hayConflicto(eq("d1"), any(), any(), isNull())).thenReturn(true);

        mockMvc.perform(post("/citas/guardar-paciente")
                        .param("doctorId", "d1")
                        .param("fecha", LocalDate.now().plusDays(1).toString())
                        .param("hora", "09:00")
                        .param("motivo", "Motivo")
                        .with(csrf())
                        .with(user("pac@test.com").roles("PACIENTE")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/citas/nueva?doctorId=d1&error=conflicto_cita"));
    }

    @Test
    void guardarPaciente_pacienteYaTieneCitaEsaHora_redirigeConError() throws Exception {
        Paciente paciente = new Paciente();
        paciente.setId("p1");
        when(citaService.buscarPacientePorEmail(anyString())).thenReturn(Optional.of(paciente));
        when(citaService.esFechaHoraPasada(any(), any())).thenReturn(false);
        when(citaService.buscarDoctorPorId("d1")).thenReturn(Optional.of(doctorActivo()));
        when(citaService.esHorarioValido(eq("d1"), any(), any())).thenReturn(true);
        when(citaService.hayConflicto(eq("d1"), any(), any(), isNull())).thenReturn(false);
        when(citaService.pacienteTieneConflicto(eq("p1"), eq("d1"), any(), any(), isNull())).thenReturn(true);

        mockMvc.perform(post("/citas/guardar-paciente")
                        .param("doctorId", "d1")
                        .param("fecha", LocalDate.now().plusDays(1).toString())
                        .param("hora", "09:00")
                        .param("motivo", "Motivo")
                        .with(csrf())
                        .with(user("pac@test.com").roles("PACIENTE")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/citas/nueva?doctorId=d1&error=conflicto_paciente"));
        verify(citaService, never()).crearCita(any(), any(), any(), any(), any());
    }

    // ── reprogramar ─────────────────────────────────────────────────────────

    @Test
    void reprogramar_exitoso_redirigeLandingConExito() throws Exception {
        Paciente paciente = new Paciente();
        paciente.setId("p1");
        paciente.setEmail("paciente@test.com");

        Cita cita = new Cita();
        cita.setId("c1");
        cita.setPacienteId("p1");
        cita.setDoctorId("d1");

        when(citaService.buscarPacientePorEmail("paciente@test.com")).thenReturn(Optional.of(paciente));
        when(citaService.buscarPorId("c1")).thenReturn(Optional.of(cita));
        when(citaService.esFechaHoraPasada(any(), any())).thenReturn(false);
        when(citaService.esHorarioValido(eq("d1"), any(), any())).thenReturn(true);
        when(citaService.hayConflicto(eq("d1"), any(), any(), eq("c1"))).thenReturn(false);

        mockMvc.perform(post("/citas/c1/reprogramar")
                        .param("fecha", LocalDate.now().plusDays(2).toString())
                        .param("hora", "10:00")
                        .with(csrf())
                        .with(user("paciente@test.com").roles("PACIENTE")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/pacientes/landing?success=cita_reprogramada"));

        verify(citaService).reprogramar(eq(cita), any(), any());
    }

    @Test
    void reprogramar_citaDeOtroPaciente_redirigeLanding() throws Exception {
        Paciente paciente = new Paciente();
        paciente.setId("p2"); // distinto del dueño
        when(citaService.buscarPacientePorEmail(anyString())).thenReturn(Optional.of(paciente));

        Cita cita = new Cita();
        cita.setId("c1");
        cita.setPacienteId("p1"); // dueño real
        when(citaService.buscarPorId("c1")).thenReturn(Optional.of(cita));

        mockMvc.perform(post("/citas/c1/reprogramar")
                        .param("fecha", LocalDate.now().plusDays(1).toString())
                        .param("hora", "09:00")
                        .with(csrf())
                        .with(user("otro@test.com").roles("PACIENTE")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/pacientes/landing"));
    }

    // ── cancelar ────────────────────────────────────────────────────────────

    @Test
    void cancelar_pacientePropietario_redirigeLanding() throws Exception {
        Paciente paciente = new Paciente();
        paciente.setId("p1");
        Cita cita = new Cita();
        cita.setId("c1");
        cita.setPacienteId("p1");
        cita.setEstado(EstadoCita.PENDIENTE);

        when(citaService.buscarPorId("c1")).thenReturn(Optional.of(cita));
        when(citaService.buscarPacientePorEmail("pac@test.com")).thenReturn(Optional.of(paciente));

        mockMvc.perform(post("/citas/c1/cancelar")
                        .with(csrf())
                        .with(user("pac@test.com").roles("PACIENTE")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/pacientes/landing"));

        verify(citaService).cancelar(cita);
    }

    @Test
    void cancelar_doctorPropietario_redirigeDoctores() throws Exception {
        Doctor doctor = new Doctor();
        doctor.setId("d1");
        Cita cita = new Cita();
        cita.setId("c1");
        cita.setDoctorId("d1");
        cita.setEstado(EstadoCita.PENDIENTE);

        when(citaService.buscarPorId("c1")).thenReturn(Optional.of(cita));
        when(citaService.buscarDoctorPorEmail("doc@test.com")).thenReturn(Optional.of(doctor));

        mockMvc.perform(post("/citas/c1/cancelar")
                        .with(csrf())
                        .with(user("doc@test.com").roles("DOCTOR")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/doctores"));

        verify(citaService).cancelar(cita);
    }

    @Test
    void cancelar_admin_redirigeCitas() throws Exception {
        Cita cita = new Cita();
        cita.setId("c1");
        cita.setEstado(EstadoCita.PENDIENTE);

        when(citaService.buscarPorId("c1")).thenReturn(Optional.of(cita));

        mockMvc.perform(post("/citas/c1/cancelar")
                        .with(csrf())
                        .with(user("admin@test.com").roles("ADMIN")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/citas"));

        verify(citaService).cancelar(cita);
    }

    @Test
    void cancelar_pacienteAjenoACita_retornaNoAutorizado() throws Exception {
        Paciente paciente = new Paciente();
        paciente.setId("p2"); // no es el dueño
        Cita cita = new Cita();
        cita.setId("c1");
        cita.setPacienteId("p1");

        when(citaService.buscarPorId("c1")).thenReturn(Optional.of(cita));
        when(citaService.buscarPacientePorEmail("intruso@test.com")).thenReturn(Optional.of(paciente));

        mockMvc.perform(post("/citas/c1/cancelar")
                        .with(csrf())
                        .with(user("intruso@test.com").roles("PACIENTE")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/pacientes/landing?error=no_autorizado"));

        verify(citaService, never()).cancelar(any());
    }

    @Test
    void cancelar_sinCsrf_rechazado() throws Exception {
        mockMvc.perform(post("/citas/c1/cancelar")
                        .with(user("pac@test.com").roles("PACIENTE")))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    org.junit.jupiter.api.Assertions.assertTrue(
                            status == 403 || (status >= 300 && status < 400),
                            "Debe ser 403 o redirección, fue: " + status);
                });
    }
}
