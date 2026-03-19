package com.gestion.proyectos.repositorio;

import com.gestion.proyectos.modelo.Paciente;
import org.springframework.data.mongodb.repository.MongoRepository;

// Repositorio para administrar los pacientes en MongoDB
public interface PacienteRepositorio extends MongoRepository<Paciente, String> {
}
