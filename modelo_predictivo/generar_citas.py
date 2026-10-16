"""Convierte el dataset agregado en citas individuales repartidas entre varios médicos, con dictamen SOAP.
Reemplaza al script_citas_soap_mock.py original (que asignaba todo a un solo médico).

- 14 médicos simulados (@sim.besalud.com) con horario semanal, slots de 20 o 30 minutos y un día libre.
- Cada día, el volumen de cada especialidad del CSV se reparte entre los médicos que atienden ese día:
  primero los controles pendientes con su mismo médico, luego los pacientes nuevos al médico con más cupos.
- Ningún médico tiene dos citas a la misma hora y ningún paciente tiene dos citas el mismo día.
- Un "Control" o una "Lectura de exámenes" siempre sigue a una consulta atendida por el mismo médico
  con el mismo diagnóstico.

Los documentos usan los nombres de campo y enums de las clases Java (Cita, Dictamen, Doctor, Paciente,
HorarioAtencion). Salida en salida/ (JSON extendido, una línea por documento) y citas_por_medico.csv.

Uso: python generar_citas.py   (después de generar_dataset.py)
"""
import calendar
import json
import random
import unicodedata
from collections import Counter, defaultdict
from dataclasses import dataclass, field
from datetime import date, datetime, time, timedelta
from pathlib import Path

import bcrypt
import pandas as pd

from catalogo_clinico import CUADROS
from generar_dataset import ARCHIVO as DATASET

RAIZ = Path(__file__).resolve().parent
CIE10 = RAIZ.parent / 'src' / 'main' / 'resources' / 'static' / 'data' / 'cie10.json'
SALIDA = RAIZ / 'salida'
CITAS_POR_MEDICO = RAIZ / 'citas_por_medico.csv'

SEMILLA = 2024
DOMINIO = '@sim.besalud.com'
PASSWORD = 'Demo1234'
N_ADULTOS, N_NINOS = 3800, 1200
COLOMBIA = timedelta(hours=5)  # Spring guarda LocalDate/LocalDateTime de Colombia (UTC-5) en UTC
PAQUETE = 'com.gestion.proyectos.modelo.'

# especialidad: (duración de la cita en minutos, día libre de cada médico; 0 = lunes, 6 = domingo).
# Quien libra entre semana cubre la guardia del domingo.
PLANTA = {
    'Medicina General': (20, [6, 6, 6, 2]),
    'Pediatría': (20, [6, 6, 3]),
    'Neumología': (30, [6, 6, 1]),
    'Cardiología': (30, [6, 0]),
    'Dermatología': (30, [6, 4]),
}
# Lunes a viernes jornada completa (18 citas de 20 min o 16 de 30 min); sábado y domingo solo mañana.
JORNADA = {20: [('08:00', '12:00'), ('14:00', '16:00')], 30: [('07:00', '12:00'), ('14:00', '17:00')]}
FIN_DE_SEMANA = [('08:00', '12:00')]
DIAS_JAVA = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY']

MEDICOS = [
    ('Laura', 'Martínez Ortega', 'F'), ('Andrés', 'Pérez Villalba', 'M'), ('Camila', 'Rodríguez Herazo', 'F'),
    ('Julián', 'Torres Benítez', 'M'),
    ('Valentina', 'Gómez Arrieta', 'F'), ('Mauricio', 'Castro Julio', 'M'), ('Daniela', 'Ramos Padilla', 'F'),
    ('Ricardo', 'Morales Cabarcas', 'M'), ('Natalia', 'Herrera Marrugo', 'F'), ('Felipe', 'Díaz Barrios', 'M'),
    ('Carolina', 'Vargas Pájaro', 'F'), ('Sergio', 'Jiménez Caraballo', 'M'),
    ('Mariana', 'Ruiz Altamar', 'F'), ('Óscar', 'Mendoza Guerrero', 'M'),
]
NOMBRES = {'F': ['María', 'Ana', 'Luisa', 'Sofía', 'Isabella', 'Paola', 'Diana', 'Yolanda', 'Rosa', 'Carmen',
                 'Andrea', 'Juliana', 'Gabriela', 'Lina', 'Marcela', 'Patricia', 'Sandra', 'Liliana', 'Elena',
                 'Valeria', 'Mariana', 'Daniela', 'Luciana', 'Salomé', 'Ximena'],
           'M': ['Carlos', 'José', 'Luis', 'Juan', 'Jorge', 'Miguel', 'Pedro', 'Alberto', 'Rafael', 'Eduardo',
                 'Fernando', 'Javier', 'Diego', 'Santiago', 'Samuel', 'Mateo', 'Sebastián', 'Nicolás',
                 'Alejandro', 'Hernán', 'Emiliano', 'Tomás', 'Jerónimo', 'Martín', 'Manuel']}
APELLIDOS = ['García', 'Rodríguez', 'Martínez', 'López', 'González', 'Hernández', 'Pérez', 'Sánchez', 'Ramírez',
             'Torres', 'Flórez', 'Rivera', 'Gómez', 'Díaz', 'Reyes', 'Morales', 'Ortiz', 'Castro', 'Julio',
             'Barrios', 'Pájaro', 'Marrugo', 'Cabarcas', 'Herazo', 'Padilla', 'Caraballo', 'Arrieta', 'Altamar',
             'Guerrero', 'Puello', 'Blanco', 'Salgado', 'Zúñiga', 'Mercado', 'Villalba', 'Atencia']

rng = random.Random(SEMILLA)
_TS_BASE = calendar.timegm((2024, 1, 1, 5, 0, 0))


def oid(tipo, n):
    """ObjectId determinista: mismos IDs en cada ejecución."""
    return f'{_TS_BASE:08x}{tipo:04x}{n:012x}'


def fecha_mongo(d):
    if isinstance(d, datetime):
        utc = d + COLOMBIA
    else:
        utc = datetime.combine(d, time()) + COLOMBIA
    return {'$date': utc.strftime('%Y-%m-%dT%H:%M:%SZ')}


def sin_tildes(texto):
    return ''.join(c for c in unicodedata.normalize('NFD', texto) if unicodedata.category(c) != 'Mn')


def minutos(hhmm):
    h, m = map(int, hhmm.split(':'))
    return h * 60 + m


def hhmm(total):
    return f'{total // 60:02d}:{total % 60:02d}'


@dataclass
class Medico:
    id: str
    nombre: str
    apellido: str
    especialidad: str
    duracion: int
    dia_libre: int

    def franjas(self, dia_semana):
        if dia_semana == self.dia_libre:
            return []
        return JORNADA[self.duracion] if dia_semana < 5 else FIN_DE_SEMANA

    def slots(self, dia_semana):
        return [hhmm(m) for ini, fin in self.franjas(dia_semana)
                for m in range(minutos(ini), minutos(fin) - self.duracion + 1, self.duracion)]

    @property
    def nombre_completo(self):
        return f'{self.nombre} {self.apellido}'


@dataclass
class Paciente:
    id: str
    nombre: str
    apellido: str
    sexo: str
    nacimiento: date
    talla_adulto: float
    peso_base: float
    antecedentes: dict
    cronicos: dict = field(default_factory=dict)        # código CIE-10 -> texto para antecedentes
    farmacos: dict = field(default_factory=dict)        # código CIE-10 -> medicamentos crónicos
    confirmados: set = field(default_factory=set)       # diagnósticos ya confirmados alguna vez
    medico_ref: dict = field(default_factory=dict)      # especialidad -> médico de referencia
    pendiente: set = field(default_factory=set)         # especialidades con control en cola
    dias_con_cita: set = field(default_factory=set)

    def edad(self, d):
        return (d - self.nacimiento).days / 365.25


@dataclass
class Control:
    objetivo: date
    paciente: Paciente
    medico: Medico
    cuadro: object
    tipo: str


# ── Catálogos ─────────────────────────────────────────────────────────────────

def cargar_cie10():
    with open(CIE10, encoding='utf-8') as f:
        catalogo = {fila[0]: fila[1] for fila in json.load(f)}
    faltantes = {c.codigo for cs in CUADROS.values() for c in cs} - catalogo.keys()
    if faltantes:
        raise SystemExit(f'Códigos CIE-10 que no están en cie10.json: {sorted(faltantes)}')
    return catalogo


def crear_medicos():
    medicos, i = defaultdict(list), 0
    for esp, (duracion, libres) in PLANTA.items():
        for libre in libres:
            nombre, apellido, _ = MEDICOS[i]
            i += 1
            medicos[esp].append(Medico(oid(1, i), nombre, apellido, esp, duracion, libre))
    return medicos


def crear_pacientes():
    adultos, ninos, usados = [], [], set()
    hoy = date(2024, 1, 1)
    for n in range(N_ADULTOS + N_NINOS):
        nino = n >= N_ADULTOS
        sexo = rng.choice('FM')
        nombre = rng.choice(NOMBRES[sexo])
        apellido = f'{rng.choice(APELLIDOS)} {rng.choice(APELLIDOS)}'
        edad = rng.uniform(0.1, 12) if nino else rng.triangular(18, 88, 45)
        talla = rng.gauss(162 if sexo == 'F' else 172, 6)
        cirugias = ['Niega'] * 6 + (['Apendicectomía', 'Colecistectomía', 'Herniorrafia inguinal'] +
                                    (['Cesárea'] if sexo == 'F' else []))
        familiares = ['Niega', 'Niega', 'Madre hipertensa', 'Padre diabético', 'Abuela con cáncer de mama',
                      'Padre con infarto antes de los 60 años', 'Hermano asmático']
        antecedentes = {
            'patologicos': 'Niega',
            'quirurgicos': 'Niega' if nino else rng.choice(cirugias),
            'farmacologicos': 'Niega',
            'alergicos': rng.choices(['Niega', 'Penicilina', 'Ibuprofeno', 'Sulfas'], [85, 7, 5, 3])[0],
            'familiares': rng.choice(familiares),
        }
        pac = Paciente(oid(2, n + 1), nombre, apellido, sexo, hoy - timedelta(days=int(edad * 365.25)),
                       talla, rng.gauss(24.5, 3.5) * (talla / 100) ** 2, antecedentes)
        (ninos if nino else adultos).append(pac)
    return adultos, ninos


# ── Dictamen ──────────────────────────────────────────────────────────────────

def ajustar_por_alergias(meds, paciente):
    alergia = paciente.antecedentes['alergicos']
    ajustados = []
    for nombre, via, dosis, duracion, cantidad in meds:
        if alergia == 'Penicilina' and 'Amoxicilina' in nombre:
            if 'suspensión' in nombre:
                nombre, dosis, duracion = 'Azitromicina 200 mg/5 ml suspensión', '5 ml cada 24 horas', 'por 3 días'
            else:
                nombre, dosis, duracion, cantidad = 'Azitromicina 500 mg tableta', '1 tableta cada 24 horas', 'por 3 días', 3
        elif alergia == 'Ibuprofeno' and 'Naproxeno' in nombre:
            nombre, dosis = 'Acetaminofén 500 mg tableta', '2 tabletas cada 8 horas si hay dolor'
        ajustados.append({'nombre': nombre, 'via': via, 'dosisFrecuencia': dosis, 'duracion': duracion,
                          'cantidad': cantidad})
    return ajustados


def signos_vitales(paciente, cuadro, tipo, d):
    edad = paciente.edad(d)
    agudo = tipo in ('PRIMERA_VEZ', 'URGENCIA')
    fiebre = cuadro.fiebre and agudo and rng.random() < 0.75
    if edad < 14:
        if edad < 1:
            talla, peso = 50 + 25 * edad, 3.3 + 6 * edad
        elif edad <= 2:
            talla, peso = 75 + 12 * (edad - 1), 9.3 + 3 * (edad - 1)
        else:
            talla, peso = 87 + 6 * (edad - 2), 12.3 + 2.6 * (edad - 2)
        peso *= rng.uniform(0.9, 1.1)
        sistolica, diastolica = rng.randint(90, 110), rng.randint(55, 70)
        fc = int(140 - edad * 5 + rng.randint(-8, 8))
        fr = max(16, int(40 - edad * 1.7 + rng.randint(-3, 3)))
    else:
        talla, peso = paciente.talla_adulto, paciente.peso_base + rng.uniform(-1.5, 1.5)
        sistolica = rng.randint(105, 130) + int(max(0, edad - 50) * 0.3)
        if cuadro.codigo == 'I10X':
            sistolica = rng.randint(145, 170) if agudo else rng.randint(124, 142)
        diastolica = int(sistolica * 0.63) + rng.randint(-4, 4)
        fc, fr = rng.randint(62, 92), rng.randint(14, 19)
    if fiebre:
        fc += 15
    if cuadro.respiratorio and agudo:
        fr += 4
    saturacion = rng.randint(95, 99)
    if cuadro.respiratorio:
        saturacion = rng.randint(88, 94) if cuadro.codigo.startswith('J44') else rng.randint(92, 97)
    return {
        'tensionSistolica': sistolica, 'tensionDiastolica': diastolica,
        'frecuenciaCardiaca': fc, 'frecuenciaRespiratoria': fr,
        'temperatura': round(rng.uniform(37.9, 39.3) if fiebre else rng.uniform(36.2, 37.1), 1),
        'saturacionOxigeno': saturacion,
        'peso': round(peso, 1), 'talla': round(talla, 1), 'imc': round(peso / (talla / 100) ** 2, 1),
    }


def crear_dictamen(paciente, medico, cuadro, tipo, momento, cie10):
    descripcion = cie10[cuadro.codigo]
    seguimiento = tipo in ('CONTROL', 'LECTURA_EXAMENES')
    if cuadro.codigo in paciente.confirmados:
        tipo_dx = 'CONFIRMADO_REPETIDO'
    elif tipo == 'CONTROL':
        tipo_dx = 'CONFIRMADO_REPETIDO'
    elif tipo == 'LECTURA_EXAMENES' or not cuadro.examenes:
        tipo_dx = 'CONFIRMADO_NUEVO'
    else:
        tipo_dx = 'IMPRESION_DIAGNOSTICA'

    if tipo == 'LECTURA_EXAMENES':
        motivo = f'Lectura de exámenes: {descripcion}'
        enfermedad = f'Paciente que trae resultados solicitados en la consulta anterior ({cuadro.ordenes.rstrip(".").lower()}).'
    elif tipo == 'CONTROL':
        motivo = f'Control: {descripcion}'
        enfermedad = f'Paciente en seguimiento por {descripcion.lower()}. Refiere adherencia al tratamiento y mejoría.'
    else:
        motivo = rng.choice(cuadro.motivos)
        enfermedad = cuadro.enfermedad

    # Los medicamentos de los cuadros crónicos se continúan en cada control; los agudos solo en la primera consulta.
    meds = cuadro.medicamentos if (not seguimiento or cuadro.cronico) else []
    incapacidad = 0
    if not seguimiento and rng.random() < cuadro.incapacidad[0]:
        incapacidad = rng.randint(cuadro.incapacidad[1], cuadro.incapacidad[2])

    antecedentes = dict(paciente.antecedentes)
    if paciente.cronicos:
        antecedentes['patologicos'] = '; '.join(paciente.cronicos.values())
        antecedentes['farmacologicos'] = '; '.join(paciente.farmacos.values()) or 'Niega'

    causa = 'ENFERMEDAD_GENERAL'
    if cuadro.codigo == 'M545' and rng.random() < 0.25:
        causa = 'ACCIDENTE_TRABAJO'
    elif cuadro.codigo in ('Z000', 'Z001'):
        causa = 'OTRA'

    relacionados = [{'codigo': c, 'descripcion': cie10[c], 'tipo': 'CONFIRMADO_REPETIDO'}
                    for c in paciente.cronicos if c != cuadro.codigo][:2]

    return {
        'fechaAtencion': fecha_mongo(momento),
        'tipoConsulta': tipo,
        'causaExterna': causa,
        'motivoConsulta': motivo,
        'enfermedadActual': enfermedad,
        'antecedentes': antecedentes,
        'signosVitales': signos_vitales(paciente, cuadro, tipo, momento.date()),
        'examenFisico': ('Paciente en mejores condiciones generales. ' if seguimiento else '') + cuadro.examen,
        'diagnosticoPrincipal': {'codigo': cuadro.codigo, 'descripcion': descripcion, 'tipo': tipo_dx},
        'diagnosticosRelacionados': relacionados,
        'medicamentos': ajustar_por_alergias(meds, paciente),
        'ordenesMedicas': cuadro.ordenes if not seguimiento else '',
        'recomendaciones': cuadro.recomendaciones,
        'incapacidadDias': incapacidad or None,
        'incapacidadJustificacion': f'Reposo por {descripcion.lower()}.' if incapacidad else None,
        'fechaEmision': fecha_mongo(momento + timedelta(minutes=medico.duracion - 3)),
        'firmadoPor': medico.nombre_completo,
        'especialidadFirmante': medico.especialidad,
    }, motivo, tipo_dx


# ── Asignación ────────────────────────────────────────────────────────────────

def elegir_cuadro(esp, alerta, factor):
    cuadros = CUADROS[esp]
    pesos = [c.peso * (factor if c.lluvia else 1) * (2 if alerta and c.alerta else 1) for c in cuadros]
    return rng.choices(cuadros, pesos)[0]


def generar():
    cie10 = cargar_cie10()
    df = pd.read_csv(RAIZ / DATASET, parse_dates=['fecha'])
    medicos = crear_medicos()
    adultos, ninos = crear_pacientes()
    mayores_40 = [p for p in adultos if p.edad(date(2024, 1, 1)) >= 40]
    pool = {'Pediatría': ninos, 'Cardiología': mayores_40}

    cola = defaultdict(list)
    citas, n_cita, sobrecupos = [], 0, 0
    volumen_esperado = {}

    def encolar(paciente, medico, cuadro, tipo, desde, rango):
        cola[medico.especialidad].append(
            Control(desde + timedelta(days=rng.randint(*rango)), paciente, medico, cuadro, tipo))
        paciente.pendiente.add(medico.especialidad)

    def registrar(d, hora, medico, paciente, cuadro, tipo):
        nonlocal n_cita
        n_cita += 1
        esp = medico.especialidad
        momento = datetime.combine(d, time.fromisoformat(hora))
        estado = rng.choices(['COMPLETADA', 'NO_ASISTIO', 'CANCELADA'], [85, 9, 6])[0]
        if tipo == 'URGENCIA':
            estado = 'COMPLETADA'
        cita = {'_id': {'$oid': oid(3, n_cita)}, 'doctorId': medico.id, 'pacienteId': paciente.id,
                'hora': hora, 'fecha': fecha_mongo(d), 'motivo': None, 'estado': estado,
                'recordatorioEnviado': True, '_class': PAQUETE + 'Cita'}
        paciente.dias_con_cita.add(d)
        paciente.medico_ref[esp] = medico

        dictamen, motivo, tipo_dx = crear_dictamen(paciente, medico, cuadro, tipo, momento, cie10)
        cita['motivo'] = dictamen['motivoConsulta'] if tipo != 'URGENCIA' else f'Urgencia: {motivo}'
        if estado == 'COMPLETADA':
            cita['inicioAtencion'] = fecha_mongo(momento + timedelta(minutes=rng.randint(0, 10)))
            cita['dictamen'] = dictamen
            if tipo_dx != 'IMPRESION_DIAGNOSTICA':
                paciente.confirmados.add(cuadro.codigo)
            if cuadro.cronico:
                paciente.cronicos[cuadro.codigo] = cuadro.cronico
                paciente.farmacos[cuadro.codigo] = ', '.join(m[0] for m in cuadro.medicamentos)
            prob, dmin, dmax = cuadro.control
            if tipo in ('PRIMERA_VEZ', 'URGENCIA') and rng.random() < prob:
                encolar(paciente, medico, cuadro, 'LECTURA_EXAMENES' if cuadro.examenes else 'CONTROL', d,
                        (dmin, dmax))
            elif tipo in ('CONTROL', 'LECTURA_EXAMENES') and cuadro.cronico and rng.random() < 0.6:
                encolar(paciente, medico, cuadro, 'CONTROL', d, (max(dmin, 20), max(dmax, 45)))
        elif tipo in ('CONTROL', 'LECTURA_EXAMENES') and rng.random() < 0.5:
            encolar(paciente, medico, cuadro, tipo, d, (5, 14))  # reprograma el control que no cumplió
        citas.append(cita)
        return cita

    for _, fila in df.sort_values(['fecha']).iterrows():
        d = fila['fecha'].date()
        esp = fila['especialidad_medica']
        n = int(fila['volumen_citas_total'])
        volumen_esperado[(d, esp)] = n
        libres = {m.id: m.slots(d.weekday()) for m in medicos[esp]}
        de_turno = [m for m in medicos[esp] if libres[m.id]]
        atendidas, extra = Counter(), Counter()

        def tomar_slot(medico):
            slots = libres[medico.id]
            atendidas[medico.id] += 1
            return slots.pop(rng.randrange(len(slots)))

        # 1. Controles vencidos, con su mismo médico y si el paciente no tiene otra cita ese día.
        restantes = []
        for c in sorted(cola[esp], key=lambda c: c.objetivo):
            if c.objetivo > d:
                restantes.append(c)
            elif (d - c.objetivo).days > 30:
                c.paciente.pendiente.discard(esp)  # el paciente no volvió
            elif n > sum(atendidas.values()) and libres[c.medico.id] and d not in c.paciente.dias_con_cita:
                c.paciente.pendiente.discard(esp)
                registrar(d, tomar_slot(c.medico), c.medico, c.paciente, c.cuadro, c.tipo)
            else:
                restantes.append(c)
        cola[esp] = restantes

        # 2. Pacientes nuevos: muchas veces vuelven a su médico de referencia; si no, al que tenga más cupos libres.
        candidatos = pool.get(esp, adultos)
        while sum(atendidas.values()) < n:
            paciente = rng.choice(candidatos)
            while d in paciente.dias_con_cita or esp in paciente.pendiente:
                paciente = rng.choice(candidatos)
            cuadro = elegir_cuadro(esp, fila['alertas_salud_publica'], fila['factor_estacional_lluvias'])
            urgente = cuadro.urgencia and rng.random() < 0.12
            tipo = 'URGENCIA' if urgente else 'PRIMERA_VEZ'
            medico = paciente.medico_ref.get(esp) if rng.random() < 0.6 else None
            if medico is None or not libres[medico.id]:
                medico = max(rng.sample(de_turno, len(de_turno)), key=lambda m: len(libres[m.id]), default=None)
            if medico is not None and libres[medico.id]:
                hora = tomar_slot(medico)
            elif de_turno:
                # Todos los cupos llenos: sobrecupo al final de la jornada del menos cargado.
                medico = min(de_turno, key=lambda m: atendidas[m.id])
                fin = minutos(medico.franjas(d.weekday())[-1][1])
                hora = hhmm(fin + medico.duracion * extra[medico.id])
                extra[medico.id] += 1
                atendidas[medico.id] += 1
                sobrecupos += 1
            else:
                break
            registrar(d, hora, medico, paciente, cuadro, tipo)

    return medicos, adultos + ninos, citas, volumen_esperado, sobrecupos


# ── Validación y salida ───────────────────────────────────────────────────────

def validar(citas, volumen_esperado, medicos):
    errores = []
    por_doctor_hora = Counter((c['doctorId'], c['fecha']['$date'], c['hora']) for c in citas)
    errores += [f'Médico con dos citas a la misma hora: {k}' for k, v in por_doctor_hora.items() if v > 1]
    por_paciente_dia = Counter((c['pacienteId'], c['fecha']['$date']) for c in citas)
    errores += [f'Paciente con dos citas el mismo día: {k}' for k, v in por_paciente_dia.items() if v > 1]

    esp_de = {m.id: m.especialidad for ms in medicos.values() for m in ms}
    reales = Counter((c['fecha']['$date'][:10], esp_de[c['doctorId']]) for c in citas)  # 05:00Z = mismo día
    for (d, esp), n in volumen_esperado.items():
        if reales[(d.isoformat(), esp)] != n:
            errores.append(f'Volumen distinto al CSV en {d} {esp}')

    atendidas = defaultdict(set)  # (paciente, médico) -> diagnósticos atendidos
    for c in sorted(citas, key=lambda c: (c['fecha']['$date'], c['hora'])):
        dic = c.get('dictamen')
        if not dic:
            continue
        clave = (c['pacienteId'], c['doctorId'])
        codigo = dic['diagnosticoPrincipal']['codigo']
        if dic['tipoConsulta'] in ('CONTROL', 'LECTURA_EXAMENES') and codigo not in atendidas[clave]:
            errores.append(f'Control sin consulta previa con el mismo médico: {c["_id"]}')
        atendidas[clave].add(codigo)
    return errores


def documentos_personas(medicos, pacientes, usados_ids):
    clave = bcrypt.hashpw(PASSWORD.encode(), bcrypt.gensalt(10)).decode().replace('$2b$', '$2a$', 1)
    docs = []
    for i, m in enumerate(m for ms in medicos.values() for m in ms):
        usuario = f'{sin_tildes(m.nombre).lower()}.{sin_tildes(m.apellido.split()[0]).lower()}'
        docs.append({
            '_id': {'$oid': m.id}, 'nombre': m.nombre, 'apellido': m.apellido,
            'telefono': f'300{5550000 + i:07d}', 'identificacion': f'{1047400000 + i}',
            'email': usuario + DOMINIO, 'password': clave, 'role': 'DOCTOR',
            'especialidad': m.especialidad, 'fechaNacimiento': fecha_mongo(date(1975 + i, 1 + i % 12, 10 + i)),
            'foto': None, 'estado': 'ACTIVO',
            'biografia': f'Médico especialista en {m.especialidad.lower()} con experiencia en la atención '
                         f'de pacientes en Cartagena. (Médico simulado para el modelo predictivo.)',
            '_class': PAQUETE + 'Doctor',
        })
    for i, p in enumerate(pacientes):
        if p.id not in usados_ids:
            continue
        docs.append({
            '_id': {'$oid': p.id}, 'nombre': p.nombre, 'apellido': p.apellido,
            'telefono': f'31{rng.randint(0, 99999999):08d}', 'identificacion': f'{1100000000 + i}',
            'email': f'p{i + 1:05d}{DOMINIO}', 'password': clave, 'role': 'PACIENTE',
            '_class': PAQUETE + 'Paciente',
        })
    return docs


def documentos_horarios(medicos):
    docs, n = [], 0
    for m in (m for ms in medicos.values() for m in ms):
        for dia in range(7):
            for ini, fin in m.franjas(dia):
                n += 1
                docs.append({'_id': {'$oid': oid(4, n)}, 'doctorId': m.id, 'diaSemana': DIAS_JAVA[dia],
                             'horaInicio': ini, 'horaFin': fin, 'duracionCitaMinutos': m.duracion,
                             '_class': PAQUETE + 'HorarioAtencion'})
    return docs


def escribir_jsonl(ruta, docs):
    with open(ruta, 'w', encoding='utf-8') as f:
        for doc in docs:
            f.write(json.dumps(doc, ensure_ascii=False) + '\n')


def escribir_citas_por_medico(medicos, citas, volumen_esperado):
    fechas = sorted({d for d, _ in volumen_esperado})
    conteo = Counter((c['doctorId'], c['fecha']['$date'][:10]) for c in citas)
    filas = []
    for m in (m for ms in medicos.values() for m in ms):
        for d in fechas:
            filas.append({'fecha': d, 'especialidad_medica': m.especialidad, 'medico_id': m.id,
                          'medico_nombre': m.nombre_completo, 'volumen_citas': conteo[(m.id, d.isoformat())]})
    pd.DataFrame(filas).to_csv(CITAS_POR_MEDICO, index=False)


if __name__ == '__main__':
    medicos, pacientes, citas, volumen_esperado, sobrecupos = generar()
    errores = validar(citas, volumen_esperado, medicos)

    usados = {c['pacienteId'] for c in citas}
    personas = documentos_personas(medicos, pacientes, usados)
    horarios = documentos_horarios(medicos)
    SALIDA.mkdir(exist_ok=True)
    escribir_jsonl(SALIDA / 'personas_sim.jsonl', personas)
    escribir_jsonl(SALIDA / 'horarios_sim.jsonl', horarios)
    escribir_jsonl(SALIDA / 'citas_sim.jsonl', citas)
    escribir_citas_por_medico(medicos, citas, volumen_esperado)

    nombre = {m.id: f'{m.nombre_completo} ({m.especialidad})' for ms in medicos.values() for m in ms}
    tipos = Counter(c['dictamen']['tipoConsulta'] for c in citas if 'dictamen' in c)
    reporte = [
        f'Citas: {len(citas)} | médicos: {len(nombre)} | pacientes con citas: {len(usados)} | sobrecupos: {sobrecupos}',
        f'Estados: {dict(Counter(c["estado"] for c in citas))}',
        f'Tipos de consulta (atendidas): {dict(tipos)}',
        'Citas por médico:',
        *[f'  {nombre[k]}: {v}' for k, v in sorted(Counter(c['doctorId'] for c in citas).items(),
                                                   key=lambda kv: nombre[kv[0]])],
        f'Errores de validación: {len(errores)}',
        *errores[:20],
    ]
    (SALIDA / 'reporte.txt').write_text('\n'.join(reporte), encoding='utf-8')
    print('\n'.join(reporte))
    if errores:
        raise SystemExit(1)
