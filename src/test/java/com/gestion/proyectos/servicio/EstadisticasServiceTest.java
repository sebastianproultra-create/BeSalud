package com.gestion.proyectos.servicio;

import com.gestion.proyectos.modelo.Cita;
import com.gestion.proyectos.modelo.Doctor;
import com.gestion.proyectos.modelo.EstadoCita;
import com.gestion.proyectos.modelo.HorarioAtencion;
import com.gestion.proyectos.modelo.Paciente;
import com.gestion.proyectos.repositorio.CitaRepositorio;
import com.gestion.proyectos.repositorio.HorarioAtencionRepositorio;
import com.gestion.proyectos.repositorio.PersonaRepositorio;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EstadisticasServiceTest {

    @Mock CitaRepositorio citaRepo;
    @Mock PersonaRepositorio personaRepo;
    @Mock HorarioAtencionRepositorio horarioRepo;
    @InjectMocks EstadisticasService service;

    private static Doctor doctor(String id, String esp, String estado) {
        Doctor d = new Doctor();
        d.setId(id);
        d.setNombre("Ana");
        d.setApellido(id);
        d.setEspecialidad(esp);
        d.setEstado(estado);
        return d;
    }

    private static Cita cita(String doctorId, LocalDate fecha, int hora, EstadoCita estado) {
        Cita c = new Cita();
        c.setDoctorId(doctorId);
        c.setPacienteId("p1");
        c.setFecha(fecha);
        c.setHora(LocalTime.of(hora, 0));
        c.setEstado(estado);
        return c;
    }

    @Test
    void calcular_resumeCitasDoctoresYOcupacion() {
        LocalDate hoy = LocalDate.now();
        LocalDate manana = hoy.plusDays(1);
        when(personaRepo.findAllDoctores()).thenReturn(List.of(
                doctor("d1", "Cardiología", "ACTIVO"), doctor("d2", "Pediatría", "ACTIVO"), doctor("d3", "Pediatría", "INACTIVO")));
        when(personaRepo.findAllPacientes()).thenReturn(List.of(new Paciente(), new Paciente()));

        List<Cita> citas = new ArrayList<>(List.of(
                cita("d1", hoy.minusDays(3), 8, EstadoCita.COMPLETADA),
                cita("d1", hoy.minusDays(2), 9, EstadoCita.ASISTIO),
                cita("d1", hoy.minusDays(1), 9, EstadoCita.NO_ASISTIO),
                cita("d2", hoy.minusDays(1), 10, EstadoCita.CANCELADA),
                cita("d1", manana, 8, EstadoCita.PENDIENTE),
                cita("d2", manana, 8, EstadoCita.PENDIENTE)));
        when(citaRepo.findAll()).thenReturn(citas);

        // d1 atiende todos los días de 8 a 10 con citas de 30 min: 4 turnos diarios, 28 en la semana.
        List<HorarioAtencion> horarios = new ArrayList<>();
        for (var dia : java.time.DayOfWeek.values()) {
            HorarioAtencion h = new HorarioAtencion("d1", dia, LocalTime.of(8, 0), LocalTime.of(10, 0));
            h.setDuracionCitaMinutos(30);
            horarios.add(h);
        }
        when(horarioRepo.findAll()).thenReturn(horarios);

        EstadisticasService.Resumen r = service.calcular();

        assertThat(r.totalCitas()).isEqualTo(6);
        assertThat(r.proximasSemana()).isEqualTo(2);
        assertThat(r.tasaAsistencia()).isEqualTo(67);   // 2 atendidas de 3 ya ocurridas
        assertThat(r.tasaCancelacion()).isEqualTo(17);  // 1 de 6
        assertThat(r.doctoresActivos()).isEqualTo(2);
        assertThat(r.doctoresPendientes()).isEqualTo(1);
        assertThat(r.pacientes()).isEqualTo(2);

        assertThat(r.porEspecialidad()).first().satisfies(d -> {
            assertThat(d.etiqueta()).isEqualTo("Cardiología");
            assertThat(d.valor()).isEqualTo(4);
        });
        assertThat(r.porDia()).hasSize(EstadisticasService.DIAS_ATRAS + EstadisticasService.DIAS_ADELANTE + 1);
        assertThat(r.porDia().get(r.indiceHoy() + 1).valor()).isEqualTo(2);

        // Solo d1 tiene horario: 1 cita sobre 28 turnos.
        assertThat(r.ocupacion()).singleElement().satisfies(o -> {
            assertThat(o.citas()).isEqualTo(1);
            assertThat(o.capacidad()).isEqualTo(28);
            assertThat(o.porcentaje()).isEqualTo(4);
        });
    }

    @Test
    void calcular_sinDatos_noFalla() {
        when(personaRepo.findAllDoctores()).thenReturn(List.of());
        when(personaRepo.findAllPacientes()).thenReturn(List.of());
        when(citaRepo.findAll()).thenReturn(List.of());
        when(horarioRepo.findAll()).thenReturn(List.of());

        EstadisticasService.Resumen r = service.calcular();

        assertThat(r.tasaAsistencia()).isZero();
        assertThat(r.porHora()).isEmpty();
        assertThat(r.ocupacion()).isEmpty();
    }
}
