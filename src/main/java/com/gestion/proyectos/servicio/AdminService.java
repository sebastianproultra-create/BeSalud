package com.gestion.proyectos.servicio;

import com.gestion.proyectos.modelo.Admin;
import com.gestion.proyectos.modelo.Doctor;
import com.gestion.proyectos.modelo.Paciente;
import com.gestion.proyectos.repositorio.AdminRepositorio;
import com.gestion.proyectos.repositorio.PersonaRepositorio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

    private final AdminRepositorio adminRepo;
    private final PersonaRepositorio personaRepo;

    public AdminService(AdminRepositorio adminRepo, PersonaRepositorio personaRepo) {
        this.adminRepo = adminRepo;
        this.personaRepo = personaRepo;
    }

    public List<Doctor> listarDoctores() {
        List<Doctor> result = personaRepo.findAllDoctores();
        log.debug("listarDoctores → {} registros", result.size());
        return result;
    }

    public List<Paciente> listarPacientes() {
        List<Paciente> result = personaRepo.findAllPacientes();
        log.debug("listarPacientes → {} registros", result.size());
        return result;
    }

    public List<Admin> listarAdmins() {
        List<Admin> result = adminRepo.findAll();
        log.debug("listarAdmins → {} registros", result.size());
        return result;
    }

    public Optional<Doctor> obtenerDoctor(String id) {
        return personaRepo.findById(id).map(p -> (Doctor) p);
    }

    public void activarDoctor(String doctorId) {
        Optional<Doctor> doctor = obtenerDoctor(doctorId);
        if (doctor.isPresent()) {
            doctor.get().setEstado("ACTIVO");
            personaRepo.save(doctor.get());
            log.info("Doctor {} activado", doctorId);
        }
    }

    public void desactivarDoctor(String doctorId) {
        Optional<Doctor> doctor = obtenerDoctor(doctorId);
        if (doctor.isPresent()) {
            doctor.get().setEstado("INACTIVO");
            personaRepo.save(doctor.get());
            log.info("Doctor {} desactivado", doctorId);
        }
    }
}
