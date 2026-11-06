package com.gestion.proyectos.modelo;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Getter;
import lombok.Setter;

/** Cliente del SaaS: un consultorio con su web propia conectada a la API de BeSalud. */
@Document(collection = "consultorios")
@Getter
@Setter
public class Consultorio {

    @Id
    private String id;                  // slug, p. ej. "san-juan"
    private String nombre;
    @Indexed
    private List<String> dominios = new ArrayList<>();   // orígenes exactos: "https://sanjuan.com"
    private List<String> doctorIds = new ArrayList<>();
    @Indexed
    private boolean activo = true;
}
