package com.gestion.proyectos.repositorio;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.gestion.proyectos.modelo.MensajeAyuda;

public interface MensajeAyudaRepositorio extends MongoRepository<MensajeAyuda, String> {
    List<MensajeAyuda> findByAtendidoFalseOrderByFechaDesc();
}
