# Modelo predictivo de BESalud

Pronostica **cuántas citas va a tener cada especialidad y cada médico** en los próximos días, para que el médico
pueda organizar su agenda antes de que se llene. Es la parte de Minería de Datos del proyecto.

- **Variable objetivo:** `volumen_citas_total` (citas por día).
- **Algoritmo:** ensembles de árboles de scikit-learn (RandomForest vs HistGradientBoosting, elegidos por MAE).
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
| ¿Qué tipo de regresión? | **Regresión sobre series de tiempo** con **ensembles de árboles** (RandomForest e HistGradientBoosting de scikit-learn). |
| ¿Variable objetivo? | `volumen_citas_total`: citas por día, de una especialidad o de un médico. |
| ¿Variables explicativas? | Características derivadas solo de la fecha: mes, día de la semana, día del mes, semana y día del año, si es festivo, si es laborable y el factor de lluvias del mes en días laborables. |

### Paso a paso

1. **Se toma la historia.** Para una especialidad es la columna `volumen_citas_total` del CSV; para un médico, sus
   citas diarias en `citas_por_medico.csv`. Son unos 1.000 días, desde enero de 2024.
2. **Se convierten las fechas en características.** De cada fecha se calculan 8 variables numéricas (mes, día de la
   semana, día del mes, semana del año, día del año, festivo, laborable y lluvias en día laborable). Así el modelo
   "ve" números y puede aplicarlos igual al pasado que al futuro.
3. **Se hace un split temporal.** Los últimos 60 días se esconden como test (no aleatorio: en series de tiempo el
   pasado predice el futuro, y mezclar días filtraría información del futuro al entrenamiento).
4. **Se entrenan dos modelos y se elige el mejor.** RandomForest e HistGradientBoosting aprenden con el train; se mide
   el MAE de cada uno sobre los 60 días escondidos y se elige el que menos se equivoca.
5. **Se reentrena el ganador con todo el historial** y se proyectan los próximos días (hasta 180).
6. **Se calcula el margen.** Con los residuos del test se estima la desviación σ (por separado para días laborables y
   no laborables) y el margen es ±1,28·σ, que corresponde al intervalo del 80 %.
7. **Se entrega.** La API devuelve, por cada día, el valor proyectado y el margen, y el panel del médico lo dibuja.

### Las características (feature engineering)

Todas salen de la fecha, así que se conocen de antemano para cualquier día futuro:

| Característica | Qué captura |
|---|---|
| `mes`, `semana_anio`, `dia_anio` | La estacionalidad anual (picos de lluvias de agosto–noviembre) |
| `dia_semana`, `laborable`, `es_festivo` | El patrón semanal y la caída de fines de semana y festivos |
| `dia_mes` | El efecto del día dentro del mes |
| `lluvias_laborable` | El factor de lluvia del mes, solo en días laborables (en fines de semana casi no hay citas, llueva o no) |

### Cómo funcionan los modelos por dentro

Ambos candidatos son **ensembles de árboles de decisión**: combinan muchos árboles para reducir el error y el sobreajuste.

| Modelo | Cómo aprende |
|---|---|
| **RandomForest** | Entrena 300 árboles, cada uno con una muestra aleatoria de los datos y un subconjunto de características, y promedia sus predicciones. |
| **HistGradientBoosting** | Entrena árboles en secuencia (hasta 300 iteraciones): cada árbol nuevo corrige el error del anterior. Internamente agrupa las características en histogramas, por eso entrena más rápido. |

**Selección por MAE.** En cada serie se entrena a ambos con el train y se evalúa su **MAE** sobre el test temporal; gana
el que se equivoca menos. Ese ganador se **reentrena con todo el historial** antes de proyectar.

**Margen de error.** Con los residuos del test se calcula la desviación estándar σ, por separado para días laborables y
no laborables (así los fines de semana y festivos no heredan el margen grande de los días hábiles). El margen es
**±1,28·σ**, que corresponde al intervalo del **80 %**; si un grupo tiene pocas muestras se usa la σ global. Los valores
se redondean a citas enteras y los negativos se llevan a 0 (no existen "−1 citas").

Configuración usada en `api_predictiva.py`:

```python
modelos_candidatos = {
    'RandomForest': RandomForestRegressor(n_estimators=300, min_samples_leaf=2, random_state=42, n_jobs=-1),
    'HistGradientBoosting': HistGradientBoostingRegressor(max_iter=300, learning_rate=0.05, max_depth=6, random_state=42),
}
```

### Ejemplo con números reales (Neumología)

Con las características de calendario y lluvia, el modelo aprende el efecto de las reglas de Cartagena: un miércoles
de octubre (hábil, lluvia fuerte) se proyecta cerca de 14 citas, un sábado cae casi a cero y un miércoles de febrero
(temporada seca) queda alrededor de 4 citas. Es decir, el mismo día de la semana cambia según la temporada y los
fines de semana casi no tienen citas, llueva o no.

### ¿Por qué estos modelos y no otra técnica?

- Los **árboles de decisión** manejan bien los datos diarios con patrones semanales y anuales codificados como
  características, que es justo el caso de las citas.
- Aceptan las **variables de calendario y lluvia** como cualquier otra columna numérica.
- Permiten **elegir el mejor entre dos modelos** midiendo el MAE en un split temporal, sin caja negra.
- Entrenan en segundos con unos pocos años de datos, así que se puede entrenar un modelo **por médico** cuando se pide.

| Alternativa | Por qué no se usó |
|---|---|
| Clustering (K-means, etc.) | Es descriptivo: agrupa elementos parecidos, pero no dice **cuántas** citas habrá. Serviría para otra pregunta, por ejemplo agrupar pacientes por diagnóstico |
| Clasificación (árboles, etc.) | Predice categorías ("alta / baja demanda"), no la cantidad exacta que necesita el médico para su agenda |
| Regresión lineal simple | No captura bien los patrones semanales y anuales ni da un margen de error por día |
| ARIMA / SARIMA | También sirve para series de tiempo, pero maneja peor varias estacionalidades a la vez, los festivos y los días sin citas, y requiere más ajuste manual |
| Redes neuronales (LSTM) | Necesitan muchos más datos y son difíciles de interpretar; para unos 1.000 días no compensan |

### Qué tan bien predice

**Método de evaluación (split temporal / holdout):** se entrena con todo el historial menos los últimos 60 días; esos
60 días se esconden y se comparan con lo que predice cada candidato. La métrica es el **MAE (error absoluto medio)**:
en promedio, de cuántas citas fue la diferencia por día. Se comparan **RandomForest**, **HistGradientBoosting** y un
**baseline ingenuo** (el promedio del train por día de la semana); gana el de menor MAE y es el que se usa para
proyectar.

> **Referencia histórica (Prophet):** Medicina General 4,20 citas/día, Neumología 1,85 y Dra. Daniela Ramos
> (Pediatría) 1,00 en los últimos 60 días. Este enfoque anterior se reemplazó por scikit-learn, que entrena y evalúa
> de forma explícita.

Para ponerlo en contexto: en esos 60 días (temporada de lluvias) Medicina General recibió unas 28 citas por día hábil,
así que equivocarse en ~4 citas es un error de alrededor del 15 %, suficiente para planear la agenda.

### Metodología (fases de CRISP-DM)

| Fase | Qué se hizo en BESalud |
|---|---|
| 1. Comprensión del negocio | Los médicos no saben con anticipación qué días se les va a llenar la agenda; se busca pronosticar la demanda por especialidad y por médico |
| 2. Comprensión de los datos | Variables del dataset: fecha, día de la semana, especialidad, festivos, temporada de lluvias, alertas de salud pública y volumen de citas |
| 3. Preparación de los datos | Agregación de las citas por día; creación de `laborable` y `lluvias_laborable`; serie por médico en `citas_por_medico.csv`; días sin citas completados con 0 |
| 4. Modelado | Características de calendario/lluvia + split temporal; RandomForest vs HistGradientBoosting elegidos por MAE; un modelo por especialidad y por médico |
| 5. Evaluación | Holdout de 60 días con MAE; comparación entre RandomForest, HistGradientBoosting y un baseline ingenuo |
| 6. Despliegue | API en FastAPI publicada en Railway, consumida por Spring Boot y mostrada en el panel del médico |

### El margen

Además del valor proyectado, el modelo da un **rango probable** (`margenMinimo` – `margenMaximo`) calculado como ±1,28·σ de los residuos del test: la demanda real debería caer dentro de ese rango **8 de cada 10 veces**.

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

### El archivo del modelo entrenado

`modelos_entrenados.joblib` (~21 MB) guarda los 19 modelos ya entrenados (5 especialidades y 14 médicos): el
modelo ganador de cada serie, sus MAE de test y la σ de los márgenes. Lo genera `entrenar_modelos.py`, que es el
entrenamiento explícito (split de 60 días, comparación de los dos modelos y MAE):

```bash
cd modelo_predictivo
python entrenar_modelos.py    # entrena todo, imprime el MAE de cada serie y escribe el .joblib (~1 min)
```

- La API carga el archivo al arrancar, así la primera consulta ya no espera al entrenamiento.
- Si el archivo falta, está dañado o se entrenó con otra versión de scikit-learn, la API entrena al vuelo como
  antes. Lo mismo si el CSV tiene días más nuevos que los del archivo.
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
> supervisada**: hacemos **regresión sobre series de tiempo** con **ensembles de árboles** (RandomForest vs
> HistGradientBoosting), que aprenden de características de calendario y lluvia. Elegimos el mejor midiendo el MAE en
> los últimos 60 días escondidos. El médico ve el pronóstico de 7, 14 o 30 días en su panel, comparado con los cupos
> de su horario, para saber qué días abrir más franjas."

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

**¿El modelo "hace trampa" porque las reglas las pusimos nosotros?** El modelo **no conoce** las reglas: solo ve fechas y
cantidades de citas, y tiene que descubrir por su cuenta que octubre se llena y que el sábado no. Además, el ruido
aleatorio (Poisson) hace que nunca acierte exacto, como se ve en el MAE.

**¿Qué significa el margen?** Es el intervalo del 80 %: de cada 10 días, en unos 8 la demanda real debería quedar dentro
de ese rango.

**¿Cómo saben que funciona?** Por la validación *holdout*: se escondieron 60 días, se predijeron y se midió el error con MAE.

**¿Qué pasa si un médico nuevo no tiene historial?** Ve el mensaje "Aún no hay suficiente historial". Se podría usar el
pronóstico de su especialidad dividido entre los médicos mientras acumula citas.

**¿Cómo se mejoraría?** Con datos reales; actualizando el historial automáticamente con las citas nuevas; agregando el
día libre de cada médico como variable; y usando datos reales de lluvia del IDEAM en vez de un factor fijo por mes.

---

## 9. Limitaciones

- Los datos son **simulados**: el modelo aprende las reglas con que se generaron, no el comportamiento real de una clínica.
- El historial termina el 15 de octubre de 2026 y **no se actualiza solo** con las citas nuevas de la app. Para incorporarlas habría que exportar las citas de MongoDB al CSV y volver a desplegar.
- Solo los 14 médicos simulados tienen pronóstico propio; los demás ven el mensaje de "sin historial".
- El pronóstico de un médico todavía puede marcar 1 o 2 citas en su día libre, porque el modelo no sabe cuál es. Se podría afinar agregando ese día como regresor.
