package com.gestion.proyectos.repositorio;

import com.gestion.proyectos.modelo.Doctor;
import org.springframework.data.mongodb.repository.MongoRepository;

// Repositorio para administrar los doctores en MongoDB
public interface DoctorRepositorio extends MongoRepository<Doctor, String> {
}
