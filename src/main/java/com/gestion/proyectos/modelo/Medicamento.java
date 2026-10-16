package com.gestion.proyectos.modelo;

import lombok.Getter;
import lombok.Setter;

/** Una línea de la fórmula médica (sección P). */
@Getter
@Setter
public class Medicamento {

    private String nombre;          // nombre genérico y concentración
    private ViaAdministracion via;
    private String dosisFrecuencia; // ej. 1 tableta cada 8 horas
    private String duracion;        // ej. por 5 días
    private Integer cantidad;       // total a dispensar

    public boolean isVacio() {
        return (nombre == null || nombre.isBlank()) && via == null
                && (dosisFrecuencia == null || dosisFrecuencia.isBlank())
                && (duracion == null || duracion.isBlank()) && cantidad == null;
    }
}
