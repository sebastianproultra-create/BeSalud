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

import java.util.Base64;

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
        String error = validarCamposRegistro(user);
        if (error != null) {
            model.addAttribute("error", error);
            return "register";
        }

        if (pacienteRepositorio.findByEmail(user.getEmail()).isPresent()
                || doctorRepositorio.findByEmail(user.getEmail()).isPresent()) {
            model.addAttribute("error", "Ya existe un usuario registrado con ese correo");
            return "register";
        }

        if ("PACIENTE".equals(user.getRole())) {
            Paciente paciente = new Paciente(user.getNombre().trim(), user.getApellido().trim(),
                    user.getTelefono().trim(), user.getIdentificacion().trim(),
                    user.getEmail().trim().toLowerCase(), passwordEncoder.encode(user.getPassword()));
            pacienteRepositorio.save(paciente);
        } else {
            String fotoBase64 = null;
            if (user.getFotoFile() != null && !user.getFotoFile().isEmpty()) {
                try {
                    byte[] bytes = user.getFotoFile().getBytes();
                    fotoBase64 = "data:" + user.getFotoFile().getContentType() + ";base64,"
                            + Base64.getEncoder().encodeToString(bytes);
                } catch (Exception e) {
                    model.addAttribute("error", "Error al procesar la imagen");
                    return "register";
                }
            }
            java.time.LocalDate fechaNac;
            try {
                fechaNac = java.time.LocalDate.parse(user.getFechaNacimiento().trim());
            } catch (Exception e) {
                model.addAttribute("error", "La fecha de nacimiento no tiene un formato válido (YYYY-MM-DD)");
                return "register";
            }
            Doctor doctor = new Doctor(user.getNombre().trim(), user.getApellido().trim(),
                    user.getTelefono().trim(), user.getIdentificacion().trim(),
                    user.getEmail().trim().toLowerCase(), passwordEncoder.encode(user.getPassword()),
                    user.getEspecialidad().trim(), fechaNac, fotoBase64, user.getBiografia());
            doctorRepositorio.save(doctor);
        }

        return "redirect:/login?registerSuccess";
    }

    private String validarCamposRegistro(UserRegistrationDTO user) {
        if (esVacio(user.getNombre())) return "El nombre es obligatorio";
        if (esVacio(user.getApellido())) return "El apellido es obligatorio";
        if (esVacio(user.getTelefono())) return "El teléfono es obligatorio";
        if (esVacio(user.getIdentificacion())) return "La identificación es obligatoria";
        if (esVacio(user.getEmail())) return "El correo electrónico es obligatorio";
        if (!user.getEmail().trim().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))
            return "El correo electrónico no tiene un formato válido";
        if (esVacio(user.getPassword())) return "La contraseña es obligatoria";
        if (user.getPassword().length() < 8) return "La contraseña debe tener al menos 8 caracteres";
        if (!"PACIENTE".equals(user.getRole()) && !"DOCTOR".equals(user.getRole()))
            return "Debe seleccionar un rol válido (Paciente o Doctor)";
        if ("DOCTOR".equals(user.getRole())) {
            if (esVacio(user.getEspecialidad())) return "La especialidad es obligatoria para doctores";
            if (esVacio(user.getFechaNacimiento())) return "La fecha de nacimiento es obligatoria para doctores";
        }
        return null;
    }

    private boolean esVacio(String s) {
        return s == null || s.trim().isEmpty();
    }
}
