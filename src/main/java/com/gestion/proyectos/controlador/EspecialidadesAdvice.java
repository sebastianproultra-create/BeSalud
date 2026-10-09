package com.gestion.proyectos.controlador;

import com.gestion.proyectos.util.Especialidades;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;
import java.util.Map;

/** Expone la lista única de especialidades a todas las vistas. */
@ControllerAdvice
public class EspecialidadesAdvice {

    @ModelAttribute("gruposEspecialidad")
    public Map<String, List<String>> gruposEspecialidad() {
        return Especialidades.GRUPOS;
    }
}
