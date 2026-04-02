package com.gestion.proyectos.seguridad;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

@Configuration
public class SecurityConfig {

        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                http
                                // Se definen qué URLs puede usar cada rol
                                .authorizeHttpRequests(auth -> auth
                                                .requestMatchers("/", "/login", "/register", "/register/save",
                                                                "/css/**",
                                                                "/js/**", "/error")
                                                .permitAll()
                                                .requestMatchers("/admin/**").hasRole("ADMIN")
                                                .requestMatchers("/doctores/**", "/pacientes/**", "/citas/**")
                                                .authenticated()
                                                .anyRequest().authenticated())

                                // Se habilita el formulario de login
                                .formLogin(form -> form
                                                .loginPage("/login") // Nuestra página personalizada
                                                .loginProcessingUrl("/login") // URL que procesa el login
                                                .successHandler(customAuthenticationSuccessHandler())
                                                .failureUrl("/login?error=true")
                                                .permitAll())
                                // Se habilita el logout
                                .logout(logout -> logout
                                                .logoutUrl("/logout")
                                                .logoutSuccessUrl("/login?logout=true")
                                                .permitAll());

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
        public AuthenticationSuccessHandler customAuthenticationSuccessHandler() {
                return (request, response, authentication) -> {
                        String role = authentication.getAuthorities().stream()
                                        .map(grantedAuthority -> grantedAuthority.getAuthority())
                                        .findFirst()
                                        .orElse("");
                        if ("ROLE_ADMIN".equals(role)) {
                                response.sendRedirect("/admin");
                        } else if ("ROLE_DOCTOR".equals(role)) {
                                response.sendRedirect("/doctores");
                        } else if ("ROLE_PACIENTE".equals(role)) {
                                response.sendRedirect("/pacientes/landing");
                        } else {
                                response.sendRedirect("/login?error=true");
                        }
                };
        }

}
