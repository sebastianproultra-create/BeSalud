package com.gestion.proyectos.repositorio;

import com.gestion.proyectos.modelo.HorarioAtencion;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.DayOfWeek;
import java.util.Collection;
import java.util.List;

// Repositorio para administrar los horarios de atención en MongoDB
public interface HorarioAtencionRepositorio extends MongoRepository<HorarioAtencion, String> {
    List<HorarioAtencion> findByDoctorId(String doctorId);
    List<HorarioAtencion> findByDoctorIdAndDiaSemana(String doctorId, DayOfWeek diaSemana);
    void deleteByDoctorIdAndDiaSemanaIn(String doctorId, Collection<DayOfWeek> dias);
}
