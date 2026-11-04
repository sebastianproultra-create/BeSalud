package com.gestion.proyectos.repositorio;

import com.gestion.proyectos.modelo.Liquidacion;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface LiquidacionRepositorio extends MongoRepository<Liquidacion, String> {

    Optional<Liquidacion> findByCodigo(String codigo);

    boolean existsByNumeroOperacion(String numeroOperacion);

    boolean existsByComprobanteHash(String comprobanteHash);

    /** Las listas no traen el archivo: se descarga solo cuando alguien lo abre. */
    @Query(value = "{}", fields = "{ 'comprobante': 0 }", sort = "{ 'registradaEn': -1 }")
    List<Liquidacion> listarSinComprobante(Pageable pagina);

    @Query(value = "{ 'doctorId': ?0 }", fields = "{ 'comprobante': 0 }", sort = "{ 'registradaEn': -1 }")
    List<Liquidacion> listarDelDoctor(String doctorId);
}
