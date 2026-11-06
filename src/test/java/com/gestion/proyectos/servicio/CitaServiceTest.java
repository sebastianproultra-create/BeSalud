package com.gestion.proyectos.servicio;

import com.gestion.proyectos.modelo.Cita;
import com.gestion.proyectos.modelo.CitaEvento;
import com.gestion.proyectos.modelo.EstadoCita;
import com.gestion.proyectos.modelo.HorarioAtencion;
import com.gestion.proyectos.repositorio.CitaRepositorio;
import com.gestion.proyectos.repositorio.HorarioAtencionRepositorio;
import com.gestion.proyectos.repositorio.PersonaRepositorio;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

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
    @Mock PersonaRepositorio personaRepo;
    @Mock HorarioAtencionRepositorio horarioRepo;
    @Mock ApplicationEventPublisher eventos;
    @Mock com.gestion.proyectos.repositorio.ReservaRepositorio reservaRepo;

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
        verify(eventos).publishEvent(new CitaEvento(CitaEvento.Tipo.CANCELADA, cita.getId()));
    }

    @Test
    void hayConflicto_sinCitasExistentes_retornaFalse() {
        when(citaRepo.findByDoctorIdAndFecha(anyString(), any())).thenReturn(List.of());
        when(horarioRepo.findByDoctorId(anyString())).thenReturn(List.of());
        LocalDate fecha = LocalDate.now().plusDays(1);
        assertThat(service.hayConflicto("doc1", fecha, LocalTime.of(10, 0), null)).isFalse();
    }

    private Cita citaDelPaciente(String id, String doctorId, LocalDate fecha, LocalTime hora, EstadoCita estado) {
        Cita c = new Cita();
        c.setId(id);
        c.setPacienteId("pac1");
        c.setDoctorId(doctorId);
        c.setFecha(fecha);
        c.setHora(hora);
        c.setEstado(estado);
        return c;
    }

    @Test
    void pacienteTieneConflicto_mismaHoraConOtroDoctor_retornaTrue() {
        LocalDate fecha = LocalDate.now().plusDays(1);
        when(horarioRepo.findByDoctorId(anyString())).thenReturn(List.of());
        when(citaRepo.findByPacienteIdAndFecha("pac1", fecha)).thenReturn(
                List.of(citaDelPaciente("c1", "docA", fecha, LocalTime.of(10, 0), EstadoCita.PENDIENTE)));

        assertThat(service.pacienteTieneConflicto("pac1", "docB", fecha, LocalTime.of(10, 15), null)).isTrue();
    }

    @Test
    void pacienteTieneConflicto_horasQueNoSeCruzan_retornaFalse() {
        LocalDate fecha = LocalDate.now().plusDays(1);
        when(horarioRepo.findByDoctorId(anyString())).thenReturn(List.of());
        when(citaRepo.findByPacienteIdAndFecha("pac1", fecha)).thenReturn(
                List.of(citaDelPaciente("c1", "docA", fecha, LocalTime.of(10, 0), EstadoCita.PENDIENTE)));

        assertThat(service.pacienteTieneConflicto("pac1", "docB", fecha, LocalTime.of(10, 30), null)).isFalse();
    }

    @Test
    void pacienteTieneConflicto_ignoraCitasCanceladasYLaQueSeReprograma() {
        LocalDate fecha = LocalDate.now().plusDays(1);
        when(horarioRepo.findByDoctorId(anyString())).thenReturn(List.of());
        when(citaRepo.findByPacienteIdAndFecha("pac1", fecha)).thenReturn(List.of(
                citaDelPaciente("c1", "docA", fecha, LocalTime.of(10, 0), EstadoCita.CANCELADA),
                citaDelPaciente("c2", "docB", fecha, LocalTime.of(10, 0), EstadoCita.PENDIENTE)));

        assertThat(service.pacienteTieneConflicto("pac1", "docB", fecha, LocalTime.of(10, 0), "c2")).isFalse();
    }

    // ── Confirmación de asistencia ──────────────────────────────────────────

    private static Cita citaEl(java.time.LocalDateTime inicio) {
        Cita c = new Cita();
        c.setId("c1");
        c.setPacienteId("p1");
        c.setDoctorId("d1");
        c.setFecha(inicio.toLocalDate());
        c.setHora(inicio.toLocalTime());
        return c;
    }

    private static com.gestion.proyectos.modelo.Doctor doctorCon(int horasCancelacion) {
        com.gestion.proyectos.modelo.Doctor d = new com.gestion.proyectos.modelo.Doctor();
        d.setHorasCancelacion(horasCancelacion);
        return d;
    }

    @Test
    void esperaConfirmacion_soloDesdeUnDiaAntesDelPlazoHastaLaCita() {
        var inicio = java.time.LocalDateTime.of(2026, 11, 10, 10, 0);
        var doctor = doctorCon(24); // ventana: desde 48 h antes
        Cita cita = citaEl(inicio);

        assertThat(CitaService.esperaConfirmacion(cita, doctor, inicio.minusHours(49))).isFalse();
        assertThat(CitaService.esperaConfirmacion(cita, doctor, inicio.minusHours(48))).isTrue();
        assertThat(CitaService.esperaConfirmacion(cita, doctor, inicio.minusHours(2))).isTrue();
        assertThat(CitaService.esperaConfirmacion(cita, doctor, inicio)).isFalse();

        cita.setAsistenciaConfirmada(true);
        assertThat(CitaService.esperaConfirmacion(cita, doctor, inicio.minusHours(10))).isFalse();
    }

    @Test
    void responderAsistencia_siLaDejaConfirmada() {
        Cita cita = citaEl(java.time.LocalDateTime.now().plusDays(2));
        when(citaRepo.save(any())).thenReturn(cita);

        assertThat(service.responderAsistencia(cita, true)).isTrue();
        assertThat(cita.getAsistenciaConfirmada()).isTrue();
        assertThat(cita.getEstado()).isEqualTo(EstadoCita.PENDIENTE);
    }

    @Test
    void responderAsistencia_noATiempo_cancelaYDevuelveElAbono() {
        Cita cita = citaEl(java.time.LocalDateTime.now().plusDays(2));
        cita.setAbonoCentavos(2_000_000L);
        when(personaRepo.findById("d1")).thenReturn(java.util.Optional.of(doctorCon(24)));
        when(citaRepo.save(any())).thenReturn(cita);

        assertThat(service.responderAsistencia(cita, false)).isTrue();
        assertThat(cita.getEstado()).isEqualTo(EstadoCita.CANCELADA);
        assertThat(cita.getEstadoAbono()).isEqualTo(com.gestion.proyectos.modelo.EstadoAbono.A_REEMBOLSAR);
    }

    @Test
    void responderAsistencia_noFueraDelPlazo_elProfesionalConservaElAbono() {
        Cita cita = citaEl(java.time.LocalDateTime.now().plusHours(5));
        cita.setAbonoCentavos(2_000_000L);
        when(personaRepo.findById("d1")).thenReturn(java.util.Optional.of(doctorCon(24)));
        when(citaRepo.save(any())).thenReturn(cita);

        assertThat(service.responderAsistencia(cita, false)).isTrue();
        assertThat(cita.getEstadoAbono()).isEqualTo(com.gestion.proyectos.modelo.EstadoAbono.RETENIDO);
    }

    // ── Pago completo antes de la cita ──────────────────────────────────────

    private static com.gestion.proyectos.modelo.Reserva reservaPagada(boolean total) {
        var r = new com.gestion.proyectos.modelo.Reserva();
        r.setReferencia("BS-T");
        r.setDoctorId("d1");
        r.setPacienteId("p1");
        r.setFecha(LocalDate.now().plusDays(5));
        r.setHora(LocalTime.of(9, 0));
        r.setMotivo("Control");
        r.setAbonoCentavos(1_600_000L);
        r.setPrecioCentavos(8_000_000L);
        r.setPagoTotal(total);
        r.setMontoCentavos(total ? 8_000_000L : 1_600_000L);
        r.setMedioPago("Visa •••• 4242");
        return r;
    }

    @Test
    void citaPagadaConTodo_quedaPagadaPorCompleto() {
        when(citaRepo.save(any(Cita.class))).thenAnswer(i -> i.getArgument(0));

        Cita cita = service.crearCitaConAbono(reservaPagada(true));

        assertThat(cita.getAbonoCentavos()).isEqualTo(1_600_000L);
        assertThat(cita.getSaldoCentavos()).isEqualTo(6_400_000L);
        assertThat(cita.isPagadaCompleta()).isTrue();
        assertThat(cita.getPagadoPesos()).isEqualTo(80_000L);
    }

    @Test
    void citaConSoloAbono_debePagarElRestoAntesDelPlazo() {
        when(citaRepo.save(any(Cita.class))).thenAnswer(i -> i.getArgument(0));

        Cita cita = service.crearCitaConAbono(reservaPagada(false));

        assertThat(cita.isPagadaCompleta()).isFalse();
        assertThat(cita.isSaldoObligatorio()).isTrue();
        assertThat(cita.getLimitePagoSaldo()).isEqualTo(cita.getFecha().atTime(9, 0).minusHours(24));
    }

    @Test
    void sinPagarElRestoATiempo_seCancelaYSeConservaElAbono() {
        Cita cita = new Cita();
        cita.setId("c1");
        cita.setEstado(EstadoCita.PENDIENTE);
        cita.setAbonoCentavos(1_600_000L);
        cita.setSaldoObligatorio(true);

        assertThat(service.cancelarPorFaltaDePago(cita)).isTrue();
        assertThat(cita.getEstado()).isEqualTo(EstadoCita.CANCELADA);
        assertThat(cita.getEstadoAbono()).isEqualTo(com.gestion.proyectos.modelo.EstadoAbono.RETENIDO);
        verify(eventos).publishEvent(new com.gestion.proyectos.modelo.CitaEvento(
                com.gestion.proyectos.modelo.CitaEvento.Tipo.CANCELADA_SIN_PAGO, "c1"));
    }

    @Test
    void citaYaPagada_noSeCancelaPorFaltaDePago() {
        Cita cita = new Cita();
        cita.setEstado(EstadoCita.PENDIENTE);
        cita.setSaldoObligatorio(true);
        cita.setSaldoCentavos(6_400_000L);

        assertThat(service.cancelarPorFaltaDePago(cita)).isFalse();
        assertThat(cita.getEstado()).isEqualTo(EstadoCita.PENDIENTE);
    }

    @Test
    void reprogramarSinPagar_noPuedeQuedarDentroDelPlazo() {
        var ahora = java.time.LocalDateTime.of(2026, 11, 2, 10, 0);
        Cita cita = new Cita();
        cita.setSaldoObligatorio(true);

        assertThat(CitaService.reprogramacionDejaPlazoParaPagar(cita, LocalDate.of(2026, 11, 3), LocalTime.of(9, 0), ahora))
                .as("faltan 23 horas").isFalse();
        assertThat(CitaService.reprogramacionDejaPlazoParaPagar(cita, LocalDate.of(2026, 11, 4), LocalTime.of(9, 0), ahora))
                .isTrue();
        cita.setSaldoCentavos(6_400_000L);
        assertThat(CitaService.reprogramacionDejaPlazoParaPagar(cita, LocalDate.of(2026, 11, 3), LocalTime.of(9, 0), ahora))
                .as("ya pagó todo").isTrue();
        Cita antigua = new Cita();
        assertThat(CitaService.reprogramacionDejaPlazoParaPagar(antigua, LocalDate.of(2026, 11, 2), LocalTime.of(11, 0), ahora))
                .as("citas anteriores a la regla").isTrue();
    }
}
