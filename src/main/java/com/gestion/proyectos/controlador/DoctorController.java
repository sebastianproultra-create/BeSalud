package com.gestion.proyectos.controlador;

import com.gestion.proyectos.util.Reloj;
import com.gestion.proyectos.modelo.CausaExterna;
import com.gestion.proyectos.modelo.Dictamen;
import com.gestion.proyectos.modelo.Doctor;
import com.gestion.proyectos.modelo.HorarioAtencion;
import com.gestion.proyectos.modelo.TipoConsulta;
import com.gestion.proyectos.modelo.TipoDiagnostico;
import com.gestion.proyectos.modelo.UserRegistrationDTO;
import com.gestion.proyectos.modelo.ViaAdministracion;
import com.gestion.proyectos.servicio.DictamenVista;
import com.gestion.proyectos.servicio.DoctorService;
import com.gestion.proyectos.servicio.PanelDoctorService;
import com.gestion.proyectos.servicio.PerfilPublicoService;
import org.springframework.beans.factory.annotation.Value;

import static com.gestion.proyectos.util.ValidacionUtil.esVacio;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
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

    private static final int POR_PAGINA = 8;

    private final DoctorService doctorService;
    private final PanelDoctorService panelDoctorService;
    private final PerfilPublicoService perfilPublicoService;
    private final String urlApp;

    public DoctorController(DoctorService doctorService, PanelDoctorService panelDoctorService,
            PerfilPublicoService perfilPublicoService, @Value("${app.url:http://localhost:8080}") String urlApp) {
        this.doctorService = doctorService;
        this.panelDoctorService = panelDoctorService;
        this.perfilPublicoService = perfilPublicoService;
        this.urlApp = urlApp.replaceAll("/+$", "");
    }

    private static String parametros(String... claveValor) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < claveValor.length; i += 2) {
            String v = claveValor[i + 1];
            if (!esVacio(v))
                sb.append(claveValor[i]).append('=').append(java.net.URLEncoder.encode(v.trim(), java.nio.charset.StandardCharsets.UTF_8)).append('&');
        }
        return sb.toString();
    }

    @GetMapping
    public String listar(Model model,
            @RequestParam(required = false) String especialidad,
            @RequestParam(required = false) String estado,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int pc,
            @RequestParam(required = false) String qp,
            @RequestParam(defaultValue = "0") int pp) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String role = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElse("");

        if ("ROLE_DOCTOR".equals(role)) {
            Doctor doctor = doctorService.buscarPorEmail(auth.getName()).orElseThrow();
            String filtroEstado = PanelDoctorService.normalizarFiltro(estado);
            String citaFija = (String) model.asMap().get("dictamenCitaId");
            var panelCitas = panelDoctorService.citas(doctor.getId(), filtroEstado, q, pc, POR_PAGINA, citaFija);
            var panelPacientes = panelDoctorService.pacientes(doctor.getId(), qp, pp, POR_PAGINA);
            var weeklyAvailability = doctorService.weeklyAvailability(doctor.getId());

            model.addAttribute("doctor", doctor);
            model.addAttribute("enlacePublico", urlApp + "/p/" + perfilPublicoService.asegurarSlug(doctor));
            model.addAttribute("horarios", doctorService.horariosDelDoctor(doctor.getId()));
            model.addAttribute("horario", new HorarioAtencion());
            model.addAttribute("citasPage", panelCitas.pagina());
            model.addAttribute("citas", panelCitas.pagina().getContent());
            model.addAttribute("conteos", panelCitas.conteos());
            model.addAttribute("pacienteNombres", panelCitas.pacienteNombres());
            model.addAttribute("filtroEstado", filtroEstado);
            model.addAttribute("busquedaCitas", q == null ? "" : q.trim());
            model.addAttribute("busquedaPacientes", qp == null ? "" : qp.trim());
            model.addAttribute("paramsCitas", parametros("estado", "TODAS".equals(filtroEstado) ? "" : filtroEstado,
                    "q", q, "qp", qp, "pp", pp > 0 ? String.valueOf(pp) : ""));
            model.addAttribute("paramsFiltro", parametros("q", q, "qp", qp, "pp", pp > 0 ? String.valueOf(pp) : ""));
            model.addAttribute("paginaPacientes", pp);
            model.addAttribute("paramsLimpiarCitas", parametros("estado", "TODAS".equals(filtroEstado) ? "" : filtroEstado,
                    "qp", qp, "pp", pp > 0 ? String.valueOf(pp) : ""));
            model.addAttribute("paginaCitas", pc);
            model.addAttribute("paramsPacientes", parametros("estado", "TODAS".equals(filtroEstado) ? "" : filtroEstado,
                    "q", q, "pc", pc > 0 ? String.valueOf(pc) : "", "qp", qp));
            model.addAttribute("hoy", Reloj.hoy());
            model.addAttribute("abonos", doctorService.resumenAbonos(doctor.getId()));
            model.addAttribute("dictamenDataMap", panelCitas.dictamenes());
            model.addAttribute("tiposConsulta", TipoConsulta.values());
            model.addAttribute("causasExternas", CausaExterna.values());
            model.addAttribute("tiposDiagnostico", TipoDiagnostico.values());
            model.addAttribute("viasAdministracion", ViaAdministracion.values());
            model.addAttribute("pacientesPage", panelPacientes.pagina());
            model.addAttribute("misPacientes", panelPacientes.pagina().getContent());
            model.addAttribute("totalPacientes", panelPacientes.total());
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
            return "doctores/dashboard";
        }

        var doctoresPage = doctorService.listarDoctoresPaginated(page, size, especialidad, search);
        model.addAttribute("doctores", doctoresPage);
        model.addAttribute("filtroEspecialidad", especialidad == null ? "" : especialidad);
        model.addAttribute("search", search == null ? "" : search);
        model.addAttribute("page", page);
        model.addAttribute("size", size);
        model.addAttribute("doctor", new Doctor());
        return "admin/doctores";
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
    public String eliminar(@PathVariable String id, RedirectAttributes redirectAttributes) {
        String error = doctorService.eliminar(id);
        if (error != null)
            redirectAttributes.addFlashAttribute("errorGeneral",
                    "No se puede eliminar: el doctor tiene citas registradas. Desactívalo desde el panel de administración.");
        else
            redirectAttributes.addFlashAttribute("exitoRegistro", "Doctor eliminado correctamente.");
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

    /** Precio, abono, dirección y política de cancelación que ven los pacientes en la página pública. */
    @PreAuthorize("hasRole('DOCTOR')")
    @PostMapping("/perfil/consulta")
    public String actualizarConsulta(@RequestParam(required = false) Integer precioConsulta,
            @RequestParam(required = false) Integer porcentajeAbono,
            @RequestParam(required = false) String direccionConsultorio,
            @RequestParam(required = false) Integer horasCancelacion,
            RedirectAttributes redirectAttributes) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Doctor doctor = doctorService.buscarPorEmail(auth.getName()).orElse(null);
        if (doctor == null)
            return REDIRECT_DOCTORES;
        String error = perfilPublicoService.guardarConsulta(doctor, precioConsulta, porcentajeAbono,
                direccionConsultorio, horasCancelacion);
        if (error != null)
            redirectAttributes.addFlashAttribute("errorConsulta", error);
        else
            redirectAttributes.addFlashAttribute("exitoConsulta", "Cambios guardados.");
        return "redirect:/doctores#perfil";
    }

    @GetMapping("/{id}/horarios")
    public String verHorarios(@PathVariable String id, Model model) {
        Doctor doctor = doctorService.buscarPorId(id).orElseThrow();
        model.addAttribute("doctor", doctor);
        model.addAttribute("horarios", doctorService.horariosDelDoctor(id));
        model.addAttribute("horario", new HorarioAtencion());
        return "doctores/horarios";
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
            @ModelAttribute("dictamen") Dictamen datos, BindingResult binding,
            RedirectAttributes redirectAttributes) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Doctor doctor = doctorService.buscarPorEmail(auth.getName()).orElseThrow();
        String error = binding.hasErrors()
                ? "Hay valores con formato inválido (revise signos vitales, cantidades y listas desplegables)."
                : doctorService.guardarDictamen(id, doctor, datos);
        if (error != null) {
            // Se devuelve lo escrito para reabrir el formulario sin perder el trabajo del médico
            redirectAttributes.addFlashAttribute("errorDictamen", error);
            redirectAttributes.addFlashAttribute("dictamenCitaId", id);
            redirectAttributes.addFlashAttribute("dictamenPrevio", DictamenVista.de(datos));
            return REDIRECT_DOCTORES + "#citas";
        }
        redirectAttributes.addFlashAttribute("exitoDictamen", "Dictamen guardado correctamente.");
        return REDIRECT_DOCTORES;
    }

    /** Limita cuántas filas de diagnósticos o medicamentos puede crear el binder. */
    @InitBinder("dictamen")
    void configurarDictamen(WebDataBinder binder) {
        binder.setAutoGrowCollectionLimit(32);
    }

    @PreAuthorize("hasRole('DOCTOR')")
    @PostMapping("/citas/{id}/abono-devuelto")
    public String marcarAbonoDevuelto(@PathVariable String id, RedirectAttributes redirectAttributes) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Doctor doctor = doctorService.buscarPorEmail(auth.getName()).orElseThrow();
        if (doctorService.marcarAbonoDevuelto(id, doctor.getId()))
            redirectAttributes.addFlashAttribute("exitoAbonos", "Listo: le avisamos al paciente que le devolviste el abono.");
        return REDIRECT_DOCTORES + "#abonos";
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