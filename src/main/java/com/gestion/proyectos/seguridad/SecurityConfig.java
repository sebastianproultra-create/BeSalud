package com.gestion.proyectos.seguridad;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                http
                                // Se definen qué URLs puede usar cada rol
                                .authorizeHttpRequests(auth -> auth
                                                .requestMatchers("/login", "/css/**", "/js/**", "/error").permitAll()
                                                .requestMatchers("/proyectos")
                                                .hasAnyRole("ADMIN", "USER", "COLABORADOR")
                                                .requestMatchers("/proyectos/crear", "/proyectos/eliminar")
                                                .hasRole("ADMIN")
                                                .requestMatchers("proyectos/crear").hasRole("COLABORADOR")
                                                .anyRequest().authenticated())

                                // Se habilita el formulario de login
                                .formLogin(form -> form
                                                .loginPage("/login") // Nuestra página personalizada
                                                .loginProcessingUrl("/login") // URL que procesa el login
                                                .defaultSuccessUrl("/proyectos", true)
                                                .failureUrl("/login?error=true")
                                                .permitAll())
                                // Se habilita el logout
                                .logout(logout -> logout
                                                .logoutUrl("/logout")
                                                .logoutSuccessUrl("/login?logout=true")
                                                .permitAll());

                return http.build();
        }

        // Usuarios definidos en memoria
        @Bean
        public UserDetailsService users() {

                UserDetails admin = User.withDefaultPasswordEncoder()
                                .username("admin")
                                .password("admin123")
                                .roles("ADMIN")
                                .build();

                UserDetails user = User.withDefaultPasswordEncoder()
                                .username("user")
                                .password("user123")
                                .roles("USER")
                                .build();

                UserDetails colaborador = User.withDefaultPasswordEncoder()
                                .username("colaborador")
                                .password("colaborador123")
                                .roles("COLABORADOR")
                                .build();

                return new InMemoryUserDetailsManager(admin, user, colaborador);
        }

}
