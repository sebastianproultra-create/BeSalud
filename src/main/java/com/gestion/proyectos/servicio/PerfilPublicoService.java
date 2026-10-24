package com.gestion.proyectos.servicio;

import static com.gestion.proyectos.util.ValidacionUtil.esVacio;

import com.gestion.proyectos.modelo.Doctor;
import com.gestion.proyectos.repositorio.PersonaRepositorio;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

/** Página propia de cada profesional (/p/{slug}) y su configuración de consulta y abono. */
@Service
public class PerfilPublicoService {

    private static final Logger log = LoggerFactory.getLogger(PerfilPublicoService.class);
    public static final int MAX_PRECIO = 5_000_000;
    public static final int MIN_PRECIO = 5_000;
    static final int MAX_SLUG = 60;

    private final PersonaRepositorio personaRepo;
    private final MongoTemplate mongo;

    public PerfilPublicoService(PersonaRepositorio personaRepo, MongoTemplate mongo) {
        this.personaRepo = personaRepo;
        this.mongo = mongo;
    }

    /** Médicos creados antes de existir las páginas propias reciben su enlace al arrancar. */
    @EventListener(ApplicationReadyEvent.class)
    public void asignarEnlacesFaltantes() {
        int asignados = 0;
        for (Doctor d : mongo.find(Query.query(Criteria.where("role").is("DOCTOR").and("slug").exists(false)), Doctor.class)) {
            asegurarSlug(d);
            asignados++;
        }
        if (asignados > 0)
            log.info("Enlaces de reserva asignados a {} profesionales", asignados);
    }

    /** Devuelve el slug del médico, creándolo (único) si todavía no tiene. */
    public String asegurarSlug(Doctor doctor) {
        if (!esVacio(doctor.getSlug()))
            return doctor.getSlug();
        String base = slugDe(doctor.getNombre() + " " + doctor.getApellido());
        if (base.isEmpty())
            base = "profesional";
        String candidato = base;
        for (int n = 2; existeSlug(candidato, doctor.getId()); n++)
            candidato = base + "-" + n;
        doctor.setSlug(candidato);
        personaRepo.save(doctor);
        return candidato;
    }

    private boolean existeSlug(String slug, String exceptoId) {
        Criteria c = Criteria.where("slug").is(slug);
        if (exceptoId != null)
            c = c.and("_id").ne(exceptoId);
        return mongo.exists(Query.query(c), Doctor.class);
    }

    /** "María José Peña" -> "maria-jose-pena". */
    public static String slugDe(String texto) {
        if (texto == null)
            return "";
        String sinTildes = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String slug = sinTildes.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "")
                .replaceAll("-{2,}", "-");
        // Corto para compartir y dentro del límite que acepta buscarActivoPorSlug (contando el sufijo -N)
        return slug.length() <= MAX_SLUG ? slug : slug.substring(0, MAX_SLUG).replaceAll("-+$", "");
    }

    /** Médicos activos, para el asistente del portal del paciente. */
    public java.util.List<Doctor> activos() {
        return mongo.find(Query.query(Criteria.where("role").is("DOCTOR").and("estado").is("ACTIVO")), Doctor.class);
    }

    /** Solo médicos activos tienen página pública. */
    public Optional<Doctor> buscarActivoPorSlug(String slug) {
        if (esVacio(slug) || !slug.matches("[a-z0-9-]{1,80}"))
            return Optional.empty();
        Doctor d = mongo.findOne(Query.query(Criteria.where("slug").is(slug).and("role").is("DOCTOR")), Doctor.class);
        return Optional.ofNullable(d).filter(x -> "ACTIVO".equals(x.getEstado()));
    }

    /**
     * Guarda precio, abono, dirección y política de cancelación. Devuelve el mensaje de error o null.
     */
    public String guardarConsulta(Doctor doctor, Integer precio, Integer porcentaje, String direccion, Integer horasCancelacion) {
        if (precio == null)
            return "Escribe el precio de la consulta: sin él tus pacientes no pueden reservar en línea.";
        if (precio < MIN_PRECIO || precio > MAX_PRECIO)
            return "El precio de la consulta debe estar entre " + AsistenteService.pesos(MIN_PRECIO) + " y "
                    + AsistenteService.pesos(MAX_PRECIO) + ".";
        if (porcentaje == null || !Doctor.PORCENTAJES_ABONO.contains(porcentaje))
            return "Elige el porcentaje del abono.";
        if (horasCancelacion != null && (horasCancelacion < 0 || horasCancelacion > 168))
            return "La anticipación para cancelar debe estar entre 0 y 168 horas.";
        String dir = direccion == null ? null : direccion.trim().replaceAll("\\s+", " ");
        if (dir != null && dir.length() > 160)
            return "La dirección no puede superar los 160 caracteres.";
        doctor.setPrecioConsulta(precio);
        doctor.setPorcentajeAbono(porcentaje);
        doctor.setDireccionConsultorio(esVacio(dir) ? null : dir);
        doctor.setHorasCancelacion(horasCancelacion);
        personaRepo.save(doctor);
        log.info("Consulta y abono actualizados para el doctor {}", doctor.getId());
        return null;
    }
}
