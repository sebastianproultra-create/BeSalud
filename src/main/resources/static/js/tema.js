// Modo oscuro: se carga en el <head> para aplicar el tema antes de pintar la página (sin parpadeo).
// La preferencia se guarda en este navegador; sin preferencia, la página sale en claro.
(function () {
    const CLAVE = 'besalud-tema';
    const raiz = document.documentElement;

    function guardada() {
        try { return localStorage.getItem(CLAVE); } catch (e) { return null; }
    }

    function aplicar(tema) {
        if (tema === 'oscuro') raiz.setAttribute('data-tema', 'oscuro');
        else raiz.removeAttribute('data-tema');
    }

    aplicar(guardada());

    const SOL = '<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="4.5"/><path d="M12 2v2.5M12 19.5V22M4.2 4.2l1.8 1.8M18 18l1.8 1.8M2 12h2.5M19.5 12H22M4.2 19.8 6 18M18 6l1.8-1.8"/></svg>';
    const LUNA = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M20.5 14.2A8.5 8.5 0 0 1 9.8 3.5a8.5 8.5 0 1 0 10.7 10.7Z"/></svg>';

    function pintarBoton(boton) {
        const oscuro = raiz.getAttribute('data-tema') === 'oscuro';
        boton.innerHTML = oscuro ? SOL : LUNA;
        boton.setAttribute('aria-pressed', oscuro ? 'true' : 'false');
        boton.setAttribute('aria-label', oscuro ? 'Cambiar a modo claro' : 'Cambiar a modo oscuro');
        boton.title = oscuro ? 'Modo claro' : 'Modo oscuro';
    }

    document.addEventListener('DOMContentLoaded', function () {
        const boton = document.createElement('button');
        boton.type = 'button';
        boton.className = 'tema-boton';
        pintarBoton(boton);
        boton.addEventListener('click', function () {
            const nuevo = raiz.getAttribute('data-tema') === 'oscuro' ? 'claro' : 'oscuro';
            // Transición suave solo durante el cambio, para no animar la carga de la página
            raiz.classList.add('tema-cambiando');
            aplicar(nuevo);
            pintarBoton(boton);
            try { localStorage.setItem(CLAVE, nuevo); } catch (e) { /* sin almacenamiento: dura hasta recargar */ }
            // Las gráficas (Chart.js) toman sus colores al dibujarse: se recarga para redibujarlas
            if (document.querySelector('canvas')) return location.reload();
            setTimeout(function () { raiz.classList.remove('tema-cambiando'); }, 350);
        });
        document.body.appendChild(boton);
    });

    // Si cambia en otra pestaña, esta se pone igual
    window.addEventListener('storage', function (e) {
        if (e.key !== CLAVE) return;
        aplicar(e.newValue);
        const boton = document.querySelector('.tema-boton');
        if (boton) pintarBoton(boton);
    });
})();
