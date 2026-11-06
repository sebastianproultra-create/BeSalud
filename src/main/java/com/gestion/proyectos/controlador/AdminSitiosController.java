package com.gestion.proyectos.controlador;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.gestion.proyectos.modelo.Doctor;
import com.gestion.proyectos.modelo.SitioWeb;
import com.gestion.proyectos.repositorio.PersonaRepositorio;
import com.gestion.proyectos.servicio.SitioWebService;

/**
 * Páginas web alojadas de los médicos, administradas por el equipo de BeSalud (como hacen Doctoralia o Medesk
 * con la "web profesional"): el médico la pide desde su panel y aquí se arma, se revisa y se publica.
 */
@ConditionalOnProperty(name = "app.api-consultorios.habilitada", havingValue = "true")
@Controller
@RequestMapping("/admin/sitios")
public class AdminSitiosController {

    private final SitioWebService sitioService;
    private final PersonaRepositorio personaRepo;
    private final String urlApp;

    public AdminSitiosController(SitioWebService sitioService, PersonaRepositorio personaRepo,
            @Value("${app.url:http://localhost:8080}") String urlApp) {
        this.sitioService = sitioService;
        this.personaRepo = personaRepo;
        this.urlApp = urlApp.replaceAll("/+$", "");
    }

    /** Médicos con el estado de su página: publicada, sin publicar o sin página. Primero los que ya tienen. */
    @GetMapping
    public String lista(Model model) {
        Map<String, SitioWeb> sitios = sitioService.porDoctor();
        List<Doctor> doctores = personaRepo.findAllDoctores().stream()
                .sorted(Comparator.comparing((Doctor d) -> !sitios.containsKey(d.getId()))
                        .thenComparing(d -> !"ACTIVO".equals(d.getEstado()))
                        .thenComparing(d -> (d.getNombre() + " " + d.getApellido()).toLowerCase()))
                .toList();
        model.addAttribute("doctores", doctores);
        model.addAttribute("sitios", sitios);
        model.addAttribute("plantillas", SitioWeb.PLANTILLAS);
        model.addAttribute("urlApp", urlApp);
        return "admin/sitios";
    }

    @GetMapping("/{doctorId}")
    public String editor(@PathVariable String doctorId, Model model) {
        Doctor doctor = doctor(doctorId);
        return editor(model, doctor, sitioService.borrador(doctor), Map.of());
    }

    /** Guarda; con accion=publicar además publica. Si hay errores vuelve al editor con lo escrito. */
    @PostMapping("/{doctorId}")
    public String guardar(@PathVariable String doctorId, @ModelAttribute SitioWebService.Formulario form,
            @RequestParam(defaultValue = "guardar") String accion, Model model, RedirectAttributes flash) {
        Doctor doctor = doctor(doctorId);
        Map<String, String> errores = sitioService.validar(form);
        if (!errores.isEmpty())
            return editor(model, doctor, sitioService.vistaPrevia(doctor, form), errores);

        sitioService.guardar(doctor, form);
        String volver = "redirect:/admin/sitios/" + doctorId;
        if (!"publicar".equals(accion)) {
            flash.addFlashAttribute("exito", "Cambios guardados.");
            return volver;
        }
        try {
            sitioService.publicar(doctor, ServletUriComponentsBuilder.fromCurrentContextPath().toUriString());
            flash.addFlashAttribute("exito", "Página publicada.");
        } catch (IllegalStateException e) {
            flash.addFlashAttribute("error", "Se guardó, pero no se publicó: " + e.getMessage());
        }
        return volver;
    }

    @PostMapping("/{doctorId}/despublicar")
    public String despublicar(@PathVariable String doctorId, RedirectAttributes flash) {
        sitioService.despublicar(doctor(doctorId));
        flash.addFlashAttribute("exito", "La página ya no está visible. Puedes volver a publicarla cuando quieras.");
        return "redirect:/admin/sitios/" + doctorId;
    }

    /**
     * La página tal como quedaría, con lo que se lleva escrito. Se muestra en un iframe del editor;
     * se pinta con srcdoc (fetch + iframe), así que no hace falta relajar X-Frame-Options.
     */
    @GetMapping("/{doctorId}/vista-previa")
    public String vistaPrevia(@PathVariable String doctorId, @ModelAttribute SitioWebService.Formulario form,
            @RequestParam(defaultValue = "false") boolean guardado, Model model) {
        Doctor doctor = doctor(doctorId);
        SitioWeb sitio = guardado ? sitioService.borrador(doctor) : sitioService.vistaPrevia(doctor, form);
        model.addAttribute("pagina", sitioService.pagina(sitio, doctor));
        model.addAttribute("vistaPrevia", true);
        return "sitio/pagina";
    }

    private String editor(Model model, Doctor doctor, SitioWeb sitio, Map<String, String> errores) {
        model.addAttribute("doctor", doctor);
        model.addAttribute("sitio", sitio);
        model.addAttribute("errores", errores);
        model.addAttribute("plantillas", SitioWeb.PLANTILLAS);
        model.addAttribute("colores", SitioWeb.COLORES);
        model.addAttribute("colorPorPlantilla", SitioWeb.COLOR_POR_PLANTILLA);
        model.addAttribute("guardado", sitio.getId() != null);
        model.addAttribute("urlSitio", urlApp + "/sitio/" + sitio.getSlug());
        model.addAttribute("doctorActivo", "ACTIVO".equals(doctor.getEstado()));
        return "admin/sitio-editor";
    }

    private Doctor doctor(String doctorId) {
        return personaRepo.findById(doctorId).filter(Doctor.class::isInstance).map(Doctor.class::cast)
                .orElseThrow(NoSuchElementException::new);
    }
}
