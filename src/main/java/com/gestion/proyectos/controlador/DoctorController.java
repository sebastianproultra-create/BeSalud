package com.gestion.proyectos.controlador;

import static com.gestion.proyectos.util.ValidacionUtil.esVacio;

import com.gestion.proyectos.modelo.Doctor;
import com.gestion.proyectos.modelo.HorarioAtencion;
import com.gestion.proyectos.servicio.DoctorService;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/doctores")
public class DoctorController {

    private static final String REDIRECT_DOCTORES = "redirect:/doctores";

    private final DoctorService doctorService;

    public DoctorController(DoctorService doctorService) {
        this.doctorService = doctorService;
    }

    @GetMapping
    public String listar(Model model,
                         @RequestParam(required = false) String especialidad,
                         @RequestParam(required = false) String estado) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String role = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElse("");

        if ("ROLE_DOCTOR".equals(role)) {
            Doctor doctor = doctorService.buscarPorEmail(auth.getName()).orElseThrow();
            String filtroEstado = estado != null ? estado : "";
            var citas = doctorService.citasDelDoctor(doctor.getId(), filtroEstado);
            var weeklyAvailability = doctorService.weeklyAvailability(doctor.getId());

            model.addAttribute("doctor", doctor);
            model.addAttribute("horarios", doctorService.horariosDelDoctor(doctor.getId()));
            model.addAttribute("horario", new HorarioAtencion());
            model.addAttribute("citas", citas);
            model.addAttribute("pacienteNombres", doctorService.mapPacienteNombres(citas));
            model.addAttribute("filtroEstado", filtroEstado);
            model.addAttribute("hoy", LocalDate.now());
            model.addAttribute("dictamenDataMap", doctorService.dictamenDataMap(citas));
            model.addAttribute("misPacientes", doctorService.pacientesDelDoctor(doctor.getId()));
            model.addAttribute("weeklyAvailability", weeklyAvailability);
            model.addAttribute("weeklySlotTotal", doctorService.weeklySlotTotal(weeklyAvailability));
            model.addAttribute("dailySlotCounts", doctorService.dailySlotCounts(weeklyAvailability));
            model.addAttribute("diasSemana", List.of(
                new String[]{"MONDAY","Lu","Lunes"},
                new String[]{"TUESDAY","Ma","Martes"},
                new String[]{"WEDNESDAY","Mi","Miércoles"},
                new String[]{"THURSDAY","Ju","Jueves"},
                new String[]{"FRIDAY","Vi","Viernes"},
                new String[]{"SATURDAY","Sá","Sábado"},
                new String[]{"SUNDAY","Do","Domingo"}
            ));
            return "doctor_dashboard";
        }

        List<Doctor> doctores = (especialidad != null && !especialidad.isEmpty())
                ? doctorService.listarPorEspecialidad(especialidad)
                : doctorService.listarTodos();
        model.addAttribute("doctores", doctores);
        model.addAttribute("filtroEspecialidad", especialidad == null ? "" : especialidad);
        model.addAttribute("doctor", new Doctor());
        return "doctores";
    }

    @PostMapping("/guardar")
    public String guardar(@ModelAttribute Doctor doctor) {
        if (esVacio(doctor.getNombre())) return "redirect:/doctores?error=nombre_requerido";
        if (esVacio(doctor.getApellido())) return "redirect:/doctores?error=apellido_requerido";
        if (esVacio(doctor.getEmail())) return "redirect:/doctores?error=email_requerido";
        if (!doctor.getEmail().trim().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))
            return "redirect:/doctores?error=email_invalido";
        if (esVacio(doctor.getEspecialidad())) return "redirect:/doctores?error=especialidad_requerida";
        if (esVacio(doctor.getId()) && doctorService.emailDuplicado(doctor.getEmail()))
            return "redirect:/doctores?error=email_duplicado";
        doctorService.guardar(doctor);
        return REDIRECT_DOCTORES;
    }

    @PostMapping("/{id}/eliminar")
    public String eliminar(@PathVariable String id) {
        doctorService.eliminar(id);
        return REDIRECT_DOCTORES;
    }

    @GetMapping("/{id}/horarios")
    public String verHorarios(@PathVariable String id, Model model) {
        Doctor doctor = doctorService.buscarPorId(id).orElseThrow();
        model.addAttribute("doctor", doctor);
        model.addAttribute("horarios", doctorService.horariosDelDoctor(id));
        model.addAttribute("horario", new HorarioAtencion());
        return "horarios";
    }

    @PreAuthorize("hasRole('DOCTOR')")
    @PostMapping("/horarios/guardar")
    public String guardarHorario(@RequestParam(required = false) List<String> days,
            @RequestParam Map<String, String> allParams,
            @RequestParam int duracionCitaMinutos) {
        if (days == null || days.isEmpty()) return "redirect:/doctores?error=no_days_selected";

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Doctor doctor = doctorService.buscarPorEmail(auth.getName()).orElseThrow();

        for (String day : days) {
            String error = doctorService.guardarHorarioDia(doctor.getId(), day, allParams, duracionCitaMinutos);
            if (error != null) return "redirect:/doctores?error=" + error;
        }
        return REDIRECT_DOCTORES;
    }

    @PreAuthorize("hasRole('DOCTOR')")
    @PostMapping("/horarios/{id}/eliminar")
    public String eliminarHorario(@PathVariable String id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Doctor doctor = doctorService.buscarPorEmail(auth.getName()).orElseThrow();
        doctorService.eliminarHorario(id, doctor.getId());
        return REDIRECT_DOCTORES;
    }

    @PreAuthorize("hasRole('DOCTOR')")
    @PostMapping("/citas/{id}/asistio")
    public String marcarAsistio(@PathVariable String id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Doctor doctor = doctorService.buscarPorEmail(auth.getName()).orElseThrow();
        doctorService.marcarAsistio(id, doctor.getId());
        return REDIRECT_DOCTORES;
    }

    @PreAuthorize("hasRole('DOCTOR')")
    @PostMapping("/citas/{id}/no-asistio")
    public String marcarNoAsistio(@PathVariable String id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Doctor doctor = doctorService.buscarPorEmail(auth.getName()).orElseThrow();
        doctorService.marcarNoAsistio(id, doctor.getId());
        return REDIRECT_DOCTORES;
    }

    @PreAuthorize("hasRole('DOCTOR')")
    @PostMapping("/citas/{id}/dictamen")
    public String guardarDictamen(@PathVariable String id,
                                   @RequestParam String diagnostico,
                                   @RequestParam String tratamiento,
                                   @RequestParam(required = false) String observaciones) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Doctor doctor = doctorService.buscarPorEmail(auth.getName()).orElseThrow();
        doctorService.guardarDictamen(id, doctor.getId(), diagnostico, tratamiento, observaciones);
        return REDIRECT_DOCTORES;
    }

    @PreAuthorize("hasRole('DOCTOR')")
    @PostMapping("/citas/{id}/cancelar")
    public String cancelarCita(@PathVariable String id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Doctor doctor = doctorService.buscarPorEmail(auth.getName()).orElseThrow();
        doctorService.cancelarCita(id, doctor.getId());
        return REDIRECT_DOCTORES;
    }
}
