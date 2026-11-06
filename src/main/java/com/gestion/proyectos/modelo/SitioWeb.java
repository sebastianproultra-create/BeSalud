package com.gestion.proyectos.modelo;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Getter;
import lombok.Setter;

/**
 * Página web que BeSalud le crea y le aloja al médico que no tiene una propia. Se publica en /sitio/{slug}
 * con una de tres plantillas; la agenda, el pago y "Mis citas" son el widget de la API de consultorios.
 */
@Document(collection = "sitios")
@Getter
@Setter
public class SitioWeb {

    /** Plantillas: clave -> nombre visible. Cada una tiene su color por defecto en COLOR_POR_PLANTILLA. */
    public static final Map<String, String> PLANTILLAS = new LinkedHashMap<>();
    /** Colores principales que puede elegir el médico: clave -> nombre visible. */
    public static final Map<String, String> COLORES = new LinkedHashMap<>();
    public static final Map<String, String> COLOR_POR_PLANTILLA = Map.of(
            "clinica", "azul", "familiar", "terracota", "infantil", "coral");
    public static final String PLANTILLA_DEFECTO = "clinica";
    public static final int MAX_SERVICIOS = 6;

    static {
        PLANTILLAS.put("clinica", "Clínica");
        PLANTILLAS.put("familiar", "Familiar");
        PLANTILLAS.put("infantil", "Infantil");
        COLORES.put("azul", "Azul");
        COLORES.put("verde", "Verde");
        COLORES.put("petroleo", "Petróleo");
        COLORES.put("terracota", "Terracota");
        COLORES.put("coral", "Coral");
        COLORES.put("violeta", "Violeta");
    }

    @Id
    private String id;
    @Indexed(unique = true)
    private String slug;
    @Indexed(unique = true)
    private String doctorId;

    private String plantilla = PLANTILLA_DEFECTO;
    private String color = COLOR_POR_PLANTILLA.get(PLANTILLA_DEFECTO);
    private String titulo;
    private String frase;
    private String presentacion;
    private List<String> servicios = new ArrayList<>();
    private String whatsapp;
    private String direccion;
    private String ciudad;

    private boolean publicado;
    /** Consultorio de la API que usa el widget; se crea o se vincula al publicar. */
    private String consultorioId;
    private Instant actualizado;

    /** Servicios en un texto, uno por línea, para el formulario del editor. */
    public String getServiciosTexto() {
        return String.join("\n", servicios);
    }
}
