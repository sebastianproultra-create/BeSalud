package com.gestion.proyectos.servicio;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.gestion.proyectos.modelo.Doctor;
import com.gestion.proyectos.modelo.SitioWeb;
import com.gestion.proyectos.repositorio.ConsultorioRepositorio;
import com.gestion.proyectos.repositorio.PersonaRepositorio;
import com.gestion.proyectos.repositorio.SitioWebRepositorio;

/**
 * Página web alojada del médico: la arma el equipo de BeSalud desde el panel de admin (el médico no la edita).
 * Borrador con sus datos de perfil, edición, publicación en /sitio/{slug} y lectura pública. Al publicar se
 * asegura su consultorio en la API para que el widget agende ahí mismo.
 */
@ConditionalOnProperty(name = "app.api-consultorios.habilitada", havingValue = "true")
@Service
public class SitioWebService {

    private static final Logger log = LoggerFactory.getLogger(SitioWebService.class);

    /** Lo que el médico escribe en el editor. Los servicios van uno por línea. */
    public record Formulario(String plantilla, String color, String titulo, String frase, String presentacion,
            String servicios, String whatsapp, String direccion, String ciudad) {
    }

    /** Un sitio publicado listo para pintar: con su médico y los enlaces de contacto ya armados. */
    public record Pagina(SitioWeb sitio, Doctor doctor, String whatsappUrl, String mapaUrl) {
    }

    private final SitioWebRepositorio sitioRepo;
    private final PersonaRepositorio personaRepo;
    private final ConsultorioRepositorio consultorioRepo;
    private final ConsultorioService consultorioService;

    public SitioWebService(SitioWebRepositorio sitioRepo, PersonaRepositorio personaRepo,
            ConsultorioRepositorio consultorioRepo, ConsultorioService consultorioService) {
        this.sitioRepo = sitioRepo;
        this.personaRepo = personaRepo;
        this.consultorioRepo = consultorioRepo;
        this.consultorioService = consultorioService;
    }

    /** Sitio de cada médico (para la lista del admin), indexado por doctorId. */
    public Map<String, SitioWeb> porDoctor() {
        Map<String, SitioWeb> mapa = new LinkedHashMap<>();
        sitioRepo.findAll().forEach(s -> mapa.put(s.getDoctorId(), s));
        return mapa;
    }

    public Optional<SitioWeb> deDoctor(String doctorId) {
        return doctorId == null ? Optional.empty() : sitioRepo.findByDoctorId(doctorId);
    }

    /** El sitio guardado del médico o, si aún no tiene, uno nuevo (sin guardar) lleno con su perfil. */
    public SitioWeb borrador(Doctor doctor) {
        return deDoctor(doctor.getId()).orElseGet(() -> nuevo(doctor));
    }

    /** Vista previa con lo que el médico lleva escrito, sin guardar ni validar (los textos se recortan). */
    public SitioWeb vistaPrevia(Doctor doctor, Formulario form) {
        SitioWeb sitio = copia(borrador(doctor));
        aplicar(sitio, form);
        return sitio;
    }

    /** Errores por campo; vacío si el formulario se puede guardar. */
    public Map<String, String> validar(Formulario form) {
        Map<String, String> errores = new LinkedHashMap<>();
        int titulo = largo(form.titulo());
        if (titulo < 3 || titulo > 80) errores.put("titulo", "Escribe el nombre de tu consultorio (de 3 a 80 caracteres).");
        if (largo(form.frase()) > 140) errores.put("frase", "La frase de bienvenida admite hasta 140 caracteres.");
        if (largo(form.presentacion()) > 1200) errores.put("presentacion", "La presentación admite hasta 1200 caracteres.");
        List<String> servicios = lineas(form.servicios());
        if (servicios.size() > SitioWeb.MAX_SERVICIOS)
            errores.put("servicios", "Puedes mostrar hasta " + SitioWeb.MAX_SERVICIOS + " servicios.");
        else if (servicios.stream().anyMatch(s -> s.length() > 60))
            errores.put("servicios", "Cada servicio admite hasta 60 caracteres.");
        String whatsapp = soloDigitos(form.whatsapp());
        if (!whatsapp.isEmpty() && (whatsapp.length() < 10 || whatsapp.length() > 15))
            errores.put("whatsapp", "Escribe el número de WhatsApp con 10 dígitos, o con indicativo del país.");
        if (largo(form.direccion()) > 140) errores.put("direccion", "La dirección admite hasta 140 caracteres.");
        if (largo(form.ciudad()) > 60) errores.put("ciudad", "La ciudad admite hasta 60 caracteres.");
        return errores;
    }

    /** Guarda el sitio (publicado o no, sigue igual). Llamar después de validar. */
    public SitioWeb guardar(Doctor doctor, Formulario form) {
        SitioWeb sitio = borrador(doctor);
        aplicar(sitio, form);
        sitio.setActualizado(Instant.now());
        return sitioRepo.save(sitio);
    }

    /**
     * Publica el sitio guardado. Solo un médico activo puede publicar: el widget no agenda con médicos inactivos.
     * El origen es el de BeSalud tal como lo ve el navegador; se autoriza en el consultorio del médico.
     */
    public SitioWeb publicar(Doctor doctor, String origenApp) {
        SitioWeb sitio = deDoctor(doctor.getId())
                .orElseThrow(() -> new IllegalStateException("Guarda tu página antes de publicarla."));
        if (!"ACTIVO".equals(doctor.getEstado()))
            throw new IllegalStateException("Tu cuenta aún no está activa. Podrás publicar cuando el administrador la active.");
        var consultorio = consultorioService.alojar(doctor, origenApp, sitio.getTitulo());
        sitio.setConsultorioId(consultorio.getId());
        sitio.setPublicado(true);
        sitio.setActualizado(Instant.now());
        log.info("Sitio {} publicado por el doctor {}", sitio.getSlug(), doctor.getId());
        return sitioRepo.save(sitio);
    }

    public void despublicar(Doctor doctor) {
        deDoctor(doctor.getId()).ifPresent(sitio -> {
            sitio.setPublicado(false);
            sitio.setActualizado(Instant.now());
            sitioRepo.save(sitio);
        });
    }

    /** Sitio público: publicado, con su médico activo y su consultorio activo. Si falta algo, no existe. */
    public Optional<Pagina> publica(String slug) {
        return sitioRepo.findBySlug(slug)
                .filter(SitioWeb::isPublicado)
                .filter(s -> s.getConsultorioId() != null && consultorioRepo.findByIdAndActivoTrue(s.getConsultorioId())
                        .filter(c -> c.getDoctorIds().contains(s.getDoctorId())).isPresent())
                .flatMap(s -> personaRepo.findById(s.getDoctorId())
                        .filter(Doctor.class::isInstance).map(Doctor.class::cast)
                        .filter(d -> "ACTIVO".equals(d.getEstado()))
                        .map(d -> pagina(s, d)));
    }

    /** Sitio con su médico para la vista previa del editor (aunque no esté publicado). */
    public Pagina pagina(SitioWeb sitio, Doctor doctor) {
        return new Pagina(sitio, doctor, whatsappUrl(sitio.getWhatsapp()), mapaUrl(sitio.getDireccion(), sitio.getCiudad()));
    }

    // ── Internos ──────────────────────────────────────────────────────────

    private SitioWeb nuevo(Doctor doctor) {
        SitioWeb sitio = new SitioWeb();
        sitio.setDoctorId(doctor.getId());
        sitio.setSlug(slugLibre(doctor));
        String nombre = "Dr. " + doctor.getNombre() + " " + doctor.getApellido();
        String especialidad = vacioANull(doctor.getEspecialidad());
        sitio.setTitulo("Consultorio " + nombre);
        sitio.setFrase(especialidad != null
                ? "Atención en " + especialidad.toLowerCase() + " cercana, puntual y sin afanes."
                : "Atención médica cercana, puntual y sin afanes.");
        sitio.setPresentacion(vacioANull(doctor.getBiografia()) != null ? doctor.getBiografia().trim()
                : "Soy " + nombre + (especialidad != null ? ", especialista en " + especialidad.toLowerCase() : "")
                        + ". Te escucho con calma y te explico cada paso de tu tratamiento.");
        sitio.setServicios(new ArrayList<>(List.of(
                especialidad != null ? "Consulta de " + especialidad.toLowerCase() : "Consulta general",
                "Control y seguimiento", "Lectura de exámenes")));
        sitio.setWhatsapp(soloDigitos(doctor.getTelefono()));
        sitio.setDireccion(vacioANull(doctor.getDireccionConsultorio()));
        return sitio;
    }

    /** Aplica el formulario con los textos recortados; plantilla y color desconocidos vuelven al valor por defecto. */
    private static void aplicar(SitioWeb sitio, Formulario form) {
        String plantilla = SitioWeb.PLANTILLAS.containsKey(form.plantilla()) ? form.plantilla() : SitioWeb.PLANTILLA_DEFECTO;
        sitio.setPlantilla(plantilla);
        sitio.setColor(SitioWeb.COLORES.containsKey(form.color()) ? form.color() : SitioWeb.COLOR_POR_PLANTILLA.get(plantilla));
        sitio.setTitulo(recortar(form.titulo(), 80));
        sitio.setFrase(recortar(form.frase(), 140));
        sitio.setPresentacion(recortar(form.presentacion(), 1200));
        sitio.setServicios(new ArrayList<>(lineas(form.servicios()).stream()
                .limit(SitioWeb.MAX_SERVICIOS).map(s -> recortar(s, 60)).toList()));
        sitio.setWhatsapp(recortar(soloDigitos(form.whatsapp()), 15));
        sitio.setDireccion(recortar(form.direccion(), 140));
        sitio.setCiudad(recortar(form.ciudad(), 60));
    }

    private String slugLibre(Doctor doctor) {
        String base = vacioANull(doctor.getSlug()) != null ? doctor.getSlug()
                : ConsultorioService.slug("dr " + doctor.getNombre() + " " + doctor.getApellido());
        if (base.isEmpty()) base = "consultorio";
        String slug = base;
        for (int n = 2; sitioRepo.existsBySlug(slug); n++) slug = base + "-" + n;
        return slug;
    }

    private static SitioWeb copia(SitioWeb original) {
        SitioWeb s = new SitioWeb();
        s.setId(original.getId());
        s.setSlug(original.getSlug());
        s.setDoctorId(original.getDoctorId());
        s.setPublicado(original.isPublicado());
        s.setConsultorioId(original.getConsultorioId());
        return s;
    }

    /** wa.me con el número completo: un celular colombiano de 10 dígitos recibe el indicativo 57. */
    static String whatsappUrl(String numero) {
        String digitos = soloDigitos(numero);
        if (digitos.length() < 10) return null;
        if (digitos.length() == 10 && digitos.startsWith("3")) digitos = "57" + digitos;
        return "https://wa.me/" + digitos + "?text=" + URLEncoder.encode("Hola, quiero información sobre una cita.", StandardCharsets.UTF_8);
    }

    static String mapaUrl(String direccion, String ciudad) {
        if (vacioANull(direccion) == null) return null;
        String consulta = vacioANull(ciudad) != null ? direccion + ", " + ciudad : direccion;
        return "https://www.google.com/maps/search/?api=1&query=" + URLEncoder.encode(consulta, StandardCharsets.UTF_8);
    }

    private static List<String> lineas(String texto) {
        if (texto == null) return List.of();
        return Arrays.stream(texto.split("\\R")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private static String soloDigitos(String texto) {
        return texto == null ? "" : texto.replaceAll("\\D", "");
    }

    private static int largo(String texto) {
        return texto == null ? 0 : texto.trim().length();
    }

    private static String recortar(String texto, int max) {
        String limpio = vacioANull(texto);
        if (limpio == null) return null;
        return limpio.length() > max ? limpio.substring(0, max).trim() : limpio;
    }

    private static String vacioANull(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
