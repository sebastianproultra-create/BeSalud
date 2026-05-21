package com.gestion.proyectos.repositorio;

import com.gestion.proyectos.modelo.Doctor;
import com.gestion.proyectos.modelo.Paciente;
import com.gestion.proyectos.modelo.Persona;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PersonaRepositorio extends MongoRepository<Persona, String> {

    @Query("{ 'email': ?0 }")
    Optional<Persona> findByEmail(String email);

    @Query("{ 'email': ?0, 'role': 'DOCTOR' }")
    Optional<Doctor> findDoctorByEmail(String email);

    @Query("{ 'email': ?0, 'role': 'PACIENTE' }")
    Optional<Paciente> findPacienteByEmail(String email);

    @Query("{ 'role': 'DOCTOR' }")
    List<Doctor> findAllDoctores();

    @Query("{ 'role': 'PACIENTE' }")
    List<Paciente> findAllPacientes();

    @Query("{ 'especialidad': { $regex: ?0, $options: 'i' }, 'role': 'DOCTOR' }")
    List<Doctor> findDoctoresByEspecialidadContainingIgnoreCase(String especialidad);
}
