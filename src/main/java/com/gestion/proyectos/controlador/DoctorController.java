package com.gestion.proyectos.controlador;

import com.gestion.proyectos.modelo.Doctor;
import com.gestion.proyectos.modelo.HorarioAtencion;
import com.gestion.proyectos.modelo.UserRegistrationDTO;
import com.gestion.proyectos.servicio.DoctorService;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.HashMap;
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
            @RequestParam(required = false) String estado,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search) {
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
            model.addAttribute("diasNombre", Map.of(
                    "MONDAY", "Lunes", "TUESDAY", "Martes", "WEDNESDAY", "Miércoles",
                    "THURSDAY", "Jueves", "FRIDAY", "Viernes", "SATURDAY", "Sábado", "SUNDAY", "Domingo"));
            model.addAttribute("diasSemana", List.of(
                    new String[] { "MONDAY", "Lu", "Lunes" },
                    new String[] { "TUESDAY", "Ma", "Martes" },
                    new String[] { "WEDNESDAY", "Mi", "Miércoles" },
                    new String[] { "THURSDAY", "Ju", "Jueves" },
                    new String[] { "FRIDAY", "Vi", "Viernes" },
                    new String[] { "SATURDAY", "Sá", "Sábado" },
                    new String[] { "SUNDAY", "Do", "Domingo" }));
            return "doctor_dashboard";
        }

        var doctoresPage = doctorService.listarDoctoresPaginated(page, size, especialidad, search);
        model.addAttribute("doctores", doctoresPage);
        model.addAttribute("filtroEspecialidad", especialidad == null ? "" : especialidad);
        model.addAttribute("search", search == null ? "" : search);
        model.addAttribute("page", page);
        model.addAttribute("size", size);
        model.addAttribute("doctor", new Doctor());
        return "doctores";
    }

    @PostMapping("/guardar")
    public String guardar(@ModelAttribute("formDoctor") UserRegistrationDTO form,
            RedirectAttributes redirectAttributes) {
        // Se conservan los datos escritos (nunca la contraseña) para no obligar a
        // rellenar todo de nuevo
        Map<String, String> datos = new HashMap<>();
        datos.put("nombre", form.getNombre());
        datos.put("apellido", form.getApellido());
        datos.put("identificacion", form.getIdentificacion());
        datos.put("telefono", form.getTelefono());
        datos.put("email", form.getEmail());
        datos.put("especialidad", form.getEspecialidad());
        datos.put("fechaNacimiento", form.getFechaNacimiento());

        String error = doctorService.registrarPorAdmin(form);
        if (error != null) {
            redirectAttributes.addFlashAttribute("errorRegistro", error);
            redirectAttributes.addFlashAttribute("formDoctor", datos);
            return "redirect:/doctores#registrar";
        }
        redirectAttributes.addFlashAttribute("exitoRegistro",
                "Doctor registrado correctamente. Su cuenta queda inactiva hasta que la actives desde el panel de administración.");
        return REDIRECT_DOCTORES;
    }

    @PostMapping("/{id}/eliminar")
    public String eliminar(@PathVariable String id) {
        doctorService.eliminar(id);
        return REDIRECT_DOCTORES;
    }

    @PostMapping("/perfil/foto")
    public String actualizarFoto(@RequestParam("foto") MultipartFile foto) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Doctor doctor = doctorService.buscarPorEmail(auth.getName()).orElse(null);
        if (doctor == null)
            return REDIRECT_DOCTORES;
        String error = doctorService.actualizarFoto(doctor, foto);
        if (error != null)
            return "redirect:/doctores?fotoError=" + error + "#perfil";
        return "redirect:/doctores?fotoOk=1#perfil";
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
        if (days == null || days.isEmpty())
            return "redirect:/doctores?error=no_days_selected";

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Doctor doctor = doctorService.buscarPorEmail(auth.getName()).orElseThrow();

        String error = doctorService.guardarHorariosSemanales(doctor.getId(), days, allParams, duracionCitaMinutos);
        if (error != null)
            return "redirect:/doctores?error=" + error;
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