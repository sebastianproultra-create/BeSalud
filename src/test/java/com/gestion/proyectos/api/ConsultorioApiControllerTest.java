package com.gestion.proyectos.api;

import com.gestion.proyectos.modelo.Cita;
import com.gestion.proyectos.modelo.Consultorio;
import com.gestion.proyectos.modelo.Doctor;
import com.gestion.proyectos.modelo.Paciente;
import com.gestion.proyectos.modelo.Reserva;
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
import com.gestion.proyectos.servicio.CitaService;
import com.gestion.proyectos.servicio.PacienteService;
import com.gestion.proyectos.servicio.PasarelaPagos;
import com.gestion.proyectos.servicio.RegistroService;
import com.gestion.proyectos.servicio.ReservaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** API sin interfaz por consultorio: el paciente agenda y paga desde la web del consultorio, nunca en el gestor. */
@WebMvcTest(controllers = ConsultorioApiController.class,
        properties = {"app.api-consultorios.habilitada=true", "app.jwt.secret=secreto-de-pruebas-de-32-caracteres-min"})
@Import({SecurityConfig.class, ApiSecurityConfig.class, TokenConsultorioService.class, Idempotencia.class})
class ConsultorioApiControllerTest {

    private static final String ORIGEN = "https://pequenospasos.com";
    private static final String BASE = "/api/v1/c/pequenos-pasos";
    private static final String DOCTOR = "doc1";
    private static final String DOCTOR_AJENO = "docAjeno";

    @Autowired private MockMvc mockMvc;
    @Autowired private TokenConsultorioService tokens;
    @MockBean private ConsultorioRepositorio consultorioRepo;
    @MockBean private CitaService citaService;
    @MockBean private ReservaService reservaService;
    @MockBean private PasarelaPagos pasarela;
    @MockBean private RegistroService registroService;
    @MockBean private PacienteService pacienteService;
    @MockBean private PersonaRepositorio personaRepositorio;
    @MockBean private AdminRepositorio adminRepositorio;
    @MockBean private CustomUserDetailsService customUserDetailsService;
    @MockBean private JwtService jwtService;
    @MockBean private JwtCookieService jwtCookieService;
    @MockBean private ConsultorioCorsSource consultorioCorsSource;
    @MockBean private com.gestion.proyectos.servicio.ConsultorioService consultorioService; // IntegracionWebAdvice
    @MockBean private com.gestion.proyectos.servicio.SitioWebService sitioWebService; // IntegracionWebAdvice
    @MockBean private RateLimiter rateLimiter;
    @MockBean private AuthenticationManager authenticationManager;
    @MockBean private OAuth2LoginSuccessHandler oauth2LoginSuccessHandler;

    private Paciente paciente;
    private Doctor doctor;

    @BeforeEach
    void preparar() {
        when(rateLimiter.permitir(anyString(), anyInt(), any())).thenReturn(true);
        Consultorio c = new Consultorio();
        c.setId("pequenos-pasos");
        c.setNombre("Pequeños Pasos");
        c.setDominios(List.of(ORIGEN));
        c.setDoctorIds(List.of(DOCTOR));
        when(consultorioRepo.findByIdAndActivoTrue("pequenos-pasos")).thenReturn(Optional.of(c));

        doctor = new Doctor();
        doctor.setId(DOCTOR);
        doctor.setNombre("Leo");
        doctor.setApellido("Ruiz");
        doctor.setEspecialidad("Pediatría");
        doctor.setEstado("ACTIVO");
        doctor.setEmail("leo@doc.com");
        when(citaService.buscarDoctorPorId(DOCTOR)).thenReturn(Optional.of(doctor));

        paciente = new Paciente("Ana", "Gómez", "300", "1", "ana@correo.com", "x");
        paciente.setId("pac1");
        when(citaService.buscarPacientePorEmail("ana@correo.com")).thenReturn(Optional.of(paciente));
        when(pasarela.usaSimulada()).thenReturn(true);
        when(pasarela.pagosDisponibles()).thenReturn(true);
    }

    private String bearer() {
        return "Bearer " + tokens.emitir("ana@correo.com", "pequenos-pasos");
    }

    // ── Tenant ──────────────────────────────────────────────────────────────

    @Test
    void consultorio_desdeSuDominio_muestraSoloSusMedicosYElTipoDePago() throws Exception {
        mockMvc.perform(get(BASE).header("Origin", ORIGEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Pequeños Pasos"))
                .andExpect(jsonPath("$.pago").value("simulado"))
                .andExpect(jsonPath("$.medicos", hasSize(1)))
                .andExpect(jsonPath("$.medicos[0].nombre").value("Dr(a). Leo Ruiz"));
    }

    @Test
    void consultorio_desdeOtroDominio_403() throws Exception {
        mockMvc.perform(get(BASE).header("Origin", "https://otro.com")).andExpect(status().isForbidden());
        mockMvc.perform(get(BASE)).andExpect(status().isForbidden());
    }

    @Test
    void disponibilidad_deMedicoDeOtroConsultorio_404() throws Exception {
        mockMvc.perform(get(BASE + "/disponibilidad").param("doctorId", DOCTOR_AJENO).header("Origin", ORIGEN))
                .andExpect(status().isNotFound());
        verify(citaService, never()).slotsDisponibles(anyString(), any(), any(), any(), any());
    }

    @Test
    void disponibilidad_devuelveLosDiasDelMedico() throws Exception {
        LinkedHashMap<String, List<String>> dias = new LinkedHashMap<>();
        dias.put("2030-01-07", List.of("08:00", "08:30"));
        when(citaService.slotsDisponibles(DOCTOR, null, null, null, null)).thenReturn(dias);

        mockMvc.perform(get(BASE + "/disponibilidad").param("doctorId", DOCTOR).header("Origin", ORIGEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dias['2030-01-07'][1]").value("08:30"));
    }

    // ── Cuenta ──────────────────────────────────────────────────────────────

    @Test
    void sesion_devuelveTokenEnVezDeLlevarAlGestor() throws Exception {
        when(authenticationManager.authenticate(any())).thenReturn(new UsernamePasswordAuthenticationToken(
                "ana@correo.com", null, List.of(new SimpleGrantedAuthority("ROLE_PACIENTE"))));

        mockMvc.perform(post(BASE + "/sesion").header("Origin", ORIGEN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"Ana@Correo.com\",\"password\":\"Clave#2026x\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", not(emptyOrNullString())))
                .andExpect(jsonPath("$.paciente.nombre").value("Ana"))
                .andExpect(jsonPath("$.redirectUrl").doesNotExist());
        verify(jwtCookieService, never()).addJwtCookie(any(), anyString());
    }

    @Test
    void registro_demasiadosDesdeLaMismaConexion_429EnJson() throws Exception {
        when(rateLimiter.permitir(org.mockito.ArgumentMatchers.startsWith("registro:"), anyInt(), any())).thenReturn(false);

        mockMvc.perform(post(BASE + "/pacientes").header("Origin", ORIGEN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana@correo.com\",\"password\":\"Clave#123\",\"aceptaPrivacidad\":true}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value(containsString("Espera unos minutos")));
        verify(registroService, never()).registrar(any());
    }

    @Test
    void registro_duplicado_409SinEnlaceAlGestor() throws Exception {
        when(registroService.verificarDuplicado(any())).thenReturn("Ya existe");

        mockMvc.perform(post(BASE + "/pacientes").header("Origin", ORIGEN).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Ana","apellido":"Gómez","identificacion":"1","telefono":"300",
                                 "email":"ana@correo.com","password":"Clave#2026x","aceptaPrivacidad":true}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.loginUrl").doesNotExist());
        verify(registroService, never()).registrar(any());
    }

    @Test
    void tokenDeOtroConsultorio_noSirve() throws Exception {
        String ajeno = "Bearer " + tokens.emitir("ana@correo.com", "otro-consultorio");
        mockMvc.perform(get(BASE + "/citas").header("Origin", ORIGEN).header("Authorization", ajeno))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenDelConsultorio_noAbreElGestor() {
        // Mismo JWT_SECRET, clave derivada distinta: el filtro del portal no puede validarlo.
        JwtService portal = new JwtService("secreto-de-pruebas-de-32-caracteres-min", 3_600_000);
        String token = tokens.emitir("ana@correo.com", "pequenos-pasos");
        org.junit.jupiter.api.Assertions.assertThrows(io.jsonwebtoken.JwtException.class,
                () -> portal.extractUsername(token));
    }

    // ── Agendar y pagar ─────────────────────────────────────────────────────

    @Test
    void reservar_sinSesion_401() throws Exception {
        mockMvc.perform(post(BASE + "/citas").header("Origin", ORIGEN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"doctorId\":\"doc1\",\"fecha\":\"2030-01-07\",\"hora\":\"08:00\",\"motivo\":\"Control\"}"))
                .andExpect(status().isUnauthorized());
        verify(reservaService, never()).apartar(any(), any(), any(), any(), any());
    }

    @Test
    void reservar_apartaElTurnoYPagaDentroDeLaWeb() throws Exception {
        when(citaService.esHorarioValido(eq(DOCTOR), any(), any())).thenReturn(true);
        Reserva r = new Reserva();
        r.setReferencia("BS-1");
        r.setMontoCentavos(1_000_000);
        r.setExpiraEn(LocalDateTime.now().plusMinutes(15));
        when(reservaService.apartar(eq(doctor), eq("pac1"), eq(LocalDate.of(2030, 1, 7)), eq(LocalTime.of(8, 0)),
                eq("Control"))).thenReturn(r);

        mockMvc.perform(post(BASE + "/citas").header("Origin", ORIGEN).header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"doctorId\":\"doc1\",\"fecha\":\"2030-01-07\",\"hora\":\"08:00\",\"motivo\":\"Control\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.referencia").value("BS-1"))
                .andExpect(jsonPath("$.abono").value(10000))
                .andExpect(jsonPath("$.pago.tipo").value("simulado"))
                .andExpect(jsonPath("$.pago.url").doesNotExist());
    }

    @Test
    void reservar_conIdempotencyKey_unReintentoDevuelveLaMismaReservaSinApartarOtroTurno() throws Exception {
        when(citaService.esHorarioValido(eq(DOCTOR), any(), any())).thenReturn(true);
        Reserva r = new Reserva();
        r.setReferencia("BS-IDEM");
        r.setMontoCentavos(1_000_000);
        r.setExpiraEn(LocalDateTime.now().plusMinutes(15));
        when(reservaService.apartar(any(), any(), any(), any(), any())).thenReturn(r);
        String cuerpo = "{\"doctorId\":\"doc1\",\"fecha\":\"2030-01-07\",\"hora\":\"08:00\",\"motivo\":\"Control\"}";

        for (String repetido : new String[] {"false", "true"})
            mockMvc.perform(post(BASE + "/citas").header("Origin", ORIGEN).header("Authorization", bearer())
                            .header("Idempotency-Key", "clave-de-prueba-123")
                            .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Idempotent-Replayed", repetido))
                    .andExpect(jsonPath("$.referencia").value("BS-IDEM"));
        verify(reservaService, times(1)).apartar(any(), any(), any(), any(), any());

        // La misma clave con otro horario es un error, no otra reserva
        mockMvc.perform(post(BASE + "/citas").header("Origin", ORIGEN).header("Authorization", bearer())
                        .header("Idempotency-Key", "clave-de-prueba-123")
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo.replace("08:00", "09:00")))
                .andExpect(status().isUnprocessableEntity());
        verify(reservaService, times(1)).apartar(any(), any(), any(), any(), any());
    }

    @Test
    void reservar_conMedicoDeOtroConsultorio_404() throws Exception {
        mockMvc.perform(post(BASE + "/citas").header("Origin", ORIGEN).header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"doctorId\":\"docAjeno\",\"fecha\":\"2030-01-07\",\"hora\":\"08:00\",\"motivo\":\"x\"}"))
                .andExpect(status().isNotFound());
        verify(reservaService, never()).apartar(any(), any(), any(), any(), any());
    }

    @Test
    void retornoDeWompi_soloAlDominioDelConsultorio() {
        assertEquals("https://pequenospasos.com/agenda?bs_ref=BS-1",
                ConsultorioApiController.urlRetorno("https://pequenospasos.com/agenda#citas", ORIGEN, "BS-1"));
        assertEquals("https://pequenospasos.com/?bs_ref=BS-1",
                ConsultorioApiController.urlRetorno("https://besalud-falso.com/robar", ORIGEN, "BS-1"));
    }

    // ── Mis citas ───────────────────────────────────────────────────────────

    @Test
    void misCitas_soloLasDeEsteConsultorio() throws Exception {
        Cita propia = cita("c1", DOCTOR);
        Cita ajena = cita("c2", DOCTOR_AJENO);
        when(citaService.listarPaginado("pac1", null, 0, 100)).thenReturn(new PageImpl<>(List.of(propia, ajena)));

        mockMvc.perform(get(BASE + "/citas").header("Origin", ORIGEN).header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value("c1"))
                .andExpect(jsonPath("$[0].puedeCancelar").value(true));
    }

    @Test
    void cancelar_citaConMedicoDeOtroConsultorio_404() throws Exception {
        when(citaService.buscarPorId("c2")).thenReturn(Optional.of(cita("c2", DOCTOR_AJENO)));

        mockMvc.perform(post(BASE + "/citas/c2/cancelar").header("Origin", ORIGEN).header("Authorization", bearer()))
                .andExpect(status().isNotFound());
        verify(citaService, never()).cancelar(any(), anyBoolean());
    }

    @Test
    void cancelar_propia_avisaSiHayDevolucion() throws Exception {
        Cita c1 = cita("c1", DOCTOR);
        c1.setAbonoCentavos(1_000_000L);
        when(citaService.buscarPorId("c1")).thenReturn(Optional.of(c1));
        when(citaService.cancelaConAnticipacion(c1)).thenReturn(true);
        when(citaService.cancelar(c1, true)).thenReturn(true);

        mockMvc.perform(post(BASE + "/citas/c1/cancelar").header("Origin", ORIGEN).header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.devolucion").value(true));
    }

    private static Cita cita(String id, String doctorId) {
        Cita c = new Cita();
        c.setId(id);
        c.setDoctorId(doctorId);
        c.setPacienteId("pac1");
        c.setFecha(LocalDate.of(2030, 1, 7));
        c.setHora(LocalTime.of(8, 0));
        c.setMotivo("Control");
        return c;
    }
}
