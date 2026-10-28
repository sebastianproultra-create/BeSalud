"""Genera el modo oscuro de BeSalud a partir de las hojas de estilo del modo claro.

Por cada archivo de static/css escribe su versión oscura en static/css/oscuro/ con la misma ruta. Cada regla que
define un color se repite bajo html[data-tema="oscuro"] con el color adaptado según para qué se usa (fondo, texto,
borde o sombra). Las reglas del modo claro no se tocan: el modo oscuro solo existe mientras <html> tenga
data-tema="oscuro". Como cada regla oscura suma el mismo prefijo, el orden de la cascada es el mismo que en claro.

Las variables (--algo) se traducen igual: donde se define --x se definen también --x--f, --x--t, --x--b y --x--s
(fondo, texto, borde y sombra), y en el modo oscuro cada uso de var(--x) pasa a la variante de su papel.

Uso (desde la raíz del repo), cada vez que cambie un CSS:
    python scripts/generar_tema_oscuro.py
"""
import colorsys
import os
import re
import sys

RAIZ = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'static', 'css')
SALIDA = 'oscuro'
PREFIJO = 'html[data-tema="oscuro"]'
CABECERA = ('/* Generado por scripts/generar_tema_oscuro.py a partir de {origen}. No editar a mano:\n'
            '   los ajustes van en base/tema-oscuro.css o en el generador. */\n')

# Papel de cada propiedad
PAPEL = {
    'color': 't', 'fill': 't', 'stroke': 't', 'caret-color': 't', 'text-decoration-color': 't',
    '-webkit-text-fill-color': 't', 'accent-color': 'k',
    'background': 'f', 'background-color': 'f', 'background-image': 'f',
    'border': 'b', 'border-color': 'b', 'border-top': 'b', 'border-right': 'b', 'border-bottom': 'b',
    'border-left': 'b', 'border-top-color': 'b', 'border-right-color': 'b', 'border-bottom-color': 'b',
    'border-left-color': 'b', 'border-block': 'b', 'border-inline': 'b', 'border-block-end': 'b',
    'border-block-start': 'b', 'border-inline-start': 'b', 'border-inline-end': 'b',
    'outline': 'b', 'outline-color': 'b', 'column-rule': 'b', 'column-rule-color': 'b',
    'box-shadow': 's', 'text-shadow': 's', 'filter': 's',
}

NOMBRES = {'white': (255, 255, 255), 'black': (0, 0, 0)}
RE_COLOR = re.compile(r'#[0-9a-fA-F]{8}\b|#[0-9a-fA-F]{6}\b|#[0-9a-fA-F]{3,4}\b|rgba?\([^()]*\)|(?<![\w-])(?:white|black)(?![\w-])')
RE_VAR = re.compile(r'var\(\s*(--[\w-]+)')


# ── Colores ────────────────────────────────────────────────────────────────

def leer_color(texto):
    t = texto.strip()
    if t.lower() in NOMBRES:
        return NOMBRES[t.lower()] + (1.0,)
    if t.startswith('#'):
        h = t[1:]
        if len(h) in (3, 4):
            h = ''.join(c * 2 for c in h)
        a = int(h[6:8], 16) / 255 if len(h) == 8 else 1.0
        return int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a
    partes = [p.strip() for p in re.split(r'[,\s/]+', t[t.index('(') + 1:-1]) if p.strip()]
    rgb = [float(p[:-1]) * 2.55 if p.endswith('%') else float(p) for p in partes[:3]]
    a = 1.0
    if len(partes) > 3:
        a = float(partes[3][:-1]) / 100 if partes[3].endswith('%') else float(partes[3])
    return int(round(rgb[0])), int(round(rgb[1])), int(round(rgb[2])), a


def escribir_color(r, g, b, a):
    if a >= 0.999:
        return '#{:02X}{:02X}{:02X}'.format(r, g, b)
    return 'rgba({}, {}, {}, {})'.format(r, g, b, round(a, 3))


def a_hsl(r, g, b):
    h, l, s = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
    return h * 360, s, l


def de_hsl(h, s, l, a):
    r, g, b = colorsys.hls_to_rgb((h % 360) / 360, max(0, min(1, l)), max(0, min(1, s)))
    return escribir_color(int(round(r * 255)), int(round(g * 255)), int(round(b * 255)), a)


VERDE = 158  # tono de la marca: las superficies oscuras quedan en verdes muy profundos


def familia_verde(h, s, l):
    croma = s * (1 - abs(2 * l - 1))
    return croma < 0.03 or 120 <= h <= 185


def fondo(r, g, b, a):
    h, s, l = a_hsl(r, g, b)
    if a <= 0.35:
        # Velos sutiles: uno oscuro sobre fondo claro pasa a ser un velo claro sobre fondo oscuro
        return escribir_color(r, g, b, a) if l >= 0.5 else de_hsl(h, min(s, 0.45), 0.72, min(1, a * 1.3))
    if a < 1 and l < 0.5:
        return escribir_color(r, g, b, a)  # velos oscuros de modales y fotos: siguen oscuros
    if a < 1:
        a = a + (1 - a) * 0.6  # paneles translúcidos claros: más opacos, para que la foto de atrás no ilumine
    if l >= 0.85:
        if familia_verde(h, s, l) and s * (1 - abs(2 * l - 1)) < 0.075:
            # Blanco = tarjeta (la superficie más alta); los mentas = fondo de página o recuadros internos
            claro = 0.135 if l >= 0.985 else 0.085 + (1 - l) * 0.25
            return de_hsl(VERDE, 0.22, claro, a)
        return de_hsl(h, 0.38, 0.17, a)  # avisos de color (ámbar, rojo...) conservan su tono
    if l >= 0.5:
        if s >= 0.4 and not familia_verde(h, s, l):
            return escribir_color(r, g, b, a)  # acentos de marca (dorado, ámbar): se ven bien sobre oscuro
        return de_hsl(h, min(s, 0.45), 0.2 + (l - 0.5) * 0.12, a)
    if familia_verde(h, s, l) and s > 0.2 and l < 0.2:
        return de_hsl(h, s, 0.2, a)  # botones y bloques verde oscuro: un poco más claros que la página
    return escribir_color(r, g, b, a)


def texto(r, g, b, a):
    h, s, l = a_hsl(r, g, b)
    if l >= 0.55:
        return escribir_color(r, g, b, a)
    if familia_verde(h, s, l):
        return de_hsl(h, min(s, 0.3), 0.92 - l * 0.45, a)
    return de_hsl(h, min(s, 0.75), 0.9 - l * 0.45, a)


def borde(r, g, b, a):
    h, s, l = a_hsl(r, g, b)
    if a <= 0.35:
        return escribir_color(r, g, b, a) if l >= 0.5 else de_hsl(h, min(s, 0.35), 0.72, min(1, a * 1.2))
    if l >= 0.6:
        tono = VERDE if familia_verde(h, s, l) else h
        sat = 0.15 if familia_verde(h, s, l) else min(s, 0.4)
        return de_hsl(tono, sat, 0.2 + (1 - l) * 0.25, a)
    return fondo(r, g, b, a)


def sombra(r, g, b, a):
    h, s, l = a_hsl(r, g, b)
    if l < 0.5:
        return escribir_color(0, 0, 0, min(0.6, a * 1.8))
    if a >= 0.5:
        # Aros y "bordes" hechos con sombra (0 0 0 2px #fff) separan del fondo claro: en oscuro, color de superficie
        return fondo(r, g, b, a)
    return escribir_color(r, g, b, a)


MAPEO = {'f': fondo, 't': texto, 'b': borde, 's': sombra}

# Variables que guardan un color (directo o a través de otra variable); se llena antes de generar
VARIABLES_DE_COLOR = set()


def traducir(valor, papel):
    """Adapta los colores y las variables de un valor según su papel."""
    if papel == 'k':
        return valor

    def color(m):
        return MAPEO[papel](*leer_color(m.group(0)))

    def variable(m):
        return m.group(0) + '--' + papel if m.group(1) in VARIABLES_DE_COLOR else m.group(0)

    # url(...) no se toca (puede traer colores en un SVG en línea)
    trozos = re.split(r'(url\([^)]*\))', valor)
    for i in range(0, len(trozos), 2):
        trozos[i] = RE_VAR.sub(variable, RE_COLOR.sub(color, trozos[i]))
    return ''.join(trozos)


def tiene_color(valor):
    sin_url = re.sub(r'url\([^)]*\)', '', valor)
    return bool(RE_COLOR.search(sin_url)) or any(v in VARIABLES_DE_COLOR for v in RE_VAR.findall(sin_url))


# ── Lectura del CSS ───────────────────────────────────────────────────────

def quitar_comentarios(css):
    return re.sub(r'/\*.*?\*/', '', css, flags=re.S)


def bloques(css, i=0):
    """Devuelve [(prelude, cuerpo | lista de bloques)] y la posición final."""
    salida = []
    n = len(css)
    while i < n:
        j = i
        profundidad = 0
        comilla = None
        while j < n:
            c = css[j]
            if comilla:
                if c == comilla:
                    comilla = None
            elif c in '"\'':
                comilla = c
            elif c == '(':
                profundidad += 1
            elif c == ')':
                profundidad -= 1
            elif profundidad == 0 and c in '{};':
                break
            j += 1
        if j >= n:
            break
        prelude = css[i:j].strip()
        if css[j] == '}':
            return salida, j + 1
        if css[j] == ';':  # @import, @charset
            i = j + 1
            continue
        if prelude.startswith('@media') or prelude.startswith('@supports'):
            hijos, i = bloques(css, j + 1)
            salida.append((prelude, hijos))
            continue
        # regla normal o @keyframes / @font-face: se lee hasta su llave de cierre
        k = j + 1
        nivel = 1
        while k < n and nivel:
            if css[k] == '{':
                nivel += 1
            elif css[k] == '}':
                nivel -= 1
            k += 1
        salida.append((prelude, css[j + 1:k - 1]))
        i = k
    return salida, i


def declaraciones(cuerpo):
    partes, actual, profundidad, comilla = [], '', 0, None
    for c in cuerpo:
        if comilla:
            if c == comilla:
                comilla = None
        elif c in '"\'':
            comilla = c
        elif c == '(':
            profundidad += 1
        elif c == ')':
            profundidad -= 1
        elif c == ';' and profundidad == 0:
            partes.append(actual)
            actual = ''
            continue
        actual += c
    partes.append(actual)
    for p in partes:
        if ':' in p:
            prop, val = p.split(':', 1)
            if prop.strip():
                yield prop.strip(), val.strip()


def separar_selectores(prelude):
    partes, actual, profundidad = [], '', 0
    for c in prelude:
        if c in '([':
            profundidad += 1
        elif c in ')]':
            profundidad -= 1
        if c == ',' and profundidad == 0:
            partes.append(actual.strip())
            actual = ''
            continue
        actual += c
    partes.append(actual.strip())
    return [p for p in partes if p]


def prefijar(selector):
    if selector.startswith(':root'):
        return PREFIJO + selector[len(':root'):]
    if re.match(r'html(?![\w-])', selector):
        return PREFIJO + selector[4:]
    return PREFIJO + ' ' + selector


# ── Generación ────────────────────────────────────────────────────────────

RE_IMAGEN = re.compile(r'(?:repeating-)?(?:linear|radial|conic)-gradient\(|url\(')


def partir_fondo(valor):
    """Separa el atajo `background` en (color, imágenes). Repetir el atajo en el modo oscuro reiniciaría
    background-size/position/repeat que otra regla haya puesto (la foto del login saldría en mosaico)."""
    importante = ' !important' if valor.rstrip().endswith('!important') else ''
    valor = re.sub(r'\s*!important\s*$', '', valor)
    if valor.strip().lower() in ('inherit', 'initial', 'unset', 'revert'):
        return None
    imagenes, resto, i = [], '', 0
    while i < len(valor):
        m = RE_IMAGEN.match(valor, i)
        if not m:
            resto += valor[i]
            i += 1
            continue
        nivel, j = 0, i
        while j < len(valor):
            if valor[j] == '(':
                nivel += 1
            elif valor[j] == ')':
                nivel -= 1
                if nivel == 0:
                    break
            j += 1
        imagenes.append(valor[i:j + 1])
        resto += ' '
        i = j + 1
    color = RE_COLOR.search(resto) or re.search(r'var\([^)]*\)|\btransparent\b|\bcurrentColor\b', resto)
    return (color.group(0) if color else 'transparent') + importante, \
        (', '.join(imagenes) if imagenes else 'none') + importante


def regla_oscura(prelude, cuerpo):
    lineas = []
    for prop, val in declaraciones(cuerpo):
        p = prop.lower()
        if p.startswith('--'):
            if tiene_color(val):
                for papel in 'ftbs':
                    lineas.append('    {}--{}: {};'.format(prop, papel, traducir(val, papel)))
        elif p == 'background' and partir_fondo(val):
            color, imagenes = partir_fondo(val)
            lineas.append('    background-color: {};'.format(traducir(color, 'f')))
            lineas.append('    background-image: {};'.format(traducir(imagenes, 'f')))
        elif p in PAPEL:
            # Todas se repiten (aunque no lleven color) para conservar el orden de la cascada
            lineas.append('    {}: {};'.format(prop, traducir(val, PAPEL[p])))
    if not lineas:
        return None
    selectores = ',\n'.join(prefijar(s) for s in separar_selectores(prelude))
    return selectores + ' {\n' + '\n'.join(lineas) + '\n}\n'


def generar(lista, sangria=''):
    salida = []
    for prelude, cuerpo in lista:
        if isinstance(cuerpo, list):
            if re.search(r'\bprint\b', prelude) and 'screen' not in prelude:
                continue  # lo impreso siempre sale en claro
            hijos = generar(cuerpo, sangria + '    ')
            if hijos:
                salida.append(prelude + ' {\n' + ''.join(hijos) + '}\n')
        elif prelude.startswith('@'):
            continue  # @keyframes, @font-face
        else:
            r = regla_oscura(prelude, cuerpo)
            if r:
                salida.append(r)
    return salida


def hojas(raiz):
    for carpeta, _, archivos in os.walk(raiz):
        if os.path.relpath(carpeta, raiz).split(os.sep)[0] == SALIDA:
            continue
        for nombre in sorted(archivos):
            if nombre.endswith('.css') and nombre != 'tema-oscuro.css':
                origen = os.path.join(carpeta, nombre)
                yield origen, bloques(quitar_comentarios(open(origen, encoding='utf-8').read()))[0]


def definiciones(lista):
    for prelude, cuerpo in lista:
        if isinstance(cuerpo, list):
            yield from definiciones(cuerpo)
        elif not prelude.startswith('@'):
            for prop, val in declaraciones(cuerpo):
                if prop.startswith('--'):
                    yield prop, val


def principal():
    raiz = os.path.normpath(RAIZ)
    todas = list(hojas(raiz))
    defs = [d for _, lista in todas for d in definiciones(lista)]
    cambio = True
    while cambio:  # una variable es de color si guarda un color o apunta a otra variable de color
        cambio = False
        for prop, val in defs:
            if prop not in VARIABLES_DE_COLOR and tiene_color(val):
                VARIABLES_DE_COLOR.add(prop)
                cambio = True
    generados = 0
    for origen, lista in todas:
        if True:
            relativo = os.path.relpath(origen, raiz).replace(os.sep, '/')
            destino = os.path.join(raiz, SALIDA, relativo)
            os.makedirs(os.path.dirname(destino), exist_ok=True)
            with open(destino, 'w', encoding='utf-8', newline='\n') as f:
                f.write(CABECERA.format(origen=relativo) + '\n' + '\n'.join(generar(lista)))
            generados += 1
    print('Generados {} archivos en {}'.format(generados, os.path.join(raiz, SALIDA)))


if __name__ == '__main__':
    sys.exit(principal())
