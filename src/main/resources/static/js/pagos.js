// Páginas de pago: cuenta regresiva del horario apartado y actualización automática mientras se confirma el pago.
(function () {
    // Cuenta regresiva: al llegar a cero se recarga y el servidor muestra que el horario se liberó
    document.querySelectorAll('.pago-reloj[data-segundos]').forEach(function (reloj) {
        const tiempo = reloj.querySelector('.pago-reloj-tiempo');
        const barra = reloj.querySelector('.pago-reloj-barra span');
        const total = Number(reloj.dataset.total) || Number(reloj.dataset.segundos) || 1;
        const fin = Date.now() + Number(reloj.dataset.segundos) * 1000;
        function pintar() {
            const resta = Math.max(0, Math.round((fin - Date.now()) / 1000));
            tiempo.textContent = Math.floor(resta / 60) + ':' + String(resta % 60).padStart(2, '0');
            reloj.classList.toggle('is-urgente', resta <= 120);
            reloj.classList.toggle('is-agotado', resta === 0);
            if (barra) barra.style.width = Math.min(100, resta / total * 100) + '%';
            if (resta === 0) {
                clearInterval(intervalo);
                setTimeout(function () { location.reload(); }, 1500);
            }
        }
        const intervalo = setInterval(pintar, 1000);
        pintar();
    });

    // Barra fija del celular: se oculta mientras el botón principal de pago está a la vista
    const barra = document.querySelector('.pago-barra-movil');
    const principal = document.querySelector('.pago-cobro .pago-boton');
    if (barra && principal && 'IntersectionObserver' in window) {
        new IntersectionObserver(function (entradas) {
            barra.classList.toggle('is-oculta', entradas[0].isIntersecting);
        }).observe(principal);
    }

    // Mis pagos: filtros por estado; un mes se oculta si no le queda ningún pago visible
    const filtros = document.querySelector('[data-filtros]');
    if (filtros) {
        const items = Array.from(document.querySelectorAll('.historial-item'));
        const meses = Array.from(document.querySelectorAll('.historial-mes'));
        filtros.querySelectorAll('[data-filtro]').forEach(function (boton) {
            const f = boton.dataset.filtro;
            const n = f === 'todos' ? items.length : items.filter(function (i) { return i.dataset.grupo === f; }).length;
            boton.querySelector('[data-cuenta]').textContent = n;
            boton.disabled = n === 0;
            boton.addEventListener('click', function () {
                filtros.querySelectorAll('[data-filtro]').forEach(function (b) {
                    b.classList.toggle('is-activo', b === boton);
                    b.setAttribute('aria-pressed', b === boton ? 'true' : 'false');
                });
                items.forEach(function (i) { i.hidden = f !== 'todos' && i.dataset.grupo !== f; });
                meses.forEach(function (mes) {
                    let sig = mes.nextElementSibling, visible = false;
                    while (sig && !sig.classList.contains('historial-mes')) {
                        if (!sig.hidden) visible = true;
                        sig = sig.nextElementSibling;
                    }
                    mes.hidden = !visible;
                });
                document.querySelector('[data-sin-resultados]').hidden = items.some(function (i) { return !i.hidden; });
            });
        });
    }

    // Comprobante: el navegador permite imprimirlo o guardarlo como PDF
    document.querySelectorAll('[data-imprimir]').forEach(function (b) {
        b.addEventListener('click', function () { window.print(); });
    });

    // Pago en proceso (PSE, Nequi): se consulta el estado cada pocos segundos
    const espera = document.querySelector('[data-estado-url]');
    if (espera) {
        let intentos = 0;
        const revisar = async function () {
            intentos++;
            try {
                const res = await fetch(espera.dataset.estadoUrl, { headers: { 'Accept': 'application/json' } });
                if (res.ok && (await res.json()).vista !== 'PROCESANDO') {
                    location.reload();
                    return;
                }
            } catch (e) {
                // sin conexión: se intenta en la próxima vuelta
            }
            if (intentos < 60) setTimeout(revisar, intentos < 10 ? 4000 : 10000);
        };
        setTimeout(revisar, 4000);
    }
})();
