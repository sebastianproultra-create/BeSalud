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
    // Campos que se validan para cada medio, en el orden en que aparecen
    const CAMPOS = {
        CARD: ['numero', 'vence', 'cvc', 'nombre', 'documento'],
        PSE: ['banco', 'documentoPse'],
        NEQUI: ['celular'],
        BANCOLOMBIA_TRANSFER: []
    };

    const paso = nombre => raiz.querySelector('[data-paso="' + nombre + '"]');
    const campo = nombre => raiz.querySelector('[data-campo="' + nombre + '"]');
    const soloDigitos = v => v.replace(/\D/g, '');
    const errorGeneral = raiz.querySelector('[data-error]');
    const botonPagar = raiz.querySelector('[data-pagar]');
    let medio = null;
    let enviando = false;

    // ── Navegación entre pasos ──
    raiz.querySelectorAll('[data-medio]').forEach(function (boton) {
        boton.addEventListener('click', function () { elegir(boton.dataset.medio); });
    });
    raiz.querySelector('[data-atras]').addEventListener('click', function () {
        paso('datos').hidden = true;
        paso('medios').hidden = false;
        limpiarErrores();
        raiz.querySelector('[data-medio="' + medio + '"]').focus();
    });

    function elegir(m) {
        medio = m;
        paso('medios').hidden = true;
        paso('datos').hidden = false;
        raiz.querySelector('[data-titulo-medio]').textContent = TITULOS[m];
        raiz.querySelectorAll('[data-form]').forEach(function (f) { f.hidden = f.dataset.form !== m; });
        limpiarErrores();
        // En el celular no se enfoca solo: abriría el teclado y taparía el formulario
        if (!window.matchMedia('(pointer: fine)').matches) return raiz.scrollIntoView({ block: 'start' });
        const primero = raiz.querySelector('[data-form="' + m + '"] input:not([readonly]), [data-form="' + m + '"] select');
        if (primero) primero.focus();
        else botonPagar.focus();
    }

    // ── Tarjeta ──
    function marcaDe(n) {
        if (/^4/.test(n)) return 'VISA';
        if (/^(5[1-5]|2(2[2-9]|[3-6]\d|7[01]|720))/.test(n)) return 'MASTERCARD';
        if (/^3[47]/.test(n)) return 'AMEX';
        if (/^3(0[0-5]|[68])/.test(n)) return 'DINERS';
        return null;
    }
    const LARGO = { AMEX: 15, DINERS: 14 };
    const largoDe = marca => LARGO[marca] || 16;

    function agrupar(n, marca) {
        if (marca === 'AMEX') return [n.slice(0, 4), n.slice(4, 10), n.slice(10, 15)].filter(Boolean).join(' ');
        if (marca === 'DINERS') return [n.slice(0, 4), n.slice(4, 10), n.slice(10, 14)].filter(Boolean).join(' ');
        return n.replace(/(\d{4})(?=\d)/g, '$1 ');
    }

    function luhn(n) {
        let suma = 0;
        for (let i = 0; i < n.length; i++) {
            let d = Number(n[n.length - 1 - i]);
            if (i % 2 === 1) { d *= 2; if (d > 9) d -= 9; }
            suma += d;
        }
        return suma % 10 === 0;
    }

    const NOMBRE_MARCA = { VISA: 'VISA', MASTERCARD: 'Mastercard', AMEX: 'AMEX', DINERS: 'Diners' };

    campo('numero').addEventListener('input', function (e) {
        const marca = marcaDe(soloDigitos(e.target.value));
        const n = soloDigitos(e.target.value).slice(0, largoDe(marca));
        e.target.value = agrupar(n, marca);
        raiz.querySelector('[data-vista-marca]').textContent = marca ? NOMBRE_MARCA[marca] : '';
        raiz.querySelector('.tarjeta-vista').dataset.marca = marca || '';
        const chip = raiz.querySelector('[data-marca-campo]');
        chip.hidden = !marca;
        chip.textContent = marca ? NOMBRE_MARCA[marca] : '';
        chip.className = 'marca-chip' + (marca ? ' is-' + marca.toLowerCase() : '');
        raiz.querySelector('[data-vista-numero]').textContent = agrupar(n + '•'.repeat(Math.max(0, largoDe(marca) - n.length)), marca);
        campo('cvc').placeholder = marca === 'AMEX' ? '1234' : '123';
        if (n.length === largoDe(marca) && !validar('numero')) campo('vence').focus();
    });
    campo('vence').addEventListener('input', function (e) {
        let d = soloDigitos(e.target.value).slice(0, 4);
        if (d.length === 1 && d > '1') d = '0' + d; // "5" -> "05"
        e.target.value = d.length > 2 || (d.length === 2 && e.inputType !== 'deleteContentBackward') ? d.slice(0, 2) + '/' + d.slice(2) : d;
        raiz.querySelector('[data-vista-vence]').textContent = e.target.value || 'MM/AA';
        if (d.length === 4 && !validar('vence')) campo('cvc').focus();
    });
    campo('cvc').addEventListener('input', function (e) { e.target.value = soloDigitos(e.target.value).slice(0, 4); });
    campo('nombre').addEventListener('input', function (e) {
        e.target.value = e.target.value.replace(/[^A-Za-zÁÉÍÓÚÜÑáéíóúüñ' ]/g, '').replace(/\s{2,}/g, ' ');
        raiz.querySelector('[data-vista-nombre]').textContent = e.target.value.trim().toUpperCase() || 'NOMBRE EN LA TARJETA';
    });
    ['documento', 'documentoPse'].forEach(function (c) {
        campo(c).addEventListener('input', function (e) { e.target.value = soloDigitos(e.target.value).slice(0, 10); });
    });
    campo('celular').addEventListener('input', function (e) {
        const d = soloDigitos(e.target.value).slice(0, 10);
        e.target.value = [d.slice(0, 3), d.slice(3, 6), d.slice(6)].filter(Boolean).join(' ');
    });

    // ── Reglas de cada campo: devuelven el mensaje de error o null ──
    const REGLAS = {
        numero: function () {
            const n = soloDigitos(campo('numero').value);
            const marca = marcaDe(n);
            if (!n) return 'Escribe el número de la tarjeta.';
            if (!marca) return 'Aceptamos Visa, Mastercard, American Express y Diners.';
            if (n.length !== largoDe(marca) || !luhn(n)) return 'El número de la tarjeta no es válido.';
            if (!TARJETAS[n]) return 'Modo pruebas: usa una tarjeta de prueba, no una real.';
            return null;
        },
        vence: function () {
            const m = /^(\d{2})\/(\d{2})$/.exec(campo('vence').value);
            if (!m) return 'Escribe mes y año (MM/AA).';
            const mes = Number(m[1]);
            if (mes < 1 || mes > 12) return 'El mes debe estar entre 01 y 12.';
            if (new Date(2000 + Number(m[2]), mes, 1) <= new Date()) return 'La tarjeta está vencida.';
            return null;
        },
        cvc: function () {
            const largo = marcaDe(soloDigitos(campo('numero').value)) === 'AMEX' ? 4 : 3;
            return campo('cvc').value.length === largo ? null : 'Debe tener ' + largo + ' dígitos.';
        },
        nombre: function () {
            return campo('nombre').value.trim().split(' ').filter(Boolean).length >= 2 ? null : 'Escribe nombre y apellido.';
        },
        documento: function () {
            return campo('documento').value.length >= 6 ? null : 'Escribe un documento de 6 a 10 dígitos.';
        },
        banco: function () {
            return campo('banco').value !== '' ? null : 'Selecciona tu banco.';
        },
        documentoPse: function () {
            return campo('documentoPse').value.length >= 6 ? null : 'Escribe un documento de 6 a 10 dígitos.';
        },
        celular: function () {
            const cel = soloDigitos(campo('celular').value);
            if (!/^3\d{9}$/.test(cel)) return 'Escribe un celular de 10 dígitos que empiece por 3.';
            if (!CELULARES[cel]) return 'Modo pruebas: usa un celular de prueba.';
            return null;
        }
    };

    // Muestra (o quita) el error debajo del campo; devuelve el mensaje
    function validar(nombre) {
        const c = campo(nombre);
        const mensaje = REGLAS[nombre]();
        const contenedor = c.closest('.pasarela-campo');
        let aviso = contenedor.querySelector('.pasarela-campo-error');
        if (!aviso) {
            aviso = document.createElement('small');
            aviso.className = 'pasarela-campo-error';
            aviso.id = 'error-' + nombre;
            contenedor.appendChild(aviso);
        }
        aviso.textContent = mensaje || '';
        aviso.hidden = !mensaje;
        c.classList.toggle('is-invalido', !!mensaje);
        c.setAttribute('aria-invalid', mensaje ? 'true' : 'false');
        if (mensaje) c.setAttribute('aria-describedby', aviso.id);
        else c.removeAttribute('aria-describedby');
        return mensaje;
    }

    Object.keys(REGLAS).forEach(function (nombre) {
        const c = campo(nombre);
        // Al salir del campo se revisa si ya escribió algo; mientras corrige, el error se actualiza
        c.addEventListener('blur', function () { if (c.value !== '') validar(nombre); });
        c.addEventListener(c.tagName === 'SELECT' ? 'change' : 'input', function () {
            if (c.classList.contains('is-invalido')) validar(nombre);
        });
    });

    function limpiarErrores() {
        raiz.querySelectorAll('.pasarela-campo-error').forEach(function (a) { a.hidden = true; });
        raiz.querySelectorAll('.is-invalido').forEach(function (c) {
            c.classList.remove('is-invalido');
            c.setAttribute('aria-invalid', 'false');
        });
        mostrarErrorGeneral(null);
    }

    function mostrarErrorGeneral(texto) {
        errorGeneral.hidden = !texto;
        errorGeneral.textContent = texto || '';
    }

    campo('terminos').addEventListener('change', function () { if (this.checked) mostrarErrorGeneral(null); });

    // ── Datos de prueba: un clic los llena ──
    raiz.querySelectorAll('[data-llenar]').forEach(function (b) {
        b.addEventListener('click', function () {
            elegir('CARD');
            escribir('numero', b.dataset.llenar);
            escribir('vence', '12' + String((new Date().getFullYear() + 3) % 100).padStart(2, '0'));
            escribir('cvc', '123');
            escribir('nombre', 'Paciente Prueba');
            escribir('documento', '1234567890');
            campo('terminos').checked = true;
            limpiarErrores();
            botonPagar.focus();
        });
    });
    raiz.querySelectorAll('[data-llenar-celular]').forEach(function (b) {
        b.addEventListener('click', function () {
            elegir('NEQUI');
            escribir('celular', b.dataset.llenarCelular);
            campo('terminos').checked = true;
            limpiarErrores();
            botonPagar.focus();
        });
    });
    function escribir(nombre, valor) {
        const c = campo(nombre);
        c.value = valor;
        c.dispatchEvent(new Event('input'));
    }

    // ── Envío ──
    // Enter en cualquier campo equivale a pagar
    paso('datos').addEventListener('keydown', function (e) {
        if (e.key === 'Enter' && e.target.matches('input:not([type="checkbox"])')) {
            e.preventDefault();
            pagar();
        }
    });
    botonPagar.addEventListener('click', pagar);

    function pagar() {
        if (enviando) return;
        if (raiz.querySelector('.pago-reloj.is-agotado')) {
            return mostrarErrorGeneral('Se acabó el tiempo para pagar. Vuelve al resumen para apartar el horario de nuevo.');
        }
        const conError = CAMPOS[medio].filter(function (n) { return validar(n); });
        if (conError.length) {
            mostrarErrorGeneral(null);
            return campo(conError[0]).focus();
        }
        if (!campo('terminos').checked) {
            mostrarErrorGeneral('Para continuar, acepta los términos y condiciones.');
            return campo('terminos').focus();
        }
        mostrarErrorGeneral(null);
        enviando = true;
        botonPagar.disabled = true;

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

        if (medio === 'BANCOLOMBIA_TRANSFER') return procesar();
        // Antes de procesar, el paso del banco o de la app; el aviso de "procesando" cubre la espera
        mostrarProcesando(ESPERA[medio][0], ESPERA[medio][1]);
        setTimeout(function () { verificar(medio); }, 1200);
    }

    function mostrarProcesando(titulo, texto) {
        raiz.querySelector('[data-procesando-titulo]').textContent = titulo;
        raiz.querySelector('[data-procesando-texto]').textContent = texto;
        raiz.querySelector('[data-procesando]').hidden = false;
    }

    function procesar(escenario) {
        const envio = raiz.querySelector('[data-envio]');
        if (escenario) envio.elements.escenario.value = escenario;
        mostrarProcesando(medio === 'CARD' ? 'Procesando tu pago…' : 'Confirmando el pago…', 'No cierres esta ventana.');
        // Pausa breve, como la respuesta del banco
        setTimeout(function () { envio.submit(); }, 1500);
    }

    // ── Verificación: 3D Secure, portal del banco (PSE) o notificación de Nequi ──
    const CODIGO_PRUEBA = '123456';
    const capa = raiz.querySelector('[data-verificacion]');
    let caja = null, cuenta = null, intentos = 0;

    function verificar(m) {
        raiz.querySelector('[data-procesando]').hidden = true;
        caja = capa.querySelector('[data-ver="' + m + '"]');
        capa.querySelectorAll('[data-ver]').forEach(function (c) { c.hidden = c !== caja; });
        if (m === 'CARD') {
            const n = soloDigitos(campo('numero').value);
            const chip = caja.querySelector('[data-ver-marca]');
            chip.textContent = NOMBRE_MARCA[marcaDe(n)];
            chip.className = 'marca-chip is-' + marcaDe(n).toLowerCase();
            caja.querySelector('[data-ver-tarjeta]').textContent = '•••• ' + n.slice(-4);
            caja.querySelector('[data-ver-codigo]').value = '';
            caja.querySelector('[data-ver-error]').hidden = true;
            intentos = 0;
        } else if (m === 'PSE') {
            const banco = campo('banco');
            caja.querySelector('[data-ver-banco]').textContent = banco.options[banco.selectedIndex].text;
        } else if (m === 'NEQUI') {
            const ahora = new Date();
            caja.querySelector('[data-ver-hora]').textContent = ahora.getHours() + ':' + String(ahora.getMinutes()).padStart(2, '0');
            let quedan = 60;
            caja.querySelector('[data-ver-cuenta]').textContent = quedan;
            cuenta = setInterval(function () {
                quedan--;
                caja.querySelector('[data-ver-cuenta]').textContent = quedan;
                if (quedan <= 0) cerrarVerificacion('La solicitud en Nequi venció. Intenta de nuevo.');
            }, 1000);
        }
        capa.hidden = false;
        document.body.classList.add('sin-scroll');
        (caja.querySelector('[data-ver-codigo]') || caja.querySelector('[data-ver-confirmar]')).focus();
    }

    function cerrarVerificacion(error) {
        clearInterval(cuenta);
        capa.hidden = true;
        document.body.classList.remove('sin-scroll');
        enviando = false;
        botonPagar.disabled = false;
        if (error) mostrarErrorGeneral(error);
        botonPagar.focus();
    }

    function finalizarVerificacion(escenario) {
        clearInterval(cuenta);
        capa.hidden = true;
        document.body.classList.remove('sin-scroll');
        procesar(escenario);
    }

    const CANCELADO = 'Cancelaste el pago. Puedes intentarlo de nuevo.';
    capa.querySelectorAll('[data-ver-cancelar]').forEach(function (b) {
        b.addEventListener('click', function () { cerrarVerificacion(CANCELADO); });
    });
    capa.querySelector('[data-ver-rechazar]').addEventListener('click', function () { finalizarVerificacion('DECLINED'); });
    capa.querySelectorAll('[data-ver-confirmar]').forEach(function (b) {
        b.addEventListener('click', function () {
            if (caja.dataset.ver !== 'CARD') return finalizarVerificacion();
            const entrada = caja.querySelector('[data-ver-codigo]');
            const error = caja.querySelector('[data-ver-error]');
            if (soloDigitos(entrada.value) === CODIGO_PRUEBA) return finalizarVerificacion();
            intentos++;
            // Tres intentos fallidos: el banco no autoriza el pago
            if (intentos >= 3) return finalizarVerificacion('DECLINED');
            const quedan = 3 - intentos;
            error.textContent = 'El código no es correcto. Te ' + (quedan === 1 ? 'queda 1 intento.' : 'quedan ' + quedan + ' intentos.');
            error.hidden = false;
            entrada.classList.add('is-invalido');
            entrada.select();
        });
    });
    const codigo = capa.querySelector('[data-ver-codigo]');
    codigo.addEventListener('input', function () {
        codigo.value = soloDigitos(codigo.value).slice(0, 6);
        codigo.classList.remove('is-invalido');
        caja.querySelector('[data-ver-error]').hidden = true;
    });
    codigo.addEventListener('keydown', function (e) {
        if (e.key === 'Enter') caja.querySelector('[data-ver-confirmar]').click();
    });
    document.addEventListener('keydown', function (e) {
        if (e.key === 'Escape' && !capa.hidden) cerrarVerificacion(CANCELADO);
    });
})();
