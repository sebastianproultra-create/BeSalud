// Pagos SIMULADOS para ver "Mis ganancias" y "Pagos a médicos" en una demo: las citas @sim.besalud.com no traen pagos.
// Toca solo citas de esos médicos, de los últimos 6 meses y sin pago; las marca con referencia DEMO-. No es dinero real.
//
//   mongosh "mongodb://localhost:27017/besalud_propuesta" scripts/demo-ganancias.js
//   LIMPIAR=1 mongosh "<uri>" scripts/demo-ganancias.js     (deshace lo que hizo este script)
//
// Se niega a correr sobre la base "besalud" salvo con CONFIRMAR=1: confírmalo con el equipo antes.
if (db.getName() === 'besalud' && process.env.CONFIRMAR !== '1') {
    print('La base es "besalud": no se toca nada. Usa CONFIRMAR=1 si de verdad es lo que quieres.');
    quit(1);
}

const QUITAR = { abonoCentavos: '', saldoCentavos: '', referenciaPago: '', referenciaSaldo: '', medioPago: '', medioPagoSaldo: '', estadoAbono: '', saldoObligatorio: '', saldoPagadoEn: '' };

if (process.env.LIMPIAR === '1') {
    const r = db.citas.updateMany({ referenciaPago: /^DEMO-/, liquidacionId: { $exists: false } }, { $unset: QUITAR });
    print('Citas limpiadas: ' + r.modifiedCount + ' (las ya incluidas en una liquidación no se tocan)');
    quit(0);
}

const PRECIO_POR_DEFECTO = 8000000; // centavos, igual que Doctor.PRECIO_CONSULTA_DEFECTO
const PORCENTAJE_ABONO = 25;
const desde = new Date();
desde.setMonth(desde.getMonth() - 6);
const hoy = new Date();

let total = 0;
db.personas.find({ role: 'DOCTOR', email: /@sim\.besalud\.com$/ }).forEach(d => {
    const precio = d.precioConsulta ? d.precioConsulta * 100 : PRECIO_POR_DEFECTO;
    const abono = Math.round(precio * PORCENTAJE_ABONO / 100);
    const ops = [];
    db.citas.find({ doctorId: String(d._id), fecha: { $gte: desde, $lte: hoy }, abonoCentavos: { $exists: false } },
        { estado: 1, fecha: 1 }).forEach(c => {
        const referencia = 'DEMO-' + String(c._id).slice(-12).toUpperCase();
        const pago = { abonoCentavos: Long(abono), referenciaPago: referencia, medioPago: 'Simulado (demo)', saldoObligatorio: true };
        if (c.estado === 'COMPLETADA' || c.estado === 'ASISTIO') {
            Object.assign(pago, { estadoAbono: 'PAGADO', saldoCentavos: Long(precio - abono), referenciaSaldo: referencia, medioPagoSaldo: 'Simulado (demo)', saldoPagadoEn: c.fecha });
        } else if (c.estado === 'NO_ASISTIO') {
            pago.estadoAbono = 'RETENIDO';
        } else if (c.estado === 'CANCELADA') {
            pago.estadoAbono = 'DEVUELTO';
        } else {
            return;
        }
        ops.push({ updateOne: { filter: { _id: c._id }, update: { $set: pago } } });
    });
    if (ops.length)
        total += db.citas.bulkWrite(ops).modifiedCount;
});
print('Citas con pago simulado: ' + total);
