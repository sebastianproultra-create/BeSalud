package com.gestion.proyectos.controlador;

import com.fasterxml.jackson.databind.JsonNode;
import com.gestion.proyectos.modelo.Doctor;
import com.gestion.proyectos.modelo.Paciente;
import com.gestion.proyectos.modelo.Reserva;
import com.gestion.proyectos.servicio.CitaService;
import com.gestion.proyectos.servicio.PasarelaPagos;
import com.gestion.proyectos.servicio.ReservaService;
import com.gestion.proyectos.util.Reloj;
import java.time.Duration;
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

    public PagoController(ReservaService reservaService, PasarelaPagos pasarela, CitaService citaService,
            @Value("${app.url:http://localhost:8080}") String urlApp) {
        this.reservaService = reservaService;
        this.pasarela = pasarela;
        this.citaService = citaService;
        this.urlApp = urlApp.replaceAll("/+$", "");
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
        model.addAttribute("pagoSimulado", !pasarela.wompiConfigurado());
        model.addAttribute("urlPago", pasarela.urlPago(reserva, paciente.getEmail(),
                paciente.getNombre() + " " + paciente.getApellido(), urlApp + "/pagos/retorno"));
        return "pagos/resumen";
    }

    @PostMapping("/pagos/{referencia}/liberar")
    public String liberar(@PathVariable String referencia, Authentication auth) {
        Paciente paciente = paciente(auth);
        Reserva reserva = reservaDe(referencia, paciente);
        reservaService.liberar(referencia, paciente.getId());
        return "redirect:/citas/nueva?doctorId=" + reserva.getDoctorId() + "&info=reserva_liberada";
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
        return "pagos/simulado";
    }

    @PostMapping("/pagos/simulado/{referencia}")
    public String pagarSimulado(@PathVariable String referencia, @RequestParam String resultado, Authentication auth) {
        reservaSimulada(referencia, auth);
        reservaService.pagoSimulado(referencia, "aprobar".equals(resultado));
        return "redirect:/pagos/" + referencia + "/resultado";
    }

    private Reserva reservaSimulada(String referencia, Authentication auth) {
        if (pasarela.wompiConfigurado())
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
        model.addAttribute("cancelarHasta", reserva.getFecha().atTime(reserva.getHora())
                .minusHours(doctor.horasCancelacionEfectivas()));
        if (vista == Vista.CONFIRMADA && reserva.getCitaId() != null)
            citaService.buscarPorId(reserva.getCitaId()).ifPresent(c -> model.addAttribute("cita", c));
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
        if (!pendiente || !pasarela.wompiConfigurado())
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

    /** Lo que el paciente paga en el consultorio; null si el médico no publicó el valor. */
    private static Long saldo(Doctor doctor, Reserva reserva) {
        if (doctor.getPrecioConsulta() == null || doctor.getPrecioConsulta() <= 0)
            return null;
        return Math.max(0, doctor.getPrecioConsulta() - reserva.getMontoPesos());
    }
}
