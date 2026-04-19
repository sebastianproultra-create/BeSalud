# BeSalud — Proyecto de gestión médica

## Stack
- Java 17 + Spring Boot 3.2.0
- MongoDB local (`mongodb://localhost:27017/besalud`)
- Thymeleaf + thymeleaf-extras-springsecurity6
- Spring Security (BCrypt, sesiones, max 1 sesión por usuario)
- Sin Maven wrapper — usar `mvn` directamente

## Estructura de paquetes
```
com.gestion.proyectos
├── config/          # DataInitializer
├── controlador/     # AdminController, CitaController, DoctorController,
│                    # HomeController, LoginController, PacienteController
├── modelo/          # Admin, Cita, Dictamen, Doctor, EstadoCita,
│                    # HorarioAtencion, Paciente, UserRegistrationDTO
├── repositorio/     # AdminRepositorio, CitaRepositorio, DoctorRepositorio,
│                    # HorarioAtencionRepositorio, PacienteRepositorio
└── seguridad/       # CustomUserDetailsService, SecurityConfig
```

## Roles y rutas
| Rol            | Ruta base       |
|----------------|-----------------|
| ROLE_ADMIN     | `/admin/**`     |
| ROLE_DOCTOR    | `/doctores/**`  |
| ROLE_PACIENTE  | `/pacientes/**` |
| Público        | `/login`, `/register`, `/register/save` |

## Autenticación
- Login por **email** (no username)
- `CustomUserDetailsService` busca en orden: Admin → Paciente → Doctor
- Redirect post-login según rol (ver `SecurityConfig.customAuthenticationSuccessHandler`)
- Sesión expira en 45 minutos, máximo 1 sesión por usuario

## Modelos clave
- `Cita`: doctorId, pacienteId, fecha, hora, motivo, estado (`EstadoCita`), dictamen
- `HorarioAtencion`: doctorId, diaSemana, horaInicio, horaFin, duracionCitaMinutos
- `EstadoCita`: PENDIENTE, ASISTIO, NO_ASISTIO, CANCELADA, COMPLETADA

## Validaciones implementadas
- **Registro**: todos los campos obligatorios, email con regex, password ≥ 8 chars, rol solo PACIENTE/DOCTOR, doctor requiere especialidad y fechaNacimiento (con try-catch para DateTimeParseException)
- **Citas (admin)**: campos vacíos, fecha/hora parseables, fecha no en pasado, doctor y paciente existen
- **Citas (paciente)**: motivo obligatorio, fecha no en pasado
- **Paciente/Doctor (admin)**: nombre/email obligatorios, formato email, email único

## Tests
```
src/test/java/com/gestion/proyectos/
├── controlador/LoginControllerTest.java     # @WebMvcTest — login, registro, validaciones
└── seguridad/
    ├── CustomUserDetailsServiceTest.java    # JUnit + Mockito puro
    └── SecurityRulesTest.java              # @WebMvcTest — reglas de acceso por rol
```
Correr con: `mvn test`

## Convenciones
- Controladores usan `esVacio(String s)` como helper privado para validar nulos/blancos
- Errores en formularios: `model.addAttribute("error", mensaje)` y retorno a la vista
- Errores en redirects: parámetro `?error=codigo_error` en la URL
