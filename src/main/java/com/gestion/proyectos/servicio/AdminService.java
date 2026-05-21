package com.gestion.proyectos.servicio;

import com.gestion.proyectos.modelo.Admin;
import com.gestion.proyectos.modelo.Doctor;
import com.gestion.proyectos.modelo.Paciente;
import com.gestion.proyectos.repositorio.AdminRepositorio;
import com.gestion.proyectos.repositorio.PersonaRepositorio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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

    public Page<Doctor> listarDoctoresPaginated(int page, int size, String especialidad, String search) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size));
        if (search != null && !search.isBlank()) {
            return personaRepo.searchDoctoresByNombreApellidoEmail(search.trim(), pageable);
        }
        if (especialidad != null && !especialidad.isBlank()) {
            return personaRepo.findDoctoresByEspecialidadContainingIgnoreCase(especialidad.trim(), pageable);
        }
        return personaRepo.findAllDoctores(pageable);
    }

    public Page<Paciente> listarPacientesPaginated(int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size));
        return personaRepo.findAllPacientes(pageable);
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
