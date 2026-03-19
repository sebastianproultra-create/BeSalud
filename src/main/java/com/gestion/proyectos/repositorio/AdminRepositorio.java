package com.gestion.proyectos.repositorio;

import com.gestion.proyectos.modelo.Admin;
import org.springframework.data.mongodb.repository.MongoRepository;

// Repositorio para administrar los admins en MongoDB
public interface AdminRepositorio extends MongoRepository<Admin, String> {
}
