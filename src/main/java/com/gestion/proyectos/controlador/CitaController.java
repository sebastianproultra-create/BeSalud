package com.gestion.proyectos.controlador;

import static com.gestion.proyectos.util.ValidacionUtil.esVacio;

import com.gestion.proyectos.modelo.Cita;
import com.gestion.proyectos.modelo.Doctor;
import com.gestion.proyectos.modelo.HorarioAtencion;
import com.gestion.proyectos.modelo.Paciente;
import com.gestion.proyectos.repositorio.CitaRepositorio;
import com.gestion.proyectos.repositorio.DoctorRepositorio;
import com.gestion.proyectos.repositorio.HorarioAtencionRepositorio;
import com.gestion.proyectos.repositorio.PacienteRepositorio;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;
import java.util.ArrayList;

@Controller
@RequestMapping("/citas")
public class CitaController {

    private static final String REDIRECT_PACIENTES_LANDING = "redirect:/pacientes/landing";
    private static final String REDIRECT_LOGIN = "redirect:/login";
    private static final String REDIRECT_CITAS = "redirect:/citas";

    private final CitaRepositorio citaRepo;
    private final DoctorRepositorio doctorRepo;
    private final PacienteRepositorio pacienteRepo;
    private final HorarioAtencionRepositorio horarioRepo;

    public CitaController(CitaRepositorio citaRepo, DoctorRepositorio doctorRepo, PacienteRepositorio pacienteRepo,
            HorarioAtencionRepositorio horarioRepo) {
        this.citaRepo = citaRepo;
        this.doctorRepo = doctorRepo;
        this.pacienteRepo = pacienteRepo;
        this.horarioRepo = horarioRepo;
    }

    @GetMapping
    public String listar(@RequestParam(required = false) String pacienteId,
                         @RequestParam(required = false) String doctorId,
                         Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String role = auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).findFirst().orElse("");
        String email = auth.getName();

        List<Cita> citas;
        if ("ROLE_DOCTOR".equals(role)) {
            Doctor doctor = doctorRepo.findByEmail(email).orElse(null);
            citas = doctor != null ? citaRepo.findByDoctorId(doctor.getId()) : List.of();
        } else if ("ROLE_PACIENTE".equals(role)) {
            Paciente paciente = pacienteRepo.findByEmail(email).orElse(null);
            citas = paciente != null ? citaRepo.findByPacienteId(paciente.getId()) : List.of();
        } else if (pacienteId != null && !pacienteId.isBlank()) {
            citas = citaRepo.findByPacienteId(pacienteId);
        } else if (doctorId != null && !doctorId.isBlank()) {
            citas = citaRepo.findByDoctorId(doctorId);
        } else {
            citas = citaRepo.findAll();
        }

        // Enriquecer con nombres de doctor y paciente
        model.addAttribute("citas", citas);
        model.addAttribute("doctoresMap", doctorRepo.findAllDoctores().stream()
                .collect(Collectors.toMap(Doctor::getId, d -> d.getNombre() + " " + d.getApellido())));
        model.addAttribute("pacientesMap", pacienteRepo.findAll().stream()
                .collect(Collectors.toMap(Paciente::getId, p -> p.getNombre() + " " + p.getApellido())));
        return "citas";
    }

    @GetMapping("/crear")
    public String crear(Model model) {
        model.addAttribute("cita", new Cita());
        model.addAttribute("doctores", doctorRepo.findAllDoctores());
        model.addAttribute("pacientes", pacienteRepo.findAll());
        return "cita_form";
    }

    @GetMapping("/nueva")
    public String nueva(@RequestParam String doctorId, Model model) {
        Doctor doctor = doctorRepo.findById(doctorId).orElse(null);
        if (doctor == null) {
            return REDIRECT_PACIENTES_LANDING;
        }
        model.addAttribute("doctorSeleccionado", doctor);
        model.addAttribute("cita", null);
        model.addAttribute("actionUrl", "/citas/guardar-paciente");
        model.addAttribute("pageTitle", "Agendar Cita Médica");
        model.addAttribute("submitLabel", "Confirmar Cita");
        model.addAttribute("backLink", "/pacientes/landing");

        // Pre-calcular todos los slots disponibles en los próximos 30 días
        LinkedHashMap<String, List<String>> slotsDisponibles = new LinkedHashMap<>();
        LocalDate today = LocalDate.now();
        for (int i = 0; i < 30; i++) {
            LocalDate fecha = today.plusDays(i);
            List<LocalTime> slots = calcularSlotsDisponibles(doctorId, fecha, null, null, null);
            if (!slots.isEmpty()) {
                slotsDisponibles.put(fecha.toString(),
                        slots.stream().map(t -> t.toString().substring(0, 5)).toList());
            }
        }
        model.addAttribute("slotsDisponibles", slotsDisponibles);

        return "cita_form_paciente";
    }

    @GetMapping("/{id}/reprogramar")
    public String reprogramar(@PathVariable String id, Model model) {
        Cita cita = citaRepo.findById(id).orElse(null);
        if (cita == null) {
            return REDIRECT_PACIENTES_LANDING;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return REDIRECT_LOGIN;
        }
        String email = auth.getName();
        Paciente paciente = pacienteRepo.findByEmail(email).orElse(null);
        if (paciente == null || !paciente.getId().equals(cita.getPacienteId())) {
            return REDIRECT_PACIENTES_LANDING;
        }

        Doctor doctor = doctorRepo.findById(cita.getDoctorId()).orElse(null);
        if (doctor == null) {
            return REDIRECT_PACIENTES_LANDING;
        }

        model.addAttribute("doctorSeleccionado", doctor);
        model.addAttribute("cita", cita);
        model.addAttribute("actionUrl", "/citas/" + cita.getId() + "/reprogramar");
        model.addAttribute("pageTitle", "Reprogramar Cita Médica");
        model.addAttribute("submitLabel", "Reprogramar Cita");
        model.addAttribute("backLink", "/pacientes/landing");

        LinkedHashMap<String, List<String>> slotsDisponibles = new LinkedHashMap<>();
        LocalDate today = LocalDate.now();
        for (int i = 0; i < 30; i++) {
            LocalDate fecha = today.plusDays(i);
            List<LocalTime> slots = calcularSlotsDisponibles(doctor.getId(), fecha, cita.getId(), cita.getFecha(), cita.getHora());
            if (!slots.isEmpty()) {
                slotsDisponibles.put(fecha.toString(),
                        slots.stream().map(t -> t.toString().substring(0, 5)).toList());
            }
        }
        model.addAttribute("slotsDisponibles", slotsDisponibles);

        return "cita_form_paciente";
    }

    @PostMapping("/{id}/reprogramar")
    public String guardarReprogramacion(@PathVariable String id,
            @RequestParam String fecha,
            @RequestParam String hora) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return REDIRECT_LOGIN;
        }
        String email = auth.getName();
        Paciente paciente = pacienteRepo.findByEmail(email).orElse(null);
        Cita cita = citaRepo.findById(id).orElse(null);
        if (paciente == null || cita == null || !paciente.getId().equals(cita.getPacienteId())) {
            return REDIRECT_PACIENTES_LANDING;
        }

        if (esVacio(fecha)) return "redirect:/citas/" + id + "/reprogramar?error=fecha_requerida";
        if (esVacio(hora)) return "redirect:/citas/" + id + "/reprogramar?error=hora_requerida";

        LocalDate fechaCita;
        LocalTime horaCita;
        try {
            fechaCita = LocalDate.parse(fecha.trim());
        } catch (Exception e) {
            return "redirect:/citas/" + id + "/reprogramar?error=fecha_invalida";
        }
        try {
            horaCita = LocalTime.parse(hora.trim());
        } catch (Exception e) {
            return "redirect:/citas/" + id + "/reprogramar?error=hora_invalida";
        }

        if (esFechaHoraPasada(fechaCita, horaCita)) {
            return "redirect:/citas/" + id + "/reprogramar?error=fecha_pasada";
        }

        if (!esHorarioValido(cita.getDoctorId(), fechaCita, horaCita)) {
            return "redirect:/citas/" + id + "/reprogramar?error=horario_invalido";
        }

        if (hayConflicto(cita.getDoctorId(), fechaCita, horaCita, cita.getId())) {
            return "redirect:/citas/" + id + "/reprogramar?error=conflicto_cita";
        }

        cita.setFecha(fechaCita);
        cita.setHora(horaCita);
        citaRepo.save(cita);
        return "redirect:/pacientes/landing?success=cita_reprogramada";
    }

    @PostMapping("/guardar")
    public String guardar(@RequestParam(required = false) String doctorId,
            @RequestParam(required = false) String pacienteId,
            @RequestParam(required = false) String fecha,
            @RequestParam(required = false) String hora,
            @RequestParam(required = false) String motivo,
            Model model) {
        if (esVacio(doctorId)) return "redirect:/citas/crear?error=doctor_requerido";
        if (esVacio(pacienteId)) return "redirect:/citas/crear?error=paciente_requerido";
        if (esVacio(fecha)) return "redirect:/citas/crear?error=fecha_requerida";
        if (esVacio(hora)) return "redirect:/citas/crear?error=hora_requerida";
        if (esVacio(motivo)) return "redirect:/citas/crear?error=motivo_requerido";

        LocalDate fechaCita;
        LocalTime horaCita;
        try {
            fechaCita = LocalDate.parse(fecha.trim());
        } catch (Exception e) {
            return "redirect:/citas/crear?error=fecha_invalida";
        }
        try {
            horaCita = LocalTime.parse(hora.trim());
        } catch (Exception e) {
            return "redirect:/citas/crear?error=hora_invalida";
        }
        if (esFechaHoraPasada(fechaCita, horaCita)) {
            return "redirect:/citas/crear?error=fecha_pasada";
        }
        if (!doctorRepo.existsById(doctorId)) return "redirect:/citas/crear?error=doctor_no_existe";
        if (!pacienteRepo.existsById(pacienteId)) return "redirect:/citas/crear?error=paciente_no_existe";
        if (!esHorarioValido(doctorId, fechaCita, horaCita))
            return "redirect:/citas/crear?error=horario_invalido";
        if (hayConflicto(doctorId, fechaCita, horaCita, null))
            return "redirect:/citas/crear?error=conflicto_cita";

        Cita cita = new Cita();
        cita.setDoctorId(doctorId);
        cita.setPacienteId(pacienteId);
        cita.setFecha(fechaCita);
        cita.setHora(horaCita);
        cita.setMotivo(motivo.trim());
        citaRepo.save(cita);
        return REDIRECT_CITAS;
    }

    @PostMapping("/guardar-paciente")
    public String guardarPaciente(@RequestParam(required = false) String doctorId,
            @RequestParam(required = false) String fecha,
            @RequestParam(required = false) String hora,
            @RequestParam(required = false) String motivo) {
        if (esVacio(doctorId)) return "redirect:/pacientes/landing?error=doctor_requerido";
        if (esVacio(fecha)) return "redirect:/citas/nueva?doctorId=" + doctorId + "&error=fecha_requerida";
        if (esVacio(hora)) return "redirect:/citas/nueva?doctorId=" + doctorId + "&error=hora_requerida";
        if (esVacio(motivo)) return "redirect:/citas/nueva?doctorId=" + doctorId + "&error=motivo_requerido";

        LocalDate fechaCita;
        LocalTime horaCita;
        try {
            fechaCita = LocalDate.parse(fecha.trim());
        } catch (Exception e) {
            return "redirect:/citas/nueva?doctorId=" + doctorId + "&error=fecha_invalida";
        }
        try {
            horaCita = LocalTime.parse(hora.trim());
        } catch (Exception e) {
            return "redirect:/citas/nueva?doctorId=" + doctorId + "&error=hora_invalida";
        }

        if (esFechaHoraPasada(fechaCita, horaCita)) {
            return "redirect:/citas/nueva?doctorId=" + doctorId + "&error=fecha_pasada";
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        Paciente paciente = pacienteRepo.findByEmail(email).orElse(null);
        if (paciente == null) {
            return "redirect:/pacientes/landing?error=paciente_no_encontrado";
        }
        if (!esHorarioValido(doctorId, fechaCita, horaCita)) {
            return "redirect:/citas/nueva?doctorId=" + doctorId + "&error=horario_invalido";
        }

        if (hayConflicto(doctorId, fechaCita, horaCita, null)) {
            return "redirect:/citas/nueva?doctorId=" + doctorId + "&error=conflicto_cita";
        }

        Cita cita = new Cita();
        cita.setDoctorId(doctorId);
        cita.setPacienteId(paciente.getId());
        cita.setFecha(fechaCita);
        cita.setHora(horaCita);
        cita.setMotivo(motivo);
        citaRepo.save(cita);
        return "redirect:/pacientes/landing?success=cita_agendada";
    }

    @PostMapping("/{id}/cancelar")
    public String cancelar(@PathVariable String id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return REDIRECT_LOGIN;

        String role = auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).findFirst().orElse("");
        String email = auth.getName();

        Cita cita = citaRepo.findById(id).orElse(null);
        if (cita == null) return REDIRECT_CITAS;

        if ("ROLE_PACIENTE".equals(role)) {
            Paciente paciente = pacienteRepo.findByEmail(email).orElse(null);
            if (paciente == null || !paciente.getId().equals(cita.getPacienteId()))
                return "redirect:/pacientes/landing?error=no_autorizado";
            cita.setEstado(com.gestion.proyectos.modelo.EstadoCita.CANCELADA);
            citaRepo.save(cita);
            return REDIRECT_PACIENTES_LANDING;
        } else if ("ROLE_DOCTOR".equals(role)) {
            Doctor doctor = doctorRepo.findByEmail(email).orElse(null);
            if (doctor == null || !doctor.getId().equals(cita.getDoctorId()))
                return "redirect:/doctores?error=no_autorizado";
            cita.setEstado(com.gestion.proyectos.modelo.EstadoCita.CANCELADA);
            citaRepo.save(cita);
            return "redirect:/doctores";
        } else if ("ROLE_ADMIN".equals(role)) {
            cita.setEstado(com.gestion.proyectos.modelo.EstadoCita.CANCELADA);
            citaRepo.save(cita);
            return REDIRECT_CITAS;
        }
        return "redirect:/citas?error=no_autorizado";
    }

    private boolean esFechaHoraPasada(LocalDate fecha, LocalTime hora) {
        return LocalDateTime.of(fecha, hora).isBefore(LocalDateTime.now());
    }

    private List<LocalTime> calcularSlotsDisponibles(String doctorId, LocalDate fecha, String excludeCitaId, LocalDate fechaActual, LocalTime horaActual) {
        List<HorarioAtencion> horarios = horarioRepo.findByDoctorId(doctorId);
        List<LocalTime> slots = new ArrayList<>();

        java.time.DayOfWeek diaSemana = fecha.getDayOfWeek();

        // Citas ya reservadas en esa fecha con ese doctor
        List<Cita> citasDelDia = citaRepo.findAll().stream()
                .filter(c -> c.getDoctorId().equals(doctorId) && c.getFecha().equals(fecha)
                        && (excludeCitaId == null || !excludeCitaId.equals(c.getId())))
                .toList();

        for (HorarioAtencion horario : horarios) {
            if (horario.getDiaSemana() == null || horario.getHoraInicio() == null || horario.getHoraFin() == null) {
                continue;
            }
            // Solo considerar el horario que corresponde al día de la semana de la fecha
            if (!horario.getDiaSemana().equals(diaSemana)) {
                continue;
            }

            int duracion = horario.getDuracionCitaMinutos() > 0 ? horario.getDuracionCitaMinutos() : 30;
            LocalTime current = horario.getHoraInicio();

            while (current.isBefore(horario.getHoraFin())) {
                final LocalTime slotTime = current;
                LocalDateTime slotStart = LocalDateTime.of(fecha, slotTime);
                LocalDateTime slotEnd = slotStart.plusMinutes(duracion);

                // El slot está disponible si:
                // 1. No choca con ninguna cita existente
                // 2. No es el mismo slot de la cita actual que se está reprogramando
                boolean esSlotActual = fechaActual != null && horaActual != null
                        && fecha.equals(fechaActual) && slotTime.equals(horaActual);

                boolean disponible = !esSlotActual && citasDelDia.stream().noneMatch(c -> {
                    LocalDateTime citaStart = LocalDateTime.of(c.getFecha(), c.getHora());
                    LocalDateTime citaEnd = citaStart.plusMinutes(duracion);
                    return slotStart.isBefore(citaEnd) && slotEnd.isAfter(citaStart);
                });

                if (disponible) {
                    slots.add(current);
                }

                current = current.plusMinutes(duracion);
            }
        }

        return slots;
    }

    private boolean esHorarioValido(String doctorId, LocalDate fechaCita, LocalTime horaCita) {
        List<HorarioAtencion> horarios = horarioRepo.findByDoctorId(doctorId);
        return horarios.stream()
                .anyMatch(h -> h.getDiaSemana() != null && h.getHoraInicio() != null && h.getHoraFin() != null &&
                        h.getDiaSemana().equals(fechaCita.getDayOfWeek()) &&
                        !horaCita.isBefore(h.getHoraInicio()) &&
                        horaCita.isBefore(h.getHoraFin()));
    }

    private int obtenerDuracionCita(String doctorId, LocalDate fechaCita, LocalTime horaCita) {
        List<HorarioAtencion> horarios = horarioRepo.findByDoctorId(doctorId);
        return horarios.stream()
                .filter(h -> h.getDiaSemana() != null && h.getHoraInicio() != null && h.getHoraFin() != null &&
                        h.getDiaSemana().equals(fechaCita.getDayOfWeek()) &&
                        !horaCita.isBefore(h.getHoraInicio()) &&
                        horaCita.isBefore(h.getHoraFin()))
                .findFirst()
                .map(HorarioAtencion::getDuracionCitaMinutos)
                .orElse(30);
    }

    private boolean hayConflicto(String doctorId, LocalDate fechaCita, LocalTime horaCita, String excludeCitaId) {
        List<Cita> citasExistentes = citaRepo.findAll().stream()
                .filter(c -> c.getDoctorId().equals(doctorId)
                        && c.getFecha().equals(fechaCita)
                        && (excludeCitaId == null || !excludeCitaId.equals(c.getId()))
                        && c.getEstado() != com.gestion.proyectos.modelo.EstadoCita.CANCELADA
                        && c.getEstado() != com.gestion.proyectos.modelo.EstadoCita.NO_ASISTIO)
                .toList();

        LocalDateTime inicioNuevo = LocalDateTime.of(fechaCita, horaCita);
        LocalDateTime finNuevo = inicioNuevo.plusMinutes(obtenerDuracionCita(doctorId, fechaCita, horaCita));

        return citasExistentes.stream().anyMatch(c -> {
            LocalDateTime inicioExistente = LocalDateTime.of(c.getFecha(), c.getHora());
            LocalDateTime finExistente = inicioExistente.plusMinutes(obtenerDuracionCita(doctorId, c.getFecha(), c.getHora()));
            return inicioNuevo.isBefore(finExistente) && finNuevo.isAfter(inicioExistente);
        });
    }
}
