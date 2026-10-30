package com.gestion.proyectos.servicio;

import com.gestion.proyectos.modelo.Cita;
import com.gestion.proyectos.modelo.Doctor;
import com.gestion.proyectos.modelo.Reserva;
import com.gestion.proyectos.repositorio.ReservaRepositorio;
import com.gestion.proyectos.util.Reloj;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    /** Cómo se ve un pago en el historial del paciente. */
    public enum EstadoPago {
        PAGADO("Pagado", "ok", "pagados"),
        EN_PROCESO("En proceso", "espera", "pagados"),
        RECHAZADO("Rechazado", "error", "rechazados"),
        POR_DEVOLVER("Devolución en proceso", "espera", "devoluciones"),
        DEVUELTO("Devuelto", "neutro", "devoluciones"),
        CONSERVADO("No reembolsable", "neutro", "pagados");

        private final String texto;
        private final String tono;
        /** Filtro de "Mis pagos" en el que aparece. */
        private final String grupo;

        EstadoPago(String texto, String tono, String grupo) {
            this.texto = texto;
            this.tono = tono;
            this.grupo = grupo;
        }

        public String getGrupo() {
            return grupo;
        }

        public String getTexto() {
            return texto;
        }

        public String getTono() {
            return tono;
        }
    }

    /** Una fila del historial: el intento de pago con su cita y lo que pasó con el dinero. */
    /** concepto: "Abono", "Pago total" o "Saldo de la consulta", para distinguir los pagos de una misma cita. */
    public record MovimientoPago(String referencia, LocalDateTime fechaPago, String profesional, String especialidad,
            LocalDate fechaCita, LocalTime horaCita, long montoPesos, String medioPago, EstadoPago estado,
            String concepto) {
    }

    static String conceptoDe(Reserva r) {
        return r.isSaldo() ? "Saldo de la consulta" : r.isPagoTotal() ? "Pago total" : "Abono";
    }

    /** Resumen del historial: lo pagado (sin lo devuelto) y lo devuelto o por devolver. */
    public record HistorialPagos(List<MovimientoPago> movimientos, long totalPagado, long totalDevuelto) {
    }

    /**
     * Pagos del paciente, del más reciente al más antiguo. No incluye los horarios apartados que nunca
     * se intentaron pagar: esos se muestran como pendientes en el portal mientras siguen vigentes.
     */
    public HistorialPagos historialDe(String pacienteId) {
        List<MovimientoPago> movimientos = new ArrayList<>();
        Map<String, Doctor> doctores = new HashMap<>();
        for (Reserva r : reservaRepo.findByPacienteIdOrderByCreadaEnDesc(pacienteId)) {
            EstadoPago estado = estadoPagoDe(r);
            if (estado == null)
                continue;
            Doctor d = doctores.computeIfAbsent(r.getDoctorId(), id -> citaService.buscarDoctorPorId(id).orElse(null));
            movimientos.add(new MovimientoPago(r.getReferencia(), r.getCreadaEn(),
                    d == null ? "Profesional" : "Dr(a). " + d.getNombre() + " " + d.getApellido(),
                    d == null ? null : d.getEspecialidad(), r.getFecha(), r.getHora(), r.getMontoPesos(),
                    r.getMedioPago(), estado, conceptoDe(r)));
        }
        long pagado = movimientos.stream().filter(m -> m.estado() == EstadoPago.PAGADO || m.estado() == EstadoPago.CONSERVADO)
                .mapToLong(MovimientoPago::montoPesos).sum();
        long devuelto = movimientos.stream().filter(m -> m.estado() == EstadoPago.DEVUELTO || m.estado() == EstadoPago.POR_DEVOLVER)
                .mapToLong(MovimientoPago::montoPesos).sum();
        return new HistorialPagos(movimientos, pagado, devuelto);
    }

    EstadoPago estadoPagoDe(Reserva r) {
        return switch (r.getEstado()) {
            case RECHAZADA -> EstadoPago.RECHAZADO;
            case CONFLICTO -> EstadoPago.POR_DEVOLVER; // pagó pero el turno ya no estaba
            case PROCESANDO -> EstadoPago.EN_PROCESO;
            case ESPERANDO_PAGO, EXPIRADA -> r.getTransaccionId() != null ? EstadoPago.EN_PROCESO : null;
            case APROBADA -> {
                Cita cita = r.getCitaId() == null ? null : citaService.buscarPorId(r.getCitaId()).orElse(null);
                if (cita == null || cita.getEstadoAbono() == null)
                    yield EstadoPago.PAGADO;
                yield switch (cita.getEstadoAbono()) {
                    case PAGADO -> EstadoPago.PAGADO;
                    case A_REEMBOLSAR -> EstadoPago.POR_DEVOLVER;
                    case DEVUELTO -> EstadoPago.DEVUELTO;
                    case RETENIDO -> EstadoPago.CONSERVADO;
                };
            }
        };
    }


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
            if (previa.isSaldo())
                continue; // el pago del saldo de otra cita no bloquea ningún turno
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
        LocalDateTime ahora = Reloj.ahora();
        r.setAbonoCentavos(doctor.getAbono() * 100L);
        r.setPrecioCentavos(doctor.getPrecioEfectivo() * 100L);
        // Sin tiempo para pagar el resto después, se cobra todo de una vez
        r.setPagoTotal(r.admiteElegirMonto() && r.debePagarTotal(ahora));
        r.setMontoCentavos(r.isPagoTotal() ? r.getPrecioCentavos() : r.getAbonoCentavos());
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
                .filter(r -> !r.isSaldo() && !r.vencida(ahora))
                .toList();
    }

    // ── Pago del saldo: el resto de la consulta, cuando el paciente quiera antes de la cita ──

    /** Lo que se puede pagar en línea para dejar la cita pagada por completo; null si no aplica. */
    public static Long saldoPorPagar(Cita cita, Doctor doctor, LocalDateTime ahora) {
        if (cita == null || doctor == null || cita.getEstado() != com.gestion.proyectos.modelo.EstadoCita.PENDIENTE
                || cita.getAbonoCentavos() == null || cita.isPagadaCompleta()
                || cita.getFecha() == null || cita.getHora() == null
                || !ahora.isBefore(LocalDateTime.of(cita.getFecha(), cita.getHora()))
                || cita.saldoVencido(ahora))
            return null;
        long saldo = doctor.getPrecioEfectivo() - cita.getAbonoPesos();
        return saldo > 0 ? saldo : null;
    }

    /** Saldo por pagar de cada cita (solo las que lo tienen), para el botón "Pagar el resto". */
    public Map<String, Long> saldosPorPagar(List<Cita> citas) {
        Map<String, Long> saldos = new HashMap<>();
        if (citas == null)
            return saldos;
        Map<String, Doctor> doctores = new HashMap<>();
        LocalDateTime ahora = Reloj.ahora();
        for (Cita c : citas) {
            Doctor d = doctores.computeIfAbsent(c.getDoctorId(), id -> citaService.buscarDoctorPorId(id).orElse(null));
            Long saldo = saldoPorPagar(c, d, ahora);
            if (saldo != null)
                saldos.put(c.getId(), saldo);
        }
        return saldos;
    }

    /** La cita no admite el pago del saldo (ya está pagada, cancelada, pasó o no es del paciente). */
    public static class SaldoNoDisponibleException extends RuntimeException {
        public SaldoNoDisponibleException(String mensaje) {
            super(mensaje);
        }
    }

    /**
     * Prepara el pago del saldo de una cita. Si ya hay uno vigente o en proceso para esa cita se reutiliza,
     * para que el paciente no pague dos veces.
     */
    public Reserva apartarSaldo(String citaId, String pacienteId) {
        Cita cita = citaService.buscarPorId(citaId)
                .filter(c -> pacienteId.equals(c.getPacienteId()))
                .orElseThrow(() -> new SaldoNoDisponibleException("Cita no encontrada: " + citaId));
        Doctor doctor = citaService.buscarDoctorPorId(cita.getDoctorId()).orElse(null);
        LocalDateTime ahora = Reloj.ahora();
        Long saldo = saldoPorPagar(cita, doctor, ahora);
        if (saldo == null)
            throw new SaldoNoDisponibleException("La cita " + citaId + " no tiene saldo por pagar");
        for (Reserva previa : reservaRepo.findByCitaIdAndTipo(citaId, Reserva.Tipo.SALDO)) {
            boolean enCurso = previa.getEstado() == Reserva.Estado.PROCESANDO
                    || previa.getEstado() == Reserva.Estado.ESPERANDO_PAGO
                            && (previa.getTransaccionId() != null || !previa.vencida(ahora))
                    || previa.getEstado() == Reserva.Estado.EXPIRADA && previa.getTransaccionId() != null;
            if (enCurso && previa.getMontoCentavos() == saldo * 100)
                return previa;
        }
        Reserva r = new Reserva();
        r.setTipo(Reserva.Tipo.SALDO);
        r.setReferencia("BS-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());
        r.setDoctorId(cita.getDoctorId());
        r.setPacienteId(pacienteId);
        r.setCitaId(cita.getId());
        r.setFecha(cita.getFecha());
        r.setHora(cita.getHora());
        r.setMotivo(cita.getMotivo());
        r.setMontoCentavos(saldo * 100);
        r.setCreadaEn(ahora);
        r.setExpiraEn(ahora.plusMinutes(minutosParaPagar));
        Reserva guardada = reservaRepo.save(r);
        log.info("Pago del saldo iniciado: referencia={} cita={}", guardada.getReferencia(), citaId);
        return guardada;
    }

    /**
     * El paciente elige si paga solo el abono o el valor total antes de ir a la pasarela. Devuelve false si ya
     * no se puede cambiar (pago en curso, reserva vencida) o si la cita es tan pronto que debe pagar todo.
     */
    public boolean elegirMonto(String referencia, String pacienteId, boolean total) {
        Reserva r = reservaRepo.findByReferencia(referencia)
                .filter(x -> pacienteId.equals(x.getPacienteId()))
                .orElse(null);
        LocalDateTime ahora = Reloj.ahora();
        if (r == null || !r.admiteElegirMonto() || r.getEstado() != Reserva.Estado.ESPERANDO_PAGO
                || r.getTransaccionId() != null || r.vencida(ahora))
            return false;
        if (!total && r.debePagarTotal(ahora))
            return false;
        r.setPagoTotal(total);
        r.setMontoCentavos(total ? r.getPrecioCentavos() : r.getAbonoCentavos());
        reservaRepo.save(r);
        return true;
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

        // El pago del saldo ya tiene su cita: se conserva el vínculo pase lo que pase
        String citaPrevia = tomada.isSaldo() ? tomada.getCitaId() : null;
        if (!"APPROVED".equals(estado)) {
            terminar(tomada, Reserva.Estado.RECHAZADA, citaPrevia);
            return Resultado.RECHAZADA;
        }
        // Si cambió entre abono y total con la pasarela ya abierta, vale el monto que realmente pagó
        if (montoCentavos != tomada.getMontoCentavos() && "COP".equals(moneda) && tomada.admiteElegirMonto()) {
            if (montoCentavos == tomada.getPrecioCentavos()
                    || montoCentavos == tomada.getAbonoCentavos() && !tomada.debePagarTotal(Reloj.ahora())) {
                tomada.setPagoTotal(montoCentavos == tomada.getPrecioCentavos());
                tomada.setMontoCentavos(montoCentavos);
            }
        }
        if (montoCentavos != tomada.getMontoCentavos() || !"COP".equals(moneda)) {
            log.error("Pago con monto o moneda distintos a la reserva {}: {} {}", referencia, montoCentavos, moneda);
            terminar(tomada, Reserva.Estado.CONFLICTO, citaPrevia);
            return Resultado.CONFLICTO;
        }
        if (tomada.isSaldo()) {
            if (!citaService.registrarSaldo(tomada)) {
                log.error("Saldo pagado para una cita que ya no lo admite (requiere devolución): referencia={}", referencia);
                terminar(tomada, Reserva.Estado.CONFLICTO, citaPrevia);
                return Resultado.CONFLICTO;
            }
            terminar(tomada, Reserva.Estado.APROBADA, citaPrevia);
            return Resultado.APROBADA;
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
        return pagoSimulado(referencia, "CARD", null, aprobar ? "APPROVED" : "DECLINED");
    }

    /** Segundos que tarda en resolverse un pago de prueba por PSE, Nequi o Bancolombia. */
    public static final int SEGUNDOS_SIMULACION = 8;

    /**
     * Pasarela de pruebas que imita a Wompi: la tarjeta responde de inmediato; PSE, Nequi y Bancolombia
     * quedan en proceso unos segundos (como cuando el banco o la app del paciente confirman) y luego se
     * resuelven por el mismo camino que un pago real.
     */
    public Resultado pagoSimulado(String referencia, String tipoMedio, String medioPago, String estadoFinal) {
        Reserva r = reservaRepo.findByReferencia(referencia).orElse(null);
        if (r == null)
            return Resultado.NO_EXISTE;
        if (r.getEstado() != Reserva.Estado.ESPERANDO_PAGO || r.getTransaccionId() != null)
            return Resultado.PENDIENTE; // ya se envió un pago para esta reserva
        String transaccion = idTransaccionDePrueba();
        r.setTipoMedio(tipoMedio);
        r.setMedioPago(medioPago);
        if ("APPROVED".equals(estadoFinal))
            r.setCodigoAutorizacion(String.valueOf(java.util.concurrent.ThreadLocalRandom.current().nextInt(100000, 1000000)));
        if ("CARD".equals(tipoMedio)) {
            reservaRepo.save(r);
            return confirmarPago(referencia, transaccion, estadoFinal, r.getMontoCentavos(), "COP");
        }
        r.setTransaccionId(transaccion);
        r.setSimulacionEstado(estadoFinal);
        r.setSimulacionListaEn(Reloj.ahora().plusSeconds(SEGUNDOS_SIMULACION));
        reservaRepo.save(r);
        log.info("Pago de prueba en proceso: referencia={} medio={}", referencia, tipoMedio);
        return Resultado.PENDIENTE;
    }

    /** Termina un pago de prueba en proceso cuando ya pasó su tiempo de espera. */
    public Reserva resolverSimulacion(Reserva r) {
        boolean enProceso = r.getSimulacionEstado() != null && r.getTransaccionId() != null
                && (r.getEstado() == Reserva.Estado.ESPERANDO_PAGO || r.getEstado() == Reserva.Estado.EXPIRADA);
        if (!enProceso || r.getSimulacionListaEn() == null || Reloj.ahora().isBefore(r.getSimulacionListaEn()))
            return r;
        confirmarPago(r.getReferencia(), r.getTransaccionId(), r.getSimulacionEstado(), r.getMontoCentavos(), "COP");
        return reservaRepo.findByReferencia(r.getReferencia()).orElse(r);
    }

    /** Mismo formato que los ids de Wompi: comercio-epoch-consecutivo. */
    private static String idTransaccionDePrueba() {
        var azar = java.util.concurrent.ThreadLocalRandom.current();
        return "1" + azar.nextInt(1000, 9999) + "-" + (System.currentTimeMillis() / 1000) + "-" + azar.nextInt(10000, 99999);
    }

    private void terminar(Reserva r, Reserva.Estado estado, String citaId) {
        if (estado == Reserva.Estado.APROBADA && r.getPagadaEn() == null)
            r.setPagadaEn(Reloj.ahora());
        r.setEstado(estado);
        r.setCitaId(citaId);
        reservaRepo.save(r);
    }

    /** ¿Hay un pago del saldo de esta cita que la pasarela aún no resuelve (PSE, Nequi)? */
    boolean saldoEnCurso(String citaId) {
        return reservaRepo.findByCitaIdAndTipo(citaId, Reserva.Tipo.SALDO).stream()
                .anyMatch(r -> r.getEstado() == Reserva.Estado.PROCESANDO
                        || r.getTransaccionId() != null && (r.getEstado() == Reserva.Estado.ESPERANDO_PAGO
                                || r.getEstado() == Reserva.Estado.EXPIRADA));
    }

    /**
     * Cancela las citas cuyo plazo para pagar el resto venció. Si hay un pago del saldo en curso se espera a
     * que la pasarela lo resuelva.
     */
    @Scheduled(fixedDelay = 600_000, initialDelay = 60_000)
    public int cancelarCitasSinPago() {
        LocalDateTime ahora = Reloj.ahora();
        int canceladas = 0;
        for (Cita cita : citaService.citasConSaldoPendiente()) {
            if (cita.saldoVencido(ahora) && !saldoEnCurso(cita.getId()) && citaService.cancelarPorFaltaDePago(cita))
                canceladas++;
        }
        if (canceladas > 0)
            log.info("Citas canceladas por no pagar el resto a tiempo: {}", canceladas);
        return canceladas;
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
