"""Genera el dataset agregado de demanda: una fila por día y especialidad, con la variable objetivo
volumen_citas_total. Reemplaza al script_base.py original.

Uso:
    python generar_dataset.py                 # desde 2024-01-01 hasta ayer
    python generar_dataset.py --hasta 2026-10-15
"""
import argparse
from datetime import date, timedelta

import holidays
import numpy as np
import pandas as pd

from catalogo_clinico import CUADROS
from config_simulacion import (DEMANDA_BASE, EFECTO_ALERTA, ESPECIALIDADES, factor_lluvias,
                               multiplicador_lluvia)

SEMILLA = 42
DESDE = date(2024, 1, 1)
ARCHIVO = 'dataset_citas_medicas_cartagena.csv'


def alertas_salud_publica(rng, desde, hasta):
    """Brotes de IRA o dengue de 2 a 4 semanas, casi siempre en temporada de lluvias."""
    dias = set()
    for anio in range(desde.year, hasta.year + 1):
        for mes_inicio in (5, 9):
            if rng.random() < 0.8:
                inicio = date(anio, mes_inicio, 1) + timedelta(days=int(rng.integers(0, 50)))
                for d in range(int(rng.integers(14, 29))):
                    dias.add(inicio + timedelta(days=d))
    return dias


def motivo_predominante(especialidad, mes, alerta):
    """Motivo del cuadro más frecuente para esa especialidad, época y situación de alerta."""
    def peso(c):
        p = c.peso * (factor_lluvias(mes) if c.lluvia else 1)
        return p * (2 if alerta and c.alerta else 1)
    return max(CUADROS[especialidad], key=peso).motivos[0]


def generar(hasta):
    rng = np.random.default_rng(SEMILLA)
    festivos = holidays.CO(years=range(DESDE.year, hasta.year + 1))
    alertas = alertas_salud_publica(rng, DESDE, hasta)

    filas = []
    for fecha in pd.date_range(DESDE, hasta, freq='D').date:
        festivo = fecha in festivos
        alerta = fecha in alertas
        for esp in ESPECIALIDADES:
            if fecha.weekday() >= 5 or festivo:
                volumen = int(rng.integers(0, 3))  # fines de semana y festivos: casi sin citas
            else:
                media = DEMANDA_BASE[esp] * multiplicador_lluvia(esp, fecha.month)
                if alerta:
                    media *= EFECTO_ALERTA.get(esp, 1)
                volumen = int(rng.poisson(media))
            filas.append({
                'fecha': fecha,
                'año': fecha.year,
                'mes': fecha.month,
                'dia_semana': fecha.weekday(),  # 0 = lunes, 6 = domingo
                'especialidad_medica': esp,
                'motivo_consulta': motivo_predominante(esp, fecha.month, alerta),
                'es_festivo': int(festivo),
                'factor_estacional_lluvias': factor_lluvias(fecha.month),
                'alertas_salud_publica': int(alerta),
                'volumen_citas_total': volumen,
            })

    df = pd.DataFrame(filas)
    # Lag semanal: volumen de la misma especialidad el mismo día de la semana anterior.
    df['volumen_citas_pasadas'] = (df.groupby('especialidad_medica')['volumen_citas_total']
                                   .shift(7).astype('Int64'))
    columnas = ['fecha', 'año', 'mes', 'dia_semana', 'especialidad_medica', 'motivo_consulta', 'es_festivo',
                'factor_estacional_lluvias', 'alertas_salud_publica', 'volumen_citas_pasadas',
                'volumen_citas_total']
    return df[columnas]


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--hasta', type=date.fromisoformat, default=date.today() - timedelta(days=1))
    args = parser.parse_args()

    df = generar(args.hasta)
    df.to_csv(ARCHIVO, index=False)
    print(f'{ARCHIVO}: {len(df)} filas, {df["volumen_citas_total"].sum()} citas '
          f'({df["fecha"].min()} a {df["fecha"].max()})')
    print(df.groupby('especialidad_medica')['volumen_citas_total'].agg(['sum', 'mean', 'max']))
