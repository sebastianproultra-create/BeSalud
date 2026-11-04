package com.gestion.proyectos.controlador;

import com.gestion.proyectos.modelo.Liquidacion;
import com.gestion.proyectos.servicio.DoctorService;
import com.gestion.proyectos.servicio.GananciasService;
import com.gestion.proyectos.servicio.OportunidadesService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;

/** "Mis ganancias" del médico, los pagos que registra el administrador y las oportunidades de la plataforma. */
@Controller
public class GananciasController {

    private final GananciasService gananciasService;
    private final OportunidadesService oportunidadesService;
    private final DoctorService doctorService;

    public GananciasController(GananciasService gananciasService, OportunidadesService oportunidadesService,
            DoctorService doctorService) {
        this.gananciasService = gananciasService;
        this.oportunidadesService = oportunidadesService;
        this.doctorService = doctorService;
    }

    @PreAuthorize("hasRole('DOCTOR')")
    @GetMapping("/doctores/ganancias")
    public String misGanancias(@RequestParam(required = false) String mes, Authentication auth, Model model) {
        model.addAttribute("g", gananciasService.panel(doctorId(auth), mesDe(mes)));
        return "doctores/ganancias";
    }

    @PreAuthorize("hasRole('DOCTOR')")
    @GetMapping("/doctores/ganancias/liquidaciones/{codigo}/comprobante")
    public ResponseEntity<byte[]> comprobanteMedico(@PathVariable String codigo, Authentication auth) {
        return archivo(gananciasService.buscarDelDoctor(codigo, doctorId(auth)).orElseThrow());
    }

    @GetMapping("/admin/liquidaciones")
    public String liquidaciones(Model model) {
        model.addAttribute("porLiquidar", gananciasService.porLiquidar());
        model.addAttribute("registradas", gananciasService.registradas());
        model.addAttribute("medios", GananciasService.MEDIOS);
        model.addAttribute("comision", gananciasService.getComisionVigente());
        return "admin/liquidaciones";
    }

    @PostMapping("/admin/liquidaciones/{doctorId}/registrar")
    public String registrar(@PathVariable String doctorId,
            @RequestParam(required = false) String medio,
            @RequestParam(required = false) String numeroOperacion,
            @RequestParam(required = false) String monto,
            @RequestParam(required = false) MultipartFile comprobante,
            Authentication auth) {
        String error = gananciasService.registrar(doctorId, medio, numeroOperacion, DevolucionController.pesos(monto),
                comprobante, auth.getName());
        if (error != null)
            return "redirect:" + UriComponentsBuilder.fromPath("/admin/liquidaciones").queryParam("error", error)
                    .queryParam("doctor", doctorId).fragment("medico-" + doctorId).encode().build().toUriString();
        return "redirect:/admin/liquidaciones?registrada=1" + (gananciasService.avisaPorCorreo(doctorId) ? "&aviso=1" : "") + "#registradas";
    }

    @GetMapping("/admin/liquidaciones/{codigo}/comprobante")
    public ResponseEntity<byte[]> comprobanteAdmin(@PathVariable String codigo) {
        return archivo(gananciasService.buscar(codigo).orElseThrow());
    }

    @GetMapping("/admin/oportunidades")
    public String oportunidades(Model model) {
        model.addAttribute("o", oportunidadesService.calcular());
        return "admin/oportunidades";
    }

    private String doctorId(Authentication auth) {
        return doctorService.buscarPorEmail(auth.getName()).orElseThrow().getId();
    }

    private static ResponseEntity<byte[]> archivo(Liquidacion l) {
        return PacienteController.archivo(l.getComprobanteNombre(), l.getComprobanteTipo(), l.getComprobante());
    }

    private static YearMonth mesDe(String mes) {
        if (mes == null || mes.isBlank())
            return null;
        try {
            return YearMonth.parse(mes);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
