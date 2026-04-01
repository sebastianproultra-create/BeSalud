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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.util.List;
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
        List<Cita> citas = citaRepo.findAll();
        model.addAttribute("citas", citas);
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
    public String nueva(@RequestParam String doctorId, @RequestParam(required = false) String fecha, Model model) {
        Doctor doctor = doctorRepo.findById(doctorId).orElse(null);
        if (doctor == null) {
            return "redirect:/pacientes/landing";
        }
        model.addAttribute("cita", new Cita());
        model.addAttribute("doctorSeleccionado", doctor);

        if (fecha != null && !fecha.isEmpty()) {
            LocalDate fechaCita = LocalDate.parse(fecha);
            List<LocalTime> slotsDisponibles = calcularSlotsDisponibles(doctorId, fechaCita);
            model.addAttribute("slotsDisponibles", slotsDisponibles);
            model.addAttribute("fechaSeleccionada", fecha);
        }

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
                .anyMatch(h -> !fechaHoraCita.isBefore(h.getInicio()) && !fechaHoraCita.isAfter(h.getFin()));

        if (!horarioValido) {
            return "redirect:/citas/nueva?doctorId=" + doctorId + "&error=horario_invalido";
        }

        // Validar que no haya conflicto con otras citas
        List<Cita> citasExistentes = citaRepo.findAll().stream()
                .filter(c -> c.getDoctorId().equals(doctorId) && c.getFecha().equals(fechaCita))
                .collect(Collectors.toList());

        // Obtener duración de cita del horario
        int duracionMinutos = horarios.stream()
                .filter(h -> !fechaHoraCita.isBefore(h.getInicio()) && !fechaHoraCita.isAfter(h.getFin()))
                .findFirst()
                .map(HorarioAtencion::getDuracionCitaMinutos)
                .orElse(30);

        LocalDateTime finCita = fechaHoraCita.plusMinutes(duracionMinutos);

        boolean conflicto = citasExistentes.stream().anyMatch(c -> {
            LocalDateTime inicioExistente = LocalDateTime.of(c.getFecha(), c.getHora());
            int duracionExistente = horarios.stream()
                    .filter(h -> !inicioExistente.isBefore(h.getInicio()) && !inicioExistente.isAfter(h.getFin()))
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

        for (HorarioAtencion horario : horarios) {
            // Solo considerar horarios que incluyan la fecha (asumiendo que inicio y fin
            // son en la misma fecha por simplicidad)
            if (horario.getInicio().toLocalDate().equals(fecha)) {
                LocalTime inicio = horario.getInicio().toLocalTime();
                LocalTime fin = horario.getFin().toLocalTime();
                int duracion = horario.getDuracionCitaMinutos();

                LocalTime current = inicio;
                while (current.isBefore(fin)) {
                    // Verificar si el slot está disponible
                    LocalDateTime slotStart = LocalDateTime.of(fecha, current);
                    LocalDateTime slotEnd = slotStart.plusMinutes(duracion);

                    boolean disponible = citaRepo.findAll().stream()
                            .filter(c -> c.getDoctorId().equals(doctorId) && c.getFecha().equals(fecha))
                            .noneMatch(c -> {
                                LocalDateTime citaStart = LocalDateTime.of(c.getFecha(), c.getHora());
                                // Obtener duración de la cita existente
                                int duracionExistente = horarios.stream()
                                        .filter(h -> h.getDoctorId().equals(doctorId))
                                        .findFirst()
                                        .map(HorarioAtencion::getDuracionCitaMinutos)
                                        .orElse(duracion);
                                LocalDateTime citaEnd = citaStart.plusMinutes(duracionExistente);
                                return !(slotEnd.isBefore(citaStart) || slotStart.isAfter(citaEnd));
                            });

                    if (disponible) {
                        slots.add(current);
                    }

                    current = current.plusMinutes(duracion);
                }
            }
        }

        return slots;
    }
}
