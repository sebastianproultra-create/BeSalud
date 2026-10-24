package com.gestion.proyectos.seguridad;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

@Service
public class JwtService {

    private final SecretKey secretKey;
    private final long expirationMillis;

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    // Clave que estuvo publicada en application.properties: con ella cualquiera podría firmarse un token de admin
    private static final String CLAVE_PUBLICADA = "besalud-super-secret-key-min-256-bits-required-for-hmac";

    public JwtService(@Value("${app.jwt.secret:}") String secret,
                      @Value("${app.jwt.expiration-ms}") long expirationMillis) {
        this.secretKey = claveSegura(secret);
        this.expirationMillis = expirationMillis;
    }

    /**
     * Sin JWT_SECRET (o con la clave publicada) se usa una clave aleatoria: es segura, pero al reiniciar
     * la app todos deben volver a iniciar sesión. En producción hay que definir JWT_SECRET.
     */
    private static SecretKey claveSegura(String secret) {
        if (secret == null || secret.isBlank() || CLAVE_PUBLICADA.equals(secret)) {
            log.warn("JWT_SECRET no está configurado: se usa una clave aleatoria (las sesiones se pierden al reiniciar).");
            byte[] aleatoria = new byte[32];
            new SecureRandom().nextBytes(aleatoria);
            return Keys.hmacShaKeyFor(aleatoria);
        }
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(UserDetails userDetails) {
        List<String> roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        Date now = new Date();
        Date expiration = new Date(now.getTime() + expirationMillis);

        return Jwts.builder()
                .subject(userDetails.getUsername())
                .claim("roles", roles)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(secretKey)
                .compact();
    }

    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        String username = extractUsername(token);
        return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        return extractAllClaims(token).getExpiration().before(new Date());
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
