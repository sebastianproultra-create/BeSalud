package com.gestion.proyectos.controlador;

import com.gestion.proyectos.modelo.Doctor;
import com.gestion.proyectos.repositorio.AdminRepositorio;
import com.gestion.proyectos.repositorio.PersonaRepositorio;
import com.gestion.proyectos.seguridad.CustomUserDetailsService;
import com.gestion.proyectos.seguridad.JwtCookieService;
import com.gestion.proyectos.seguridad.JwtService;
import com.gestion.proyectos.seguridad.SecurityConfig;
import com.gestion.proyectos.servicio.DoctorService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DoctorController.class)
@Import(SecurityConfig.class)
class DoctorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private DoctorService doctorService;
    @MockBean private CustomUserDetailsService customUserDetailsService;
    @MockBean private PersonaRepositorio personaRepositorio;
    @MockBean private AdminRepositorio adminRepositorio;
    @MockBean private JwtService jwtService;
    @MockBean private JwtCookieService jwtCookieService;

    // ── guardar horario ──────────────────────────────────────────────────────

    @Test
    void guardarHorario_exitoso_redirigeDoctores() throws Exception {
        Doctor doctor = new Doctor();
        doctor.setId("d1");
        when(doctorService.buscarPorEmail("doc@test.com")).thenReturn(Optional.of(doctor));
        when(doctorService.guardarHorarioDia(eq("d1"), eq("MONDAY"), any(), eq(30))).thenReturn(null);

        mockMvc.perform(post("/doctores/horarios/guardar")
                        .param("days", "MONDAY")
                        .param("MONDAY_horaInicio", "08:00")
                        .param("MONDAY_horaFin", "12:00")
                        .param("duracionCitaMinutos", "30")
                        .with(csrf())
                        .with(user("doc@test.com").roles("DOCTOR")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/doctores"));

        verify(doctorService).guardarHorarioDia(eq("d1"), eq("MONDAY"), any(), eq(30));
    }

    @Test
    void guardarHorario_sinDias_redirigeDoctoresConError() throws Exception {
        mockMvc.perform(post("/doctores/horarios/guardar")
                        .param("duracionCitaMinutos", "30")
                        .with(csrf())
                        .with(user("doc@test.com").roles("DOCTOR")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/doctores?error=no_days_selected"));

        verify(doctorService, never()).guardarHorarioDia(any(), any(), any(), anyInt());
    }

    @Test
    void guardarHorario_errorEnDia_redirigeDoctoresConError() throws Exception {
        Doctor doctor = new Doctor();
        doctor.setId("d1");
        when(doctorService.buscarPorEmail("doc@test.com")).thenReturn(Optional.of(doctor));
        when(doctorService.guardarHorarioDia(eq("d1"), eq("MONDAY"), any(), eq(30)))
                .thenReturn("hora_invalida");

        mockMvc.perform(post("/doctores/horarios/guardar")
                        .param("days", "MONDAY")
                        .param("duracionCitaMinutos", "30")
                        .with(csrf())
                        .with(user("doc@test.com").roles("DOCTOR")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/doctores?error=hora_invalida"));
    }

    @Test
    void guardarHorario_rolPaciente_accesoNegado() throws Exception {
        mockMvc.perform(post("/doctores/horarios/guardar")
                        .param("days", "MONDAY")
                        .param("duracionCitaMinutos", "30")
                        .with(csrf())
                        .with(user("pac@test.com").roles("PACIENTE")))
                .andExpect(status().isForbidden());
    }

    // ── eliminar horario ─────────────────────────────────────────────────────

    @Test
    void eliminarHorario_exitoso_redirigeDoctores() throws Exception {
        Doctor doctor = new Doctor();
        doctor.setId("d1");
        when(doctorService.buscarPorEmail("doc@test.com")).thenReturn(Optional.of(doctor));

        mockMvc.perform(post("/doctores/horarios/h1/eliminar")
                        .with(csrf())
                        .with(user("doc@test.com").roles("DOCTOR")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/doctores"));

        verify(doctorService).eliminarHorario("h1", "d1");
    }

    @Test
    void eliminarHorario_rolAdmin_accesoNegado() throws Exception {
        mockMvc.perform(post("/doctores/horarios/h1/eliminar")
                        .with(csrf())
                        .with(user("admin@test.com").roles("ADMIN")))
                .andExpect(status().isForbidden());
    }

    // ── cancelar cita (doctor) ───────────────────────────────────────────────

    @Test
    void cancelarCita_exitoso_redirigeDoctores() throws Exception {
        Doctor doctor = new Doctor();
        doctor.setId("d1");
        when(doctorService.buscarPorEmail("doc@test.com")).thenReturn(Optional.of(doctor));

        mockMvc.perform(post("/doctores/citas/c1/cancelar")
                        .with(csrf())
                        .with(user("doc@test.com").roles("DOCTOR")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/doctores"));

        verify(doctorService).cancelarCita("c1", "d1");
    }

    // ── dictamen ─────────────────────────────────────────────────────────────

    @Test
    void guardarDictamen_exitoso_redirigeDoctores() throws Exception {
        Doctor doctor = new Doctor();
        doctor.setId("d1");
        when(doctorService.buscarPorEmail("doc@test.com")).thenReturn(Optional.of(doctor));

        mockMvc.perform(post("/doctores/citas/c1/dictamen")
                        .param("diagnostico", "Gripa común")
                        .param("tratamiento", "Reposo y líquidos")
                        .param("observaciones", "Revisar en 5 días")
                        .with(csrf())
                        .with(user("doc@test.com").roles("DOCTOR")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/doctores"));

        verify(doctorService).guardarDictamen("c1", "d1", "Gripa común", "Reposo y líquidos", "Revisar en 5 días");
    }

    @Test
    void guardarDictamen_sinObservaciones_redirigeDoctores() throws Exception {
        Doctor doctor = new Doctor();
        doctor.setId("d1");
        when(doctorService.buscarPorEmail("doc@test.com")).thenReturn(Optional.of(doctor));

        mockMvc.perform(post("/doctores/citas/c1/dictamen")
                        .param("diagnostico", "Gripa común")
                        .param("tratamiento", "Reposo y líquidos")
                        .with(csrf())
                        .with(user("doc@test.com").roles("DOCTOR")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/doctores"));

        verify(doctorService).guardarDictamen("c1", "d1", "Gripa común", "Reposo y líquidos", null);
    }

    // ── marcar asistencia ────────────────────────────────────────────────────

    @Test
    void marcarAsistio_exitoso_redirigeDoctores() throws Exception {
        Doctor doctor = new Doctor();
        doctor.setId("d1");
        when(doctorService.buscarPorEmail("doc@test.com")).thenReturn(Optional.of(doctor));

        mockMvc.perform(post("/doctores/citas/c1/asistio")
                        .with(csrf())
                        .with(user("doc@test.com").roles("DOCTOR")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/doctores"));

        verify(doctorService).marcarAsistio("c1", "d1");
    }

    @Test
    void marcarNoAsistio_exitoso_redirigeDoctores() throws Exception {
        Doctor doctor = new Doctor();
        doctor.setId("d1");
        when(doctorService.buscarPorEmail("doc@test.com")).thenReturn(Optional.of(doctor));

        mockMvc.perform(post("/doctores/citas/c1/no-asistio")
                        .with(csrf())
                        .with(user("doc@test.com").roles("DOCTOR")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/doctores"));

        verify(doctorService).marcarNoAsistio("c1", "d1");
    }
}
