# BESalud — guía para agentes

Sistema de citas médicas: Spring Boot 3.2.0 + Thymeleaf + MongoDB (`personas`, `citas`, `horariosAtencion`, `admins`)
y una API FastAPI/Prophet en `modelo_predictivo/` que Spring consume por HTTP. Monorepo con dos servicios en Railway.
Responde en español y no hagas commit/push sin que lo pidan.

## Comandos

No hay `mvnw` y `mvn` **no está en el PATH** de esta máquina. Usa el Maven que dejó la extensión de VS Code:

```powershell
$mvn = Get-ChildItem "$env:USERPROFILE\.m2\wrapper\dists\apache-maven-3.9.9" -Recurse -Filter mvn.cmd |
  Select-Object -First 1 -ExpandProperty FullName
& $mvn test                                   # toda la suite
& $mvn -Dtest=CitaServiceTest test            # un solo test
& $mvn -Dtest=CitaServiceTest#metodo test     # un solo método
& $mvn spring-boot:run                        # app en http://localhost:8080
```

- La suite son solo tests unitarios y `@WebMvcTest`/Mockito: **no necesitan MongoDB ni la API de Python**.
  `SecurityRulesTest` y `VistasRenderTest` son la red de seguridad de rutas/roles y de las plantillas Thymeleaf.
- Java del sistema es 22; el `pom.xml` compila a 17. `surefire` ya trae `-Dnet.bytebuddy.experimental=true`;
  no lo quites. No hay linter/formatter ni CI; la verificación es `mvn test` (opcional `mvn sonar:sonar`, requiere
  SonarQube local y `SONAR_TOKEN`). El `Dockerfile` compila con `-DskipTests`.
- `spring.thymeleaf.cache=true`: tras cambiar plantillas/estáticos hay que reiniciar la app.

## Estructura y convenciones

- Paquete raíz `com.gestion.proyectos`; capas `controlador`, `servicio`, `repositorio`, `modelo`, `seguridad`,
  `config`, `util`, `oauth2`. El frontend es Thymeleaf + JS/CSS propios en `src/main/resources` (Chart.js,
  FontAwesome y fuentes están vendorizados: no agregues CDNs ni un build de Node).
- El **admin vive en la colección `admins`** (`Admin` no extiende `Persona`). `Doctor` y `Paciente` extienden
  `Persona`, comparten la colección `personas` y se distinguen por `role` (`DOCTOR`/`PACIENTE`).
  `CustomUserDetailsService` busca primero en `admins`, luego paciente y luego doctor.
- `Cita` guarda `doctorId`/`pacienteId` como `String` (apuntan a `personas._id`), `fecha` como `LocalDate` y `hora`
  como `LocalTime`. `MongoConfig` persiste `hora` como texto `"HH:mm"`; los datos simulados y de demo guardan `fecha`
  como medianoche de Colombia (`05:00Z`). Respeta esas convenciones al tocar datos con scripts o inserts.
- Las contraseñas se guardan con BCrypt. `DataInitializer` crea admin/doctor/paciente de prueba al arrancar y
  **reescribe la contraseña del doctor y del paciente en cada inicio**:
  admin `admin@besalud.com`/`admin12345`, doctor `sebastianfontalvoayola27@gmail.com`/`seba1234`,
  paciente `paciente@besalud.com`/`paciente1234` (override con `besalud.*.password`).
- Datos de demo: `scripts/seed-demo.js` y `scripts/limpiar-datos.js` (Node, sin `package.json` versionado; instala
  `mongodb@6 bcryptjs@2` a mano). Crean/limpian `@demo.besalud.com` con contraseña `Demo1234` y 15 especialidades;
  es independiente del dataset simulado `@sim.besalud.com` (5 especialidades) del modelo predictivo.

## Configuración

- Spring lee `SPRING_DATA_MONGODB_URI` (por defecto `mongodb://localhost:27017/besalud`). El `.env` de la raíz solo
  lo carga VS Code (`launch.json`); `spring-boot:run` no lo lee. `seed-demo.js`/`limpiar-datos.js` usan `MURI`;
  `importar_atlas.py` acepta `MONGODB_URI`, `SPRING_DATA_MONGODB_URI` o el `.env`. No mezcles esas variables.
- Otras variables: `API_PYTHON_URL` (sin `/` final), `JWT_SECRET`, `GOOGLE_CLIENT_ID/SECRET`, `GEMINI_API_KEY`,
  `BREVO_API_KEY` + `MAIL_REMITENTE`, `APP_URL`, `PORT`. Sin claves de Gemini/correo la app usa el respaldo por
  palabras clave o solo registra en log; no falla.
- Seguridad: CSRF activo, `formLogin` deshabilitado (login propio que emite JWT en cookie HttpOnly), sesiones de
  45 min y rate limiting. Antes de cambiar `SecurityConfig`, revisa `SecurityRulesTest`.
- Los archivos con tildes son UTF-8. En PowerShell 5.1 usa `Get-Content -Encoding UTF8` o verás mojibake.

## Modelo predictivo (`modelo_predictivo/`)

- `api_predictiva.py` entrena **al vuelo** desde los CSV al primer request de cada especialidad/médico y cachea en
  memoria; la primera consulta tarda segundos. Local: `uvicorn api_predictiva:app --port 8000` desde esa carpeta.
  Rutas: `GET /salud`, `/medicos?especialidad=`, `/prediccion/{especialidad}?dias=&medico_id=` (máx. 180 días).
  Spring expone `/api/predicciones/mia` (médico en sesión) y `/api/predicciones/{especialidad}`.
- Dependencias: `pip install -r requirements.txt` para la API; `requirements-scripts.txt` para regenerar datos.
- El dataset cubre solo 5 especialidades y 14 médicos simulados (`@sim.besalud.com`). Cambiar el catálogo de
  especialidades obliga a regenerar CSVs y a actualizar `config_simulacion.py` (y `util/Especialidades.java`).
- Regenerar/importar es destructivo: `importar_atlas.py` sin `--confirmar` solo informa; con `--confirmar` borra lo
  `@sim.besalud.com` y reinserta; `--borrar-mock-anterior` borra además el mock viejo. Confirma con el usuario antes.
  `salida/` está en `.gitignore`; los CSV sí se versionan (Railway entrena con ellos).

## Documentación existente

- `README.md`: visión general, roles y flujo.
- `modelo_predictivo/README.md`: reglas de simulación, evaluación MAE, cuentas de prueba (`Demo1234`) y guía de demo.
- `CLAUDE.md` y `.claude/ARCHITECTURE_MAP.md` están **desactualizados** (p. ej. colecciones y nombres de
  repositorios/paquetes): úsalos como contexto, pero verifica contra el código.
