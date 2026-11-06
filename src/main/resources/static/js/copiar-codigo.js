// Botones "Copiar" de bloques de código: <button data-copiar="#idDelBloque">…<span>Copiar</span></button>
(function () {
    document.addEventListener('click', function (e) {
        const boton = e.target.closest('[data-copiar]');
        if (!boton) return;
        const origen = document.querySelector(boton.dataset.copiar);
        if (!origen) return;
        const texto = origen.value !== undefined ? origen.value : origen.textContent;
        const etiqueta = boton.querySelector('span:last-child') || boton;
        const original = etiqueta.textContent;
        const listo = function () {
            etiqueta.textContent = 'Copiado';
            setTimeout(function () { etiqueta.textContent = original; }, 2000);
        };
        if (navigator.clipboard) navigator.clipboard.writeText(texto).then(listo, function () { seleccionar(origen); });
        else seleccionar(origen);
    });

    // Sin portapapeles (http sin localhost, navegadores viejos): se deja el texto seleccionado para Ctrl+C.
    function seleccionar(nodo) {
        const rango = document.createRange();
        rango.selectNodeContents(nodo);
        const sel = window.getSelection();
        sel.removeAllRanges();
        sel.addRange(rango);
    }
})();
