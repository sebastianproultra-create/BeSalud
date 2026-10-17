# Modelo predictivo de BESalud

Pronostica **cuántas citas va a tener cada especialidad y cada médico** en los próximos días, para que el médico
pueda organizar su agenda antes de que se llene. Es la parte de Minería de Datos del proyecto.

- **Variable objetivo:** `volumen_citas_total` (citas por día).
- **Algoritmo:** [Prophet](https://facebook.github.io/prophet/) (Meta), un modelo de series de tiempo.
- **Dónde corre:** una API en Python (FastAPI) desplegada en Railway, que Spring Boot consulta.

```
Médico abre su panel ──► Spring Boot ──► API Python (Prophet) ──► CSV con el historial
   (gráfica)            /api/predicciones/mia    /prediccion/{especialidad}?medico_id=
```

---

## 1. Los datos

El historial es **simulado** con reglas realistas para Cartagena, desde el 1 de enero de 2024 hasta el 15 de octubre de 2026.

### Reglas de la simulación

| Regla | Cómo se aplica |
|---|---|
| Demanda base (día hábil, temporada seca) | Medicina General 20, Cardiología 7, Dermatología 7, Pediatría 6, Neumología 4 citas |
| Lluvias en Cartagena | Factor por mes: 1,5 en mayo–junio y agosto, 2,0 en septiembre, **3,0 en octubre** y 2,5 en noviembre |
| A quién afecta la lluvia | Pediatría y Neumología reciben el factor completo (se duplican o triplican); Medicina General sube un 30 % del factor; Cardiología y Dermatología no cambian |
| Alertas de salud pública | Brotes de IRA o dengue de 2 a 4 semanas, casi siempre en lluvias: +30 % en Medicina General y Pediatría y +20 % en Neumología |
| Fines de semana y festivos colombianos | Caída fuerte: de 0 a 2 citas por especialidad |
| Ruido | Cada día se sortea con una distribución de Poisson alrededor de la media |

### Archivos

| Archivo | Qué contiene |
|---|---|
| `dataset_citas_medicas_cartagena.csv` | Una fila por día y especialidad (5.095 filas, 37.382 citas). Columnas: `fecha`, `año`, `mes`, `dia_semana`, `especialidad_medica`, `motivo_consulta`, `es_festivo`, `factor_estacional_lluvias`, `alertas_salud_publica`, `volumen_citas_pasadas` (misma especialidad, una semana antes) y `volumen_citas_total` |
| `citas_por_medico.csv` | Una fila por día y médico, con las citas que le tocaron. Con este archivo se entrena el modelo de cada médico |

### De volumen diario a citas individuales

`generar_citas.py` convierte el volumen de cada día en citas reales dentro de MongoDB:

- **14 médicos** (ver la [tabla de cuentas](#7-cuentas-de-prueba)), cada uno con un horario semanal y un día libre:
  - de lunes a viernes, jornada completa: 18 citas de 20 minutos (Medicina General y Pediatría) o 16 de 30 minutos (el resto);
  - sábado y domingo, solo en la mañana.
- **~5.000 pacientes** (1.200 niños, que solo van a Pediatría; Cardiología atiende a mayores de 40).
- Cada día, primero se agendan los **controles pendientes con el mismo médico**. Después los pacientes nuevos van a su médico de referencia o al que tenga más cupos.
- **Nunca** hay dos citas del mismo médico a la misma hora ni dos citas del mismo paciente el mismo día.
- Cada cita atendida trae un **dictamen SOAP completo** con los campos de las clases Java: tipo de consulta, antecedentes, signos vitales con IMC calculado, diagnóstico CIE-10 tomado del catálogo de la app, fórmula médica, órdenes, recomendaciones e incapacidad.
- **Coherencia clínica:**
  - un "Control" o una "Lectura de exámenes" siempre viene después de una consulta con el mismo médico y el mismo diagnóstico;
  - las enfermedades crónicas pasan a los antecedentes del paciente;
  - a los alérgicos a la penicilina no se les formula amoxicilina.
- Estados de las citas: 85 % completadas, 9 % el paciente no asistió y 6 % canceladas.

El script se valida a sí mismo al terminar: si encuentra un choque de horario, un volumen que no coincide con el CSV o un control sin consulta previa, falla. La última corrida dio **0 errores**.

---

## 2. Cómo funciona el modelo

### La idea en una frase

El modelo mira **cómo se comportaron las citas en el pasado** (qué días se llenan, en qué meses sube la demanda,
qué pasa los festivos) y **repite ese comportamiento hacia el futuro**, con un margen de error.

### Paso a paso

1. **Se toma la historia.** Para una especialidad es la columna `volumen_citas_total` del CSV; para un médico, sus
   citas diarias en `citas_por_medico.csv`. Son unos 1.000 días, desde enero de 2024.
2. **Se le agregan dos datos que se conocen de antemano:** si cada día es hábil (no es fin de semana ni festivo
   colombiano) y el factor de lluvia de ese mes en Cartagena.
3. **Prophet aprende.** Busca la combinación de "piezas" (ver la tabla de abajo) que mejor reproduce la historia.
   Por ejemplo, descubre solo que en octubre Neumología recibe muchas más citas que en febrero, sin que nadie se lo diga.
4. **Se proyecta.** Con lo aprendido calcula los próximos días (hasta 180), sumando las piezas para cada fecha futura.
5. **Se calcula el margen.** Prophet simula muchos futuros posibles y se queda con el rango donde cae el 80 % de ellos.
6. **Se entrega.** La API devuelve, por cada día, el valor proyectado y el margen, y el panel del médico lo dibuja.

### Las piezas que suma el modelo

Prophet descompone la serie de citas en partes que se suman:

```
citas(día) = tendencia + patrón semanal + patrón anual + efecto de días hábiles + efecto de lluvia en días hábiles
```

| Componente | Qué aprende |
|---|---|
| Tendencia | Si la demanda va subiendo o bajando con los meses |
| Estacionalidad semanal | Que los lunes y martes se llenan más que el sábado |
| Estacionalidad anual | Los picos de mayo–junio y de agosto–noviembre |
| Regresor `laborable` | 1 si es día hábil y no festivo colombiano. Hace que fines de semana y festivos caigan casi a cero |
| Regresor `lluvias_laborable` | El factor de lluvia del mes, solo en días hábiles (en fines de semana casi no hay citas, llueva o no) |

Los dos regresores se conocen de antemano para cualquier fecha futura, porque el calendario y la temporada de lluvias son fijos. Por eso Prophet puede usarlos para proyectar.

### Ejemplo con números reales (Neumología)

Valores que sacó el modelo entrenado. Cada pieza se mide **respecto a un día promedio**, por eso algunas son
negativas: "−1,2" significa "1,2 citas menos que un día cualquiera".

| Pieza | Miércoles 21 oct 2026 (hábil, lluvia fuerte) | Sábado 24 oct 2026 | Miércoles 17 feb 2027 (hábil, seco) |
|---|---|---|---|
| Tendencia (nivel base) | 5,5 | 5,5 | 5,6 |
| Patrón semanal | 0,0 | +0,1 | 0,0 |
| Patrón anual | +0,8 | +0,9 | −0,1 |
| Día hábil + lluvia | **+7,7** | **−4,3** | −1,1 |
| **Proyección** | **≈ 14 citas** (margen 11–17) | **≈ 2 citas** (margen 0–5) | **≈ 4 citas** (margen 2–7) |
| Lo que pasó el año anterior, mismo mes | miércoles de oct 2025: 9 a 15 | sábados: 0 a 2 | miércoles de feb 2026: 1 a 7 |

Así se ve el efecto de las reglas de Cartagena: el mismo día de la semana pasa de unas 4 citas en temporada seca a
unas 14 en el pico de lluvias, y el sábado cae casi a cero llueva o no.

### ¿Por qué Prophet?

- Está hecho para datos **diarios con patrones semanales y anuales**, que es justo el caso de las citas.
- Trae los **festivos de Colombia** incorporados y acepta variables extra como la lluvia.
- Da el **margen de error** sin trabajo adicional.
- Entrena en segundos con unos pocos años de datos, así que se puede entrenar un modelo **por médico** cuando se pide.

### Qué tan bien predice

Se entrenó con todo el historial menos los últimos 60 días y luego se comparó con lo que realmente pasó en esos 60 días. El error es el promedio de citas de diferencia por día:

| Serie | Sin regresores (versión original) | Con regresores (versión actual) |
|---|---|---|
| Medicina General | 5,52 citas/día | **4,20** (−24 %) |
| Neumología | 2,53 | **1,85** (−27 %) |
| Dra. Daniela Ramos (Pediatría) | 1,08 | **1,00** |

En fines de semana el error bajó a ~0,6 citas.

### El margen

Además del valor proyectado, Prophet da un **rango probable** (`margenMinimo` – `margenMaximo`): la demanda real debería caer dentro de ese rango **8 de cada 10 veces**.

---

## 3. La API (Python)

Archivo: `api_predictiva.py`. Producción: `https://besalud-prediccion-production.up.railway.app`

| Ruta | Qué devuelve |
|---|---|
| `GET /salud` | Si la API está viva y qué especialidades conoce |
| `GET /medicos?especialidad=pediatria` | Los médicos con historial (id, nombre y especialidad) |
| `GET /prediccion/{especialidad}?dias=30` | Pronóstico de toda la especialidad |
| `GET /prediccion/{especialidad}?dias=30&medico_id=...` | Pronóstico de un médico |

- `dias` va de 1 a 180.
- La especialidad acepta mayúsculas o minúsculas, con o sin tilde (`pediatria` = `Pediatría`).
- La primera consulta de cada especialidad o médico entrena el modelo (unos segundos). Las siguientes salen de memoria.

Ejemplo de respuesta:

```json
{
  "especialidad": "Medicina General",
  "medico_id": "659246d00001000000000001",
  "medico_nombre": "Laura Martínez Ortega",
  "dias_proyectados": 3,
  "predicciones": [
    { "fecha": "2026-10-16", "volumenProyectado": 8, "margenMinimo": 7, "margenMaximo": 10 },
    { "fecha": "2026-10-17", "volumenProyectado": 0, "margenMinimo": 0, "margenMaximo": 2 }
  ]
}
```

Errores: **404** si la especialidad o el médico no tienen historial, y **400** si el médico no pertenece a esa especialidad.

---

## 4. La conexión con Spring Boot

- `PrediccionService` llama a la API con la URL de la propiedad `api.python.url`, que se lee de la variable de entorno `API_PYTHON_URL` (en local, `http://localhost:8000`).
- `PrediccionController` expone:
  - `GET /api/predicciones/mia?dias=30`: el pronóstico **del médico con la sesión abierta**. Spring averigua solo su especialidad y su id. Si quien pregunta no es médico, responde 403.
  - `GET /api/predicciones/{especialidad}?medicoId=...&dias=30`: cualquier especialidad o médico.
- Si la API responde 404 o 400, Spring pasa ese mismo error. Si la API está caída, responde 500 con un mensaje claro sin romper la app.

---

## 5. La parte visual (panel del médico)

En el panel del médico hay una sección **"Demanda proyectada"**, entre "Disponibilidad semanal" y "Mis citas". También aparece en la barra de secciones.

| Elemento | Qué muestra |
|---|---|
| Botones **7 / 14 / 30 días** | Cuántos días hacia adelante ver |
| **Mañana** | Citas esperadas para mañana y su rango |
| **Día más cargado** | La fecha con más demanda del periodo y cuántas citas |
| **Próximos N días** | El total del periodo y el promedio por día de consulta |
| **Días sin cupo suficiente** | Los días en que la demanda proyectada supera los cupos de su horario. Si hay alguno, la tarjeta se pone **ámbar** y le sugiere abrir más franjas |
| **Gráfica** | Barras verdes con las citas proyectadas por día (**ámbar** si superan los cupos), franja menta con el margen probable y línea punteada con los cupos de su horario |
| Resumen | Una frase con el total, el día más cargado y qué significa el margen |

Los **cupos** se calculan en el navegador con el horario que el médico tiene configurado (franja ÷ duración de la cita), igual que los cupos de la agenda. Si cambia su horario, la comparación cambia sola.

Estados especiales:

- **Cargando:** un aviso mientras la API responde. La primera vez puede tardar unos segundos.
- **Sin historial:** "Aún no hay suficiente historial de citas para proyectar tu demanda." Es lo que ven los médicos que no están en la simulación.
- **API caída:** "El modelo predictivo no está disponible en este momento."

Archivos: `templates/doctores/dashboard.html` (sección `#demanda`), `static/js/prediccion.js` y `static/css/doctores/dashboard.css` (bloque "DEMANDA PROYECTADA"). La gráfica usa Chart.js, que ya venía en `static/vendor/chart/`.

---

## 6. Cómo regenerar y desplegar

```bash
cd modelo_predictivo
pip install -r requirements-scripts.txt

python generar_dataset.py          # 1. CSV agregado
python generar_citas.py            # 2. médicos, pacientes, horarios y citas en salida/ + citas_por_medico.csv
python importar_atlas.py           # 3. solo muestra qué hay en la base (no escribe)
python importar_atlas.py --confirmar --borrar-mock-anterior   # 4. reemplaza los datos simulados
```

- `importar_atlas.py` lee la URI de `MONGODB_URI` o del `.env` de la raíz (que **no** se sube al repo).
- Solo borra lo simulado (`@sim.besalud.com`) y las citas del mock viejo; no toca usuarios ni citas reales.

**Railway:** el servicio de la API usa este repo con Root Directory `/modelo_predictivo` y el `Procfile`
(`uvicorn api_predictiva:app --host 0.0.0.0 --port $PORT`). El servicio de Spring necesita la variable
`API_PYTHON_URL` con el dominio de la API, sin `/` al final.

Si se regeneran los CSV, hay que hacer commit para que Railway entrene con los datos nuevos.

---

## 7. Cuentas de prueba

Todos los médicos simulados entran con la contraseña **`Demo1234`**.

| Médico | Especialidad | Correo | Día libre | Citas en el historial |
|---|---|---|---|---|
| Laura Martínez Ortega | Medicina General | `laura.martinez@sim.besalud.com` | domingo | 4.375 |
| Andrés Pérez Villalba | Medicina General | `andres.perez@sim.besalud.com` | domingo | 4.349 |
| Camila Rodríguez Herazo | Medicina General | `camila.rodriguez@sim.besalud.com` | domingo | 4.361 |
| Julián Torres Benítez | Medicina General | `julian.torres@sim.besalud.com` | miércoles | 3.245 |
| Valentina Gómez Arrieta | Pediatría | `valentina.gomez@sim.besalud.com` | domingo | 2.400 |
| Mauricio Castro Julio | Pediatría | `mauricio.castro@sim.besalud.com` | domingo | 2.355 |
| Daniela Ramos Padilla | Pediatría | `daniela.ramos@sim.besalud.com` | jueves | 1.736 |
| Ricardo Morales Cabarcas | Neumología | `ricardo.morales@sim.besalud.com` | domingo | 1.567 |
| Natalia Herrera Marrugo | Neumología | `natalia.herrera@sim.besalud.com` | domingo | 1.630 |
| Felipe Díaz Barrios | Neumología | `felipe.diaz@sim.besalud.com` | martes | 1.272 |
| Carolina Vargas Pájaro | Cardiología | `carolina.vargas@sim.besalud.com` | domingo | 2.895 |
| Sergio Jiménez Caraballo | Cardiología | `sergio.jimenez@sim.besalud.com` | lunes | 2.182 |
| Mariana Ruiz Altamar | Dermatología | `mariana.ruiz@sim.besalud.com` | domingo | 2.973 |
| Óscar Mendoza Guerrero | Dermatología | `oscar.mendoza@sim.besalud.com` | viernes | 2.042 |

Quien libra entre semana cubre la guardia del domingo; por eso atiende menos citas.

Los pacientes simulados también pueden entrar con la misma contraseña. Sus correos van de `p00001@sim.besalud.com` a
`p05000@sim.besalud.com`, pero solo existen los 4.986 que tienen citas; a unos pocos números no les corresponde cuenta.

> ⚠️ La contraseña es conocida y está en el código. Sirve para la demo del proyecto; no deben existir estas cuentas en
> una base con pacientes reales.

---

## 8. Limitaciones

- Los datos son **simulados**: el modelo aprende las reglas con que se generaron, no el comportamiento real de una clínica.
- El historial termina el 15 de octubre de 2026 y **no se actualiza solo** con las citas nuevas de la app. Para incorporarlas habría que exportar las citas de MongoDB al CSV y volver a desplegar.
- Solo los 14 médicos simulados tienen pronóstico propio; los demás ven el mensaje de "sin historial".
- El pronóstico de un médico todavía puede marcar 1 o 2 citas en su día libre, porque el modelo no sabe cuál es. Se podría afinar agregando ese día como regresor.
