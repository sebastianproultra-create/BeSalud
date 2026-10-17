package com.gestion.proyectos.servicio;

import com.gestion.proyectos.modelo.Cita;
import com.gestion.proyectos.modelo.EstadoCita;
import com.gestion.proyectos.modelo.Paciente;

import org.bson.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static com.gestion.proyectos.util.ValidacionUtil.esVacio;

// Citas y pacientes del panel médico paginados en la base: un médico con miles de citas
// no debe mandar todas al navegador (la página llegaba a pesar 14 MB).
@Service
public class PanelDoctorService {

    public static final List<String> FILTROS = List.of("TODAS", "HOY", "PENDIENTE", "ASISTIO", "COMPLETADA", "NO_ASISTIO", "CANCELADA");

    private final MongoTemplate mongo;

    public PanelDoctorService(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public record PanelCitas(Page<Cita> pagina, Map<String, Long> conteos,
            Map<String, String> pacienteNombres, Map<String, Map<String, Object>> dictamenes) {}

    public record PanelPacientes(Page<Paciente> pagina, long total) {}

    public static String normalizarFiltro(String filtro) {
        return filtro != null && FILTROS.contains(filtro) ? filtro : "TODAS";
    }

    public PanelCitas citas(String doctorId, String filtro, String busqueda, int pagina, int tamano, String citaFija) {
        filtro = normalizarFiltro(filtro);
        LocalDate hoy = LocalDate.now();

        Criteria criterio = Criteria.where("doctorId").is(doctorId);
        if ("HOY".equals(filtro)) {
            criterio = criterio.and("fecha").is(hoy);
        } else if (!"TODAS".equals(filtro)) {
            criterio = criterio.and("estado").is(EstadoCita.valueOf(filtro));
        }
        if (!esVacio(busqueda)) {
            criterio = criterio.and("pacienteId").in(idsDePacientesQueCoinciden(pacientesIds(doctorId), busqueda));
        }

        Query consulta = new Query(criterio);
        long total = mongo.count(consulta, Cita.class);
        Pageable pageable = PageRequest.of(paginaValida(pagina, total, tamano), tamano,
                Sort.by(Sort.Direction.DESC, "fecha").and(Sort.by(Sort.Direction.ASC, "hora")));
        List<Cita> contenido = new ArrayList<>(mongo.find(Query.of(consulta).with(pageable), Cita.class));

        // Tras un error al guardar el dictamen, la cita debe estar en la página para reabrir su formulario
        if (citaFija != null && contenido.stream().noneMatch(c -> citaFija.equals(c.getId()))) {
            Cita fija = mongo.findById(citaFija, Cita.class);
            if (fija != null && doctorId.equals(fija.getDoctorId())) contenido.add(0, fija);
        }

        return new PanelCitas(new PageImpl<>(contenido, pageable, total), conteos(doctorId, hoy),
                nombres(contenido), dictamenes(contenido));
    }

    public PanelPacientes pacientes(String doctorId, String busqueda, int pagina, int tamano) {
        Criteria base = Criteria.where("id").in(pacientesIds(doctorId)).and("role").is("PACIENTE");
        long registrados = mongo.count(new Query(base), Paciente.class);
        Query consulta = esVacio(busqueda) ? new Query(base)
                : new Query(new Criteria().andOperator(base, nombreCoincide(busqueda)));
        long total = esVacio(busqueda) ? registrados : mongo.count(consulta, Paciente.class);

        Pageable pageable = PageRequest.of(paginaValida(pagina, total, tamano), tamano,
                Sort.by("nombre").and(Sort.by("apellido")));
        List<Paciente> contenido = mongo.find(Query.of(consulta).with(pageable), Paciente.class);
        return new PanelPacientes(new PageImpl<>(contenido, pageable, total), registrados);
    }

    private List<String> pacientesIds(String doctorId) {
        return mongo.findDistinct(new Query(Criteria.where("doctorId").is(doctorId)), "pacienteId", Cita.class, String.class);
    }

    // Un enlace viejo con ?pc=10 cuando solo quedan 3 páginas cae en la última en vez de en una vacía
    private static int paginaValida(int pagina, long total, int tamano) {
        int ultima = (int) Math.max(0, (total - 1) / tamano);
        return Math.min(Math.max(0, pagina), ultima);
    }

    private Map<String, Long> conteos(String doctorId, LocalDate hoy) {
        Map<String, Long> conteos = new LinkedHashMap<>();
        conteos.put("TODAS", 0L);
        conteos.put("HOY", mongo.count(new Query(Criteria.where("doctorId").is(doctorId).and("fecha").is(hoy)), Cita.class));
        for (EstadoCita e : EstadoCita.values()) conteos.put(e.name(), 0L);

        Aggregation porEstado = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("doctorId").is(doctorId)),
                Aggregation.group("estado").count().as("n"));
        for (Document d : mongo.aggregate(porEstado, Cita.class, Document.class)) {
            long n = ((Number) d.get("n")).longValue();
            conteos.merge("TODAS", n, Long::sum);
            Object estado = d.get("_id");
            if (estado != null) conteos.computeIfPresent(estado.toString(), (k, v) -> v + n);
        }
        return conteos;
    }

    private List<String> idsDePacientesQueCoinciden(List<String> pacientesDelDoctor, String busqueda) {
        Query q = new Query(new Criteria().andOperator(
                Criteria.where("id").in(pacientesDelDoctor).and("role").is("PACIENTE"), nombreCoincide(busqueda)));
        q.fields().include("id");
        return mongo.find(q, Paciente.class).stream().map(Paciente::getId).toList();
    }

    // Cada palabra debe aparecer en el nombre o el apellido: "ana gil" encuentra a "Ana María Gil"
    private Criteria nombreCoincide(String busqueda) {
        List<Criteria> palabras = new ArrayList<>();
        for (String palabra : busqueda.trim().split("\\s+")) {
            Pattern p = Pattern.compile(Pattern.quote(palabra), Pattern.CASE_INSENSITIVE);
            palabras.add(new Criteria().orOperator(Criteria.where("nombre").regex(p), Criteria.where("apellido").regex(p)));
        }
        return new Criteria().andOperator(palabras.toArray(new Criteria[0]));
    }

    private Map<String, String> nombres(List<Cita> citas) {
        List<String> ids = citas.stream().map(Cita::getPacienteId).filter(id -> id != null).distinct().toList();
        Map<String, String> nombres = new HashMap<>();
        mongo.find(new Query(Criteria.where("id").in(ids).and("role").is("PACIENTE")), Paciente.class)
                .forEach(p -> nombres.put(p.getId(), p.getNombre() + " " + p.getApellido()));
        return nombres;
    }

    private Map<String, Map<String, Object>> dictamenes(List<Cita> citas) {
        Map<String, Map<String, Object>> result = new HashMap<>();
        for (Cita c : citas)
            if (c.getDictamen() != null) result.put(c.getId(), DictamenVista.de(c.getDictamen()));
        return result;
    }
}
