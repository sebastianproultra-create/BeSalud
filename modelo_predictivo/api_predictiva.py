"""API predictiva de BESalud: pronostica el volumen de citas con scikit-learn.

GET /prediccion/{especialidad}?dias=30                 -> demanda de toda la especialidad
GET /prediccion/{especialidad}?dias=30&medico_id=...   -> agenda proyectada de un médico
GET /medicos?especialidad=...                          -> médicos con historial
GET /salud

Local:   uvicorn api_predictiva:app --port 8000
Railway: ver Procfile.
"""
import logging
import unicodedata
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
from sklearn.ensemble import HistGradientBoostingRegressor, RandomForestRegressor
from sklearn.metrics import mean_absolute_error

from config_simulacion import factor_lluvias

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger('api_predictiva')

RAIZ = Path(__file__).resolve().parent
MAX_DIAS = 180
ARCHIVO_MODELOS = RAIZ / 'modelos_entrenados.joblib'
DIAS_TEST = 60      # el test son los últimos 60 días (split temporal, NO aleatorio)
Z_80 = 1.28         # z del intervalo de 80 %

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
    """Variables numéricas calculadas solo desde la fecha."""
    f = pd.DatetimeIndex(fechas)
    laborable = np.array([int(d.dayofweek < 5 and d.date() not in FESTIVOS) for d in f])
    return pd.DataFrame({
        'mes': f.month,
        'dia_semana': f.dayofweek,
        'dia_mes': f.day,
        'semana_anio': f.isocalendar().week.astype(int).to_numpy(),
        'dia_anio': f.dayofyear,
        'es_festivo': [int(d.date() in FESTIVOS) for d in f],
        'laborable': laborable,
        'lluvias_laborable': np.array([factor_lluvias(m) for m in f.month]) * laborable,
    })


def modelos_candidatos():
    return {
        'RandomForest': RandomForestRegressor(n_estimators=300, min_samples_leaf=2, random_state=42, n_jobs=-1),
        'HistGradientBoosting': HistGradientBoostingRegressor(max_iter=300, learning_rate=0.05, max_depth=6, random_state=42),
    }


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


def entrenar(serie, etiqueta):
    """Entrena los candidatos, elige el de menor MAE en test y devuelve el artefacto guardable.

    Split temporal: se entrena con todo menos los últimos `dias_test` días y se evalúa sobre
    esos días escondidos (no aleatorio, para no filtrar el futuro). Entre RandomForest e
    HistGradientBoosting se elige el de menor MAE en test; los márgenes son ±1.28·σ de los
    residuos del test (≈ intervalo del 80 %), con σ distinta para días laborables y no laborables.
    """
    X = caracteristicas(serie['ds'])
    y = serie['y'].to_numpy()

    # El test son los últimos días: en series de tiempo el pasado predice el futuro, no al revés.
    dias_test = min(DIAS_TEST, len(serie) // 4)
    corte = len(serie) - dias_test
    X_train, y_train = X.iloc[:corte], y[:corte]
    X_test, y_test = X.iloc[corte:], y[corte:]
    laborable_test = X_test['laborable'].to_numpy()

    # Baseline ingenuo: el promedio de citas del train por día de la semana, aplicado al test.
    media_por_dia = X_train.assign(y=y_train).groupby('dia_semana')['y'].mean()
    baseline = X_test['dia_semana'].map(media_por_dia).fillna(y_train.mean()).to_numpy()
    mae_baseline = mean_absolute_error(y_test, baseline)

    candidatos = modelos_candidatos()
    maes = {}
    predicciones = {}
    for nombre, modelo in candidatos.items():
        modelo.fit(X_train, y_train)
        predicciones[nombre] = modelo.predict(X_test)
        maes[nombre] = mean_absolute_error(y_test, predicciones[nombre])
    ganador = min(maes, key=maes.get)

    logger.info(
        'entrenar %s train=%d test=%d | MAE baseline=%.3f | RandomForest=%.3f '
        'HistGradientBoosting=%.3f | ganador=%s',
        etiqueta, len(y_train), len(y_test), mae_baseline,
        maes['RandomForest'], maes['HistGradientBoosting'], ganador,
    )

    # Residuos del ganador en test; σ por grupo para que fines de semana/festivos no
    # hereden un margen enorme de los días hábiles.
    res = y_test - predicciones[ganador]
    sigmas = {}
    for grupo, valor in (('laborable', 1), ('no_laborable', 0)):
        mascara = laborable_test == valor
        sigma = res[mascara].std() if mascara.sum() >= 2 else res.std()
        sigmas[grupo] = 0.0 if np.isnan(sigma) else float(sigma)

    # Reentrenar el ganador con TODO el histórico.
    modelo_final = clone(candidatos[ganador])
    modelo_final.fit(X, y)

    return {
        'modelo': modelo_final,
        'ganador': ganador,
        'mae': {**maes, 'baseline': mae_baseline},
        'sigma_laborable': sigmas['laborable'],
        'sigma_no_laborable': sigmas['no_laborable'],
        'ultima_fecha': serie['ds'].iloc[-1],
        'dias_train': len(y_train),
        'dias_test': len(y_test),
    }


def cargar_modelos():
    """Modelos entrenados con entrenar_modelos.py; si el archivo no sirve, se entrena al vuelo."""
    if not ARCHIVO_MODELOS.exists():
        return {}
    try:
        guardado = joblib.load(ARCHIVO_MODELOS)
        if guardado.get('sklearn') != sklearn.__version__:
            raise ValueError(f"entrenado con scikit-learn {guardado.get('sklearn')}, hay {sklearn.__version__}")
        return guardado['modelos']
    except Exception as error:  # archivo dañado o de otra versión: no bloquea la API
        logger.warning('No se pudo usar %s (%s); se entrena al vuelo.', ARCHIVO_MODELOS.name, error)
        return {}


MODELOS_GUARDADOS = cargar_modelos()


@lru_cache(maxsize=64)
def pronosticar(especialidad, medico_id):
    """Pronostica MAX_DIAS días futuros (desde el día siguiente al último dato)."""
    serie = serie_de(especialidad, medico_id)
    clave = clave_de(especialidad, medico_id)
    artefacto = MODELOS_GUARDADOS.get(clave)
    # Si el CSV tiene datos más nuevos que los del entrenamiento guardado, se reentrena.
    if artefacto is None or artefacto['ultima_fecha'] != serie['ds'].iloc[-1]:
        artefacto = entrenar(serie, clave)

    fechas_fut = pd.date_range(artefacto['ultima_fecha'] + pd.Timedelta(days=1), periods=MAX_DIAS)
    X_fut = caracteristicas(fechas_fut)
    sigma_fut = np.where(X_fut['laborable'].to_numpy() == 1,
                         artefacto['sigma_laborable'], artefacto['sigma_no_laborable'])
    yhat = np.maximum(0, artefacto['modelo'].predict(X_fut))
    yhat_lower = np.maximum(0, yhat - Z_80 * sigma_fut)
    yhat_upper = yhat + Z_80 * sigma_fut

    return pd.DataFrame({'ds': fechas_fut, 'yhat': yhat, 'yhat_lower': yhat_lower, 'yhat_upper': yhat_upper})


@app.get('/salud')
def salud():
    return {'estado': 'ok', 'especialidades': sorted(ESPECIALIDADES.values())}


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
    if medico_id:
        filas = df_medicos[df_medicos['medico_id'] == medico_id]
        if filas.empty:
            raise HTTPException(status_code=404, detail='Médico sin historial de citas.')
        if filas['especialidad_medica'].iloc[0] != nombre:
            raise HTTPException(status_code=400, detail='El médico no pertenece a esa especialidad.')
        medico_nombre = filas['medico_nombre'].iloc[0]

    resultados = pronosticar(nombre, medico_id).head(dias)
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
        'dias_proyectados': dias,
        'predicciones': predicciones,
    }


if __name__ == '__main__':
    import uvicorn
    uvicorn.run(app, host='0.0.0.0', port=8000)
