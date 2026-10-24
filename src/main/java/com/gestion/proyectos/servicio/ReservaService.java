package com.gestion.proyectos.servicio;

import com.gestion.proyectos.modelo.Cita;
import com.gestion.proyectos.modelo.Doctor;
import com.gestion.proyectos.modelo.Reserva;
import com.gestion.proyectos.repositorio.ReservaRepositorio;
import com.gestion.proyectos.util.Reloj;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Abono antes de agendar: se aparta el turno unos minutos, el paciente paga y solo entonces nace la cita.
 */
@Service
public class ReservaService {

    private static final Logger log = LoggerFactory.getLogger(ReservaService.class);

    public enum Resultado { APROBADA, RECHAZADA, PENDIENTE, EXPIRADA, CONFLICTO, NO_EXISTE }

    private final ReservaRepositorio reservaRepo;
    private final CitaService citaService;
    private final MongoTemplate mongo;
    private final int minutosParaPagar;

    public ReservaService(ReservaRepositorio reservaRepo, CitaService citaService, MongoTemplate mongo,
            @Value("${app.pagos.minutos-para-pagar:15}") int minutosParaPagar) {
        this.reservaRepo = reservaRepo;
        this.citaService = citaService;
        this.mongo = mongo;
        this.minutosParaPagar = minutosParaPagar;
    }

    public int getMinutosParaPagar() {
        return minutosParaPagar;
    }

    /**
     * Aparta el turno. Las reservas anteriores del mismo paciente que seguían esperando pago se anulan
     * (si volvió atrás y eligió otra hora, la primera no debe quedar bloqueando a otros).
     *
     * @throws CitaService.TurnoOcupadoException si otro paciente apartó el turno al mismo tiempo
     */
    public Reserva apartar(Doctor doctor, String pacienteId, LocalDate fecha, LocalTime hora, String motivo) {
        liberarVencidas();
        for (Reserva previa : reservaRepo.findByPacienteIdAndEstado(pacienteId, Reserva.Estado.ESPERANDO_PAGO)) {
            previa.setEstado(Reserva.Estado.EXPIRADA);
            reservaRepo.save(previa);
        }
        Reserva r = new Reserva();
        r.setReferencia("BS-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());
        r.setDoctorId(doctor.getId());
        r.setPacienteId(pacienteId);
        r.setFecha(fecha);
        r.setHora(hora);
        r.setMotivo(motivo.trim());
        r.setMontoCentavos(doctor.getAbono() * 100L);
        LocalDateTime ahora = Reloj.ahora();
        r.setCreadaEn(ahora);
        r.setExpiraEn(ahora.plusMinutes(minutosParaPagar));
        try {
            Reserva guardada = reservaRepo.save(r);
            log.info("Turno apartado: referencia={} doctor={} {} {}", guardada.getReferencia(), doctor.getId(), fecha, hora);
            return guardada;
        } catch (DuplicateKeyException e) {
            throw new CitaService.TurnoOcupadoException(doctor.getId() + " " + fecha + " " + hora);
        }
    }

    public Optional<Reserva> buscarPorReferencia(String referencia) {
        return referencia == null ? Optional.empty() : reservaRepo.findByReferencia(referencia);
    }

    /** Reservas del paciente que aún esperan pago y no se han vencido (aviso en su portal). */
    public java.util.List<Reserva> pendientesDe(String pacienteId) {
        LocalDateTime ahora = Reloj.ahora();
        return reservaRepo.findByPacienteIdAndEstado(pacienteId, Reserva.Estado.ESPERANDO_PAGO).stream()
                .filter(r -> !r.vencida(ahora))
                .toList();
    }

    /** El paciente desiste antes de pagar: el horario queda libre de inmediato. */
    public boolean liberar(String referencia, String pacienteId) {
        Reserva liberada = mongo.findAndModify(
                Query.query(Criteria.where("referencia").is(referencia).and("pacienteId").is(pacienteId)
                        .and("estado").is(Reserva.Estado.ESPERANDO_PAGO)),
                new Update().set("estado", Reserva.Estado.EXPIRADA).unset("turno"), Reserva.class);
        if (liberada != null)
            log.info("Reserva liberada por el paciente: referencia={}", referencia);
        return liberada != null;
    }

    /**
     * Aplica el resultado del pago. Puede llegar dos veces (la redirección del paciente y el webhook),
     * así que solo una toma la reserva: la pasa a PROCESANDO de forma atómica antes de crear la cita.
     */
    public Resultado confirmarPago(String referencia, String transaccionId, String estado, long montoCentavos,
            String moneda) {
        Reserva actual = reservaRepo.findByReferencia(referencia).orElse(null);
        if (actual == null)
            return Resultado.NO_EXISTE;
        if (actual.getEstado() == Reserva.Estado.APROBADA)
            return Resultado.APROBADA;
        if (actual.getEstado() == Reserva.Estado.CONFLICTO)
            return Resultado.CONFLICTO;
        if (!"APPROVED".equals(estado) && !"DECLINED".equals(estado) && !"VOIDED".equals(estado)
                && !"ERROR".equals(estado)) {
            // PSE o Nequi pueden tardar: se guarda la transacción para volver a consultarla
            if (transaccionId != null && actual.getTransaccionId() == null) {
                actual.setTransaccionId(transaccionId);
                reservaRepo.save(actual);
            }
            return Resultado.PENDIENTE;
        }

        Reserva tomada = mongo.findAndModify(
                Query.query(Criteria.where("referencia").is(referencia)
                        .and("estado").in(Reserva.Estado.ESPERANDO_PAGO, Reserva.Estado.EXPIRADA, Reserva.Estado.RECHAZADA)),
                new Update().set("estado", Reserva.Estado.PROCESANDO).set("transaccionId", transaccionId).unset("turno"),
                FindAndModifyOptions.options().returnNew(true), Reserva.class);
        if (tomada == null) // otra petición la está procesando o ya la procesó
            return reservaRepo.findByReferencia(referencia)
                    .map(r -> r.getEstado() == Reserva.Estado.APROBADA ? Resultado.APROBADA : Resultado.PENDIENTE)
                    .orElse(Resultado.NO_EXISTE);

        if (!"APPROVED".equals(estado)) {
            terminar(tomada, Reserva.Estado.RECHAZADA, null);
            return Resultado.RECHAZADA;
        }
        if (montoCentavos != tomada.getMontoCentavos() || !"COP".equals(moneda)) {
            log.error("Pago con monto o moneda distintos a la reserva {}: {} {}", referencia, montoCentavos, moneda);
            terminar(tomada, Reserva.Estado.CONFLICTO, null);
            return Resultado.CONFLICTO;
        }
        // Pagó: si el turno sigue libre se crea la cita aunque haya pagado después de vencer el apartado.
        if (citaService.hayConflicto(tomada.getDoctorId(), tomada.getFecha(), tomada.getHora(), null,
                tomada.getPacienteId()) || !citaService.doctorActivo(tomada.getDoctorId())) {
            log.error("Pago aprobado sin turno disponible (requiere devolución): referencia={}", referencia);
            terminar(tomada, Reserva.Estado.CONFLICTO, null);
            return Resultado.CONFLICTO;
        }
        try {
            Cita cita = citaService.crearCitaConAbono(tomada);
            terminar(tomada, Reserva.Estado.APROBADA, cita.getId());
            return Resultado.APROBADA;
        } catch (CitaService.TurnoOcupadoException e) {
            log.error("Pago aprobado pero el turno se ocupó (requiere devolución): referencia={}", referencia);
            terminar(tomada, Reserva.Estado.CONFLICTO, null);
            return Resultado.CONFLICTO;
        }
    }

    /** Pago simulado (solo cuando Wompi no está configurado): mismo camino que un pago real. */
    public Resultado pagoSimulado(String referencia, boolean aprobar) {
        Reserva r = reservaRepo.findByReferencia(referencia).orElse(null);
        if (r == null)
            return Resultado.NO_EXISTE;
        return confirmarPago(referencia, "SIM-" + referencia, aprobar ? "APPROVED" : "DECLINED",
                r.getMontoCentavos(), "COP");
    }

    private void terminar(Reserva r, Reserva.Estado estado, String citaId) {
        r.setEstado(estado);
        r.setCitaId(citaId);
        reservaRepo.save(r);
    }

    /** Libera los turnos cuyo tiempo para pagar se acabó. */
    @Scheduled(fixedDelay = 60_000)
    public void liberarVencidas() {
        var resultado = mongo.updateMulti(
                Query.query(Criteria.where("estado").is(Reserva.Estado.ESPERANDO_PAGO).and("expiraEn").lt(Reloj.ahora())),
                new Update().set("estado", Reserva.Estado.EXPIRADA).unset("turno"), Reserva.class);
        if (resultado.getModifiedCount() > 0)
            log.info("Reservas vencidas liberadas: {}", resultado.getModifiedCount());
    }
}
