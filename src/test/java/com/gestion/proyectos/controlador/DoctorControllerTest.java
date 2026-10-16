package com.gestion.proyectos.controlador;

import com.gestion.proyectos.seguridad.RateLimiter;
import com.gestion.proyectos.modelo.Doctor;
import com.gestion.proyectos.modelo.TipoConsulta;
import com.gestion.proyectos.modelo.ViaAdministracion;
import com.gestion.proyectos.oauth2.OAuth2LoginSuccessHandler;
import com.gestion.proyectos.repositorio.AdminRepositorio;
import com.gestion.proyectos.repositorio.PersonaRepositorio;
import com.gestion.proyectos.seguridad.CustomUserDetailsService;
import com.gestion.proyectos.seguridad.JwtCookieService;
import com.gestion.proyectos.seguridad.JwtService;
import com.gestion.proyectos.seguridad.SecurityConfig;
import com.gestion.proyectos.servicio.DictamenValidador;
import com.gestion.proyectos.servicio.DoctorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
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

    @MockBean
    private RateLimiter rateLimiter;

    @BeforeEach
    void permitirPeticiones() {
        when(rateLimiter.permitir(anyString(), anyInt(), any())).thenReturn(true);
    }

    @MockBean private DoctorService doctorService;
    @MockBean private CustomUserDetailsService customUserDetailsService;
    @MockBean private PersonaRepositorio personaRepositorio;
    @MockBean private AdminRepositorio adminRepositorio;
    @MockBean private JwtService jwtService;
    @MockBean private JwtCookieService jwtCookieService;
    @MockBean private OAuth2LoginSuccessHandler oauth2LoginSuccessHandler;

    // ── guardar horario ──────────────────────────────────────────────────────

    @Test
    void guardarHorario_exitoso_redirigeDoctores() throws Exception {
        Doctor doctor = new Doctor();
        doctor.setId("d1");
        when(doctorService.buscarPorEmail("doc@test.com")).thenReturn(Optional.of(doctor));
        when(doctorService.guardarHorariosSemanales(eq("d1"), eq(List.of("MONDAY")), any(), eq(30))).thenReturn(null);

        mockMvc.perform(post("/doctores/horarios/guardar")
                        .param("days", "MONDAY")
                        .param("MONDAY_horaInicio", "08:00")
                        .param("MONDAY_horaFin", "12:00")
                        .param("duracionCitaMinutos", "30")
                        .with(csrf())
                        .with(user("doc@test.com").roles("DOCTOR")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/doctores"));

        verify(doctorService).guardarHorariosSemanales(eq("d1"), eq(List.of("MONDAY")), any(), eq(30));
    }

    @Test
    void guardarHorario_sinDias_redirigeDoctoresConError() throws Exception {
        mockMvc.perform(post("/doctores/horarios/guardar")
                        .param("duracionCitaMinutos", "30")
                        .with(csrf())
                        .with(user("doc@test.com").roles("DOCTOR")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/doctores?error=no_days_selected"));

        verify(doctorService, never()).guardarHorariosSemanales(any(), any(), any(), anyInt());
    }

    @Test
    void guardarHorario_errorEnDia_redirigeDoctoresConError() throws Exception {
        Doctor doctor = new Doctor();
        doctor.setId("d1");
        when(doctorService.buscarPorEmail("doc@test.com")).thenReturn(Optional.of(doctor));
        when(doctorService.guardarHorariosSemanales(eq("d1"), eq(List.of("MONDAY")), any(), eq(30)))
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

    private Doctor doctorD1() {
        Doctor doctor = new Doctor();
        doctor.setId("d1");
        when(doctorService.buscarPorEmail("doc@test.com")).thenReturn(Optional.of(doctor));
        return doctor;
    }

    @Test
    void guardarDictamen_soap_enlazaSeccionesYListas() throws Exception {
        Doctor doctor = doctorD1();
        when(doctorService.guardarDictamen(eq("c1"), eq(doctor), any())).thenReturn(null);

        mockMvc.perform(post("/doctores/citas/c1/dictamen")
                        .param("tipoConsulta", "PRIMERA_VEZ")
                        .param("causaExterna", "ENFERMEDAD_GENERAL")
                        .param("motivoConsulta", "Me duele la garganta")
                        .param("enfermedadActual", "Dolor desde hace 3 días")
                        .param("antecedentes.alergicos", "Penicilina")
                        .param("signosVitales.tensionSistolica", "120")
                        .param("signosVitales.tensionDiastolica", "80")
                        .param("signosVitales.temperatura", "38.2")
                        .param("examenFisico", "Amígdalas hipertróficas")
                        .param("diagnosticoPrincipal.codigo", "J039")
                        .param("diagnosticoPrincipal.descripcion", "Amigdalitis aguda")
                        .param("diagnosticoPrincipal.tipo", "CONFIRMADO_NUEVO")
                        .param("diagnosticosRelacionados[0].codigo", "R509")
                        .param("diagnosticosRelacionados[0].descripcion", "Fiebre")
                        .param("diagnosticosRelacionados[0].tipo", "IMPRESION_DIAGNOSTICA")
                        .param("medicamentos[0].nombre", "Acetaminofén 500 mg")
                        .param("medicamentos[0].via", "ORAL")
                        .param("medicamentos[0].dosisFrecuencia", "1 tableta cada 8 horas")
                        .param("medicamentos[0].duracion", "Por 5 días")
                        .param("medicamentos[0].cantidad", "15")
                        .param("incapacidadDias", "")
                        .with(csrf())
                        .with(user("doc@test.com").roles("DOCTOR")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/doctores"))
                .andExpect(flash().attributeExists("exitoDictamen"));

        verify(doctorService).guardarDictamen(eq("c1"), eq(doctor), argThat(d ->
                d.getTipoConsulta() == TipoConsulta.PRIMERA_VEZ
                        && "Penicilina".equals(d.getAntecedentes().getAlergicos())
                        && d.getSignosVitales().getTensionSistolica() == 120
                        && d.getSignosVitales().getTemperatura() == 38.2
                        && "J039".equals(d.getDiagnosticoPrincipal().getCodigo())
                        && d.getDiagnosticosRelacionados().size() == 1
                        && d.getMedicamentos().get(0).getVia() == ViaAdministracion.ORAL
                        && d.getMedicamentos().get(0).getCantidad() == 15
                        && d.getIncapacidadDias() == null));
    }

    @Test
    void guardarDictamen_errorDeValidacion_devuelveDatosParaReabrirElFormulario() throws Exception {
        Doctor doctor = doctorD1();
        when(doctorService.guardarDictamen(eq("c1"), eq(doctor), any())).thenReturn("Falta el examen físico.");

        mockMvc.perform(post("/doctores/citas/c1/dictamen")
                        .param("motivoConsulta", "Tos")
                        .with(csrf())
                        .with(user("doc@test.com").roles("DOCTOR")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/doctores#citas"))
                .andExpect(flash().attribute("errorDictamen", "Falta el examen físico."))
                .andExpect(flash().attribute("dictamenCitaId", "c1"))
                .andExpect(flash().attributeExists("dictamenPrevio"));
    }

    @Test
    void guardarDictamen_numeroOEnumInvalido_noLlamaAlServicio() throws Exception {
        doctorD1();

        mockMvc.perform(post("/doctores/citas/c1/dictamen")
                        .param("signosVitales.temperatura", "treinta")
                        .param("tipoConsulta", "INVENTADA")
                        .with(csrf())
                        .with(user("doc@test.com").roles("DOCTOR")))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("errorDictamen"));

        verify(doctorService, never()).guardarDictamen(any(), any(), any());
    }

    @Test
    void guardarDictamen_firmaEnviadaAMano_seDescarta() throws Exception {
        Doctor doctor = doctorD1();
        when(doctorService.guardarDictamen(eq("c1"), eq(doctor), any())).thenReturn(null);

        mockMvc.perform(post("/doctores/citas/c1/dictamen")
                        .param("firmadoPor", "Otro médico")
                        .with(csrf())
                        .with(user("doc@test.com").roles("DOCTOR")))
                .andExpect(status().is3xxRedirection());

        // El controlador solo enlaza; DictamenValidador.normalizar copia únicamente campos clínicos
        verify(doctorService).guardarDictamen(eq("c1"), eq(doctor),
                argThat(d -> DictamenValidador.normalizar(d).getFirmadoPor() == null));
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

    // ── foto demasiado grande ────────────────────────────────────────────────

    @Test
    void actualizarFoto_archivoMuyGrande_redirigeConErrorTamano() throws Exception {
        Doctor doctor = new Doctor();
        when(doctorService.buscarPorEmail("doc@test.com")).thenReturn(Optional.of(doctor));
        when(doctorService.actualizarFoto(any(), any()))
                .thenThrow(new org.springframework.web.multipart.MaxUploadSizeExceededException(2L * 1024 * 1024));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/doctores/perfil/foto")
                        .file(new org.springframework.mock.web.MockMultipartFile("foto", "f.png", "image/png", new byte[10]))
                        .with(csrf())
                        .with(user("doc@test.com").roles("DOCTOR")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/doctores?fotoError=tamano#perfil"));
    }
}
