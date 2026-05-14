package com.gestion.proyectos.servicio;

import com.gestion.proyectos.modelo.Cita;
import com.gestion.proyectos.modelo.Doctor;
import com.gestion.proyectos.modelo.Paciente;
import com.gestion.proyectos.repositorio.CitaRepositorio;
import com.gestion.proyectos.repositorio.DoctorRepositorio;
import com.gestion.proyectos.repositorio.PacienteRepositorio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class PacienteService {

    private static final Logger log = LoggerFactory.getLogger(PacienteService.class);

    private final PacienteRepositorio pacienteRepo;
    private final DoctorRepositorio doctorRepo;
    private final CitaRepositorio citaRepo;

    public PacienteService(PacienteRepositorio pacienteRepo, DoctorRepositorio doctorRepo,
            CitaRepositorio citaRepo) {
        this.pacienteRepo = pacienteRepo;
        this.doctorRepo = doctorRepo;
        this.citaRepo = citaRepo;
    }

    public List<Paciente> listarTodos() {
        return pacienteRepo.findAll();
    }

    public Optional<Paciente> buscarPorEmail(String email) {
        Optional<Paciente> paciente = pacienteRepo.findByEmail(email);
        if (paciente.isPresent()) {
            return paciente;
        }
        return doctorRepo.findByEmail(email).map(this::convertirDoctorAPaciente);
    }

    private Paciente convertirDoctorAPaciente(Doctor doctor) {
        Paciente paciente = new Paciente();
        paciente.setId(doctor.getId());
        paciente.setNombre(doctor.getNombre());
        paciente.setApellido(doctor.getApellido());
        paciente.setTelefono(doctor.getTelefono());
        paciente.setIdentificacion(doctor.getIdentificacion());
        paciente.setEmail(doctor.getEmail());
        paciente.setPassword(doctor.getPassword());
        paciente.setRole("PACIENTE");
        return paciente;
    }

    /** null = OK, mensaje de error si falla validación o duplicado */
    public String validarYGuardar(Paciente paciente) {
        if (paciente.getNombre() == null || paciente.getNombre().isBlank())
            return "El nombre es obligatorio";
        if (paciente.getApellido() == null || paciente.getApellido().isBlank())
            return "El apellido es obligatorio";
        if (paciente.getEmail() == null || paciente.getEmail().isBlank())
            return "El correo es obligatorio";
        if (!paciente.getEmail().trim().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))
            return "El correo no tiene un formato válido";
        var conflicto = pacienteRepo.findByEmail(paciente.getEmail().trim());
        if (conflicto.isPresent() && !conflicto.get().getId().equals(paciente.getId()))
            return "Ya existe un paciente registrado con ese correo";
        pacienteRepo.save(paciente);
        log.info("Paciente guardado: {}", paciente.getEmail());
        return null;
    }

    public List<Doctor> listarDoctores(String especialidad) {
        return listarDoctores(especialidad, null, false);
    }

    public List<Doctor> listarDoctores(String especialidad, String currentUserEmail, boolean ocultarDoctorPropio) {
        List<Doctor> doctores;
        if (especialidad != null && !especialidad.isEmpty()) {
            doctores = doctorRepo.findByEspecialidadContainingIgnoreCase(especialidad);
        } else {
            doctores = doctorRepo.findAllDoctores();
        }
        if (ocultarDoctorPropio && currentUserEmail != null && !currentUserEmail.isBlank()) {
            String emailLower = currentUserEmail.trim().toLowerCase();
            return doctores.stream()
                    .filter(d -> d.getEmail() == null || !d.getEmail().trim().equalsIgnoreCase(emailLower))
                    .toList();
        }
        return doctores;
    }

    public List<String> especialidadesDisponibles() {
        return doctorRepo.findAllDoctores().stream()
                .map(Doctor::getEspecialidad)
                .filter(e -> e != null)
                .distinct()
                .toList();
    }

    public List<Cita> citasDelPaciente(String pacienteId) {
        return citaRepo.findByPacienteId(pacienteId);
    }

    public Map<String, String> mapDoctores() {
        return doctorRepo.findAllDoctores().stream()
                .collect(Collectors.toMap(Doctor::getId, d -> "Dr. " + d.getNombre() + " " + d.getApellido()));
    }
}
