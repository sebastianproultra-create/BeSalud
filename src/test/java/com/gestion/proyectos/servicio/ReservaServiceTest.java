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

    // ── Pasarela de pruebas ─────────────────────────────────────────────────

    @Test
    void pruebaConTarjeta_seResuelveDeInmediatoYGuardaElMedio() {
        laReservaSeToma();
        when(citaService.doctorActivo("d1")).thenReturn(true);
        Cita cita = new Cita();
        cita.setId("c1");
        when(citaService.crearCitaConAbono(reserva)).thenReturn(cita);

        assertThat(service.pagoSimulado("BS-1", "CARD", "Visa •••• 4242", "APPROVED")).isEqualTo(Resultado.APROBADA);
        assertThat(reserva.getMedioPago()).isEqualTo("Visa •••• 4242");
        assertThat(reserva.getEstado()).isEqualTo(Reserva.Estado.APROBADA);
        assertThat(reserva.getCodigoAutorizacion()).matches("\\d{6}");
        assertThat(reserva.getPagadaEn()).isNotNull();
    }

    @Test
    void pruebaRechazada_noTieneCodigoDeAutorizacion() {
        laReservaSeToma();

        service.pagoSimulado("BS-1", "CARD", "Visa •••• 4111", "DECLINED");

        assertThat(reserva.getEstado()).isEqualTo(Reserva.Estado.RECHAZADA);
        assertThat(reserva.getCodigoAutorizacion()).isNull();
        assertThat(reserva.getPagadaEn()).isNull();
    }

    @Test
    void pruebaConNequi_quedaEnProcesoHastaQuePaseLaEspera() {
        assertThat(service.pagoSimulado("BS-1", "NEQUI", "Nequi •••• 1111", "APPROVED")).isEqualTo(Resultado.PENDIENTE);
        assertThat(reserva.getTransaccionId()).matches("\\d{5}-\\d+-\\d{5}");
        assertThat(reserva.getSimulacionEstado()).isEqualTo("APPROVED");

        service.resolverSimulacion(reserva); // todavía no pasan los segundos de espera
        verify(citaService, never()).crearCitaConAbono(any());
        assertThat(reserva.getEstado()).isEqualTo(Reserva.Estado.ESPERANDO_PAGO);
    }

    @Test
    void pruebaEnProceso_alTerminarLaEsperaSeConfirma() {
        service.pagoSimulado("BS-1", "PSE", "PSE · Banco que rechaza", "DECLINED");
        reserva.setSimulacionListaEn(com.gestion.proyectos.util.Reloj.ahora().minusSeconds(1));
        laReservaSeToma();

        service.resolverSimulacion(reserva);

        assertThat(reserva.getEstado()).isEqualTo(Reserva.Estado.RECHAZADA);
        verify(citaService, never()).crearCitaConAbono(any());
    }

    @Test
    void pruebaRepetida_noCambiaElPagoEnCurso() {
        service.pagoSimulado("BS-1", "NEQUI", "Nequi •••• 1111", "APPROVED");
        String transaccion = reserva.getTransaccionId();

        assertThat(service.pagoSimulado("BS-1", "CARD", "Visa •••• 4111", "DECLINED")).isEqualTo(Resultado.PENDIENTE);
        assertThat(reserva.getTransaccionId()).isEqualTo(transaccion);
        assertThat(reserva.getMedioPago()).isEqualTo("Nequi •••• 1111");
    }

    // ── Historial de pagos ──────────────────────────────────────────────────

    @Test
    void historial_omiteLoQueNuncaSePagoYTraduceElEstadoDelAbono() {
        Reserva aprobada = new Reserva();
        aprobada.setReferencia("BS-A");
        aprobada.setDoctorId("d1");
        aprobada.setEstado(Reserva.Estado.APROBADA);
        aprobada.setCitaId("c1");
        aprobada.setMontoCentavos(1_200_000);
        Reserva rechazada = new Reserva();
        rechazada.setReferencia("BS-R");
        rechazada.setDoctorId("d1");
        rechazada.setEstado(Reserva.Estado.RECHAZADA);
        rechazada.setMontoCentavos(1_200_000);
        Reserva sinIntento = new Reserva();
        sinIntento.setReferencia("BS-X");
        sinIntento.setDoctorId("d1");
        sinIntento.setEstado(Reserva.Estado.EXPIRADA);
        when(reservaRepo.findByPacienteIdOrderByCreadaEnDesc("p1")).thenReturn(java.util.List.of(aprobada, rechazada, sinIntento));
        Cita devuelta = new Cita();
        devuelta.setEstadoAbono(com.gestion.proyectos.modelo.EstadoAbono.DEVUELTO);
        when(citaService.buscarPorId("c1")).thenReturn(Optional.of(devuelta));
        when(citaService.buscarDoctorPorId("d1")).thenReturn(Optional.empty());

        ReservaService.HistorialPagos h = service.historialDe("p1");

        assertThat(h.movimientos()).extracting(ReservaService.MovimientoPago::referencia).containsExactly("BS-A", "BS-R");
        assertThat(h.movimientos()).extracting(ReservaService.MovimientoPago::estado)
                .containsExactly(ReservaService.EstadoPago.DEVUELTO, ReservaService.EstadoPago.RECHAZADO);
        assertThat(h.totalPagado()).isZero();
        assertThat(h.totalDevuelto()).isEqualTo(12_000);
    }

    @Test
    void estadoDelPago_pagoEnProcesoYPagadoSinCupo() {
        Reserva enProceso = new Reserva();
        enProceso.setEstado(Reserva.Estado.ESPERANDO_PAGO);
        enProceso.setTransaccionId("1234-1-12345");
        Reserva sinCupo = new Reserva();
        sinCupo.setEstado(Reserva.Estado.CONFLICTO);

        assertThat(service.estadoPagoDe(enProceso)).isEqualTo(ReservaService.EstadoPago.EN_PROCESO);
        assertThat(service.estadoPagoDe(sinCupo)).isEqualTo(ReservaService.EstadoPago.POR_DEVOLVER);
    }

    @Test
    void cadaEstadoTieneSuFiltroEnMisPagos() {
        assertThat(ReservaService.EstadoPago.CONSERVADO.getGrupo()).isEqualTo("pagados");
        assertThat(ReservaService.EstadoPago.POR_DEVOLVER.getGrupo()).isEqualTo("devoluciones");
        assertThat(ReservaService.EstadoPago.DEVUELTO.getGrupo()).isEqualTo("devoluciones");
        assertThat(ReservaService.EstadoPago.RECHAZADO.getGrupo()).isEqualTo("rechazados");
    }

    // ── Pago del saldo (el resto de la consulta, después de reservar) ───────

    private static com.gestion.proyectos.modelo.Doctor doctorDe80Mil() {
        com.gestion.proyectos.modelo.Doctor d = new com.gestion.proyectos.modelo.Doctor();
        d.setId("d1");
        d.setPrecioConsulta(80_000);
        return d;
    }

    private static Cita citaConAbono() {
        Cita c = new Cita();
        c.setId("c1");
        c.setDoctorId("d1");
        c.setPacienteId("p1");
        c.setFecha(LocalDate.now().plusDays(3));
        c.setHora(LocalTime.of(9, 0));
        c.setAbonoCentavos(2_000_000L);
        return c;
    }

    @Test
    void saldoPorPagar_esElPrecioMenosElAbonoYSoloSiLaCitaSigueAbierta() {
        var ahora = java.time.LocalDateTime.now();
        Cita c = citaConAbono();
        assertThat(ReservaService.saldoPorPagar(c, doctorDe80Mil(), ahora)).isEqualTo(60_000L);

        c.setSaldoCentavos(6_000_000L);
        assertThat(ReservaService.saldoPorPagar(c, doctorDe80Mil(), ahora)).as("ya pagada").isNull();

        Cita cancelada = citaConAbono();
        cancelada.setEstado(com.gestion.proyectos.modelo.EstadoCita.CANCELADA);
        assertThat(ReservaService.saldoPorPagar(cancelada, doctorDe80Mil(), ahora)).isNull();

        Cita pasada = citaConAbono();
        pasada.setFecha(LocalDate.now().minusDays(1));
        assertThat(ReservaService.saldoPorPagar(pasada, doctorDe80Mil(), ahora)).isNull();

        Cita sinAbono = citaConAbono();
        sinAbono.setAbonoCentavos(null);
        assertThat(ReservaService.saldoPorPagar(sinAbono, doctorDe80Mil(), ahora)).isNull();
    }

    @Test
    void apartarSaldo_creaElPagoPorElRestoSinApartarTurno() {
        when(citaService.buscarPorId("c1")).thenReturn(Optional.of(citaConAbono()));
        when(citaService.buscarDoctorPorId("d1")).thenReturn(Optional.of(doctorDe80Mil()));
        when(reservaRepo.findByCitaIdAndTipo("c1", Reserva.Tipo.SALDO)).thenReturn(java.util.List.of());
        when(reservaRepo.save(any(Reserva.class))).thenAnswer(i -> i.getArgument(0));

        Reserva r = service.apartarSaldo("c1", "p1");

        assertThat(r.isSaldo()).isTrue();
        assertThat(r.getCitaId()).isEqualTo("c1");
        assertThat(r.getMontoCentavos()).isEqualTo(6_000_000);
        r.actualizarTurno();
        assertThat(r.getTurno()).as("la cita ya ocupa el turno").isNull();
    }

    @Test
    void apartarSaldo_reutilizaElPagoQueSigueVigente() {
        when(citaService.buscarPorId("c1")).thenReturn(Optional.of(citaConAbono()));
        when(citaService.buscarDoctorPorId("d1")).thenReturn(Optional.of(doctorDe80Mil()));
        Reserva vigente = new Reserva();
        vigente.setTipo(Reserva.Tipo.SALDO);
        vigente.setMontoCentavos(6_000_000);
        vigente.setExpiraEn(java.time.LocalDateTime.now().plusMinutes(10));
        when(reservaRepo.findByCitaIdAndTipo("c1", Reserva.Tipo.SALDO)).thenReturn(java.util.List.of(vigente));

        assertThat(service.apartarSaldo("c1", "p1")).isSameAs(vigente);
        verify(reservaRepo, never()).save(any());
    }

    @Test
    void apartarSaldo_rechazaCitasDeOtroPacienteOYaPagadas() {
        when(citaService.buscarPorId("c1")).thenReturn(Optional.of(citaConAbono()));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.apartarSaldo("c1", "otro"))
                .isInstanceOf(ReservaService.SaldoNoDisponibleException.class);

        Cita pagada = citaConAbono();
        pagada.setSaldoCentavos(6_000_000L);
        when(citaService.buscarPorId("c1")).thenReturn(Optional.of(pagada));
        when(citaService.buscarDoctorPorId("d1")).thenReturn(Optional.of(doctorDe80Mil()));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.apartarSaldo("c1", "p1"))
                .isInstanceOf(ReservaService.SaldoNoDisponibleException.class);
    }

    @Test
    void saldoAprobado_dejaLaCitaPagadaSinCrearOtra() {
        reserva.setTipo(Reserva.Tipo.SALDO);
        reserva.setCitaId("c1");
        laReservaSeToma();
        when(citaService.registrarSaldo(reserva)).thenReturn(true);

        assertThat(service.confirmarPago("BS-1", "tx1", "APPROVED", 2_000_000, "COP")).isEqualTo(Resultado.APROBADA);
        assertThat(reserva.getEstado()).isEqualTo(Reserva.Estado.APROBADA);
        assertThat(reserva.getCitaId()).isEqualTo("c1");
        verify(citaService, never()).crearCitaConAbono(any());
    }

    @Test
    void saldoDeCitaCanceladaMientrasPagaba_quedaParaDevolucion() {
        reserva.setTipo(Reserva.Tipo.SALDO);
        reserva.setCitaId("c1");
        laReservaSeToma();
        when(citaService.registrarSaldo(reserva)).thenReturn(false);

        assertThat(service.confirmarPago("BS-1", "tx1", "APPROVED", 2_000_000, "COP")).isEqualTo(Resultado.CONFLICTO);
        assertThat(reserva.getCitaId()).as("el reembolso sigue ligado a su cita").isEqualTo("c1");
    }

    @Test
    void saldoRechazado_laCitaSigueConSuAbono() {
        reserva.setTipo(Reserva.Tipo.SALDO);
        reserva.setCitaId("c1");
        laReservaSeToma();

        assertThat(service.confirmarPago("BS-1", "tx1", "DECLINED", 2_000_000, "COP")).isEqualTo(Resultado.RECHAZADA);
        verify(citaService, never()).registrarSaldo(any());
    }

    @Test
    void reservarOtraCita_noAnulaElPagoDelSaldoEnCurso() {
        Reserva saldo = new Reserva();
        saldo.setTipo(Reserva.Tipo.SALDO);
        saldo.setEstado(Reserva.Estado.ESPERANDO_PAGO);
        when(reservaRepo.findByPacienteIdAndEstado("p1", Reserva.Estado.ESPERANDO_PAGO)).thenReturn(java.util.List.of(saldo));
        when(mongo.updateMulti(any(Query.class), any(Update.class), eq(Reserva.class)))
                .thenReturn(com.mongodb.client.result.UpdateResult.acknowledged(0, 0L, null));
        when(reservaRepo.save(any(Reserva.class))).thenAnswer(i -> i.getArgument(0));

        service.apartar(doctorDe80Mil(), "p1", LocalDate.now().plusDays(2), LocalTime.of(10, 0), "Control");

        assertThat(saldo.getEstado()).isEqualTo(Reserva.Estado.ESPERANDO_PAGO);
    }

    // ── Abono o total al reservar y plazo del resto ─────────────────────────

    private Reserva apartarPara(LocalDate fecha, LocalTime hora) {
        when(mongo.updateMulti(any(Query.class), any(Update.class), eq(Reserva.class)))
                .thenReturn(com.mongodb.client.result.UpdateResult.acknowledged(0, 0L, null));
        when(reservaRepo.save(any(Reserva.class))).thenAnswer(i -> i.getArgument(0));
        com.gestion.proyectos.modelo.Doctor d = doctorDe80Mil();
        d.setPorcentajeAbono(20);
        return service.apartar(d, "p1", fecha, hora, "Control");
    }

    @Test
    void reservarConTiempo_cobraElAbonoYPermiteElegirTodo() {
        Reserva r = apartarPara(LocalDate.now().plusDays(5), LocalTime.of(9, 0));

        assertThat(r.isPagoTotal()).isFalse();
        assertThat(r.getMontoCentavos()).isEqualTo(1_600_000L);
        assertThat(r.admiteElegirMonto()).isTrue();
    }

    @Test
    void reservarParaManana_cobraElTotal() {
        java.time.LocalDateTime pronto = com.gestion.proyectos.util.Reloj.ahora().plusHours(25);
        Reserva r = apartarPara(pronto.toLocalDate(), pronto.toLocalTime().withSecond(0).withNano(0));

        assertThat(r.isPagoTotal()).isTrue();
        assertThat(r.getMontoCentavos()).isEqualTo(8_000_000L);
    }

    @Test
    void elegirMonto_cambiaEntreAbonoYTotal() {
        reserva.setAbonoCentavos(1_600_000L);
        reserva.setPrecioCentavos(8_000_000L);
        reserva.setFecha(LocalDate.now().plusDays(5));
        reserva.setExpiraEn(java.time.LocalDateTime.now().plusMinutes(10));

        assertThat(service.elegirMonto("BS-1", "p1", true)).isTrue();
        assertThat(reserva.getMontoCentavos()).isEqualTo(8_000_000L);
        assertThat(service.elegirMonto("BS-1", "p1", false)).isTrue();
        assertThat(reserva.getMontoCentavos()).isEqualTo(1_600_000L);
        assertThat(service.elegirMonto("BS-1", "otro", true)).as("no es su reserva").isFalse();
    }

    @Test
    void elegirMonto_noPermiteSoloAbonoSiLaCitaEsMuyPronto() {
        java.time.LocalDateTime pronto = com.gestion.proyectos.util.Reloj.ahora().plusHours(20);
        reserva.setAbonoCentavos(1_600_000L);
        reserva.setPrecioCentavos(8_000_000L);
        reserva.setFecha(pronto.toLocalDate());
        reserva.setHora(pronto.toLocalTime());
        reserva.setExpiraEn(java.time.LocalDateTime.now().plusMinutes(10));

        assertThat(service.elegirMonto("BS-1", "p1", false)).isFalse();
    }

    @Test
    void pagoPorElTotalConLaPasarelaAbiertaEnAbono_seAceptaComoPagoTotal() {
        reserva.setAbonoCentavos(1_600_000L);
        reserva.setPrecioCentavos(8_000_000L);
        reserva.setMontoCentavos(1_600_000L);
        reserva.setFecha(LocalDate.now().plusDays(5));
        laReservaSeToma();
        when(citaService.doctorActivo("d1")).thenReturn(true);
        Cita cita = new Cita();
        cita.setId("c1");
        when(citaService.crearCitaConAbono(reserva)).thenReturn(cita);

        assertThat(service.confirmarPago("BS-1", "tx1", "APPROVED", 8_000_000, "COP")).isEqualTo(Resultado.APROBADA);
        assertThat(reserva.isPagoTotal()).isTrue();
    }

    private Cita citaSinPagarElResto(java.time.LocalDateTime inicio) {
        Cita c = citaConAbono();
        c.setFecha(inicio.toLocalDate());
        c.setHora(inicio.toLocalTime());
        c.setSaldoObligatorio(true);
        return c;
    }

    @Test
    void plazoVencidoSinPagar_cancelaLaCita() {
        Cita vencida = citaSinPagarElResto(com.gestion.proyectos.util.Reloj.ahora().plusHours(20));
        Cita aTiempo = citaSinPagarElResto(com.gestion.proyectos.util.Reloj.ahora().plusDays(4));
        aTiempo.setId("c2");
        when(citaService.citasConSaldoPendiente()).thenReturn(java.util.List.of(vencida, aTiempo));
        when(reservaRepo.findByCitaIdAndTipo("c1", Reserva.Tipo.SALDO)).thenReturn(java.util.List.of());
        when(citaService.cancelarPorFaltaDePago(vencida)).thenReturn(true);

        assertThat(service.cancelarCitasSinPago()).isEqualTo(1);
        verify(citaService, never()).cancelarPorFaltaDePago(aTiempo);
    }

    @Test
    void plazoVencidoConPagoDelRestoEnCurso_esperaALaPasarela() {
        Cita vencida = citaSinPagarElResto(com.gestion.proyectos.util.Reloj.ahora().plusHours(20));
        Reserva enCurso = new Reserva();
        enCurso.setTipo(Reserva.Tipo.SALDO);
        enCurso.setEstado(Reserva.Estado.EXPIRADA);
        enCurso.setTransaccionId("tx-pse");
        when(citaService.citasConSaldoPendiente()).thenReturn(java.util.List.of(vencida));
        when(reservaRepo.findByCitaIdAndTipo("c1", Reserva.Tipo.SALDO)).thenReturn(java.util.List.of(enCurso));

        assertThat(service.cancelarCitasSinPago()).isZero();
        verify(citaService, never()).cancelarPorFaltaDePago(any());
    }

    @Test
    void conPlazoVencido_yaNoSeOfrecePagarElResto() {
        Cita vencida = citaSinPagarElResto(java.time.LocalDateTime.now().plusHours(20));
        assertThat(ReservaService.saldoPorPagar(vencida, doctorDe80Mil(), java.time.LocalDateTime.now())).isNull();
    }
}
