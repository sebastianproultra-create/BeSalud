package com.gestion.proyectos.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.gestion.proyectos.seguridad.ConsultorioCorsSource;
import com.gestion.proyectos.seguridad.JwtAuthenticationFilter;

/**
 * Cadena de seguridad propia de la API de consultorios, separada de la del gestor (SecurityConfig).
 *
 * Atiende solo sus rutas y va antes que la del gestor, así que SecurityConfig queda idéntico al del equipo:
 * el gestor no sabe que la API existe. Estas rutas no pasan por el CSRF, el login ni las reglas de roles del
 * gestor; cada endpoint valida el origen del consultorio y su propio token (TokenConsultorioService).
 *
 * No crea sesiones. Sí reconoce la cookie del portal (JwtAuthenticationFilter) porque el flujo v1 heredado
 * (/api/v1/acceso y /api/v1/agendar) entrega al paciente al gestor, y el portal /desarrolladores pinta la
 * barra según quién esté conectado. Con la API apagada esta cadena no existe.
 */
@ConditionalOnProperty(name = "app.api-consultorios.habilitada", havingValue = "true")
@Configuration
public class ApiSecurityConfig {

    static final String[] RUTAS = {"/api/v1/**", "/widget/**", "/sitio/**", "/desarrolladores"};

    @Bean
    @Order(1)
    public SecurityFilterChain apiConsultoriosFilterChain(HttpSecurity http, ConsultorioCorsSource corsSource,
            JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        http.securityMatcher(RUTAS)
                .cors(cors -> cors.configurationSource(corsSource))
                // Las webs de los consultorios llaman sin cookies (token Bearer): no hay sesión que un sitio
                // ajeno pueda usar, así que el CSRF del gestor no aplica aquí.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.NEVER))
                .requestCache(cache -> cache.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
