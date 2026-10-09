package com.gestion.proyectos.seguridad;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;

// Corre antes de Spring Security para contar también las peticiones que luego se rechazan (p. ej. por CSRF).
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private static final Set<String> RUTAS_REGISTRO = Set.of("/register/save", "/elegir-rol");
    private static final int MAX_REGISTROS = 10;
    private static final Duration VENTANA_REGISTRO = Duration.ofMinutes(10);
    private static final String RUTA_TRIAGE = "/triage";
    private static final int MAX_TRIAGE = 5;
    private static final int MAX_POST = 60;
    private static final Duration VENTANA_POST = Duration.ofMinutes(1);

    private final RateLimiter rateLimiter;

    public RateLimitFilter(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // Con forward-headers-strategy=NATIVE e internal-proxies (ver application.properties) es la IP real del cliente.
        String ip = request.getRemoteAddr();
        String ruta = request.getServletPath();
        log.debug("POST {} remoteAddr={} xff={}", ruta, ip, request.getHeader("X-Forwarded-For"));

        boolean permitido;
        if (RUTAS_REGISTRO.contains(ruta))
            permitido = rateLimiter.permitir("registro:" + ip, MAX_REGISTROS, VENTANA_REGISTRO);
        else if (RUTA_TRIAGE.equals(ruta))
            // Cada evaluación consume cuota de la API de Gemini.
            permitido = rateLimiter.permitir("triage:" + ip, MAX_TRIAGE, VENTANA_POST);
        else
            permitido = rateLimiter.permitir("post:" + ip, MAX_POST, VENTANA_POST);

        if (!permitido) {
            log.warn("Límite de peticiones excedido: ip={} ruta={}", ip, ruta);
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(RUTAS_REGISTRO.contains(ruta)
                    ? VENTANA_REGISTRO.toSeconds() : VENTANA_POST.toSeconds()));
            response.setContentType("text/html;charset=UTF-8");
            response.getWriter().write("""
                    <!DOCTYPE html><html lang="es"><head><meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>Demasiadas peticiones - BeSalud</title>
                    <link rel="stylesheet" href="/css/global.css"></head>
                    <body><div class="container"><div class="card text-center">
                    <h2>Demasiadas peticiones</h2>
                    <p class="mb-20">Hiciste demasiadas solicitudes en poco tiempo. Espera unos minutos e intenta de nuevo.</p>
                    <a href="/" class="btn btn-primary">Volver al inicio</a>
                    </div></div></body></html>
                    """);
            return;
        }
        chain.doFilter(request, response);
    }
}
