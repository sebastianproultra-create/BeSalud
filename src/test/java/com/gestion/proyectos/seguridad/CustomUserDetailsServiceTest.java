package com.gestion.proyectos.seguridad;

import com.gestion.proyectos.modelo.Admin;
import com.gestion.proyectos.modelo.Doctor;
import com.gestion.proyectos.modelo.Paciente;
import com.gestion.proyectos.repositorio.AdminRepositorio;
import com.gestion.proyectos.repositorio.DoctorRepositorio;
import com.gestion.proyectos.repositorio.PacienteRepositorio;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private PacienteRepositorio pacienteRepositorio;

    @Mock
    private DoctorRepositorio doctorRepositorio;

    @Mock
    private AdminRepositorio adminRepositorio;

    @InjectMocks
    private CustomUserDetailsService userDetailsService;

    @Test
    void loadAdmin_retornaRoleAdmin() {
        Admin admin = new Admin();
        admin.setEmail("admin@test.com");
        admin.setPassword("encoded");
        when(adminRepositorio.findByEmail("admin@test.com")).thenReturn(Optional.of(admin));

        UserDetails details = userDetailsService.loadUserByUsername("admin@test.com");

        assertEquals("admin@test.com", details.getUsername());
        assertTrue(details.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
    }

    @Test
    void loadPaciente_retornaRolePaciente() {
        Paciente paciente = new Paciente();
        paciente.setEmail("paciente@test.com");
        paciente.setPassword("encoded");
        when(adminRepositorio.findByEmail("paciente@test.com")).thenReturn(Optional.empty());
        when(pacienteRepositorio.findByEmail("paciente@test.com")).thenReturn(Optional.of(paciente));

        UserDetails details = userDetailsService.loadUserByUsername("paciente@test.com");

        assertEquals("paciente@test.com", details.getUsername());
        assertTrue(details.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_PACIENTE")));
    }

    @Test
    void loadDoctor_retornaRoleDoctor() {
        Doctor doctor = new Doctor();
        doctor.setEmail("doctor@test.com");
        doctor.setPassword("encoded");
        when(adminRepositorio.findByEmail("doctor@test.com")).thenReturn(Optional.empty());
        when(pacienteRepositorio.findByEmail("doctor@test.com")).thenReturn(Optional.empty());
        when(doctorRepositorio.findByEmail("doctor@test.com")).thenReturn(Optional.of(doctor));

        UserDetails details = userDetailsService.loadUserByUsername("doctor@test.com");

        assertEquals("doctor@test.com", details.getUsername());
        assertTrue(details.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_DOCTOR")));
    }

    @Test
    void loadUsuarioInexistente_lanzaExcepcion() {
        when(adminRepositorio.findByEmail("nadie@test.com")).thenReturn(Optional.empty());
        when(pacienteRepositorio.findByEmail("nadie@test.com")).thenReturn(Optional.empty());
        when(doctorRepositorio.findByEmail("nadie@test.com")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class,
                () -> userDetailsService.loadUserByUsername("nadie@test.com"));
    }
}
