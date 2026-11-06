# BeSalud API para webs de consultorios

Módulo opcional de BeSalud. Lleva la agenda y los pagos del gestor a la web de un consultorio, con dos servicios:

| Servicio | Para quién | Quién lo maneja |
|---|---|---|
| **Página alojada** (`/sitio/{slug}`) | El médico **sin** web | El **equipo de BeSalud** la arma y la publica desde el admin. El médico solo la pide o pide cambios desde el Centro de ayuda |
| **API y widget** (`/desarrolladores`) | El médico o la clínica **con** web propia | Su programador pega el widget o usa la API |

## Un interruptor: apagada no cambia nada

```properties
app.api-consultorios.habilitada=${API_CONSULTORIOS:false}
app.api-consultorios.demo=${API_CONSULTORIOS_DEMO:}
```

| Estado | Qué pasa |
|---|---|
| **Apagada** (por defecto) | BeSalud funciona exactamente como sin el módulo. No se crean la cadena de seguridad de la API, sus beans, sus rutas, sus temas de ayuda ni las tarjetas nuevas. `/api/v1/**`, `/sitio/**`, `/widget/**` y `/desarrolladores` caen en las reglas normales del gestor (login o 403). Lo prueba `ApiApagadaTest` |
| **Encendida** (`API_CONSULTORIOS=true`) | Se suman la API, el widget, el portal `/desarrolladores`, las páginas alojadas y "Páginas web" en el menú del admin |

## Cómo se mantiene separado del gestor

- **Seguridad propia** (`api/ApiSecurityConfig`, `@Order(1)`): atiende solo `/api/v1/**`, `/widget/**`, `/sitio/**` y `/desarrolladores`. No usa sesiones ni el CSRF del gestor (las webs llaman con token Bearer) y tiene su propio CORS por dominio de consultorio. `SecurityConfig` y `RateLimitFilter` **no se tocaron**.
- **Límite de peticiones:** el de registros de la API vive en sus controladores y responde JSON. `ApiCorsEnLimiteFilter` solo añade CORS a un 429 del limitador general, para que el widget muestre "espera unos minutos" en vez de un error de red.
- **Datos:** misma Mongo. Solo agrega colecciones (`consultorios`, `sitios`, `pasesAcceso`) y dos campos opcionales en `Paciente` (`consultorioOrigen`, `doctorAsignado`) que el gestor ignora.
- **Archivos del gestor que cambian** (todo condicionado al interruptor salvo lo marcado):
  - `AdminController` y `admin/dashboard.html`: columna "Sitio web".
  - `doctores/dashboard.html` y su CSS: tarjeta "Mi página web".
  - `navbar-admin.html`: enlace "Páginas web". `layout.html`: enlace "Desarrolladores" en el pie.
  - `Paciente`, `PacienteService`, `PersonaRepositorio`: campos y consultas opcionales.
  - `PagoController`: `Vista` y `vistaDe` pasan a públicos para reusarlos.
  - `login.html`: aviso de enlace vencido (`?enlaceVencido`).
  - `EmailDominioValidator` (siempre activo): **arreglo de un bug**. Pedía MX, A y AAAA en una sola consulta, que JNDI envía como `ANY`, y los resolvers modernos la contestan casi vacía (RFC 8482); ahora pregunta un tipo a la vez.

## Página alojada (administrada por BeSalud)

Así lo hacen Doctoralia y Medesk con su "web profesional": la plataforma la monta y el médico no la edita.

- **Médico**, tarjeta **Mi página web**: "Solicitar mi página" o "Pedir cambios" (abren el Centro de ayuda); si ya la tiene, ve su enlace, el estilo y cuántos pacientes llegaron por ahí. El código para una web propia queda plegado.
- **Admin**, **Páginas web** (`/admin/sitios`): lista de médicos con el estado de su página.
- **Editor** (`/admin/sitios/{id}`): 3 estilos, 6 colores, textos, servicios, WhatsApp, ciudad y dirección, con vista previa en vivo (móvil o escritorio) por `srcdoc`, sin relajar `X-Frame-Options`. Botones Guardar, Guardar y publicar, Despublicar.
- **Al publicar**, `ConsultorioService.alojar` usa el consultorio del médico (o crea uno) y autoriza el dominio de BeSalud. Solo se publica si el médico está activo; la página responde 404 si no está publicada o si el médico o el consultorio están inactivos.

## API por consultorio

Todo bajo `/api/v1/c/{consultorio}` (`ConsultorioApiController`):

| Ruta | Para qué |
|---|---|
| `GET /` | Nombre, médicos activos y tipo de pago |
| `GET /disponibilidad?doctorId=` | Días y horas libres (próximos 30 días) |
| `POST /pacientes`, `POST /sesion` | Registro e inicio de sesión. Devuelven un **token del consultorio**, sin cookie de BeSalud |
| `GET /yo` | Datos del paciente |
| `POST /citas` | Aparta el turno y devuelve cómo pagar. Acepta `Idempotency-Key` |
| `POST /pagos/{ref}/simulado`, `GET /pagos/{ref}?id=` | Pago de prueba y estado del abono |
| `GET /citas`, `POST /citas/{id}/cancelar` | "Mis citas" y cancelación |

**Aislamiento:** cada petición debe venir de un dominio del consultorio (`Origin`); solo se ven sus médicos y sus citas; el token se firma con una clave derivada distinta a la del portal, así que no abre el gestor ni sirve en otro consultorio.

**Inspirado en plataformas conocidas:**

| Mejora | Inspiración | Cómo funciona |
|---|---|---|
| Eventos del widget | Calendly y Cal.com | `besalud:listo`, `abierto`, `cerrado`, `sesion-iniciada`, `turno-apartado`, `cita-confirmada`, `pago-fallido`, `cita-cancelada` en `window` o con `BeSalud.on(evento, fn)`. Sin datos personales |
| Datos prellenados | Prefill de Calendly | `data-email`, `data-nombre`, `data-apellido`, `data-telefono`, `data-motivo` o `BeSalud.abrir("agendar", {...})` |
| Reintentos seguros | `Idempotency-Key` de Stripe | La misma clave devuelve la misma reserva (`Idempotent-Replayed: true`); con otros datos, 422. Dura 24 h |

El widget también acepta `data-titulo` para el nombre de la ventana (la página alojada pasa el suyo).

## Probarlo en local

```powershell
$env:API_CONSULTORIOS="true"; $env:API_CONSULTORIOS_DEMO="demo"; mvn spring-boot:run
```

`scripts/crear-consultorio-demo.js` (mongosh) crea el médico y el consultorio `demo` que usa "Pruébalo en vivo" en `/desarrolladores`.

## Pruebas

`ApiApagadaTest` (apagada no existe nada), `IdempotenciaTest`, `ConsultorioApiControllerTest`, `ApiRegistroControllerTest`, `ConsultorioServiceTest`, `SitioWebServiceTest`, `SitioWebTest`, `AdminSitioWebTest` y `PortalDesarrolladoresTest`.

## Pendiente

- Webhooks firmados al consultorio (`cita.confirmada`, `cita.cancelada`, firma HMAC como `Stripe-Signature`).
- Correos con la marca del consultorio y "Olvidé mi contraseña" en el widget.
- `Idempotencia` y el límite de registros viven en memoria: con varias instancias hay que pasarlos a Mongo o Redis.
