/**
 * BeSalud – Dictamen médico SOAP (panel del médico).
 *  - Formulario: filas dinámicas (diagnósticos relacionados y fórmula médica),
 *    IMC automático, resaltado de alergias y validaciones que el HTML no cubre.
 *  - Modal "Ver dictamen": pinta el dictamen guardado (SOAP o formato anterior).
 * Datos del servidor en window.BESALUD_DICTAMEN = { datos, previo, citaId, error }.
 * Las mismas reglas se validan otra vez en el servidor (DictamenValidador).
 */
(function () {
    'use strict';

    var SIN_ALERGIAS = /^\s*(no|ninguna?s?|niega|negativo|no refiere|no conocidas?|n\/?a|-)\s*\.?\s*$/i;
    var CFG = window.BESALUD_DICTAMEN || {};
    var form = document.getElementById('dictamenForm');
    if (!form) return;

    function $(id) { return document.getElementById(id); }
    function vacio(v) { return v === null || v === undefined || String(v).trim() === ''; }
    function numero(v) { return vacio(v) ? '' : String(v).replace(/\.0$/, ''); }

    /* ── Filas dinámicas ── */
    function renumerar(cont) {
        var prefijo = cont.dataset.prefijo;
        var filas = cont.querySelectorAll('.fila-dinamica');
        filas.forEach(function (fila, i) {
            fila.querySelectorAll('[data-campo]').forEach(function (el) {
                el.name = prefijo + '[' + i + '].' + el.dataset.campo;
            });
        });
        var btn = cont.nextElementSibling;
        if (btn) btn.hidden = filas.length >= Number(cont.dataset.max);
    }

    function agregarFila(contId, tplId, datos) {
        var cont = $(contId);
        if (cont.querySelectorAll('.fila-dinamica').length >= Number(cont.dataset.max)) return;
        var fila = $(tplId).content.firstElementChild.cloneNode(true);
        if (datos) {
            fila.querySelectorAll('[data-campo]').forEach(function (el) {
                var v = datos[el.dataset.campo];
                el.value = vacio(v) ? '' : numero(v);
            });
        }
        fila.querySelector('.btn-quitar-fila').addEventListener('click', function () {
            fila.remove();
            renumerar(cont);
        });
        cont.appendChild(fila);
        renumerar(cont);
        return fila;
    }

    function limpiarFilas() {
        ['filasRelacionados', 'filasMedicamentos'].forEach(function (id) {
            $(id).replaceChildren();
            renumerar($(id));
        });
    }

    $('btnAgregarRelacionado').addEventListener('click', function () {
        var f = agregarFila('filasRelacionados', 'tplRelacionado');
        if (f) f.querySelector('input').focus();
    });
    $('btnAgregarMedicamento').addEventListener('click', function () {
        var f = agregarFila('filasMedicamentos', 'tplMedicamento');
        if (f) f.querySelector('input').focus();
    });

    /* ── IMC y alergias ── */
    function clasificarImc(imc) {
        if (imc < 18.5) return 'bajo peso';
        if (imc < 25) return 'normal';
        if (imc < 30) return 'sobrepeso';
        return 'obesidad';
    }

    function actualizarImc() {
        var peso = parseFloat($('svPeso').value);
        var talla = parseFloat($('svTalla').value);
        var salida = $('svImc');
        if (peso > 0 && talla > 0) {
            var imc = Math.round(peso / Math.pow(talla / 100, 2) * 10) / 10;
            salida.textContent = imc + ' (' + clasificarImc(imc) + ')';
        } else {
            salida.textContent = '—';
        }
    }

    function actualizarAlergias() {
        var valor = $('antAlergicos').value;
        var tiene = !vacio(valor) && !SIN_ALERGIAS.test(valor);
        $('grupoAlergicos').classList.toggle('campo-alerta', tiene);
        $('grupoAlergicos').querySelector('.alerta-alergia-texto').hidden = !tiene;
    }

    $('svPeso').addEventListener('input', actualizarImc);
    $('svTalla').addEventListener('input', actualizarImc);
    $('antAlergicos').addEventListener('input', actualizarAlergias);

    /* ── Validaciones que dependen de varios campos ── */
    form.addEventListener('input', function (e) {
        if (e.target.classList.contains('cie10'))
            e.target.value = e.target.value.toUpperCase();
        if (e.target.validity && e.target.validity.customError)
            e.target.setCustomValidity('');
    });

    function validarCruzado() {
        var sis = $('svSistolica'), dia = $('svDiastolica');
        sis.setCustomValidity('');
        dia.setCustomValidity('');
        if (vacio(sis.value) !== vacio(dia.value)) {
            (vacio(sis.value) ? sis : dia).setCustomValidity('Registre la tensión arterial completa (sistólica y diastólica).');
        } else if (!vacio(sis.value) && Number(sis.value) <= Number(dia.value)) {
            sis.setCustomValidity('La tensión sistólica debe ser mayor que la diastólica.');
        }

        var dias = $('incapacidadDias'), just = $('incapacidadJustificacion');
        dias.setCustomValidity('');
        just.setCustomValidity('');
        if (!vacio(dias.value) && vacio(just.value))
            just.setCustomValidity('Si otorga incapacidad debe escribir la justificación.');
        else if (vacio(dias.value) && !vacio(just.value))
            dias.setCustomValidity('Indique los días de incapacidad o borre la justificación.');

        var vistos = {};
        form.querySelectorAll('input.cie10').forEach(function (el) {
            var codigo = el.value.replace(/[.\s]/g, '').toUpperCase();
            if (el.validity.customError) el.setCustomValidity('');
            if (!codigo) return;
            if (vistos[codigo]) el.setCustomValidity('El código ' + codigo + ' está repetido en los diagnósticos.');
            vistos[codigo] = true;
        });

        ['motivoConsulta', 'enfermedadActual', 'examenFisico', 'dxDescripcion'].forEach(function (id) {
            var el = $(id);
            if (el.value.length > 0 && el.value.trim() === '') el.setCustomValidity('Este campo no puede quedar solo con espacios.');
        });
    }

    // El clic ocurre antes de la validación nativa: así los mensajes personalizados la bloquean
    $('btnGuardarDictamen').addEventListener('click', validarCruzado);

    /* ── Abrir / llenar / cerrar ── */
    function asignar(nombre, valor) {
        var el = form.elements[nombre];
        if (el) el.value = vacio(valor) ? '' : numero(valor);
    }

    function llenar(d) {
        ['tipoConsulta', 'causaExterna', 'motivoConsulta', 'enfermedadActual', 'examenFisico',
         'ordenesMedicas', 'recomendaciones', 'incapacidadDias', 'incapacidadJustificacion']
            .forEach(function (k) { asignar(k, d[k]); });
        Object.keys(d.antecedentes || {}).forEach(function (k) { asignar('antecedentes.' + k, d.antecedentes[k]); });
        Object.keys(d.signosVitales || {}).forEach(function (k) { asignar('signosVitales.' + k, d.signosVitales[k]); });
        var dx = d.diagnosticoPrincipal || {};
        ['codigo', 'descripcion', 'tipo'].forEach(function (k) { asignar('diagnosticoPrincipal.' + k, dx[k]); });
        (d.diagnosticosRelacionados || []).forEach(function (r) { agregarFila('filasRelacionados', 'tplRelacionado', r); });
        (d.medicamentos || []).forEach(function (m) { agregarFila('filasMedicamentos', 'tplMedicamento', m); });
        actualizarImc();
        actualizarAlergias();
    }

    window.abrirDictamen = function (btn) {
        form.action = '/doctores/citas/' + btn.dataset.citaId + '/dictamen';
        form.reset();
        limpiarFilas();
        $('motivoConsulta').value = btn.dataset.motivo || '';
        $('dxFechaAtencion').textContent = btn.dataset.inicio || 'se registra al guardar';
        $('dictamenError').hidden = true;
        actualizarImc();
        actualizarAlergias();
        $('dictamenModal').style.display = 'flex';
        $('tipoConsulta').focus();
    };

    window.cerrarModal = function () {
        $('dictamenModal').style.display = 'none';
    };

    // Si el servidor rechazó el dictamen, se reabre con lo que el médico había escrito
    if (CFG.previo && CFG.citaId) {
        var boton = document.querySelector('button[data-cita-id="' + CSS.escape(CFG.citaId) + '"][data-accion="dictamen"]');
        if (boton) {
            window.abrirDictamen(boton);
            llenar(CFG.previo);
            if (CFG.error) {
                $('dictamenError').textContent = CFG.error;
                $('dictamenError').hidden = false;
                var alertaPagina = $('alertaDictamen');
                if (alertaPagina) alertaPagina.hidden = true;
            }
        }
    }

    /* ── Modal "Ver dictamen" ── */
    function el(tag, clase, texto) {
        var e = document.createElement(tag);
        if (clase) e.className = clase;
        if (!vacio(texto)) e.textContent = texto;
        return e;
    }

    function campo(etiqueta, valor) {
        if (vacio(valor)) return null;
        var p = el('p');
        p.appendChild(el('strong', null, etiqueta));
        p.appendChild(el('span', 'dictamen-valor', valor));
        return p;
    }

    function seccion(cont, titulo, hijos) {
        hijos = hijos.filter(Boolean);
        if (!hijos.length) return;
        var s = el('section');
        s.appendChild(el('h4', null, titulo));
        hijos.forEach(function (h) { s.appendChild(h); });
        cont.appendChild(s);
    }

    function textoDx(dx) {
        return dx.codigo + ' — ' + dx.descripcion + (dx.tipoTexto ? ' (' + dx.tipoTexto + ')' : '');
    }

    function tablaMedicamentos(meds) {
        if (!meds || !meds.length) return null;
        var envoltura = el('div', 'dictamen-tabla-wrapper');
        var t = el('table', 'dictamen-tabla');
        var cab = el('tr');
        ['Medicamento', 'Vía', 'Dosis y frecuencia', 'Duración', 'Cantidad'].forEach(function (h) { cab.appendChild(el('th', null, h)); });
        t.appendChild(el('thead')).appendChild(cab);
        var cuerpo = t.appendChild(el('tbody'));
        meds.forEach(function (m) {
            var fila = el('tr');
            [m.nombre, m.viaTexto, m.dosisFrecuencia, m.duracion, numero(m.cantidad)].forEach(function (v) { fila.appendChild(el('td', null, v)); });
            cuerpo.appendChild(fila);
        });
        envoltura.appendChild(t);
        return envoltura;
    }

    function pintar(cont, d) {
        cont.replaceChildren();
        if (d.formatoAnterior) {
            seccion(cont, 'Dictamen', [
                campo('Diagnóstico', d.diagnostico),
                campo('Tratamiento', d.tratamiento),
                campo('Observaciones', d.observaciones || 'Ninguna')
            ]);
            return;
        }
        if (d.tieneAlergias)
            cont.appendChild(el('div', 'alerta-alergia', '⚠ Alergias: ' + d.antecedentes.alergicos));

        seccion(cont, '1. Datos generales', [
            campo('Fecha y hora de atención', d.fechaAtencion),
            campo('Tipo de consulta', d.tipoConsultaTexto),
            campo('Causa externa', d.causaExternaTexto)
        ]);

        var a = d.antecedentes || {};
        seccion(cont, '2. S — Subjetivo', [
            campo('Motivo de consulta', d.motivoConsulta ? '“' + d.motivoConsulta + '”' : ''),
            campo('Enfermedad actual', d.enfermedadActual),
            campo('Antecedentes patológicos', a.patologicos),
            campo('Antecedentes quirúrgicos', a.quirurgicos),
            campo('Antecedentes farmacológicos', a.farmacologicos),
            campo('Antecedentes alérgicos', a.alergicos),
            campo('Antecedentes familiares', a.familiares)
        ]);

        var sv = d.signosVitales || {};
        var ta = !vacio(sv.tensionSistolica) ? numero(sv.tensionSistolica) + '/' + numero(sv.tensionDiastolica) + ' mmHg' : '';
        function med(v, u) { return vacio(v) ? '' : numero(v) + ' ' + u; }
        var signos = [
            ['T.A.', ta], ['F.C.', med(sv.frecuenciaCardiaca, 'lpm')], ['F.R.', med(sv.frecuenciaRespiratoria, 'rpm')],
            ['Temp.', med(sv.temperatura, '°C')], ['SpO₂', med(sv.saturacionOxigeno, '%')], ['Peso', med(sv.peso, 'kg')],
            ['Talla', med(sv.talla, 'cm')], ['IMC', vacio(sv.imc) ? '' : numero(sv.imc) + ' (' + clasificarImc(sv.imc) + ')']
        ].filter(function (s) { return !vacio(s[1]); });
        var grid = null;
        if (signos.length) {
            grid = el('div', 'signos-grid');
            signos.forEach(function (s) {
                var c = el('div', 'signo');
                c.appendChild(el('span', 'signo-etiqueta', s[0]));
                c.appendChild(el('span', 'signo-valor', s[1]));
                grid.appendChild(c);
            });
        }
        seccion(cont, '3. O — Objetivo', [grid, campo('Examen físico', d.examenFisico)]);

        var rel = (d.diagnosticosRelacionados || []).map(textoDx).join('\n');
        seccion(cont, '4. A — Análisis', [
            campo('Diagnóstico principal', d.diagnosticoPrincipal ? textoDx(d.diagnosticoPrincipal) : ''),
            campo('Diagnósticos relacionados', rel)
        ]);

        seccion(cont, '5. P — Plan', [
            d.medicamentos && d.medicamentos.length ? el('strong', 'dictamen-etiqueta-bloque', 'Fórmula médica') : null,
            tablaMedicamentos(d.medicamentos),
            campo('Órdenes médicas', d.ordenesMedicas),
            campo('Recomendaciones / dieta', d.recomendaciones),
            campo('Incapacidad', vacio(d.incapacidadDias) ? '' : d.incapacidadDias + (d.incapacidadDias == 1 ? ' día' : ' días') + ' — ' + d.incapacidadJustificacion)
        ]);

        if (!vacio(d.firmadoPor)) {
            seccion(cont, 'Firma', [
                el('p', null, 'Dr(a). ' + d.firmadoPor + (d.especialidadFirmante ? ' — ' + d.especialidadFirmante : '')
                        + (d.fechaEmision ? ' · emitido el ' + d.fechaEmision : '')),
                el('p', 'dictamen-declaracion', 'El médico firmante declara que este dictamen refleja su evaluación profesional de la consulta.')
            ]);
        }
    }

    window.verDictamen = function (citaId) {
        var d = (CFG.datos || {})[citaId];
        if (!d) return;
        pintar($('verDictamenCuerpo'), d);
        $('verDictamenModal').style.display = 'flex';
    };

    window.cerrarVerModal = function () {
        $('verDictamenModal').style.display = 'none';
    };

    $('dictamenModal').addEventListener('click', function (e) { if (e.target === this) window.cerrarModal(); });
    $('verDictamenModal').addEventListener('click', function (e) { if (e.target === this) window.cerrarVerModal(); });
})();
