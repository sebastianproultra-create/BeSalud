package com.gestion.proyectos.repositorio;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import com.gestion.proyectos.modelo.Consultorio;

public interface ConsultorioRepositorio extends MongoRepository<Consultorio, String> {
    Optional<Consultorio> findByIdAndActivoTrue(String id);

    // En Mongo, igualdad contra un array coincide si algún elemento es igual.
    @Query(value = "{ 'dominios': ?0, 'activo': true }", exists = true)
    boolean existeDominioActivo(String dominio);

    @Query("{ 'dominios': ?0 }")
    List<Consultorio> buscarPorDominio(String dominio);

    @Query("{ 'doctorIds': ?0 }")
    List<Consultorio> buscarPorDoctor(String doctorId);

    @Query("{ 'doctorIds': { $in: ?0 } }")
    List<Consultorio> buscarPorDoctores(Collection<String> doctorIds);
}
