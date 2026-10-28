package com.gestion.proyectos.servicio;

import com.gestion.proyectos.modelo.Cita;
import com.gestion.proyectos.modelo.CitaEvento;
import com.gestion.proyectos.modelo.EstadoAbono;
import com.gestion.proyectos.modelo.Paciente;
import com.gestion.proyectos.repositorio.CitaRepositorio;
import com.gestion.proyectos.repositorio.HorarioAtencionRepositorio;
import com.gestion.proyectos.repositorio.PersonaRepositorio;
import com.gestion.proyectos.util.Reloj;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DoctorServiceAbonosTest {

    @Mock PersonaRepositorio personaRepo;
    @Mock HorarioAtencionRepositorio horarioRepo;
    @Mock CitaRepositorio citaRepo;
    @Mock RegistroService registroService;
    @Mock ApplicationEventPublisher eventos;

    @InjectMocks DoctorService service;

    private static Cita cita(String id, long pesos, EstadoAbono estado) {
        Cita c = new Cita();
        c.setId(id);
        c.setDoctorId("d1");
        c.setPacienteId("p1");
        c.setFecha(Reloj.hoy());
        c.setAbonoCentavos(pesos * 100);
        c.setEstadoAbono(estado);
        return c;
    }

    @Test
    void resumen_sumaLoRecibidoLoConservadoYLoPendiente() {
        when(citaRepo.findByDoctorIdAndFechaBetweenAndAbonoCentavosIsNotNull(eq("d1"), any(), any())).thenReturn(List.of(
                cita("a", 16_000, EstadoAbono.PAGADO),
                cita("b", 20_000, EstadoAbono.RETENIDO),
                cita("c", 12_000, EstadoAbono.A_REEMBOLSAR),
                cita("d", 10_000, EstadoAbono.DEVUELTO)));
        when(citaRepo.findByDoctorIdAndEstadoAbonoOrderByFechaAsc("d1", EstadoAbono.A_REEMBOLSAR))
                .thenReturn(List.of(cita("c", 12_000, EstadoAbono.A_REEMBOLSAR)));
        Paciente p = new Paciente();
        p.setNombre("Ana");
        p.setApellido("Ruiz");
        when(personaRepo.findById("p1")).thenReturn(Optional.of(p));

        DoctorService.ResumenAbonos r = service.resumenAbonos("d1");

        assertThat(r.cobradosMes()).isEqualTo(36_000);
        assertThat(r.retenidosMes()).isEqualTo(20_000);
        assertThat(r.porDevolver()).isEqualTo(12_000);
        assertThat(r.pacientes()).containsEntry("p1", "Ana Ruiz");
    }

    @Test
    void marcarDevuelto_cambiaElEstadoYAvisaAlPaciente() {
        Cita c = cita("c1", 12_000, EstadoAbono.A_REEMBOLSAR);
        when(citaRepo.findById("c1")).thenReturn(Optional.of(c));

        assertThat(service.marcarAbonoDevuelto("c1", "d1")).isTrue();
        assertThat(c.getEstadoAbono()).isEqualTo(EstadoAbono.DEVUELTO);
        assertThat(c.getAbonoDevueltoEn()).isNotNull();
        verify(eventos).publishEvent(new CitaEvento(CitaEvento.Tipo.ABONO_DEVUELTO, "c1"));
    }

    @Test
    void marcarDevuelto_soloSiEstabaPorDevolver() {
        Cita c = cita("c1", 12_000, EstadoAbono.RETENIDO);
        when(citaRepo.findById("c1")).thenReturn(Optional.of(c));

        assertThat(service.marcarAbonoDevuelto("c1", "d1")).isFalse();
        assertThat(c.getEstadoAbono()).isEqualTo(EstadoAbono.RETENIDO);
        verify(citaRepo, never()).save(any());
    }

    @Test
    void marcarDevuelto_deOtroMedico_noSePermite() {
        when(citaRepo.findById("c1")).thenReturn(Optional.of(cita("c1", 12_000, EstadoAbono.A_REEMBOLSAR)));

        assertThatThrownBy(() -> service.marcarAbonoDevuelto("c1", "otro")).isInstanceOf(AccessDeniedException.class);
    }
}
