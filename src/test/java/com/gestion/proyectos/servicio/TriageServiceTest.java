package com.gestion.proyectos.servicio;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gestion.proyectos.modelo.Doctor;
import com.gestion.proyectos.modelo.ResultadoTriage;
import com.gestion.proyectos.repositorio.PersonaRepositorio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TriageServiceTest {

    @Mock PersonaRepositorio personaRepo;
    @Mock CitaService citaService;

    TriageService service;

    private static Doctor doctor(String id, String especialidad) {
        Doctor d = new Doctor();
        d.setId(id);
        d.setNombre("Ana");
        d.setApellido("Ruiz");
        d.setEspecialidad(especialidad);
        d.setEstado("ACTIVO");
        return d;
    }

    @BeforeEach
    void setUp() {
        // Sin API key: siempre se usa el respaldo por palabras clave, sin red.
        service = new TriageService(personaRepo, citaService, new ObjectMapper(), "", "modelo");
        lenient().when(personaRepo.findDoctoresActivos()).thenReturn(List.of(
                doctor("d1", "Cardiología"), doctor("d2", "Dermatología"), doctor("d3", "Medicina General")));
    }

    @Test
    void respaldo_sintomasDelCorazon_sugiereCardiologia() {
        ResultadoTriage r = service.evaluar("Siento palpitaciones y me duele el pecho al subir escaleras");
        assertThat(r.especialidad()).isEqualTo("Cardiología");
        assertThat(r.generadoPorIa()).isFalse();
        assertThat(r.resumen()).startsWith("Paciente refiere:");
    }

    @Test
    void respaldo_ignoraTildesYMayusculas() {
        assertThat(service.evaluar("Tengo una MANCHA rara en la PIEL del brazo").especialidad()).isEqualTo("Dermatología");
    }

    @Test
    void respaldo_sinCoincidencias_usaMedicinaGeneral() {
        assertThat(service.evaluar("me siento raro desde ayer y no sé qué tengo").especialidad()).isEqualTo("Medicina General");
    }

    @Test
    void respaldo_especialidadSinDoctoresActivos_noSeSugiere() {
        // Hay palabras de pediatría, pero no hay pediatras activos.
        assertThat(service.evaluar("mi bebe tiene un poco de fiebre").especialidad()).isEqualTo("Medicina General");
    }

    @Test
    void senalesDeAlarma_marcanEmergenciaYPrioridadAlta() {
        ResultadoTriage r = service.evaluar("tengo un dolor fuerte en el pecho y me falta el aire");
        assertThat(r.emergencia()).isTrue();
        assertThat(r.prioridad()).isEqualTo("ALTA");
        assertThat(r.recomendacion()).contains("123");
    }

    @Test
    void resumen_largo_seRecorta() {
        ResultadoTriage r = service.evaluar("dolor de cabeza ".repeat(40));
        assertThat(r.resumen().length()).isLessThanOrEqualTo(200);
    }

    @Test
    void doctoresSugeridos_soloDeLaEspecialidad_conPrimerosTurnos() {
        LinkedHashMap<String, List<String>> slots = new LinkedHashMap<>();
        slots.put("2099-01-05", List.of("08:00", "08:30", "09:00", "09:30"));
        when(citaService.slotsDisponibles(eq("d1"), isNull(), isNull(), isNull())).thenReturn(slots);

        var sugeridos = service.doctoresSugeridos("cardiologia");

        assertThat(sugeridos).hasSize(1);
        assertThat(sugeridos.get(0).doctor().getId()).isEqualTo("d1");
        assertThat(sugeridos.get(0).horas()).containsExactly("08:00", "08:30", "09:00");
    }

    @Test
    void doctoresSugeridos_sinTurnos_quedaSinFecha() {
        when(citaService.slotsDisponibles(any(), any(), any(), any())).thenReturn(new LinkedHashMap<>());
        var sugeridos = service.doctoresSugeridos("Dermatología");
        assertThat(sugeridos).hasSize(1);
        assertThat(sugeridos.get(0).fecha()).isNull();
    }
}
