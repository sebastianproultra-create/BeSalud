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
        return pacienteRepo.findByEmail(email);
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
        if (especialidad != null && !especialidad.isEmpty())
            return doctorRepo.findByEspecialidadContainingIgnoreCase(especialidad);
        return doctorRepo.findAllDoctores();
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
