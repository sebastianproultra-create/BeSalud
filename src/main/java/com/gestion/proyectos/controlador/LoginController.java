package com.gestion.proyectos.controlador;

import com.gestion.proyectos.modelo.UserRegistrationDTO;
import com.gestion.proyectos.modelo.Doctor;
import com.gestion.proyectos.modelo.Paciente;
import com.gestion.proyectos.repositorio.DoctorRepositorio;
import com.gestion.proyectos.repositorio.PacienteRepositorio;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping
public class LoginController {

    private final PacienteRepositorio pacienteRepositorio;
    private final DoctorRepositorio doctorRepositorio;
    private final PasswordEncoder passwordEncoder;

    public LoginController(PacienteRepositorio pacienteRepositorio, DoctorRepositorio doctorRepositorio,
            PasswordEncoder passwordEncoder) {
        this.pacienteRepositorio = pacienteRepositorio;
        this.doctorRepositorio = doctorRepositorio;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/register")
    public String register(Model model) {
        model.addAttribute("user", new UserRegistrationDTO());
        return "register";
    }

    @PostMapping("/register/save")
    public String registerSave(UserRegistrationDTO user, Model model) {
        // Check if email already exists in either repo
        if (pacienteRepositorio.findByEmail(user.getEmail()).isPresent()
                || doctorRepositorio.findByEmail(user.getEmail()).isPresent()) {
            model.addAttribute("error", "Ya existe un usuario registrado con ese correo");
            return "register";
        }

        if ("PACIENTE".equals(user.getRole())) {
            Paciente paciente = new Paciente(user.getNombre(), user.getApellido(), user.getTelefono(),
                    user.getIdentificacion(), user.getEmail(), passwordEncoder.encode(user.getPassword()));
            pacienteRepositorio.save(paciente);
        } else if ("DOCTOR".equals(user.getRole())) {
            Doctor doctor = new Doctor(user.getNombre(), user.getApellido(), user.getTelefono(),
                    user.getIdentificacion(), user.getEmail(), passwordEncoder.encode(user.getPassword()),
                    user.getEspecialidad(), java.time.LocalDate.parse(user.getFechaNacimiento()), user.getFoto(),
                    user.getBiografia());
            doctorRepositorio.save(doctor);
        }

        return "redirect:/login?registerSuccess";
    }
}
