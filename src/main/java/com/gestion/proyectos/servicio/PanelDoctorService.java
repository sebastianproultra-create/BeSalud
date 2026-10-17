package com.gestion.proyectos.servicio;

import com.gestion.proyectos.modelo.Cita;
import com.gestion.proyectos.modelo.EstadoCita;
import com.gestion.proyectos.modelo.Paciente;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
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
        if (busqueda != null && !busqueda.isBlank()) {
            criterio = criterio.and("pacienteId").in(idsDePacientesQueCoinciden(busqueda));
        }

        Pageable pageable = PageRequest.of(Math.max(0, pagina), tamano,
                Sort.by(Sort.Direction.DESC, "fecha").and(Sort.by(Sort.Direction.ASC, "hora")));
        Query consulta = new Query(criterio);
        long total = mongo.count(consulta, Cita.class);
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
        List<String> ids = mongo.findDistinct(new Query(Criteria.where("doctorId").is(doctorId)), "pacienteId", Cita.class, String.class);
        Criteria criterio = Criteria.where("id").in(ids).and("role").is("PACIENTE");
        if (busqueda != null && !busqueda.isBlank()) criterio = criterio.andOperator(nombreCoincide(busqueda));

        Pageable pageable = PageRequest.of(Math.max(0, pagina), tamano,
                Sort.by("nombre").and(Sort.by("apellido")));
        Query consulta = new Query(criterio);
        long total = mongo.count(consulta, Paciente.class);
        List<Paciente> contenido = mongo.find(Query.of(consulta).with(pageable), Paciente.class);
        return new PanelPacientes(new PageImpl<>(contenido, pageable, total), ids.size());
    }

    private Map<String, Long> conteos(String doctorId, LocalDate hoy) {
        Map<String, Long> conteos = new LinkedHashMap<>();
        conteos.put("TODAS", mongo.count(new Query(Criteria.where("doctorId").is(doctorId)), Cita.class));
        conteos.put("HOY", mongo.count(new Query(Criteria.where("doctorId").is(doctorId).and("fecha").is(hoy)), Cita.class));
        for (EstadoCita e : EstadoCita.values()) {
            conteos.put(e.name(), mongo.count(new Query(Criteria.where("doctorId").is(doctorId).and("estado").is(e)), Cita.class));
        }
        return conteos;
    }

    private List<String> idsDePacientesQueCoinciden(String busqueda) {
        Query q = new Query(new Criteria().andOperator(Criteria.where("role").is("PACIENTE"), nombreCoincide(busqueda)));
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
