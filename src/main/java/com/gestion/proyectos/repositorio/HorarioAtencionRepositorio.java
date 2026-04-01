package com.gestion.proyectos.repositorio;

import com.gestion.proyectos.modelo.HorarioAtencion;
import org.springframework.data.mongodb.repository.MongoRepository;

// Repositorio para administrar los horarios de atención en MongoDB
public interface HorarioAtencionRepositorio extends MongoRepository<HorarioAtencion, String> {
    java.util.List<HorarioAtencion> findByDoctorId(String doctorId);
}
