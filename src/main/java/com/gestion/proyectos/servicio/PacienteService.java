package com.gestion.proyectos.servicio;

import com.gestion.proyectos.modelo.Cita;
import com.gestion.proyectos.modelo.Doctor;
import com.gestion.proyectos.modelo.Paciente;
import com.gestion.proyectos.repositorio.CitaRepositorio;
import com.gestion.proyectos.repositorio.PersonaRepositorio;
import com.gestion.proyectos.util.ValidacionUtil;

import static com.gestion.proyectos.util.ValidacionUtil.literalRegex;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
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

    private final PasswordEncoder passwordEncoder;

    public PacienteService(PersonaRepositorio personaRepo, CitaRepositorio citaRepo,
                           PasswordEncoder passwordEncoder) {
        this.personaRepo = personaRepo;
        this.citaRepo = citaRepo;
        this.passwordEncoder = passwordEncoder;
    }

    public List<Paciente> listarTodos() {
        return personaRepo.findAllPacientes();
    }

    public Page<Paciente> listarPaginado(int page, int size) {
        return personaRepo.findAllPacientes(PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)),
                Sort.by("apellido", "nombre")));
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
        if (!ValidacionUtil.telefonoValido(paciente.getTelefono()))
            return ValidacionUtil.ERROR_TELEFONO;
        if (!ValidacionUtil.identificacionValida(paciente.getIdentificacion()))
            return ValidacionUtil.ERROR_IDENTIFICACION;
        if (personaRepo.findByIdentificacion(paciente.getIdentificacion().trim()).isPresent())
            return "Ya existe un usuario registrado con esa identificación";
        String clave = paciente.getPassword();
        String errorClave = ValidacionUtil.errorClave(clave);
        if (errorClave != null)
            return errorClave;
        paciente.setPassword(passwordEncoder.encode(clave));
        paciente.setEmail(paciente.getEmail().trim().toLowerCase());
        personaRepo.save(paciente);
        log.info("Paciente guardado: {}", paciente.getEmail());
        return null;
    }

    public Page<Doctor> listarDoctoresPaginated(int page, int size, String especialidad, String search) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size));
        boolean hasSearch = search != null && !search.isBlank();
        boolean hasEspecialidad = especialidad != null && !especialidad.isBlank();
        if (hasSearch && hasEspecialidad)
            return personaRepo.searchDoctoresActivosCombinado(literalRegex(search.trim()),
                    literalRegex(especialidad.trim()), pageable);
        if (hasSearch) {
            String limpio = search.trim().replaceAll("(?i)^(dra?\\.?)\\s+", "").trim();
            if (limpio.isEmpty()) limpio = search.trim();
            String[] partes = limpio.split("\\s+");
            if (partes.length >= 2)
                return personaRepo.searchDoctoresActivosByNombreYApellido(literalRegex(partes[0]),
                        literalRegex(partes[1]), pageable);
            return personaRepo.searchDoctoresActivosByNombreApellidoEmail(literalRegex(limpio), pageable);
        }
        if (hasEspecialidad)
            return personaRepo.findDoctoresActivosByEspecialidad(literalRegex(especialidad.trim()), pageable);
        return personaRepo.findDoctoresActivos(pageable);
    }

    public List<String> especialidadesDisponibles() {
        return personaRepo.findDoctoresActivos().stream()
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
