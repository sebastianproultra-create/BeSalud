// Datos de demostración para BeSalud: 13 doctores activos con horarios, 1 pendiente, 10 pacientes y ~50 citas.
// Se puede correr varias veces: borra y recrea solo los usuarios @demo.besalud.com (contraseña Demo1234).
//
// Uso (con la app arrancada al menos una vez, para que existan el doctor y paciente de prueba):
//   cd scripts && npm install mongodb@6 bcryptjs@2
//   node seed-demo.js                          -> MongoDB local
//   MURI="mongodb+srv://..." node seed-demo.js -> otra base (cuidado con producción)
const { MongoClient, ObjectId } = require('mongodb');
const bcrypt = require('bcryptjs');

const URI = process.env.MURI || 'mongodb://127.0.0.1:27017';
const DOMINIO = '@demo.besalud.com';
const DOCTOR_PRINCIPAL = 'sebastianfontalvoayola27@gmail.com';
const PACIENTE_PRINCIPAL = 'paciente@besalud.com';
const PASSWORD = bcrypt.hashSync('Demo1234', 10);

// LocalDate se guarda como medianoche de Colombia (UTC-5).
const fechaCol = d => new Date(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate(), 5));
const DIAS = ['SUNDAY', 'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'];
const L_V = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'];

const doctores = [
  ['Laura', 'Gómez', 'Cardiología', 1981, 'Cardióloga con 15 años de experiencia en hipertensión y prevención cardiovascular.',
    [[['MONDAY', 'WEDNESDAY', 'FRIDAY'], '08:00', '12:00'], [['TUESDAY'], '14:00', '17:00']], 30],
  ['Andrés', 'Rojas', 'Dermatología', 1986, 'Dermatólogo clínico: acné, manchas, alergias de la piel y control de lunares.',
    [[['MONDAY', 'THURSDAY'], '09:00', '13:00'], [['SATURDAY'], '08:00', '11:00']], 30],
  ['María Fernanda', 'Ruiz', 'Pediatría', 1984, 'Pediatra. Control de crecimiento, vacunación y enfermedades comunes de la infancia.',
    [[L_V, '07:00', '11:00']], 20],
  ['Carlos', 'Herrera', 'Neurología', 1975, 'Neurólogo especializado en migraña, mareo y trastornos del sueño.',
    [[['TUESDAY', 'THURSDAY'], '08:00', '12:00'], [['TUESDAY', 'THURSDAY'], '14:00', '16:00']], 40],
  ['Valentina', 'Torres', 'Ginecología', 1988, 'Ginecóloga y obstetra. Control prenatal y salud femenina.',
    [[['MONDAY', 'WEDNESDAY'], '13:00', '17:00'], [['FRIDAY'], '08:00', '12:00']], 30],
  ['Julián', 'Castro', 'Gastroenterología', 1979, 'Gastroenterólogo: gastritis, colon irritable y reflujo.',
    [[['WEDNESDAY', 'FRIDAY'], '08:00', '12:00']], 30],
  ['Daniela', 'Moreno', 'Psicología', 1990, 'Psicóloga clínica. Ansiedad, estrés laboral y acompañamiento emocional.',
    [[L_V, '14:00', '18:00']], 60],
  ['Felipe', 'Ortiz', 'Ortopedia y Traumatología', 1982, 'Ortopedista. Lesiones deportivas, dolor de espalda y articulaciones.',
    [[['MONDAY', 'TUESDAY', 'THURSDAY'], '08:00', '12:00']], 30],
  ['Natalia', 'Vargas', 'Oftalmología', 1987, 'Oftalmóloga. Control de la visión, ojo seco y conjuntivitis.',
    [[['TUESDAY', 'FRIDAY'], '09:00', '13:00']], 20],
  ['Santiago', 'Ramírez', 'Otorrinolaringología', 1980, 'Otorrinolaringólogo: oído, nariz, garganta y sinusitis.',
    [[['MONDAY', 'WEDNESDAY'], '08:00', '11:00'], [['THURSDAY'], '14:00', '17:00']], 30],
  ['Camila', 'Restrepo', 'Medicina General', 1992, 'Médica general. Consulta de primera vez y chequeos preventivos.',
    [[L_V, '13:00', '17:00'], [['SATURDAY'], '08:00', '12:00']], 20],
  ['Diego', 'Salazar', 'Neumología', 1977, 'Neumólogo. Asma, tos persistente y enfermedades respiratorias.',
    [[['WEDNESDAY', 'THURSDAY'], '09:00', '12:00']], 30],
];
const doctorPendiente = ['Jorge', 'Pineda', 'Dermatología', 1991, 'Recién registrado, pendiente de aprobación del administrador.'];

const pacientes = [
  ['Juan Pablo', 'Mejía'], ['Sofía', 'Ramírez'], ['Mateo', 'Cárdenas'], ['Isabella', 'López'], ['Samuel', 'Duque'],
  ['Mariana', 'Ospina'], ['Tomás', 'Giraldo'], ['Gabriela', 'Quintero'], ['Nicolás', 'Arango'], ['Luciana', 'Patiño'],
];

const sinTildes = s => s.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase().replace(/\s+/g, '.');

(async () => {
  const c = await MongoClient.connect(URI);
  const db = c.db('besalud');
  const personas = db.collection('personas');
  const horarios = db.collection('horariosAtencion');
  const citas = db.collection('citas');

  // Limpiar lo creado antes por este script.
  const viejos = await personas.find({ email: { $regex: DOMINIO.replace('.', '\\.') + '$' } }).project({ _id: 1 }).toArray();
  const idsViejos = viejos.map(p => String(p._id));
  await citas.deleteMany({ $or: [{ doctorId: { $in: idsViejos } }, { pacienteId: { $in: idsViejos } }] });
  await horarios.deleteMany({ doctorId: { $in: idsViejos } });
  await personas.deleteMany({ _id: { $in: viejos.map(p => p._id) } });

  // Doctores
  const docs = [];
  let ced = 1012000100;
  for (const [nombre, apellido, esp, anio, bio, franjas, duracion] of doctores) {
    const _id = new ObjectId();
    docs.push({ _id, nombre, apellido, esp, franjas, duracion });
    await personas.insertOne({
      _id, especialidad: esp, fechaNacimiento: new Date(Date.UTC(anio, 4, 15, 5)), biografia: bio, estado: 'ACTIVO',
      nombre, apellido, telefono: '31' + String(ced).slice(-8), identificacion: String(ced++),
      email: `dr.${sinTildes(nombre.split(' ')[0])}.${sinTildes(apellido)}${DOMINIO}`, password: PASSWORD,
      role: 'DOCTOR', _class: 'com.gestion.proyectos.modelo.Doctor',
    });
  }
  {
    const [nombre, apellido, esp, anio, bio] = doctorPendiente;
    await personas.insertOne({
      especialidad: esp, fechaNacimiento: new Date(Date.UTC(anio, 1, 3, 5)), biografia: bio, estado: 'INACTIVO',
      nombre, apellido, telefono: '3157778899', identificacion: String(ced++),
      email: `dr.${sinTildes(nombre)}.${sinTildes(apellido)}${DOMINIO}`, password: PASSWORD,
      role: 'DOCTOR', _class: 'com.gestion.proyectos.modelo.Doctor',
    });
  }

  // El doctor de prueba de la app también recibe horario (lunes a viernes, 8 a 12).
  const principal = await personas.findOne({ email: DOCTOR_PRINCIPAL, role: 'DOCTOR' });
  if (principal) {
    await personas.updateOne({ _id: principal._id }, { $set: { estado: 'ACTIVO' } });
    await horarios.deleteMany({ doctorId: String(principal._id) });
    docs.push({ _id: principal._id, nombre: principal.nombre, apellido: principal.apellido,
      esp: principal.especialidad, franjas: [[L_V, '08:00', '12:00']], duracion: 30 });
  }

  for (const d of docs) {
    for (const [dias, ini, fin] of d.franjas) {
      for (const dia of dias) {
        await horarios.insertOne({ doctorId: String(d._id), diaSemana: dia, horaInicio: ini, horaFin: fin,
          duracionCitaMinutos: d.duracion, _class: 'com.gestion.proyectos.modelo.HorarioAtencion' });
      }
    }
  }

  // Pacientes
  const pacs = [];
  let cedP = 1035000200;
  for (const [nombre, apellido] of pacientes) {
    const _id = new ObjectId();
    pacs.push({ _id, nombre });
    await personas.insertOne({ _id, nombre, apellido, telefono: '30' + String(cedP).slice(-8),
      identificacion: String(cedP++), email: `${sinTildes(nombre.split(' ')[0])}.${sinTildes(apellido)}${DOMINIO}`,
      password: PASSWORD, role: 'PACIENTE', _class: 'com.gestion.proyectos.modelo.Paciente' });
  }
  const pacPrincipal = await personas.findOne({ email: PACIENTE_PRINCIPAL, role: 'PACIENTE' });
  if (pacPrincipal) pacs.unshift({ _id: pacPrincipal._id, nombre: pacPrincipal.nombre });

  // Turnos válidos de un doctor en una fecha (respetando su horario y duración).
  const ocupado = new Set();
  const turno = (doc, dia, preferencia) => {
    const nombreDia = DIAS[dia.getDay()];
    const slots = [];
    for (const [dias, ini, fin] of doc.franjas) {
      if (!dias.includes(nombreDia)) continue;
      const [hi, mi] = ini.split(':').map(Number), [hf, mf] = fin.split(':').map(Number);
      for (let m = hi * 60 + mi; m + doc.duracion <= hf * 60 + mf; m += doc.duracion)
        slots.push(String(Math.floor(m / 60)).padStart(2, '0') + ':' + String(m % 60).padStart(2, '0'));
    }
    if (!slots.length) return null;
    for (let k = 0; k < slots.length; k++) {
      const h = slots[(preferencia + k) % slots.length];
      if (!ocupado.has(`${doc._id}|${dia.toDateString()}|${h}`)) return h;
    }
    return null;
  };
  const pacienteLibre = (p, dia, h) => !ocupado.has(`p${p._id}|${dia.toDateString()}|${h}`);

  const motivos = {
    'Cardiología': ['Palpitaciones al hacer ejercicio', 'Control de presión arterial alta'],
    'Dermatología': ['Mancha en la piel que ha crecido', 'Acné persistente en la cara'],
    'Pediatría': ['Control de crecimiento del niño', 'Fiebre y tos en niña de 4 años'],
    'Neurología': ['Migraña frecuente', 'Mareos al levantarse'],
    'Ginecología': ['Control ginecológico anual', 'Dolor menstrual intenso'],
    'Gastroenterología': ['Acidez y ardor de estómago', 'Dolor abdominal después de comer'],
    'Psicología': ['Ansiedad y problemas para dormir', 'Estrés laboral'],
    'Ortopedia y Traumatología': ['Dolor en la rodilla al correr', 'Dolor de espalda baja'],
    'Oftalmología': ['Visión borrosa de lejos', 'Ojos rojos y secos'],
    'Otorrinolaringología': ['Dolor de oído', 'Sinusitis recurrente'],
    'Medicina General': ['Chequeo general', 'Gripa con malestar general'],
    'Neumología': ['Tos persistente hace un mes', 'Control de asma'],
  };
  const dictamenes = [
    ['Cuadro viral leve', 'Hidratación, reposo y acetaminofén si hay fiebre', 'Control si persiste más de 5 días'],
    ['Hipertensión arterial controlada', 'Continuar tratamiento y dieta baja en sal', 'Próximo control en 3 meses'],
    ['Dermatitis de contacto', 'Crema hidratante y evitar el producto irritante', ''],
    ['Gastritis leve', 'Dieta blanda y omeprazol por 4 semanas', 'Evitar café y comidas picantes'],
    ['Esguince de tobillo grado I', 'Reposo, hielo y vendaje por una semana', 'Terapia física si no mejora'],
  ];

  const hoy = new Date(); hoy.setHours(0, 0, 0, 0);
  const nuevas = [];
  const crear = (doc, pac, diasDesdeHoy, estado, prefer) => {
    for (let intento = 0; intento < 14; intento++) {
      const dia = new Date(hoy); dia.setDate(hoy.getDate() + diasDesdeHoy + (diasDesdeHoy >= 0 ? intento : -intento));
      if (diasDesdeHoy >= 0 && dia <= hoy) continue; // futuras: desde mañana
      const h = turno(doc, dia, prefer);
      if (!h || !pacienteLibre(pac, dia, h)) continue;
      ocupado.add(`${doc._id}|${dia.toDateString()}|${h}`);
      ocupado.add(`p${pac._id}|${dia.toDateString()}|${h}`);
      const lista = motivos[doc.esp] || motivos['Medicina General'];
      const cita = { doctorId: String(doc._id), pacienteId: String(pac._id), hora: h, fecha: fechaCol(dia),
        motivo: lista[(prefer + nuevas.length) % lista.length], estado, _class: 'com.gestion.proyectos.modelo.Cita' };
      if (estado === 'COMPLETADA') {
        const [diagnostico, tratamiento, observaciones] = dictamenes[nuevas.length % dictamenes.length];
        cita.dictamen = { diagnostico, tratamiento, observaciones };
      }
      nuevas.push(cita);
      return;
    }
  };

  // Historial (pasado) y agenda (futuro), repartidos entre doctores y pacientes.
  const pasados = ['COMPLETADA', 'COMPLETADA', 'ASISTIO', 'NO_ASISTIO', 'CANCELADA', 'COMPLETADA'];
  let i = 0;
  for (const doc of docs) {
    for (let k = 0; k < 2; k++, i++) crear(doc, pacs[i % pacs.length], -(3 + (i % 18)), pasados[i % pasados.length], i);
    for (let k = 0; k < 2; k++, i++) crear(doc, pacs[(i * 3) % pacs.length], 1 + (i % 10), 'PENDIENTE', i);
  }
  if (nuevas.length) await citas.insertMany(nuevas);

  const resumen = {};
  for (const x of nuevas) resumen[x.estado] = (resumen[x.estado] || 0) + 1;
  console.log(`Doctores activos: ${docs.length} | pendiente: 1 | pacientes nuevos: ${pacientes.length}`);
  console.log(`Horarios: ${await horarios.countDocuments({ doctorId: { $in: docs.map(d => String(d._id)) } })}`);
  console.log('Citas:', nuevas.length, JSON.stringify(resumen));
  console.log('Citas del paciente de prueba:', nuevas.filter(x => pacPrincipal && x.pacienteId === String(pacPrincipal._id)).length);
  await c.close();
})().catch(e => { console.error(e); process.exit(1); });
