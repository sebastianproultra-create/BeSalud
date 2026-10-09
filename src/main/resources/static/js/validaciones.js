/**
 * BeSalud – Validación en vivo de formularios (alta de doctores y pacientes desde el panel admin).
 *
 * Uso: añadir data-validar="nombre|identificacion|telefono|email|password" al <input>.
 * Las reglas son las mismas que aplica el servidor (ValidacionUtil.java): el servidor sigue siendo
 * la validación definitiva, esto solo avisa al instante.
 *
 * El mensaje se muestra bajo el campo y también se registra con setCustomValidity(), de modo que
 * el navegador bloquea el envío mientras haya errores.
 */
(function () {
    'use strict';

    // ── Reglas (espejo de ValidacionUtil.java) ───────────────────────────────

    var NOMBRE = /^\p{L}+(?:[ '\-]\p{L}+)*$/u;
    var EMAIL = /^[A-Za-z0-9]+(?:[._%+\-][A-Za-z0-9]+)*@(?:[A-Za-z0-9](?:[A-Za-z0-9\-]{0,61}[A-Za-z0-9])?\.)+[A-Za-z]{2,}$/;
    var GMAIL_USUARIO = /^[a-z0-9]+(?:\.[a-z0-9]+)*$/;
    var DOMINIOS_ERRADOS = {
        'gmial.com': 'gmail.com', 'gmai.com': 'gmail.com', 'gamil.com': 'gmail.com', 'gmaill.com': 'gmail.com',
        'gmail.con': 'gmail.com', 'gmail.cm': 'gmail.com', 'hotmial.com': 'hotmail.com', 'hotmal.com': 'hotmail.com',
        'hotmail.con': 'hotmail.com', 'outlok.com': 'outlook.com', 'outlook.con': 'outlook.com',
        'yahooo.com': 'yahoo.com', 'yahoo.con': 'yahoo.com'
    };

    function limpiar(v) { return v.trim().replace(/\s+/g, ' '); }
    function bytesUtf8(s) { return new TextEncoder().encode(s).length; }

    /** Cada validador devuelve el mensaje de error o '' si el valor es válido. */
    var VALIDADORES = {
        nombre: function (v, campo) {
            var n = (campo && campo.dataset.etiqueta) || 'nombre';
            v = limpiar(v);
            if (!v) return '';
            if (v.length < 2 || v.length > 50 || !NOMBRE.test(v))
                return 'El ' + n + ' debe tener entre 2 y 50 letras (sin números ni símbolos)';
            return '';
        },
        identificacion: function (v) {
            if (!v) return '';
            if (!/^\d+$/.test(v)) return 'Solo números, sin puntos ni espacios';
            if (v.length < 6) return 'Debe tener al menos 6 dígitos (' + v.length + '/6)';
            if (v.length > 10) return 'No puede tener más de 10 dígitos';
            return '';
        },
        telefono: function (v) {
            if (!v) return '';
            if (v.charAt(0) !== '3') return 'Debe empezar por 3 (ej: 3001234567)';
            if (v.length < 10) return 'Faltan ' + (10 - v.length) + ' dígito(s): debe tener 10';
            return '';
        },
        email: function (v) {
            v = v.trim();
            if (!v) return '';
            if (v.length > 100) return 'El correo no puede superar los 100 caracteres';
            var arroba = v.indexOf('@');
            if (arroba > 64 || !EMAIL.test(v)) return 'El correo electrónico no tiene un formato válido';

            var local = v.substring(0, arroba).toLowerCase();
            var dominio = v.substring(arroba + 1).toLowerCase();
            if (!/[a-z]/i.test(local)) return 'El correo no puede estar formado solo por números';
            var etiquetas = dominio.split('.');
            if (/^\d+$/.test(etiquetas[etiquetas.length - 2])) return 'El dominio del correo no es válido';
            if (DOMINIOS_ERRADOS[dominio])
                return 'El dominio parece mal escrito. ¿Quisiste decir ' + DOMINIOS_ERRADOS[dominio] + '?';
            if (dominio === 'gmail.com' || dominio === 'googlemail.com') {
                var mas = local.indexOf('+');
                var usuario = mas >= 0 ? local.substring(0, mas) : local;
                var largo = usuario.replace(/\./g, '').length;
                if (!GMAIL_USUARIO.test(usuario) || largo < 6 || largo > 30)
                    return 'Un correo de Gmail debe tener entre 6 y 30 letras o números antes del @ (se permiten puntos)';
            }
            return '';
        },
        password: function (v) {
            if (!v) return '';
            if (v.length < 8) return 'Debe tener al menos 8 caracteres (' + v.length + '/8)';
            if (bytesUtf8(v) > 72) return 'No puede superar los 72 caracteres';
            if (!/\p{L}/u.test(v) || !/\d/.test(v)) return 'Debe incluir al menos una letra y un número';
            return '';
        }
    };

    // ── Comportamiento por campo ─────────────────────────────────────────────

    function pista(campo) {
        var h = campo.parentNode.querySelector('.field-hint[data-para="' + campo.id + '"]');
        if (!h) {
            h = document.createElement('span');
            h.className = 'field-hint';
            h.setAttribute('data-para', campo.id);
            h.setAttribute('aria-live', 'polite');
            campo.insertAdjacentElement('afterend', h);
        }
        return h;
    }

    function validar(campo, mostrar) {
        var tipo = campo.dataset.validar;
        var valor = campo.value;
        var msg = VALIDADORES[tipo](valor, campo);
        var hint = pista(campo);

        campo.setCustomValidity(msg);
        campo.setAttribute('aria-invalid', msg ? 'true' : 'false');

        if (!mostrar) return msg;
        campo.classList.toggle('input-error', !!msg);
        campo.classList.toggle('input-success', !msg && valor.trim() !== '');
        hint.className = 'field-hint' + (msg ? ' error' : '');
        hint.textContent = msg;
        return msg;
    }

    function iniciar(campo) {
        var tipo = campo.dataset.validar;
        if (!VALIDADORES[tipo] || !campo.id) return;

        var soloDigitos = (tipo === 'telefono' || tipo === 'identificacion');

        campo.addEventListener('input', function () {
            if (soloDigitos) {
                var limpio = campo.value.replace(/\D+/g, '');
                if (limpio !== campo.value) campo.value = limpio;
            }
            validar(campo, campo.dataset.tocado === '1' || campo.value.length > 0);
        });
        campo.addEventListener('blur', function () {
            campo.dataset.tocado = '1';
            validar(campo, true);
        });
        // Al enviar con errores, el navegador enfoca el primero y muestra el mensaje
        campo.addEventListener('invalid', function () {
            campo.dataset.tocado = '1';
            validar(campo, true);
        });

        // Valores que vuelven del servidor tras un error: se revalidan al cargar
        validar(campo, campo.value.trim() !== '');
    }

    document.addEventListener('DOMContentLoaded', function () {
        document.querySelectorAll('input[data-validar]').forEach(iniciar);

        // Si el servidor devolvió un error, deja el formulario a la vista
        var alerta = document.querySelector('[data-scroll-error]');
        if (alerta) alerta.scrollIntoView({ block: 'center' });
    });
})();