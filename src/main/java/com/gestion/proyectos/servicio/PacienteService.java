package com.gestion.proyectos.servicio;

import com.gestion.proyectos.modelo.Cita;
import com.gestion.proyectos.modelo.Doctor;
import com.gestion.proyectos.modelo.Paciente;
import com.gestion.proyectos.repositorio.CitaRepositorio;
import com.gestion.proyectos.repositorio.PersonaRepositorio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class PacienteService {

    private static final Logger log = LoggerFactory.getLogger(PacienteService.class);

    private final PersonaRepositorio personaRepo;
    private final CitaRepositorio citaRepo;

    public PacienteService(PersonaRepositorio personaRepo, CitaRepositorio citaRepo) {
        this.personaRepo = personaRepo;
        this.citaRepo = citaRepo;
    }

    public List<Paciente> listarTodos() {
        return personaRepo.findAllPacientes();
    }

    public Optional<Paciente> buscarPorEmail(String email) {
        return personaRepo.findPacienteByEmail(email);
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
        var conflicto = personaRepo.findPacienteByEmail(paciente.getEmail().trim());
        if (conflicto.isPresent() && !conflicto.get().getId().equals(paciente.getId()))
            return "Ya existe un paciente registrado con ese correo";
        personaRepo.save(paciente);
        log.info("Paciente guardado: {}", paciente.getEmail());
        return null;
    }

    public List<Doctor> listarDoctores(String especialidad) {
        if (especialidad != null && !especialidad.isEmpty())
            return personaRepo.findDoctoresByEspecialidadContainingIgnoreCase(especialidad);
        return personaRepo.findAllDoctores();
    }

    public Page<Doctor> listarDoctoresPaginated(int page, int size, String especialidad, String search) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size));
        boolean hasSearch = search != null && !search.isBlank();
        boolean hasEspecialidad = especialidad != null && !especialidad.isBlank();
        if (hasSearch && hasEspecialidad)
            return personaRepo.searchDoctoresCombinado(search.trim(), especialidad.trim(), pageable);
        if (hasSearch) {
            String limpio = search.trim().replaceAll("(?i)^(dra?\\.?)\\s+", "").trim();
            if (limpio.isEmpty()) limpio = search.trim();
            String[] partes = limpio.split("\\s+");
            if (partes.length >= 2)
                return personaRepo.searchDoctoresByNombreYApellido(partes[0], partes[1], pageable);
            return personaRepo.searchDoctoresByNombreApellidoEmail(limpio, pageable);
        }
        if (hasEspecialidad)
            return personaRepo.findDoctoresByEspecialidadContainingIgnoreCase(especialidad.trim(), pageable);
        return personaRepo.findAllDoctores(pageable);
    }

    public List<String> especialidadesDisponibles() {
        return personaRepo.findAllDoctores().stream()
                .map(Doctor::getEspecialidad)
                .filter(e -> e != null)
                .distinct()
                .toList();
    }

    public List<Cita> citasDelPaciente(String pacienteId) {
        return citaRepo.findByPacienteId(pacienteId);
    }

    public Map<String, String> mapDoctores() {
        return personaRepo.findAllDoctores().stream()
                .collect(Collectors.toMap(Doctor::getId, d -> "Dr. " + d.getNombre() + " " + d.getApellido()));
    }
}
