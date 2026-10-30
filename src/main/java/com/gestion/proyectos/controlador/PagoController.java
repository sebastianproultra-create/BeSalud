package com.gestion.proyectos.controlador;

import com.fasterxml.jackson.databind.JsonNode;
import com.gestion.proyectos.modelo.Cita;
import com.gestion.proyectos.modelo.Doctor;
import com.gestion.proyectos.modelo.Paciente;
import com.gestion.proyectos.modelo.Reserva;
import com.gestion.proyectos.servicio.CitaService;
import com.gestion.proyectos.servicio.PasarelaPagos;
import com.gestion.proyectos.servicio.ReservaService;
import com.gestion.proyectos.util.Calendario;
import com.gestion.proyectos.util.Reloj;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Pago del abono: resumen antes de pagar, pasarela (Wompi o simulada), regreso desde Wompi, eventos
 * firmados y la página de resultado que ve el paciente.
 */
@Controller
public class PagoController {

    /** Qué ve el paciente en la página de resultado. */
    enum Vista { CONFIRMADA, RECHAZADA, PROCESANDO, CONFLICTO, EXPIRADA }

    private final ReservaService reservaService;
    private final PasarelaPagos pasarela;
    private final CitaService citaService;
    private final String urlApp;
    private final String nitComercio;

    public PagoController(ReservaService reservaService, PasarelaPagos pasarela, CitaService citaService,
            @Value("${app.url:http://localhost:8080}") String urlApp,
            @Value("${app.comercio.nit:}") String nitComercio) {
        this.reservaService = reservaService;
        this.pasarela = pasarela;
        this.citaService = citaService;
        this.urlApp = urlApp.replaceAll("/+$", "");
        this.nitComercio = nitComercio;
    }

    // ── Resumen antes de pagar ──────────────────────────────────────────────

    @GetMapping("/pagos/{referencia}")
    public String resumen(@PathVariable String referencia, Authentication auth, Model model) {
        Paciente paciente = paciente(auth);
        Reserva reserva = reservaDe(referencia, paciente);
        if (reserva.getEstado() != Reserva.Estado.ESPERANDO_PAGO || reserva.vencida(Reloj.ahora())
                || reserva.getTransaccionId() != null)
            return "redirect:/pagos/" + referencia + "/resultado";
        Doctor doctor = doctorDe(reserva);
        model.addAttribute("reserva", reserva);
        model.addAttribute("doctor", doctor);
        model.addAttribute("segundosRestantes", segundosRestantes(reserva));
        model.addAttribute("segundosTotales", reservaService.getMinutosParaPagar() * 60);
        model.addAttribute("saldo", saldo(doctor, reserva));
        model.addAttribute("abonoPagado", abonoPagado(reserva));
        model.addAttribute("debePagarTotal", reserva.debePagarTotal(Reloj.ahora()));
        model.addAttribute("limiteSaldo", Cita.limitePagoSaldo(reserva.getFecha(), reserva.getHora()));
        model.addAttribute("pagoSimulado", pasarela.usaSimulada());
        model.addAttribute("urlPago", pasarela.urlPago(reserva, paciente.getEmail(),
                paciente.getNombre() + " " + paciente.getApellido(), urlApp + "/pagos/retorno"));
        return "pagos/resumen";
    }

    /** Antes de pagar, el paciente elige si paga solo el abono o el valor total de la consulta. */
    @PostMapping("/pagos/{referencia}/monto")
    public String elegirMonto(@PathVariable String referencia, @RequestParam boolean total, Authentication auth) {
        Paciente paciente = paciente(auth);
        reservaDe(referencia, paciente);
        reservaService.elegirMonto(referencia, paciente.getId(), total);
        return "redirect:/pagos/" + referencia;
    }

    @PostMapping("/pagos/{referencia}/liberar")
    public String liberar(@PathVariable String referencia, Authentication auth) {
        Paciente paciente = paciente(auth);
        Reserva reserva = reservaDe(referencia, paciente);
        reservaService.liberar(referencia, paciente.getId());
        if (reserva.isSaldo()) // la cita sigue igual, con su abono
            return "redirect:/pacientes/landing";
        return "redirect:/citas/nueva?doctorId=" + reserva.getDoctorId() + "&info=reserva_liberada";
    }

    /**
     * El paciente paga el resto de la consulta cuando quiera antes de la cita, por la misma pasarela que el
     * abono. Así la cita queda pagada por completo y no tiene que pagar nada en el consultorio.
     */
    @PostMapping("/citas/{id}/pagar-saldo")
    public String pagarSaldo(@PathVariable String id, Authentication auth) {
        if (!pasarela.pagosDisponibles())
            return "redirect:/pacientes/landing?error=pagos_no_disponibles";
        Paciente paciente = paciente(auth);
        try {
            Reserva reserva = reservaService.apartarSaldo(id, paciente.getId());
            return "redirect:/pagos/" + reserva.getReferencia();
        } catch (ReservaService.SaldoNoDisponibleException e) {
            return "redirect:/pacientes/landing?error=saldo_no_disponible";
        }
    }

    // ── Pasarela simulada (solo sin llaves de Wompi) ─────────────────────────

    @GetMapping("/pagos/simulado/{referencia}")
    public String simulado(@PathVariable String referencia, Authentication auth, Model model) {
        Reserva reserva = reservaSimulada(referencia, auth);
        if (reserva.getEstado() != Reserva.Estado.ESPERANDO_PAGO || reserva.vencida(Reloj.ahora()))
            return "redirect:/pagos/" + referencia + "/resultado";
        model.addAttribute("reserva", reserva);
        model.addAttribute("doctor", doctorDe(reserva));
        model.addAttribute("segundosRestantes", segundosRestantes(reserva));
        model.addAttribute("segundosTotales", reservaService.getMinutosParaPagar() * 60);
        model.addAttribute("bancos", BANCOS_PRUEBA);
        model.addAttribute("concepto", reserva.isSaldo() ? "Saldo de la consulta"
                : reserva.isPagoTotal() ? "Valor total de la cita" : "Abono de cita");
        model.addAttribute("correoPaciente", auth.getName());
        return "pagos/simulado";
    }

    /** Bancos de la lista de PSE en la pasarela de pruebas; los dos primeros son los de prueba de Wompi. */
    /** Bancos que muestra el simulador de PSE (solo la demostración: no se conecta con ninguno). */
    static final List<String> BANCOS_PRUEBA = List.of("Bancolombia", "Banco de Bogotá", "Davivienda", "BBVA Colombia",
            "Banco de Occidente", "Banco Popular", "Scotiabank Colpatria", "Banco AV Villas", "Banco Caja Social",
            "Banco Itaú", "Banco Falabella", "Nequi", "Daviplata");

    /**
     * Recibe solo lo necesario para el comprobante (marca y últimos 4 dígitos, banco, últimos 4 del
     * celular). El número completo de la tarjeta, la fecha y el CVC nunca salen del navegador.
     */
    @PostMapping("/pagos/simulado/{referencia}")
    public String pagarSimulado(@PathVariable String referencia, @RequestParam String medio,
            @RequestParam String escenario, @RequestParam(required = false) String marca,
            @RequestParam(required = false) String ultimos4, @RequestParam(required = false) Integer banco,
            Authentication auth) {
        reservaSimulada(referencia, auth);
        String estado = "APPROVED".equals(escenario) ? "APPROVED" : "DECLINED";
        String digitos = ultimos4 != null && ultimos4.matches("\\d{4}") ? ultimos4 : "••••";
        String descripcion = switch (medio) {
            case "CARD" -> MARCAS.getOrDefault(marca, "Tarjeta") + " •••• " + digitos;
            case "PSE" -> "PSE · " + (banco != null && banco >= 0 && banco < BANCOS_PRUEBA.size()
                    ? BANCOS_PRUEBA.get(banco) : "Banco");
            case "NEQUI" -> "Nequi •••• " + digitos;
            case "BANCOLOMBIA_TRANSFER" -> "Botón Bancolombia";
            default -> null;
        };
        if (descripcion == null)
            return "redirect:/pagos/simulado/" + referencia;
        reservaService.pagoSimulado(referencia, medio, descripcion, estado);
        return "redirect:/pagos/" + referencia + "/resultado";
    }

    private static final Map<String, String> MARCAS = Map.of("VISA", "Visa", "MASTERCARD", "Mastercard",
            "AMEX", "American Express", "DINERS", "Diners Club");

    private Reserva reservaSimulada(String referencia, Authentication auth) {
        if (!pasarela.usaSimulada())
            throw new NoSuchElementException();
        return reservaDe(referencia, paciente(auth));
    }

    // ── Regreso desde Wompi y eventos ───────────────────────────────────────

    /**
     * Wompi devuelve al paciente aquí con ?id=TRANSACCION. No se confía en la URL: se pregunta a Wompi
     * el estado, la referencia y el monto de esa transacción.
     */
    @GetMapping("/pagos/retorno")
    public String retorno(@RequestParam(required = false) String id) {
        return pasarela.consultar(id)
                .map(t -> {
                    reservaService.confirmarPago(t.referencia(), t.id(), t.estado(), t.montoCentavos(), t.moneda());
                    return reservaService.buscarPorReferencia(t.referencia())
                            .map(r -> "redirect:/pagos/" + r.getReferencia() + "/resultado")
                            .orElse("redirect:/pacientes/landing?error=pago_sin_confirmar");
                })
                .orElse("redirect:/pacientes/landing?error=pago_sin_confirmar");
    }

    /** Evento transaction.updated de Wompi; se ignora si la firma no coincide. */
    @PostMapping("/pagos/wompi/eventos")
    @ResponseBody
    public ResponseEntity<Void> evento(@RequestBody JsonNode evento) {
        pasarela.eventoValido(evento).ifPresent(t -> reservaService.confirmarPago(t.referencia(), t.id(), t.estado(),
                t.montoCentavos(), t.moneda()));
        return ResponseEntity.ok().build();
    }

    // ── Resultado ───────────────────────────────────────────────────────────

    @GetMapping("/pagos/{referencia}/resultado")
    public String resultado(@PathVariable String referencia, Authentication auth, Model model) {
        Reserva reserva = actualizar(reservaDe(referencia, paciente(auth)));
        if (reserva.getEstado() == Reserva.Estado.ESPERANDO_PAGO && reserva.getTransaccionId() == null
                && !reserva.vencida(Reloj.ahora()))
            return "redirect:/pagos/" + referencia; // aún no ha pagado
        Doctor doctor = doctorDe(reserva);
        Vista vista = vistaDe(reserva);
        model.addAttribute("vista", vista.name());
        model.addAttribute("reserva", reserva);
        model.addAttribute("doctor", doctor);
        model.addAttribute("saldo", saldo(doctor, reserva));
        model.addAttribute("abonoPagado", abonoPagado(reserva));
        model.addAttribute("pagoSimulado", pasarela.usaSimulada());
        model.addAttribute("nitComercio", nitComercio.isBlank() ? null : nitComercio);
        model.addAttribute("cancelarHasta", reserva.getFecha().atTime(reserva.getHora())
                .minusHours(doctor.horasCancelacionEfectivas()));
        model.addAttribute("limiteSaldo", Cita.limitePagoSaldo(reserva.getFecha(), reserva.getHora()));
        if (vista == Vista.CONFIRMADA && reserva.getCitaId() != null)
            citaService.buscarPorId(reserva.getCitaId()).ifPresent(c -> {
                model.addAttribute("cita", c);
                // Si aún le falta el resto, se ofrece pagarlo desde el comprobante
                model.addAttribute("saldoPorPagar", ReservaService.saldoPorPagar(c, doctor, Reloj.ahora()));
                model.addAttribute("enlaceGoogle", Calendario.enlaceGoogle(c.getFecha().atTime(c.getHora()), 30,
                        "Cita con Dr(a). " + doctor.getNombre() + " " + doctor.getApellido(),
                        doctor.getDireccionConsultorio(),
                        "Cita agendada en BeSalud. Referencia del pago: " + reserva.getReferencia() + "."));
            });
        return "pagos/resultado";
    }

    /** Para que la página "procesando" se actualice sola mientras Wompi confirma. */
    @GetMapping("/pagos/{referencia}/estado")
    @ResponseBody
    public Map<String, String> estado(@PathVariable String referencia, Authentication auth) {
        return Map.of("vista", vistaDe(actualizar(reservaDe(referencia, paciente(auth)))).name());
    }

    /**
     * Si hay una transacción pendiente se vuelve a consultar en Wompi. En local los eventos de Wompi no
     * llegan (localhost no es accesible desde internet), así que esta consulta es la que confirma.
     */
    private Reserva actualizar(Reserva r) {
        boolean pendiente = r.getTransaccionId() != null
                && (r.getEstado() == Reserva.Estado.ESPERANDO_PAGO || r.getEstado() == Reserva.Estado.EXPIRADA);
        if (!pendiente)
            return r;
        if (pasarela.usaSimulada())
            return reservaService.resolverSimulacion(r);
        if (!pasarela.wompiConfigurado())
            return r;
        pasarela.consultar(r.getTransaccionId()).ifPresent(t ->
                reservaService.confirmarPago(t.referencia(), t.id(), t.estado(), t.montoCentavos(), t.moneda()));
        return reservaService.buscarPorReferencia(r.getReferencia()).orElse(r);
    }

    static Vista vistaDe(Reserva r) {
        return switch (r.getEstado()) {
            case APROBADA -> Vista.CONFIRMADA;
            case RECHAZADA -> Vista.RECHAZADA;
            case CONFLICTO -> Vista.CONFLICTO;
            case PROCESANDO -> Vista.PROCESANDO;
            case ESPERANDO_PAGO, EXPIRADA -> r.getTransaccionId() != null ? Vista.PROCESANDO : Vista.EXPIRADA;
        };
    }

    // ── Apoyo ───────────────────────────────────────────────────────────────

    private Paciente paciente(Authentication auth) {
        return citaService.buscarPacientePorEmail(auth.getName()).orElseThrow(NoSuchElementException::new);
    }

    /** Solo el paciente dueño de la reserva la puede ver. */
    private Reserva reservaDe(String referencia, Paciente paciente) {
        return reservaService.buscarPorReferencia(referencia)
                .filter(r -> paciente.getId().equals(r.getPacienteId()))
                .orElseThrow(NoSuchElementException::new);
    }

    private Doctor doctorDe(Reserva reserva) {
        return citaService.buscarDoctorPorId(reserva.getDoctorId()).orElseThrow(NoSuchElementException::new);
    }

    private static long segundosRestantes(Reserva reserva) {
        return Math.max(0, Duration.between(Reloj.ahora(), reserva.getExpiraEn()).getSeconds());
    }

    /** Lo que le falta pagar antes de la cita; si pagó todo (o es el pago del saldo) no queda nada. */
    private static Long saldo(Doctor doctor, Reserva reserva) {
        if (reserva.isSaldo() || reserva.isPagoTotal())
            return 0L;
        return Math.max(0, doctor.getPrecioEfectivo() - reserva.getMontoPesos());
    }

    /** En el pago del saldo: el abono que ya se había pagado al reservar (para mostrar las cuentas). */
    private Long abonoPagado(Reserva reserva) {
        if (!reserva.isSaldo() || reserva.getCitaId() == null)
            return null;
        return citaService.buscarPorId(reserva.getCitaId()).map(Cita::getAbonoPesos).orElse(null);
    }
}
