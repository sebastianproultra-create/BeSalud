package com.gestion.proyectos.servicio;

import static com.gestion.proyectos.util.ValidacionUtil.esVacio;

import com.gestion.proyectos.modelo.Doctor;
import com.gestion.proyectos.modelo.Paciente;
import com.gestion.proyectos.modelo.UserRegistrationDTO;
import com.gestion.proyectos.repositorio.AdminRepositorio;
import com.gestion.proyectos.repositorio.PersonaRepositorio;
import com.gestion.proyectos.util.Especialidades;
import com.gestion.proyectos.util.ValidacionUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Base64;

@Service
public class RegistroService {

    private static final Logger log = LoggerFactory.getLogger(RegistroService.class);

    private final PersonaRepositorio personaRepo;
    private final AdminRepositorio adminRepo;
    private final PasswordEncoder passwordEncoder;

    public RegistroService(PersonaRepositorio personaRepo, AdminRepositorio adminRepo,
                           PasswordEncoder passwordEncoder) {
        this.personaRepo = personaRepo;
        this.adminRepo = adminRepo;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Valida reglas no cubiertas por Bean Validation: formatos de patrón y campos condicionales de DOCTOR.
     * Los campos básicos (notBlank, email, size) ya fueron validados con @Valid antes de llegar aquí.
     */
    public String validar(UserRegistrationDTO user) {
        if (esVacio(user.getNombre())) return "El nombre es obligatorio";
        if (esVacio(user.getApellido())) return "El apellido es obligatorio";
        if (!ValidacionUtil.telefonoValido(user.getTelefono()))
            return ValidacionUtil.ERROR_TELEFONO;
        if (!ValidacionUtil.identificacionValida(user.getIdentificacion()))
            return ValidacionUtil.ERROR_IDENTIFICACION;
        if (!"PACIENTE".equals(user.getRole()) && !"DOCTOR".equals(user.getRole()))
            return "Debe seleccionar un rol válido (Paciente o Doctor)";
        if ("DOCTOR".equals(user.getRole())) {
            if (esVacio(user.getEspecialidad())) return "La especialidad es obligatoria para doctores";
            if (!Especialidades.esValida(user.getEspecialidad())) return "Selecciona una especialidad de la lista";
            if (esVacio(user.getFechaNacimiento())) return "La fecha de nacimiento es obligatoria para doctores";
            LocalDate fechaNac;
            try {
                fechaNac = LocalDate.parse(user.getFechaNacimiento().trim());
            } catch (Exception e) {
                return "La fecha de nacimiento no tiene un formato válido (YYYY-MM-DD)";
            }
            LocalDate hoy = LocalDate.now();
            if (!fechaNac.isBefore(hoy))
                return "La fecha de nacimiento no puede ser hoy ni una fecha futura";
            int edad = java.time.Period.between(fechaNac, hoy).getYears();
            if (edad < 23) return "El doctor debe tener al menos 23 años";
            if (edad > 100) return "Fecha de nacimiento fuera de rango razonable";
        }
        return null;
    }

    /** Verifica duplicados en todos los repositorios. Retorna mensaje de error o null si OK. */
    public String verificarDuplicado(UserRegistrationDTO user) {
        String email = user.getEmail().trim().toLowerCase();
        if (adminRepo.findByEmail(email).isPresent())
            return "Ya existe un usuario registrado con ese correo";
        if (personaRepo.findByEmail(email).isPresent())
            return "Ya existe un usuario registrado con ese correo";
        if (user.getIdentificacion() != null
                && personaRepo.findByIdentificacion(user.getIdentificacion().trim()).isPresent())
            return "Ya existe un usuario registrado con esa identificación";
        return null;
    }

    /**
     * Guarda Paciente o Doctor. Retorna null si OK, mensaje de error si falla
     * (solo puede fallar al procesar la foto o parsear fecha).
     */
    public String registrar(UserRegistrationDTO user) {
        String email = user.getEmail().trim().toLowerCase();
        String password = passwordEncoder.encode(user.getPassword());

        if ("PACIENTE".equals(user.getRole())) {
            Paciente paciente = new Paciente(user.getNombre().trim(), user.getApellido().trim(),
                    user.getTelefono().trim(), user.getIdentificacion().trim(), email, password);
            try {
                personaRepo.save(paciente);
            } catch (DuplicateKeyException ex) {
                log.warn("Error al guardar paciente por clave duplicada: {}", email, ex);
                return "Ya existe un usuario registrado con ese correo";
            }
            log.info("Paciente registrado: {}", email);
            return null;
        }

        String fotoBase64 = null;
        if (user.getFotoFile() != null && !user.getFotoFile().isEmpty()) {
            try {
                byte[] bytes = user.getFotoFile().getBytes();
                fotoBase64 = "data:" + user.getFotoFile().getContentType() + ";base64,"
                        + Base64.getEncoder().encodeToString(bytes);
            } catch (Exception e) {
                return "Error al procesar la imagen";
            }
        } else if (user.getFotoUrl() != null && !user.getFotoUrl().isBlank()) {
            fotoBase64 = user.getFotoUrl();
        }

        LocalDate fechaNac;
        try {
            fechaNac = LocalDate.parse(user.getFechaNacimiento().trim());
        } catch (Exception e) {
            return "La fecha de nacimiento no tiene un formato válido (YYYY-MM-DD)";
        }

        Doctor doctor = new Doctor(user.getNombre().trim(), user.getApellido().trim(),
                user.getTelefono().trim(), user.getIdentificacion().trim(), email, password,
                user.getEspecialidad().trim(), fechaNac, fotoBase64, user.getBiografia());
        try {
            personaRepo.save(doctor);
        } catch (DuplicateKeyException ex) {
            log.warn("Error al guardar doctor por clave duplicada: {}", email, ex);
            return "Ya existe un usuario registrado con ese correo";
        }
        log.info("Doctor registrado: {}", email);
        return null;
    }
}
