package com.gestion.proyectos.controlador;

import com.gestion.proyectos.modelo.Cita;
import com.gestion.proyectos.modelo.EstadoCita;
import com.gestion.proyectos.modelo.Paciente;
import com.gestion.proyectos.repositorio.PersonaRepositorio;
import com.gestion.proyectos.servicio.CitaService;
import com.gestion.proyectos.servicio.WhatsappService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * El bot de WhatsApp reenvía aquí lo que el paciente toca en "¿Vas a ir a tu cita?", para que confirmar o
 * cancelar haga exactamente lo mismo que los botones del portal (incluida la devolución del abono).
 */
@RestController
public class WhatsappController {

    public record Respuesta(String payload, String telefono) {}

    private final CitaService citaService;
    private final PersonaRepositorio personaRepo;
    private final String secreto;
    private final String urlApp;

    public WhatsappController(CitaService citaService, PersonaRepositorio personaRepo,
            @Value("${app.whatsapp.bot-secret:}") String secreto,
            @Value("${app.url:http://localhost:8080}") String urlApp) {
        this.citaService = citaService;
        this.personaRepo = personaRepo;
        this.secreto = secreto == null ? "" : secreto.trim();
        this.urlApp = urlApp.replaceAll("/+$", "");
    }

    @PostMapping("/whatsapp/asistencia")
    public ResponseEntity<Map<String, String>> asistencia(@RequestHeader(value = "X-Bot-Secret", required = false) String clave,
            @RequestBody Respuesta r) {
        if (secreto.isBlank() || clave == null
                || !MessageDigest.isEqual(secreto.getBytes(StandardCharsets.UTF_8), clave.getBytes(StandardCharsets.UTF_8)))
            return ResponseEntity.status(401).build();

        String[] partes = r.payload() == null ? new String[0] : r.payload().split(":", 2);
        if (partes.length != 2)
            return ResponseEntity.notFound().build();
        Boolean asistira = switch (partes[0]) {
            case "ASISTIRE", "CONFIRMAR" -> true;
            case "NO_IRE", "CANCELAR" -> false;
            default -> null;
        };
        Cita cita = citaService.buscarPorId(partes[1]).orElse(null);
        if (asistira == null || cita == null)
            return ResponseEntity.notFound().build();

        // Solo responde el dueño de la cita: el número que toca el botón debe ser el del paciente
        String telefonoPaciente = personaRepo.findById(cita.getPacienteId())
                .filter(p -> p instanceof Paciente).map(p -> WhatsappService.numero(p.getTelefono())).orElse(null);
        if (telefonoPaciente == null || !telefonoPaciente.equals(r.telefono()))
            return ResponseEntity.status(403).build();

        if (cita.getEstado() != EstadoCita.PENDIENTE)
            return ResponseEntity.ok(Map.of("texto", "Esta cita ya no está pendiente. Revisa tus citas en BeSalud: "
                    + urlApp + "/pacientes/landing"));
        boolean ok = citaService.responderAsistencia(cita, asistira);
        String texto = !ok ? "No pudimos registrar tu respuesta. Respóndela en BeSalud: " + urlApp + "/pacientes/landing"
                : asistira ? "✅ ¡Gracias! Confirmaste tu asistencia. Te esperamos; recuerda llegar 10 minutos antes."
                : "Listo, cancelamos tu cita. En un momento te llega el detalle por aquí y por correo.";
        return ResponseEntity.ok(Map.of("texto", texto));
    }
}
