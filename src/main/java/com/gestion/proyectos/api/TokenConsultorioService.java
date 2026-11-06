package com.gestion.proyectos.api;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Date;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Token del paciente dentro de la web de UN consultorio. Se firma con una clave derivada distinta a la del
 * portal: JwtAuthenticationFilter no lo puede validar, así que no abre el gestor de citas, y el claim "cid"
 * impide usarlo en la API de otro consultorio.
 */
@ConditionalOnProperty(name = "app.api-consultorios.habilitada", havingValue = "true")
@Service
public class TokenConsultorioService {

    static final Duration VIGENCIA = Duration.ofHours(2);
    private static final String CLAIM_CONSULTORIO = "cid";

    private final SecretKey clave;

    public TokenConsultorioService(@Value("${app.jwt.secret:}") String secreto) {
        this.clave = Keys.hmacShaKeyFor(derivar(secreto));
    }

    // HMAC(secreto, etiqueta): misma variable JWT_SECRET, clave distinta. Sin secreto, aleatoria (se pierde al reiniciar).
    private static byte[] derivar(String secreto) {
        if (secreto == null || secreto.isBlank()) {
            byte[] aleatoria = new byte[32];
            new SecureRandom().nextBytes(aleatoria);
            return aleatoria;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal("besalud-api-consultorios".getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public String emitir(String email, String consultorioId) {
        Date ahora = new Date();
        return Jwts.builder()
                .subject(email)
                .claim(CLAIM_CONSULTORIO, consultorioId)
                .issuedAt(ahora)
                .expiration(new Date(ahora.getTime() + VIGENCIA.toMillis()))
                .signWith(clave)
                .compact();
    }

    /** Correo del paciente si el token es válido, vigente y de este consultorio. */
    public Optional<String> emailDe(String token, String consultorioId) {
        if (token == null || token.isBlank())
            return Optional.empty();
        try {
            Claims claims = Jwts.parser().verifyWith(clave).build().parseSignedClaims(token).getPayload();
            if (!consultorioId.equals(claims.get(CLAIM_CONSULTORIO, String.class)))
                return Optional.empty();
            return Optional.ofNullable(claims.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
