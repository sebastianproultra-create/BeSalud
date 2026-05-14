package com.gestion.proyectos.servicio;

import com.gestion.proyectos.modelo.Admin;
import com.gestion.proyectos.modelo.Doctor;
import com.gestion.proyectos.modelo.Paciente;
import com.gestion.proyectos.repositorio.AdminRepositorio;
import com.gestion.proyectos.repositorio.DoctorRepositorio;
import com.gestion.proyectos.repositorio.PacienteRepositorio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

    private final AdminRepositorio adminRepo;
    private final DoctorRepositorio doctorRepo;
    private final PacienteRepositorio pacienteRepo;

    public AdminService(AdminRepositorio adminRepo, DoctorRepositorio doctorRepo,
                        PacienteRepositorio pacienteRepo) {
        this.adminRepo = adminRepo;
        this.doctorRepo = doctorRepo;
        this.pacienteRepo = pacienteRepo;
    }

    public List<Doctor> listarDoctores() {
        List<Doctor> result = doctorRepo.findAllDoctores();
        log.debug("listarDoctores → {} registros", result.size());
        return result;
    }

    public List<Paciente> listarPacientes() {
        List<Paciente> result = pacienteRepo.findAll();
        log.debug("listarPacientes → {} registros", result.size());
        return result;
    }

    public List<Admin> listarAdmins() {
        List<Admin> result = adminRepo.findAll();
        log.debug("listarAdmins → {} registros", result.size());
        return result;
    }
}
