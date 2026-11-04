package com.gestion.proyectos.repositorio;

import com.gestion.proyectos.modelo.Devolucion;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface DevolucionRepositorio extends MongoRepository<Devolucion, String> {

    Optional<Devolucion> findByCodigo(String codigo);

    Optional<Devolucion> findByOrigen(String origen);

    boolean existsByNumeroOperacionAndOrigenNot(String numeroOperacion, String origen);

    boolean existsByComprobanteHashAndOrigenNot(String comprobanteHash, String origen);

    /** Las listas no traen el archivo: se descarga solo cuando alguien lo abre. */
    @Query(value = "{}", fields = "{ 'comprobante': 0 }", sort = "{ 'registradaEn': -1 }")
    List<Devolucion> listarSinComprobante(Pageable pagina);

    @Query(value = "{ 'pacienteId': ?0 }", fields = "{ 'comprobante': 0 }", sort = "{ 'registradaEn': -1 }")
    List<Devolucion> listarDelPaciente(String pacienteId);
}
