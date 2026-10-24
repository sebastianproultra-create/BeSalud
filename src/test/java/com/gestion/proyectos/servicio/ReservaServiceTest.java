package com.gestion.proyectos.servicio;

import com.gestion.proyectos.modelo.Cita;
import com.gestion.proyectos.modelo.Reserva;
import com.gestion.proyectos.repositorio.ReservaRepositorio;
import com.gestion.proyectos.servicio.ReservaService.Resultado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservaServiceTest {

    @Mock ReservaRepositorio reservaRepo;
    @Mock CitaService citaService;
    @Mock MongoTemplate mongo;

    ReservaService service;
    Reserva reserva;

    @BeforeEach
    void setUp() {
        service = new ReservaService(reservaRepo, citaService, mongo, 15);
        reserva = new Reserva();
        reserva.setReferencia("BS-1");
        reserva.setDoctorId("d1");
        reserva.setPacienteId("p1");
        reserva.setFecha(LocalDate.of(2026, 11, 2));
        reserva.setHora(LocalTime.of(9, 0));
        reserva.setMontoCentavos(2_000_000);
        reserva.setEstado(Reserva.Estado.ESPERANDO_PAGO);
        org.mockito.Mockito.lenient().when(reservaRepo.findByReferencia("BS-1")).thenReturn(Optional.of(reserva));
    }

    private void laReservaSeToma() {
        when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Reserva.class)))
                .thenReturn(reserva);
    }

    @Test
    void pagoAprobado_creaLaCita() {
        laReservaSeToma();
        when(citaService.doctorActivo("d1")).thenReturn(true);
        Cita cita = new Cita();
        cita.setId("c1");
        when(citaService.crearCitaConAbono(reserva)).thenReturn(cita);

        assertThat(service.confirmarPago("BS-1", "tx1", "APPROVED", 2_000_000, "COP")).isEqualTo(Resultado.APROBADA);
        assertThat(reserva.getEstado()).isEqualTo(Reserva.Estado.APROBADA);
        assertThat(reserva.getCitaId()).isEqualTo("c1");
    }

    @Test
    void pagoRechazado_noCreaCita() {
        laReservaSeToma();

        assertThat(service.confirmarPago("BS-1", "tx1", "DECLINED", 2_000_000, "COP")).isEqualTo(Resultado.RECHAZADA);
        assertThat(reserva.getEstado()).isEqualTo(Reserva.Estado.RECHAZADA);
        verify(citaService, never()).crearCitaConAbono(any());
    }

    @Test
    void montoDistinto_quedaEnConflicto() {
        laReservaSeToma();

        assertThat(service.confirmarPago("BS-1", "tx1", "APPROVED", 100, "COP")).isEqualTo(Resultado.CONFLICTO);
        verify(citaService, never()).crearCitaConAbono(any());
    }

    @Test
    void turnoTomadoMientrasPagaba_quedaEnConflicto() {
        laReservaSeToma();
        when(citaService.hayConflicto("d1", reserva.getFecha(), reserva.getHora(), null, "p1")).thenReturn(true);

        assertThat(service.confirmarPago("BS-1", "tx1", "APPROVED", 2_000_000, "COP")).isEqualTo(Resultado.CONFLICTO);
        assertThat(reserva.getEstado()).isEqualTo(Reserva.Estado.CONFLICTO);
        verify(citaService, never()).crearCitaConAbono(any());
    }

    @Test
    void segundaConfirmacion_noDuplicaLaCita() {
        reserva.setEstado(Reserva.Estado.APROBADA);

        assertThat(service.confirmarPago("BS-1", "tx1", "APPROVED", 2_000_000, "COP")).isEqualTo(Resultado.APROBADA);
        verify(mongo, never()).findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Reserva.class));
        verify(citaService, never()).crearCitaConAbono(any());
    }

    @Test
    void pagoEnProceso_quedaPendiente() {
        assertThat(service.confirmarPago("BS-1", "tx1", "PENDING", 2_000_000, "COP")).isEqualTo(Resultado.PENDIENTE);
        verify(citaService, never()).crearCitaConAbono(any());
    }

    @Test
    void referenciaDesconocida() {
        assertThat(service.confirmarPago("BS-X", "tx1", "APPROVED", 1, "COP")).isEqualTo(Resultado.NO_EXISTE);
    }
}
