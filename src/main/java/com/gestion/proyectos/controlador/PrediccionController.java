package com.gestion.proyectos.controlador;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;

import com.gestion.proyectos.modelo.Doctor;
import com.gestion.proyectos.servicio.DoctorService;
import com.gestion.proyectos.servicio.PrediccionService;

@RestController
@RequestMapping("/api/predicciones")
@CrossOrigin(origins = "*") // Fundamental para que el frontend no tenga bloqueos de CORS
public class PrediccionController {

    private static final String ERROR_CONEXION = "{\"error\": \"Error al conectar con el modelo predictivo.\"}";

    private final PrediccionService prediccionService;
    private final DoctorService doctorService;

    public PrediccionController(PrediccionService prediccionService, DoctorService doctorService) {
        this.prediccionService = prediccionService;
        this.doctorService = doctorService;
    }

    /** Proyección del médico que tiene la sesión abierta, según su especialidad. */
    @GetMapping("/mia")
    public ResponseEntity<String> obtenerMiPrediccion(Authentication auth,
            @RequestParam(defaultValue = "30") int dias) {
        Doctor doctor = auth == null ? null : doctorService.buscarPorEmail(auth.getName()).orElse(null);
        if (doctor == null) {
            return ResponseEntity.status(403).body("{\"error\": \"Solo disponible para médicos.\"}");
        }
        return consultar(doctor.getEspecialidad(), doctor.getId(), dias);
    }

    /** Proyección de una especialidad; con medicoId, la de ese médico. */
    @GetMapping("/{especialidad}")
    public ResponseEntity<String> obtenerPrediccion(@PathVariable String especialidad,
            @RequestParam(required = false) String medicoId,
            @RequestParam(defaultValue = "30") int dias) {
        return consultar(especialidad, medicoId, dias);
    }

    private ResponseEntity<String> consultar(String especialidad, String medicoId, int dias) {
        try {
            return ResponseEntity.ok(prediccionService.obtenerProyeccion(especialidad, medicoId, dias));
        } catch (HttpStatusCodeException e) {
            // 404 (especialidad o médico sin historial), 400, 422...: se pasa el detalle de la API de Python.
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        } catch (Exception e) {
            // Si la API de Python está apagada o falla, devuelve un error 500 sin romper Spring Boot
            return ResponseEntity.internalServerError().body(ERROR_CONEXION);
        }
    }
}
