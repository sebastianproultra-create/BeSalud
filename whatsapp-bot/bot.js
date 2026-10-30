const http = require('http');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const PORT = process.env.PORT || 3000;
const VERIFY_TOKEN = process.env.WHATSAPP_VERIFY_TOKEN;
const TOKEN = process.env.WHATSAPP_TOKEN;
const PHONE_NUMBER_ID = process.env.WHATSAPP_PHONE_NUMBER_ID;
const APP_SECRET = process.env.WHATSAPP_APP_SECRET;
const GRAPH = `https://graph.facebook.com/${process.env.GRAPH_VERSION || 'v25.0'}`;
const PORTAL = process.env.PORTAL_URL || 'https://besalud-app-production.up.railway.app';
const DRY_RUN = process.env.DRY_RUN === '1';
const BOT_SECRET = process.env.WHATSAPP_BOT_SECRET;

const RUTAS_JSON = [
  process.env.CHATBOT_JSON,
  path.join(__dirname, 'chatbot-landing.json'),
  path.join(__dirname, '..', 'src', 'main', 'resources', 'static', 'data', 'chatbot-landing.json'),
].filter(Boolean);

function cargarArbol() {
  const ruta = RUTAS_JSON.find(r => fs.existsSync(r));
  if (!ruta) throw new Error('No encuentro chatbot-landing.json en: ' + RUTAS_JSON.join(', '));
  const nodos = JSON.parse(fs.readFileSync(ruta, 'utf8')).nodos;
  console.log(`Árbol cargado: ${nodos.length} nodos desde ${ruta}`);
  return new Map(nodos.map(n => [n.id, n]));
}

const arbol = cargarArbol();
const nodoActual = new Map();
const procesados = new Set();

function normalizar(texto) {
  return texto.toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '').trim();
}

function buscarPorPalabraClave(texto) {
  const t = normalizar(texto);
  let mejor = null, puntos = 0;
  // Gana la coincidencia más específica: palabras más largas pesan más y el título del nodo también cuenta
  for (const nodo of arbol.values()) {
    const titulo = normalizar(nodo.pregunta || '').split(/[^a-zñ]+/).filter(w => w.length > 3);
    const p = [...(nodo.palabrasClave || []).map(normalizar), ...titulo]
      .filter(k => t.includes(k)).reduce((s, k) => s + k.length, 0);
    if (p > puntos) { mejor = nodo; puntos = p; }
  }
  return mejor;
}

function elegirNodo(de, mensaje) {
  const actual = arbol.get(nodoActual.get(de)) || arbol.get('inicio');
  const elegido = mensaje.interactive?.list_reply?.id || mensaje.interactive?.button_reply?.id;
  if (elegido) return arbol.get(elegido) || arbol.get('inicio');

  const texto = mensaje.text?.body || '';
  const t = normalizar(texto);
  if (!nodoActual.has(de) || /^(0|hola|menu|inicio|empezar)\b/.test(t)) return arbol.get('inicio');
  const numero = parseInt(t, 10);
  if (numero >= 1 && numero <= (actual.opciones || []).length) return arbol.get(actual.opciones[numero - 1]);
  return buscarPorPalabraClave(texto) || { ...actual, respuesta: 'No le entendí. Elija una opción de la lista o escriba "menú".' };
}

function construirMensaje(para, nodo) {
  let cuerpo = nodo.respuesta;
  if (nodo.accion) cuerpo += `\n\n👉 ${nodo.accion.texto}: ${PORTAL}${nodo.accion.url}`;

  const filas = (nodo.opciones || [])
    .map(id => arbol.get(id)).filter(Boolean)
    .map((h, i) => ({ id: h.id, title: `${i + 1}. ${h.pregunta}`.slice(0, 24), ...(h.detalle && { description: h.detalle.slice(0, 72) }) }));
  if (nodo.id !== 'inicio') filas.push({ id: 'inicio', title: '0. Menú principal' });

  return {
    messaging_product: 'whatsapp',
    to: para,
    type: 'interactive',
    interactive: {
      type: 'list',
      header: { type: 'text', text: 'BeSalud' },
      body: { text: cuerpo.slice(0, 1024) },
      footer: { text: 'Toque Ver opciones o escriba el número' },
      action: { button: 'Ver opciones', sections: [{ title: 'Opciones', rows: filas.slice(0, 10) }] },
    },
  };
}

async function enviar(payload) {
  if (DRY_RUN) return console.log('[DRY_RUN]', JSON.stringify(payload)) || true;
  const res = await fetch(`${GRAPH}/${PHONE_NUMBER_ID}/messages`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${TOKEN}`, 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
  if (!res.ok) console.error('Error de WhatsApp', res.status, await res.text());
  return res.ok;
}

// Confirmar o cancelar lo resuelve el portal, con la misma lógica (y devolución del abono) que sus botones
async function responderAsistencia(payload, telefono) {
  if (!BOT_SECRET) return null;
  try {
    const res = await fetch(`${PORTAL}/whatsapp/asistencia`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Bot-Secret': BOT_SECRET },
      body: JSON.stringify({ payload, telefono }),
    });
    if (!res.ok) return console.error('El portal no aceptó la respuesta', res.status) || null;
    return (await res.json()).texto;
  } catch (e) {
    console.error('No se pudo contactar al portal', e.message);
    return 'No pudimos registrar su respuesta ahora. Respóndala en BeSalud: ' + PORTAL + '/pacientes/landing';
  }
}

async function atender(cuerpo) {
  for (const entry of cuerpo.entry || []) {
    for (const change of entry.changes || []) {
      for (const mensaje of change.value?.messages || []) {
        // Meta reintenta el webhook si tardamos; evita responder dos veces el mismo mensaje
        if (procesados.has(mensaje.id)) continue;
        procesados.add(mensaje.id);
        if (procesados.size > 5000) procesados.clear();

        const payload = mensaje.button?.payload || mensaje.interactive?.button_reply?.id;
        if (payload && payload.includes(':')) {
          const texto = await responderAsistencia(payload, mensaje.from);
          if (texto) {
            nodoActual.set(mensaje.from, 'inicio');
            await enviar({ messaging_product: 'whatsapp', to: mensaje.from, type: 'text', text: { body: texto } });
            continue;
          }
        }

        const nodo = elegirNodo(mensaje.from, mensaje);
        nodoActual.set(mensaje.from, nodo.id);
        console.log(`${mensaje.from.slice(-4)} -> ${nodo.id}`);
        await enviar(construirMensaje(mensaje.from, nodo));
      }
    }
  }
}

function firmaValida(req, crudo) {
  if (!APP_SECRET) return true;
  const esperada = 'sha256=' + crypto.createHmac('sha256', APP_SECRET).update(crudo).digest('hex');
  const recibida = req.headers['x-hub-signature-256'] || '';
  return recibida.length === esperada.length && crypto.timingSafeEqual(Buffer.from(recibida), Buffer.from(esperada));
}

http.createServer((req, res) => {
  const url = new URL(req.url, 'http://localhost');

  if (req.method === 'GET' && url.pathname === '/webhook') {
    const ok = url.searchParams.get('hub.mode') === 'subscribe' && url.searchParams.get('hub.verify_token') === VERIFY_TOKEN;
    res.writeHead(ok ? 200 : 403);
    return res.end(ok ? url.searchParams.get('hub.challenge') : '');
  }

  if (req.method === 'POST' && url.pathname === '/webhook') {
    const partes = [];
    req.on('data', c => partes.push(c));
    req.on('end', () => {
      const crudo = Buffer.concat(partes);
      if (!firmaValida(req, crudo)) { res.writeHead(401); return res.end(); }
      res.writeHead(200);
      res.end();
      try {
        atender(JSON.parse(crudo)).catch(e => console.error(e));
      } catch (e) {
        console.error('JSON inválido', e.message);
      }
    });
    return;
  }

  res.writeHead(url.pathname === '/' ? 200 : 404);
  res.end(url.pathname === '/' ? 'Bot de WhatsApp de BeSalud activo' : '');
}).listen(PORT, () => console.log(`Bot de WhatsApp escuchando en ${PORT}`));
