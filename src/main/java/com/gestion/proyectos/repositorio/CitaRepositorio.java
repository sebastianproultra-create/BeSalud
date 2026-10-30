package com.gestion.proyectos.repositorio;

import com.gestion.proyectos.modelo.Cita;
import com.gestion.proyectos.modelo.EstadoCita;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;

import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface CitaRepositorio extends MongoRepository<Cita, String> {
    List<Cita> findByDoctorId(String doctorId);
    List<Cita> findByPacienteId(String pacienteId);
    Page<Cita> findByDoctorId(String doctorId, Pageable pageable);
    Page<Cita> findByPacienteId(String pacienteId, Pageable pageable);
    List<Cita> findByDoctorIdAndEstado(String doctorId, EstadoCita estado);
    List<Cita> findByDoctorIdAndFecha(String doctorId, LocalDate fecha);
    List<Cita> findByPacienteIdAndFecha(String pacienteId, LocalDate fecha);
    List<Cita> findByFechaAndEstado(LocalDate fecha, EstadoCita estado);
    /** Citas pendientes que deben pagar el resto antes de la cita y aún no lo han hecho. */
    List<Cita> findByEstadoAndSaldoObligatorioTrueAndSaldoCentavosIsNull(EstadoCita estado);
    List<Cita> findByDoctorIdAndEstadoAbonoOrderByFechaAsc(String doctorId, com.gestion.proyectos.modelo.EstadoAbono estadoAbono);
    List<Cita> findByDoctorIdAndFechaBetweenAndAbonoCentavosIsNotNull(String doctorId, LocalDate desde, LocalDate hasta);

    /**
     * Marca que ya se pidió la confirmación sin reescribir la cita: si el paciente la cancela o responde
     * al mismo tiempo, su cambio no se pierde.
     */
    @Query("{ '_id': ?0, 'estado': 'PENDIENTE' }")
    @Update("{ '$set': { 'confirmacionSolicitada': true } }")
    long marcarConfirmacionSolicitada(String id);

    /** Marca el aviso de "paga el resto" sin pisar otros cambios de la cita. */
    @Query("{ '_id': ?0, 'estado': 'PENDIENTE' }")
    @Update("{ '$set': { 'recordatorioSaldoEnviado': true } }")
    long marcarRecordatorioSaldo(String id);
}
