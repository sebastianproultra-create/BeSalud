/* Demanda proyectada del panel del médico: pide el pronóstico a /api/predicciones/mia (modelo Prophet),
   lo compara con los cupos del horario del médico y lo dibuja con Chart.js. */
(function () {
    const seccion = document.getElementById('demanda');
    if (!seccion || typeof Chart === 'undefined') return;

    const $ = id => document.getElementById(id);
    const css = getComputedStyle(seccion);
    const color = (nombre, respaldo) => css.getPropertyValue(nombre).trim() || respaldo;
    const VERDE = color('--d-primary-c', '#0D4738');
    const MENTA = color('--d-pf', '#B7EED9');
    const AMBAR = color('--d-amber', '#855403');
    const AMBAR_C = color('--d-amber-c', '#FFBD6A');
    const TINTA = color('--d-on', '#131E1A');
    const TINTA_SUAVE = color('--d-on-var', '#404945');
    const REJILLA = 'rgba(0, 47, 36, 0.08)';

    const DIAS_JAVA = { SUNDAY: 0, MONDAY: 1, TUESDAY: 2, WEDNESDAY: 3, THURSDAY: 4, FRIDAY: 5, SATURDAY: 6 };
    const fmtCorto = new Intl.DateTimeFormat('es-CO', { weekday: 'short', day: 'numeric' });
    const fmtLargo = new Intl.DateTimeFormat('es-CO', { weekday: 'long', day: 'numeric', month: 'long' });
    const citas = n => n === 1 ? 'cita' : 'citas';

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

    let datos = [];
    let grafica = null;

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
        const excede = tramo.map((d, i) => tieneHorario && d.valor > cupos[i]);

        // Indicadores
        const manana = tramo.find(d => d.fecha > hoy) || tramo[0];
        $('dkManana').textContent = manana.valor;
        $('dkMananaRango').textContent = `entre ${manana.min} y ${manana.max} · ${fmtLargo.format(manana.fecha)}`;

        const pico = tramo.reduce((a, b) => (b.valor > a.valor ? b : a));
        $('dkPico').textContent = pico.valor;
        $('dkPicoFecha').textContent = fmtLargo.format(pico.fecha);

        const total = tramo.reduce((s, d) => s + d.valor, 0);
        const diasConsulta = tramo.filter((d, i) => tieneHorario ? cupos[i] > 0 : d.fecha.getDay() % 6 !== 0).length;
        $('dkTotalTitulo').textContent = `Próximos ${tramo.length} días`;
        $('dkTotal').textContent = total;
        $('dkTotalPromedio').textContent = diasConsulta
            ? `≈ ${Math.round(total / diasConsulta)} por día de consulta` : 'Sin días de consulta';

        const nExcede = excede.filter(Boolean).length;
        const caja = $('dkAlertaCaja');
        caja.classList.toggle('demanda-kpi--alerta', nExcede > 0);
        if (!tieneHorario) {
            $('dkAlerta').textContent = '—';
            $('dkAlertaUnidad').textContent = '';
            $('dkAlertaFoot').textContent = 'Configura tu horario para comparar';
        } else {
            $('dkAlerta').textContent = nExcede;
            $('dkAlertaUnidad').textContent = nExcede === 1 ? 'día' : 'días';
            $('dkAlertaFoot').textContent = nExcede
                ? 'La demanda supera tus cupos: considera abrir más franjas'
                : 'Tus cupos cubren la demanda';
        }

        $('demandaResumen').textContent =
            `Se esperan unas ${total} ${citas(total)} en los próximos ${tramo.length} días; el día más cargado sería el ` +
            `${fmtLargo.format(pico.fecha)} con cerca de ${pico.valor}. El margen muestra el rango en el que caería ` +
            'la demanda 8 de cada 10 veces.';
        $('demandaChart').setAttribute('aria-label',
            `Citas proyectadas por día durante los próximos ${tramo.length} días. Total ${total}, máximo ${pico.valor}.`);

        // Gráfica
        const etiquetas = tramo.map(d => fmtCorto.format(d.fecha).replace('.', ''));
        const conjuntos = [
            {
                label: 'Margen', data: tramo.map(d => [d.min, d.max]), order: 3,
                backgroundColor: MENTA, borderRadius: 4, borderSkipped: false,
                grouped: false, barPercentage: 0.9, categoryPercentage: 0.9,
            },
            {
                label: 'Proyectadas', data: tramo.map(d => d.valor), order: 2,
                backgroundColor: excede.map(e => (e ? AMBAR_C : VERDE)), borderRadius: 3, borderSkipped: 'start',
                grouped: false, barPercentage: 0.45, categoryPercentage: 0.9,
            },
        ];
        if (tieneHorario) {
            conjuntos.push({
                type: 'line', label: 'Cupos', data: cupos, order: 1,
                borderColor: AMBAR, borderWidth: 1.5, borderDash: [5, 4], stepped: 'middle',
                pointRadius: 0, pointHoverRadius: 0, fill: false,
            });
        }

        if (grafica) grafica.destroy();
        grafica = new Chart($('demandaChart'), {
            type: 'bar',
            data: { labels: etiquetas, datasets: conjuntos },
            options: {
                maintainAspectRatio: false,
                animation: { duration: 300 },
                interaction: { mode: 'index', intersect: false },
                scales: {
                    y: { beginAtZero: true, ticks: { precision: 0, color: TINTA_SUAVE }, grid: { color: REJILLA }, border: { display: false } },
                    x: { grid: { display: false }, ticks: { color: TINTA_SUAVE, maxRotation: 0, autoSkip: true, maxTicksLimit: dias > 14 ? 10 : dias } },
                },
                plugins: {
                    legend: { display: false },
                    tooltip: {
                        backgroundColor: '#FFFFFF', titleColor: TINTA, bodyColor: TINTA,
                        borderColor: 'rgba(0, 47, 36, 0.2)', borderWidth: 1, padding: 10, displayColors: false,
                        callbacks: {
                            title: items => fmtLargo.format(tramo[items[0].dataIndex].fecha),
                            label: item => {
                                const d = tramo[item.dataIndex];
                                if (item.dataset.label === 'Margen') return `Margen: ${d.min} – ${d.max}`;
                                if (item.dataset.label === 'Cupos') return `Cupos de tu horario: ${item.raw}`;
                                return `Proyectadas: ${d.valor} ${citas(d.valor)}` + (excede[item.dataIndex] ? ' (supera tus cupos)' : '');
                            },
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
            mostrar('listo');
            // Las etiquetas de los ejes se miden con la fuente: esperar a que cargue para no cortarlas.
            document.fonts.ready.then(() => pintar(Number(document.querySelector('.demanda-rango.is-active').dataset.dias)));
        })
        .catch(function (e) {
            mostrar('vacio', e.message === 'sin-historial'
                ? 'Aún no hay suficiente historial de citas para proyectar tu demanda.'
                : 'El modelo predictivo no está disponible en este momento. Intenta de nuevo más tarde.');
        });
})();
