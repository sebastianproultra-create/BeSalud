package com.gestion.proyectos.controlador;

import com.fasterxml.jackson.databind.JsonNode;
import com.gestion.proyectos.modelo.Paciente;
import com.gestion.proyectos.modelo.Reserva;
import com.gestion.proyectos.servicio.CitaService;
import com.gestion.proyectos.servicio.PasarelaPagos;
import com.gestion.proyectos.servicio.ReservaService;
import com.gestion.proyectos.util.Reloj;
import java.util.NoSuchElementException;
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

/** Resultado del pago del abono: pasarela simulada, regreso desde Wompi y eventos firmados de Wompi. */
@Controller
public class PagoController {

    private final ReservaService reservaService;
    private final PasarelaPagos pasarela;
    private final CitaService citaService;

    public PagoController(ReservaService reservaService, PasarelaPagos pasarela, CitaService citaService) {
        this.reservaService = reservaService;
        this.pasarela = pasarela;
        this.citaService = citaService;
    }

    /** Pasarela de demostración: solo existe mientras Wompi no esté configurado. */
    @GetMapping("/pagos/simulado/{referencia}")
    public String simulado(@PathVariable String referencia, Authentication auth, Model model) {
        Reserva reserva = reservaDelPaciente(referencia, auth);
        if (reserva.getEstado() != Reserva.Estado.ESPERANDO_PAGO || reserva.vencida(Reloj.ahora()))
            return "redirect:/citas/nueva?doctorId=" + reserva.getDoctorId() + "&error=reserva_vencida";
        model.addAttribute("reserva", reserva);
        model.addAttribute("doctor", citaService.buscarDoctorPorId(reserva.getDoctorId()).orElseThrow());
        return "pagos/simulado";
    }

    @PostMapping("/pagos/simulado/{referencia}")
    public String pagarSimulado(@PathVariable String referencia, @RequestParam String resultado, Authentication auth) {
        Reserva reserva = reservaDelPaciente(referencia, auth);
        return destino(reservaService.pagoSimulado(referencia, "aprobar".equals(resultado)), reserva);
    }

    private Reserva reservaDelPaciente(String referencia, Authentication auth) {
        if (pasarela.wompiConfigurado())
            throw new NoSuchElementException();
        Paciente paciente = citaService.buscarPacientePorEmail(auth.getName()).orElseThrow();
        return reservaService.buscarPorReferencia(referencia)
                .filter(r -> paciente.getId().equals(r.getPacienteId()))
                .orElseThrow(NoSuchElementException::new);
    }

    /**
     * Wompi devuelve al paciente aquí con ?id=TRANSACCION. No se confía en la URL: se pregunta a Wompi
     * el estado, la referencia y el monto de esa transacción.
     */
    @GetMapping("/pagos/retorno")
    public String retorno(@RequestParam(required = false) String id) {
        return pasarela.consultar(id)
                .map(t -> destino(reservaService.confirmarPago(t.referencia(), t.id(), t.estado(), t.montoCentavos(),
                        t.moneda()), reservaService.buscarPorReferencia(t.referencia()).orElse(null)))
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

    private static String destino(ReservaService.Resultado resultado, Reserva reserva) {
        return switch (resultado) {
            case APROBADA -> "redirect:/pacientes/landing?success=cita_agendada";
            case RECHAZADA -> reserva != null
                    ? "redirect:/citas/nueva?doctorId=" + reserva.getDoctorId() + "&error=pago_rechazado"
                    : "redirect:/pacientes/landing?error=pago_rechazado";
            case PENDIENTE -> "redirect:/pacientes/landing?success=pago_pendiente";
            case CONFLICTO -> "redirect:/pacientes/landing?error=pago_conflicto";
            case EXPIRADA, NO_EXISTE -> "redirect:/pacientes/landing?error=pago_sin_confirmar";
        };
    }
}
