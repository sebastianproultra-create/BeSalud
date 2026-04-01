package com.gestion.proyectos.config;

import com.gestion.proyectos.modelo.Admin;
import com.gestion.proyectos.repositorio.AdminRepositorio;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private final AdminRepositorio adminRepositorio;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(AdminRepositorio adminRepositorio, PasswordEncoder passwordEncoder) {
        this.adminRepositorio = adminRepositorio;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) throws Exception {
        // Crear usuario admin por defecto si no existe
        if (adminRepositorio.findByEmail("admin").isEmpty()) {
            Admin admin = new Admin();
            admin.setNombre("Administrador");
            admin.setEmail("admin");
            admin.setPassword(passwordEncoder.encode("admin"));
            adminRepositorio.save(admin);
            System.out.println("Usuario admin creado con email: admin y contraseña: admin");
        }
    }
}