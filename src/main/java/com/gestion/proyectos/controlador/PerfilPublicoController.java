package com.gestion.proyectos.controlador;

import com.gestion.proyectos.modelo.Doctor;
import com.gestion.proyectos.servicio.AsistenteService;
import com.gestion.proyectos.servicio.CitaService;
import com.gestion.proyectos.servicio.PerfilPublicoService;
import com.gestion.proyectos.servicio.ReservaService;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

/** Página pública de reservas de cada profesional: el enlace que comparte en su WhatsApp o Instagram. */
@Controller
public class PerfilPublicoController {

    private static final int DIAS_VISIBLES = 5;
    private static final int HORAS_POR_DIA = 6;

    private final PerfilPublicoService perfilService;
    private final CitaService citaService;
    private final AsistenteService asistenteService;
    private final ReservaService reservaService;

    public PerfilPublicoController(PerfilPublicoService perfilService, CitaService citaService,
            AsistenteService asistenteService, ReservaService reservaService) {
        this.perfilService = perfilService;
        this.citaService = citaService;
        this.asistenteService = asistenteService;
        this.reservaService = reservaService;
    }

    @GetMapping("/p/{slug}")
    public String perfil(@PathVariable String slug, Model model, CsrfToken csrf, Authentication auth) {
        // El chat necesita el token CSRF: se genera antes de empezar a enviar la página
        csrf.getToken();
        Doctor doctor = perfilService.buscarActivoPorSlug(slug).orElseThrow(NoSuchElementException::new);

        Map<String, List<String>> proximos = new LinkedHashMap<>();
        // Sin precio publicado no hay reservas en línea: no se muestran horarios
        Map<String, List<String>> libres = doctor.recibeReservas()
                ? citaService.slotsDisponibles(doctor.getId(), null, null, null) : Map.of();
        for (var e : libres.entrySet()) {
            if (proximos.size() == DIAS_VISIBLES)
                break;
            proximos.put(e.getKey(), e.getValue().subList(0, Math.min(HORAS_POR_DIA, e.getValue().size())));
        }
        boolean esPaciente = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_PACIENTE".equals(a.getAuthority()));
        model.addAttribute("doctor", doctor);
        model.addAttribute("proximos", proximos);
        model.addAttribute("fechas", proximos.keySet().stream().map(LocalDate::parse).toList());
        model.addAttribute("esPaciente", esPaciente);
        model.addAttribute("sesionIniciada", auth != null && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken));
        model.addAttribute("minutosParaPagar", reservaService.getMinutosParaPagar());
        return "publico/profesional";
    }

    public record Pregunta(String mensaje) {}

    @PostMapping("/p/{slug}/asistente")
    @ResponseBody
    public ResponseEntity<AsistenteService.Respuesta> asistente(@PathVariable String slug, @RequestBody Pregunta pregunta) {
        return perfilService.buscarActivoPorSlug(slug)
                .map(d -> ResponseEntity.ok(asistenteService.responder(d, pregunta == null ? null : pregunta.mensaje())))
                .orElse(ResponseEntity.notFound().build());
    }

    /** Asistente del portal del paciente: busca entre todos los profesionales activos. */
    @PostMapping("/pacientes/asistente")
    @ResponseBody
    public AsistenteService.Respuesta asistenteGeneral(@RequestBody Pregunta pregunta) {
        return asistenteService.responderGeneral(
                perfilService.activos().stream().filter(Doctor::recibeReservas).toList(), pregunta == null ? null : pregunta.mensaje());
    }
}
