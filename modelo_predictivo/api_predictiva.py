"""API predictiva de BESalud: pronostica el volumen de citas con scikit-learn.

GET /prediccion/{especialidad}?dias=30                 -> demanda de toda la especialidad
GET /prediccion/{especialidad}?dias=30&medico_id=...   -> agenda proyectada de un médico (sin historial:
                                                          promedio por médico de su especialidad, respaldo=true)
GET /medicos?especialidad=...                          -> médicos con historial
GET /salud

Local:   uvicorn api_predictiva:app --port 8000
Railway: ver Procfile.
"""
import hashlib
import inspect
import logging
import unicodedata
from datetime import datetime, timedelta, timezone
from functools import lru_cache
from pathlib import Path
from typing import Optional

import holidays
import joblib
import numpy as np
import pandas as pd
import sklearn
from fastapi import FastAPI, HTTPException, Query
from sklearn.base import clone
from sklearn.ensemble import HistGradientBoostingRegressor
from sklearn.metrics import mean_absolute_error

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger('api_predictiva')

RAIZ = Path(__file__).resolve().parent
MAX_DIAS = 180
ARCHIVO_MODELOS = RAIZ / 'modelos_entrenados.joblib'
CORTES = 6          # validación rolling-origin: 6 cortes de 60 días, el último año del historial
DIAS_TEST = 60      # días de cada corte (split temporal, NO aleatorio)
# Cuantiles 15/85 de los residuos: como las citas son enteras y el rango se redondea, así cubre
# ≈80 % de los días hábiles (medido fuera de muestra, ver README).
P_INTERVALO = 0.15
COLOMBIA = timezone(timedelta(hours=-5))

app = FastAPI(title='API Predictiva BESalud')

df_especialidades = pd.read_csv(RAIZ / 'dataset_citas_medicas_cartagena.csv', parse_dates=['fecha'])
df_medicos = pd.read_csv(RAIZ / 'citas_por_medico.csv', parse_dates=['fecha'], dtype={'medico_id': str})


def normalizar(texto):
    """'pediatria', 'Pediatría' y 'PEDIATRÍA' son la misma especialidad."""
    sin_tildes = unicodedata.normalize('NFD', texto)
    return ''.join(c for c in sin_tildes if unicodedata.category(c) != 'Mn').strip().lower()


ESPECIALIDADES = {normalizar(e): e for e in df_especialidades['especialidad_medica'].unique()}


FESTIVOS = holidays.CO(years=range(2024, pd.Timestamp.today().year + 2))


def caracteristicas(fechas):
    """Variables numéricas calculadas solo desde el calendario (sirven igual para el pasado y el futuro).

    `mes` captura la temporada de lluvias. Se probaron también dia_mes, semana_anio, dia_anio y un
    factor de lluvias por mes: no mejoraban el MAE y las de año permitían memorizar fechas (ver README).
    """
    f = pd.DatetimeIndex(fechas)
    return pd.DataFrame({
        'mes': f.month,
        'dia_semana': f.dayofweek,
        'es_festivo': [int(d.date() in FESTIVOS) for d in f],
        'laborable': [int(d.dayofweek < 5 and d.date() not in FESTIVOS) for d in f],
    })


def modelos_candidatos():
    """Mismo algoritmo con dos funciones de pérdida: error cuadrático y Poisson (la natural para conteos)."""
    return {
        'HistGradientBoosting': HistGradientBoostingRegressor(max_iter=300, learning_rate=0.05, max_depth=6, random_state=42),
        'HistGradientBoosting Poisson': HistGradientBoostingRegressor(loss='poisson', max_iter=300, learning_rate=0.05,
                                                                      max_depth=6, random_state=42),
    }


def huella_codigo():
    """Huella de todo lo que define el modelo: si cambia cualquiera, el .joblib quedó viejo."""
    partes = [sklearn.__version__, inspect.getsource(caracteristicas), inspect.getsource(entrenar),
              repr(modelos_candidatos()), str(CORTES), str(DIAS_TEST), str(P_INTERVALO)]
    return hashlib.sha256('|'.join(partes).encode('utf-8')).hexdigest()


def huella_datos(serie):
    """Huella de la serie completa: detecta cambios en cualquier fila del CSV, no solo en la última fecha."""
    return hashlib.sha256(pd.util.hash_pandas_object(serie, index=False).to_numpy().tobytes()).hexdigest()


def serie_de(especialidad, medico_id):
    """Serie diaria (ds, y) de un médico o de toda la especialidad, ordenada por fecha."""
    if medico_id:
        serie = df_medicos[df_medicos['medico_id'] == medico_id]
        serie = serie.rename(columns={'fecha': 'ds', 'volumen_citas': 'y'})
    else:
        serie = df_especialidades[df_especialidades['especialidad_medica'] == especialidad]
        serie = serie.rename(columns={'fecha': 'ds', 'volumen_citas_total': 'y'})
    return serie[['ds', 'y']].sort_values('ds').reset_index(drop=True)


def clave_de(especialidad, medico_id):
    return f'medico:{medico_id}' if medico_id else f'especialidad:{especialidad}'


def cuantiles_por_grupo(y, yhat, laborable):
    """Cuantiles (P, 1-P) del residuo relativo (y - ŷ)/(ŷ + 1), aparte para días laborables y no laborables.

    Dividir entre ŷ+1 hace que el margen crezca con la demanda: con conteos la variación sube con la
    media, así que octubre (más citas) necesita un rango más ancho que febrero.
    """
    r = (y - yhat) / (yhat + 1)
    cuantiles = {}
    for grupo, valor in (('laborable', 1), ('no_laborable', 0)):
        rg = r[laborable == valor]
        lo, hi = np.quantile(rg if len(rg) >= 10 else r, [P_INTERVALO, 1 - P_INTERVALO])
        cuantiles[grupo] = (float(lo), float(hi))
    return cuantiles


def rango(yhat, laborable, cuantiles):
    """Rango probable (mínimo, máximo) de cada día a partir de los cuantiles del residuo relativo."""
    lo = np.where(laborable == 1, cuantiles['laborable'][0], cuantiles['no_laborable'][0])
    hi = np.where(laborable == 1, cuantiles['laborable'][1], cuantiles['no_laborable'][1])
    return np.maximum(0, yhat + lo * (yhat + 1)), yhat + hi * (yhat + 1)


def entrenar(serie, etiqueta):
    """Valida con varios cortes, elige el candidato de menor MAE medio y devuelve el artefacto guardable.

    Rolling-origin: en cada uno de los CORTES se entrena con todo lo anterior al corte y se evalúa en
    los DIAS_TEST días siguientes (nunca se usa el futuro para predecir el pasado). Los cortes cubren
    el último año, así el MAE mezcla temporada seca y de lluvias en vez de depender de un solo tramo.
    Con los residuos fuera de muestra del ganador se calibra el rango probable; su cobertura se mide
    dejando cada corte fuera (se calibra con los otros cortes y se evalúa en ese).
    """
    X = caracteristicas(serie['ds'])
    y = serie['y'].to_numpy()
    laborable = X['laborable'].to_numpy()
    n = len(y)
    dias_test = min(DIAS_TEST, n // (CORTES + 1))
    inicios = [n - k * dias_test for k in range(CORTES, 0, -1)]

    candidatos = modelos_candidatos()
    maes = {nombre: [] for nombre in [*candidatos, 'baseline']}
    fuera_de_muestra = {nombre: [] for nombre in candidatos}
    for ini in inicios:
        fin = ini + dias_test
        X_train, y_train, X_test, y_test = X.iloc[:ini], y[:ini], X.iloc[ini:fin], y[ini:fin]
        # Baseline ingenuo: el promedio de citas del train por día de la semana.
        media_por_dia = X_train.assign(y=y_train).groupby('dia_semana')['y'].mean()
        baseline = X_test['dia_semana'].map(media_por_dia).fillna(y_train.mean()).to_numpy()
        maes['baseline'].append(mean_absolute_error(y_test, baseline))
        for nombre, modelo in candidatos.items():
            pred = np.maximum(0, clone(modelo).fit(X_train, y_train).predict(X_test))
            maes[nombre].append(mean_absolute_error(y_test, pred))
            fuera_de_muestra[nombre].append(pred)
    ganador = min(candidatos, key=lambda nombre: np.mean(maes[nombre]))

    # Rango probable con los residuos fuera de muestra del ganador.
    y_oof, lab_oof = y[inicios[0]:], laborable[inicios[0]:]
    yhat_oof = np.concatenate(fuera_de_muestra[ganador])
    cuantiles = cuantiles_por_grupo(y_oof, yhat_oof, lab_oof)

    # Cobertura medida sin trampa: cada corte se evalúa con cuantiles calibrados en los otros.
    corte_de = np.repeat(np.arange(CORTES), dias_test)
    cubre = np.zeros(len(y_oof), dtype=bool)
    for k in range(CORTES):
        fuera = corte_de == k
        q = cuantiles_por_grupo(y_oof[~fuera], yhat_oof[~fuera], lab_oof[~fuera])
        lo, hi = rango(yhat_oof[fuera], lab_oof[fuera], q)
        cubre[fuera] = (y_oof[fuera] >= np.round(lo)) & (y_oof[fuera] <= np.round(hi))
    cobertura = float(cubre[lab_oof == 1].mean()) if (lab_oof == 1).any() else float(cubre.mean())

    logger.info('entrenar %s | %d cortes de %d días | MAE medio: %s | ganador=%s | cobertura del rango=%.0f%%',
                etiqueta, CORTES, dias_test, ', '.join(f'{k}={np.mean(v):.3f}' for k, v in maes.items()),
                ganador, 100 * cobertura)

    return {
        'modelo': clone(candidatos[ganador]).fit(X, y),  # el ganador se reentrena con TODO el histórico
        'ganador': ganador,
        'mae': {nombre: float(np.mean(v)) for nombre, v in maes.items()},
        'mae_desv': {nombre: float(np.std(v)) for nombre, v in maes.items()},
        'cuantiles': cuantiles,
        'cobertura_habiles': cobertura,
        'ultima_fecha': serie['ds'].iloc[-1],
        'huella_codigo': huella_codigo(),
        'huella_datos': huella_datos(serie),
        'cortes': CORTES,
        'dias_test': dias_test,
    }


ESTADO_MODELOS = 'sin archivo: se entrena al vuelo'


def cargar_modelos():
    """Modelos entrenados con entrenar_modelos.py; si el archivo no sirve, se entrena al vuelo."""
    global ESTADO_MODELOS
    if not ARCHIVO_MODELOS.exists():
        return {}
    try:
        guardado = joblib.load(ARCHIVO_MODELOS)
        if guardado.get('sklearn') != sklearn.__version__:
            raise ValueError(f"entrenado con scikit-learn {guardado.get('sklearn')}, hay {sklearn.__version__}")
        if guardado.get('huella_codigo') != huella_codigo():
            raise ValueError('cambiaron las features, los hiperparámetros o el código del modelo')
        ESTADO_MODELOS = 'vigentes'
        return guardado['modelos']
    except Exception as error:  # archivo dañado o viejo: no bloquea la API
        ESTADO_MODELOS = f'obsoletos ({error}): se entrena al vuelo; ejecuta entrenar_modelos.py'
        logger.warning('No se pudo usar %s (%s); se entrena al vuelo.', ARCHIVO_MODELOS.name, error)
        return {}


MODELOS_GUARDADOS = cargar_modelos()


@lru_cache(maxsize=64)
def artefacto_de(especialidad, medico_id):
    """Modelo guardado de la serie; si no existe o el CSV cambió desde que se entrenó, se entrena al vuelo."""
    serie = serie_de(especialidad, medico_id)
    clave = clave_de(especialidad, medico_id)
    artefacto = MODELOS_GUARDADOS.get(clave)
    if artefacto is None or artefacto.get('huella_datos') != huella_datos(serie):
        artefacto = entrenar(serie, clave)
    return artefacto


def pronosticar(especialidad, medico_id, dias=MAX_DIAS):
    """Pronostica `dias` días desde mañana (hora de Colombia), aunque el historial termine antes:
    las características son solo de calendario, así que sirven para cualquier fecha futura."""
    artefacto = artefacto_de(especialidad, medico_id)
    manana = pd.Timestamp(datetime.now(COLOMBIA).date() + timedelta(days=1))
    fechas = pd.date_range(max(manana, artefacto['ultima_fecha'] + pd.Timedelta(days=1)), periods=dias)
    X_fut = caracteristicas(fechas)
    yhat = np.maximum(0, artefacto['modelo'].predict(X_fut))
    yhat_lower, yhat_upper = rango(yhat, X_fut['laborable'].to_numpy(), artefacto['cuantiles'])

    return pd.DataFrame({'ds': fechas, 'yhat': yhat, 'yhat_lower': yhat_lower, 'yhat_upper': yhat_upper})


@app.get('/salud')
def salud():
    return {'estado': 'ok', 'modelos': ESTADO_MODELOS, 'especialidades': sorted(ESPECIALIDADES.values())}


@app.get('/medicos')
def medicos(especialidad: Optional[str] = None):
    df = df_medicos[['medico_id', 'medico_nombre', 'especialidad_medica']].drop_duplicates()
    if especialidad:
        df = df[df['especialidad_medica'].map(normalizar) == normalizar(especialidad)]
    return df.rename(columns={'especialidad_medica': 'especialidad'}).to_dict(orient='records')


@app.get('/prediccion/{especialidad}')
def obtener_prediccion(especialidad: str, dias: int = Query(30, ge=1, le=MAX_DIAS),
                       medico_id: Optional[str] = None):
    nombre = ESPECIALIDADES.get(normalizar(especialidad))
    if nombre is None:
        raise HTTPException(status_code=404, detail='Especialidad no encontrada en el historial.')

    medico_nombre = None
    respaldo = False
    if medico_id:
        filas = df_medicos[df_medicos['medico_id'] == medico_id]
        if filas.empty:
            # Médico nuevo, sin historial: se usa el modelo ya entrenado de su especialidad.
            respaldo = True
        elif filas['especialidad_medica'].iloc[0] != nombre:
            raise HTTPException(status_code=400, detail='El médico no pertenece a esa especialidad.')
        else:
            medico_nombre = filas['medico_nombre'].iloc[0]

    resultados = pronosticar(nombre, None if respaldo else medico_id, dias)
    if respaldo:
        # El modelo de la especialidad pronostica el total de todos sus médicos: se reparte entre ellos
        # para mostrarle al médico nuevo la demanda de un médico promedio, no la de toda la especialidad.
        n_medicos = df_medicos.loc[df_medicos['especialidad_medica'] == nombre, 'medico_id'].nunique()
        resultados[['yhat', 'yhat_lower', 'yhat_upper']] /= max(1, n_medicos)
    predicciones = [{
        'fecha': fila.ds.strftime('%Y-%m-%d'),
        'volumenProyectado': max(0, int(round(fila.yhat))),
        'margenMinimo': max(0, int(round(fila.yhat_lower))),
        'margenMaximo': max(0, int(round(fila.yhat_upper))),
    } for fila in resultados.itertuples()]

    return {
        'especialidad': nombre,
        'medico_id': medico_id,
        'medico_nombre': medico_nombre,
        'respaldo': respaldo,
        'dias_proyectados': dias,
        'predicciones': predicciones,
    }


if __name__ == '__main__':
    import uvicorn
    uvicorn.run(app, host='0.0.0.0', port=8000)
