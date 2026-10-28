package com.gestion.proyectos.servicio;

import com.gestion.proyectos.util.Reloj;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import com.gestion.proyectos.modelo.MensajeAyuda;
import com.gestion.proyectos.repositorio.AdminRepositorio;
import com.gestion.proyectos.repositorio.MensajeAyudaRepositorio;
import com.gestion.proyectos.util.ValidacionUtil;

/** Formulario de contacto del Centro de ayuda: valida, guarda y avisa a los administradores. */
@Service
public class AyudaService {

    private static final Logger log = LoggerFactory.getLogger(AyudaService.class);

    /** Temas del formulario (clave enviada → texto visible). */
    public static final Map<String, String> TEMAS = new LinkedHashMap<>();
    static {
        TEMAS.put("registro", "Registro o inicio de sesión");
        TEMAS.put("citas", "Agendar, reprogramar o cancelar citas");
        TEMAS.put("medicos", "Cuentas de médicos y especialistas");
        TEMAS.put("datos", "Privacidad y datos personales");
        TEMAS.put("instituciones", "Instituciones y clínicas asociadas");
        TEMAS.put("otro", "Otro tema");
    }

    private final MensajeAyudaRepositorio mensajeRepo;
    private final AdminRepositorio adminRepo;
    private final CorreoService correo;

    public AyudaService(MensajeAyudaRepositorio mensajeRepo, AdminRepositorio adminRepo, CorreoService correo) {
        this.mensajeRepo = mensajeRepo;
        this.adminRepo = adminRepo;
        this.correo = correo;
    }

    /** Normaliza los campos del mensaje y devuelve el error, o null si es válido. */
    public String validar(MensajeAyuda m) {
        m.setNombre(ValidacionUtil.limpiarEspacios(m.getNombre()));
        m.setEmail(m.getEmail() == null ? null : m.getEmail().trim().toLowerCase());
        m.setMensaje(m.getMensaje() == null ? null : m.getMensaje().trim());

        if (ValidacionUtil.esVacio(m.getNombre()) || m.getNombre().length() < 2)
            return "Escribe tu nombre.";
        if (m.getNombre().length() > 80)
            return "El nombre no puede superar los 80 caracteres.";
        String errorEmail = ValidacionUtil.errorEmail(m.getEmail());
        if (errorEmail != null)
            return errorEmail + ".";
        if (!TEMAS.containsKey(m.getTema()))
            return "Selecciona el tema de tu consulta.";
        if (ValidacionUtil.esVacio(m.getMensaje()) || m.getMensaje().length() < 10)
            return "Cuéntanos tu duda en al menos 10 caracteres.";
        if (m.getMensaje().length() > 2000)
            return "El mensaje no puede superar los 2000 caracteres.";
        return null;
    }

    public MensajeAyuda guardar(MensajeAyuda m) {
        m.setFecha(Reloj.ahora());
        m.setAtendido(false);
        log.info("Mensaje de ayuda recibido ({})", m.getTema());
        return mensajeRepo.save(m);
    }

    /**
     * Correo a cada administrador (en segundo plano: lo llama el controlador para que
     * pase por el proxy de @Async). Si el correo no está configurado, queda solo en el panel.
     */
    @Async
    public void avisarAdministradores(MensajeAyuda m) {
        String html = "<p><strong>" + HtmlUtils.htmlEscape(m.getNombre()) + "</strong> ("
                + HtmlUtils.htmlEscape(m.getEmail()) + ") escribió sobre <em>"
                + HtmlUtils.htmlEscape(TEMAS.get(m.getTema())) + "</em>:</p><p>"
                + HtmlUtils.htmlEscape(m.getMensaje()).replace("\n", "<br>")
                + "</p><p>Respóndele por correo y márcalo como atendido en el panel de administración.</p>";
        adminRepo.findAll().forEach(a -> correo.enviar(a.getEmail(), a.getNombre(),
                "BeSalud · Nuevo mensaje del Centro de ayuda", html));
    }

    public List<MensajeAyuda> pendientes() {
        return mensajeRepo.findByAtendidoFalseOrderByFechaDesc();
    }

    public void marcarAtendido(String id) {
        mensajeRepo.findById(id).ifPresent(m -> {
            m.setAtendido(true);
            mensajeRepo.save(m);
        });
    }
}
