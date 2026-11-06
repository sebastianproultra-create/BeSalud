package com.gestion.proyectos.modelo;

import java.util.Date;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Getter;
import lombok.Setter;

/**
 * Pase de un solo uso para entrar a BeSalud tras registrarse en la web de un consultorio.
 * Vive en Mongo (no en memoria) para que funcione con varias instancias del backend;
 * el índice TTL borra solos los vencidos.
 */
@Document(collection = "pasesAcceso")
@Getter
@Setter
public class PaseAcceso {

    @Id
    private String codigo;
    private String email;
    private String doctorId;
    @Indexed(expireAfterSeconds = 0)
    private Date expira;
}
