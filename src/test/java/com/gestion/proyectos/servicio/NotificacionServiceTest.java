package com.gestion.proyectos.servicio;

import com.gestion.proyectos.modelo.Cita;
import com.gestion.proyectos.modelo.Dictamen;
import com.gestion.proyectos.modelo.CitaEvento;
import com.gestion.proyectos.modelo.Doctor;
import com.gestion.proyectos.modelo.EstadoCita;
import com.gestion.proyectos.modelo.Paciente;
import com.gestion.proyectos.repositorio.CitaRepositorio;
import com.gestion.proyectos.repositorio.PersonaRepositorio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificacionServiceTest {

    @Mock CitaRepositorio citaRepo;
    @Mock PersonaRepositorio personaRepo;
    @Mock CorreoService correo;

    NotificacionService service;
    Paciente paciente;
    Doctor doctor;

    @BeforeEach
    void setUp() {
        service = new NotificacionService(citaRepo, personaRepo, correo, "https://besalud.test/");
        paciente = new Paciente("Ana", "<b>Ruiz</b>", "3001112233", "123", "ana@gmail.com", "x");
        paciente.setId("p1");
        doctor = new Doctor();
        doctor.setId("d1");
        doctor.setNombre("Laura");
        doctor.setApellido("Gómez");
        doctor.setEspecialidad("Cardiología");
    }

    private Cita cita(LocalDate fecha) {
        Cita c = new Cita();
        c.setId("c1");
        c.setPacienteId("p1");
        c.setDoctorId("d1");
        c.setFecha(fecha);
        c.setHora(LocalTime.of(9, 30));
        c.setMotivo("Palpitaciones <script>");
        c.setEstado(EstadoCita.PENDIENTE);
        return c;
    }

    private void conPersonas() {
        when(personaRepo.findById("p1")).thenReturn(Optional.of(paciente));
        when(personaRepo.findById("d1")).thenReturn(Optional.of(doctor));
    }

    @Test
    void citaCreada_enviaConfirmacionAlPaciente() {
        when(citaRepo.findById("c1")).thenReturn(Optional.of(cita(LocalDate.of(2026, 10, 13))));
        conPersonas();

        service.alCambiarCita(new CitaEvento(CitaEvento.Tipo.CREADA, "c1"));

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(correo).enviar(eq("ana@gmail.com"), anyString(), eq("Tu cita quedó agendada · BeSalud"), html.capture());
        assertThat(html.getValue())
                .contains("Martes 13 de octubre de 2026")
                .contains("Dr(a). Laura Gómez")
                .contains("Cardiología")
                .contains("https://besalud.test/pacientes/landing");
    }

    @Test
    void dictamen_avisaSinIncluirDatosClinicos() {
        Cita c = cita(LocalDate.of(2026, 10, 13));
        Dictamen d = new Dictamen();
        d.setMotivoConsulta("Dolor torácico");
        c.setDictamen(d);
        when(citaRepo.findById("c1")).thenReturn(Optional.of(c));
        conPersonas();

        service.alCambiarCita(new CitaEvento(CitaEvento.Tipo.DICTAMEN, "c1"));

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(correo).enviar(eq("ana@gmail.com"), anyString(), eq("Tu dictamen médico está disponible · BeSalud"), html.capture());
        assertThat(html.getValue()).doesNotContain("Dolor torácico");
    }

    @Test
    void citaPagada_enviaComprobanteConEnlaceAGoogleCalendar() throws Exception {
        Cita pagada = cita(LocalDate.of(2026, 10, 30));
        pagada.setMotivo("Chequeo anual");
        pagada.setReferenciaPago("BS-3236031AB887409B");
        pagada.setAbonoCentavos(1_600_000L);
        doctor.setPrecioConsulta(80_000);
        doctor.setDireccionConsultorio("Calle 72 # 45-10, consultorio 302");
        when(citaRepo.findById("c1")).thenReturn(Optional.of(pagada));
        conPersonas();

        service.alCambiarCita(new CitaEvento(CitaEvento.Tipo.CREADA, "c1"));

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(correo).enviar(eq("ana@gmail.com"), anyString(), eq("Cita confirmada · comprobante de pago · BeSalud"),
                html.capture());
        assertThat(html.getValue()).contains("Comprobante de pago", "$16.000", "$64.000", "BS-3236031AB887409B",
                "jueves 29 de octubre", "calendar.google.com", "dates=20261030T093000")
                .doesNotContain("Outlook");
        // Vista previa opcional: mvn test -Dpreview.correo=ruta.html
        String destino = System.getProperty("preview.correo");
        if (destino != null)
            java.nio.file.Files.writeString(java.nio.file.Path.of(destino), html.getValue());
    }

    @Test
    void plantilla_escapaElTextoDelUsuario() {
        String html = service.plantilla(paciente, doctor, cita(LocalDate.now()), "T", "M", "B");
        assertThat(html).doesNotContain("<script>").contains("&lt;script&gt;");
    }

    @Test
    void recordatorios_soloUnaVezPorCita() {
        Cita pendiente = cita(LocalDate.now().plusDays(1));
        Cita yaAvisada = cita(LocalDate.now().plusDays(1));
        yaAvisada.setId("c2");
        yaAvisada.setRecordatorioEnviado(true);
        when(citaRepo.findByFechaAndEstado(LocalDate.now().plusDays(1), EstadoCita.PENDIENTE))
                .thenReturn(List.of(pendiente, yaAvisada));
        conPersonas();

        service.enviarRecordatorios();

        verify(correo).enviar(eq("ana@gmail.com"), anyString(), eq("Recordatorio: tu cita es mañana · BeSalud"), anyString());
        verify(citaRepo).save(pendiente);
        verify(citaRepo, never()).save(yaAvisada);
        assertThat(pendiente.isRecordatorioEnviado()).isTrue();
    }

    @Test
    void citaInexistente_noEnviaNada() {
        when(citaRepo.findById("x")).thenReturn(Optional.empty());
        service.alCambiarCita(new CitaEvento(CitaEvento.Tipo.CANCELADA, "x"));
        verify(correo, never()).enviar(any(), any(), any(), any());
    }
}
