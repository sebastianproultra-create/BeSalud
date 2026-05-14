package com.gestion.proyectos.config;

import com.gestion.proyectos.modelo.Role;
import com.gestion.proyectos.modelo.User;
import com.gestion.proyectos.repositorio.RoleRepository;
import com.gestion.proyectos.repositorio.UserRepository;
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

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${besalud.admin.email:admin@besalud.com}")
    private String defaultAdminEmail;

    @Value("${besalud.admin.password:admin12345}")
    private String defaultAdminPassword;

    @Value("${besalud.doctor.password:seba1234}")
    private String defaultDoctorPassword;

    @Value("${besalud.paciente.password:paciente1234}")
    private String defaultPacientePassword;

    public DataInitializer(RoleRepository roleRepository, UserRepository userRepository,
            PasswordEncoder passwordEncoder) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) throws Exception {
        // Crear roles por defecto
        Role adminRole = roleRepository.findByName("ROLE_ADMIN").orElseGet(() -> {
            Role role = new Role("ROLE_ADMIN");
            return roleRepository.save(role);
        });

        Role doctorRole = roleRepository.findByName("ROLE_DOCTOR").orElseGet(() -> {
            Role role = new Role("ROLE_DOCTOR");
            return roleRepository.save(role);
        });

        Role pacienteRole = roleRepository.findByName("ROLE_PACIENTE").orElseGet(() -> {
            Role role = new Role("ROLE_PACIENTE");
            return roleRepository.save(role);
        });

        // Crear admin por defecto
        String email = defaultAdminEmail.trim().toLowerCase();
        if (userRepository.findByEmail(email).isEmpty()) {
            User admin = new User();
            admin.setNombre("Administrador");
            admin.setApellido("Sistema");
            admin.setEmail(email);
            admin.setPassword(passwordEncoder.encode(defaultAdminPassword));
            admin.addRole(adminRole);
            userRepository.save(admin);
            log.info("Usuario admin creado con email: {}", email);
            log.warn(
                    "Contraseña por defecto del admin en uso. Cámbiala en producción vía besalud.admin.password o variable de entorno.");
        }

        // Crear doctor de prueba
        String doctorEmail = "sebastianfontalvoayola27@gmail.com";
        String doctorPassword = defaultDoctorPassword;
        Optional<User> existingDoctor = userRepository.findByEmail(doctorEmail);
        if (existingDoctor.isEmpty()) {
            User doctor = new User();
            doctor.setNombre("Sebastián");
            doctor.setApellido("Fontalvo");
            doctor.setTelefono("3001234567");
            doctor.setIdentificacion("1234567890");
            doctor.setEmail(doctorEmail);
            doctor.setPassword(passwordEncoder.encode(doctorPassword));
            doctor.setEspecialidad("Medicina General");
            doctor.setFechaNacimiento(LocalDate.of(1990, 1, 1));
            doctor.addRole(doctorRole);
            doctor.addRole(pacienteRole);
            userRepository.save(doctor);
            log.info("Doctor de prueba creado: {} / contraseña: {}", doctorEmail, doctorPassword);
        } else {
            User doctor = existingDoctor.get();
            doctor.setPassword(passwordEncoder.encode(doctorPassword));
            userRepository.save(doctor);
            log.info("Contraseña del doctor {} actualizada. Contraseña: {}", doctorEmail, doctorPassword);
        }

        // Crear paciente de prueba
        String pacienteEmail = "paciente@besalud.com";
        String pacientePassword = defaultPacientePassword;
        Optional<User> existingPaciente = userRepository.findByEmail(pacienteEmail);
        if (existingPaciente.isEmpty()) {
            User paciente = new User();
            paciente.setNombre("Paciente");
            paciente.setApellido("Prueba");
            paciente.setTelefono("3009876543");
            paciente.setIdentificacion("9876543210");
            paciente.setEmail(pacienteEmail);
            paciente.setPassword(passwordEncoder.encode(pacientePassword));
            paciente.addRole(pacienteRole);
            userRepository.save(paciente);
            log.info("Paciente de prueba creado: {} / contraseña: {}", pacienteEmail, pacientePassword);
        } else {
            User paciente = existingPaciente.get();
            paciente.setPassword(passwordEncoder.encode(pacientePassword));
            userRepository.save(paciente);
            log.info("Contraseña del paciente {} actualizada. Contraseña: {}", pacienteEmail, pacientePassword);
        }
    }
}
