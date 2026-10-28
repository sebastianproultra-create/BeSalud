"""Entrena y guarda los modelos de BESalud: una especialidad o un médico = un modelo.

Uso (desde esta carpeta):  python entrenar_modelos.py
Genera modelos_entrenados.joblib, que api_predictiva.py carga al arrancar.
Hay que repetirlo y commitear el archivo si cambian los CSV o el código del modelo.
"""
import joblib
import sklearn

import api_predictiva as api


def main():
    modelos = {}
    for nombre in sorted(api.ESPECIALIDADES.values()):
        modelos[api.clave_de(nombre, None)] = api.entrenar(api.serie_de(nombre, None), nombre)
    for medico_id in sorted(api.df_medicos['medico_id'].unique()):
        modelos[api.clave_de(None, medico_id)] = api.entrenar(api.serie_de(None, medico_id), f'medico {medico_id}')

    joblib.dump({'sklearn': sklearn.__version__, 'modelos': modelos}, api.ARCHIVO_MODELOS, compress=3)

    print(f'\n{len(modelos)} modelos guardados en {api.ARCHIVO_MODELOS.name} '
          f'({api.ARCHIVO_MODELOS.stat().st_size / 1e6:.1f} MB)')
    for clave, m in modelos.items():
        mae = m['mae']
        print(f"{clave:55s} MAE baseline={mae['baseline']:.3f}  RF={mae['RandomForest']:.3f}  "
              f"HGB={mae['HistGradientBoosting']:.3f}  -> {m['ganador']}")


if __name__ == '__main__':
    main()
