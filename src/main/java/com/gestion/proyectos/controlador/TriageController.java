package com.gestion.proyectos.controlador;

import com.gestion.proyectos.modelo.ResultadoTriage;
import com.gestion.proyectos.servicio.TriageService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/triage")
public class TriageController {

    static final int MIN_CARACTERES = 10;
    static final int MAX_CARACTERES = 1000;
    static final int MAX_MOTIVO = 300;

    private final TriageService triageService;

    public TriageController(TriageService triageService) {
        this.triageService = triageService;
    }

    @GetMapping
    public String formulario() {
        return "triage";
    }

    /** Lleva al agendamiento sin poner el resumen de síntomas en la URL (dato de salud). */
    @PostMapping("/agendar")
    public String agendar(@RequestParam String doctorId, @RequestParam(required = false) String motivo,
            RedirectAttributes redirectAttributes) {
        String resumen = motivo == null ? "" : motivo.trim();
        if (resumen.length() > MAX_MOTIVO)
            resumen = resumen.substring(0, MAX_MOTIVO);
        redirectAttributes.addFlashAttribute("motivoSugerido", resumen);
        redirectAttributes.addAttribute("doctorId", doctorId);
        return "redirect:/citas/nueva";
    }

    @PostMapping
    public String evaluar(@RequestParam(required = false) String sintomas, Model model) {
        String texto = sintomas == null ? "" : sintomas.trim();
        model.addAttribute("sintomas", texto);

        if (texto.length() < MIN_CARACTERES) {
            model.addAttribute("error", "Cuéntanos un poco más sobre lo que sientes (mínimo " + MIN_CARACTERES + " caracteres).");
            return "triage";
        }
        if (texto.length() > MAX_CARACTERES) {
            model.addAttribute("error", "El texto es muy largo (máximo " + MAX_CARACTERES + " caracteres).");
            return "triage";
        }

        ResultadoTriage resultado = triageService.evaluar(texto);
        model.addAttribute("resultado", resultado);
        model.addAttribute("doctores", triageService.doctoresSugeridos(resultado.especialidad()));
        return "triage";
    }
}
