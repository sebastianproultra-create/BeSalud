package com.gestion.proyectos.repositorio;

import com.gestion.proyectos.modelo.Paciente;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

// Repositorio para administrar los pacientes en MongoDB
public interface PacienteRepositorio extends MongoRepository<Paciente, String> {
    @Query("{ 'email': ?0, 'role': 'PACIENTE' }")
    java.util.Optional<Paciente> findByEmail(String email);

    @Query("{ 'role': 'PACIENTE' }")
    java.util.List<Paciente> findAll();
}
