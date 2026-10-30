(function () {
    const raiz = document.querySelector('[data-chatbot-landing]');
    if (!raiz) return;

    const lanzador = document.querySelector('[data-chatbot-abrir]');
    const panel = raiz;
    const mensajes = raiz.querySelector('.cb-mensajes');
    const opciones = raiz.querySelector('.cb-opciones');
    const form = raiz.querySelector('.cb-form');
    const entrada = form.querySelector('input');
    let arbol = null;

    function normalizar(t) {
        return t.toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '').trim();
    }

    function burbuja(texto, quien) {
        const p = document.createElement('div');
        p.className = 'cb-burbuja cb-' + quien;
        p.textContent = texto;
        mensajes.appendChild(p);
        mensajes.scrollTop = mensajes.scrollHeight;
        return p;
    }

    function mostrar(id) {
        const nodo = arbol.get(id) || arbol.get('inicio');
        const b = burbuja(nodo.respuesta, 'bot');
        if (nodo.accion) {
            const a = document.createElement('a');
            a.className = 'cb-accion';
            a.href = nodo.accion.url;
            a.textContent = nodo.accion.texto + ' →';
            b.appendChild(a);
        }
        opciones.innerHTML = '';
        const ids = (nodo.opciones || []).slice();
        if (nodo.id !== 'inicio' && !ids.includes('inicio')) ids.push('inicio');
        ids.map(i => arbol.get(i)).filter(Boolean).forEach(h => {
            const btn = document.createElement('button');
            btn.type = 'button';
            btn.className = 'cb-opcion';
            btn.textContent = h.id === 'inicio' ? 'Menú principal' : h.pregunta;
            btn.addEventListener('click', () => {
                burbuja(btn.textContent, 'usuario');
                mostrar(h.id);
            });
            opciones.appendChild(btn);
        });
    }

    function buscar(texto) {
        const t = normalizar(texto);
        let mejor = null, puntos = 0;
        // Gana la coincidencia más específica: palabras más largas pesan más y el título del nodo también cuenta
        arbol.forEach(nodo => {
            const titulo = normalizar(nodo.pregunta || '').split(/[^a-zñ]+/).filter(w => w.length > 3);
            const p = [...(nodo.palabrasClave || []).map(normalizar), ...titulo]
                .filter(k => t.includes(k)).reduce((s, k) => s + k.length, 0);
            if (p > puntos) { mejor = nodo; puntos = p; }
        });
        return mejor;
    }

    async function cargar() {
        if (arbol) return;
        const res = await fetch(raiz.dataset.fuente);
        const datos = await res.json();
        arbol = new Map(datos.nodos.map(n => [n.id, n]));
        mostrar('inicio');
    }

    function abrir(si) {
        panel.hidden = !si;
        lanzador.setAttribute('aria-expanded', si);
        if (si) {
            cargar().catch(() => burbuja('No pude cargar el asistente. Intente de nuevo más tarde.', 'bot'));
            entrada.focus();
        }
    }

    lanzador.addEventListener('click', () => abrir(panel.hidden));
    raiz.querySelector('[data-chatbot-cerrar]').addEventListener('click', () => abrir(false));

    form.addEventListener('submit', e => {
        e.preventDefault();
        const texto = entrada.value.trim();
        if (!texto || !arbol) return;
        entrada.value = '';
        burbuja(texto, 'usuario');
        const nodo = buscar(texto);
        if (nodo) return mostrar(nodo.id);
        burbuja('No le entendí. Elija una de las opciones o escriba otra palabra, por ejemplo "cita" o "pagos".', 'bot');
    });
})();
