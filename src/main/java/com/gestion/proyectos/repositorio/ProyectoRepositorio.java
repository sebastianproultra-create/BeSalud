
package com.gestion.proyectos.repositorio;

import com.gestion.proyectos.modelo.Proyecto;
import org.springframework.data.mongodb.repository.MongoRepository;

// Esta interfaz se encarga de comunicarse con MongoDB
// MongoRepository ya trae métodos como guardar, listar y eliminar
public interface ProyectoRepositorio extends MongoRepository<Proyecto, String> {
}
