package com.gestion.proyectos.servicio;

import com.gestion.proyectos.modelo.Cita;
import com.gestion.proyectos.modelo.CitaEvento;
import com.gestion.proyectos.modelo.Devolucion;
import com.gestion.proyectos.modelo.DevolucionSinCitaEvento;
import com.gestion.proyectos.modelo.EstadoAbono;
import com.gestion.proyectos.modelo.Persona;
import com.gestion.proyectos.modelo.Reserva;
import com.gestion.proyectos.repositorio.CitaRepositorio;
import com.gestion.proyectos.repositorio.DevolucionRepositorio;
import com.gestion.proyectos.repositorio.PersonaRepositorio;
import com.gestion.proyectos.repositorio.ReservaRepositorio;
import com.gestion.proyectos.util.Reloj;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Devoluciones a los pacientes. El dinero entra a la cuenta de BeSalud, así que las hace el administrador: la app
 * no mueve dinero, pero solo da una devolución por hecha con número de operación, monto exacto y comprobante, y el
 * paciente puede confirmar que le llegó o avisar que no.
 *
 * Se devuelve lo pagado por una cita cancelada (origen "cita-ID") o un pago aprobado que no pudo aplicarse, como
 * cuando el turno ya no estaba (origen "reserva-REFERENCIA").
 */
@Service
public class DevolucionService {

    private static final Logger log = LoggerFactory.getLogger(DevolucionService.class);

    /** Días que se le prometen al paciente; pasados estos, el administrador la ve como vencida. */
    public static final int DIAS_PLAZO = 5;
    public static final List<String> MEDIOS = List.of("Wompi (panel de comercio)", "Transferencia bancaria", "Nequi",
            "Daviplata");
    private static final String ORIGEN_CITA = "cita-";
    private static final String ORIGEN_RESERVA = "reserva-";
    private static final Pattern NUMERO_OPERACION = Pattern.compile("[A-Z0-9._-]{4,60}");
    private static final int MAX_NOTA = 300;
    private static final int MAX_HISTORIAL = 50;

    /** Una devolución por hacer, con lo que el administrador necesita para hacerla. */
    public record Pendiente(String origen, String paciente, String documento, String telefono, String email,
            String profesional, String motivo, long montoPesos, LocalDate fecha, LocalTime hora, List<String> pagos,
            Long diasEspera, boolean vencida, Devolucion anterior) {
    }

    /** Una devolución ya registrada, con los nombres para la tabla. */
    public record Registrada(Devolucion devolucion, String paciente, String profesional) {
    }

    private final DevolucionRepositorio repo;
    private final CitaRepositorio citaRepo;
    private final ReservaRepositorio reservaRepo;
    private final PersonaRepositorio personaRepo;
    private final ApplicationEventPublisher eventos;

    public DevolucionService(DevolucionRepositorio repo, CitaRepositorio citaRepo, ReservaRepositorio reservaRepo,
            PersonaRepositorio personaRepo, ApplicationEventPublisher eventos) {
        this.repo = repo;
        this.citaRepo = citaRepo;
        this.reservaRepo = reservaRepo;
        this.personaRepo = personaRepo;
        this.eventos = eventos;
    }

    public long contarPendientes() {
        return citaRepo.countByEstadoAbono(EstadoAbono.A_REEMBOLSAR) + reservaRepo.contarPagosPorDevolver();
    }

    /** Lo que falta devolver: primero los pagos que no pudieron aplicarse, luego las citas de la más antigua a la más reciente. */
    public List<Pendiente> pendientes() {
        List<Pendiente> lista = new ArrayList<>();
        for (Reserva r : reservaRepo.listarPagosPorDevolver())
            lista.add(pendiente(ORIGEN_RESERVA + r.getReferencia(), r.getPacienteId(), r.getDoctorId(), motivoDe(r),
                    r.getMontoPesos(), r.getFecha(), r.getHora(), List.of(pago(r.getReferencia(), r.getMedioPago())),
                    r.getPagadaEn() != null ? r.getPagadaEn() : r.getCreadaEn()));
        for (Cita c : citaRepo.findByEstadoAbonoOrderByFechaAsc(EstadoAbono.A_REEMBOLSAR)) {
            List<String> pagos = new ArrayList<>();
            pagos.add(pago(c.getReferenciaPago(), c.getMedioPago()));
            if (c.getReferenciaSaldo() != null)
                pagos.add(pago(c.getReferenciaSaldo(), c.getMedioPagoSaldo()));
            lista.add(pendiente(ORIGEN_CITA + c.getId(), c.getPacienteId(), c.getDoctorId(), textoCancelacion(c),
                    c.getPagadoPesos() == null ? 0 : c.getPagadoPesos(), c.getFecha(), c.getHora(), pagos,
                    c.getReembolsoSolicitadoEn()));
        }
        return lista;
    }

    private Pendiente pendiente(String origen, String pacienteId, String doctorId, String motivo, long pesos,
            LocalDate fecha, LocalTime hora, List<String> pagos, LocalDateTime desde) {
        Persona paciente = personaRepo.findById(pacienteId).orElse(null);
        Long dias = desde == null ? null : ChronoUnit.DAYS.between(desde, Reloj.ahora());
        return new Pendiente(origen, nombre(paciente, "Paciente"), paciente == null ? null : paciente.getIdentificacion(),
                paciente == null ? null : paciente.getTelefono(), paciente == null ? null : paciente.getEmail(),
                nombre(personaRepo.findById(doctorId).orElse(null), "Profesional"), motivo, pesos, fecha, hora, pagos,
                dias, dias != null && dias > DIAS_PLAZO, repo.findByOrigen(origen).orElse(null));
    }

    private static String pago(String referencia, String medio) {
        return referencia + (medio != null ? " · " + medio : "");
    }

    /** Las últimas devoluciones registradas, sin el archivo. */
    public List<Registrada> registradas() {
        Map<String, String> nombres = new HashMap<>();
        return repo.listarSinComprobante(PageRequest.of(0, MAX_HISTORIAL)).stream()
                .map(d -> new Registrada(d,
                        nombres.computeIfAbsent(d.getPacienteId(), id -> nombre(personaRepo.findById(id).orElse(null), "Paciente")),
                        nombres.computeIfAbsent(d.getDoctorId(), id -> nombre(personaRepo.findById(id).orElse(null), "Profesional"))))
                .toList();
    }

    /**
     * Registra que el dinero ya se devolvió. Devuelve null si quedó guardada o el motivo por el que no: "no_pendiente",
     * "medio", "numero", "numero_usado", "monto", "archivo_vacio", "archivo_tamano", "archivo_tipo", "archivo_usado"
     * o "procesando".
     */
    public String registrar(String origen, String medio, String numero, Long montoPesos, MultipartFile archivo,
            String admin) {
        Cita cita = citaPendiente(origen);
        Reserva reserva = cita == null ? reservaPendiente(origen) : null;
        if (cita == null && reserva == null)
            return "no_pendiente";
        if (medio == null || !MEDIOS.contains(medio))
            return "medio";
        String operacion = numero == null ? "" : numero.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        if (!NUMERO_OPERACION.matcher(operacion).matches())
            return "numero";
        if (repo.existsByNumeroOperacionAndOrigenNot(operacion, origen))
            return "numero_usado";
        Long pagado = cita != null ? cita.getPagadoPesos() : Long.valueOf(reserva.getMontoPesos());
        if (montoPesos == null || !montoPesos.equals(pagado))
            return "monto";
        if (archivo == null || archivo.isEmpty())
            return "archivo_vacio";
        if (archivo.getSize() > DocumentoClinicoService.MAX_BYTES)
            return "archivo_tamano";
        byte[] bytes;
        try {
            bytes = archivo.getBytes();
        } catch (IOException e) {
            log.warn("No se pudo leer el comprobante de {}: {}", origen, e.getMessage());
            return "procesando";
        }
        String tipo = DocumentoClinicoService.tipoReal(bytes);
        if (tipo == null)
            return "archivo_tipo";
        String hash = PasarelaPagos.sha256(bytes);
        if (repo.existsByComprobanteHashAndOrigenNot(hash, origen))
            return "archivo_usado";

        LocalDateTime ahora = Reloj.ahora();
        Devolucion d = repo.findByOrigen(origen).orElseGet(() -> cita != null ? nueva(origen, cita) : nueva(origen, reserva));
        d.setMontoCentavos(montoPesos * 100);
        d.setMedio(medio);
        d.setNumeroOperacion(operacion);
        d.setComprobante(bytes);
        d.setComprobanteTipo(tipo);
        d.setComprobanteNombre(DocumentoClinicoService.nombreSeguro(archivo.getOriginalFilename(), tipo));
        d.setComprobanteHash(hash);
        d.setRegistradaPor(admin);
        d.setRegistradaEn(ahora);
        d.setEstado(Devolucion.Estado.REGISTRADA);
        d.getEventos().add(new Devolucion.Evento(ahora, admin,
                "Registró la devolución por " + medio + ", operación " + operacion
                        + " (comprobante " + hash.substring(0, 12) + "…)"));
        try {
            repo.save(d);
        } catch (DuplicateKeyException e) {
            return "numero_usado";
        }
        if (cita != null) {
            cita.setEstadoAbono(EstadoAbono.DEVUELTO);
            cita.setAbonoDevueltoEn(ahora);
            cita.setMedioDevolucion(medio);
            cita.setNumeroDevolucion(operacion);
            citaRepo.save(cita);
            eventos.publishEvent(new CitaEvento(CitaEvento.Tipo.ABONO_DEVUELTO, cita.getId()));
        } else {
            reserva.setDevuelta(true);
            reservaRepo.save(reserva);
            eventos.publishEvent(new DevolucionSinCitaEvento(reserva, medio, operacion));
        }
        log.info("Devolución {} registrada por {}: {}", d.getCodigo(), admin, origen);
        return null;
    }

    private Cita citaPendiente(String origen) {
        if (origen == null || !origen.startsWith(ORIGEN_CITA))
            return null;
        return citaRepo.findById(origen.substring(ORIGEN_CITA.length()))
                .filter(c -> c.getEstadoAbono() == EstadoAbono.A_REEMBOLSAR).orElse(null);
    }

    private Reserva reservaPendiente(String origen) {
        if (origen == null || !origen.startsWith(ORIGEN_RESERVA))
            return null;
        return reservaRepo.findByReferencia(origen.substring(ORIGEN_RESERVA.length()))
                .filter(r -> r.getEstado() == Reserva.Estado.CONFLICTO && !r.isDevuelta()).orElse(null);
    }

    private Devolucion nueva(String origen, Cita cita) {
        Devolucion d = nueva(origen, cita.getPacienteId(), cita.getDoctorId());
        d.setCitaId(cita.getId());
        if (cita.getReferenciaPago() != null)
            d.getReferencias().add(cita.getReferenciaPago());
        if (cita.getReferenciaSaldo() != null)
            d.getReferencias().add(cita.getReferenciaSaldo());
        if (cita.getReembolsoSolicitadoEn() != null)
            d.getEventos().add(new Devolucion.Evento(cita.getReembolsoSolicitadoEn(), "BeSalud", textoCancelacion(cita)));
        return d;
    }

    private Devolucion nueva(String origen, Reserva reserva) {
        Devolucion d = nueva(origen, reserva.getPacienteId(), reserva.getDoctorId());
        d.getReferencias().add(reserva.getReferencia());
        d.getEventos().add(new Devolucion.Evento(reserva.getPagadaEn() != null ? reserva.getPagadaEn() : reserva.getCreadaEn(),
                "BeSalud", motivoDe(reserva)));
        return d;
    }

    private static Devolucion nueva(String origen, String pacienteId, String doctorId) {
        Devolucion d = new Devolucion();
        d.setCodigo("DEV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT));
        d.setOrigen(origen);
        d.setPacienteId(pacienteId);
        d.setDoctorId(doctorId);
        return d;
    }

    public Optional<Devolucion> buscar(String codigo) {
        return repo.findByCodigo(codigo);
    }

    /** La devolución con su comprobante, solo si es de ese paciente. */
    public Optional<Devolucion> buscarDelPaciente(String codigo, String pacienteId) {
        return repo.findByCodigo(codigo).filter(d -> d.getPacienteId().equals(pacienteId));
    }

    /** Devoluciones que el paciente aún no ha confirmado. */
    public List<Devolucion> porConfirmar(String pacienteId) {
        return repo.listarDelPaciente(pacienteId).stream().filter(Devolucion::isPorConfirmar).toList();
    }

    /** Para enlazar cada pago de "Mis pagos" con su constancia: referencia del pago → código de la devolución. */
    public Map<String, String> codigosPorReferencia(String pacienteId) {
        Map<String, String> codigos = new HashMap<>();
        for (Devolucion d : repo.listarDelPaciente(pacienteId))
            d.getReferencias().forEach(r -> codigos.put(r, d.getCodigo()));
        return codigos;
    }

    /** El paciente confirma que le llegó: queda cerrada. */
    public boolean confirmar(String codigo, String pacienteId) {
        Devolucion d = buscarDelPaciente(codigo, pacienteId).filter(Devolucion::isPorConfirmar).orElse(null);
        if (d == null)
            return false;
        LocalDateTime ahora = Reloj.ahora();
        d.setEstado(Devolucion.Estado.CONFIRMADA);
        d.setConfirmadaEn(ahora);
        d.getEventos().add(new Devolucion.Evento(ahora, "Paciente", "Confirmó que recibió el dinero"));
        repo.save(d);
        return true;
    }

    /** El paciente avisa que no le llegó: la devolución vuelve a pendientes, con su nota, para que se revise. */
    public boolean reclamar(String codigo, String pacienteId, String nota) {
        Devolucion d = buscarDelPaciente(codigo, pacienteId).filter(Devolucion::isPorConfirmar).orElse(null);
        if (d == null)
            return false;
        String origen = d.getOrigen();
        Cita cita = origen.startsWith(ORIGEN_CITA)
                ? citaRepo.findById(origen.substring(ORIGEN_CITA.length())).orElse(null) : null;
        Reserva reserva = origen.startsWith(ORIGEN_RESERVA)
                ? reservaRepo.findByReferencia(origen.substring(ORIGEN_RESERVA.length())).orElse(null) : null;
        if (cita == null && reserva == null)
            return false;
        String texto = nota == null ? "" : nota.trim();
        if (texto.length() > MAX_NOTA)
            texto = texto.substring(0, MAX_NOTA);
        d.setEstado(Devolucion.Estado.RECLAMADA);
        d.setNotaReclamo(texto.isEmpty() ? null : texto);
        d.getEventos().add(new Devolucion.Evento(Reloj.ahora(), "Paciente",
                texto.isEmpty() ? "Avisó que no le ha llegado el dinero" : "Avisó que no le ha llegado el dinero: " + texto));
        repo.save(d);
        if (cita != null) {
            cita.setEstadoAbono(EstadoAbono.A_REEMBOLSAR);
            cita.setAbonoDevueltoEn(null);
            citaRepo.save(cita);
        } else {
            reserva.setDevuelta(false);
            reservaRepo.save(reserva);
        }
        log.warn("Devolución {} reclamada por el paciente: vuelve a pendientes", codigo);
        return true;
    }

    private static String textoCancelacion(Cita c) {
        if (c.getCanceladaPor() == null)
            return "Cita cancelada";
        return switch (c.getCanceladaPor()) {
            case Cita.CANCELO_MEDICO -> "Canceló el médico";
            case Cita.CANCELO_PACIENTE -> "Canceló el paciente a tiempo";
            default -> "Canceló el administrador";
        };
    }

    private static String motivoDe(Reserva r) {
        return r.getMotivoConflicto() != null ? r.getMotivoConflicto() : "Pago aprobado que no pudo aplicarse a una cita";
    }

    private static String nombre(Persona p, String respaldo) {
        return p == null ? respaldo : p.getNombre() + " " + p.getApellido();
    }
}
