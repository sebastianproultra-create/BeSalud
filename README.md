# BeSalud

## ¿De qué trata este proyecto?

BeSalud es un sistema web de gestión para una clínica o consultorio médico. Su objetivo principal es facilitar la administración de pacientes, médicos y citas médicas mediante una aplicación desarrollada con Java, Spring Boot y MongoDB.

La idea es que cada tipo de usuario tenga un acceso y flujo específico:

- Administradores: gestionan usuarios y supervisan el funcionamiento del sistema.
- Doctores: gestionan su agenda, revisan citas y emiten dictámenes.
- Pacientes: registran su información, agendan citas y consultan su historial.

El proyecto está pensado como una solución de aula para demostrar el uso de autenticación, control de roles, sesiones, validaciones y administración de datos clínicos.

## Funcionalidades principales

- Login con autenticación por email.
- Diferenciación por roles: Admin, Paciente y Doctor.
- Redirección automática según el perfil luego de iniciar sesión.
- Registro y gestión de usuarios.
- Programación de citas médicas con validaciones.
- Control de horarios de atención por doctor.
- Validación de citas futuras y sin superposición de horarios.
- Gestión de historial y dictámenes médicos.
- Sesiones con duración de 45 minutos y máximo una sesión por usuario.

## Arquitectura

El proyecto está basado en Spring Boot y sigue una estructura organizada por capas:

- `config`: configuración global, seguridad y carga inicial de datos.
- `controlador`: endpoints y lógica de navegación por rol.
- `modelo`: entidades del sistema (Admin, Doctor, Paciente, Cita, HorarioAtencion).
- `repositorio`: acceso a MongoDB mediante repositorios.
- `seguridad`: autenticación, autorización y detalles del usuario actual.
- `excepcion`: manejo de errores específicos como horarios inválidos.

## Stack tecnológico

- Java 17
- Spring Boot 3.2.0
- MongoDB (`localhost:27017/besalud`)
- Thymeleaf
- Spring Security
- Maven

## Roles del sistema

### Administrador
Puede supervisar la plataforma y gestionar usuarios clave.

### Doctor
Puede:
- ver su agenda,
- atender citas,
- definir o consultar horarios,
- registrar dictámenes.

### Paciente
Puede:
- registrarse,
- iniciar sesión,
- reservar citas,
- ver su historial y citas propias.

## Flujo típico

1. Un usuario ingresa al sistema y accede al login.
2. El sistema valida el email y contraseña.
3. Se identifica el rol y se redirige al dashboard correspondiente.
4. El paciente agenda una cita con un doctor disponible.
5. Se validan reglas como fecha futura, horario dentro de atención y ausencia de solapamiento.
6. El doctor revisa la cita y puede generar un dictamen.

## Objetivo pedagógico

Este proyecto demuestra cómo construir una aplicación web con:

- autenticación y autorización,
- persistencia con MongoDB,
- validaciones de negocio,
- separación por roles,
- patrones MVC con Spring.

Es un proyecto ideal para entender cómo se organiza una aplicación clínica con varios perfiles de usuario y lógica de negocio medica.

## Cómo ejecutarlo

Desde la raíz del proyecto, se usa Maven, sin wrapper:

```bash
mvn spring-boot:run
```

Asegúrate de que MongoDB esté levantado en `localhost:27017` y que la base `besalud` esté disponible.

## Conclusión

BeSalud no es solo un sistema de citas: es una aplicación de gestión clínica completa que refleja cómo una plataforma web puede apoyar procesos reales de salud, organización y atención médica.
