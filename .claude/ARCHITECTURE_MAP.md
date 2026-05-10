# Architecture Map — BeSalud

## Paquetes (com.gestion.proyectos)
```
config/
  DataInitializer.java      — Carga datos iniciales (admin, doctores, pacientes)
  SecurityConfig.java       — BCrypt, sesiones, rutas protegidas, max 1 sesión

controlador/
  LoginController.java      — GET/POST /login, /logout, redirect por rol
  CitaController.java       — CRUD citas (paciente agenda, doctor gestiona)
  AdminController.java      — Gestión usuarios, dashboard admin
  DoctorController.java     — Horarios, historial, dictamen
  PacienteController.java   — Perfil, citas propias

modelo/
  Admin.java                — email, password, nombre
  Doctor.java               — email, password, nombre, especialidad, horarios
  Paciente.java             — email, password, nombre, fechaNacimiento
  Cita.java                 — doctorId, pacienteId, fecha, hora, motivo, EstadoCita, dictamen
  HorarioAtencion.java      — doctorId, diaSemana, horaInicio, horaFin, duracionCitaMinutos

repositorio/
  AdminRepository.java      — findByEmail
  DoctorRepository.java     — findByEmail, findById
  PacienteRepository.java   — findByEmail, findById
  CitaRepository.java       — findByPacienteId, findByDoctorId, findByFechaAndDoctorId

seguridad/
  CustomUserDetailsService  — Busca Admin → Paciente → Doctor por email
  UserPrincipal.java        — Wraps modelo con rol (ROLE_ADMIN, ROLE_DOCTOR, ROLE_PACIENTE)

excepcion/
  HorarioInvalidoException  — Lanzada si cita fuera de horario de atención
```

## Rutas por rol
| Rol      | Acceso base         | Redirect login |
|----------|---------------------|----------------|
| ADMIN    | /admin/**           | /admin/dashboard |
| DOCTOR   | /doctor/**          | /doctor/dashboard |
| PACIENTE | /paciente/**        | /paciente/dashboard |
| Público  | /login, /registro   | — |

## Validaciones clave
- Cita: fecha futura, hora dentro de HorarioAtencion, sin solapamiento
- Registro: email único en las 3 colecciones, contraseña mínimo 8 chars
- Sesión: máx 1 simultánea por usuario (Spring Security sessionManagement)
