const { MongoClient, ObjectId } = require('mongodb');

const PLANTILLA = process.env.PLANTILLA_RECORDATORIO || 'recordatorio_cita_besalud';
const CADA_MINUTOS = Number(process.env.RECORDATORIO_CADA_MINUTOS || 15);
const ZONA = 'America/Bogota';
const PLANTILLA_AGENDADA = process.env.PLANTILLA_AGENDADA || 'cita_agendada_besalud';
const PORTAL = process.env.PORTAL_URL || 'https://besalud-app-production.up.railway.app';

let db;

function fechaBogota(diasDesdeHoy) {
  const d = new Date(Date.now() + diasDesdeHoy * 86400000);
  return d.toLocaleDateString('en-CA', { timeZone: ZONA });
}

// Mongo guarda los LocalDate de Spring como Date a medianoche UTC o como texto según la versión
function filtroFecha(isoDia) {
  const inicio = new Date(`${isoDia}T00:00:00Z`);
  const fin = new Date(inicio.getTime() + 86400000);
  return { $or: [{ fecha: isoDia }, { fecha: { $gte: inicio, $lt: fin } }] };
}

function telefonoWhatsapp(telefono) {
  const digitos = String(telefono || '').replace(/\D/g, '');
  if (digitos.length === 10 && digitos.startsWith('3')) return '57' + digitos;
  if (digitos.length === 12 && digitos.startsWith('57')) return digitos;
  return null;
}

async function buscarPersona(id) {
  if (!id) return null;
  const porId = ObjectId.isValid(id) ? { $or: [{ _id: new ObjectId(id) }, { _id: id }] } : { _id: id };
  return db.collection('personas').findOne(porId);
}

function textoFecha(isoDia) {
  return new Date(`${isoDia}T12:00:00Z`).toLocaleDateString('es-CO', { weekday: 'long', day: 'numeric', month: 'long', timeZone: 'UTC' });
}

function textoHora(hora) {
  if (hora instanceof Date) hora = `${hora.getUTCHours()}:${hora.getUTCMinutes()}`;
  const [h, m] = String(hora || '').split(':').map(Number);
  if (Number.isNaN(h)) return String(hora || '');
  return new Date(Date.UTC(2000, 0, 1, h, m || 0)).toLocaleTimeString('es-CO', { hour: 'numeric', minute: '2-digit', timeZone: 'UTC' });
}

function minutos(hora) {
  if (hora instanceof Date) return hora.getUTCHours() * 60 + hora.getUTCMinutes();
  const [h, m] = String(hora || '').split(':').map(Number);
  return Number.isNaN(h) ? null : h * 60 + (m || 0);
}

function minutosAhoraBogota() {
  const [h, m] = new Date().toLocaleTimeString('en-GB', { timeZone: ZONA, hour12: false }).split(':').map(Number);
  return h * 60 + m;
}

async function citasPorRecordar(dia) {
  return db.collection('citas').find({
    ...filtroFecha(dia),
    estado: 'PENDIENTE',
    'recordatorioWhatsapp.estado': { $exists: false },
  }).toArray();
}

async function revisar(enviar) {
  const hoy = fechaBogota(0);
  const manana = fechaBogota(1);
  const ahora = minutosAhoraBogota();
  // Las citas pedidas para el mismo día también se recuerdan, salvo las que ya pasaron hace más de una hora
  const deHoy = (await citasPorRecordar(hoy)).filter(c => minutos(c.hora) === null || minutos(c.hora) >= ahora - 60);
  const citas = [...deHoy.map(c => ({ ...c, dia: hoy })), ...(await citasPorRecordar(manana)).map(c => ({ ...c, dia: manana }))];

  for (const cita of citas) {
    const [paciente, doctor] = await Promise.all([buscarPersona(cita.pacienteId), buscarPersona(cita.doctorId)]);
    const telefono = telefonoWhatsapp(paciente?.telefono);
    let estado = 'sin_telefono';
    if (telefono) {
      const datos = [
        paciente.nombre || 'paciente',
        doctor ? `Dr(a). ${doctor.nombre} ${doctor.apellido || ''}`.trim() : 'su profesional',
        textoFecha(cita.dia),
        textoHora(cita.hora),
      ];
      let ok = await enviar({
        messaging_product: 'whatsapp',
        to: telefono,
        type: 'template',
        template: {
          name: PLANTILLA,
          language: { code: 'es' },
          components: [
            { type: 'body', parameters: datos.map(text => ({ type: 'text', text })) },
            { type: 'button', sub_type: 'quick_reply', index: '0', parameters: [{ type: 'payload', payload: `CONFIRMAR:${cita._id}` }] },
            { type: 'button', sub_type: 'quick_reply', index: '1', parameters: [{ type: 'payload', payload: `CANCELAR:${cita._id}` }] },
          ],
        },
      });
      // Sin plantilla aprobada solo llega si el paciente escribió al bot en las últimas 24 h
      if (!ok) ok = await enviar({
        messaging_product: 'whatsapp',
        to: telefono,
        type: 'interactive',
        interactive: {
          type: 'button',
          header: { type: 'text', text: 'Recordatorio de cita' },
          body: { text: `Hola ${datos[0]}, le recordamos su cita en BeSalud con ${datos[1]} el ${datos[2]} a las ${datos[3]}. ¿Asistirá?` },
          footer: { text: 'BeSalud' },
          action: { buttons: [
            { type: 'reply', reply: { id: `CONFIRMAR:${cita._id}`, title: 'Confirmar' } },
            { type: 'reply', reply: { id: `CANCELAR:${cita._id}`, title: 'Cancelar' } },
          ] },
        },
      });
      estado = ok ? 'enviado' : 'fallido';
    }
    const intentos = (cita.recordatorioWhatsapp?.intentos || 0) + 1;
    // Un fallo puede ser pasajero (plantilla aún en revisión); tras 3 se deja de insistir
    const cambios = estado === 'fallido' && intentos < 3
      ? { 'recordatorioWhatsapp.intentos': intentos }
      : { 'recordatorioWhatsapp.estado': estado, 'recordatorioWhatsapp.intentos': intentos, 'recordatorioWhatsapp.fecha': new Date() };
    await db.collection('citas').updateOne({ _id: cita._id }, { $set: cambios });
    console.log(`recordatorio ${String(cita._id).slice(-6)} -> ${estado} (intento ${intentos})`);
  }
}

async function responderBoton(payload) {
  const [accion, id] = String(payload).split(':');
  if (!db || !id || !['CONFIRMAR', 'CANCELAR'].includes(accion)) return null;
  const filtro = { _id: ObjectId.isValid(id) ? new ObjectId(id) : id };
  const respuesta = accion === 'CONFIRMAR' ? 'CONFIRMADA' : 'QUIERE_CANCELAR';
  const cita = await db.collection('citas').findOneAndUpdate(filtro, { $set: { 'recordatorioWhatsapp.respuesta': respuesta, 'recordatorioWhatsapp.respondida': new Date() } });
  if (!cita) return null;
  const [paciente, doctor] = await Promise.all([buscarPersona(cita.pacienteId), buscarPersona(cita.doctorId)]);
  const detalle = [
    doctor && `👨‍⚕️ Dr(a). ${doctor.nombre} ${doctor.apellido || ''}`.trim(),
    doctor?.especialidad && `🩺 ${doctor.especialidad}`,
    `🗓️ ${textoFecha(diaDeCita(cita.fecha))}`,
    `⏰ ${textoHora(cita.hora)}`,
    cita.motivo && `📝 ${cita.motivo}`,
  ].filter(Boolean).join('\n');
  const nombre = paciente?.nombre ? `, ${paciente.nombre}` : '';
  return accion === 'CONFIRMAR'
    ? `✅ *¡Listo${nombre}! Su cita quedó confirmada.*\n\n${detalle}\n\nLe recomendamos llegar 10 minutos antes. Puede ver sus citas en: ${PORTAL}/login\n\nSi necesita algo más, escriba *menú*.`
    : `❌ *Cancelar cita*\n\n${detalle}\n\nPara cancelar o reprogramar ingrese a "Mis citas" en BeSalud; así se aplican las condiciones de devolución del abono:\n${PORTAL}/login\n\nSi necesita algo más, escriba *menú*.`;
}

function diaDeCita(fecha) {
  return fecha instanceof Date ? fecha.toLocaleDateString('en-CA', { timeZone: ZONA }) : String(fecha);
}

async function avisarAgendada(enviar, cita) {
  const [paciente, doctor] = await Promise.all([buscarPersona(cita.pacienteId), buscarPersona(cita.doctorId)]);
  const telefono = telefonoWhatsapp(paciente?.telefono);
  if (!telefono) return;
  const datos = [
    paciente.nombre || 'paciente',
    doctor ? `Dr(a). ${doctor.nombre} ${doctor.apellido || ''}`.trim() : 'su profesional',
    doctor?.especialidad || 'Consulta',
    textoFecha(diaDeCita(cita.fecha)),
    textoHora(cita.hora),
  ];
  let ok = await enviar({
    messaging_product: 'whatsapp',
    to: telefono,
    type: 'template',
    template: { name: PLANTILLA_AGENDADA, language: { code: 'es' }, components: [{ type: 'body', parameters: datos.map(text => ({ type: 'text', text })) }] },
  });
  if (!ok) ok = await enviar({
    messaging_product: 'whatsapp',
    to: telefono,
    type: 'text',
    text: { body: `📅 *Cita agendada en BeSalud*\n\nHola ${datos[0]}, su cita quedó agendada:\n\n👨‍⚕️ ${datos[1]}\n🩺 ${datos[2]}\n🗓️ ${datos[3]}\n⏰ ${datos[4]}${cita.motivo ? `\n📝 ${cita.motivo}` : ''}\n\nPuede verla o cancelarla en "Mis citas": ${PORTAL}/login\n\nLe recordaremos un día antes.` },
  });
  await db.collection('citas').updateOne({ _id: cita._id }, { $set: { 'avisoAgendadaWhatsapp': { estado: ok ? 'enviado' : 'fallido', fecha: new Date() } } });
  console.log(`cita agendada ${String(cita._id).slice(-6)} -> ${ok ? 'enviado' : 'fallido'}`);
}

function escucharNuevas(enviar) {
  // Change stream de Atlas: avisa apenas el portal inserta la cita, sin esperar el ciclo de 15 min
  db.collection('citas').watch([{ $match: { operationType: 'insert' } }])
    .on('change', ev => avisarAgendada(enviar, ev.fullDocument).catch(e => console.error('Error avisando cita agendada', e.message)))
    .on('error', e => console.error('Se cayó el aviso de citas nuevas', e.message));
}

async function iniciar(enviar) {
  const uri = process.env.MONGODB_URI;
  if (!uri) return console.log('Recordatorios desactivados: falta MONGODB_URI');
  const cliente = new MongoClient(uri);
  await cliente.connect();
  db = cliente.db();
  console.log(`Recordatorios activos (base ${db.databaseName}, cada ${CADA_MINUTOS} min)`);
  escucharNuevas(enviar);
  const ciclo = () => revisar(enviar).catch(e => console.error('Error en recordatorios', e.message));
  ciclo();
  setInterval(ciclo, CADA_MINUTOS * 60000);
}

module.exports = { iniciar, responderBoton };
