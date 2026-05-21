package com.gestion.proyectos.seguridad;

import com.gestion.proyectos.modelo.Admin;
import com.gestion.proyectos.modelo.Doctor;
import com.gestion.proyectos.modelo.Paciente;
import com.gestion.proyectos.repositorio.AdminRepositorio;
import com.gestion.proyectos.repositorio.PersonaRepositorio;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final PersonaRepositorio personaRepositorio;
    private final AdminRepositorio adminRepositorio;

    public CustomUserDetailsService(PersonaRepositorio personaRepositorio,
            AdminRepositorio adminRepositorio) {
        this.personaRepositorio = personaRepositorio;
        this.adminRepositorio = adminRepositorio;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        if (username == null) {
            throw new UsernameNotFoundException("Usuario no encontrado");
        }
        String email = username.trim().toLowerCase();

        Admin admin = adminRepositorio.findByEmail(email).orElse(null);
        if (admin != null) {
            return new User(admin.getEmail(), admin.getPassword(),
                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN")));
        }

        Paciente paciente = personaRepositorio.findPacienteByEmail(email).orElse(null);
        if (paciente != null) {
            return new User(paciente.getEmail(), paciente.getPassword(),
                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_PACIENTE")));
        }

        Doctor doctor = personaRepositorio.findDoctorByEmail(email).orElse(null);
        if (doctor != null) {
            return new User(doctor.getEmail(), doctor.getPassword(),
                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_DOCTOR")));
        }

        throw new UsernameNotFoundException("Usuario no encontrado: " + email);
    }
}
