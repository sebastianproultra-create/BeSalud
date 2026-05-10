package com.gestion.proyectos.modelo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
@NoArgsConstructor
public class UserRegistrationDTO {
    private String nombre;
    private String apellido;
    private String telefono;
    private String identificacion;
    private String email;
    private String password;
    private String role;
    private String especialidad;
    private String fechaNacimiento;
    private String foto;
    private String biografia;
    private MultipartFile fotoFile;
}
