package com.gestion.proyectos.repositorio;

import com.gestion.proyectos.modelo.Cita;
import com.gestion.proyectos.modelo.EstadoCita;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface CitaRepositorio extends MongoRepository<Cita, String> {
    List<Cita> findByDoctorId(String doctorId);
    List<Cita> findByPacienteId(String pacienteId);
    Page<Cita> findByDoctorId(String doctorId, Pageable pageable);
    Page<Cita> findByPacienteId(String pacienteId, Pageable pageable);
    List<Cita> findByDoctorIdAndEstado(String doctorId, EstadoCita estado);
    List<Cita> findByDoctorIdAndFecha(String doctorId, LocalDate fecha);
    List<Cita> findByPacienteIdAndFecha(String pacienteId, LocalDate fecha);
    List<Cita> findByFechaAndEstado(LocalDate fecha, EstadoCita estado);
}
