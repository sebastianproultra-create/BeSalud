package com.gestion.proyectos.servicio;

import com.gestion.proyectos.modelo.Cita;
import com.gestion.proyectos.modelo.Doctor;
import com.gestion.proyectos.modelo.EstadoAbono;
import com.gestion.proyectos.modelo.EstadoCita;
import com.gestion.proyectos.modelo.HorarioAtencion;
import com.gestion.proyectos.modelo.Liquidacion;
import com.gestion.proyectos.repositorio.CitaRepositorio;
import com.gestion.proyectos.repositorio.HorarioAtencionRepositorio;
import com.gestion.proyectos.repositorio.LiquidacionRepositorio;
import com.gestion.proyectos.repositorio.PersonaRepositorio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GananciasServiceTest {

    private static final YearMonth MES = YearMonth.of(2026, 3);
    private static final MockMultipartFile PNG = new MockMultipartFile("comprobante", "recibo.png", "image/png",
            new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3});

    @Mock CitaRepositorio citaRepo;
    @Mock LiquidacionRepositorio liquidacionRepo;
    @Mock PersonaRepositorio personaRepo;
    @Mock HorarioAtencionRepositorio horarioRepo;
    @Mock org.springframework.context.ApplicationEventPublisher eventos;

    GananciasService service;

    @BeforeEach
    void preparar() {
        service = new GananciasService(citaRepo, liquidacionRepo, personaRepo, horarioRepo, eventos, 10);
    }

    private static Cita cita(String id, LocalDate fecha, EstadoCita estado, EstadoAbono abono, long pesos) {
        Cita c = new Cita();
        c.setId(id);
        c.setDoctorId("d1");
        c.setPacienteId("p1");
        c.setFecha(fecha);
        c.setEstado(estado);
        c.setEstadoAbono(abono);
        c.setAbonoCentavos(pesos * 100);
        return c;
    }

    @Test
    void origen_soloEsGananciaLoQueYaEsDelMedico() {
        LocalDate f = MES.atDay(2);
        assertThat(GananciasService.origenDe(cita("a", f, EstadoCita.COMPLETADA, EstadoAbono.PAGADO, 1)))
                .isEqualTo(GananciasService.Origen.ATENDIDA);
        assertThat(GananciasService.origenDe(cita("b", f, EstadoCita.NO_ASISTIO, EstadoAbono.RETENIDO, 1)))
                .isEqualTo(GananciasService.Origen.NO_ASISTIO);
        assertThat(GananciasService.origenDe(cita("c", f, EstadoCita.CANCELADA, EstadoAbono.RETENIDO, 1)))
                .isEqualTo(GananciasService.Origen.CANCELACION);
        assertThat(GananciasService.liquidable(cita("d", f, EstadoCita.PENDIENTE, EstadoAbono.PAGADO, 1))).isFalse();
        assertThat(GananciasService.liquidable(cita("e", f, EstadoCita.CANCELADA, EstadoAbono.A_REEMBOLSAR, 1))).isFalse();
        assertThat(GananciasService.liquidable(cita("f", f, EstadoCita.CANCELADA, EstadoAbono.DEVUELTO, 1))).isFalse();

        Cita pagada = cita("g", f, EstadoCita.COMPLETADA, EstadoAbono.PAGADO, 1);
        pagada.setLiquidacionId("l1");
        assertThat(GananciasService.liquidable(pagada)).isFalse();

        Cita sinPago = new Cita();
        sinPago.setEstado(EstadoCita.COMPLETADA);
        assertThat(GananciasService.origenDe(sinPago)).isNull();
    }

    @Test
    void panel_separaLoGanadoLoPendienteYLoDevueltoConLaComisionDeCadaCita() {
        Cita atendida = cita("a", MES.atDay(2), EstadoCita.COMPLETADA, EstadoAbono.PAGADO, 80_000);
        Cita conOtraComision = cita("b", MES.atDay(3), EstadoCita.COMPLETADA, EstadoAbono.PAGADO, 100_000);
        conOtraComision.setComisionPorcentaje(20);
        Cita falta = cita("c", MES.atDay(9), EstadoCita.NO_ASISTIO, EstadoAbono.RETENIDO, 20_000);
        Cita devuelta = cita("d", MES.atDay(10), EstadoCita.CANCELADA, EstadoAbono.DEVUELTO, 20_000);
        Cita mesAnterior = cita("e", MES.minusMonths(1).atDay(5), EstadoCita.COMPLETADA, EstadoAbono.PAGADO, 100_000);
        mesAnterior.setLiquidacionId("l1");
        when(citaRepo.findByDoctorId("d1")).thenReturn(List.of(atendida, conOtraComision, falta, devuelta, mesAnterior));
        Liquidacion anterior = new Liquidacion();
        anterior.setId("l1");
        anterior.setCodigo("LIQ-1");
        anterior.setNetoPesos(90_000);
        when(liquidacionRepo.listarDelDoctor("d1")).thenReturn(List.of(anterior));
        when(horarioRepo.findByDoctorId("d1")).thenReturn(List.of(
                new HorarioAtencion("d1", DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(10, 0))));

        GananciasService.Panel p = service.panel("d1", MES);

        assertThat(p.brutoPesos()).isEqualTo(200_000);
        assertThat(p.comisionPesos()).isEqualTo(8_000 + 20_000 + 2_000);
        assertThat(p.netoPesos()).isEqualTo(170_000);
        assertThat(p.variacion()).isEqualTo(89); // 170.000 frente a 90.000
        assertThat(p.porLiquidarPesos()).isEqualTo(170_000);
        assertThat(p.pagadoPesos()).isEqualTo(90_000);
        assertThat(p.lineas()).hasSize(4);
        assertThat(p.lineas().get(0).origen()).isEqualTo(GananciasService.Origen.DEVUELTO);
        assertThat(p.lineas().get(0).netoPesos()).isZero();
        // Marzo de 2026 tiene 5 lunes: 2 horas y 4 turnos de 30 minutos cada uno
        assertThat(p.turnosOfrecidos()).isEqualTo(20);
        assertThat(p.turnosOcupados()).isEqualTo(3);
        assertThat(p.horasOfrecidas()).isEqualTo(10);
        assertThat(p.ingresoPorHora()).isEqualTo(17_000);
    }

    @Test
    void panel_sinMesPedidoAbreElUltimoConGanancias() {
        when(citaRepo.findByDoctorId("d1")).thenReturn(List.of(cita("a", MES.atDay(2), EstadoCita.COMPLETADA, EstadoAbono.PAGADO, 1_000)));

        assertThat(service.panel("d1", null).mes()).isEqualTo(MES);
    }

    private void dosCitasPorPagar() {
        when(citaRepo.findByDoctorId("d1")).thenReturn(List.of(
                cita("a", MES.atDay(2), EstadoCita.COMPLETADA, EstadoAbono.PAGADO, 80_000),
                cita("b", MES.atDay(3), EstadoCita.NO_ASISTIO, EstadoAbono.RETENIDO, 20_000),
                cita("c", MES.atDay(4), EstadoCita.PENDIENTE, EstadoAbono.PAGADO, 20_000)));
    }

    @Test
    void registrar_pagaSoloLoDisponibleYMarcaEsasCitas() {
        dosCitasPorPagar();
        when(citaRepo.marcarLiquidadas(any(), any())).thenReturn(2L);

        assertThat(service.registrar("d1", "Nequi", " neq 12345 ", 90_000L, PNG, "admin@besalud.com")).isNull();
        verify(eventos).publishEvent(any(com.gestion.proyectos.modelo.LiquidacionRegistradaEvento.class));

        ArgumentCaptor<Liquidacion> guardada = ArgumentCaptor.forClass(Liquidacion.class);
        verify(liquidacionRepo).save(guardada.capture());
        Liquidacion l = guardada.getValue();
        assertThat(l.getCitaIds()).containsExactly("a", "b");
        assertThat(l.getBrutoPesos()).isEqualTo(100_000);
        assertThat(l.getComisionPesos()).isEqualTo(10_000);
        assertThat(l.getNetoPesos()).isEqualTo(90_000);
        assertThat(l.getNumeroOperacion()).isEqualTo("NEQ12345");
        assertThat(l.getComprobanteHash()).hasSize(64);
        verify(citaRepo).marcarLiquidadas(List.of("a", "b"), l.getId());
    }

    @Test
    void registrar_sinPruebaCompletaNoPagaNada() {
        dosCitasPorPagar();

        assertThat(service.registrar("d1", "Efectivo", "NEQ12345", 90_000L, PNG, "a")).isEqualTo("medio");
        assertThat(service.registrar("d1", "Nequi", "12", 90_000L, PNG, "a")).isEqualTo("numero");
        assertThat(service.registrar("d1", "Nequi", "NEQ12345", 89_999L, PNG, "a")).isEqualTo("monto");
        assertThat(service.registrar("d1", "Nequi", "NEQ12345", 90_000L, null, "a")).isEqualTo("archivo_vacio");
        assertThat(service.registrar("d1", "Nequi", "NEQ12345", 90_000L, new MockMultipartFile("c", "falso.pdf",
                "application/pdf", "<html>".getBytes()), "a")).isEqualTo("archivo_tipo");

        when(liquidacionRepo.existsByComprobanteHash(any())).thenReturn(true);
        assertThat(service.registrar("d1", "Nequi", "NEQ12345", 90_000L, PNG, "a")).isEqualTo("archivo_usado");
        when(liquidacionRepo.existsByNumeroOperacion("NEQ12345")).thenReturn(true);
        assertThat(service.registrar("d1", "Nequi", "NEQ12345", 90_000L, PNG, "a")).isEqualTo("numero_usado");

        verify(liquidacionRepo, never()).save(any());
        verify(citaRepo, never()).marcarLiquidadas(any(), any());
    }

    @Test
    void registrar_sinSaldoNoHaceNada() {
        when(citaRepo.findByDoctorId("d1")).thenReturn(List.of());

        assertThat(service.registrar("d1", "Nequi", "NEQ12345", 0L, PNG, "a")).isEqualTo("sin_saldo");
    }

    @Test
    void porLiquidar_agrupaPorMedicoDeMayorAMenor() {
        Doctor uno = new Doctor();
        uno.setId("d1");
        uno.setNombre("Ana");
        uno.setApellido("Ruiz");
        when(personaRepo.findById(eq("d1"))).thenReturn(Optional.of(uno));
        Cita otra = cita("x", MES.atDay(2), EstadoCita.COMPLETADA, EstadoAbono.PAGADO, 200_000);
        otra.setDoctorId("d2");
        when(citaRepo.findByAbonoCentavosIsNotNullAndLiquidacionIdIsNull()).thenReturn(List.of(
                cita("a", MES.atDay(2), EstadoCita.COMPLETADA, EstadoAbono.PAGADO, 80_000),
                cita("c", MES.atDay(4), EstadoCita.PENDIENTE, EstadoAbono.PAGADO, 20_000),
                otra));

        List<GananciasService.PorLiquidar> lista = service.porLiquidar();

        assertThat(lista).extracting(GananciasService.PorLiquidar::doctorId).containsExactly("d2", "d1");
        assertThat(lista.get(1).medico()).isEqualTo("Ana Ruiz");
        assertThat(lista.get(1).citas()).isEqualTo(1);
        assertThat(lista.get(1).netoPesos()).isEqualTo(72_000);
    }

    @Test
    void otroMedicoNoVeLaLiquidacion() {
        Liquidacion l = new Liquidacion();
        l.setDoctorId("d1");
        when(liquidacionRepo.findByCodigo("LIQ-1")).thenReturn(Optional.of(l));

        assertThat(service.buscarDelDoctor("LIQ-1", "d1")).isPresent();
        assertThat(service.buscarDelDoctor("LIQ-1", "otro")).isEmpty();
    }
}
