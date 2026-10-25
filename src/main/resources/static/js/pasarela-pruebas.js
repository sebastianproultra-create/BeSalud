// Pasarela de pruebas: imita el checkout de Wompi con sus datos de prueba. Los datos de la tarjeta se validan
// aquí y nunca se envían: al servidor solo llegan el medio, el resultado de la prueba, la marca y los últimos 4.
(function () {
    const raiz = document.querySelector('[data-pasarela]');
    if (!raiz) return;

    const TARJETAS = { '4242424242424242': 'APPROVED', '4111111111111111': 'DECLINED' };
    const CELULARES = { '3991111111': 'APPROVED', '3992222222': 'DECLINED' };
    const BANCOS = { '0': 'APPROVED', '1': 'DECLINED' };
    const TITULOS = { CARD: 'Tarjeta de crédito o débito', PSE: 'PSE', NEQUI: 'Nequi', BANCOLOMBIA_TRANSFER: 'Botón Bancolombia' };
    const ESPERA = {
        CARD: ['Procesando tu pago…', 'Estamos validando la tarjeta con tu banco.'],
        PSE: ['Conectando con tu banco…', 'Te llevamos al portal de PSE.'],
        NEQUI: ['Enviando la notificación…', 'Revisa tu celular y acepta el pago en la app Nequi.'],
        BANCOLOMBIA_TRANSFER: ['Conectando con Bancolombia…', 'Autoriza la transferencia en la Sucursal Virtual.']
    };

    const paso = nombre => raiz.querySelector('[data-paso="' + nombre + '"]');
    const campo = nombre => raiz.querySelector('[data-campo="' + nombre + '"]');
    const error = raiz.querySelector('[data-error]');
    let medio = null;

    // ── Navegación entre pasos ──
    raiz.querySelectorAll('[data-medio]').forEach(function (boton) {
        boton.addEventListener('click', function () { elegir(boton.dataset.medio); });
    });
    raiz.querySelector('[data-atras]').addEventListener('click', function () {
        paso('datos').hidden = true;
        paso('medios').hidden = false;
        mostrarError(null);
    });

    function elegir(m) {
        medio = m;
        paso('medios').hidden = true;
        paso('datos').hidden = false;
        raiz.querySelector('[data-titulo-medio]').textContent = TITULOS[m];
        raiz.querySelectorAll('[data-form]').forEach(function (f) { f.hidden = f.dataset.form !== m; });
        mostrarError(null);
        const primero = raiz.querySelector('[data-form="' + m + '"] input:not([readonly]), [data-form="' + m + '"] select');
        if (primero) primero.focus();
    }

    // ── Tarjeta: formato, marca y vista previa ──
    const soloDigitos = v => v.replace(/\D/g, '');

    function marcaDe(n) {
        if (/^4/.test(n)) return 'VISA';
        if (/^(5[1-5]|2(2[2-9]|[3-6]\d|7[01]|720))/.test(n)) return 'MASTERCARD';
        if (/^3[47]/.test(n)) return 'AMEX';
        if (/^3(0[0-5]|[68])/.test(n)) return 'DINERS';
        return null;
    }

    function luhn(n) {
        let suma = 0;
        for (let i = 0; i < n.length; i++) {
            let d = Number(n[n.length - 1 - i]);
            if (i % 2 === 1) { d *= 2; if (d > 9) d -= 9; }
            suma += d;
        }
        return n.length >= 13 && suma % 10 === 0;
    }

    const NOMBRE_MARCA = { VISA: 'VISA', MASTERCARD: 'Mastercard', AMEX: 'AMEX', DINERS: 'Diners' };

    campo('numero').addEventListener('input', function (e) {
        const n = soloDigitos(e.target.value).slice(0, 19);
        e.target.value = n.replace(/(\d{4})(?=\d)/g, '$1 ');
        const marca = marcaDe(n);
        raiz.querySelector('[data-vista-marca]').textContent = marca ? NOMBRE_MARCA[marca] : '';
        raiz.querySelector('.tarjeta-vista').dataset.marca = marca || '';
        const relleno = (n + '•'.repeat(Math.max(0, 16 - n.length))).replace(/(.{4})(?=.)/g, '$1 ');
        raiz.querySelector('[data-vista-numero]').textContent = relleno;
    });
    campo('vence').addEventListener('input', function (e) {
        const d = soloDigitos(e.target.value).slice(0, 4);
        e.target.value = d.length > 2 ? d.slice(0, 2) + '/' + d.slice(2) : d;
        raiz.querySelector('[data-vista-vence]').textContent = e.target.value || 'MM/AA';
    });
    campo('cvc').addEventListener('input', function (e) { e.target.value = soloDigitos(e.target.value).slice(0, 4); });
    campo('nombre').addEventListener('input', function (e) {
        raiz.querySelector('[data-vista-nombre]').textContent = e.target.value.toUpperCase() || 'NOMBRE EN LA TARJETA';
    });
    ['documento', 'documentoPse'].forEach(function (c) {
        campo(c).addEventListener('input', function (e) { e.target.value = soloDigitos(e.target.value).slice(0, 12); });
    });
    campo('celular').addEventListener('input', function (e) {
        const d = soloDigitos(e.target.value).slice(0, 10);
        e.target.value = d.replace(/^(\d{3})(\d{0,3})(\d{0,4}).*/, function (_, a, b, c) { return [a, b, c].filter(Boolean).join(' '); });
    });

    function venceValido(v) {
        const m = /^(\d{2})\/(\d{2})$/.exec(v);
        if (!m || Number(m[1]) < 1 || Number(m[1]) > 12) return false;
        const fin = new Date(2000 + Number(m[2]), Number(m[1]), 1);
        return fin > new Date();
    }

    // ── Datos de prueba: un clic los llena ──
    raiz.querySelectorAll('[data-llenar]').forEach(function (b) {
        b.addEventListener('click', function () {
            elegir('CARD');
            escribir('numero', b.dataset.llenar);
            const anio = String((new Date().getFullYear() + 3) % 100).padStart(2, '0');
            escribir('vence', '12' + anio);
            escribir('cvc', '123');
            escribir('nombre', 'Paciente Prueba');
            escribir('documento', '1234567890');
            campo('terminos').checked = true;
        });
    });
    raiz.querySelectorAll('[data-llenar-celular]').forEach(function (b) {
        b.addEventListener('click', function () {
            elegir('NEQUI');
            escribir('celular', b.dataset.llenarCelular);
            campo('terminos').checked = true;
        });
    });
    function escribir(nombre, valor) {
        const c = campo(nombre);
        c.value = valor;
        c.dispatchEvent(new Event('input'));
    }

    // ── Validación y envío ──
    function mostrarError(texto, campoConError) {
        error.hidden = !texto;
        error.textContent = texto || '';
        raiz.querySelectorAll('.is-invalido').forEach(function (c) { c.classList.remove('is-invalido'); });
        if (campoConError) {
            campoConError.classList.add('is-invalido');
            campoConError.focus();
        }
    }

    function validar() {
        if (medio === 'CARD') {
            const n = soloDigitos(campo('numero').value);
            if (!luhn(n) || !marcaDe(n)) return ['Revisa el número de la tarjeta.', campo('numero')];
            if (!venceValido(campo('vence').value)) return ['La fecha de vencimiento no es válida.', campo('vence')];
            const largoCvc = marcaDe(n) === 'AMEX' ? 4 : 3;
            if (campo('cvc').value.length !== largoCvc) return ['El CVC debe tener ' + largoCvc + ' dígitos.', campo('cvc')];
            if (campo('nombre').value.trim().length < 3) return ['Escribe el nombre como aparece en la tarjeta.', campo('nombre')];
            if (campo('documento').value.length < 5) return ['Escribe tu número de documento.', campo('documento')];
            if (!TARJETAS[n]) return ['Estás en modo pruebas: usa una de las tarjetas de prueba (no ingreses tarjetas reales).', campo('numero')];
            return null;
        }
        if (medio === 'PSE') {
            if (campo('banco').value === '') return ['Selecciona tu banco.', campo('banco')];
            if (campo('documentoPse').value.length < 5) return ['Escribe tu número de documento.', campo('documentoPse')];
            return null;
        }
        if (medio === 'NEQUI') {
            const cel = soloDigitos(campo('celular').value);
            if (!/^3\d{9}$/.test(cel)) return ['Escribe un celular de 10 dígitos que empiece por 3.', campo('celular')];
            if (!CELULARES[cel]) return ['Estás en modo pruebas: usa uno de los celulares de prueba.', campo('celular')];
            return null;
        }
        return null;
    }

    raiz.querySelector('[data-pagar]').addEventListener('click', function () {
        const fallo = validar();
        if (fallo) return mostrarError(fallo[0], fallo[1]);
        if (!campo('terminos').checked) return mostrarError('Debes aceptar los términos para continuar.', campo('terminos'));
        mostrarError(null);

        const envio = raiz.querySelector('[data-envio]');
        const poner = (n, v) => { envio.elements[n].value = v == null ? '' : v; };
        poner('medio', medio);
        if (medio === 'CARD') {
            const n = soloDigitos(campo('numero').value);
            poner('escenario', TARJETAS[n]);
            poner('marca', marcaDe(n));
            poner('ultimos4', n.slice(-4));
        } else if (medio === 'PSE') {
            poner('escenario', BANCOS[campo('banco').value]);
            poner('banco', campo('banco').value);
        } else if (medio === 'NEQUI') {
            const cel = soloDigitos(campo('celular').value);
            poner('escenario', CELULARES[cel]);
            poner('ultimos4', cel.slice(-4));
        } else {
            poner('escenario', 'APPROVED');
        }

        const espera = raiz.querySelector('[data-procesando]');
        raiz.querySelector('[data-procesando-titulo]').textContent = ESPERA[medio][0];
        raiz.querySelector('[data-procesando-texto]').textContent = ESPERA[medio][1];
        espera.hidden = false;
        // Pausa breve, como la respuesta del banco
        setTimeout(function () { envio.submit(); }, 1800);
    });
})();
