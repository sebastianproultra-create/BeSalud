package com.gestion.proyectos.repositorio;

import com.gestion.proyectos.modelo.Doctor;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

// Repositorio para administrar los doctores en MongoDB
public interface DoctorRepositorio extends MongoRepository<Doctor, String> {
    @Query("{ 'email': ?0, 'role': 'DOCTOR' }")
    java.util.Optional<Doctor> findByEmail(String email);

    @Query("{ 'role': 'DOCTOR' }")
    java.util.List<Doctor> findAllDoctores();

    @Query("{ 'especialidad': { $regex: ?0, $options: 'i' }, 'role': 'DOCTOR' }")
    java.util.List<Doctor> findByEspecialidadContainingIgnoreCase(String especialidad);
}
