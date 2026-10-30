// Historial clínico: descargar en PDF (con "Guardar como PDF" del navegador) todo el historial o una sola consulta.
(function () {
    'use strict';

    const raiz = document.documentElement;
    let estado = null;

    function prepararImpresion(consulta) {
        if (estado) return;
        // Al imprimir se ven todas las consultas abiertas y en tema claro (el papel es blanco); luego se restaura
        const tarjetas = Array.from(document.querySelectorAll('.hc-consulta__tarjeta'));
        estado = {
            tarjetas: tarjetas.map(function (d) { return [d, d.open]; }),
            consulta: consulta,
            tema: raiz.getAttribute('data-tema')
        };
        tarjetas.forEach(function (d) { d.open = true; });
        raiz.removeAttribute('data-tema');
        if (consulta) {
            document.body.classList.add('hc-imprime-una');
            consulta.classList.add('is-impresa');
        }
    }

    function restaurar() {
        if (!estado) return;
        estado.tarjetas.forEach(function (par) { par[0].open = par[1]; });
        if (estado.consulta) estado.consulta.classList.remove('is-impresa');
        if (estado.tema) raiz.setAttribute('data-tema', estado.tema);
        document.body.classList.remove('hc-imprime-una');
        estado = null;
    }

    document.addEventListener('click', function (e) {
        const boton = e.target.closest('[data-imprimir]');
        if (!boton) return;
        prepararImpresion(boton.closest('.hc-consulta'));
        window.print();
    });

    // También con Ctrl+P: se imprime el historial completo
    window.addEventListener('beforeprint', function () { prepararImpresion(null); });
    window.addEventListener('afterprint', restaurar);

    // Si llega desde "Mis citas" con #consulta-ID, se abre esa consulta
    const destino = location.hash && document.getElementById(location.hash.slice(1));
    if (destino && destino.classList.contains('hc-consulta')) {
        const tarjeta = destino.querySelector('.hc-consulta__tarjeta');
        if (tarjeta) tarjeta.open = true;
    }
})();
