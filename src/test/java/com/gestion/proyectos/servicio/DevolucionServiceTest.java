package com.gestion.proyectos.servicio;

import com.gestion.proyectos.modelo.Cita;
import com.gestion.proyectos.modelo.CitaEvento;
import com.gestion.proyectos.modelo.Devolucion;
import com.gestion.proyectos.modelo.DevolucionSinCitaEvento;
import com.gestion.proyectos.modelo.EstadoAbono;
import com.gestion.proyectos.modelo.Reserva;
import com.gestion.proyectos.repositorio.CitaRepositorio;
import com.gestion.proyectos.repositorio.DevolucionRepositorio;
import com.gestion.proyectos.repositorio.PersonaRepositorio;
import com.gestion.proyectos.repositorio.ReservaRepositorio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DevolucionServiceTest {

    private static final String MEDIO = "Nequi";
    private static final MockMultipartFile PNG = new MockMultipartFile("comprobante", "recibo.png", "image/png",
            new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3});

    @Mock DevolucionRepositorio repo;
    @Mock CitaRepositorio citaRepo;
    @Mock ReservaRepositorio reservaRepo;
    @Mock PersonaRepositorio personaRepo;
    @Mock ApplicationEventPublisher eventos;

    DevolucionService service;
    Cita cita;

    @BeforeEach
    void preparar() {
        service = new DevolucionService(repo, citaRepo, reservaRepo, personaRepo, eventos);
        cita = new Cita();
        cita.setId("c1");
        cita.setDoctorId("d1");
        cita.setPacienteId("p1");
        cita.setAbonoCentavos(2_000_000L);
        cita.setReferenciaPago("BS-1");
        cita.setEstadoAbono(EstadoAbono.A_REEMBOLSAR);
        when(citaRepo.findById("c1")).thenReturn(Optional.of(cita));
        when(repo.findByOrigen("cita-c1")).thenReturn(Optional.empty());
    }

    private String registrar(String medio, String numero, Long monto, MockMultipartFile archivo) {
        return service.registrar("cita-c1", medio, numero, monto, archivo, "admin@besalud.com");
    }

    @Test
    void registrar_guardaLaPruebaMarcaDevueltoYAvisaAlPaciente() {
        assertThat(registrar(MEDIO, " abc 12345 ", 20_000L, PNG)).isNull();

        ArgumentCaptor<Devolucion> guardada = ArgumentCaptor.forClass(Devolucion.class);
        verify(repo).save(guardada.capture());
        Devolucion d = guardada.getValue();
        assertThat(d.getCodigo()).startsWith("DEV-");
        assertThat(d.getNumeroOperacion()).isEqualTo("ABC12345");
        assertThat(d.getMontoPesos()).isEqualTo(20_000);
        assertThat(d.getComprobanteTipo()).isEqualTo("image/png");
        assertThat(d.getComprobanteHash()).hasSize(64);
        assertThat(d.getRegistradaPor()).isEqualTo("admin@besalud.com");
        assertThat(d.getReferencias()).containsExactly("BS-1");
        assertThat(d.getEstado()).isEqualTo(Devolucion.Estado.REGISTRADA);
        assertThat(cita.getEstadoAbono()).isEqualTo(EstadoAbono.DEVUELTO);
        assertThat(cita.getAbonoDevueltoEn()).isNotNull();
        verify(eventos).publishEvent(new CitaEvento(CitaEvento.Tipo.ABONO_DEVUELTO, "c1"));
    }

    @Test
    void registrar_sinPruebaCompletaNoMarcaNadaComoDevuelto() {
        assertThat(registrar("Efectivo", "ABC12345", 20_000L, PNG)).isEqualTo("medio");
        assertThat(registrar(MEDIO, "12", 20_000L, PNG)).isEqualTo("numero");
        assertThat(registrar(MEDIO, "ABC12345", 19_999L, PNG)).isEqualTo("monto");
        assertThat(registrar(MEDIO, "ABC12345", null, PNG)).isEqualTo("monto");
        assertThat(registrar(MEDIO, "ABC12345", 20_000L, null)).isEqualTo("archivo_vacio");
        assertThat(registrar(MEDIO, "ABC12345", 20_000L, new MockMultipartFile("comprobante", "falso.pdf",
                "application/pdf", "<html>no soy un pdf</html>".getBytes()))).isEqualTo("archivo_tipo");

        verify(repo, never()).save(any());
        assertThat(cita.getEstadoAbono()).isEqualTo(EstadoAbono.A_REEMBOLSAR);
    }

    @Test
    void registrar_noAceptaUnaPruebaYaUsadaEnOtraDevolucion() {
        when(repo.existsByNumeroOperacionAndOrigenNot("ABC12345", "cita-c1")).thenReturn(true);
        assertThat(registrar(MEDIO, "ABC12345", 20_000L, PNG)).isEqualTo("numero_usado");

        when(repo.existsByNumeroOperacionAndOrigenNot("ABC12345", "cita-c1")).thenReturn(false);
        when(repo.existsByComprobanteHashAndOrigenNot(any(), eq("cita-c1"))).thenReturn(true);
        assertThat(registrar(MEDIO, "ABC12345", 20_000L, PNG)).isEqualTo("archivo_usado");

        verify(repo, never()).save(any());
    }

    @Test
    void registrar_siDosAdministradoresChocanSoloQuedaUna() {
        when(repo.save(any())).thenThrow(new DuplicateKeyException("duplicada"));

        assertThat(registrar(MEDIO, "ABC12345", 20_000L, PNG)).isEqualTo("numero_usado");

        assertThat(cita.getEstadoAbono()).isEqualTo(EstadoAbono.A_REEMBOLSAR);
        verify(citaRepo, never()).save(any());
    }

    @Test
    void registrar_loQueNoEstaPendienteNoSeToca() {
        cita.setEstadoAbono(EstadoAbono.DEVUELTO);

        assertThat(registrar(MEDIO, "ABC12345", 20_000L, PNG)).isEqualTo("no_pendiente");

        verify(repo, never()).save(any());
    }

    private Devolucion registrada() {
        Devolucion d = new Devolucion();
        d.setCodigo("DEV-1");
        d.setOrigen("cita-c1");
        d.setCitaId("c1");
        d.setPacienteId("p1");
        when(repo.findByCodigo("DEV-1")).thenReturn(Optional.of(d));
        cita.setEstadoAbono(EstadoAbono.DEVUELTO);
        return d;
    }

    @Test
    void confirmar_cierraLaDevolucionUnaSolaVez() {
        Devolucion d = registrada();

        assertThat(service.confirmar("DEV-1", "p1")).isTrue();

        assertThat(d.getEstado()).isEqualTo(Devolucion.Estado.CONFIRMADA);
        assertThat(d.getConfirmadaEn()).isNotNull();
        assertThat(service.confirmar("DEV-1", "p1")).isFalse();
        assertThat(service.reclamar("DEV-1", "p1", "ya no")).isFalse();
    }

    @Test
    void reclamar_vuelveAPendientesParaQueSeRevise() {
        Devolucion d = registrada();

        assertThat(service.reclamar("DEV-1", "p1", "  No veo nada en mi Nequi ")).isTrue();

        assertThat(d.getEstado()).isEqualTo(Devolucion.Estado.RECLAMADA);
        assertThat(d.getNotaReclamo()).isEqualTo("No veo nada en mi Nequi");
        assertThat(cita.getEstadoAbono()).isEqualTo(EstadoAbono.A_REEMBOLSAR);
        assertThat(cita.getAbonoDevueltoEn()).isNull();
        verify(citaRepo).save(cita);
    }

    private Reserva pagoSinCupo() {
        Reserva r = new Reserva();
        r.setReferencia("BS-9");
        r.setPacienteId("p1");
        r.setDoctorId("d1");
        r.setMontoCentavos(2_000_000L);
        r.setEstado(Reserva.Estado.CONFLICTO);
        r.setMotivoConflicto("Pagó, pero el horario ya no estaba disponible");
        when(reservaRepo.findByReferencia("BS-9")).thenReturn(Optional.of(r));
        when(repo.findByOrigen("reserva-BS-9")).thenReturn(Optional.empty());
        return r;
    }

    @Test
    void pagoSinCupo_apareceEnPendientesYSeDevuelveConLaMismaPrueba() {
        Reserva r = pagoSinCupo();
        when(reservaRepo.listarPagosPorDevolver()).thenReturn(java.util.List.of(r));
        when(reservaRepo.contarPagosPorDevolver()).thenReturn(1L);

        assertThat(service.contarPendientes()).isEqualTo(1);
        assertThat(service.pendientes()).singleElement().satisfies(p -> {
            assertThat(p.origen()).isEqualTo("reserva-BS-9");
            assertThat(p.montoPesos()).isEqualTo(20_000);
            assertThat(p.motivo()).contains("ya no estaba disponible");
        });
        assertThat(service.registrar("reserva-BS-9", MEDIO, "ABC12345", 19_000L, PNG, "admin")).isEqualTo("monto");
        assertThat(r.isDevuelta()).isFalse();

        assertThat(service.registrar("reserva-BS-9", MEDIO, "ABC12345", 20_000L, PNG, "admin")).isNull();

        assertThat(r.isDevuelta()).isTrue();
        verify(reservaRepo).save(r);
        verify(eventos).publishEvent(new DevolucionSinCitaEvento(r, MEDIO, "ABC12345"));
        assertThat(service.registrar("reserva-BS-9", MEDIO, "ABC12345", 20_000L, PNG, "admin")).isEqualTo("no_pendiente");
    }

    @Test
    void pagoSinCupo_siElPacienteReclamaVuelveAPendientes() {
        Reserva r = pagoSinCupo();
        r.setDevuelta(true);
        Devolucion d = new Devolucion();
        d.setCodigo("DEV-9");
        d.setOrigen("reserva-BS-9");
        d.setPacienteId("p1");
        when(repo.findByCodigo("DEV-9")).thenReturn(Optional.of(d));

        assertThat(service.reclamar("DEV-9", "p1", null)).isTrue();

        assertThat(r.isDevuelta()).isFalse();
        assertThat(d.getEstado()).isEqualTo(Devolucion.Estado.RECLAMADA);
    }

    @Test
    void otroPacienteNoVeNiConfirmaNiReclama() {
        Devolucion d = registrada();

        assertThat(service.buscarDelPaciente("DEV-1", "otro")).isEmpty();
        assertThat(service.confirmar("DEV-1", "otro")).isFalse();
        assertThat(service.reclamar("DEV-1", "otro", "")).isFalse();

        assertThat(d.getEstado()).isEqualTo(Devolucion.Estado.REGISTRADA);
        verify(repo, never()).save(any());
    }
}
