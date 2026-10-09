package com.gestion.proyectos.servicio;

import com.gestion.proyectos.util.ValidacionUtil;
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

    public List<Admin> listarAdmins() {
        List<Admin> result = adminRepo.findAll();
        log.debug("listarAdmins → {} registros", result.size());
        return result;
    }

    public Page<Doctor> listarDoctoresPaginated(int page, int size, String especialidad, String search) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size));
        boolean hasSearch = search != null && !search.isBlank();
        boolean hasEspecialidad = especialidad != null && !especialidad.isBlank();

        if (hasSearch && hasEspecialidad) {
            return personaRepo.searchDoctoresCombinado(ValidacionUtil.literalRegex(search.trim()), ValidacionUtil.literalRegex(especialidad.trim()), pageable);
        } else if (hasSearch) {
            return personaRepo.searchDoctoresByNombreApellidoEmail(ValidacionUtil.literalRegex(search.trim()), pageable);
        } else if (hasEspecialidad) {
            return personaRepo.findDoctoresByEspecialidadContainingIgnoreCase(ValidacionUtil.literalRegex(especialidad.trim()), pageable);
        } else {
            return personaRepo.findAllDoctores(pageable);
        }
    }

    public Page<Paciente> listarPacientesPaginated(int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size));
        return personaRepo.findAllPacientes(pageable);
    }

    public Optional<Doctor> obtenerDoctor(String id) {
        return personaRepo.findById(id).filter(p -> p instanceof Doctor).map(p -> (Doctor) p);
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
