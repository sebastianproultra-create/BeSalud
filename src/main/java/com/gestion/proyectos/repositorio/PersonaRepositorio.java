package com.gestion.proyectos.repositorio;

import com.gestion.proyectos.modelo.Doctor;
import com.gestion.proyectos.modelo.Paciente;
import com.gestion.proyectos.modelo.Persona;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

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

    @Query("{ 'role': 'DOCTOR' }")
    Page<Doctor> findAllDoctores(Pageable pageable);

    @Query("{ 'role': 'PACIENTE' }")
    List<Paciente> findAllPacientes();

    @Query("{ 'role': 'PACIENTE' }")
    Page<Paciente> findAllPacientes(Pageable pageable);

    @Query("{ 'especialidad': { $regex: ?0, $options: 'i' }, 'role': 'DOCTOR' }")
    List<Doctor> findDoctoresByEspecialidadContainingIgnoreCase(String especialidad);

    @Query("{ 'especialidad': { $regex: ?0, $options: 'i' }, 'role': 'DOCTOR' }")
    Page<Doctor> findDoctoresByEspecialidadContainingIgnoreCase(String especialidad, Pageable pageable);

    @Query("{ $or: [ { 'nombre': { $regex: ?0, $options: 'i' } }, { 'apellido': { $regex: ?0, $options: 'i' } }, { 'email': { $regex: ?0, $options: 'i' } } ], 'role': 'DOCTOR' }")
    Page<Doctor> searchDoctoresByNombreApellidoEmail(String q, Pageable pageable);
}
