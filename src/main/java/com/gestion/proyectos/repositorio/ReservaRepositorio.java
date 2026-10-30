package com.gestion.proyectos.repositorio;

import com.gestion.proyectos.modelo.Reserva;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ReservaRepositorio extends MongoRepository<Reserva, String> {
    Optional<Reserva> findByReferencia(String referencia);
    List<Reserva> findByDoctorIdAndFechaAndEstado(String doctorId, LocalDate fecha, Reserva.Estado estado);
    List<Reserva> findByPacienteIdAndEstado(String pacienteId, Reserva.Estado estado);
    List<Reserva> findByPacienteIdOrderByCreadaEnDesc(String pacienteId);
    List<Reserva> findByCitaIdAndTipo(String citaId, Reserva.Tipo tipo);
}
