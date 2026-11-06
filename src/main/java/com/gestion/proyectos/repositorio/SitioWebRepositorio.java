package com.gestion.proyectos.repositorio;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.gestion.proyectos.modelo.SitioWeb;

public interface SitioWebRepositorio extends MongoRepository<SitioWeb, String> {
    Optional<SitioWeb> findByDoctorId(String doctorId);

    Optional<SitioWeb> findBySlug(String slug);

    boolean existsBySlug(String slug);
}
