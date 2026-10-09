package com.gestion.proyectos.controlador;

import com.gestion.proyectos.modelo.ResultadoTriage;
import com.gestion.proyectos.servicio.TriageService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/triage")
public class TriageController {

    static final int MIN_CARACTERES = 10;
    static final int MAX_CARACTERES = 1000;

    private final TriageService triageService;

    public TriageController(TriageService triageService) {
        this.triageService = triageService;
    }

    @GetMapping
    public String formulario() {
        return "triage";
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
