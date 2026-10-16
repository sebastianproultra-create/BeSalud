"""Parámetros de la simulación compartidos por el generador del dataset, el de citas y la API."""

ESPECIALIDADES = ['Medicina General', 'Pediatría', 'Neumología', 'Cardiología', 'Dermatología']

# Demanda media de un día hábil en temporada seca.
DEMANDA_BASE = {'Medicina General': 20, 'Pediatría': 6, 'Neumología': 4, 'Cardiología': 7, 'Dermatología': 7}

# Cuánto le pega la lluvia a cada especialidad (1 = recibe el factor completo, 0 = no le afecta).
# Pediatría y Neumología se duplican o triplican; Medicina General sube un poco.
SENSIBILIDAD_LLUVIA = {'Medicina General': 0.3, 'Pediatría': 1.0, 'Neumología': 1.0,
                       'Cardiología': 0.0, 'Dermatología': 0.0}

# Cartagena: lluvias leves en mayo-junio y temporada fuerte de agosto a noviembre, con pico en octubre.
FACTOR_LLUVIAS = {1: 1.0, 2: 1.0, 3: 1.0, 4: 1.0, 5: 1.5, 6: 1.5,
                  7: 1.0, 8: 1.5, 9: 2.0, 10: 3.0, 11: 2.5, 12: 1.0}

# Alertas de salud pública (brotes de IRA / dengue): suben la demanda de estas especialidades.
EFECTO_ALERTA = {'Medicina General': 1.3, 'Pediatría': 1.3, 'Neumología': 1.2}


def factor_lluvias(mes: int) -> float:
    return FACTOR_LLUVIAS[mes]


def multiplicador_lluvia(especialidad: str, mes: int) -> float:
    return 1 + (factor_lluvias(mes) - 1) * SENSIBILIDAD_LLUVIA[especialidad]
