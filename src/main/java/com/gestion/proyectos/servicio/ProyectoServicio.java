
package com.gestion.proyectos.servicio;

import com.gestion.proyectos.modelo.Proyecto;
import com.gestion.proyectos.modelo.Tarea;
import com.gestion.proyectos.repositorio.ProyectoRepositorio;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

// Esta clase contiene la lógica del negocio
@Service
public class ProyectoServicio {

    // Se usa el repositorio para acceder a la base de datos
    private final ProyectoRepositorio repositorio;

    public ProyectoServicio(ProyectoRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    // Retorna la lista de todos los proyectos
    public List<Proyecto> listar() {
        return repositorio.findAll();
    }

    // Guarda un proyecto en la base de datos
    public void guardar(Proyecto proyecto) {
        repositorio.save(proyecto);
    }

    // Elimina un proyecto según su id
    public void eliminar(String id) {
        repositorio.deleteById(id);
    }

    // Agrega una tarea dentro de un proyecto (documento anidado en MongoDB)
    public void agregarTarea(String proyectoId, Tarea tarea) {
        Optional<Proyecto> optional = repositorio.findById(proyectoId);
        if (optional.isPresent()) {
            Proyecto proyecto = optional.get();
            proyecto.getTareas().add(tarea);
            repositorio.save(proyecto);
        }
    }

    // Busca un proyecto por su id
    public Proyecto buscarPorId(String id) {
        return repositorio.findById(id).orElse(null);
    }
}
