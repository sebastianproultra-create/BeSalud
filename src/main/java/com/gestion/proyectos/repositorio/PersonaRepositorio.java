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

    @Query("{ 'identificacion': ?0 }")
    Optional<Persona> findByIdentificacion(String identificacion);

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

    @Query("{ 'role': 'DOCTOR', 'estado': 'ACTIVO' }")
    List<Doctor> findDoctoresActivos();

    @Query("{ 'especialidad': { $regex: ?0, $options: 'i' }, 'role': 'DOCTOR' }")
    Page<Doctor> findDoctoresByEspecialidadContainingIgnoreCase(String especialidad, Pageable pageable);

    @Query("{ $or: [ { 'nombre': { $regex: ?0, $options: 'i' } }, { 'apellido': { $regex: ?0, $options: 'i' } }, { 'email': { $regex: ?0, $options: 'i' } } ], 'role': 'DOCTOR' }")
    Page<Doctor> searchDoctoresByNombreApellidoEmail(String q, Pageable pageable);

    @Query("{ $and: [ { 'nombre': { $regex: ?0, $options: 'i' } }, { 'apellido': { $regex: ?1, $options: 'i' } } ], 'role': 'DOCTOR' }")
    Page<Doctor> searchDoctoresByNombreYApellido(String nombre, String apellido, Pageable pageable);

    @Query("{ '_class': 'com.gestion.proyectos.modelo.Doctor', $and: [ " +
            "  { $or: [ { 'nombre': { $regex: ?0, $options: 'i' } }, { 'apellido': { $regex: ?0, $options: 'i' } }, { 'email': { $regex: ?0, $options: 'i' } } ] }, "
            +
            "  { 'especialidad': { $regex: ?1, $options: 'i' } } " +
            "] }")
    Page<Doctor> searchDoctoresCombinado(String search, String especialidad, Pageable pageable);
}
