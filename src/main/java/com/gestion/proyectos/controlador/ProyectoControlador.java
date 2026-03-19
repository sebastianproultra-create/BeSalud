
package com.gestion.proyectos.controlador;

import com.gestion.proyectos.modelo.Proyecto;
import com.gestion.proyectos.modelo.Tarea;
import com.gestion.proyectos.servicio.ProyectoServicio;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

// Este controlador maneja las peticiones del navegador
@Controller
@RequestMapping("/proyectos")
public class ProyectoControlador {

    private final ProyectoServicio servicio;

    public ProyectoControlador(ProyectoServicio servicio) {
        this.servicio = servicio;
    }

    // Muestra la lista de proyectos
    @GetMapping
    public String listar(Model model) {
        model.addAttribute("proyectos", servicio.listar());
        model.addAttribute("proyecto", new Proyecto());
        return "proyectos";
    }

    // Guarda un proyecto enviado desde el formulario
    @PostMapping("/guardar")
    public String guardar(@ModelAttribute Proyecto proyecto) {
        servicio.guardar(proyecto);
        return "redirect:/proyectos";
    }

    // Elimina un proyecto usando el id
    @GetMapping("/eliminar/{id}")
    public String eliminar(@PathVariable String id) {
        servicio.eliminar(id);
        return "redirect:/proyectos";
    }

    // Agrega una tarea a un proyecto existente
    @PostMapping("/{id}/tareas")
    public String agregarTarea(@PathVariable String id,
                               @RequestParam String titulo,
                               @RequestParam String estado) {
        Tarea tarea = new Tarea(titulo, estado);
        servicio.agregarTarea(id, tarea);
        return "redirect:/proyectos";
    }
}
