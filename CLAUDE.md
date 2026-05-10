# BeSalud

## Stack
Java 17 + Spring Boot 3.2.0 | MongoDB localhost:27017/besalud | Thymeleaf + Spring Security | NO Maven wrapper → usar mvn

## Auth
Login por email. Busca: Admin → Paciente → Doctor. Sesión: 45min, máx 1 por usuario. Redirect post-login por rol.

## Modelos clave
- Cita: doctorId, pacienteId, fecha, hora, motivo, EstadoCita, dictamen
- EstadoCita: PENDIENTE, ASISTIO, NO_ASISTIO, CANCELADA, COMPLETADA
- HorarioAtencion: doctorId, diaSemana, horaInicio, horaFin, duracionCitaMinutos

## Convenciones
- esVacio(String s) → helper para nulos/blancos
- Errores form: model.addAttribute("error", msg) → retorno vista
- Errores redirect: ?error=codigo

## Contexto adicional (cargar solo si necesario)
- Arquitectura detallada → .claude/ARCHITECTURE_MAP.md
- Comandos → .claude/QUICK_START.md
- Bugs conocidos → .claude/COMMON_MISTAKES.md

## Gestión de contexto
- Leer solo el fragmento relevante del archivo (usar offset/limit), no archivos completos
- Usar grep antes de read para localizar antes de abrir
- NO releer archivos ya leídos en la misma sesión
- Después de completar una tarea grande: sugerir /compact
- Entre tareas no relacionadas: sugerir /clear

## Rama de trabajo
Siempre trabajar en rama `pruebas`. Si Claude Code crea worktree automático (claude/*), los cambios van a `pruebas` via cherry-pick o merge manual.

## Respuesta
Sigue .claude/caveman.md
