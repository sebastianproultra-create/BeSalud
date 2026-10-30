/* Demanda proyectada del panel del médico: pide el pronóstico a /api/predicciones/mia (modelo scikit-learn),
   lo compara con los cupos del horario del médico y lo dibuja con Chart.js. */
(function () {
    const seccion = document.getElementById('demanda');
    if (!seccion || typeof Chart === 'undefined') return;

    const $ = id => document.getElementById(id);
    const css = getComputedStyle(seccion);
    // En modo oscuro cada variable tiene su versión para texto (--t) y para relleno (--f): ver css/oscuro/
    const oscuro = document.documentElement.getAttribute('data-tema') === 'oscuro';
    const color = (nombre, respaldo, papel) =>
        (oscuro && css.getPropertyValue(nombre + '--' + (papel || 't')).trim()) || css.getPropertyValue(nombre).trim() || respaldo;
    const VERDE = color('--d-primary-c', '#0D4738');
    const AMBAR = color('--d-amber', '#855403');
    const AMBAR_C = color('--d-amber-c', '#FFBD6A', 'f');
    const CUPO = color('--d-highest', '#D9E5DF', 'b');
    const TINTA = color('--d-on', '#131E1A');
    const TINTA_SUAVE = color('--d-on-var', '#404945');
    const FONDO = oscuro ? '#1B2A24' : '#FFFFFF';
    const REJILLA = oscuro ? 'rgba(255, 255, 255, 0.08)' : 'rgba(0, 47, 36, 0.08)';

    const DIAS_JAVA = { SUNDAY: 0, MONDAY: 1, TUESDAY: 2, WEDNESDAY: 3, THURSDAY: 4, FRIDAY: 5, SATURDAY: 6 };
    const fmtCorto = new Intl.DateTimeFormat('es-CO', { weekday: 'short', day: 'numeric' });
    const fmtLargo = new Intl.DateTimeFormat('es-CO', { weekday: 'long', day: 'numeric', month: 'long' });
    const fmtDia = new Intl.DateTimeFormat('es-CO', { weekday: 'long' });
    const fmtDiaMes = new Intl.DateTimeFormat('es-CO', { day: 'numeric', month: 'short' });
    const citas = n => n === 1 ? 'cita' : 'citas';
    const diasPalabra = n => n === 1 ? 'día' : 'días';
    const capital = t => t.charAt(0).toUpperCase() + t.slice(1);
    const plural = dia => (dia.endsWith('s') ? dia : dia + 's');   // jueves -> jueves, sábado -> sábados
    const lista = xs => (xs.length > 1 ? `${xs.slice(0, -1).join(', ')} y ${xs[xs.length - 1]}` : xs[0]);

    // Cupos por día de la semana según las franjas del horario (igual que los slots de la agenda).
    const cuposPorDia = [0, 0, 0, 0, 0, 0, 0];
    const minutos = hhmm => { const [h, m] = hhmm.split(':').map(Number); return h * 60 + m; };
    document.querySelectorAll('#demandaHorarios span').forEach(function (s) {
        const dur = Number(s.dataset.duracion) || 30;
        const dia = DIAS_JAVA[s.dataset.dia];
        if (dia === undefined) return;
        cuposPorDia[dia] += Math.max(0, Math.floor((minutos(s.dataset.fin) - minutos(s.dataset.inicio)) / dur));
    });
    const tieneHorario = cuposPorDia.some(n => n > 0);

    // "2026-10-19" -> fecha local (sin desfase por zona horaria).
    const aFecha = iso => { const [a, m, d] = iso.split('-').map(Number); return new Date(a, m - 1, d); };
    const hoy = new Date(); hoy.setHours(0, 0, 0, 0);
    const manana = new Date(hoy); manana.setDate(hoy.getDate() + 1);

    let datos = [];
    let grafica = null;

    // Rango probable como bigote (línea vertical con topes) sobre cada barra. Se traza dos veces, ancho y con el
    // color del fondo y luego fino con el de la tinta, para que se lea igual sobre la barra verde que sobre el fondo.
    const bigotes = {
        id: 'bigotes',
        afterDatasetsDraw(chart, args, opciones) {
            const ctx = chart.ctx;
            const y = chart.scales.y;
            ctx.save();
            ctx.lineCap = 'round';
            chart.getDatasetMeta(0).data.forEach(function (barra, i) {
                const d = opciones.tramo[i];
                if (!d || d.valor === 0) return;   // sin citas esperadas no hay rango que mostrar
                const arriba = y.getPixelForValue(d.max);
                const abajo = y.getPixelForValue(d.min);
                const tope = Math.max(3, barra.width * 0.3);
                [[FONDO, 4], [TINTA, 1.5]].forEach(function ([trazo, ancho]) {
                    ctx.strokeStyle = trazo;
                    ctx.lineWidth = ancho;
                    ctx.beginPath();
                    ctx.moveTo(barra.x, abajo); ctx.lineTo(barra.x, arriba);
                    ctx.moveTo(barra.x - tope, arriba); ctx.lineTo(barra.x + tope, arriba);
                    ctx.moveTo(barra.x - tope, abajo); ctx.lineTo(barra.x + tope, abajo);
                    ctx.stroke();
                });
            });
            ctx.restore();
        },
    };

    function mostrar(estado, texto) {
        $('demandaCargando').hidden = estado !== 'cargando';
        $('demandaContenido').hidden = estado !== 'listo';
        $('demandaVacio').hidden = estado !== 'vacio';
        seccion.querySelector('.doc-segmentos').hidden = estado === 'vacio';
        if (texto) $('demandaVacioTexto').textContent = texto;
    }

    function pintar(dias) {
        const tramo = datos.slice(0, dias);
        if (!tramo.length) { mostrar('vacio', 'El pronóstico disponible ya no cubre los próximos días.'); return; }

        const cupos = tramo.map(d => cuposPorDia[d.fecha.getDay()]);
        const atiende = tramo.map((d, i) => (tieneHorario ? cupos[i] > 0 : d.fecha.getDay() % 6 !== 0));
        const excede = tramo.map((d, i) => tieneHorario && d.valor > cupos[i]);
        const nExcede = excede.filter(Boolean).length;
        const total = tramo.reduce((s, d) => s + d.valor, 0);

        // Próximo día de consulta: el primero en que atiende y se espera al menos una cita.
        const proximo = tramo.find((d, i) => atiende[i] && d.valor > 0) || tramo[0];
        $('dkProximoTitulo').textContent = proximo.fecha.getTime() === manana.getTime() ? 'Mañana' : 'Próximo día de consulta';
        $('dkProximo').textContent = proximo.valor;
        $('dkProximoUnidad').textContent = citas(proximo.valor);
        $('dkProximoFecha').textContent = capital(fmtLargo.format(proximo.fecha));
        $('dkProximoRango').textContent = `Lo más probable: entre ${proximo.min} y ${proximo.max} ${citas(proximo.max)}`;

        // Día(s) más cargado(s): si varios empatan, se dicen todos en vez de destacar solo el primero.
        const maximo = Math.max(...tramo.map(d => d.valor));
        const cargados = tramo.filter(d => d.valor === maximo);
        const mismoDia = cargados.every(d => d.fecha.getDay() === cargados[0].fecha.getDay());
        const fechasCortas = cargados.map(d => fmtDiaMes.format(d.fecha).replace('.', ''));
        $('dkPicoTitulo').textContent = cargados.length > 1 ? 'Días más cargados' : 'Día más cargado';
        $('dkPico').textContent = maximo;
        $('dkPicoUnidad').textContent = citas(maximo);
        if (cargados.length === 1) {
            $('dkPicoFecha').textContent = capital(fmtLargo.format(cargados[0].fecha));
            $('dkPicoDetalle').textContent = 'El día con más citas esperadas';
        } else {
            $('dkPicoFecha').textContent = mismoDia
                ? `Los ${plural(fmtDia.format(cargados[0].fecha))}` : `${cargados.length} días empatados`;
            $('dkPicoDetalle').textContent = cargados.length > 4
                ? `${fechasCortas.slice(0, 3).join(', ')} y ${cargados.length - 3} más` : lista(fechasCortas);
        }

        const diasConsulta = atiende.filter(Boolean).length;
        $('dkTotalTitulo').textContent = `Próximos ${tramo.length} días`;
        $('dkTotal').textContent = total;
        $('dkTotalPromedio').textContent = diasConsulta
            ? `En promedio, ${Math.round(total / diasConsulta)} por día de consulta` : 'Sin días de consulta';

        // Ocupación: qué parte de los cupos del periodo llenarían las citas esperadas.
        const cuposTotales = cupos.reduce((s, c) => s + c, 0);
        const ocupacion = cuposTotales
            ? Math.round(100 * tramo.reduce((s, d, i) => s + (cupos[i] > 0 ? d.valor : 0), 0) / cuposTotales) : null;
        $('dkOcupacionCaja').classList.toggle('demanda-kpi--alerta', nExcede > 0);
        if (ocupacion === null) {
            $('dkOcupacion').textContent = '—';
            $('dkOcupacionUnidad').textContent = '';
            $('dkOcupacionFoot').textContent = 'Configura tu horario para comparar';
        } else {
            $('dkOcupacion').textContent = ocupacion;
            $('dkOcupacionUnidad').textContent = '%';
            $('dkOcupacionFoot').textContent = nExcede
                ? `${nExcede} ${diasPalabra(nExcede)} en que la demanda supera tus cupos: considera abrir más franjas`
                : 'Ningún día la demanda supera tus cupos';
        }

        const masLlenos = cargados.length === 1
            ? `El día más lleno sería el ${fmtLargo.format(cargados[0].fecha)}, con unas ${maximo} ${citas(maximo)}.`
            : mismoDia
                ? `Los días más llenos serían los ${plural(fmtDia.format(cargados[0].fecha))}, con unas ${maximo} ${citas(maximo)}.`
                : `Los días más llenos tendrían unas ${maximo} ${citas(maximo)}.`;
        $('demandaResumen').textContent =
            `En los próximos ${tramo.length} días se esperan unas ${total} ${citas(total)}` +
            (ocupacion === null ? '. ' : `, que llenarían cerca del ${ocupacion} % de tus cupos. `) + masLlenos +
            (ocupacion === null ? ' Configura tu horario para compararlas con tus cupos.'
                : nExcede ? ` En ${nExcede} ${diasPalabra(nExcede)} la demanda supera tus cupos y algunos pacientes no alcanzarían cita; considera abrir más franjas.` : '');
        // El ámbar solo se explica en la leyenda cuando aparece en la gráfica.
        $('demandaLeyendaExcede').hidden = nExcede === 0;
        $('demandaChart').setAttribute('aria-label',
            `Citas esperadas por día durante los próximos ${tramo.length} días frente a tus cupos. ` +
            `Total ${total}, máximo ${maximo} por día` + (ocupacion === null ? '.' : `, ocupación ${ocupacion} %.`));

        // Gráfica: columna gris = cupos del día; barra verde adentro = citas esperadas; bigote = rango probable.
        const etiquetas = tramo.map(d => fmtCorto.format(d.fecha).replace('.', ''));
        const conjuntos = [{
            label: 'Citas esperadas', data: tramo.map(d => d.valor), order: 1,
            backgroundColor: excede.map(e => (e ? AMBAR_C : VERDE)), borderRadius: 3, borderSkipped: 'start',
            grouped: false, barPercentage: 0.5, categoryPercentage: 0.9,
        }];
        if (tieneHorario) {
            conjuntos.push({
                label: 'Tus cupos', data: cupos, order: 2,
                backgroundColor: CUPO, borderRadius: 4, borderSkipped: 'start',
                grouped: false, barPercentage: 0.8, categoryPercentage: 0.9,
            });
        }

        if (grafica) grafica.destroy();
        grafica = new Chart($('demandaChart'), {
            type: 'bar',
            data: { labels: etiquetas, datasets: conjuntos },
            plugins: [bigotes],
            options: {
                // Al menos el doble de resolución: con zoom o escalado de Windows fraccionario el canvas se ve borroso.
                devicePixelRatio: Math.max(2, window.devicePixelRatio || 1),
                maintainAspectRatio: false,
                animation: { duration: 300 },
                interaction: { mode: 'index', intersect: false },
                scales: {
                    y: {
                        beginAtZero: true, suggestedMax: Math.max(...tramo.map(d => d.max), ...cupos),
                        ticks: { precision: 0, color: TINTA_SUAVE }, grid: { color: REJILLA }, border: { display: false },
                    },
                    x: { grid: { display: false }, ticks: { color: TINTA_SUAVE, maxRotation: 0, autoSkip: true, maxTicksLimit: dias > 14 ? 10 : dias } },
                },
                plugins: {
                    legend: { display: false },
                    bigotes: { tramo },
                    tooltip: {
                        backgroundColor: FONDO, titleColor: TINTA, bodyColor: TINTA,
                        footerColor: AMBAR, footerFont: { weight: '700' },
                        borderColor: oscuro ? 'rgba(255, 255, 255, 0.14)' : 'rgba(0, 47, 36, 0.2)', borderWidth: 1, padding: 10, displayColors: false,
                        filter: item => item.datasetIndex === 0,
                        callbacks: {
                            title: items => capital(fmtLargo.format(tramo[items[0].dataIndex].fecha)),
                            label: item => {
                                const i = item.dataIndex;
                                const d = tramo[i];
                                const lineas = [`Se esperan ${d.valor} ${citas(d.valor)}`];
                                if (d.valor > 0) lineas.push(`Lo más probable: entre ${d.min} y ${d.max}`);
                                if (tieneHorario) lineas.push(cupos[i] ? `Tus cupos: ${cupos[i]}` : 'No atiendes este día');
                                return lineas;
                            },
                            footer: items => (excede[items[0].dataIndex] ? 'La demanda supera tus cupos' : ''),
                        },
                    },
                },
            },
        });
    }

    document.querySelectorAll('.demanda-rango').forEach(function (b) {
        b.addEventListener('click', function () {
            document.querySelectorAll('.demanda-rango').forEach(x => x.classList.toggle('is-active', x === b));
            if (datos.length) pintar(Number(b.dataset.dias));
        });
    });

    mostrar('cargando');
    Chart.defaults.font.family = "'Plus Jakarta Sans', 'Segoe UI', sans-serif";
    Chart.defaults.font.size = 12;

    fetch(seccion.dataset.url, { credentials: 'same-origin', headers: { Accept: 'application/json' } })
        .then(function (res) {
            if (res.status === 404) throw new Error('sin-historial');
            if (!res.ok) throw new Error('servicio');
            return res.json();
        })
        .then(function (json) {
            datos = (json.predicciones || [])
                .map(p => ({ fecha: aFecha(p.fecha), valor: p.volumenProyectado, min: p.margenMinimo, max: p.margenMaximo }))
                .filter(d => d.fecha >= hoy);
            if (!datos.length) { mostrar('vacio', 'Todavía no hay un pronóstico disponible para los próximos días.'); return; }
            $('demandaRespaldo').hidden = !json.respaldo;
            mostrar('listo');
            // Las etiquetas de los ejes se miden con la fuente: esperar a que cargue para no cortarlas.
            document.fonts.ready.then(() => pintar(Number(document.querySelector('.demanda-rango.is-active').dataset.dias)));
        })
        .catch(function (e) {
            mostrar('vacio', e.message === 'sin-historial'
                ? 'Tu especialidad todavía no está en el modelo predictivo, así que no hay una proyección de demanda para ti.'
                : 'El modelo predictivo no está disponible en este momento. Intenta de nuevo más tarde.');
        });
})();
