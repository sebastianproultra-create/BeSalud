package com.gestion.proyectos.config;

import com.gestion.proyectos.modelo.Admin;
import com.gestion.proyectos.modelo.Doctor;
import com.gestion.proyectos.modelo.Paciente;
import com.gestion.proyectos.repositorio.AdminRepositorio;
import com.gestion.proyectos.repositorio.PersonaRepositorio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.Base64;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final AdminRepositorio adminRepositorio;
    private final PersonaRepositorio personaRepositorio;
    private final PasswordEncoder passwordEncoder;

    @Value("${besalud.admin.email:admin@besalud.com}")
    private String defaultAdminEmail;

    // Vacía: el admin nace con una contraseña aleatoria que se muestra una sola vez en el log
    @Value("${besalud.admin.password:}")
    private String defaultAdminPassword;

    @Value("${besalud.doctor.password:seba1234}")
    private String defaultDoctorPassword;

    @Value("${besalud.paciente.password:paciente1234}")
    private String defaultPacientePassword;

    // Cuentas de prueba (doctor y paciente) con contraseñas conocidas: solo para desarrollo o una demo.
    // En producción quedan apagadas para no dejar una puerta trasera.
    @Value("${besalud.demo.usuarios:false}")
    private boolean crearUsuariosDemo;

    public DataInitializer(AdminRepositorio adminRepositorio, PersonaRepositorio personaRepositorio,
            PasswordEncoder passwordEncoder) {
        this.adminRepositorio = adminRepositorio;
        this.personaRepositorio = personaRepositorio;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) throws Exception {
        String email = defaultAdminEmail.trim().toLowerCase();
        if (adminRepositorio.findByEmail(email).isEmpty()) {
            Admin admin = new Admin();
            admin.setNombre("Administrador");
            admin.setEmail(email);
            boolean generada = defaultAdminPassword == null || defaultAdminPassword.isBlank();
            String clave = generada ? claveAleatoria() : defaultAdminPassword;
            admin.setPassword(passwordEncoder.encode(clave));
            adminRepositorio.save(admin);
            log.info("Usuario admin creado con email: {}", email);
            if (generada)
                log.warn("Contraseña inicial del admin (se muestra solo esta vez, cámbiala): {}", clave);
        }

        if (!crearUsuariosDemo)
            return;

        // Solo se crean si faltan: nunca se les vuelve a poner la contraseña de prueba, así quien la
        // haya cambiado no la pierde en el siguiente arranque. Las contraseñas no van al log.
        String doctorEmail = "sebastianfontalvoayola27@gmail.com";
        if (personaRepositorio.findDoctorByEmail(doctorEmail).isEmpty()) {
            Doctor doctor = new Doctor("Sebastián", "Fontalvo", "3001234567", "1234567890",
                    doctorEmail, passwordEncoder.encode(defaultDoctorPassword),
                    "Medicina General", LocalDate.of(1990, 1, 1), null, null);
            personaRepositorio.save(doctor);
            log.info("Doctor de prueba creado: {}", doctorEmail);
        }

        String pacienteEmail = "paciente@besalud.com";
        if (personaRepositorio.findPacienteByEmail(pacienteEmail).isEmpty()) {
            Paciente paciente = new Paciente("Paciente", "Prueba", "3009876543", "9876543210",
                    pacienteEmail, passwordEncoder.encode(defaultPacientePassword));
            personaRepositorio.save(paciente);
            log.info("Paciente de prueba creado: {}", pacienteEmail);
        }
    }

    private static String claveAleatoria() {
        byte[] bytes = new byte[12];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
