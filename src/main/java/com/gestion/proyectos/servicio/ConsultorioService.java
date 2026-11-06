package com.gestion.proyectos.servicio;

import java.net.URI;
import java.text.Normalizer;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.gestion.proyectos.modelo.Consultorio;
import com.gestion.proyectos.modelo.Doctor;
import com.gestion.proyectos.repositorio.ConsultorioRepositorio;
import com.gestion.proyectos.repositorio.PersonaRepositorio;

/**
 * Alta de consultorios (tenants) desde el panel de admin: al activar un médico se registra la URL de su
 * sitio web y queda autorizada para usar la API (CORS + validación de tenant) sin tocar Mongo a mano.
 */
@Service
public class ConsultorioService {

    private static final Logger log = LoggerFactory.getLogger(ConsultorioService.class);

    /** Lo que ve el médico en "Mi página web": su consultorio, sus dominios y cuántos pacientes llegaron por ahí. */
    public record Integracion(Consultorio consultorio, List<String> dominios, long pacientesWeb) {
    }

    private final ConsultorioRepositorio consultorioRepo;
    private final PersonaRepositorio personaRepo;

    public ConsultorioService(ConsultorioRepositorio consultorioRepo, PersonaRepositorio personaRepo) {
        this.consultorioRepo = consultorioRepo;
        this.personaRepo = personaRepo;
    }

    /**
     * Integración web del médico, o vacío si aún no tiene sitio vinculado. Los dominios de desarrollo
     * 127.0.0.1 se ocultan porque son el mismo sitio que su versión con localhost.
     */
    public Optional<Integracion> integracionDe(String doctorId) {
        if (doctorId == null) return Optional.empty();
        return consultorioRepo.buscarPorDoctor(doctorId).stream().findFirst()
                .map(c -> new Integracion(c,
                        c.getDominios().stream().filter(d -> !d.contains("://127.0.0.1")).toList(),
                        personaRepo.contarPacientesDeConsultorio(c.getId())));
    }

    /**
     * Autoriza el sitio web para el doctor. Si el dominio ya es de un consultorio, suma el doctor a ese
     * consultorio (clínica con varios médicos); si el doctor ya tiene consultorio, le suma el dominio;
     * si no, crea uno nuevo. Devuelve el consultorio guardado, o vacío si la URL no es válida.
     */
    public Optional<Consultorio> vincularSitio(Doctor doctor, String url) {
        String origen = normalizarOrigen(url);
        if (origen == null) return Optional.empty();

        Consultorio consultorio = consultorioRepo.buscarPorDominio(origen).stream().findFirst()
                .or(() -> consultorioRepo.buscarPorDoctor(doctor.getId()).stream().findFirst())
                .orElseGet(() -> nuevo(doctor));

        autorizar(consultorio, origen);
        agregar(consultorio.getDoctorIds(), doctor.getId());
        consultorio.setActivo(true);

        Consultorio guardado = consultorioRepo.save(consultorio);
        log.info("Sitio {} vinculado al doctor {} (consultorio {})", origen, doctor.getId(), guardado.getId());
        return Optional.of(guardado);
    }

    /**
     * Consultorio para la página que BeSalud le aloja al médico (/sitio/{slug}): usa el que ya tenga (así sus
     * pacientes son los mismos en su web propia y en la alojada) o crea uno, y autoriza el origen de BeSalud.
     * No busca por dominio como vincularSitio: el origen de BeSalud lo comparten todos los sitios alojados.
     * Si el admin lo había pausado, sigue pausado.
     */
    public Consultorio alojar(Doctor doctor, String origenApp, String nombre) {
        String origen = normalizarOrigen(origenApp);
        if (origen == null) throw new IllegalArgumentException("Origen de BeSalud inválido: " + origenApp);
        Consultorio consultorio = consultorioRepo.buscarPorDoctor(doctor.getId()).stream().findFirst()
                .orElseGet(() -> {
                    Consultorio c = nuevo(doctor);
                    if (nombre != null && !nombre.isBlank()) c.setNombre(nombre.trim());
                    return c;
                });
        autorizar(consultorio, origen);
        agregar(consultorio.getDoctorIds(), doctor.getId());
        Consultorio guardado = consultorioRepo.save(consultorio);
        log.info("Página alojada del doctor {} usa el consultorio {}", doctor.getId(), guardado.getId());
        return guardado;
    }

    /** Consultorio de cada doctor de la lista, para mostrarlo en el panel. */
    public Map<String, Consultorio> porDoctor(Collection<String> doctorIds) {
        Map<String, Consultorio> mapa = new HashMap<>();
        if (doctorIds.isEmpty()) return mapa;
        for (Consultorio c : consultorioRepo.buscarPorDoctores(doctorIds))
            for (String id : c.getDoctorIds())
                if (doctorIds.contains(id)) mapa.putIfAbsent(id, c);
        return mapa;
    }

    /**
     * "https://Consultorio.com/agenda?x=1" -> "https://consultorio.com". Es el formato exacto del header
     * Origin que manda el navegador, que es contra lo que se compara. null si no es http(s) con host.
     */
    public static String normalizarOrigen(String url) {
        if (url == null || url.isBlank()) return null;
        String limpia = url.trim();
        if (!limpia.matches("(?i)^https?://.*")) limpia = "https://" + limpia;
        try {
            URI uri = URI.create(limpia);
            String esquema = uri.getScheme().toLowerCase();
            String host = uri.getHost();
            if (host == null || host.isBlank() || !host.matches("[A-Za-z0-9.-]+")) return null;
            if (!host.contains(".") && !"localhost".equalsIgnoreCase(host)) return null;
            int puerto = uri.getPort();
            boolean puertoPorDefecto = puerto == -1 || ("https".equals(esquema) && puerto == 443)
                    || ("http".equals(esquema) && puerto == 80);
            return esquema + "://" + host.toLowerCase() + (puertoPorDefecto ? "" : ":" + puerto);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Consultorio nuevo(Doctor doctor) {
        String base = slug("dr " + doctor.getNombre() + " " + doctor.getApellido());
        String id = base;
        for (int n = 2; consultorioRepo.existsById(id); n++) id = base + "-" + n;
        Consultorio c = new Consultorio();
        c.setId(id);
        c.setNombre("Consultorio Dr. " + doctor.getNombre() + " " + doctor.getApellido());
        return c;
    }

    public static String slug(String texto) {
        String sinTildes = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    private static void autorizar(Consultorio consultorio, String origen) {
        agregar(consultorio.getDominios(), origen);
        // En desarrollo el navegador puede llegar como localhost o como 127.0.0.1: se autorizan ambos.
        if (origen.contains("://localhost:")) agregar(consultorio.getDominios(), origen.replace("://localhost:", "://127.0.0.1:"));
        else if (origen.contains("://127.0.0.1:")) agregar(consultorio.getDominios(), origen.replace("://127.0.0.1:", "://localhost:"));
    }

    private static void agregar(List<String> lista, String valor) {
        if (!lista.contains(valor)) lista.add(valor);
    }
}
