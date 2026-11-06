package com.gestion.proyectos.repositorio;

import com.gestion.proyectos.modelo.DocumentoClinico;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface DocumentoClinicoRepositorio extends MongoRepository<DocumentoClinico, String> {

    /** Para listar: sin el contenido del archivo, que puede pesar varios MB. */
    @Query(value = "{ 'pacienteId': ?0 }", fields = "{ 'contenido': 0 }", sort = "{ 'subidoEn': -1 }")
    List<DocumentoClinico> listarSinContenido(String pacienteId);

    long countByPacienteId(String pacienteId);
}
