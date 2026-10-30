const http = require('http');
const fs = require('fs');
const path = require('path');
const QRCode = require('qrcode');
const { Client, LocalAuth } = require('whatsapp-web.js');

const PORT = process.env.PORT || 3000;
const SECRETO = process.env.WHATSAPP_BOT_SECRET;
const PORTAL = (process.env.PORTAL_URL || 'https://besalud-app-production.up.railway.app').replace(/\/+$/, '');
const DATOS = process.env.DATA_DIR || path.join(__dirname, 'datos');
const PENDIENTES = path.join(DATOS, 'pendientes.json');
const HORAS_PENDIENTE = 72;

const nodos = JSON.parse(fs.readFileSync(path.join(__dirname, 'chatbot-landing.json'), 'utf8')).nodos;
const arbol = new Map(nodos.map(n => [n.id, n]));
const nodoActual = new Map();

// Preguntas "¿Vas a ir?" esperando respuesta, por número; en disco para sobrevivir a un reinicio
let pendientes = {};
try { pendientes = JSON.parse(fs.readFileSync(PENDIENTES, 'utf8')); } catch { }
const guardarPendientes = () => fs.writeFile(PENDIENTES, JSON.stringify(pendientes), () => { });

let qrActual = null;
let listo = false;

const client = new Client({
  authStrategy: new LocalAuth({ dataPath: path.join(DATOS, 'sesion') }),
  puppeteer: {
    headless: true,
    executablePath: process.env.PUPPETEER_EXECUTABLE_PATH || undefined,
    args: ['--no-sandbox', '--disable-setuid-sandbox', '--disable-dev-shm-usage', '--disable-gpu', '--no-zygote'],
  },
});

client.on('qr', qr => { qrActual = qr; listo = false; console.log('Hay un QR nuevo para vincular el número: abra /qr'); });
client.on('ready', () => { qrActual = null; listo = true; console.log('WhatsApp Web conectado'); });
client.on('disconnected', motivo => { listo = false; console.warn('WhatsApp desconectado:', motivo); client.initialize(); });
client.on('auth_failure', m => console.error('Falló la autenticación:', m));

function normalizar(t) {
  return t.toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '').trim();
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

function elegirNodo(de, texto) {
  const actual = arbol.get(nodoActual.get(de)) || arbol.get('inicio');
  const t = normalizar(texto);
  if (!nodoActual.has(de) || /^(0|hola|menu|inicio|empezar|buenas)\b/.test(t)) return arbol.get('inicio');
  const n = parseInt(t, 10);
  if (n >= 1 && n <= (actual.opciones || []).length) return arbol.get(actual.opciones[n - 1]);
  return buscarPorPalabraClave(texto) || { ...actual, respuesta: 'No le entendí. Responda con el número de una opción o escriba "menú".' };
}

function textoNodo(nodo) {
  const lineas = [`*BeSalud*`, '', nodo.respuesta];
  if (nodo.accion) lineas.push('', `👉 ${nodo.accion.texto}: ${PORTAL}${nodo.accion.url}`);
  const opciones = (nodo.opciones || []).map(id => arbol.get(id)).filter(Boolean);
  if (opciones.length || nodo.id !== 'inicio') lineas.push('');
  opciones.forEach((h, i) => lineas.push(`${i + 1}. ${h.pregunta}`));
  if (nodo.id !== 'inicio') lineas.push('0. Menú principal');
  return lineas.join('\n');
}

async function numeroDe(msg) {
  // WhatsApp ya identifica a algunos usuarios con un id privado (@lid): el número real sale del contacto
  const contacto = await msg.getContact();
  return contacto.number || msg.from.replace(/@.*/, '');
}

async function responderAsistencia(id, telefono) {
  try {
    const res = await fetch(`${PORTAL}/whatsapp/asistencia`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Bot-Secret': SECRETO },
      body: JSON.stringify({ payload: id, telefono }),
    });
    if (!res.ok) return console.error('El portal no aceptó la respuesta', res.status) || null;
    return (await res.json()).texto;
  } catch (e) {
    console.error('No se pudo contactar al portal', e.message);
    return `No pudimos registrar su respuesta ahora. Respóndala en BeSalud: ${PORTAL}/pacientes/landing`;
  }
}

client.on('message', async msg => {
  if (msg.fromMe || msg.isStatus || msg.from.endsWith('@g.us') || msg.from === 'status@broadcast') return;
  if (msg.type !== 'chat') return;
  const texto = (msg.body || '').trim();
  const telefono = await numeroDe(msg);

  const pendiente = pendientes[telefono];
  const n = parseInt(texto, 10);
  if (pendiente && Date.now() < pendiente.expira && n >= 1 && n <= pendiente.opciones.length) {
    const respuesta = await responderAsistencia(pendiente.opciones[n - 1].id, telefono);
    if (respuesta) {
      delete pendientes[telefono];
      guardarPendientes();
      nodoActual.set(msg.from, 'inicio');
      await msg.reply(respuesta);
      return;
    }
  }

  const nodo = elegirNodo(msg.from, texto);
  nodoActual.set(msg.from, nodo.id);
  console.log(`...${telefono.slice(-4)} -> ${nodo.id}`);
  await msg.reply(textoNodo(nodo));
});

async function enviar({ telefono, texto, opciones = [] }) {
  const digitos = String(telefono || '').replace(/\D/g, '');
  const wid = await client.getNumberId(digitos);
  if (!wid) return { ok: false, motivo: 'El número no tiene WhatsApp' };
  let cuerpo = texto;
  if (opciones.length) {
    cuerpo += '\n\nResponda con el número:\n' + opciones.map((o, i) => `${i + 1}. ${o.titulo}`).join('\n');
    pendientes[digitos] = { opciones, expira: Date.now() + HORAS_PENDIENTE * 3600_000 };
    guardarPendientes();
  }
  await client.sendMessage(wid._serialized, cuerpo);
  return { ok: true };
}

function leerCuerpo(req) {
  return new Promise((ok, mal) => {
    let s = '';
    req.on('data', c => { s += c; if (s.length > 100_000) req.destroy(); });
    req.on('end', () => { try { ok(JSON.parse(s || '{}')); } catch (e) { mal(e); } });
  });
}

const autorizado = clave => SECRETO && clave === SECRETO;

http.createServer(async (req, res) => {
  const url = new URL(req.url, 'http://localhost');

  if (req.method === 'GET' && url.pathname === '/qr') {
    if (!autorizado(url.searchParams.get('clave'))) { res.writeHead(401); return res.end(); }
    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
    if (listo) return res.end('<h2>✅ El número ya está vinculado a BeSalud.</h2>');
    if (!qrActual) return res.end('<meta http-equiv="refresh" content="3"><h2>Generando el código QR…</h2>');
    const img = await QRCode.toDataURL(qrActual, { width: 360, margin: 2 });
    return res.end(`<meta http-equiv="refresh" content="20"><h2>Escanee con WhatsApp → Dispositivos vinculados</h2><img src="${img}">`);
  }

  if (req.method === 'POST' && url.pathname === '/enviar') {
    if (!autorizado(req.headers['x-bot-secret'])) { res.writeHead(401); return res.end(); }
    if (!listo) { res.writeHead(503); return res.end('WhatsApp no está conectado'); }
    try {
      const r = await enviar(await leerCuerpo(req));
      res.writeHead(r.ok ? 200 : 422, { 'Content-Type': 'application/json' });
      return res.end(JSON.stringify(r));
    } catch (e) {
      console.error('Error enviando', e.message);
      res.writeHead(500); return res.end();
    }
  }

  res.writeHead(url.pathname === '/' ? 200 : 404);
  res.end(url.pathname === '/' ? `Bot de WhatsApp Web de BeSalud: ${listo ? 'conectado' : 'sin vincular'}` : '');
}).listen(PORT, () => console.log(`Escuchando en ${PORT}`));

fs.mkdirSync(DATOS, { recursive: true });
client.initialize();
