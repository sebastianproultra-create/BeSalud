package com.gestion.proyectos.api;

import java.io.IOException;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.gestion.proyectos.seguridad.ConsultorioCorsSource;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

/**
 * El límite general de peticiones del gestor (RateLimitFilter) corre antes que la seguridad y responde 429
 * sin cabeceras CORS: el navegador se lo ocultaría a la web del consultorio, que solo vería "error de red".
 * Este filtro va antes que él y, solo en rutas de la API y para orígenes autorizados, añade el
 * Access-Control-Allow-Origin a un 429 para que el widget pueda mostrar "espera unos minutos".
 * Así no hace falta tocar RateLimitFilter.
 */
@ConditionalOnProperty(name = "app.api-consultorios.habilitada", havingValue = "true")
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiCorsEnLimiteFilter extends OncePerRequestFilter {

    private static final int DEMASIADAS = 429;

    private final ConsultorioCorsSource corsSource;

    public ApiCorsEnLimiteFilter(ConsultorioCorsSource corsSource) {
        this.corsSource = corsSource;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getHeader("Origin") == null || !ConsultorioCorsSource.esRutaApi(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String origen = request.getHeader("Origin");
        HttpServletResponse envuelta = new HttpServletResponseWrapper(response) {
            @Override
            public void setStatus(int sc) {
                if (sc == DEMASIADAS) permitirOrigen();
                super.setStatus(sc);
            }

            @Override
            public void sendError(int sc, String msg) throws IOException {
                if (sc == DEMASIADAS) permitirOrigen();
                super.sendError(sc, msg);
            }

            @Override
            public void sendError(int sc) throws IOException {
                if (sc == DEMASIADAS) permitirOrigen();
                super.sendError(sc);
            }

            private void permitirOrigen() {
                if (!containsHeader("Access-Control-Allow-Origin") && corsSource.getCorsConfiguration(request) != null)
                    setHeader("Access-Control-Allow-Origin", origen);
            }
        };
        chain.doFilter(request, envuelta);
    }
}
