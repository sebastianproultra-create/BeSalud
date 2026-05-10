package com.gestion.proyectos.modelo;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "personas")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class Persona {

    @Id
    private String id;
    private String nombre;
    private String apellido;
    private String telefono;
    private String identificacion;

    @Indexed(unique = true)
    private String email;
    private String password;
    private String role;
}
