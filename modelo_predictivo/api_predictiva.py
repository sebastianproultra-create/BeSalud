"""API predictiva de BESalud: pronostica el volumen de citas con Prophet.

GET /prediccion/{especialidad}?dias=30                 -> demanda de toda la especialidad
GET /prediccion/{especialidad}?dias=30&medico_id=...   -> agenda proyectada de un médico
GET /medicos?especialidad=...                          -> médicos con historial
GET /salud

Local:   uvicorn api_predictiva:app --port 8000
Railway: ver Procfile.
"""
import unicodedata
from functools import lru_cache
from pathlib import Path
from typing import Optional

import holidays
import pandas as pd
from fastapi import FastAPI, HTTPException, Query
from prophet import Prophet

from config_simulacion import factor_lluvias

RAIZ = Path(__file__).resolve().parent
MAX_DIAS = 180

app = FastAPI(title='API Predictiva BESalud')

df_especialidades = pd.read_csv(RAIZ / 'dataset_citas_medicas_cartagena.csv', parse_dates=['fecha'])
df_medicos = pd.read_csv(RAIZ / 'citas_por_medico.csv', parse_dates=['fecha'], dtype={'medico_id': str})


def normalizar(texto):
    """'pediatria', 'Pediatría' y 'PEDIATRÍA' son la misma especialidad."""
    sin_tildes = unicodedata.normalize('NFD', texto)
    return ''.join(c for c in sin_tildes if unicodedata.category(c) != 'Mn').strip().lower()


ESPECIALIDADES = {normalizar(e): e for e in df_especialidades['especialidad_medica'].unique()}


FESTIVOS = holidays.CO(years=range(2024, pd.Timestamp.today().year + 2))


def con_regresores(df):
    """Regresores conocidos de antemano: si el día es hábil y el factor de lluvias de ese mes.
    La lluvia solo cuenta en días hábiles: fines de semana y festivos casi no tienen citas en ninguna época."""
    df = df.copy()
    df['laborable'] = [int(d.dayofweek < 5 and d.date() not in FESTIVOS) for d in df['ds']]
    df['lluvias_laborable'] = df['ds'].dt.month.map(factor_lluvias) * df['laborable']
    return df


@lru_cache(maxsize=64)
def pronosticar(especialidad, medico_id):
    """Entrena una vez por especialidad/médico y guarda el pronóstico de MAX_DIAS días."""
    if medico_id:
        serie = df_medicos[df_medicos['medico_id'] == medico_id]
        serie = serie.rename(columns={'fecha': 'ds', 'volumen_citas': 'y'})
    else:
        serie = df_especialidades[df_especialidades['especialidad_medica'] == especialidad]
        serie = serie.rename(columns={'fecha': 'ds', 'volumen_citas_total': 'y'})

    modelo = Prophet(yearly_seasonality=True, weekly_seasonality=True, daily_seasonality=False)
    # Con estos regresores el error medio de los últimos 60 días bajó ~25 % frente a solo festivos y lluvia.
    modelo.add_regressor('laborable')
    modelo.add_regressor('lluvias_laborable')
    modelo.fit(con_regresores(serie[['ds', 'y']]))

    futuro = con_regresores(modelo.make_future_dataframe(periods=MAX_DIAS))
    return modelo.predict(futuro)[['ds', 'yhat', 'yhat_lower', 'yhat_upper']].tail(MAX_DIAS)


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
    # Prophet puede dar valores negativos en días sin atención; se limitan a 0.
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
