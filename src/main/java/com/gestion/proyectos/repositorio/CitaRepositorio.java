package com.gestion.proyectos.repositorio;

import com.gestion.proyectos.modelo.Cita;
import org.springframework.data.mongodb.repository.MongoRepository;

// Repositorio para administrar las citas en MongoDB
public interface CitaRepositorio extends MongoRepository<Cita, String> {
}
