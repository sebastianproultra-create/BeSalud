package com.gestion.proyectos.repositorio;

import com.gestion.proyectos.modelo.Cita;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

// Repositorio para administrar las citas en MongoDB
public interface CitaRepositorio extends MongoRepository<Cita, String> {
    List<Cita> findByDoctorId(String doctorId);
    List<Cita> findByPacienteId(String pacienteId);
}
