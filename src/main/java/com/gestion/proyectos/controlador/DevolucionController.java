package com.gestion.proyectos.controlador;

import com.gestion.proyectos.modelo.Devolucion;
import com.gestion.proyectos.servicio.DevolucionService;
import com.gestion.proyectos.servicio.PacienteService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

/** El administrador registra las devoluciones con su comprobante; el paciente las revisa y confirma. */
@Controller
public class DevolucionController {

    private final DevolucionService devolucionService;
    private final PacienteService pacienteService;

    public DevolucionController(DevolucionService devolucionService, PacienteService pacienteService) {
        this.devolucionService = devolucionService;
        this.pacienteService = pacienteService;
    }

    @GetMapping("/admin/devoluciones")
    public String panel(Model model) {
        model.addAttribute("pendientes", devolucionService.pendientes());
        model.addAttribute("registradas", devolucionService.registradas());
        model.addAttribute("medios", DevolucionService.MEDIOS);
        model.addAttribute("diasPlazo", DevolucionService.DIAS_PLAZO);
        return "admin/devoluciones";
    }

    @PostMapping("/admin/devoluciones/{origen}/registrar")
    public String registrar(@PathVariable String origen,
            @RequestParam(required = false) String medio,
            @RequestParam(required = false) String numeroOperacion,
            @RequestParam(required = false) String monto,
            @RequestParam(required = false) MultipartFile comprobante,
            Authentication auth) {
        String error = devolucionService.registrar(origen, medio, numeroOperacion, pesos(monto), comprobante, auth.getName());
        if (error != null)
            return "redirect:" + UriComponentsBuilder.fromPath("/admin/devoluciones").queryParam("error", error)
                    .queryParam("origen", origen).fragment(origen).encode().build().toUriString();
        return "redirect:/admin/devoluciones?registrada=1#registradas";
    }

    @GetMapping("/admin/devoluciones/{codigo}/comprobante")
    public ResponseEntity<byte[]> comprobanteAdmin(@PathVariable String codigo) {
        return archivo(devolucionService.buscar(codigo).orElseThrow());
    }

    @GetMapping("/pacientes/devoluciones/{codigo}")
    public String constancia(@PathVariable String codigo, Authentication auth, Model model) {
        model.addAttribute("devolucion",
                devolucionService.buscarDelPaciente(codigo, pacienteId(auth)).orElseThrow());
        return "pacientes/devolucion";
    }

    @GetMapping("/pacientes/devoluciones/{codigo}/comprobante")
    public ResponseEntity<byte[]> comprobantePaciente(@PathVariable String codigo, Authentication auth) {
        return archivo(devolucionService.buscarDelPaciente(codigo, pacienteId(auth)).orElseThrow());
    }

    @PostMapping("/pacientes/devoluciones/{codigo}/confirmar")
    public String confirmar(@PathVariable String codigo, Authentication auth) {
        devolucionService.confirmar(codigo, pacienteId(auth));
        return "redirect:/pacientes/devoluciones/" + codigo + "?confirmada=1";
    }

    @PostMapping("/pacientes/devoluciones/{codigo}/reclamar")
    public String reclamar(@PathVariable String codigo, @RequestParam(required = false) String nota, Authentication auth) {
        devolucionService.reclamar(codigo, pacienteId(auth), nota);
        return "redirect:/pacientes/devoluciones/" + codigo + "?reclamada=1";
    }

    private String pacienteId(Authentication auth) {
        return pacienteService.buscarPorEmail(auth.getName()).orElseThrow().getId();
    }

    private static ResponseEntity<byte[]> archivo(Devolucion d) {
        return PacienteController.archivo(d.getComprobanteNombre(), d.getComprobanteTipo(), d.getComprobante());
    }

    /** Acepta "80000", "80.000" o "$80.000"; null si no es un número. */
    static Long pesos(String monto) {
        String digitos = monto == null ? "" : monto.replaceAll("[$.\\s]", "");
        return digitos.matches("\\d{1,12}") ? Long.valueOf(digitos) : null;
    }
}
