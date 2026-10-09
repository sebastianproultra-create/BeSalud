package com.gestion.proyectos.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

// Correos en segundo plano (@Async) y recordatorios periódicos (@Scheduled).
@Configuration
@EnableAsync
@EnableScheduling
public class TareasConfig {
}
