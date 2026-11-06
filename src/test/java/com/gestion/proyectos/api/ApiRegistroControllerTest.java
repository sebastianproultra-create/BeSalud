package com.gestion.proyectos.api;

import com.gestion.proyectos.modelo.Consultorio;
import com.gestion.proyectos.modelo.PaseAcceso;
import com.gestion.proyectos.modelo.UserRegistrationDTO;
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
import com.gestion.proyectos.servicio.RegistroService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** API pública para webs de consultorios: registro por JSON y pase de acceso a la agenda. */
@WebMvcTest(controllers = ApiRegistroController.class, properties = "app.api-consultorios.habilitada=true")
@Import({SecurityConfig.class, com.gestion.proyectos.api.ApiSecurityConfig.class})
class ApiRegistroControllerTest {

    private static final String ORIGEN = "https://sanjuan.com";
    private static final String DOCTOR = "doc1";

    @Autowired private MockMvc mockMvc;
    @MockBean private RegistroService registroService;
    @MockBean private CitaService citaService;
    @MockBean private PaseAccesoService paseService;
    @MockBean private ConsultorioRepositorio consultorioRepo;
    @MockBean private PersonaRepositorio personaRepositorio;
    @MockBean private AdminRepositorio adminRepositorio;
    @MockBean private CustomUserDetailsService customUserDetailsService;
    @MockBean private JwtService jwtService;
    @MockBean private JwtCookieService jwtCookieService;
    @MockBean private ConsultorioCorsSource consultorioCorsSource;
    @MockBean private com.gestion.proyectos.servicio.ConsultorioService consultorioService; // IntegracionWebAdvice
    @MockBean private com.gestion.proyectos.servicio.SitioWebService sitioWebService; // IntegracionWebAdvice
    @MockBean private RateLimiter rateLimiter;
    @MockBean private com.gestion.proyectos.servicio.PacienteService pacienteService;
    @MockBean private AuthenticationManager authenticationManager;
    @MockBean private OAuth2LoginSuccessHandler oauth2LoginSuccessHandler;

    @BeforeEach
    void preparar() {
        when(rateLimiter.permitir(anyString(), anyInt(), any())).thenReturn(true);
        Consultorio c = new Consultorio();
        c.setId("san-juan");
        c.setDominios(List.of(ORIGEN));
        c.setDoctorIds(List.of(DOCTOR));
        when(consultorioRepo.findByIdAndActivoTrue("san-juan")).thenReturn(Optional.of(c));
        when(citaService.doctorActivo(DOCTOR)).thenReturn(true);
        when(paseService.emitir(anyString(), anyString())).thenReturn("PASE123");
    }

    private static String json(String doctorId) {
        return """
                {"nombre":"Ana","apellido":"Gómez","identificacion":"1102030405","telefono":"3001234567",
                 "email":"Ana@Correo.com","password":"Clave#2026x","aceptaPrivacidad":true,
                 "doctorId":"%s","consultorioId":"san-juan"}""".formatted(doctorId);
    }

    @Test
    void registro_valido_creaPacienteSinCsrfYDevuelveUrlDeAcceso() throws Exception {
        mockMvc.perform(post("/api/v1/pacientes").header("Origin", ORIGEN)
                        .contentType(MediaType.APPLICATION_JSON).content(json(DOCTOR)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.redirectUrl", containsString("/api/v1/acceso?t=PASE123")));

        ArgumentCaptor<UserRegistrationDTO> dto = ArgumentCaptor.forClass(UserRegistrationDTO.class);
        verify(registroService).registrar(dto.capture());
        assertEquals("PACIENTE", dto.getValue().getRole());
        verify(paseService).emitir("ana@correo.com", DOCTOR);
    }

    @Test
    void registro_desdeDominioAjeno_403() throws Exception {
        mockMvc.perform(post("/api/v1/pacientes").header("Origin", "https://otro.com")
                        .contentType(MediaType.APPLICATION_JSON).content(json(DOCTOR)))
                .andExpect(status().isForbidden());
        verify(registroService, never()).registrar(any());
    }

    @Test
    void registro_conDoctorDeOtroConsultorio_400() throws Exception {
        mockMvc.perform(post("/api/v1/pacientes").header("Origin", ORIGEN)
                        .contentType(MediaType.APPLICATION_JSON).content(json("docAjeno")))
                .andExpect(status().isBadRequest());
        verify(registroService, never()).registrar(any());
    }

    @Test
    void registro_duplicado_409ConLoginQueVuelveALaAgenda() throws Exception {
        when(registroService.verificarDuplicado(any())).thenReturn("Ya existe un usuario registrado con ese correo");

        mockMvc.perform(post("/api/v1/pacientes").header("Origin", ORIGEN)
                        .contentType(MediaType.APPLICATION_JSON).content(json(DOCTOR)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.loginUrl", containsString("/login?continuar=%2Fcitas%2Fnueva%3FdoctorId%3Ddoc1")));
        verify(registroService, never()).registrar(any());
    }

    @Test
    void acceso_conPaseValido_fijaCookieYRedirigeALaAgenda() throws Exception {
        PaseAcceso pase = new PaseAcceso();
        pase.setEmail("ana@correo.com");
        pase.setDoctorId(DOCTOR);
        when(paseService.consumir("PASE123")).thenReturn(Optional.of(pase));
        when(customUserDetailsService.loadUserByUsername("ana@correo.com"))
                .thenReturn(User.withUsername("ana@correo.com").password("x").roles("PACIENTE").build());
        when(jwtService.generateToken(any())).thenReturn("jwt");

        mockMvc.perform(get("/api/v1/acceso").param("t", "PASE123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/citas/nueva?doctorId=" + DOCTOR));
        verify(jwtCookieService).addJwtCookie(any(), eq("jwt"));
    }

    @Test
    void acceso_conPaseVencido_vaAlLogin() throws Exception {
        when(paseService.consumir("viejo")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/acceso").param("t", "viejo"))
                .andExpect(redirectedUrl("/login?enlaceVencido=true"));
        verify(jwtCookieService, never()).addJwtCookie(any(), any());
    }

    @Test
    @WithMockUser(roles = "PACIENTE")
    void agendar_conSesionDePaciente_vaDirectoALaAgenda() throws Exception {
        mockMvc.perform(get("/api/v1/agendar").param("doctorId", DOCTOR))
                .andExpect(redirectedUrl("/citas/nueva?doctorId=" + DOCTOR));
    }

    @Test
    void agendar_sinSesion_vaAlLoginYVuelveALaAgenda() throws Exception {
        mockMvc.perform(get("/api/v1/agendar").param("doctorId", DOCTOR))
                .andExpect(redirectedUrl("/login?continuar=%2Fcitas%2Fnueva%3FdoctorId%3D" + DOCTOR));
    }

    @Test
    void agendar_conDoctorInvalido_vaAlInicio() throws Exception {
        mockMvc.perform(get("/api/v1/agendar").param("doctorId", "../evil"))
                .andExpect(redirectedUrl("/"));
    }

    private static String jsonSesion(String password) {
        return """
                {"email":"Ana@Correo.com","password":"%s","doctorId":"doc1","consultorioId":"san-juan"}"""
                .formatted(password);
    }

    private static UsernamePasswordAuthenticationToken autenticado(String rol) {
        return new UsernamePasswordAuthenticationToken("ana@correo.com", null,
                List.of(new SimpleGrantedAuthority("ROLE_" + rol)));
    }

    @Test
    void sesion_valida_devuelveUrlDeAccesoALaAgenda() throws Exception {
        when(authenticationManager.authenticate(any())).thenReturn(autenticado("PACIENTE"));

        mockMvc.perform(post("/api/v1/sesion").header("Origin", ORIGEN)
                        .contentType(MediaType.APPLICATION_JSON).content(jsonSesion("Clave#2026x")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redirectUrl", containsString("/api/v1/acceso?t=PASE123")));
        verify(paseService).emitir("ana@correo.com", DOCTOR);
        verify(rateLimiter).reiniciar(anyString());
    }

    @Test
    void sesion_claveIncorrecta_401YCuentaElFallo() throws Exception {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("x"));

        mockMvc.perform(post("/api/v1/sesion").header("Origin", ORIGEN)
                        .contentType(MediaType.APPLICATION_JSON).content(jsonSesion("mala")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Correo o contraseña incorrectos"));
        verify(rateLimiter).registrar(anyString(), any());
        verify(paseService, never()).emitir(anyString(), anyString());
    }

    @Test
    void sesion_cuentaDeDoctor_403() throws Exception {
        when(authenticationManager.authenticate(any())).thenReturn(autenticado("DOCTOR"));

        mockMvc.perform(post("/api/v1/sesion").header("Origin", ORIGEN)
                        .contentType(MediaType.APPLICATION_JSON).content(jsonSesion("Clave#2026x")))
                .andExpect(status().isForbidden());
        verify(paseService, never()).emitir(anyString(), anyString());
    }

    @Test
    void sesion_bloqueadaPorFallos_429SinIntentarAutenticar() throws Exception {
        when(rateLimiter.bloqueado(anyString(), anyInt(), any())).thenReturn(true);

        mockMvc.perform(post("/api/v1/sesion").header("Origin", ORIGEN)
                        .contentType(MediaType.APPLICATION_JSON).content(jsonSesion("Clave#2026x")))
                .andExpect(status().isTooManyRequests());
        verify(authenticationManager, never()).authenticate(any());
    }

    @Test
    void sesion_desdeDominioAjeno_403() throws Exception {
        mockMvc.perform(post("/api/v1/sesion").header("Origin", "https://otro.com")
                        .contentType(MediaType.APPLICATION_JSON).content(jsonSesion("Clave#2026x")))
                .andExpect(status().isForbidden());
        verify(authenticationManager, never()).authenticate(any());
    }
}
