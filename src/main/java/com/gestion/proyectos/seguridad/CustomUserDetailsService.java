package com.gestion.proyectos.seguridad;

import com.gestion.proyectos.modelo.Admin;
import com.gestion.proyectos.modelo.Doctor;
import com.gestion.proyectos.modelo.Paciente;
import com.gestion.proyectos.repositorio.AdminRepositorio;
import com.gestion.proyectos.repositorio.DoctorRepositorio;
import com.gestion.proyectos.repositorio.PacienteRepositorio;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final PacienteRepositorio pacienteRepositorio;
    private final DoctorRepositorio doctorRepositorio;
    private final AdminRepositorio adminRepositorio;

    public CustomUserDetailsService(PacienteRepositorio pacienteRepositorio, DoctorRepositorio doctorRepositorio,
            AdminRepositorio adminRepositorio) {
        this.pacienteRepositorio = pacienteRepositorio;
        this.doctorRepositorio = doctorRepositorio;
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

        Doctor doctor = doctorRepositorio.findByEmail(email).orElse(null);
        Paciente paciente = pacienteRepositorio.findByEmail(email).orElse(null);

        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        String password = null;
        String resolvedEmail = null;

        if (doctor != null) {
            authorities.add(new SimpleGrantedAuthority("ROLE_DOCTOR"));
            password = doctor.getPassword();
            resolvedEmail = doctor.getEmail();
        }
        if (paciente != null) {
            authorities.add(new SimpleGrantedAuthority("ROLE_PACIENTE"));
            if (password == null) {
                password = paciente.getPassword();
                resolvedEmail = paciente.getEmail();
            }
        }
        if (!authorities.isEmpty()) {
            return new User(resolvedEmail, password, authorities);
        }

        throw new UsernameNotFoundException("Usuario no encontrado: " + email);
    }
}
