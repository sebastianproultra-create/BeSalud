# Modelo predictivo de BESalud

Pronostica **cuántas citas va a tener cada especialidad y cada médico** en los próximos días, para que el médico
pueda organizar su agenda antes de que se llene. Es la parte de Minería de Datos del proyecto.

- **Variable objetivo:** `volumen_citas_total` (citas por día).
- **Algoritmo:** HistGradientBoosting de scikit-learn (ensemble de árboles), con pérdida cuadrática o Poisson
  según cuál tenga menor MAE en una validación de 6 cortes.
- **Dónde corre:** una API en Python (FastAPI) desplegada en Railway, que Spring Boot consulta.

```
Médico abre su panel ──► Spring Boot ──► API Python (scikit-learn) ──► CSV con el historial
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

### Qué tipo de técnica de minería de datos es

| Pregunta | Respuesta |
|---|---|
| ¿Tarea predictiva o descriptiva? | **Predictiva**: estima un valor futuro. Las descriptivas, como el clustering o las reglas de asociación, agrupan o encuentran patrones sin predecir. |
| ¿Aprendizaje supervisado o no supervisado? | **Supervisado**: aprende de ejemplos donde la respuesta es conocida (cuántas citas hubo cada día). |
| ¿Clasificación o regresión? | **Regresión**: la salida es un número (cantidad de citas), no una categoría. |
| ¿Qué tipo de regresión? | **Regresión sobre series de tiempo** con **ensembles de árboles** (HistGradientBoosting de scikit-learn, pérdida cuadrática o Poisson). |
| ¿Variable objetivo? | `volumen_citas_total`: citas por día, de una especialidad o de un médico. |
| ¿Variables explicativas? | Cuatro características derivadas solo del calendario: mes, día de la semana, si es festivo y si es laborable. |

### Paso a paso

1. **Se toma la historia.** Para una especialidad es la columna `volumen_citas_total` del CSV; para un médico, sus
   citas diarias en `citas_por_medico.csv`. Son unos 1.000 días, desde enero de 2024.
2. **Se convierten las fechas en características.** De cada fecha se calculan 4 variables numéricas (mes, día de la
   semana, festivo y laborable). Así el modelo "ve" números y puede aplicarlos igual al pasado que al futuro.
3. **Se valida con varios cortes temporales (rolling-origin).** Se hacen 6 cortes de 60 días que cubren el último año.
   En cada corte se entrena con todo lo anterior y se evalúa en los 60 días siguientes (nunca aleatorio: en series de
   tiempo el pasado predice el futuro, y mezclar días filtraría información del futuro al entrenamiento).
4. **Se comparan dos candidatos y se elige el mejor.** HistGradientBoosting con pérdida cuadrática y con pérdida
   Poisson; gana el de menor **MAE medio en los 6 cortes**, y se reporta junto con su desviación.
5. **Se reentrena el ganador con todo el historial** y se proyectan los días desde mañana (hasta 180).
6. **Se calcula el rango probable.** Con los errores fuera de muestra de los 6 cortes se toman los cuantiles 15 % y
   85 % del error relativo, por separado para días laborables y no laborables. El rango crece con la demanda, y su
   cobertura real se mide en un backtest (≈80 %).
7. **Se entrega.** La API devuelve, por cada día, el valor proyectado y el rango, y el panel del médico lo dibuja.

### Las características (feature engineering)

Todas salen de la fecha, así que se conocen de antemano para cualquier día futuro:

| Característica | Qué captura |
|---|---|
| `mes` | La estacionalidad anual (picos de lluvias de agosto–noviembre) |
| `dia_semana`, `laborable`, `es_festivo` | El patrón semanal y la caída de fines de semana y festivos |

**Selección de características.** Antes había 8. En la validación de 6 cortes se compararon cuatro conjuntos (MAE medio
de las 19 series, mejor candidato):

| Conjunto | MAE medio |
|---|---|
| 8 características (+ `dia_mes`, `semana_anio`, `dia_anio`, `lluvias_laborable`) | 1,217 |
| Sin `semana_anio` ni `dia_anio` | 1,220 |
| Sin las anteriores ni `dia_mes` | 1,171 |
| **Solo las 4 de calendario (la elegida)** | **1,169** |

- `semana_anio` y `dia_anio` le permitían al árbol **memorizar fechas**: con menos de 3 años de datos, aprendía cuándo
  cayeron los brotes aleatorios de 2024 y 2025 y los repetía en 2026.
- `lluvias_laborable` salía de la misma función con la que se simularon los datos. Quitarla no empeora el error, porque
  `mes` ya captura la temporada; así el modelo no recibe ninguna regla de la simulación.

### Cómo funciona el modelo por dentro

**HistGradientBoosting** es un **ensemble de árboles de decisión**: entrena árboles en secuencia (hasta 300
iteraciones) y cada árbol nuevo corrige el error de los anteriores. Internamente agrupa las características en
histogramas, por eso entrena rápido.

Se prueban **dos funciones de pérdida** y gana la de menor MAE medio en cada serie:

| Candidato | Qué optimiza |
|---|---|
| **Pérdida cuadrática** | El error al cuadrado: la opción por defecto de una regresión |
| **Pérdida Poisson** | La verosimilitud de un conteo: la natural cuando se predice "cuántas citas", que nunca son negativas y varían más cuando la media es alta |

La pérdida Poisson gana en 17 de las 19 series. También se probó **RandomForest** en la validación de 6 cortes, y
nunca ganó con las 4 características (MAE medio 1,193 contra 1,169); se descartó para entrenar más rápido.

**Rango probable.** Con los errores fuera de muestra de los 6 cortes se calcula el error relativo
(real − proyectado) / (proyectado + 1) y se toman sus cuantiles 15 % y 85 %, por separado para días laborables y no
laborables. Al dividir entre lo proyectado, **el rango crece con la demanda**: con conteos la variación sube con la
media, así que octubre necesita un margen más ancho que febrero. Los valores se redondean a citas enteras y los
negativos se llevan a 0 (no existen "−1 citas").

Configuración usada en `api_predictiva.py`:

```python
def modelos_candidatos():
    return {
        'HistGradientBoosting': HistGradientBoostingRegressor(max_iter=300, learning_rate=0.05, max_depth=6, random_state=42),
        'HistGradientBoosting Poisson': HistGradientBoostingRegressor(loss='poisson', max_iter=300, learning_rate=0.05,
                                                                      max_depth=6, random_state=42),
    }
```

### Ejemplo con números reales (Neumología)

Con las características de calendario, el modelo aprende el efecto de las reglas de Cartagena: un miércoles de octubre
(hábil, lluvia fuerte) se proyecta en unas **12 citas (rango 7–18)**, un sábado cae a **1 (0–2)** y un miércoles de
febrero (temporada seca) queda en unas **4 (2–6)**. El mismo día de la semana cambia según la temporada, y el rango es
más ancho cuando se esperan más citas.

### ¿Por qué estos modelos y no otra técnica?

- Los **árboles de decisión** manejan bien los datos diarios con patrones semanales y anuales codificados como
  características, que es justo el caso de las citas.
- La **pérdida Poisson** está hecha para conteos.
- Permiten **elegir el mejor candidato** midiendo el MAE en una validación temporal, sin caja negra.
- Entrenan en segundos con unos pocos años de datos, así que se puede entrenar un modelo **por médico** cuando se pide.

| Alternativa | Por qué no se usó |
|---|---|
| Clustering (K-means, etc.) | Es descriptivo: agrupa elementos parecidos, pero no dice **cuántas** citas habrá. Serviría para otra pregunta, por ejemplo agrupar pacientes por diagnóstico |
| Clasificación (árboles, etc.) | Predice categorías ("alta / baja demanda"), no la cantidad exacta que necesita el médico para su agenda |
| Regresión lineal simple | No captura bien los patrones semanales y anuales ni da un margen de error por día |
| ARIMA / SARIMA | También sirve para series de tiempo, pero maneja peor varias estacionalidades a la vez, los festivos y los días sin citas, y requiere más ajuste manual |
| Redes neuronales (LSTM) | Necesitan muchos más datos y son difíciles de interpretar; para unos 1.000 días no compensan |

### Qué tan bien predice

**Método de evaluación (rolling-origin):** 6 cortes de 60 días que cubren el último año del historial. En cada corte
se entrena con todo lo anterior y se predicen los 60 días siguientes. Un solo corte no basta: según dónde caiga (seca o
lluvias) el MAE de una misma especialidad puede variar más de un 50 %. La métrica es el **MAE (error absoluto medio)**:
en promedio, de cuántas citas fue la diferencia por día. Se reporta la media ± la desviación entre cortes, y se compara
contra un **baseline ingenuo** (el promedio del train por día de la semana).

| Serie | Modelo | Baseline por día de la semana | Baseline por mes × día × laborable |
|---|---|---|---|
| Medicina General | 3,10 ± 0,72 | 4,52 ± 0,82 | 3,49 |
| Pediatría | 1,88 ± 0,30 | 3,23 ± 0,95 | 2,00 |
| Neumología | 1,66 ± 0,30 | 2,54 ± 0,69 | 1,79 |
| Dermatología | 1,74 ± 0,06 | 1,95 ± 0,12 | 1,85 |
| Cardiología | 1,66 ± 0,23 | 1,90 ± 0,15 | 1,76 |
| Promedio de las 19 series (5 especialidades + 14 médicos) | **1,17** | 1,58 | 1,26 |

El modelo le gana al baseline simple en **las 19 series** (en un médico, por muy poco: 0,84 contra 0,85).

**Baseline fuerte.** Con 4 características de calendario cabe la pregunta: "¿el modelo hace algo más que promediar
por mes y día de la semana?". Por eso se comparó también contra una tabla de promedios por mes × día de la semana ×
laborable. El modelo gana en **las 19 series**, pero por poco: **un 7 %** en promedio (1,17 contra 1,26). La ganancia
viene de que cada casilla de la tabla tiene apenas unos 10 días de historia y su promedio es ruidoso, mientras que los
árboles comparten información entre meses y días parecidos. A eso se suman lo que la tabla no da: la pérdida Poisson,
el rango probable calibrado y un modelo que se reentrena solo. El ganador se
elige con estos mismos cortes, así que la cifra es levemente optimista; con 6 cortes y solo dos candidatos el efecto
es pequeño.

Para ponerlo en contexto: Medicina General recibe unas 20 a 28 citas por día hábil según la temporada, así que
equivocarse en ~3 citas es un error de alrededor del 12 %, suficiente para planear la agenda.

**Cobertura del rango probable (backtest).** Para cada corte se calibran los cuantiles con los otros 5 cortes y se
cuenta en cuántos días hábiles la demanda real cayó dentro del rango. Resultado: **80,8 %** en promedio (74–79 % en
las especialidades, 79–88 % en los médicos). Medido sobre todos los días hábiles de la validación: 79 % en temporada
de lluvias y 84 % en temporada seca. En fines de semana y festivos la cobertura es mayor (≈95 %), porque casi siempre
hay 0, 1 o 2 citas y un rango entero de 0 a 2 las cubre casi todas.

### Metodología (fases de CRISP-DM)

| Fase | Qué se hizo en BESalud |
|---|---|
| 1. Comprensión del negocio | Los médicos no saben con anticipación qué días se les va a llenar la agenda; se busca pronosticar la demanda por especialidad y por médico |
| 2. Comprensión de los datos | Variables del dataset: fecha, día de la semana, especialidad, festivos, temporada de lluvias, alertas de salud pública y volumen de citas |
| 3. Preparación de los datos | Agregación de las citas por día; serie por médico en `citas_por_medico.csv`; días sin citas completados con 0; características de calendario. Columnas descartadas: `alertas_salud_publica` (no se conoce de antemano para los días futuros), `volumen_citas_pasadas` (exige pronosticar día a día usando predicciones como entrada) y `motivo_consulta` (es un detalle de cada cita, no del total diario) |
| 4. Modelado | HistGradientBoosting con pérdida cuadrática o Poisson; selección de características comparando cuatro conjuntos; un modelo por especialidad y por médico |
| 5. Evaluación | Validación rolling-origin de 6 cortes con MAE (media ± desviación) contra un baseline ingenuo; cobertura del rango medida en un backtest |
| 6. Despliegue | API en FastAPI publicada en Railway, consumida por Spring Boot y mostrada en el panel del médico |

### El rango probable

Además del valor proyectado, el modelo da un **rango probable** (`margenMinimo` – `margenMaximo`): en la validación,
la demanda real de los días hábiles cayó dentro de ese rango **8 de cada 10 veces** (80,8 %).

---

## 3. La API (Python)

Archivo: `api_predictiva.py`. Producción: `https://besalud-prediccion-production.up.railway.app`

| Ruta | Qué devuelve |
|---|---|
| `GET /salud` | Si la API está viva, el estado del archivo de modelos (`modelos`: `vigentes` u `obsoletos (motivo)`) y qué especialidades conoce |
| `GET /medicos?especialidad=pediatria` | Los médicos con historial (id, nombre y especialidad) |
| `GET /prediccion/{especialidad}?dias=30` | Pronóstico de toda la especialidad |
| `GET /prediccion/{especialidad}?dias=30&medico_id=...` | Pronóstico de un médico |

- `dias` va de 1 a 180.
- La especialidad acepta mayúsculas o minúsculas, con o sin tilde (`pediatria` = `Pediatría`).
- Las fechas arrancan **mañana** (hora de Colombia), aunque el historial termine antes: las características son solo de
  calendario, así que sirven para cualquier día futuro.
- Los modelos salen de `modelos_entrenados.joblib`. Si falta o quedó viejo, la primera consulta de cada especialidad o
  médico entrena el modelo (unos 10 segundos) y las siguientes salen de memoria.

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
| **Próximo día de consulta** | Citas esperadas y su rango para el próximo día en que atiende (dice "Mañana" si es mañana); se salta fines de semana y festivos sin citas |
| **Día(s) más cargado(s)** | Cuántas citas tendría el día más lleno. Si varios empatan los dice todos (por ejemplo, "Los jueves: 5, 12, 19 y 26 nov") |
| **Próximos N días** | El total del periodo y el promedio por día de consulta |
| **Ocupación de tu agenda** | Qué porcentaje de sus cupos llenarían las citas esperadas. Si algún día la **demanda** (pacientes que pedirían cita) supera sus cupos, la tarjeta se pone **ámbar** y le sugiere abrir más franjas. El sistema nunca agenda más citas que cupos: el aviso significa que algunos pacientes no alcanzarían cita, algo que pasa sobre todo si el médico recorta su horario |
| **Gráfica** | Por cada día, una columna gris con los cupos del horario y, adentro, una barra verde con las citas esperadas (**ámbar** si superan los cupos), como un vaso que se llena. Un bigote con topes marca el rango probable; los días sin citas esperadas no muestran nada |
| **¿Cómo leer esta gráfica?** | Bloque plegable que explica la columna, la barra, el bigote y el color ámbar |
| Resumen | Total del periodo, ocupación de la agenda, días más llenos y, si hace falta, la sugerencia de abrir más franjas |

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

### El archivo del modelo entrenado

`modelos_entrenados.joblib` (~3,5 MB) guarda los 19 modelos ya entrenados (5 especialidades y 14 médicos): el
modelo ganador de cada serie, el MAE medio y la desviación de cada candidato y del baseline, los cuantiles del rango y
su cobertura medida. Lo genera `entrenar_modelos.py`, que es el entrenamiento explícito (validación de 6 cortes,
comparación de los candidatos, MAE y cobertura):

```bash
cd modelo_predictivo
python entrenar_modelos.py    # entrena todo, imprime MAE ± desviación y cobertura de cada serie (~3-4 min)
```

- La API carga el archivo al arrancar, así la primera consulta ya no espera al entrenamiento.
- El archivo guarda dos huellas (hash). La del código cubre la versión de scikit-learn, `caracteristicas()`,
  `entrenar()`, los hiperparámetros, `CORTES`, `DIAS_TEST` y `P_INTERVALO`; la de los datos cubre toda la serie de
  cada modelo.
- Si el archivo falta, está dañado o la huella del código no coincide (cambiaron las features, los hiperparámetros o
  la versión de scikit-learn), la API deja un `WARNING` en el log, marca `modelos: obsoletos (...)` en `/salud` y
  entrena al vuelo. Si cambió cualquier fila del CSV, reentrena solo ese modelo.
- Para inspeccionarlo: `joblib.load('modelos_entrenados.joblib')['modelos']['especialidad:Pediatría']`.

Si se regeneran los CSV o se cambia el código del modelo, hay que correr `entrenar_modelos.py` y hacer commit del
CSV y del `.joblib` juntos para que Railway use los datos y modelos nuevos.

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

## 8. Guía para la presentación

### Resumen en 30 segundos

> "BESalud pronostica cuántas citas va a tener cada médico en los próximos días. Es una tarea **predictiva y
> supervisada**: hacemos **regresión sobre series de tiempo** con un **ensemble de árboles** (HistGradientBoosting,
> con pérdida cuadrática o Poisson) que aprende de características de calendario. Lo validamos con 6 cortes
> temporales: se equivoca en 1,2 citas por día en promedio, contra 1,6 de un baseline ingenuo, y su rango probable
> acierta 8 de cada 10 días hábiles. El médico ve el pronóstico de 7, 14 o 30 días en su panel, comparado con los
> cupos de su horario, para saber qué días abrir más franjas."

### Orden sugerido para la demo

1. Mostrar el CSV y explicar las reglas de la simulación (sección 1).
2. Explicar el tipo de técnica, las características y la selección por MAE (sección 2).
3. Mostrar el ejemplo con números (octubre vs. febrero vs. sábado).
4. Mostrar la tabla de evaluación (MAE).
5. Entrar como un médico de **Pediatría o Neumología** (el efecto de la lluvia se ve más) y recorrer "Demanda
   proyectada": indicadores, gráfica y botones de 7, 14 y 30 días.
6. Entrar como un médico de **Dermatología** para contrastar: la demanda casi no cambia con la lluvia.

### Preguntas que puede hacer la profe

**¿Es regresión o clustering?** Regresión. La salida es un número (cuántas citas habrá) y se aprende de datos
históricos con la respuesta conocida; el clustering agrupa sin predecir.

**¿Por qué los datos son simulados?** No se tiene acceso a datos reales de una clínica, por privacidad. Se simularon
con reglas realistas de Cartagena (lluvias, festivos, fines de semana) para que el modelo tuviera patrones que aprender.

**¿El modelo "hace trampa" porque las reglas las pusimos nosotros?** Antes sí recibía una pista: una variable de lluvia
que salía de la misma función de la simulación. La quitamos, y el error no empeoró. Hoy el modelo solo ve mes, día de
la semana, festivo y laborable, y tiene que descubrir por su cuenta que octubre se llena y que el sábado no. Los
festivos salen de la librería `holidays`, que es el calendario oficial de Colombia, igual que en la vida real. Además,
el ruido aleatorio (Poisson) y los brotes aleatorios hacen que nunca acierte exacto, como se ve en el MAE.

**¿Cómo comprobaron que el rango es del 80 %?** Con un backtest: en cada uno de los 6 cortes se calibró el rango con
los otros 5 y se contó en cuántos días hábiles la demanda real cayó dentro. Dio 80,8 % (79 % en lluvias, 84 % en seca).

**¿Su MAE de un solo corte es representativo?** No lo usamos: validamos con 6 cortes que cubren un año entero y
reportamos la media ± la desviación.

**¿Cómo saben que funciona?** Por la validación rolling-origin: en 6 cortes de 60 días el modelo predijo días que no
había visto y le ganó al baseline en las 19 series.

**¿Qué pasa si un médico nuevo no tiene historial?** Ve el mensaje "Aún no hay suficiente historial". Se podría usar el
pronóstico de su especialidad dividido entre los médicos mientras acumula citas.

**¿Cómo se mejoraría?** Con datos reales; actualizando el historial automáticamente con las citas nuevas; agregando el
día libre de cada médico como variable; y usando datos reales de lluvia del IDEAM en vez de un factor fijo por mes.

---

## 9. Limitaciones

- Los datos son **simulados**: el modelo aprende las reglas con que se generaron, no el comportamiento real de una clínica.
- El historial termina el 15 de octubre de 2026 y **no se actualiza solo** con las citas nuevas de la app. El pronóstico sí arranca siempre mañana, pero lo que pasó después de esa fecha no se aprende. Para incorporarlo habría que exportar las citas de MongoDB al CSV y volver a desplegar.
- Los modelos por médico y por especialidad son independientes: la suma de los médicos no da exactamente el total de la especialidad (diferencias de pocas citas al mes).
- En los datos simulados ningún médico supera los cupos de su horario, así que la alerta de "días sin cupo suficiente" casi nunca se activa en la demo.
- Solo los 14 médicos simulados tienen pronóstico propio; los demás ven el mensaje de "sin historial".
- El pronóstico de un médico todavía puede marcar 1 o 2 citas en su día libre, porque el modelo no sabe cuál es. Se podría afinar agregando ese día como regresor.
