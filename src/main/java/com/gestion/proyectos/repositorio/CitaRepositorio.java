package com.gestion.proyectos.repositorio;

import com.gestion.proyectos.modelo.Cita;
import com.gestion.proyectos.modelo.EstadoCita;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDate;
import java.util.List;

public interface CitaRepositorio extends MongoRepository<Cita, String> {
    List<Cita> findByDoctorId(String doctorId);
    List<Cita> findByPacienteId(String pacienteId);
    List<Cita> findByDoctorIdAndEstado(String doctorId, EstadoCita estado);
    List<Cita> findByDoctorIdAndFecha(String doctorId, LocalDate fecha);
}
