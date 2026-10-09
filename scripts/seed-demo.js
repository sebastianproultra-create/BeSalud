// Datos de demostración para BeSalud: doctores activos con horarios en 15 especialidades, algunos pendientes
// de aprobación, pacientes y citas (historial con dictámenes + agenda futura), todo sin choques de horario.
// Se puede correr varias veces: borra y recrea solo los usuarios @demo.besalud.com (contraseña Demo1234).
//
// Uso (con la app arrancada al menos una vez, para que existan el doctor y paciente de prueba):
//   cd scripts && npm install mongodb@6 bcryptjs@2
//   node seed-demo.js                          -> MongoDB local
//   MURI="mongodb+srv://..." node seed-demo.js -> otra base (cuidado con producción)
//   PACIENTES=120 node seed-demo.js            -> cambiar la cantidad de pacientes (por defecto 80)
const { MongoClient, ObjectId } = require('mongodb');
const bcrypt = require('bcryptjs');

const URI = process.env.MURI || 'mongodb://127.0.0.1:27017';
const DOMINIO = '@demo.besalud.com';
const DOCTOR_PRINCIPAL = 'sebastianfontalvoayola27@gmail.com';
const PACIENTE_PRINCIPAL = 'paciente@besalud.com';
const PASSWORD = bcrypt.hashSync('Demo1234', 10);
const N_PACIENTES = Number(process.env.PACIENTES || 80);
const CITAS_PASADAS_POR_DOCTOR = 3;
const CITAS_FUTURAS_POR_DOCTOR = 4;

// Generador pseudoaleatorio con semilla: los mismos datos en cada ejecución.
let semilla = 20261009;
const azar = () => ((semilla = (semilla * 1103515245 + 12345) % 2147483648) / 2147483648);
const elegir = lista => lista[Math.floor(azar() * lista.length)];

// LocalDate se guarda como medianoche de Colombia (UTC-5).
const fechaCol = d => new Date(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate(), 5));
const DIAS = ['SUNDAY', 'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'];
const L_V = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'];

const PLANTILLAS_HORARIO = [
  [[L_V, '08:00', '12:00']],
  [[L_V, '14:00', '18:00']],
  [[['MONDAY', 'WEDNESDAY', 'FRIDAY'], '07:00', '12:00'], [['TUESDAY', 'THURSDAY'], '14:00', '17:00']],
  [[['TUESDAY', 'THURSDAY'], '08:00', '12:00'], [['TUESDAY', 'THURSDAY'], '14:00', '18:00']],
  [[['MONDAY', 'TUESDAY', 'WEDNESDAY'], '13:00', '17:00'], [['SATURDAY'], '08:00', '12:00']],
  [[['WEDNESDAY', 'THURSDAY', 'FRIDAY'], '08:00', '13:00']],
  [[L_V, '06:30', '10:30']],
  [[['MONDAY', 'THURSDAY'], '09:00', '13:00'], [['FRIDAY', 'SATURDAY'], '08:00', '11:00']],
];

// [especialidad, cuántos doctores, duración de cita, perfil, motivos de consulta, dictámenes]
const ESPECIALIDADES = [
  ['Medicina General', 6, 20, 'consulta de primera vez, chequeos preventivos y enfermedades comunes',
    ['Chequeo general anual', 'Gripa con malestar general', 'Dolor de cabeza frecuente', 'Certificado médico'],
    [['Infección respiratoria viral', 'Hidratación, reposo y acetaminofén si hay fiebre', 'Control si persiste más de 5 días'],
     ['Paciente sano', 'Mantener hábitos saludables', 'Control anual']]],
  ['Cardiología', 4, 30, 'hipertensión, arritmias y prevención cardiovascular',
    ['Palpitaciones al hacer ejercicio', 'Control de presión arterial alta', 'Dolor en el pecho al caminar'],
    [['Hipertensión arterial controlada', 'Continuar losartán y dieta baja en sal', 'Próximo control en 3 meses'],
     ['Extrasístoles benignas', 'Reducir cafeína y estrés', 'Holter si los síntomas aumentan']]],
  ['Dermatología', 4, 30, 'acné, manchas, alergias de la piel y control de lunares',
    ['Mancha en la piel que ha crecido', 'Acné persistente en la cara', 'Picazón y ronchas en los brazos'],
    [['Dermatitis de contacto', 'Crema hidratante y evitar el producto irritante', ''],
     ['Acné moderado', 'Gel de peróxido de benzoilo en la noche', 'Control en 8 semanas']]],
  ['Pediatría', 4, 20, 'control de crecimiento, vacunación y enfermedades de la infancia',
    ['Control de crecimiento del niño', 'Fiebre y tos en niña de 4 años', 'Vacunas pendientes'],
    [['Niño sano, crecimiento adecuado', 'Continuar alimentación balanceada', 'Próximas vacunas a los 5 años'],
     ['Faringitis viral', 'Líquidos abundantes y acetaminofén según peso', 'Volver si la fiebre dura más de 3 días']]],
  ['Neurología', 3, 40, 'migraña, mareo, epilepsia y trastornos del sueño',
    ['Migraña frecuente', 'Mareos al levantarse', 'Hormigueo en las manos'],
    [['Migraña sin aura', 'Diario de crisis y analgésico al inicio del dolor', 'Evitar ayunos prolongados'],
     ['Vértigo posicional benigno', 'Maniobras de reposicionamiento en casa', '']]],
  ['Ginecología y Obstetricia', 4, 30, 'control prenatal, planificación y salud femenina',
    ['Control ginecológico anual', 'Dolor menstrual intenso', 'Control prenatal'],
    [['Control normal', 'Citología al día', 'Repetir en un año'],
     ['Dismenorrea primaria', 'Ibuprofeno los primeros días del ciclo', 'Ecografía si no mejora']]],
  ['Gastroenterología', 3, 30, 'gastritis, colon irritable y reflujo',
    ['Acidez y ardor de estómago', 'Dolor abdominal después de comer', 'Diarrea frecuente'],
    [['Gastritis leve', 'Dieta blanda y omeprazol por 4 semanas', 'Evitar café y comidas picantes'],
     ['Colon irritable', 'Más fibra y agua, menos ultraprocesados', 'Control en 2 meses']]],
  ['Psicología Clínica', 4, 60, 'ansiedad, estrés, duelo y acompañamiento emocional',
    ['Ansiedad y problemas para dormir', 'Estrés laboral', 'Tristeza persistente'],
    [['Trastorno de ansiedad leve', 'Psicoterapia semanal y técnicas de respiración', 'Seguimiento en 1 semana'],
     ['Estrés laboral', 'Higiene del sueño y pausas activas', '']]],
  ['Psiquiatría', 2, 45, 'depresión, trastorno bipolar y manejo farmacológico de la ansiedad',
    ['Depresión que no mejora', 'Ataques de pánico'],
    [['Episodio depresivo moderado', 'Inicio de tratamiento y psicoterapia', 'Control en 4 semanas']]],
  ['Ortopedia y Traumatología', 4, 30, 'lesiones deportivas, fracturas, columna y articulaciones',
    ['Dolor en la rodilla al correr', 'Dolor de espalda baja', 'Torcedura de tobillo'],
    [['Esguince de tobillo grado I', 'Reposo, hielo y vendaje por una semana', 'Terapia física si no mejora'],
     ['Lumbalgia mecánica', 'Ejercicios de estiramiento y analgésico', '']]],
  ['Oftalmología', 3, 20, 'control de la visión, ojo seco y conjuntivitis',
    ['Visión borrosa de lejos', 'Ojos rojos y secos', 'Revisión de fórmula'],
    [['Miopía leve', 'Gafas con nueva fórmula', 'Control en un año'],
     ['Síndrome de ojo seco', 'Lágrimas artificiales cada 6 horas', '']]],
  ['Otorrinolaringología', 3, 30, 'oído, nariz, garganta y sinusitis',
    ['Dolor de oído', 'Sinusitis recurrente', 'Ronquera hace dos semanas'],
    [['Otitis externa', 'Gotas óticas por 7 días', 'No mojar el oído'],
     ['Rinosinusitis aguda', 'Lavados nasales con suero', 'Control si hay fiebre']]],
  ['Neumología', 3, 30, 'asma, tos persistente y enfermedades respiratorias',
    ['Tos persistente hace un mes', 'Control de asma', 'Ahogo al subir escaleras'],
    [['Asma controlada', 'Continuar inhalador', 'Espirometría en 6 meses']]],
  ['Endocrinología y Metabolismo', 3, 30, 'diabetes, tiroides y control de peso',
    ['Control de diabetes', 'Cansancio y aumento de peso', 'Resultados de tiroides'],
    [['Diabetes tipo 2 en buen control', 'Continuar metformina y actividad física', 'Hemoglobina glicosilada en 3 meses'],
     ['Hipotiroidismo', 'Levotiroxina en ayunas', 'Control de TSH en 6 semanas']]],
  ['Urología', 2, 30, 'vías urinarias, próstata y cálculos renales',
    ['Ardor al orinar', 'Control de próstata'],
    [['Infección urinaria baja', 'Antibiótico por 5 días y abundante agua', 'Urocultivo de control']]],
];

const NOMBRES_F = ['Laura', 'María Fernanda', 'Valentina', 'Daniela', 'Natalia', 'Camila', 'Paula', 'Andrea', 'Carolina',
  'Juliana', 'Alejandra', 'Catalina', 'Diana', 'Lorena', 'Mónica', 'Ángela', 'Sara', 'Manuela', 'Luisa', 'Adriana'];
const NOMBRES_M = ['Andrés', 'Carlos', 'Julián', 'Felipe', 'Santiago', 'Diego', 'Sebastián', 'Juan Pablo', 'Mateo',
  'Alejandro', 'Daniel', 'Ricardo', 'Mauricio', 'Javier', 'Esteban', 'Camilo', 'David', 'Óscar', 'Gustavo', 'Hernán'];
const APELLIDOS = ['Gómez', 'Rojas', 'Ruiz', 'Herrera', 'Torres', 'Castro', 'Moreno', 'Ortiz', 'Vargas', 'Ramírez',
  'Restrepo', 'Salazar', 'Mejía', 'Cárdenas', 'López', 'Duque', 'Ospina', 'Giraldo', 'Quintero', 'Arango', 'Patiño',
  'Martínez', 'Rodríguez', 'García', 'Hernández', 'Jiménez', 'Muñoz', 'Álvarez', 'Romero', 'Valencia', 'Zapata',
  'Correa', 'Henao', 'Montoya', 'Sánchez', 'Pérez', 'Gutiérrez', 'Rincón', 'Bermúdez', 'Escobar'];

const sinTildes = s => s.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase().replace(/\s+/g, '.');
const emailsUsados = new Set();
const emailUnico = base => {
  let email = base + DOMINIO, n = 2;
  while (emailsUsados.has(email)) email = base + (n++) + DOMINIO;
  emailsUsados.add(email);
  return email;
};
const nombresUsados = new Set();
const personaNueva = () => {
  for (;;) {
    const mujer = azar() < 0.5;
    const nombre = elegir(mujer ? NOMBRES_F : NOMBRES_M);
    const apellido = elegir(APELLIDOS) + ' ' + elegir(APELLIDOS);
    if (!nombresUsados.has(nombre + apellido)) { nombresUsados.add(nombre + apellido); return { nombre, apellido, mujer }; }
  }
};

(async () => {
  const c = await MongoClient.connect(URI);
  const db = c.db('besalud');
  const personas = db.collection('personas');
  const horarios = db.collection('horariosAtencion');
  const citas = db.collection('citas');

  // Limpiar lo creado antes por este script.
  const viejos = await personas.find({ email: { $regex: DOMINIO.replace(/\./g, '\\.') + '$' } }).project({ _id: 1 }).toArray();
  const idsViejos = viejos.map(p => String(p._id));
  await citas.deleteMany({ $or: [{ doctorId: { $in: idsViejos } }, { pacienteId: { $in: idsViejos } }] });
  await horarios.deleteMany({ doctorId: { $in: idsViejos } });
  await personas.deleteMany({ _id: { $in: viejos.map(p => p._id) } });

  // Doctores activos
  const docs = [], docsInsert = [], horariosInsert = [];
  let cedula = 1012000100, plantilla = 0;
  for (const [esp, cantidad, duracion, perfil, motivos, dictamenes] of ESPECIALIDADES) {
    for (let k = 0; k < cantidad; k++) {
      const { nombre, apellido, mujer } = personaNueva();
      const _id = new ObjectId();
      const franjas = PLANTILLAS_HORARIO[plantilla++ % PLANTILLAS_HORARIO.length];
      const anios = 6 + Math.floor(azar() * 25);
      docs.push({ _id, esp, franjas, duracion, motivos, dictamenes });
      docsInsert.push({
        _id, especialidad: esp, fechaNacimiento: new Date(Date.UTC(1958 + Math.floor(azar() * 38), Math.floor(azar() * 12), 1 + Math.floor(azar() * 27), 5)),
        biografia: `${mujer ? 'Médica' : 'Médico'} especialista en ${esp.toLowerCase()} con ${anios} años de experiencia en ${perfil}.`,
        estado: 'ACTIVO', nombre, apellido, telefono: '3' + String(100000000 + Math.floor(azar() * 899999999)),
        identificacion: String(cedula++), email: emailUnico(`dr.${sinTildes(nombre.split(' ')[0])}.${sinTildes(apellido.split(' ')[0])}`),
        password: PASSWORD, role: 'DOCTOR', _class: 'com.gestion.proyectos.modelo.Doctor',
      });
      for (const [dias, ini, fin] of franjas)
        for (const dia of dias)
          horariosInsert.push({ doctorId: String(_id), diaSemana: dia, horaInicio: ini, horaFin: fin,
            duracionCitaMinutos: duracion, _class: 'com.gestion.proyectos.modelo.HorarioAtencion' });
    }
  }
  // Doctores recién registrados, pendientes de aprobación (sin horario).
  for (const esp of ['Dermatología', 'Cardiología', 'Pediatría', 'Nutrición y Dietética']) {
    const { nombre, apellido } = personaNueva();
    docsInsert.push({
      especialidad: esp, fechaNacimiento: new Date(Date.UTC(1993, 2, 10, 5)),
      biografia: 'Recién registrado, pendiente de aprobación del administrador.', estado: 'INACTIVO',
      nombre, apellido, telefono: '3' + String(100000000 + Math.floor(azar() * 899999999)), identificacion: String(cedula++),
      email: emailUnico(`dr.${sinTildes(nombre.split(' ')[0])}.${sinTildes(apellido.split(' ')[0])}`),
      password: PASSWORD, role: 'DOCTOR', _class: 'com.gestion.proyectos.modelo.Doctor',
    });
  }
  await personas.insertMany(docsInsert);

  // El doctor de prueba de la app también recibe horario (lunes a viernes, 8 a 12).
  const principal = await personas.findOne({ email: DOCTOR_PRINCIPAL, role: 'DOCTOR' });
  if (principal) {
    await personas.updateOne({ _id: principal._id }, { $set: { estado: 'ACTIVO' } });
    await horarios.deleteMany({ doctorId: String(principal._id) });
    const franjas = [[L_V, '08:00', '12:00']];
    const cfg = ESPECIALIDADES.find(e => e[0] === principal.especialidad) || ESPECIALIDADES[0];
    docs.push({ _id: principal._id, esp: principal.especialidad, franjas, duracion: 30, motivos: cfg[4], dictamenes: cfg[5] });
    for (const dia of L_V)
      horariosInsert.push({ doctorId: String(principal._id), diaSemana: dia, horaInicio: '08:00', horaFin: '12:00',
        duracionCitaMinutos: 30, _class: 'com.gestion.proyectos.modelo.HorarioAtencion' });
  }
  await horarios.insertMany(horariosInsert);

  // Pacientes
  const pacs = [], pacsInsert = [];
  let cedulaP = 1035000200;
  for (let k = 0; k < N_PACIENTES; k++) {
    const { nombre, apellido } = personaNueva();
    const _id = new ObjectId();
    pacs.push({ _id });
    pacsInsert.push({ _id, nombre, apellido, telefono: '3' + String(100000000 + Math.floor(azar() * 899999999)),
      identificacion: String(cedulaP++), email: emailUnico(`${sinTildes(nombre.split(' ')[0])}.${sinTildes(apellido.split(' ')[0])}`),
      password: PASSWORD, role: 'PACIENTE', _class: 'com.gestion.proyectos.modelo.Paciente' });
  }
  await personas.insertMany(pacsInsert);
  const pacPrincipal = await personas.findOne({ email: PACIENTE_PRINCIPAL, role: 'PACIENTE' });
  if (pacPrincipal) pacs.unshift({ _id: pacPrincipal._id });

  // Citas: respetan el horario y la duración de cada doctor, sin choques de doctor ni de paciente.
  const ocupado = new Set();
  const turnosDelDia = (doc, dia) => {
    const nombreDia = DIAS[dia.getDay()], slots = [];
    for (const [dias, ini, fin] of doc.franjas) {
      if (!dias.includes(nombreDia)) continue;
      const [hi, mi] = ini.split(':').map(Number), [hf, mf] = fin.split(':').map(Number);
      for (let m = hi * 60 + mi; m + doc.duracion <= hf * 60 + mf; m += doc.duracion)
        slots.push(String(Math.floor(m / 60)).padStart(2, '0') + ':' + String(m % 60).padStart(2, '0'));
    }
    return slots;
  };
  // El paciente no puede tener dos citas que se crucen ese día (aunque sean con doctores distintos).
  const agendaPaciente = new Map();
  const minutos = h => { const [hh, mm] = h.split(':').map(Number); return hh * 60 + mm; };
  const libre = (doc, pac, dia, h) => {
    if (ocupado.has(`${doc._id}|${dia.toDateString()}|${h}`)) return false;
    const ini = minutos(h), fin = ini + doc.duracion;
    return !(agendaPaciente.get(`${pac._id}|${dia.toDateString()}`) || []).some(([a, b]) => ini < b && fin > a);
  };

  const hoy = new Date(); hoy.setHours(0, 0, 0, 0);
  const ahora = new Date();
  const nuevas = [];
  const crear = (doc, pac, desplazamiento, estado) => {
    for (let intento = 0; intento < 20; intento++) {
      const dia = new Date(hoy);
      dia.setDate(hoy.getDate() + desplazamiento + (desplazamiento >= 0 ? intento : -intento));
      const slots = turnosDelDia(doc, dia);
      if (!slots.length) continue;
      const inicio = Math.floor(azar() * slots.length);
      for (let s = 0; s < slots.length; s++) {
        const h = slots[(inicio + s) % slots.length];
        const [hh, mm] = h.split(':').map(Number);
        const momento = new Date(dia); momento.setHours(hh, mm);
        if (desplazamiento >= 0 && momento <= ahora) continue;
        if (!libre(doc, pac, dia, h)) continue;
        ocupado.add(`${doc._id}|${dia.toDateString()}|${h}`);
        const clave = `${pac._id}|${dia.toDateString()}`;
        agendaPaciente.set(clave, [...(agendaPaciente.get(clave) || []), [minutos(h), minutos(h) + doc.duracion]]);
        const cita = { doctorId: String(doc._id), pacienteId: String(pac._id), hora: h, fecha: fechaCol(dia),
          motivo: elegir(doc.motivos), estado, _class: 'com.gestion.proyectos.modelo.Cita' };
        if (estado === 'COMPLETADA') {
          const [diagnostico, tratamiento, observaciones] = elegir(doc.dictamenes);
          cita.dictamen = { diagnostico, tratamiento, observaciones };
        }
        nuevas.push(cita);
        return;
      }
    }
  };

  const estadosPasados = ['COMPLETADA', 'COMPLETADA', 'COMPLETADA', 'ASISTIO', 'NO_ASISTIO', 'CANCELADA'];
  for (const doc of docs) {
    for (let k = 0; k < CITAS_PASADAS_POR_DOCTOR; k++)
      crear(doc, elegir(pacs), -(1 + Math.floor(azar() * 30)), elegir(estadosPasados));
    for (let k = 0; k < CITAS_FUTURAS_POR_DOCTOR; k++)
      crear(doc, elegir(pacs), Math.floor(azar() * 14), azar() < 0.9 ? 'PENDIENTE' : 'CANCELADA');
  }
  // Historial visible para el paciente de prueba.
  if (pacPrincipal) {
    for (const [i, estado] of ['COMPLETADA', 'COMPLETADA', 'NO_ASISTIO'].entries()) crear(docs[i * 7 % docs.length], pacs[0], -(5 + i * 6), estado);
    for (let i = 0; i < 3; i++) crear(docs[(i * 11 + 3) % docs.length], pacs[0], 2 + i * 3, 'PENDIENTE');
  }
  if (nuevas.length) await citas.insertMany(nuevas);

  const resumen = {};
  for (const x of nuevas) resumen[x.estado] = (resumen[x.estado] || 0) + 1;
  console.log(`Especialidades: ${ESPECIALIDADES.length} | doctores activos: ${docs.length} | pendientes: 4 | pacientes nuevos: ${N_PACIENTES}`);
  console.log(`Horarios: ${horariosInsert.length} | citas: ${nuevas.length} ${JSON.stringify(resumen)}`);
  if (pacPrincipal) console.log('Citas del paciente de prueba:', nuevas.filter(x => x.pacienteId === String(pacPrincipal._id)).length);
  await c.close();
})().catch(e => { console.error(e); process.exit(1); });
