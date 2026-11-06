package com.gestion.proyectos.api;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.gestion.proyectos.modelo.PaseAcceso;

// Pase de un solo uso para "traspasar" al paciente recién registrado desde la web del consultorio
// a BeSalud ya con sesión iniciada. Hace falta porque la cookie JWT no se puede fijar desde un
// fetch de otro dominio (SameSite=Lax); se fija cuando el navegador entra a /api/v1/acceso.
@ConditionalOnProperty(name = "app.api-consultorios.habilitada", havingValue = "true")
@Service
public class PaseAccesoService {

    private static final long VIGENCIA_SEGUNDOS = 120;
    private final SecureRandom random = new SecureRandom();
    private final MongoTemplate mongoTemplate;

    public PaseAccesoService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public String emitir(String email, String doctorId) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        PaseAcceso pase = new PaseAcceso();
        pase.setCodigo(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
        pase.setEmail(email);
        pase.setDoctorId(doctorId);
        pase.setExpira(Date.from(Instant.now().plusSeconds(VIGENCIA_SEGUNDOS)));
        mongoTemplate.insert(pase);
        return pase.getCodigo();
    }

    // findAndRemove es atómico: aunque lleguen dos peticiones a la vez con el mismo código, solo una lo obtiene.
    // El TTL de Mongo corre cada ~60 s, por eso también se filtra por fecha aquí.
    public Optional<PaseAcceso> consumir(String codigo) {
        if (codigo == null || codigo.isBlank()) return Optional.empty();
        Query q = new Query(Criteria.where("_id").is(codigo).and("expira").gt(new Date()));
        return Optional.ofNullable(mongoTemplate.findAndRemove(q, PaseAcceso.class));
    }
}
