// Asistente de agenda: página pública del profesional (/p/{slug}) y botón flotante del portal del paciente.
(function () {
    const caja = document.querySelector('[data-asistente]');
    if (!caja) return;

    const endpoint = caja.dataset.endpoint;
    const doctorPagina = caja.dataset.doctorId; // vacío en el portal: cada horario trae su profesional
    const modo = caja.dataset.modo; // paciente | visitante | otro
    const mensajes = caja.querySelector('.chat-mensajes');
    const form = caja.querySelector('.chat-form');
    const input = form.querySelector('input');
    // Token CSRF: meta de la página o, si no hay, el campo oculto de cualquier formulario (p. ej. cerrar sesión)
    const token = document.querySelector('meta[name="_csrf"]')?.content
        || document.querySelector('input[name="_csrf"]')?.value;
    const cabecera = document.querySelector('meta[name="_csrf_header"]')?.content || 'X-CSRF-TOKEN';
    let ocupado = false;

    // Botón flotante: abre y cierra el panel
    const lanzador = document.querySelector('[data-asistente-abrir]');
    if (lanzador) {
        const alternar = function (abrir) {
            caja.hidden = !abrir;
            lanzador.setAttribute('aria-expanded', String(abrir));
            if (abrir) input.focus();
        };
        lanzador.addEventListener('click', function () { alternar(caja.hidden); });
        caja.querySelector('[data-asistente-cerrar]')?.addEventListener('click', function () {
            alternar(false);
            lanzador.focus();
        });
        document.addEventListener('keydown', function (e) {
            if (e.key === 'Escape' && !caja.hidden) alternar(false);
        });
    }

    function enlaceReserva(h) {
        const doctorId = h.doctorId || doctorPagina;
        const destino = '/citas/nueva?doctorId=' + encodeURIComponent(doctorId)
            + '&fecha=' + encodeURIComponent(h.fecha) + '&hora=' + encodeURIComponent(h.hora);
        if (modo === 'paciente') return destino;
        if (modo === 'visitante') return '/login?continuar=' + encodeURIComponent(destino);
        return null;
    }

    function agregar(texto, clase) {
        const div = document.createElement('div');
        div.className = 'chat-msg ' + clase;
        div.textContent = texto;
        mensajes.appendChild(div);
        mensajes.scrollTop = mensajes.scrollHeight;
        return div;
    }

    function agregarRespuesta(r) {
        const div = agregar(r.texto, r.emergencia ? 'is-bot is-emergencia' : 'is-bot');
        if (r.horarios && r.horarios.length) {
            const lista = document.createElement('div');
            lista.className = 'chat-horarios';
            r.horarios.forEach(function (h) {
                const url = enlaceReserva(h);
                const el = document.createElement(url ? 'a' : 'span');
                el.className = 'chat-slot' + (url ? '' : ' is-inactivo');
                el.textContent = h.etiqueta;
                if (url) el.href = url;
                lista.appendChild(el);
            });
            div.appendChild(lista);
            mensajes.scrollTop = mensajes.scrollHeight;
        }
    }

    async function preguntar(texto) {
        texto = texto.trim();
        if (!texto || ocupado) return;
        ocupado = true;
        agregar(texto, 'is-usuario');
        const espera = agregar('Buscando…', 'is-bot is-espera');
        try {
            const headers = { 'Content-Type': 'application/json' };
            if (token) headers[cabecera] = token;
            const res = await fetch(endpoint, {
                method: 'POST',
                headers: headers,
                body: JSON.stringify({ mensaje: texto })
            });
            espera.remove();
            if (res.status === 429) {
                agregar('Hiciste muchas preguntas seguidas. Espera unos minutos e intenta de nuevo.', 'is-bot');
            } else if (!res.ok || !(res.headers.get('Content-Type') || '').includes('json')) {
                agregar('No pude responder ahora. Recarga la página e intenta de nuevo.', 'is-bot');
            } else {
                agregarRespuesta(await res.json());
            }
        } catch (e) {
            espera.remove();
            agregar('No hay conexión. Revisa tu internet e intenta de nuevo.', 'is-bot');
        } finally {
            ocupado = false;
        }
    }

    form.addEventListener('submit', function (e) {
        e.preventDefault();
        const texto = input.value;
        input.value = '';
        preguntar(texto);
    });

    caja.querySelectorAll('[data-pregunta]').forEach(function (b) {
        b.addEventListener('click', function () { preguntar(b.dataset.pregunta); });
    });
})();
