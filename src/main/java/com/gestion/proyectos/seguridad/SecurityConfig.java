package com.gestion.proyectos.seguridad;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import com.gestion.proyectos.oauth2.OAuth2LoginSuccessHandler;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

        private static final String LOGIN_URL = "/login";

        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http,
                        JwtAuthenticationFilter jwtAuthenticationFilter,
                        JwtCookieService jwtCookieService,
                        OAuth2LoginSuccessHandler oauth2LoginSuccessHandler) throws Exception {
                http
                                .csrf(org.springframework.security.config.Customizer.withDefaults())
                                .authorizeHttpRequests(auth -> auth
                                                .requestMatchers("/", LOGIN_URL, "/register", "/register/save",
                                                                "/css/**", "/images/**", "/vendor/**",
                                                                "/js/**", "/error", "/elegir-rol", "/privacidad")
                                                .permitAll()
                                                .requestMatchers("/admin/**").hasRole("ADMIN")
                                                .requestMatchers("/citas").hasRole("ADMIN")
                                                .requestMatchers("/citas/*/cancelar").hasAnyRole("PACIENTE", "ADMIN")
                                                .requestMatchers("/doctores/*/horarios").hasRole("ADMIN")
                                                .requestMatchers("/pacientes/landing", "/triage", "/triage/agendar", "/citas/nueva",
                                                                "/citas/guardar-paciente", "/citas/*/reprogramar")
                                                .hasRole("PACIENTE")
                                                .requestMatchers("/pacientes", "/pacientes/**",
                                                                "/citas/crear", "/citas/guardar",
                                                                "/doctores/guardar", "/doctores/*/eliminar")
                                                .hasRole("ADMIN")
                                                .requestMatchers("/doctores/perfil/foto").hasRole("DOCTOR")
                                                .requestMatchers("/doctores")
                                                .hasAnyRole("ADMIN", "DOCTOR")
                                                .anyRequest().authenticated())
                                .formLogin(form -> form.disable())
                                .oauth2Login(oauth2 -> oauth2
                                                .loginPage(LOGIN_URL)
                                                .successHandler(oauth2LoginSuccessHandler))
                                .logout(logout -> logout
                                                .logoutUrl("/logout")
                                                .addLogoutHandler((request, response, authentication) ->
                                                                jwtCookieService.clearJwtCookie(response))
                                                .logoutSuccessUrl(LOGIN_URL + "?logout=true")
                                                .permitAll())
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                                .exceptionHandling(ex -> ex
                                                .authenticationEntryPoint((request, response, authException) ->
                                                                response.sendRedirect(LOGIN_URL + "?sessionExpired=true")))
                                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

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
        public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
                return configuration.getAuthenticationManager();
        }

}
