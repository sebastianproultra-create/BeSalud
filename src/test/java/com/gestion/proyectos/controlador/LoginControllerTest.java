package com.gestion.proyectos.controlador;

import com.gestion.proyectos.modelo.Paciente;
import com.gestion.proyectos.repositorio.DoctorRepositorio;
import com.gestion.proyectos.repositorio.PacienteRepositorio;
import com.gestion.proyectos.seguridad.CustomUserDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LoginController.class)
class LoginControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PacienteRepositorio pacienteRepositorio;

    @MockBean
    private DoctorRepositorio doctorRepositorio;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

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
        when(pacienteRepositorio.findByEmail("dup@correo.com"))
                .thenReturn(Optional.of(new Paciente()));

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
        when(pacienteRepositorio.findByEmail(anyString())).thenReturn(java.util.Optional.empty());
        when(doctorRepositorio.findByEmail(anyString())).thenReturn(java.util.Optional.empty());

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
        when(pacienteRepositorio.findByEmail(anyString())).thenReturn(java.util.Optional.empty());
        when(doctorRepositorio.findByEmail(anyString())).thenReturn(java.util.Optional.empty());

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
        when(pacienteRepositorio.findByEmail(anyString())).thenReturn(Optional.empty());
        when(doctorRepositorio.findByEmail(anyString())).thenReturn(Optional.empty());

        mockMvc.perform(post("/register/save")
                        .param("email", "nuevo@correo.com")
                        .param("nombre", "Maria")
                        .param("apellido", "Lopez")
                        .param("telefono", "3009876543")
                        .param("identificacion", "654321")
                        .param("password", "pass456")
                        .param("role", "PACIENTE")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?registerSuccess"));
    }

    @Test
    void registerSave_nuevoDoctor_redirigeTLogin() throws Exception {
        when(pacienteRepositorio.findByEmail(anyString())).thenReturn(Optional.empty());
        when(doctorRepositorio.findByEmail(anyString())).thenReturn(Optional.empty());

        mockMvc.perform(post("/register/save")
                        .param("email", "doctor@correo.com")
                        .param("nombre", "Carlos")
                        .param("apellido", "Gomez")
                        .param("telefono", "3001112233")
                        .param("identificacion", "789012")
                        .param("password", "docpass")
                        .param("role", "DOCTOR")
                        .param("especialidad", "Cardiologia")
                        .param("fechaNacimiento", "1980-05-15")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?registerSuccess"));
    }
}
