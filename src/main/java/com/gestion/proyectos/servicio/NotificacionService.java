package com.gestion.proyectos.servicio;

import com.gestion.proyectos.modelo.Cita;
import com.gestion.proyectos.modelo.CitaEvento;
import com.gestion.proyectos.modelo.Doctor;
import com.gestion.proyectos.modelo.EstadoCita;
import com.gestion.proyectos.modelo.Paciente;
import com.gestion.proyectos.repositorio.CitaRepositorio;
import com.gestion.proyectos.repositorio.PersonaRepositorio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
public class NotificacionService {

    private static final Logger log = LoggerFactory.getLogger(NotificacionService.class);
    private static final Locale ES = Locale.forLanguageTag("es-CO");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", ES);
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("h:mm a", ES);

    private final CitaRepositorio citaRepo;
    private final PersonaRepositorio personaRepo;
    private final CorreoService correo;
    private final String urlApp;

    public NotificacionService(CitaRepositorio citaRepo, PersonaRepositorio personaRepo, CorreoService correo,
            @Value("${app.url:http://localhost:8080}") String urlApp) {
        this.citaRepo = citaRepo;
        this.personaRepo = personaRepo;
        this.correo = correo;
        this.urlApp = urlApp.replaceAll("/+$", "");
    }

    @Async
    @EventListener
    public void alCambiarCita(CitaEvento evento) {
        citaRepo.findById(evento.citaId()).ifPresent(cita -> {
            switch (evento.tipo()) {
                case CREADA -> enviar(cita, "Tu cita quedó agendada", "Tu cita quedó agendada",
                        "Te esperamos. Recuerda llegar 10 minutos antes.", "Ver mis citas");
                case REPROGRAMADA -> enviar(cita, "Tu cita fue reprogramada", "Cambiamos la fecha de tu cita",
                        "Estos son los nuevos datos de tu cita.", "Ver mis citas");
                case CANCELADA -> enviar(cita, "Tu cita fue cancelada", "Tu cita fue cancelada",
                        "Si todavía necesitas la consulta, puedes agendar un nuevo turno cuando quieras.", "Agendar otra cita");
            }
        });
    }

    // Cada hora: recordatorio de las citas de mañana que aún no lo recibieron.
    @Scheduled(cron = "0 0 * * * *", zone = "America/Bogota")
    public void enviarRecordatorios() {
        LocalDate manana = LocalDate.now().plusDays(1);
        int enviados = 0;
        for (Cita cita : citaRepo.findByFechaAndEstado(manana, EstadoCita.PENDIENTE)) {
            if (cita.isRecordatorioEnviado()) continue;
            enviar(cita, "Recordatorio: tu cita es mañana", "Mañana tienes cita",
                    "Si no puedes asistir, cancélala desde BeSalud para liberar el turno a otro paciente.", "Ver mis citas");
            cita.setRecordatorioEnviado(true);
            citaRepo.save(cita);
            enviados++;
        }
        if (enviados > 0) log.info("Recordatorios procesados para {}: {}", manana, enviados);
    }

    private void enviar(Cita cita, String asunto, String titulo, String mensaje, String textoBoton) {
        Paciente paciente = personaRepo.findById(cita.getPacienteId())
                .filter(p -> p instanceof Paciente).map(p -> (Paciente) p).orElse(null);
        if (paciente == null) return;
        Doctor doctor = personaRepo.findById(cita.getDoctorId())
                .filter(p -> p instanceof Doctor).map(p -> (Doctor) p).orElse(null);
        String html = plantilla(paciente, doctor, cita, titulo, mensaje, textoBoton);
        correo.enviar(paciente.getEmail(), paciente.getNombre() + " " + paciente.getApellido(), asunto + " · BeSalud", html);
    }

    String plantilla(Paciente paciente, Doctor doctor, Cita cita, String titulo, String mensaje, String textoBoton) {
        String fecha = cita.getFecha() == null ? "" : cita.getFecha().format(FECHA);
        String hora = cita.getHora() == null ? "" : cita.getHora().format(HORA).replace(' ', ' ').toLowerCase();
        String nombreDoctor = doctor == null ? "Por confirmar" : "Dr(a). " + doctor.getNombre() + " " + doctor.getApellido();
        String especialidad = doctor == null || doctor.getEspecialidad() == null ? "" : doctor.getEspecialidad();
        String cancelada = cita.getEstado() == EstadoCita.CANCELADA ? "text-decoration:line-through;color:#6B6B65;" : "";

        return """
                <!DOCTYPE html><html lang="es"><body style="margin:0;padding:0;background:#F5F2EC;font-family:Arial,Helvetica,sans-serif;color:#1C1C1A;">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#F5F2EC;padding:32px 16px;">
                <tr><td align="center">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:520px;background:#FDFCF9;border-radius:12px;overflow:hidden;border:1px solid #E5DFD3;">
                  <tr><td style="background:#0D3B2E;padding:22px 28px;color:#FDFCF9;font-size:22px;font-family:Georgia,serif;">
                    <span style="color:#C8A96E;">&#10022;</span> BeSalud</td></tr>
                  <tr><td style="padding:28px;">
                    <p style="margin:0 0 6px;font-size:13px;color:#6B6B65;">Hola, %s</p>
                    <h1 style="margin:0 0 12px;font-size:24px;font-family:Georgia,serif;color:#0D3B2E;font-weight:normal;">%s</h1>
                    <p style="margin:0 0 20px;font-size:15px;line-height:1.5;">%s</p>
                    <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#F5F2EC;border-radius:8px;border-left:4px solid #C8A96E;">
                      <tr><td style="padding:16px 18px;font-size:14px;line-height:1.7;%s">
                        <strong style="font-size:16px;color:#0D3B2E;">%s · %s</strong><br>
                        %s<br>
                        <span style="color:#6B6B65;">%s</span><br>
                        <span style="color:#6B6B65;">Motivo:</span> %s
                      </td></tr>
                    </table>
                    <p style="margin:24px 0 0;"><a href="%s" style="display:inline-block;background:#1A5C45;color:#FDFCF9;text-decoration:none;padding:12px 22px;border-radius:6px;font-size:14px;">%s</a></p>
                  </td></tr>
                  <tr><td style="padding:16px 28px;border-top:1px solid #E5DFD3;font-size:12px;color:#6B6B65;">
                    Este es un correo automático de BeSalud. Si es una emergencia, llama al 123.</td></tr>
                </table></td></tr></table></body></html>
                """.formatted(
                esc(paciente.getNombre()), esc(titulo), esc(mensaje), cancelada,
                esc(capitalizar(fecha)), esc(hora), esc(nombreDoctor), esc(especialidad),
                esc(cita.getMotivo() == null ? "" : cita.getMotivo()),
                urlApp + "/pacientes/landing", esc(textoBoton));
    }

    private static String esc(String s) {
        return HtmlUtils.htmlEscape(s == null ? "" : s, "UTF-8");
    }

    private static String capitalizar(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
