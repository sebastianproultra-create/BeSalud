// Crea el médico y el consultorio "demo" que usa "Pruébalo en vivo" en /desarrolladores.
// Uso: mongosh "mongodb://localhost:27017/besalud" scripts/crear-consultorio-demo.js
// y arranca la app con API_CONSULTORIOS=true y API_CONSULTORIOS_DEMO=demo. Si APP_URL no es localhost:8080,
// agrega ese dominio a "dominios". Idempotente: se puede correr varias veces.
const email = "medico.demo@demo.besalud.com";
let d = db.personas.findOne({ email });
if (!d) {
  const id = new ObjectId();
  db.personas.insertOne({ _id: id, nombre: "Demostración", apellido: "BeSalud", email, role: "DOCTOR", estado: "ACTIVO",
    especialidad: "Medicina General", slug: "demostracion-besalud", telefono: "3000000000", identificacion: "DEMO-0001",
    biografia: "Médico de demostración del portal para desarrolladores.", password: "!sin-acceso",
    precioConsulta: 50000, porcentajeAbono: 20, _class: "com.gestion.proyectos.modelo.Doctor" });
  d = db.personas.findOne({ _id: id });
  const dias = ["MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY","SATURDAY"];
  db.horariosAtencion.insertMany(dias.map(dia => ({ doctorId: id.toString(), diaSemana: dia, horaInicio: "08:00",
    horaFin: dia === "SATURDAY" ? "12:00" : "17:00", duracionCitaMinutos: 30, _class: "com.gestion.proyectos.modelo.HorarioAtencion" })));
}
db.consultorios.updateOne({ _id: "demo" }, { $set: { nombre: "Consultorio de demostración",
  dominios: ["http://localhost:8080", "http://127.0.0.1:8080"], doctorIds: [d._id.toString()], activo: true,
  _class: "com.gestion.proyectos.modelo.Consultorio" } }, { upsert: true });
printjson({ doctor: d._id.toString(), horarios: db.horariosAtencion.countDocuments({ doctorId: d._id.toString() }), consultorio: db.consultorios.findOne({ _id: "demo" }) });
