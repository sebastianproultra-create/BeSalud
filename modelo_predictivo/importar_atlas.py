"""Carga en MongoDB los médicos, pacientes, horarios y citas simulados que deja generar_citas.py en salida/.

Por defecto solo muestra qué hay en la base y qué haría (no escribe nada). Con --confirmar:
  1. Borra lo simulado de una corrida anterior: citas y horarios de los médicos @sim.besalud.com y las
     personas @sim.besalud.com.
  2. Con --borrar-mock-anterior, borra también las 42.144 citas del mock original (las que traen el campo SOAP).
  3. Inserta los documentos nuevos.
No toca a los usuarios reales ni sus citas.

La URI se lee de la variable MONGODB_URI (o SPRING_DATA_MONGODB_URI), o del archivo .env en la raíz del repo.
Uso:
    python importar_atlas.py
    python importar_atlas.py --confirmar --borrar-mock-anterior
"""
import argparse
import os
from pathlib import Path

from bson import json_util
from pymongo import MongoClient

RAIZ = Path(__file__).resolve().parent
SALIDA = RAIZ / 'salida'
FILTRO_SIM = {'email': {'$regex': r'@sim\.besalud\.com$'}}
FILTRO_MOCK = {'SOAP': {'$exists': True}}
LOTE = 2000


def leer_uri():
    for variable in ('MONGODB_URI', 'SPRING_DATA_MONGODB_URI'):
        if os.environ.get(variable):
            return os.environ[variable]
    env = RAIZ.parent / '.env'
    if env.exists():
        for linea in env.read_text(encoding='utf-8-sig').splitlines():
            if linea.strip().startswith(('mongodb://', 'mongodb+srv://')):
                return linea.strip()  # la URI sola, sin nombre de variable
            clave, _, valor = linea.partition('=')
            if clave.strip() in ('MONGODB_URI', 'SPRING_DATA_MONGODB_URI', 'spring.data.mongodb.uri') and valor.strip():
                return valor.strip().strip('"\'')
    raise SystemExit('Falta la URI: define MONGODB_URI o agrégala al .env de la raíz del repo.')


def leer_jsonl(nombre):
    with open(SALIDA / nombre, encoding='utf-8') as f:
        return [json_util.loads(linea) for linea in f]


def insertar(coleccion, docs):
    for i in range(0, len(docs), LOTE):
        coleccion.insert_many(docs[i:i + LOTE], ordered=False)
    print(f'  {coleccion.name}: {len(docs)} insertados')


def resumen(db):
    personas, citas = db.personas, db.citas
    por_doctor = list(citas.aggregate([{'$group': {'_id': '$doctorId', 'n': {'$sum': 1}}},
                                       {'$sort': {'n': -1}}, {'$limit': 5}]))
    print(f'Base: {db.name}')
    print(f'  Médicos: {personas.count_documents({"role": "DOCTOR"})} '
          f'(activos {personas.count_documents({"role": "DOCTOR", "estado": "ACTIVO"})}, '
          f'simulados {personas.count_documents({**FILTRO_SIM, "role": "DOCTOR"})})')
    print(f'  Pacientes: {personas.count_documents({"role": "PACIENTE"})} '
          f'(simulados {personas.count_documents({**FILTRO_SIM, "role": "PACIENTE"})})')
    print(f'  Citas: {citas.count_documents({})} (del mock original con campo SOAP: '
          f'{citas.count_documents(FILTRO_MOCK)})')
    print('  Médicos con más citas: ' + ', '.join(f'{d["_id"]}={d["n"]}' for d in por_doctor))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--confirmar', action='store_true', help='borra lo simulado anterior e inserta')
    parser.add_argument('--borrar-mock-anterior', action='store_true',
                        help='borra también las citas del mock original (campo SOAP)')
    args = parser.parse_args()

    # Sin nombre de base en la URI, Spring usa "test"; aquí igual, para escribir donde lee la app.
    db = MongoClient(leer_uri()).get_default_database('test')
    resumen(db)

    personas, horarios, citas = (leer_jsonl(n) for n in
                                 ('personas_sim.jsonl', 'horarios_sim.jsonl', 'citas_sim.jsonl'))
    print(f'\nA insertar: {len(personas)} personas, {len(horarios)} horarios, {len(citas)} citas')
    if not args.confirmar:
        print('Simulación: no se escribió nada. Repite con --confirmar para aplicar.')
        return

    ids_sim = [str(p['_id']) for p in db.personas.find({**FILTRO_SIM, 'role': 'DOCTOR'}, {'_id': 1})]
    print('\nBorrando lo simulado anterior...')
    print(f'  citas: {db.citas.delete_many({"doctorId": {"$in": ids_sim}}).deleted_count}')
    print(f'  horarios: {db.horariosAtencion.delete_many({"doctorId": {"$in": ids_sim}}).deleted_count}')
    print(f'  personas: {db.personas.delete_many(FILTRO_SIM).deleted_count}')
    if args.borrar_mock_anterior:
        print(f'  citas del mock original: {db.citas.delete_many(FILTRO_MOCK).deleted_count}')

    print('Insertando...')
    insertar(db.personas, personas)
    insertar(db.horariosAtencion, horarios)
    insertar(db.citas, citas)
    print()
    resumen(db)


if __name__ == '__main__':
    main()
