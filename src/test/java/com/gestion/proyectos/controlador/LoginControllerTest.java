package com.gestion.proyectos.controlador;

import com.gestion.proyectos.seguridad.RateLimiter;
import com.gestion.proyectos.oauth2.OAuth2LoginSuccessHandler;
import com.gestion.proyectos.servicio.DoctorService;
import com.gestion.proyectos.repositorio.AdminRepositorio;
import com.gestion.proyectos.repositorio.PersonaRepositorio;
import com.gestion.proyectos.seguridad.CustomUserDetailsService;
import com.gestion.proyectos.seguridad.JwtCookieService;
import com.gestion.proyectos.seguridad.JwtService;
import com.gestion.proyectos.servicio.RegistroService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.gestion.proyectos.seguridad.SecurityConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LoginController.class)
@Import(SecurityConfig.class)
class LoginControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RateLimiter rateLimiter;

    @BeforeEach
    void permitirPeticiones() {
        when(rateLimiter.permitir(anyString(), anyInt(), any())).thenReturn(true);
    }

    @MockBean
    private PersonaRepositorio personaRepositorio;

    @MockBean
    private AdminRepositorio adminRepositorio;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @MockBean
    private RegistroService registroService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private JwtCookieService jwtCookieService;

    @MockBean
    private DoctorService doctorService;

    @MockBean
    private OAuth2LoginSuccessHandler oauth2LoginSuccessHandler;

    @MockBean
    private AuthenticationManager authenticationManager;

    @Test
    void login_conDemasiadosFallos_redirigeBloqueadoSinAutenticar() throws Exception {
        when(rateLimiter.bloqueado(anyString(), anyInt(), any())).thenReturn(true);

        mockMvc.perform(post("/login")
                        .param("email", "victima@test.com")
                        .param("password", "x")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?bloqueado=true"));
        verify(authenticationManager, never()).authenticate(any());
    }

    @Test
    void login_credencialesIncorrectas_registraElFallo() throws Exception {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("mal"));

        mockMvc.perform(post("/login")
                        .param("email", "Usuario@Test.com")
                        .param("password", "x")
                        .with(csrf()))
                .andExpect(redirectedUrl("/login?error=true"));
        verify(rateLimiter).registrar(eq("login:127.0.0.1:usuario@test.com"), any());
    }

    @Test
    void loginPage_retornaVistaLogin() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"));
    }

    @Test
    void registerPage_retornaVistaRegister() throws Exception {
        mockMvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attributeExists("user"));
    }

    @Test
    void registerSave_emailDuplicado_muestraError() throws Exception {
        when(registroService.validar(any())).thenReturn(null);
        when(registroService.verificarDuplicado(any())).thenReturn("Ya existe un usuario registrado con ese correo");

        mockMvc.perform(post("/register/save")
                        .param("email", "dup@correo.com")
                        .param("nombre", "Juan")
                        .param("apellido", "Perez")
                        .param("telefono", "3001234567")
                        .param("identificacion", "123456")
                        .param("password", "pass123")
                        .param("role", "PACIENTE")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attributeExists("error"));
    }

    @Test
    void registerSave_sinCsrf_retorna403() throws Exception {
        mockMvc.perform(post("/register/save")
                        .param("email", "test@correo.com")
                        .param("role", "PACIENTE"))
                .andExpect(status().isForbidden());
    }

    @Test
    void registerSave_camposVacios_muestraError() throws Exception {
        mockMvc.perform(post("/register/save")
                        .param("nombre", "")
                        .param("email", "test@correo.com")
                        .param("role", "PACIENTE")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attributeExists("error"));
    }

    @Test
    void registerSave_emailInvalido_muestraError() throws Exception {
        mockMvc.perform(post("/register/save")
                        .param("nombre", "Juan")
                        .param("apellido", "Perez")
                        .param("telefono", "123")
                        .param("identificacion", "456")
                        .param("email", "no-es-un-email")
                        .param("password", "password123")
                        .param("role", "PACIENTE")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attributeExists("error"));
    }

    @Test
    void registerSave_passwordCorta_muestraError() throws Exception {
        mockMvc.perform(post("/register/save")
                        .param("nombre", "Juan")
                        .param("apellido", "Perez")
                        .param("telefono", "123")
                        .param("identificacion", "456")
                        .param("email", "juan@correo.com")
                        .param("password", "corta")
                        .param("role", "PACIENTE")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attributeExists("error"));
    }

    @Test
    void registerSave_rolInvalido_muestraError() throws Exception {
        when(registroService.validar(any())).thenReturn("Debe seleccionar un rol válido (Paciente o Doctor)");

        mockMvc.perform(post("/register/save")
                        .param("nombre", "Juan")
                        .param("apellido", "Perez")
                        .param("telefono", "123")
                        .param("identificacion", "456")
                        .param("email", "juan@correo.com")
                        .param("password", "password123")
                        .param("role", "ADMIN")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attributeExists("error"));
    }

    @Test
    void registerSave_doctorSinEspecialidad_muestraError() throws Exception {
        when(registroService.validar(any())).thenReturn("La especialidad es obligatoria para doctores");

        mockMvc.perform(post("/register/save")
                        .param("nombre", "Carlos")
                        .param("apellido", "Gomez")
                        .param("telefono", "123")
                        .param("identificacion", "456")
                        .param("email", "doctor@correo.com")
                        .param("password", "password123")
                        .param("role", "DOCTOR")
                        .param("especialidad", "")
                        .param("fechaNacimiento", "1980-01-01")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attributeExists("error"));
    }

    @Test
    void registerSave_doctorFechaNacimientoInvalida_muestraError() throws Exception {
        when(registroService.validar(any())).thenReturn(null);
        when(registroService.verificarDuplicado(any())).thenReturn(null);
        when(registroService.registrar(any())).thenReturn("La fecha de nacimiento no tiene un formato válido (YYYY-MM-DD)");

        mockMvc.perform(post("/register/save")
                        .param("nombre", "Carlos")
                        .param("apellido", "Gomez")
                        .param("telefono", "123")
                        .param("identificacion", "456")
                        .param("email", "doctor@correo.com")
                        .param("password", "password123")
                        .param("role", "DOCTOR")
                        .param("especialidad", "Cardiologia")
                        .param("fechaNacimiento", "no-es-fecha")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attributeExists("error"));
    }

    @Test
    void registerSave_nuevoPaciente_redirigeTLogin() throws Exception {
        when(registroService.validar(any())).thenReturn(null);
        when(registroService.verificarDuplicado(any())).thenReturn(null);
        when(registroService.registrar(any())).thenReturn(null);

        mockMvc.perform(post("/register/save")
                        .param("email", "nuevo@correo.com")
                        .param("nombre", "Maria")
                        .param("apellido", "Lopez")
                        .param("telefono", "3009876543")
                        .param("identificacion", "654321")
                        .param("password", "password456")
                        .param("role", "PACIENTE")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?registerSuccess"));
    }

    @Test
    void registerSave_nuevoDoctor_redirigeTLogin() throws Exception {
        when(registroService.validar(any())).thenReturn(null);
        when(registroService.verificarDuplicado(any())).thenReturn(null);
        when(registroService.registrar(any())).thenReturn(null);

        mockMvc.perform(post("/register/save")
                        .param("email", "doctor@correo.com")
                        .param("nombre", "Carlos")
                        .param("apellido", "Gomez")
                        .param("telefono", "3001112233")
                        .param("identificacion", "789012")
                        .param("password", "docpass123")
                        .param("role", "DOCTOR")
                        .param("especialidad", "Cardiologia")
                        .param("fechaNacimiento", "1980-05-15")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?registerSuccess"));
    }
}
