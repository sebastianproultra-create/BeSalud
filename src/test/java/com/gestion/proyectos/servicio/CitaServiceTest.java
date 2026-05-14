package com.gestion.proyectos.servicio;

import com.gestion.proyectos.modelo.Cita;
import com.gestion.proyectos.modelo.EstadoCita;
import com.gestion.proyectos.modelo.HorarioAtencion;
import com.gestion.proyectos.repositorio.CitaRepositorio;
import com.gestion.proyectos.repositorio.DoctorRepositorio;
import com.gestion.proyectos.repositorio.HorarioAtencionRepositorio;
import com.gestion.proyectos.repositorio.PacienteRepositorio;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CitaServiceTest {

    @Mock CitaRepositorio citaRepo;
    @Mock DoctorRepositorio doctorRepo;
    @Mock PacienteRepositorio pacienteRepo;
    @Mock HorarioAtencionRepositorio horarioRepo;

    @InjectMocks CitaService service;

    @Test
    void esFechaHoraPasada_fechaPasada_retornaTrue() {
        assertThat(service.esFechaHoraPasada(LocalDate.now().minusDays(1), LocalTime.NOON)).isTrue();
    }

    @Test
    void esFechaHoraPasada_fechaFutura_retornaFalse() {
        assertThat(service.esFechaHoraPasada(LocalDate.now().plusDays(1), LocalTime.NOON)).isFalse();
    }

    @Test
    void esHorarioValido_horaEnRango_retornaTrue() {
        HorarioAtencion h = new HorarioAtencion();
        h.setDiaSemana(DayOfWeek.MONDAY);
        h.setHoraInicio(LocalTime.of(8, 0));
        h.setHoraFin(LocalTime.of(17, 0));
        h.setDuracionCitaMinutos(30);
        when(horarioRepo.findByDoctorId(anyString())).thenReturn(List.of(h));

        LocalDate lunes = LocalDate.now().with(DayOfWeek.MONDAY).plusWeeks(1);
        assertThat(service.esHorarioValido("doc1", lunes, LocalTime.of(10, 0))).isTrue();
    }

    @Test
    void esHorarioValido_horaFueraRango_retornaFalse() {
        HorarioAtencion h = new HorarioAtencion();
        h.setDiaSemana(DayOfWeek.MONDAY);
        h.setHoraInicio(LocalTime.of(8, 0));
        h.setHoraFin(LocalTime.of(12, 0));
        h.setDuracionCitaMinutos(30);
        when(horarioRepo.findByDoctorId(anyString())).thenReturn(List.of(h));

        LocalDate lunes = LocalDate.now().with(DayOfWeek.MONDAY).plusWeeks(1);
        assertThat(service.esHorarioValido("doc1", lunes, LocalTime.of(14, 0))).isFalse();
    }

    @Test
    void cancelar_setaEstadoCancelada() {
        Cita cita = new Cita();
        when(citaRepo.save(any())).thenReturn(cita);
        service.cancelar(cita);
        assertThat(cita.getEstado()).isEqualTo(EstadoCita.CANCELADA);
        verify(citaRepo).save(cita);
    }

    @Test
    void hayConflicto_sinCitasExistentes_retornaFalse() {
        when(citaRepo.findByDoctorIdAndFecha(anyString(), any())).thenReturn(List.of());
        when(horarioRepo.findByDoctorId(anyString())).thenReturn(List.of());
        LocalDate fecha = LocalDate.now().plusDays(1);
        assertThat(service.hayConflicto("doc1", fecha, LocalTime.of(10, 0), null)).isFalse();
    }
}
