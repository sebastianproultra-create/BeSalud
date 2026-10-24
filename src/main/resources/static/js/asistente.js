// Asistente de agenda de la página pública del profesional (/p/{slug}).
(function () {
    const caja = document.querySelector('.pro-asistente');
    if (!caja) return;

    const endpoint = caja.dataset.endpoint;
    const doctorId = caja.dataset.doctorId;
    const modo = caja.dataset.modo; // paciente | visitante | otro
    const mensajes = document.getElementById('chat-mensajes');
    const form = document.getElementById('chat-form');
    const input = document.getElementById('chat-texto');
    const token = document.querySelector('meta[name="_csrf"]')?.content;
    const cabecera = document.querySelector('meta[name="_csrf_header"]')?.content;
    let ocupado = false;

    function enlaceReserva(h) {
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
                el.className = 'pro-slot' + (url ? '' : ' is-inactivo');
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
            if (token && cabecera) headers[cabecera] = token;
            const res = await fetch(endpoint, {
                method: 'POST',
                headers: headers,
                body: JSON.stringify({ mensaje: texto })
            });
            espera.remove();
            if (res.status === 429) {
                agregar('Hiciste muchas preguntas seguidas. Espera unos minutos e intenta de nuevo.', 'is-bot');
            } else if (!res.ok) {
                agregar('No pude responder ahora. Intenta de nuevo en un momento.', 'is-bot');
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
