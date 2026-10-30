package com.gestion.proyectos.servicio;

import com.gestion.proyectos.util.Reloj;
import com.gestion.proyectos.modelo.Cita;
import com.gestion.proyectos.modelo.CitaEvento;
import com.gestion.proyectos.modelo.Doctor;
import com.gestion.proyectos.modelo.EstadoAbono;
import com.gestion.proyectos.modelo.EstadoCita;
import com.gestion.proyectos.modelo.HorarioAtencion;
import com.gestion.proyectos.modelo.Paciente;
import com.gestion.proyectos.modelo.Reserva;
import com.gestion.proyectos.repositorio.CitaRepositorio;
import com.gestion.proyectos.repositorio.HorarioAtencionRepositorio;
import com.gestion.proyectos.repositorio.PersonaRepositorio;
import com.gestion.proyectos.repositorio.ReservaRepositorio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class CitaService {

    private static final Logger log = LoggerFactory.getLogger(CitaService.class);

    private final CitaRepositorio citaRepo;
    private final PersonaRepositorio personaRepo;
    private final HorarioAtencionRepositorio horarioRepo;
    private final ApplicationEventPublisher eventos;
    private final ReservaRepositorio reservaRepo;

    /** El turno lo tomó otra cita o una reserva en pago (lo detecta el índice único de Mongo). */
    public static class TurnoOcupadoException extends RuntimeException {
        public TurnoOcupadoException(String turno) {
            super("Turno ocupado: " + turno);
        }
    }

    public CitaService(CitaRepositorio citaRepo, PersonaRepositorio personaRepo,
                       HorarioAtencionRepositorio horarioRepo, ApplicationEventPublisher eventos,
                       ReservaRepositorio reservaRepo) {
        this.citaRepo = citaRepo;
        this.personaRepo = personaRepo;
        this.horarioRepo = horarioRepo;
        this.eventos = eventos;
        this.reservaRepo = reservaRepo;
    }

    public Page<Cita> listarPaginado(String pacienteId, String doctorId, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)),
                Sort.by(Sort.Direction.DESC, "fecha", "hora"));
        if (pacienteId != null && !pacienteId.isBlank()) return citaRepo.findByPacienteId(pacienteId, pageable);
        if (doctorId != null && !doctorId.isBlank()) return citaRepo.findByDoctorId(doctorId, pageable);
        return citaRepo.findAll(pageable);
    }

    /** Nombres de las personas pedidas (solo las de la página, no toda la colección). */
    public Map<String, String> nombresPorId(Collection<String> ids) {
        Map<String, String> nombres = new HashMap<>();
        personaRepo.findAllById(ids).forEach(p -> nombres.put(p.getId(), p.getNombre() + " " + p.getApellido()));
        return nombres;
    }

    public LinkedHashMap<String, List<String>> slotsDisponibles(String doctorId, String excludeCitaId,
                                                                  LocalDate fechaActual, LocalTime horaActual) {
        return slotsDisponibles(doctorId, excludeCitaId, fechaActual, horaActual, null);
    }

    /** pacienteIdPropio: sus propias reservas en pago no le ocultan el turno (puede volver a elegirlo). */
    public LinkedHashMap<String, List<String>> slotsDisponibles(String doctorId, String excludeCitaId,
                                                                  LocalDate fechaActual, LocalTime horaActual,
                                                                  String pacienteIdPropio) {
        LinkedHashMap<String, List<String>> slots = new LinkedHashMap<>();
        LocalDate today = Reloj.hoy();
        List<HorarioAtencion> horarios = horarioRepo.findByDoctorId(doctorId);
        for (int i = 0; i < 30; i++) {
            LocalDate fecha = today.plusDays(i);
            List<LocalTime> tiempos = calcularSlotsDisponibles(horarios, doctorId, fecha, excludeCitaId, fechaActual,
                    horaActual, pacienteIdPropio);
            if (!tiempos.isEmpty()) {
                slots.put(fecha.toString(), tiempos.stream().map(t -> t.toString().substring(0, 5)).toList());
            }
        }
        return slots;
    }

    public Cita crearCita(String doctorId, String pacienteId, LocalDate fecha, LocalTime hora, String motivo) {
        if (!doctorActivo(doctorId)) {
            throw new IllegalStateException("Doctor no activo: " + doctorId);
        }
        Cita cita = new Cita();
        cita.setDoctorId(doctorId);
        cita.setPacienteId(pacienteId);
        cita.setFecha(fecha);
        cita.setHora(hora);
        cita.setMotivo(motivo.trim());
        Cita guardada = guardarOcupandoTurno(cita);
        log.info("Cita creada: id={} doctor={} paciente={} fecha={} hora={}", guardada.getId(), doctorId, pacienteId, fecha, hora);
        eventos.publishEvent(new CitaEvento(CitaEvento.Tipo.CREADA, guardada.getId()));
        return guardada;
    }

    /**
     * Cita que nace de una reserva pagada. Si el paciente pagó todo, queda pagada por completo; si pagó el
     * abono, debe pagar el resto antes del plazo ({@link Cita#HORAS_LIMITE_SALDO} horas antes).
     */
    public Cita crearCitaConAbono(Reserva reserva) {
        Cita cita = new Cita();
        cita.setDoctorId(reserva.getDoctorId());
        cita.setPacienteId(reserva.getPacienteId());
        cita.setFecha(reserva.getFecha());
        cita.setHora(reserva.getHora());
        cita.setMotivo(reserva.getMotivo());
        long abono = reserva.getAbonoCentavos() != null && reserva.getAbonoCentavos() < reserva.getMontoCentavos()
                ? reserva.getAbonoCentavos() : reserva.getMontoCentavos();
        cita.setAbonoCentavos(abono);
        cita.setReferenciaPago(reserva.getReferencia());
        cita.setMedioPago(reserva.getMedioPago());
        cita.setEstadoAbono(EstadoAbono.PAGADO);
        cita.setSaldoObligatorio(true);
        if (reserva.isPagoTotal() && reserva.getMontoCentavos() > abono) {
            cita.setSaldoCentavos(reserva.getMontoCentavos() - abono);
            cita.setReferenciaSaldo(reserva.getReferencia());
            cita.setMedioPagoSaldo(reserva.getMedioPago());
            cita.setSaldoPagadoEn(Reloj.ahora());
        }
        Cita guardada = guardarOcupandoTurno(cita);
        log.info("Cita creada con abono: id={} referencia={}", guardada.getId(), reserva.getReferencia());
        eventos.publishEvent(new CitaEvento(CitaEvento.Tipo.CREADA, guardada.getId()));
        return guardada;
    }

    /**
     * Registra el pago del saldo de una cita: queda pagada por completo. Devuelve false si la cita ya no
     * está pendiente o ya tenía el saldo pagado (ese pago hay que devolverlo).
     */
    public boolean registrarSaldo(Reserva reserva) {
        Cita cita = reserva.getCitaId() == null ? null : citaRepo.findById(reserva.getCitaId()).orElse(null);
        if (cita == null || cita.getEstado() != EstadoCita.PENDIENTE || cita.isPagadaCompleta()
                || !reserva.getPacienteId().equals(cita.getPacienteId()))
            return false;
        cita.setSaldoCentavos(reserva.getMontoCentavos());
        cita.setReferenciaSaldo(reserva.getReferencia());
        cita.setMedioPagoSaldo(reserva.getMedioPago());
        cita.setSaldoPagadoEn(Reloj.ahora());
        citaRepo.save(cita);
        log.info("Saldo pagado: cita={} referencia={}", cita.getId(), reserva.getReferencia());
        eventos.publishEvent(new CitaEvento(CitaEvento.Tipo.SALDO_PAGADO, cita.getId()));
        return true;
    }

    /** Citas pendientes a las que todavía les falta pagar el resto. */
    public List<Cita> citasConSaldoPendiente() {
        return citaRepo.findByEstadoAndSaldoObligatorioTrueAndSaldoCentavosIsNull(EstadoCita.PENDIENTE);
    }

    /**
     * Venció el plazo sin pagar el resto: la cita se cancela, el turno queda libre para otro paciente y el
     * profesional conserva el abono, como en una cancelación tardía.
     */
    public boolean cancelarPorFaltaDePago(Cita cita) {
        if (cita.getEstado() != EstadoCita.PENDIENTE || cita.isPagadaCompleta())
            return false;
        cita.setEstado(EstadoCita.CANCELADA);
        if (cita.getAbonoCentavos() != null)
            cita.setEstadoAbono(EstadoAbono.RETENIDO);
        citaRepo.save(cita);
        log.info("Cita cancelada por no pagar el resto a tiempo: id={}", cita.getId());
        eventos.publishEvent(new CitaEvento(CitaEvento.Tipo.CANCELADA_SIN_PAGO, cita.getId()));
        return true;
    }

    /** ¿Se puede mover la cita a esa fecha? Si le falta pagar el resto, el nuevo plazo no puede haber vencido. */
    public static boolean reprogramacionDejaPlazoParaPagar(Cita cita, LocalDate fecha, LocalTime hora, LocalDateTime ahora) {
        if (!cita.isSaldoObligatorio() || cita.isPagadaCompleta())
            return true;
        LocalDateTime limite = Cita.limitePagoSaldo(fecha, hora);
        return limite != null && ahora.isBefore(limite);
    }

    private Cita guardarOcupandoTurno(Cita cita) {
        try {
            return citaRepo.save(cita);
        } catch (DuplicateKeyException e) {
            throw new TurnoOcupadoException(cita.getDoctorId() + " " + cita.getFecha() + " " + cita.getHora());
        }
    }

    public void reprogramar(Cita cita, LocalDate fecha, LocalTime hora) {
        if (cita.getEstado() != EstadoCita.PENDIENTE) {
            throw new IllegalStateException("Solo citas PENDIENTE pueden reprogramarse: " + cita.getId());
        }
        LocalDate fechaAnterior = cita.getFecha();
        LocalTime horaAnterior = cita.getHora();
        cita.setFecha(fecha);
        cita.setHora(hora);
        cita.setRecordatorioEnviado(false);
        cita.setRecordatorioSaldoEnviado(false);
        // Nueva fecha: se vuelve a preguntar si asistirá
        cita.setAsistenciaConfirmada(null);
        cita.setConfirmacionSolicitada(false);
        try {
            guardarOcupandoTurno(cita);
        } catch (TurnoOcupadoException e) {
            cita.setFecha(fechaAnterior);
            cita.setHora(horaAnterior);
            throw e;
        }
        log.info("Cita reprogramada: id={} nueva fecha={} hora={}", cita.getId(), fecha, hora);
        eventos.publishEvent(new CitaEvento(CitaEvento.Tipo.REPROGRAMADA, cita.getId()));
    }

    /** Cancelación del administrador: si hubo abono, se devuelve. */
    public boolean cancelar(Cita cita) {
        return cancelar(cita, false);
    }

    /**
     * Solo se cancelan citas pendientes: una cita atendida o completada no puede volverse cancelada
     * (se perdería el dictamen y la historia). Devuelve false si la cita no estaba pendiente.
     * Si cancela el paciente con menos anticipación de la que pide el profesional, el abono se retiene.
     */
    public boolean cancelar(Cita cita, boolean porPaciente) {
        if (cita.getEstado() != EstadoCita.PENDIENTE) {
            log.warn("Intento de cancelar una cita en estado {}: {}", cita.getEstado(), cita.getId());
            return false;
        }
        if (cita.getAbonoCentavos() != null) {
            boolean aTiempo = !porPaciente || cancelaConAnticipacion(cita);
            cita.setEstadoAbono(aTiempo ? EstadoAbono.A_REEMBOLSAR : EstadoAbono.RETENIDO);
        }
        cita.setEstado(EstadoCita.CANCELADA);
        citaRepo.save(cita);
        log.info("Cita cancelada: id={}", cita.getId());
        eventos.publishEvent(new CitaEvento(CitaEvento.Tipo.CANCELADA, cita.getId()));
        return true;
    }

    /**
     * Se le pregunta al paciente si asistirá desde un día antes del plazo de cancelación hasta la hora de la
     * cita. Así, si responde que no a tiempo, el abono se devuelve.
     */
    public static final int HORAS_EXTRA_CONFIRMACION = 24;

    public static boolean esperaConfirmacion(Cita cita, Doctor doctor, LocalDateTime ahora) {
        if (cita.getEstado() != EstadoCita.PENDIENTE || cita.getAsistenciaConfirmada() != null
                || cita.getFecha() == null || cita.getHora() == null)
            return false;
        int horas = (doctor == null ? 24 : doctor.horasCancelacionEfectivas()) + HORAS_EXTRA_CONFIRMACION;
        LocalDateTime inicio = LocalDateTime.of(cita.getFecha(), cita.getHora());
        return !ahora.isBefore(inicio.minusHours(horas)) && ahora.isBefore(inicio);
    }

    /** Citas del paciente a las que todavía debe responder si asistirá. */
    public List<Cita> porConfirmarDe(String pacienteId) {
        LocalDateTime ahora = Reloj.ahora();
        return citaRepo.findByPacienteId(pacienteId).stream()
                .filter(c -> c.getEstado() == EstadoCita.PENDIENTE && c.getFecha() != null
                        && !c.getFecha().isBefore(ahora.toLocalDate()))
                .filter(c -> esperaConfirmacion(c, buscarDoctorPorId(c.getDoctorId()).orElse(null), ahora))
                .sorted(java.util.Comparator.comparing(Cita::getFecha).thenComparing(Cita::getHora))
                .toList();
    }

    /**
     * Respuesta del paciente. Si asistirá, la cita queda confirmada; si no, se cancela y el abono se devuelve
     * cuando responde dentro del plazo (si no, lo conserva el profesional, como en cualquier cancelación tardía).
     */
    public boolean responderAsistencia(Cita cita, boolean asistira) {
        if (cita.getEstado() != EstadoCita.PENDIENTE)
            return false;
        if (!asistira)
            return cancelar(cita, true);
        cita.setAsistenciaConfirmada(true);
        citaRepo.save(cita);
        log.info("Asistencia confirmada por el paciente: cita={}", cita.getId());
        return true;
    }

    /** ¿Faltan al menos las horas que pide el profesional para cancelar con devolución? */
    public boolean cancelaConAnticipacion(Cita cita) {
        if (cita.getFecha() == null || cita.getHora() == null)
            return true;
        int horas = buscarDoctorPorId(cita.getDoctorId()).map(Doctor::horasCancelacionEfectivas).orElse(24);
        return !Reloj.ahora().plusHours(horas).isAfter(LocalDateTime.of(cita.getFecha(), cita.getHora()));
    }

    public Optional<Cita> buscarPorId(String id) {
        return citaRepo.findById(id);
    }

    public Optional<Paciente> buscarPacientePorEmail(String email) {
        return personaRepo.findPacienteByEmail(email);
    }

    public Optional<Doctor> buscarDoctorPorEmail(String email) {
        return personaRepo.findDoctorByEmail(email);
    }

    public Optional<Doctor> buscarDoctorPorId(String id) {
        return personaRepo.findById(id).filter(p -> p instanceof Doctor).map(p -> (Doctor) p);
    }

    public boolean doctorActivo(String doctorId) {
        return buscarDoctorPorId(doctorId).map(d -> "ACTIVO".equals(d.getEstado())).orElse(false);
    }

    public List<Doctor> listarDoctores() {
        return personaRepo.findAllDoctores();
    }

    public List<Doctor> listarDoctoresActivos() {
        return personaRepo.findDoctoresActivos();
    }

    public List<Paciente> listarPacientes() {
        return personaRepo.findAllPacientes();
    }

    public boolean doctorExiste(String doctorId) {
        return personaRepo.existsById(doctorId);
    }

    public boolean pacienteExiste(String pacienteId) {
        return personaRepo.existsById(pacienteId);
    }

    public boolean esFechaHoraPasada(LocalDate fecha, LocalTime hora) {
        return LocalDateTime.of(fecha, hora).isBefore(Reloj.ahora());
    }

    public boolean esHorarioValido(String doctorId, LocalDate fecha, LocalTime hora) {
        return horarioRepo.findByDoctorId(doctorId).stream()
                .anyMatch(h -> h.getDiaSemana() != null && h.getHoraInicio() != null && h.getHoraFin() != null
                        && h.getDiaSemana().equals(fecha.getDayOfWeek())
                        && !hora.isBefore(h.getHoraInicio())
                        && hora.isBefore(h.getHoraFin()));
    }

    public boolean hayConflicto(String doctorId, LocalDate fecha, LocalTime hora, String excludeCitaId) {
        return hayConflicto(doctorId, fecha, hora, excludeCitaId, null);
    }

    public boolean hayConflicto(String doctorId, LocalDate fecha, LocalTime hora, String excludeCitaId,
                                String pacienteIdPropio) {
        List<HorarioAtencion> horarios = horarioRepo.findByDoctorId(doctorId);
        List<Cita> existentes = ocupadasDelDia(doctorId, fecha, excludeCitaId, pacienteIdPropio);

        LocalDateTime inicioNuevo = LocalDateTime.of(fecha, hora);
        LocalDateTime finNuevo = inicioNuevo.plusMinutes(duracionDesdeHorarios(horarios, fecha, hora));

        return existentes.stream().anyMatch(c -> {
            LocalDateTime inicio = LocalDateTime.of(c.getFecha(), c.getHora());
            LocalDateTime fin = inicio.plusMinutes(duracionDesdeHorarios(horarios, c.getFecha(), c.getHora()));
            return inicioNuevo.isBefore(fin) && finNuevo.isAfter(inicio);
        });
    }

    public boolean pacienteTieneConflicto(String pacienteId, String doctorId, LocalDate fecha, LocalTime hora,
                                          String excludeCitaId) {
        LocalDateTime inicioNuevo = LocalDateTime.of(fecha, hora);
        LocalDateTime finNuevo = inicioNuevo.plusMinutes(
                duracionDesdeHorarios(horarioRepo.findByDoctorId(doctorId), fecha, hora));

        return citaRepo.findByPacienteIdAndFecha(pacienteId, fecha).stream()
                .filter(c -> (excludeCitaId == null || !excludeCitaId.equals(c.getId()))
                        && c.getEstado() != EstadoCita.CANCELADA
                        && c.getEstado() != EstadoCita.NO_ASISTIO)
                .anyMatch(c -> {
                    LocalDateTime inicio = LocalDateTime.of(c.getFecha(), c.getHora());
                    LocalDateTime fin = inicio.plusMinutes(duracionDesdeHorarios(
                            horarioRepo.findByDoctorId(c.getDoctorId()), c.getFecha(), c.getHora()));
                    return inicioNuevo.isBefore(fin) && finNuevo.isAfter(inicio);
                });
    }

    /**
     * Citas que ocupan turno ese día más los turnos apartados por reservas que esperan el pago
     * (estas últimas como citas sin guardar, solo para calcular choques).
     */
    private List<Cita> ocupadasDelDia(String doctorId, LocalDate fecha, String excludeCitaId, String pacienteIdPropio) {
        List<Cita> ocupadas = new ArrayList<>(citaRepo.findByDoctorIdAndFecha(doctorId, fecha).stream()
                .filter(c -> (excludeCitaId == null || !excludeCitaId.equals(c.getId()))
                        && c.getEstado() != EstadoCita.CANCELADA
                        && c.getEstado() != EstadoCita.NO_ASISTIO)
                .toList());
        LocalDateTime ahora = Reloj.ahora();
        for (Reserva r : reservaRepo.findByDoctorIdAndFechaAndEstado(doctorId, fecha, Reserva.Estado.ESPERANDO_PAGO)) {
            if (r.vencida(ahora) || (pacienteIdPropio != null && pacienteIdPropio.equals(r.getPacienteId())))
                continue;
            Cita apartada = new Cita();
            apartada.setDoctorId(doctorId);
            apartada.setFecha(r.getFecha());
            apartada.setHora(r.getHora());
            ocupadas.add(apartada);
        }
        return ocupadas;
    }

    private List<LocalTime> calcularSlotsDisponibles(List<HorarioAtencion> horarios, String doctorId, LocalDate fecha,
                                                      String excludeCitaId, LocalDate fechaActual, LocalTime horaActual,
                                                      String pacienteIdPropio) {
        List<LocalTime> slots = new ArrayList<>();
        java.time.DayOfWeek dia = fecha.getDayOfWeek();
        // Sin horario ese día no hace falta consultar sus citas: ahorra dos consultas a Mongo por día libre
        if (horarios.stream().noneMatch(h -> dia.equals(h.getDiaSemana())))
            return slots;
        LocalDateTime ahora = Reloj.ahora();
        List<Cita> citasDelDia = ocupadasDelDia(doctorId, fecha, excludeCitaId, pacienteIdPropio);

        for (HorarioAtencion horario : horarios) {
            if (horario.getDiaSemana() == null || horario.getHoraInicio() == null || horario.getHoraFin() == null) continue;
            if (!horario.getDiaSemana().equals(dia)) continue;

            int duracion = horario.getDuracionCitaMinutos() > 0 ? horario.getDuracionCitaMinutos() : 30;
            LocalTime current = horario.getHoraInicio();

            while (!current.plusMinutes(duracion).isAfter(horario.getHoraFin())) {
                final LocalTime slot = current;
                LocalDateTime slotStart = LocalDateTime.of(fecha, slot);
                LocalDateTime slotEnd = slotStart.plusMinutes(duracion);

                boolean esPasado = slotStart.isBefore(ahora);
                boolean esSlotActual = fechaActual != null && horaActual != null
                        && fecha.equals(fechaActual) && slot.equals(horaActual);

                boolean disponible = !esPasado && !esSlotActual && citasDelDia.stream().noneMatch(c -> {
                    LocalDateTime citaStart = LocalDateTime.of(c.getFecha(), c.getHora());
                    LocalDateTime citaEnd = citaStart.plusMinutes(duracion);
                    return slotStart.isBefore(citaEnd) && slotEnd.isAfter(citaStart);
                });

                if (disponible) slots.add(current);
                current = current.plusMinutes(duracion);
            }
        }
        return slots;
    }

    private int duracionDesdeHorarios(List<HorarioAtencion> horarios, LocalDate fecha, LocalTime hora) {
        return horarios.stream()
                .filter(h -> h.getDiaSemana() != null && h.getHoraInicio() != null && h.getHoraFin() != null
                        && h.getDiaSemana().equals(fecha.getDayOfWeek())
                        && !hora.isBefore(h.getHoraInicio())
                        && hora.isBefore(h.getHoraFin()))
                .findFirst()
                .map(HorarioAtencion::getDuracionCitaMinutos)
                .orElse(30);
    }
}
