package com.gestion.proyectos.controlador;

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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.ArrayList;

@Controller
@RequestMapping("/citas")
public class CitaController {

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
    public String listar(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String role = auth.getAuthorities().stream().map(a -> a.getAuthority()).findFirst().orElse("");
        String email = auth.getName();

        List<Cita> citas;
        if ("ROLE_DOCTOR".equals(role)) {
            Doctor doctor = doctorRepo.findByEmail(email).orElse(null);
            citas = doctor != null ? citaRepo.findByDoctorId(doctor.getId()) : List.of();
        } else if ("ROLE_PACIENTE".equals(role)) {
            Paciente paciente = pacienteRepo.findByEmail(email).orElse(null);
            citas = paciente != null ? citaRepo.findByPacienteId(paciente.getId()) : List.of();
        } else {
            citas = citaRepo.findAll(); // ADMIN ve todas
        }

        // Enriquecer con nombres de doctor y paciente
        model.addAttribute("citas", citas);
        model.addAttribute("doctoresMap", doctorRepo.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(Doctor::getId, d -> d.getNombre() + " " + d.getApellido())));
        model.addAttribute("pacientesMap", pacienteRepo.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(Paciente::getId, p -> p.getNombre() + " " + p.getApellido())));
        return "citas";
    }

    @GetMapping("/crear")
    public String crear(Model model) {
        model.addAttribute("cita", new Cita());
        model.addAttribute("doctores", doctorRepo.findAll());
        model.addAttribute("pacientes", pacienteRepo.findAll());
        return "cita_form";
    }

    @GetMapping("/nueva")
    public String nueva(@RequestParam String doctorId, Model model) {
        Doctor doctor = doctorRepo.findById(doctorId).orElse(null);
        if (doctor == null) {
            return "redirect:/pacientes/landing";
        }
        model.addAttribute("doctorSeleccionado", doctor);

        // Pre-calcular todos los slots disponibles en los próximos 30 días
        LinkedHashMap<String, List<String>> slotsDisponibles = new LinkedHashMap<>();
        LocalDate today = LocalDate.now();
        for (int i = 0; i < 30; i++) {
            LocalDate fecha = today.plusDays(i);
            List<LocalTime> slots = calcularSlotsDisponibles(doctorId, fecha);
            if (!slots.isEmpty()) {
                slotsDisponibles.put(fecha.toString(),
                        slots.stream().map(t -> t.toString().substring(0, 5)).collect(Collectors.toList()));
            }
        }
        model.addAttribute("slotsDisponibles", slotsDisponibles);

        return "cita_form_paciente";
    }

    @PostMapping("/guardar")
    public String guardar(@RequestParam String doctorId,
            @RequestParam String pacienteId,
            @RequestParam String fecha,
            @RequestParam String hora,
            @RequestParam String motivo) {
        Cita cita = new Cita();
        cita.setDoctorId(doctorId);
        cita.setPacienteId(pacienteId);
        cita.setFecha(LocalDate.parse(fecha));
        cita.setHora(LocalTime.parse(hora));
        cita.setMotivo(motivo);
        citaRepo.save(cita);
        return "redirect:/citas";
    }

    @PreAuthorize("hasRole('PACIENTE')")
    @PostMapping("/guardar-paciente")
    public String guardarPaciente(@RequestParam String doctorId,
            @RequestParam String fecha,
            @RequestParam String hora,
            @RequestParam String motivo) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        Paciente paciente = pacienteRepo.findByEmail(email).orElse(null);
        if (paciente == null) {
            return "redirect:/pacientes/landing?error=paciente_no_encontrado";
        }

        LocalDate fechaCita = LocalDate.parse(fecha);
        LocalTime horaCita = LocalTime.parse(hora);
        LocalDateTime fechaHoraCita = LocalDateTime.of(fechaCita, horaCita);

        // Validar que la cita esté dentro del horario del doctor
        List<HorarioAtencion> horarios = horarioRepo.findByDoctorId(doctorId);
        boolean horarioValido = horarios.stream()
                .anyMatch(h -> h.getDiaSemana() != null && h.getHoraInicio() != null && h.getHoraFin() != null &&
                        h.getDiaSemana().equals(fechaHoraCita.getDayOfWeek()) &&
                        !fechaHoraCita.toLocalTime().isBefore(h.getHoraInicio()) &&
                        !fechaHoraCita.toLocalTime().isAfter(h.getHoraFin()));

        if (!horarioValido) {
            return "redirect:/citas/nueva?doctorId=" + doctorId + "&error=horario_invalido";
        }

        // Validar que no haya conflicto con otras citas
        List<Cita> citasExistentes = citaRepo.findAll().stream()
                .filter(c -> c.getDoctorId().equals(doctorId) && c.getFecha().equals(fechaCita))
                .collect(Collectors.toList());

        // Obtener duración de cita del horario
        int duracionMinutos = horarios.stream()
                .filter(h -> h.getDiaSemana() != null && h.getHoraInicio() != null && h.getHoraFin() != null &&
                        h.getDiaSemana().equals(fechaHoraCita.getDayOfWeek()) &&
                        !fechaHoraCita.toLocalTime().isBefore(h.getHoraInicio()) &&
                        !fechaHoraCita.toLocalTime().isAfter(h.getHoraFin()))
                .findFirst()
                .map(HorarioAtencion::getDuracionCitaMinutos)
                .orElse(30);

        LocalDateTime finCita = fechaHoraCita.plusMinutes(duracionMinutos);

        boolean conflicto = citasExistentes.stream().anyMatch(c -> {
            LocalDateTime inicioExistente = LocalDateTime.of(c.getFecha(), c.getHora());
            int duracionExistente = horarios.stream()
                    .filter(h -> h.getDiaSemana() != null && h.getHoraInicio() != null && h.getHoraFin() != null &&
                            h.getDiaSemana().equals(inicioExistente.getDayOfWeek()) &&
                            !inicioExistente.toLocalTime().isBefore(h.getHoraInicio()) &&
                            !inicioExistente.toLocalTime().isAfter(h.getHoraFin()))
                    .findFirst()
                    .map(HorarioAtencion::getDuracionCitaMinutos)
                    .orElse(30);
            LocalDateTime finExistente = inicioExistente.plusMinutes(duracionExistente);
            return !(finCita.isBefore(inicioExistente) || fechaHoraCita.isAfter(finExistente));
        });

        if (conflicto) {
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

    @PreAuthorize("hasRole('PACIENTE') or hasRole('ADMIN')")
    @PostMapping("/{id}/cancelar")
    public String cancelar(@PathVariable String id) {
        citaRepo.findById(id).ifPresent(c -> {
            c.setEstado(com.gestion.proyectos.modelo.EstadoCita.CANCELADA);
            citaRepo.save(c);
        });
        return "redirect:/citas";
    }

    private List<LocalTime> calcularSlotsDisponibles(String doctorId, LocalDate fecha) {
        List<HorarioAtencion> horarios = horarioRepo.findByDoctorId(doctorId);
        List<LocalTime> slots = new ArrayList<>();

        java.time.DayOfWeek diaSemana = fecha.getDayOfWeek();

        // Citas ya reservadas en esa fecha con ese doctor
        List<Cita> citasDelDia = citaRepo.findAll().stream()
                .filter(c -> c.getDoctorId().equals(doctorId) && c.getFecha().equals(fecha))
                .collect(Collectors.toList());

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

                // El slot está disponible si no choca con ninguna cita existente
                boolean disponible = citasDelDia.stream().noneMatch(c -> {
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
}
