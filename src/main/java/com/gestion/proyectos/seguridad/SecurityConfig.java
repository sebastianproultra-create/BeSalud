package com.gestion.proyectos.seguridad;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.session.HttpSessionEventPublisher;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

        private static final String LOGIN_URL = "/login";

        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                http
                                // Se definen qué URLs puede usar cada rol
                                .authorizeHttpRequests(auth -> auth
                                                .requestMatchers("/", LOGIN_URL, "/register", "/register/save",
                                                                "/css/**", "/images/**",
                                                                "/js/**", "/error")
                                                .permitAll()
                                                .requestMatchers("/admin/**").hasRole("ADMIN")
                                                .requestMatchers("/doctores/**", "/pacientes/**", "/citas/**")
                                                .authenticated()
                                                .anyRequest().authenticated())

                                // Se habilita el formulario de login
                                .formLogin(form -> form
                                                .loginPage(LOGIN_URL) // Nuestra página personalizada
                                                .loginProcessingUrl(LOGIN_URL) // URL que procesa el login
                                                .successHandler(customAuthenticationSuccessHandler())
                                                .failureUrl(LOGIN_URL + "?error=true")
                                                .permitAll())
                                // Se habilita el logout
                                .logout(logout -> logout
                                                .logoutUrl("/logout")
                                                .logoutSuccessUrl(LOGIN_URL + "?logout=true")
                                                .permitAll())
                                // Configuración de sesiones
                                .sessionManagement(session -> session
                                                .sessionFixation().migrateSession()
                                                .sessionCreationPolicy(
                                                                SessionCreationPolicy.IF_REQUIRED)
                                                .maximumSessions(1)
                                                .maxSessionsPreventsLogin(false)
                                                .expiredUrl(LOGIN_URL + "?sessionExpired=true"));

                return http.build();
        }

        @Bean
        public PasswordEncoder passwordEncoder() {
                return new BCryptPasswordEncoder();
        }

        @Bean
        public DaoAuthenticationProvider authenticationProvider(CustomUserDetailsService userDetailsService) {
                DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
                authProvider.setUserDetailsService(userDetailsService);
                authProvider.setPasswordEncoder(passwordEncoder());
                return authProvider;
        }

        @Bean
        public HttpSessionEventPublisher httpSessionEventPublisher() {
                return new HttpSessionEventPublisher();
        }

        private static final String SELECT_ROLE_URL = "/seleccionar-rol";

        public AuthenticationSuccessHandler customAuthenticationSuccessHandler() {
                return (request, response, authentication) -> {
                        java.util.Collection<? extends GrantedAuthority> authorities =
                                        authentication.getAuthorities();
                        boolean isAdmin = authorities.stream()
                                        .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
                        boolean isDoctor = authorities.stream()
                                        .anyMatch(a -> "ROLE_DOCTOR".equals(a.getAuthority()));
                        boolean isPaciente = authorities.stream()
                                        .anyMatch(a -> "ROLE_PACIENTE".equals(a.getAuthority()));
                        if (isAdmin) {
                                response.sendRedirect("/admin");
                        } else if (isDoctor && isPaciente) {
                                response.sendRedirect(SELECT_ROLE_URL);
                        } else if (isDoctor) {
                                response.sendRedirect("/doctores");
                        } else if (isPaciente) {
                                response.sendRedirect("/pacientes/landing");
                        } else {
                                response.sendRedirect(LOGIN_URL + "?error=true");
                        }
                };
        }

}
