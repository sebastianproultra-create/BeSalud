package com.gestion.proyectos.servicio;

import com.gestion.proyectos.modelo.Admin;
import com.gestion.proyectos.modelo.Doctor;
import com.gestion.proyectos.modelo.Paciente;
import com.gestion.proyectos.modelo.User;
import com.gestion.proyectos.repositorio.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

    private final UserRepository userRepository;

    public AdminService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<Doctor> listarDoctores() {
        List<Doctor> result = userRepository.findAll().stream()
                .filter(user -> user.hasRole("ROLE_DOCTOR"))
                .map(this::convertirUserADoctor)
                .collect(Collectors.toList());
        log.debug("listarDoctores → {} registros", result.size());
        return result;
    }

    public List<Paciente> listarPacientes() {
        List<Paciente> result = userRepository.findAll().stream()
                .filter(user -> user.hasRole("ROLE_PACIENTE"))
                .map(this::convertirUserAPaciente)
                .collect(Collectors.toList());
        log.debug("listarPacientes → {} registros", result.size());
        return result;
    }

    public List<Admin> listarAdmins() {
        List<Admin> result = userRepository.findAll().stream()
                .filter(user -> user.hasRole("ROLE_ADMIN"))
                .map(this::convertirUserAAdmin)
                .collect(Collectors.toList());
        log.debug("listarAdmins → {} registros", result.size());
        return result;
    }

    private Doctor convertirUserADoctor(User user) {
        Doctor doctor = new Doctor();
        doctor.setId(user.getId().toString());
        doctor.setNombre(user.getNombre());
        doctor.setApellido(user.getApellido());
        doctor.setTelefono(user.getTelefono());
        doctor.setIdentificacion(user.getIdentificacion());
        doctor.setEmail(user.getEmail());
        doctor.setPassword(user.getPassword());
        doctor.setEspecialidad(user.getEspecialidad());
        doctor.setFechaNacimiento(user.getFechaNacimiento());
        doctor.setFoto(user.getFoto());
        doctor.setBiografia(user.getBiografia());
        return doctor;
    }

    private Paciente convertirUserAPaciente(User user) {
        Paciente paciente = new Paciente();
        paciente.setId(user.getId().toString());
        paciente.setNombre(user.getNombre());
        paciente.setApellido(user.getApellido());
        paciente.setTelefono(user.getTelefono());
        paciente.setIdentificacion(user.getIdentificacion());
        paciente.setEmail(user.getEmail());
        paciente.setPassword(user.getPassword());
        paciente.setRole("PACIENTE");
        return paciente;
    }

    private Admin convertirUserAAdmin(User user) {
        Admin admin = new Admin();
        admin.setId(user.getId().toString());
        admin.setNombre(user.getNombre());
        admin.setApellido(user.getApellido());
        admin.setEmail(user.getEmail());
        admin.setPassword(user.getPassword());
        return admin;
    }
}
