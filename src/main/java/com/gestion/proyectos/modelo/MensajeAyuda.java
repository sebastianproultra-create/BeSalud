package com.gestion.proyectos.modelo;

import java.time.LocalDateTime;

import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Getter;
import lombok.Setter;

/** Mensaje enviado desde el formulario del Centro de ayuda; lo atiende un administrador. */
@Document(collection = "mensajesAyuda")
@Getter
@Setter
public class MensajeAyuda {

    private String id;
    private String nombre;
    private String email;
    private String tema;
    private String mensaje;
    private LocalDateTime fecha;
    @Indexed
    private boolean atendido;
}
