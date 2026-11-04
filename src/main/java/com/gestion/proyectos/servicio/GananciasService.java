package com.gestion.proyectos.servicio;

import com.gestion.proyectos.modelo.Cita;
import com.gestion.proyectos.modelo.Doctor;
import com.gestion.proyectos.modelo.EstadoCita;
import com.gestion.proyectos.modelo.HorarioAtencion;
import com.gestion.proyectos.modelo.Liquidacion;
import com.gestion.proyectos.modelo.LiquidacionRegistradaEvento;
import com.gestion.proyectos.modelo.Persona;
import com.gestion.proyectos.repositorio.CitaRepositorio;
import com.gestion.proyectos.repositorio.HorarioAtencionRepositorio;
import com.gestion.proyectos.repositorio.LiquidacionRepositorio;
import com.gestion.proyectos.repositorio.PersonaRepositorio;
import com.gestion.proyectos.util.Reloj;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Lo que gana cada médico con BeSalud. El dinero entra a la cuenta de la plataforma: queda disponible cuando la cita se
 * atiende o el paciente pierde lo pagado (no asistió, canceló tarde o no pagó a tiempo), se le descuenta la comisión
 * vigente al reservar y el administrador lo liquida con comprobante.
 */
@Service
public class GananciasService {

    private static final Logger log = LoggerFactory.getLogger(GananciasService.class);

    public static final List<String> MEDIOS = List.of("Transferencia bancaria", "Nequi", "Daviplata");
    private static final Pattern NUMERO_OPERACION = Pattern.compile("[A-Z0-9._-]{4,60}");
    private static final Locale ES = Locale.forLanguageTag("es-CO");
    private static final DateTimeFormatter MES_CORTO = DateTimeFormatter.ofPattern("MMM", ES);
    private static final int MESES_GRAFICA = 6;
    private static final int ALTURA_BARRA = 120;
    private static final int MAX_HISTORIAL = 50;

    /** De dónde sale cada peso. Solo los tres primeros le dejan dinero al médico. */
    public enum Origen {
        ATENDIDA("Consulta atendida", "success", true),
        NO_ASISTIO("El paciente no asistió", "warning", true),
        CANCELACION("Cancelación tardía o sin pago a tiempo", "warning", true),
        POR_ATENDER("Por atender", "info", false),
        POR_DEVOLVER("Se está devolviendo al paciente", "danger", false),
        DEVUELTO("Devuelto al paciente", "danger", false);

        private final String texto;
        private final String tono;
        private final boolean ganancia;

        Origen(String texto, String tono, boolean ganancia) {
            this.texto = texto;
            this.tono = tono;
            this.ganancia = ganancia;
        }

        public String getTexto() {
            return texto;
        }

        public String getTono() {
            return tono;
        }

        public boolean isGanancia() {
            return ganancia;
        }
    }

    /** Una cita pagada: lo que pagó el paciente, la comisión y lo que le queda al médico. */
    public record Linea(LocalDate fecha, LocalTime hora, String paciente, Origen origen, long brutoPesos, int porcentaje,
            long comisionPesos, long netoPesos, String liquidacionCodigo) {
    }

    public record MesGrafica(String etiqueta, long netoPesos, int altura, boolean actual) {
    }

    /** Todo lo que ve el médico en "Mis ganancias" para un mes. */
    public record Panel(YearMonth mes, YearMonth anterior, YearMonth siguiente, int porcentajeVigente,
            long brutoPesos, long comisionPesos, long netoPesos, Integer variacion,
            long porLiquidarPesos, long enCursoPesos, long pagadoPesos,
            long atendidas, long noAsistio, long canceladas,
            long turnosOfrecidos, long turnosOcupados, long cuposVacios, int ocupacion,
            long horasOfrecidas, long ingresoPorHora, long valorCuposVacios,
            List<Linea> lineas, List<MesGrafica> grafica, List<Liquidacion> liquidaciones) {
    }

    /** Lo que BeSalud le debe a un médico. */
    public record PorLiquidar(String doctorId, String medico, String especialidad, String email, long citas,
            long brutoPesos, long comisionPesos, long netoPesos, LocalDate desde, LocalDate hasta) {
    }

    public record Registrada(Liquidacion liquidacion, String medico) {
    }

    private final CitaRepositorio citaRepo;
    private final LiquidacionRepositorio liquidacionRepo;
    private final PersonaRepositorio personaRepo;
    private final HorarioAtencionRepositorio horarioRepo;
    private final ApplicationEventPublisher eventos;
    private final CorreoService correo;
    private final int comisionVigente;

    public GananciasService(CitaRepositorio citaRepo, LiquidacionRepositorio liquidacionRepo,
            PersonaRepositorio personaRepo, HorarioAtencionRepositorio horarioRepo, ApplicationEventPublisher eventos, CorreoService correo,
            @Value("${besalud.comision.porcentaje:10}") int comisionVigente) {
        this.citaRepo = citaRepo;
        this.liquidacionRepo = liquidacionRepo;
        this.personaRepo = personaRepo;
        this.horarioRepo = horarioRepo;
        this.eventos = eventos;
        this.correo = correo;
        this.comisionVigente = comisionVigente;
    }

    public int getComisionVigente() {
        return comisionVigente;
    }

    /** null si la cita no tiene un pago en línea o su estado no dice qué pasó con él. */
    static Origen origenDe(Cita c) {
        if (c.getAbonoCentavos() == null || c.getEstadoAbono() == null)
            return null;
        return switch (c.getEstadoAbono()) {
            case RETENIDO -> c.getEstado() == EstadoCita.NO_ASISTIO ? Origen.NO_ASISTIO : Origen.CANCELACION;
            case A_REEMBOLSAR -> Origen.POR_DEVOLVER;
            case DEVUELTO -> Origen.DEVUELTO;
            case PAGADO -> c.getEstado() == EstadoCita.ASISTIO || c.getEstado() == EstadoCita.COMPLETADA ? Origen.ATENDIDA
                    : c.getEstado() == EstadoCita.PENDIENTE ? Origen.POR_ATENDER : null;
        };
    }

    /** La comisión pactada al reservar; las citas anteriores a la comisión usan la vigente. */
    private int porcentajeDe(Cita c) {
        return c.getComisionPorcentaje() != null ? c.getComisionPorcentaje() : comisionVigente;
    }

    private static long brutoDe(Cita c) {
        return c.getPagadoPesos() == null ? 0 : c.getPagadoPesos();
    }

    static long comision(long bruto, int porcentaje) {
        return Math.round(bruto * porcentaje / 100.0);
    }

    private long netoDe(Cita c) {
        long bruto = brutoDe(c);
        return bruto - comision(bruto, porcentajeDe(c));
    }

    public Panel panel(String doctorId, YearMonth pedido) {
        LocalDate hoy = Reloj.hoy();
        YearMonth actual = YearMonth.from(hoy);
        List<Cita> citas = citaRepo.findByDoctorId(doctorId).stream().filter(c -> c.getFecha() != null).toList();
        List<Cita> pagadas = citas.stream().filter(c -> origenDe(c) != null).toList();
        Map<YearMonth, Long> netoPorMes = new HashMap<>();
        for (Cita c : pagadas)
            if (origenDe(c).isGanancia())
                netoPorMes.merge(YearMonth.from(c.getFecha()), netoDe(c), Long::sum);

        // Sin mes pedido se abre el último con ganancias, para no mostrar un mes vacío apenas empieza
        YearMonth mes = pedido != null && !pedido.isAfter(actual) ? pedido
                : netoPorMes.keySet().stream().filter(m -> !m.isAfter(actual)).max(Comparator.naturalOrder()).orElse(actual);

        Map<String, String> pacientes = new HashMap<>();
        Map<String, String> codigos = new HashMap<>();
        List<Liquidacion> liquidaciones = liquidacionRepo.listarDelDoctor(doctorId);
        liquidaciones.forEach(l -> codigos.put(l.getId(), l.getCodigo()));
        List<Linea> lineas = new ArrayList<>();
        for (Cita c : pagadas) {
            if (!YearMonth.from(c.getFecha()).equals(mes))
                continue;
            Origen origen = origenDe(c);
            long bruto = brutoDe(c);
            int porcentaje = origen.isGanancia() ? porcentajeDe(c) : 0;
            lineas.add(new Linea(c.getFecha(), c.getHora(),
                    pacientes.computeIfAbsent(c.getPacienteId(), id -> nombre(personaRepo.findById(id).orElse(null), "Paciente")),
                    origen, bruto, porcentaje, origen.isGanancia() ? comision(bruto, porcentaje) : 0,
                    origen.isGanancia() ? bruto - comision(bruto, porcentaje) : 0,
                    c.getLiquidacionId() == null ? null : codigos.get(c.getLiquidacionId())));
        }
        lineas.sort(Comparator.comparing(Linea::fecha).reversed());
        long bruto = lineas.stream().filter(l -> l.origen().isGanancia()).mapToLong(Linea::brutoPesos).sum();
        long comision = lineas.stream().mapToLong(Linea::comisionPesos).sum();
        long neto = lineas.stream().mapToLong(Linea::netoPesos).sum();
        long anterior = netoPorMes.getOrDefault(mes.minusMonths(1), 0L);

        long porLiquidar = pagadas.stream().filter(GananciasService::liquidable).mapToLong(this::netoDe).sum();
        long enCurso = pagadas.stream().filter(c -> origenDe(c) == Origen.POR_ATENDER).mapToLong(this::netoDe).sum();
        long pagado = liquidaciones.stream().mapToLong(Liquidacion::getNetoPesos).sum();

        // Rentabilidad: lo que ofreció contra lo que se usó, hasta hoy si el mes sigue en curso
        LocalDate inicio = mes.atDay(1);
        LocalDate fin = mes.equals(actual) ? hoy.plusDays(1) : mes.plusMonths(1).atDay(1);
        List<Cita> delMes = citas.stream().filter(c -> !c.getFecha().isBefore(inicio) && c.getFecha().isBefore(fin)).toList();
        long atendidas = delMes.stream().filter(c -> c.getEstado() == EstadoCita.ASISTIO || c.getEstado() == EstadoCita.COMPLETADA).count();
        long noAsistio = delMes.stream().filter(c -> c.getEstado() == EstadoCita.NO_ASISTIO).count();
        long canceladas = delMes.stream().filter(c -> c.getEstado() == EstadoCita.CANCELADA).count();
        long ocupados = delMes.size() - canceladas;
        List<HorarioAtencion> horarios = horarioRepo.findByDoctorId(doctorId);
        long ofrecidos = turnos(horarios, inicio, fin);
        long horas = minutos(horarios, inicio, fin) / 60;
        long vacios = Math.max(0, ofrecidos - ocupados);
        long precioNeto = personaRepo.findById(doctorId).filter(Doctor.class::isInstance).map(Doctor.class::cast)
                .map(d -> (long) d.getPrecioEfectivo() - comision(d.getPrecioEfectivo(), comisionVigente)).orElse(0L);

        YearMonth desde = mes.minusMonths(MESES_GRAFICA - 1L);
        long maximo = 0;
        for (int i = 0; i < MESES_GRAFICA; i++)
            maximo = Math.max(maximo, netoPorMes.getOrDefault(desde.plusMonths(i), 0L));
        List<MesGrafica> grafica = new ArrayList<>();
        for (int i = 0; i < MESES_GRAFICA; i++) {
            YearMonth m = desde.plusMonths(i);
            long valor = netoPorMes.getOrDefault(m, 0L);
            grafica.add(new MesGrafica(m.format(MES_CORTO), valor,
                    maximo == 0 ? 0 : (int) Math.round(ALTURA_BARRA * (double) valor / maximo), m.equals(mes)));
        }

        return new Panel(mes, mes.minusMonths(1), mes.isBefore(actual) ? mes.plusMonths(1) : null, comisionVigente,
                bruto, comision, neto, anterior == 0 ? null : (int) Math.round(100.0 * (neto - anterior) / anterior),
                porLiquidar, enCurso, pagado, atendidas, noAsistio, canceladas,
                ofrecidos, ocupados, vacios, EstadisticasService.porcentaje(Math.min(ocupados, ofrecidos), ofrecidos),
                horas, horas == 0 ? 0 : neto / horas, vacios * precioNeto,
                lineas, grafica, liquidaciones);
    }

    /** ¿Ya es dinero del médico y falta pagárselo? */
    static boolean liquidable(Cita c) {
        Origen origen = origenDe(c);
        return origen != null && origen.isGanancia() && c.getLiquidacionId() == null;
    }

    /** Turnos que el médico ofrece entre dos fechas (fin excluido) según su horario actual. */
    static long turnos(List<HorarioAtencion> horarios, LocalDate inicio, LocalDate fin) {
        return recorrer(horarios, inicio, fin, true);
    }

    static long minutos(List<HorarioAtencion> horarios, LocalDate inicio, LocalDate fin) {
        return recorrer(horarios, inicio, fin, false);
    }

    private static long recorrer(List<HorarioAtencion> horarios, LocalDate inicio, LocalDate fin, boolean enTurnos) {
        long total = 0;
        for (LocalDate dia = inicio; dia.isBefore(fin); dia = dia.plusDays(1))
            for (HorarioAtencion h : horarios) {
                if (h.getDiaSemana() != dia.getDayOfWeek() || h.getHoraInicio() == null || h.getHoraFin() == null)
                    continue;
                long duracion = Duration.between(h.getHoraInicio(), h.getHoraFin()).toMinutes();
                int minutosCita = h.getDuracionCitaMinutos() > 0 ? h.getDuracionCitaMinutos() : 30;
                total += enTurnos ? duracion / minutosCita : duracion;
            }
        return total;
    }

    /** Lo que se le debe a cada médico, de la deuda más grande a la más pequeña. */
    public List<PorLiquidar> porLiquidar() {
        Map<String, List<Cita>> porDoctor = new LinkedHashMap<>();
        for (Cita c : citaRepo.findByAbonoCentavosIsNotNullAndLiquidacionIdIsNull())
            if (c.getFecha() != null && liquidable(c))
                porDoctor.computeIfAbsent(c.getDoctorId(), id -> new ArrayList<>()).add(c);
        List<PorLiquidar> lista = new ArrayList<>();
        porDoctor.forEach((doctorId, citas) -> {
            Persona p = personaRepo.findById(doctorId).orElse(null);
            long bruto = citas.stream().mapToLong(GananciasService::brutoDe).sum();
            long neto = citas.stream().mapToLong(this::netoDe).sum();
            lista.add(new PorLiquidar(doctorId, nombre(p, "Profesional"), p instanceof Doctor d ? d.getEspecialidad() : null,
                    p == null ? null : p.getEmail(), citas.size(), bruto, bruto - neto, neto,
                    citas.stream().map(Cita::getFecha).min(Comparator.naturalOrder()).orElse(null),
                    citas.stream().map(Cita::getFecha).max(Comparator.naturalOrder()).orElse(null)));
        });
        lista.sort(Comparator.comparingLong(PorLiquidar::netoPesos).reversed());
        return lista;
    }

    public List<Registrada> registradas() {
        Map<String, String> nombres = new HashMap<>();
        return liquidacionRepo.listarSinComprobante(PageRequest.of(0, MAX_HISTORIAL)).stream()
                .map(l -> new Registrada(l, nombres.computeIfAbsent(l.getDoctorId(),
                        id -> nombre(personaRepo.findById(id).orElse(null), "Profesional"))))
                .toList();
    }

    /**
     * Registra que al médico ya se le pagó todo lo disponible. Devuelve null si quedó guardada o el motivo por el que
     * no: "sin_saldo", "medio", "numero", "numero_usado", "monto", "archivo_vacio", "archivo_tamano", "archivo_tipo",
     * "archivo_usado" o "procesando".
     */
    public String registrar(String doctorId, String medio, String numero, Long montoPesos, MultipartFile archivo,
            String admin) {
        List<Cita> citas = citaRepo.findByDoctorId(doctorId).stream().filter(GananciasService::liquidable).toList();
        if (citas.isEmpty())
            return "sin_saldo";
        if (medio == null || !MEDIOS.contains(medio))
            return "medio";
        String operacion = numero == null ? "" : numero.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        if (!NUMERO_OPERACION.matcher(operacion).matches())
            return "numero";
        if (liquidacionRepo.existsByNumeroOperacion(operacion))
            return "numero_usado";
        long neto = citas.stream().mapToLong(this::netoDe).sum();
        if (montoPesos == null || montoPesos != neto)
            return "monto";
        if (archivo == null || archivo.isEmpty())
            return "archivo_vacio";
        if (archivo.getSize() > DocumentoClinicoService.MAX_BYTES)
            return "archivo_tamano";
        byte[] bytes;
        try {
            bytes = archivo.getBytes();
        } catch (IOException e) {
            log.warn("No se pudo leer el comprobante de la liquidación del médico {}: {}", doctorId, e.getMessage());
            return "procesando";
        }
        String tipo = DocumentoClinicoService.tipoReal(bytes);
        if (tipo == null)
            return "archivo_tipo";
        String hash = PasarelaPagos.sha256(bytes);
        if (liquidacionRepo.existsByComprobanteHash(hash))
            return "archivo_usado";

        long bruto = citas.stream().mapToLong(GananciasService::brutoDe).sum();
        List<String> ids = citas.stream().map(Cita::getId).toList();
        Liquidacion l = new Liquidacion();
        l.setCodigo("LIQ-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT));
        l.setDoctorId(doctorId);
        l.setCitaIds(ids);
        l.setBrutoPesos(bruto);
        l.setComisionPesos(bruto - neto);
        l.setNetoPesos(neto);
        l.setMedio(medio);
        l.setNumeroOperacion(operacion);
        l.setComprobante(bytes);
        l.setComprobanteTipo(tipo);
        l.setComprobanteNombre(DocumentoClinicoService.nombreSeguro(archivo.getOriginalFilename(), tipo));
        l.setComprobanteHash(hash);
        l.setRegistradaPor(admin);
        l.setRegistradaEn(Reloj.ahora());
        try {
            liquidacionRepo.save(l);
        } catch (DuplicateKeyException e) {
            return "numero_usado";
        }
        long marcadas = citaRepo.marcarLiquidadas(ids, l.getId());
        if (marcadas != ids.size())
            log.error("Liquidación {}: se esperaban {} citas y se marcaron {}", l.getCodigo(), ids.size(), marcadas);
        eventos.publishEvent(new LiquidacionRegistradaEvento(doctorId, l.getCodigo(), neto, ids.size(), medio, operacion));
        log.info("Liquidación {} registrada por {}: médico={} neto={}", l.getCodigo(), admin, doctorId, neto);
        return null;
    }

    /** ¿Se le intentará avisar por correo a este médico cuando le registren un pago? */
    public boolean avisaPorCorreo(String doctorId) {
        return personaRepo.findById(doctorId).map(p -> correo.puedeEnviarA(p.getEmail())).orElse(false);
    }

    public Optional<Liquidacion> buscar(String codigo) {
        return liquidacionRepo.findByCodigo(codigo);
    }

    /** La liquidación con su comprobante, solo si es de ese médico. */
    public Optional<Liquidacion> buscarDelDoctor(String codigo, String doctorId) {
        return liquidacionRepo.findByCodigo(codigo).filter(l -> l.getDoctorId().equals(doctorId));
    }

    private static String nombre(Persona p, String respaldo) {
        return p == null ? respaldo : p.getNombre() + " " + p.getApellido();
    }
}
