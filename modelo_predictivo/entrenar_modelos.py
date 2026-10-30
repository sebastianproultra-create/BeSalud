"""Entrena y guarda los modelos de BESalud: una especialidad o un médico = un modelo.

Uso (desde esta carpeta):  python entrenar_modelos.py
Genera modelos_entrenados.joblib, que api_predictiva.py carga al arrancar.
Hay que repetirlo y commitear el archivo si cambian los CSV o el código del modelo;
si se te olvida, la API lo detecta (huellas), avisa en el log y en /salud, y entrena al vuelo.
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

    joblib.dump({'sklearn': sklearn.__version__, 'huella_codigo': api.huella_codigo(), 'modelos': modelos}, api.ARCHIVO_MODELOS, compress=3)

    print(f'\n{len(modelos)} modelos guardados en {api.ARCHIVO_MODELOS.name} '
          f'({api.ARCHIVO_MODELOS.stat().st_size / 1e6:.1f} MB)')
    print(f'MAE medio ± desviación en {api.CORTES} cortes de {api.DIAS_TEST} días; cobertura del rango en días hábiles')
    for clave, m in modelos.items():
        mae, desv = m['mae'], m['mae_desv']
        detalle = '  '.join(f'{nombre}={mae[nombre]:.2f}±{desv[nombre]:.2f}' for nombre in mae)
        print(f"{clave:40s} {detalle}  -> {m['ganador']}  "
              f"rango p={m['p_intervalo']:.3f} cobertura={m['cobertura_habiles']:.0%} "
              f"(cortes {min(m['cobertura_cortes']):.0%}-{max(m['cobertura_cortes']):.0%})")
    ganadores = [m['mae'][m['ganador']] for m in modelos.values()]
    baselines = [m['mae']['baseline'] for m in modelos.values()]
    coberturas = [m['cobertura_habiles'] for m in modelos.values()]
    print(f'\nPromedio: MAE ganador={sum(ganadores) / len(ganadores):.3f}  baseline={sum(baselines) / len(baselines):.3f}  '
          f'cobertura={sum(coberturas) / len(coberturas):.1%}  '
          f'gana al baseline en {sum(g < b for g, b in zip(ganadores, baselines))}/{len(modelos)}')


if __name__ == '__main__':
    main()
