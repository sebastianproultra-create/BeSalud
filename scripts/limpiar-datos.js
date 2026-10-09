// Revisa y limpia datos inconsistentes de BeSalud. Por defecto NO modifica nada (solo informa).
//
// Uso:
//   cd scripts && npm install mongodb@6 bcryptjs@2
//   MURI="mongodb+srv://..." DB=besalud node limpiar-datos.js                      -> informe (no cambia nada)
//   MURI="..." node limpiar-datos.js --aplicar                                     -> aplica correcciones seguras
//   MURI="..." node limpiar-datos.js --aplicar --borrar-huerfanos                  -> además borra citas/horarios sin doctor o paciente
//
// Correcciones seguras (--aplicar):
//   1. Especialidad con otra tilde/mayúsculas/espacios -> nombre oficial de la lista (Cardiologia -> Cardiología).
//   2. Correo con mayúsculas o espacios -> minúsculas y recortado (si no choca con otro usuario).
//   3. Contraseña guardada en texto plano (altas de paciente hechas por el admin antes del arreglo) -> bcrypt.
// Solo se informan (hay que decidir a mano): especialidades fuera de la lista, teléfonos e identificaciones inválidos,
// identificaciones o correos repetidos. Los usuarios @demo.besalud.com se ignoran.
const { MongoClient } = require('mongodb');
const bcrypt = require('bcryptjs');

const URI = process.env.MURI || 'mongodb://127.0.0.1:27017';
const DB = process.env.DB || 'besalud';
const APLICAR = process.argv.includes('--aplicar');
const BORRAR = process.argv.includes('--borrar-huerfanos');

const ESPECIALIDADES = [
  'Geriatría', 'Medicina Familiar', 'Medicina General', 'Medicina Interna', 'Pediatría',
  'Alergología e Inmunología', 'Cardiología', 'Dermatología', 'Endocrinología y Metabolismo', 'Gastroenterología',
  'Ginecología y Obstetricia', 'Hematología', 'Infectología', 'Nefrología', 'Neumología', 'Neurología',
  'Nutrición y Dietética', 'Oftalmología', 'Oncología Médica', 'Otorrinolaringología', 'Reumatología', 'Urología',
  'Cirugía Cardiovascular', 'Cirugía General', 'Cirugía Maxilofacial', 'Cirugía Plástica y Estética',
  'Cirugía Vascular', 'Neurocirugía', 'Ortopedia y Traumatología',
  'Fisiatría (Medicina Física)', 'Psicología Clínica', 'Psiquiatría', 'Terapia Ocupacional',
  'Medicina del Deporte', 'Medicina del Dolor y Cuidados Paliativos', 'Medicina Laboral', 'Toxicología',
];
const normalizar = s => String(s).normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/\s+/g, ' ').trim().toLowerCase();
const OFICIAL = new Map(ESPECIALIDADES.map(e => [normalizar(e), e]));

const informe = [];
const nota = (tipo, texto) => informe.push({ tipo, texto });

(async () => {
  const cliente = await MongoClient.connect(URI);
  const db = cliente.db(DB);
  const personas = db.collection('personas');
  const citas = db.collection('citas');
  const horarios = db.collection('horariosAtencion');

  const todas = (await personas.find({ email: { $not: /@demo\.besalud\.com$/i } }).toArray());
  const etiqueta = p => `${p.role} ${p.nombre || ''} ${p.apellido || ''} <${p.email}>`;
  let arreglos = 0;

  // 1 y 2: especialidad y correo
  const correos = new Map();
  for (const p of todas) {
    const clave = String(p.email || '').trim().toLowerCase();
    correos.set(clave, (correos.get(clave) || 0) + 1);
  }
  for (const p of todas) {
    const cambios = {};
    if (p.role === 'DOCTOR') {
      const oficial = p.especialidad ? OFICIAL.get(normalizar(p.especialidad)) : null;
      if (oficial && oficial !== p.especialidad) {
        cambios.especialidad = oficial;
        nota('ARREGLO', `${etiqueta(p)}: especialidad "${p.especialidad}" -> "${oficial}"`);
      } else if (!oficial) {
        nota('REVISAR', `${etiqueta(p)}: especialidad fuera de la lista: "${p.especialidad}"`);
      }
    }
    const limpio = String(p.email || '').trim().toLowerCase();
    if (limpio && limpio !== p.email) {
      if (correos.get(limpio) > 1) nota('REVISAR', `${etiqueta(p)}: correo repetido al pasarlo a minúsculas`);
      else { cambios.email = limpio; nota('ARREGLO', `${etiqueta(p)}: correo -> ${limpio}`); }
    }
    // 3: contraseña en texto plano (un hash bcrypt siempre empieza por $2)
    if (p.password && !/^\$2[aby]\$/.test(p.password)) {
      cambios.password = bcrypt.hashSync(p.password, 10);
      nota('ARREGLO', `${etiqueta(p)}: contraseña estaba sin cifrar -> bcrypt`);
    }
    if (p.telefono && !/^3\d{9}$/.test(String(p.telefono).trim()))
      nota('REVISAR', `${etiqueta(p)}: teléfono inválido "${p.telefono}"`);
    if (p.identificacion && !/^\d{6,10}$/.test(String(p.identificacion).trim()))
      nota('REVISAR', `${etiqueta(p)}: identificación inválida "${p.identificacion}"`);
    if (Object.keys(cambios).length) {
      arreglos++;
      if (APLICAR) await personas.updateOne({ _id: p._id }, { $set: cambios });
    }
  }

  // identificaciones repetidas
  const porId = new Map();
  for (const p of todas.filter(p => p.identificacion)) {
    const k = String(p.identificacion).trim();
    porId.set(k, [...(porId.get(k) || []), etiqueta(p)]);
  }
  for (const [k, quienes] of porId) if (quienes.length > 1) nota('REVISAR', `identificación ${k} repetida: ${quienes.join(' | ')}`);

  // huérfanos
  const idsDoctor = new Set((await personas.find({ role: 'DOCTOR' }, { projection: { _id: 1 } }).toArray()).map(d => String(d._id)));
  const idsPaciente = new Set((await personas.find({ role: 'PACIENTE' }, { projection: { _id: 1 } }).toArray()).map(d => String(d._id)));
  const citasHuerfanas = (await citas.find({}, { projection: { doctorId: 1, pacienteId: 1, fecha: 1, estado: 1 } }).toArray())
    .filter(c => !idsDoctor.has(String(c.doctorId)) || !idsPaciente.has(String(c.pacienteId)));
  const horariosHuerfanos = (await horarios.find({}, { projection: { doctorId: 1 } }).toArray())
    .filter(h => !idsDoctor.has(String(h.doctorId)));
  for (const c of citasHuerfanas) nota('HUÉRFANO', `cita ${c._id} (${c.estado}) sin doctor o paciente existente`);
  for (const h of horariosHuerfanos) nota('HUÉRFANO', `horario ${h._id} sin doctor existente`);
  if (BORRAR && APLICAR) {
    if (citasHuerfanas.length) await citas.deleteMany({ _id: { $in: citasHuerfanas.map(c => c._id) } });
    if (horariosHuerfanos.length) await horarios.deleteMany({ _id: { $in: horariosHuerfanos.map(h => h._id) } });
  }

  for (const n of informe) console.log(`[${n.tipo}] ${n.texto}`);
  console.log('\n--- Resumen ---');
  console.log(`Usuarios revisados: ${todas.length} | con correcciones: ${arreglos}`);
  console.log(`Citas huérfanas: ${citasHuerfanas.length} | horarios huérfanos: ${horariosHuerfanos.length}`);
  console.log(APLICAR
    ? `Correcciones aplicadas.${BORRAR ? ' Huérfanos borrados.' : ' Huérfanos NO borrados (usa --borrar-huerfanos).'}`
    : 'Modo informe: no se modificó nada. Usa --aplicar para corregir.');
  await cliente.close();
})().catch(e => { console.error(e); process.exit(1); });
