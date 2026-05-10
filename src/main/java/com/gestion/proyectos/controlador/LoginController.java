package com.gestion.proyectos.controlador;

import static com.gestion.proyectos.util.ValidacionUtil.esVacio;

import com.gestion.proyectos.modelo.UserRegistrationDTO;
import com.gestion.proyectos.modelo.Doctor;
import com.gestion.proyectos.modelo.Paciente;
import com.gestion.proyectos.repositorio.AdminRepositorio;
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

    private static final String EMAIL_REGEX = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
    private static final String VIEW_REGISTER = "register";
    private static final String ATTR_ERROR = "error";

    private final PacienteRepositorio pacienteRepositorio;
    private final DoctorRepositorio doctorRepositorio;
    private final AdminRepositorio adminRepositorio;
    private final PasswordEncoder passwordEncoder;

    public LoginController(PacienteRepositorio pacienteRepositorio, DoctorRepositorio doctorRepositorio,
            AdminRepositorio adminRepositorio, PasswordEncoder passwordEncoder) {
        this.pacienteRepositorio = pacienteRepositorio;
        this.doctorRepositorio = doctorRepositorio;
        this.adminRepositorio = adminRepositorio;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/register")
    public String register(Model model) {
        model.addAttribute("user", new UserRegistrationDTO());
        return VIEW_REGISTER;
    }

    @PostMapping("/register/save")
    public String registerSave(UserRegistrationDTO user, Model model) {
        String error = validarCamposRegistro(user);
        if (error != null) {
            model.addAttribute("user", user);
            model.addAttribute(ATTR_ERROR, error);
            return VIEW_REGISTER;
        }

        String emailNormalizado = user.getEmail().trim().toLowerCase();
        if (adminRepositorio.findByEmail(emailNormalizado).isPresent()) {
            model.addAttribute("user", user);
            model.addAttribute(ATTR_ERROR, "Ya existe un usuario registrado con ese correo");
            return VIEW_REGISTER;
        }
        if ("PACIENTE".equals(user.getRole()) && pacienteRepositorio.findByEmail(emailNormalizado).isPresent()) {
            model.addAttribute("user", user);
            model.addAttribute(ATTR_ERROR, "Ya existe un paciente registrado con ese correo");
            return VIEW_REGISTER;
        }
        if ("DOCTOR".equals(user.getRole()) && doctorRepositorio.findByEmail(emailNormalizado).isPresent()) {
            model.addAttribute("user", user);
            model.addAttribute(ATTR_ERROR, "Ya existe un doctor registrado con ese correo");
            return VIEW_REGISTER;
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
                    model.addAttribute("user", user);
                    model.addAttribute(ATTR_ERROR, "Error al procesar la imagen");
                    return VIEW_REGISTER;
                }
            }
            java.time.LocalDate fechaNac;
            try {
                fechaNac = java.time.LocalDate.parse(user.getFechaNacimiento().trim());
            } catch (Exception e) {
                model.addAttribute("user", user);
                model.addAttribute(ATTR_ERROR, "La fecha de nacimiento no tiene un formato válido (YYYY-MM-DD)");
                return VIEW_REGISTER;
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
        if (!user.getTelefono().trim().matches("^3\\d{1,9}$"))
            return "El teléfono debe empezar por 3 y tener máximo 10 dígitos";
        if (esVacio(user.getIdentificacion())) return "La identificación es obligatoria";
        if (!user.getIdentificacion().trim().matches("^\\d{1,10}$"))
            return "La identificación debe contener solo números y máximo 10 dígitos";
        if (esVacio(user.getEmail())) return "El correo electrónico es obligatorio";
        if (!user.getEmail().trim().matches(EMAIL_REGEX))
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
}
