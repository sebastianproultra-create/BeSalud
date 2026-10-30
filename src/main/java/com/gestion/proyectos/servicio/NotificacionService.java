package com.gestion.proyectos.servicio;

import com.gestion.proyectos.util.Reloj;
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
import java.util.List;
import java.util.Locale;
import com.gestion.proyectos.util.Calendario;

@Service
public class NotificacionService {

    private static final Logger log = LoggerFactory.getLogger(NotificacionService.class);
    private static final Locale ES = Locale.forLanguageTag("es-CO");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", ES);
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("h:mm a", ES);
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM, h:mm a", ES);

    private final CitaRepositorio citaRepo;
    private final PersonaRepositorio personaRepo;
    private final CorreoService correo;
    private final WhatsappService whatsapp;
    private final String urlApp;

    public NotificacionService(CitaRepositorio citaRepo, PersonaRepositorio personaRepo, CorreoService correo,
            WhatsappService whatsapp, @Value("${app.url:http://localhost:8080}") String urlApp) {
        this.citaRepo = citaRepo;
        this.personaRepo = personaRepo;
        this.correo = correo;
        this.whatsapp = whatsapp;
        this.urlApp = urlApp.replaceAll("/+$", "");
    }

    @Async
    @EventListener
    public void alCambiarCita(CitaEvento evento) {
        citaRepo.findById(evento.citaId()).ifPresent(cita -> {
            switch (evento.tipo()) {
                case CREADA -> {
                    if (cita.getReferenciaPago() != null)
                        enviarComprobante(cita);
                    else
                        enviar(cita, "Tu cita quedó agendada", "Tu cita quedó agendada",
                                "Te esperamos. Recuerda llegar 10 minutos antes.", "Ver mis citas");
                }
                case REPROGRAMADA -> enviar(cita, "Tu cita fue reprogramada", "Cambiamos la fecha de tu cita",
                        "Estos son los nuevos datos de tu cita.", "Ver mis citas");
                case CANCELADA -> enviar(cita, "Tu cita fue cancelada", "Tu cita fue cancelada",
                        "Si todavía necesitas la consulta, puedes agendar un nuevo turno cuando quieras.", "Agendar otra cita");
                // Sin datos clínicos en el correo: la historia clínica es reservada y solo se consulta dentro de la app.
                case DICTAMEN -> enviar(cita, "Tu dictamen médico está disponible", "Tu dictamen médico está disponible",
                        "Tu médico registró el resultado de la consulta. Ingresa a BeSalud para verlo.", "Ver mi dictamen");
                case ABONO_DEVUELTO -> enviar(cita, cita.isPagadaCompleta() ? "Te devolvimos el pago" : "Te devolvimos el abono",
                        cita.isPagadaCompleta() ? "Te devolvimos el pago" : "Te devolvimos el abono",
                        "El profesional registró la devolución de " + (cita.isPagadaCompleta() ? "lo que pagaste" : "tu abono") + " ("
                                + pesos(cita.getPagadoPesos() == null ? 0 : cita.getPagadoPesos())
                                + "). Según tu banco, puede tardar unos días hábiles en verse reflejado.", "Ver mis citas");
                case SALDO_PAGADO -> enviar(cita, "Tu cita quedó pagada por completo", "Tu cita quedó pagada por completo",
                        "Recibimos el pago del saldo de " + pesos(cita.getSaldoPesos() == null ? 0 : cita.getSaldoPesos())
                                + " (referencia " + cita.getReferenciaSaldo() + "). Ya no tienes que pagar nada en el consultorio. "
                                + "Si cancelas a tiempo te devolvemos todo lo pagado.", "Ver mis citas");
                case CANCELADA_SIN_PAGO -> enviar(cita, "Cancelamos tu cita por falta de pago", "Cancelamos tu cita",
                        "No recibimos el pago del resto de la consulta antes del plazo (" + Cita.HORAS_LIMITE_SALDO
                                + " horas antes de la cita), así que el horario quedó libre. Como en una cancelación tardía, "
                                + "el abono no es reembolsable. Si todavía necesitas la consulta, puedes agendar de nuevo.",
                        "Agendar otra cita");
            }
        });
    }

    // Cada hora: recordatorio de las citas de mañana que aún no lo recibieron.
    @Scheduled(cron = "0 0 * * * *", zone = "America/Bogota")
    public void enviarRecordatorios() {
        LocalDate manana = Reloj.hoy().plusDays(1);
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

    // Cada hora: aviso a quien aún no paga el resto, cuando falta un día para el plazo.
    @Scheduled(cron = "0 10 * * * *", zone = "America/Bogota")
    public void recordarPagoSaldo() {
        var ahora = Reloj.ahora();
        int enviados = 0;
        for (Cita cita : citaRepo.findByEstadoAndSaldoObligatorioTrueAndSaldoCentavosIsNull(EstadoCita.PENDIENTE)) {
            var limite = cita.getLimitePagoSaldo();
            if (cita.isRecordatorioSaldoEnviado() || limite == null || !ahora.isBefore(limite)
                    || ahora.isBefore(limite.minusHours(24)))
                continue;
            Doctor doctor = personaRepo.findById(cita.getDoctorId())
                    .filter(p -> p instanceof Doctor).map(p -> (Doctor) p).orElse(null);
            String falta = doctor == null || cita.getAbonoPesos() == null ? "el resto de la consulta"
                    : pesos(Math.max(0, doctor.getPrecioEfectivo() - cita.getAbonoPesos()));
            enviar(cita, "Paga el resto de tu cita", "Falta pagar el resto de tu cita",
                    "Te falta pagar " + falta + ". Hazlo desde BeSalud antes del " + limite.format(FECHA_HORA)
                            + "; si no, cancelaremos la cita y el abono no será reembolsable.", "Pagar ahora");
            citaRepo.marcarRecordatorioSaldo(cita.getId());
            enviados++;
        }
        if (enviados > 0) log.info("Recordatorios de pago del resto: {}", enviados);
    }

    // Cada hora: se pregunta "¿Vas a ir?" a las citas que entraron en la ventana de confirmación.
    @Scheduled(cron = "0 5 * * * *", zone = "America/Bogota")
    public void pedirConfirmaciones() {
        var ahora = Reloj.ahora();
        int enviados = 0;
        // La ventana más larga es de 48 h de plazo + 24 h de margen: basta con revisar los próximos 4 días
        for (int d = 0; d <= 4; d++) {
            for (Cita cita : citaRepo.findByFechaAndEstado(ahora.toLocalDate().plusDays(d), EstadoCita.PENDIENTE)) {
                if (cita.isConfirmacionSolicitada()) continue;
                Doctor doctor = personaRepo.findById(cita.getDoctorId())
                        .filter(p -> p instanceof Doctor).map(p -> (Doctor) p).orElse(null);
                if (!CitaService.esperaConfirmacion(cita, doctor, ahora)) continue;
                enviar(cita, "¿Vas a ir a tu cita?", "¿Vas a ir a tu cita?", mensajeConfirmacion(cita, doctor, ahora),
                        "Responder en BeSalud", List.of(new WhatsappService.Boton("ASISTIRE:" + cita.getId(), "Sí, asistiré"),
                                new WhatsappService.Boton("NO_IRE:" + cita.getId(), "No podré ir")));
                citaRepo.marcarConfirmacionSolicitada(cita.getId());
                enviados++;
            }
        }
        if (enviados > 0) log.info("Solicitudes de confirmación de asistencia: {}", enviados);
    }

    static String mensajeConfirmacion(Cita cita, Doctor doctor, java.time.LocalDateTime ahora) {
        int horas = doctor == null ? 24 : doctor.horasCancelacionEfectivas();
        var limite = cita.getFecha().atTime(cita.getHora()).minusHours(horas);
        if (cita.getAbonoCentavos() == null || !ahora.isBefore(limite))
            return "Confírmanos si asistirás. Si no puedes ir, avísanos para liberar el turno a otro paciente.";
        return "Confírmanos si asistirás. Si no puedes ir y nos avisas antes del " + limite.format(FECHA_HORA)
                + ", cancelamos la cita y te devolvemos lo que pagaste.";
    }

    private void enviar(Cita cita, String asunto, String titulo, String mensaje, String textoBoton) {
        enviar(cita, asunto, titulo, mensaje, textoBoton, List.of());
    }

    private void enviar(Cita cita, String asunto, String titulo, String mensaje, String textoBoton,
            List<WhatsappService.Boton> botones) {
        Paciente paciente = personaRepo.findById(cita.getPacienteId())
                .filter(p -> p instanceof Paciente).map(p -> (Paciente) p).orElse(null);
        if (paciente == null) return;
        Doctor doctor = personaRepo.findById(cita.getDoctorId())
                .filter(p -> p instanceof Doctor).map(p -> (Doctor) p).orElse(null);
        String html = plantilla(paciente, doctor, cita, titulo, mensaje, textoBoton);
        correo.enviar(paciente.getEmail(), paciente.getNombre() + " " + paciente.getApellido(), asunto + " · BeSalud", html);
        enviarWhatsapp(paciente, doctor, cita, titulo, mensaje, textoBoton, List.of(), botones);
    }

    /** Cita pagada con abono: correo de confirmación con el comprobante y la cita para el calendario. */
    private void enviarComprobante(Cita cita) {
        Paciente paciente = personaRepo.findById(cita.getPacienteId())
                .filter(p -> p instanceof Paciente).map(p -> (Paciente) p).orElse(null);
        if (paciente == null) return;
        Doctor doctor = personaRepo.findById(cita.getDoctorId())
                .filter(p -> p instanceof Doctor).map(p -> (Doctor) p).orElse(null);
        String html = plantilla(paciente, doctor, cita, "¡Tu cita está confirmada!",
                (cita.isPagadaCompleta() ? "Recibimos el pago total de tu consulta." : "Recibimos el pago de tu abono.")
                        + " Guarda este correo como comprobante.", "Ver mis citas",
                bloqueComprobante(doctor, cita));
        correo.enviar(paciente.getEmail(), paciente.getNombre() + " " + paciente.getApellido(),
                "Cita confirmada · comprobante de pago · BeSalud", html);
        enviarWhatsapp(paciente, doctor, cita, "¡Tu cita está confirmada!",
                "Recibimos el pago de tu abono. Guarda este mensaje como comprobante.", "Ver mis citas",
                lineasComprobante(doctor, cita), List.of());
    }

    /** Mismo título, mensaje y datos de la cita que el correo, en texto para WhatsApp. */
    private void enviarWhatsapp(Paciente paciente, Doctor doctor, Cita cita, String titulo, String mensaje,
            String textoBoton, List<String> extra, List<WhatsappService.Boton> botones) {
        String fecha = cita.getFecha() == null ? "" : capitalizar(cita.getFecha().format(FECHA));
        String hora = cita.getHora() == null ? "" : cita.getHora().format(HORA).replace(' ', ' ').toLowerCase();
        String cuando = fecha + (hora.isEmpty() ? "" : " · " + hora);
        String conQuien = doctor == null ? "Por confirmar" : "Dr(a). " + doctor.getNombre() + " " + doctor.getApellido()
                + (doctor.getEspecialidad() == null ? "" : " (" + doctor.getEspecialidad() + ")");
        StringBuilder texto = new StringBuilder("*").append(titulo).append("*\n\nHola ").append(paciente.getNombre())
                .append(". ").append(mensaje).append("\n\n🗓️ ").append(cuando).append("\n👨‍⚕️ ").append(conQuien);
        if (cita.getMotivo() != null && !cita.getMotivo().isBlank())
            texto.append("\n📝 Motivo: ").append(cita.getMotivo());
        extra.forEach(l -> texto.append("\n").append(l));
        if (botones.isEmpty())
            texto.append("\n\n👉 ").append(textoBoton).append(": ").append(urlApp).append("/pacientes/landing");
        String mensajePlantilla = extra.isEmpty() ? mensaje : mensaje + " " + String.join(" · ", extra);
        whatsapp.enviar(paciente.getTelefono(), List.of(paciente.getNombre(), titulo, cuando, conQuien, mensajePlantilla),
                texto.toString(), botones);
    }

    /** Las mismas filas del comprobante del correo, en texto. */
    List<String> lineasComprobante(Doctor doctor, Cita cita) {
        long abono = cita.getAbonoPesos() == null ? 0 : cita.getAbonoPesos();
        var lineas = new java.util.ArrayList<String>();
        lineas.add("💳 Abono pagado: " + pesos(abono));
        if (doctor != null && doctor.getPrecioEfectivo() > abono)
            lineas.add("💵 Pagas en el consultorio: " + pesos(doctor.getPrecioEfectivo() - abono));
        if (doctor != null && doctor.getDireccionConsultorio() != null)
            lineas.add("📍 Lugar: " + doctor.getDireccionConsultorio());
        lineas.add("🧾 Referencia: " + cita.getReferenciaPago());
        if (doctor != null && cita.getFecha() != null && cita.getHora() != null)
            lineas.add("Si no puedes ir, cancela antes del " + cita.getFecha().atTime(cita.getHora())
                    .minusHours(doctor.horasCancelacionEfectivas()).format(FECHA_HORA) + " y te devolvemos el abono.");
        return lineas;
    }

    /** Filas del comprobante: abono, saldo, lugar, referencia y hasta cuándo se puede cancelar con devolución. */
    String bloqueComprobante(Doctor doctor, Cita cita) {
        long abono = cita.getAbonoPesos() == null ? 0 : cita.getAbonoPesos();
        StringBuilder filas = new StringBuilder();
        if (cita.isPagadaCompleta()) {
            filas.append(fila("Pagaste el total", pesos(cita.getPagadoPesos())));
        } else {
            filas.append(fila("Abono pagado", pesos(abono)));
            if (doctor != null && doctor.getPrecioEfectivo() > abono)
                filas.append(fila(cita.isSaldoObligatorio() && cita.getLimitePagoSaldo() != null
                        ? "Pagas antes del " + cita.getLimitePagoSaldo().format(FECHA_HORA) : "Pagas en el consultorio",
                        pesos(doctor.getPrecioEfectivo() - abono)));
        }
        if (doctor != null && doctor.getDireccionConsultorio() != null)
            filas.append(fila("Lugar", doctor.getDireccionConsultorio()));
        if (cita.getMedioPago() != null)
            filas.append(fila("Medio de pago", cita.getMedioPago()));
        filas.append(fila("Referencia", cita.getReferenciaPago()));
        String politica = "";
        if (doctor != null && cita.getFecha() != null && cita.getHora() != null) {
            var limite = cita.getFecha().atTime(cita.getHora()).minusHours(doctor.horasCancelacionEfectivas());
            politica = "<p style=\"margin:16px 0 0;font-size:13px;line-height:1.6;color:#5F6662;\">Si no puedes ir, cancela antes del "
                    + "<strong style=\"color:#171B19;\">" + esc(limite.format(FECHA_HORA)) + "</strong> y te devolvemos " + (cita.isPagadaCompleta() ? "todo lo pagado" : "el abono") + ". "
                    + "Después de esa hora, el profesional lo conserva.</p>";
            String google = Calendario.enlaceGoogle(cita.getFecha().atTime(cita.getHora()), 30,
                    "Cita con Dr(a). " + doctor.getNombre() + " " + doctor.getApellido(),
                    doctor.getDireccionConsultorio(), "Referencia del abono: " + cita.getReferenciaPago());
            politica += "<p style=\"margin:12px 0 0;font-size:13px;line-height:1.6;color:#5F6662;\">"
                    + "<a href=\"" + esc(google) + "\" style=\"color:#0D4738;font-weight:700;\">Agregar a Google Calendar</a></p>";
        }
        return """
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="margin-top:20px;border:1px dashed #D5DED8;border-radius:3px;">
                  <tr><td style="padding:16px 22px 6px;">
                    <div style="font-size:10px;font-weight:600;letter-spacing:0.18em;text-transform:uppercase;color:#969D98;">Comprobante de pago</div>
                  </td></tr>
                  %s
                </table>%s
                """.formatted(filas, politica);
    }

    private static String fila(String etiqueta, String valor) {
        return "<tr><td style=\"padding:8px 22px;border-top:1px solid #EAEFEA;font-size:14px;\">"
                + "<span style=\"color:#5F6662;\">" + esc(etiqueta) + "</span>"
                + "<span style=\"float:right;color:#171B19;font-weight:600;\">" + esc(valor) + "</span></td></tr>";
    }

    private static String pesos(long valor) {
        return "$" + String.format(Locale.ROOT, "%,d", valor).replace(',', '.');
    }

    String plantilla(Paciente paciente, Doctor doctor, Cita cita, String titulo, String mensaje, String textoBoton) {
        return plantilla(paciente, doctor, cita, titulo, mensaje, textoBoton, "");
    }

    /** bloqueExtra: HTML ya escapado que va debajo de los datos de la cita (por ejemplo, el comprobante). */
    String plantilla(Paciente paciente, Doctor doctor, Cita cita, String titulo, String mensaje, String textoBoton,
            String bloqueExtra) {
        String fecha = cita.getFecha() == null ? "" : cita.getFecha().format(FECHA);
        String hora = cita.getHora() == null ? "" : cita.getHora().format(HORA).replace(' ', ' ').toLowerCase();
        String nombreDoctor = doctor == null ? "Por confirmar" : "Dr(a). " + doctor.getNombre() + " " + doctor.getApellido();
        String especialidad = doctor == null || doctor.getEspecialidad() == null ? "" : doctor.getEspecialidad();
        String tachado = cita.getEstado() == EstadoCita.CANCELADA ? "text-decoration:line-through;" : "";

        // Mismo lenguaje visual que la portada: verde bosque, acento ámbar, Newsreader + Plus Jakarta Sans
        // (con Georgia/Arial de respaldo porque Gmail ignora las fuentes web).
        return """
                <!DOCTYPE html><html lang="es"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
                <link href="https://fonts.googleapis.com/css2?family=Newsreader:opsz,wght@6..72,400;6..72,500&amp;family=Plus+Jakarta+Sans:wght@400;600&amp;display=swap" rel="stylesheet"></head>
                <body style="margin:0;padding:0;background:#F4F7F4;font-family:'Plus Jakarta Sans',Arial,Helvetica,sans-serif;color:#525956;">
                <div style="display:none;max-height:0;overflow:hidden;opacity:0;">%s</div>
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" bgcolor="#F4F7F4" style="background:#F4F7F4;padding:32px 12px;">
                <tr><td align="center">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:560px;background:#FAFBF9;border:1px solid #EAEFEA;border-radius:4px;overflow:hidden;">
                  <tr><td bgcolor="#082921" style="background:#082921;background-image:linear-gradient(160deg,#0D4738 0%%,#082921 100%%);padding:36px 36px 32px;">
                    <div style="font-family:'Newsreader',Georgia,serif;font-size:24px;font-weight:500;color:#FFFFFF;">
                      <span style="display:inline-block;width:26px;height:26px;line-height:26px;text-align:center;background:rgba(255,255,255,0.1);border-radius:2px;color:#A9D5C3;font-family:Arial,sans-serif;font-size:20px;">+</span>&nbsp;BeSalud</div>
                    <div style="margin:28px 0 14px;"><span style="display:inline-block;padding:6px 14px;border:1px solid rgba(169,213,195,0.3);border-radius:3px;background:rgba(13,71,56,0.4);color:#C4E5D7;font-size:10px;font-weight:600;letter-spacing:0.25em;text-transform:uppercase;">Hola, %s</span></div>
                    <h1 style="margin:0;font-family:'Newsreader',Georgia,serif;font-size:32px;line-height:1.15;font-weight:400;color:#FFFFFF;">%s</h1>
                  </td></tr>
                  <tr><td style="padding:32px 36px 8px;">
                    <p style="margin:0 0 24px;font-size:15px;line-height:1.7;color:#525956;">%s</p>
                    <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#F4F7F4;border:1px solid #EAEFEA;border-left:3px solid #D99B4B;border-radius:3px;">
                      <tr><td style="padding:20px 22px;%s">
                        <div style="font-size:10px;font-weight:600;letter-spacing:0.18em;text-transform:uppercase;color:#969D98;margin-bottom:6px;">Tu cita</div>
                        <div style="font-family:'Newsreader',Georgia,serif;font-size:20px;color:#0D4738;margin-bottom:10px;">%s · %s</div>
                        <div style="font-size:14px;line-height:1.7;color:#171B19;">%s</div>
                        <div style="font-size:13px;line-height:1.7;color:#5F6662;">%s</div>
                        <div style="font-size:13px;line-height:1.7;color:#5F6662;margin-top:6px;"><span style="color:#969D98;">Motivo:</span> %s</div>
                      </td></tr>
                    </table>%s
                  </td></tr>
                  <tr><td style="padding:24px 36px 36px;">
                    <a href="%s" style="display:inline-block;background:#D99B4B;border:1px solid #D99B4B;color:#171B19;text-decoration:none;padding:14px 28px;border-radius:3px;font-size:11px;font-weight:600;letter-spacing:0.14em;text-transform:uppercase;">%s</a>
                  </td></tr>
                  <tr><td bgcolor="#0D4738" style="background:#0D4738;padding:22px 36px;">
                    <div style="font-family:'Newsreader',Georgia,serif;font-size:18px;color:#FFFFFF;"><span style="color:#69B493;font-size:22px;">+</span> BeSalud</div>
                    <div style="margin-top:6px;font-size:12px;line-height:1.6;color:rgba(196,229,215,0.7);">Correo automático, no lo respondas. Si es una emergencia, llama al 123.</div>
                  </td></tr>
                </table></td></tr></table></body></html>
                """.formatted(
                esc(mensaje), esc(paciente.getNombre()), esc(titulo), esc(mensaje), tachado,
                esc(capitalizar(fecha)), esc(hora), esc(nombreDoctor), esc(especialidad),
                esc(cita.getMotivo() == null ? "" : cita.getMotivo()), bloqueExtra,
                urlApp + "/pacientes/landing", esc(textoBoton));
    }

    private static String esc(String s) {
        return HtmlUtils.htmlEscape(s == null ? "" : s, "UTF-8");
    }

    private static String capitalizar(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
