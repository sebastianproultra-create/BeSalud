package com.gestion.proyectos.servicio;

import com.gestion.proyectos.modelo.UserRegistrationDTO;
import com.gestion.proyectos.repositorio.AdminRepositorio;
import com.gestion.proyectos.repositorio.PersonaRepositorio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistroServiceTest {

    @Mock PersonaRepositorio personaRepo;
    @Mock AdminRepositorio adminRepo;
    @Mock PasswordEncoder passwordEncoder;

    @InjectMocks RegistroService service;

    private UserRegistrationDTO dto;

    @BeforeEach
    void setUp() {
        dto = new UserRegistrationDTO();
        dto.setNombre("Ana");
        dto.setApellido("López");
        dto.setTelefono("3001234567");
        dto.setIdentificacion("1234567890");
        dto.setEmail("ana@example.com");
        dto.setPassword("password123");
        dto.setRole("PACIENTE");
    }

    @Test
    void validar_telefonoNoEmpieza3_retornaError() {
        dto.setTelefono("1234567890");
        assertThat(service.validar(dto)).contains("empezar por 3");
    }

    @Test
    void validar_identificacionConLetras_retornaError() {
        dto.setIdentificacion("abc123");
        assertThat(service.validar(dto)).contains("solo números");
    }

    @Test
    void validar_especialidadFueraDeLista_retornaError() {
        dto.setRole("DOCTOR");
        dto.setFechaNacimiento("1990-01-01");
        dto.setEspecialidad("Cardiologia");
        assertThat(service.validar(dto)).contains("lista");
    }

    @Test
    void validar_telefonoCorto_retornaError() {
        dto.setTelefono("300123");
        assertThat(service.validar(dto)).contains("10 dígitos");
    }

    @Test
    void validar_rolInvalido_retornaError() {
        dto.setRole("ADMIN");
        assertThat(service.validar(dto)).contains("rol válido");
    }

    @Test
    void validar_doctorSinEspecialidad_retornaError() {
        dto.setRole("DOCTOR");
        dto.setFechaNacimiento("1990-01-01");
        assertThat(service.validar(dto)).contains("especialidad");
    }

    @Test
    void validar_doctorSinFechaNacimiento_retornaError() {
        dto.setRole("DOCTOR");
        dto.setEspecialidad("Cardiología");
        assertThat(service.validar(dto)).contains("fecha de nacimiento");
    }

    @Test
    void validar_pacienteValido_retornaNull() {
        assertThat(service.validar(dto)).isNull();
    }

    @Test
    void verificarDuplicado_emailExisteEnAdmin_retornaError() {
        when(adminRepo.findByEmail(anyString())).thenReturn(Optional.of(new com.gestion.proyectos.modelo.Admin()));
        assertThat(service.verificarDuplicado(dto)).contains("correo");
    }

    @Test
    void verificarDuplicado_emailNuevo_retornaNull() {
        when(adminRepo.findByEmail(anyString())).thenReturn(Optional.empty());
        when(personaRepo.findByEmail(anyString())).thenReturn(Optional.empty());
        assertThat(service.verificarDuplicado(dto)).isNull();
    }
}
