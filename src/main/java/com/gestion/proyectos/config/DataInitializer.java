package com.gestion.proyectos.config;

import com.gestion.proyectos.modelo.Admin;
import com.gestion.proyectos.modelo.Doctor;
import com.gestion.proyectos.modelo.Paciente;
import com.gestion.proyectos.repositorio.AdminRepositorio;
import com.gestion.proyectos.repositorio.DoctorRepositorio;
import com.gestion.proyectos.repositorio.PacienteRepositorio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final AdminRepositorio adminRepositorio;
    private final DoctorRepositorio doctorRepositorio;
    private final PacienteRepositorio pacienteRepositorio;
    private final PasswordEncoder passwordEncoder;

    @Value("${besalud.admin.email:admin@besalud.com}")
    private String defaultAdminEmail;

    @Value("${besalud.admin.password:admin12345}")
    private String defaultAdminPassword;

    @Value("${besalud.doctor.password:seba1234}")
    private String defaultDoctorPassword;

    @Value("${besalud.paciente.password:paciente1234}")
    private String defaultPacientePassword;

    public DataInitializer(AdminRepositorio adminRepositorio, DoctorRepositorio doctorRepositorio,
            PacienteRepositorio pacienteRepositorio, PasswordEncoder passwordEncoder) {
        this.adminRepositorio = adminRepositorio;
        this.doctorRepositorio = doctorRepositorio;
        this.pacienteRepositorio = pacienteRepositorio;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) throws Exception {
        String email = defaultAdminEmail.trim().toLowerCase();
        if (adminRepositorio.findByEmail(email).isEmpty()) {
            Admin admin = new Admin();
            admin.setNombre("Administrador");
            admin.setEmail(email);
            admin.setPassword(passwordEncoder.encode(defaultAdminPassword));
            adminRepositorio.save(admin);
            log.info("Usuario admin creado con email: {}", email);
            log.warn("Contraseña por defecto del admin en uso. Cámbiala en producción vía besalud.admin.password o variable de entorno.");
        }

        // Ensure test doctor exists with BCrypt-encoded password
        String doctorEmail = "sebastianfontalvoayola27@gmail.com";
        String doctorPassword = defaultDoctorPassword;
        Optional<Doctor> existingDoctor = doctorRepositorio.findByEmail(doctorEmail);
        if (existingDoctor.isEmpty()) {
            Doctor doctor = new Doctor("Sebastián", "Fontalvo", "3001234567", "1234567890",
                    doctorEmail, passwordEncoder.encode(doctorPassword),
                    "Medicina General", LocalDate.of(1990, 1, 1), null, null);
            doctorRepositorio.save(doctor);
            log.info("Doctor de prueba creado: {} / contraseña: {}", doctorEmail, doctorPassword);
        } else {
            Doctor doctor = existingDoctor.get();
            doctor.setPassword(passwordEncoder.encode(doctorPassword));
            doctorRepositorio.save(doctor);
            log.info("Contraseña del doctor {} actualizada. Contraseña: {}", doctorEmail, doctorPassword);
        }

        // Ensure test patient exists
        String pacienteEmail = "paciente@besalud.com";
        String pacientePassword = defaultPacientePassword;
        Optional<Paciente> existingPaciente = pacienteRepositorio.findByEmail(pacienteEmail);
        if (existingPaciente.isEmpty()) {
            Paciente paciente = new Paciente("Paciente", "Prueba", "3009876543", "9876543210",
                    pacienteEmail, passwordEncoder.encode(pacientePassword));
            pacienteRepositorio.save(paciente);
            log.info("Paciente de prueba creado: {} / contraseña: {}", pacienteEmail, pacientePassword);
        } else {
            Paciente paciente = existingPaciente.get();
            paciente.setPassword(passwordEncoder.encode(pacientePassword));
            pacienteRepositorio.save(paciente);
            log.info("Contraseña del paciente {} actualizada. Contraseña: {}", pacienteEmail, pacientePassword);
        }
    }
}
