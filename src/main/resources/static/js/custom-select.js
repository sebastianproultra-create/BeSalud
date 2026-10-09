/**
 * BeSalud – Custom Select Dropdown
 * Reemplaza los <select class="styled-select"> por un desplegable con estilo.
 *  - Conserva los <optgroup> como encabezados de grupo.
 *  - Lista con barra de desplazamiento (ver .custom-select-options en global.css).
 *  - Al abrir se desplaza hasta la opción elegida.
 *  - Teclado: ↑ ↓ Inicio Fin Enter Esc, y una letra salta a la siguiente opción que empiece por ella.
 */
(function () {
    'use strict';

    function buildCustomSelect(nativeSelect) {
        if (nativeSelect.closest('.custom-select-wrapper')) return; // already wrapped

        var wrapper = document.createElement('div');
        wrapper.className = 'custom-select-wrapper';

        var trigger = document.createElement('div');
        trigger.className = 'custom-select-trigger';
        trigger.setAttribute('tabindex', '0');
        trigger.setAttribute('role', 'combobox');
        trigger.setAttribute('aria-haspopup', 'listbox');
        trigger.setAttribute('aria-expanded', 'false');

        var optionsContainer = document.createElement('div');
        optionsContainer.className = 'custom-select-options';
        optionsContainer.setAttribute('role', 'listbox');

        // Opciones (con encabezado cuando cambia de <optgroup>)
        var options = nativeSelect.options;
        var grupoActual = null;
        for (var i = 0; i < options.length; i++) {
            var grupo = options[i].parentNode && options[i].parentNode.tagName === 'OPTGROUP'
                ? options[i].parentNode.label : null;
            if (grupo && grupo !== grupoActual) {
                var cab = document.createElement('div');
                cab.className = 'custom-select-group';
                cab.setAttribute('role', 'presentation');
                cab.textContent = grupo;
                optionsContainer.appendChild(cab);
            }
            grupoActual = grupo;

            var opt = document.createElement('div');
            opt.className = 'custom-select-option';
            if (options[i].value === '') opt.classList.add('is-placeholder');
            if (options[i].selected) opt.classList.add('selected');
            opt.setAttribute('role', 'option');
            opt.setAttribute('data-value', options[i].value);
            opt.textContent = options[i].textContent;
            optionsContainer.appendChild(opt);
        }

        function opciones() {
            return Array.prototype.slice.call(optionsContainer.querySelectorAll('.custom-select-option'));
        }

        // Set initial trigger text
        var selectedOpt = nativeSelect.options[nativeSelect.selectedIndex];
        trigger.textContent = selectedOpt ? selectedOpt.textContent : '';

        // Insert into DOM
        nativeSelect.parentNode.insertBefore(wrapper, nativeSelect);
        wrapper.appendChild(nativeSelect);
        wrapper.appendChild(trigger);
        wrapper.appendChild(optionsContainer);

        function marcarActiva(opt) {
            opciones().forEach(function (o) { o.classList.remove('is-active'); });
            if (!opt) return;
            opt.classList.add('is-active');
            // El encabezado de grupo es sticky: sin restar su alto, la opción queda tapada debajo
            var cab = optionsContainer.querySelector('.custom-select-group');
            var alto = cab ? cab.offsetHeight : 0;
            var top = opt.offsetTop, bottom = top + opt.offsetHeight;
            if (top - alto < optionsContainer.scrollTop) optionsContainer.scrollTop = Math.max(0, top - alto);
            else if (bottom > optionsContainer.scrollTop + optionsContainer.clientHeight)
                optionsContainer.scrollTop = bottom - optionsContainer.clientHeight;
        }

        function abrir() {
            closeAllExcept(wrapper);
            wrapper.classList.add('open');
            trigger.setAttribute('aria-expanded', 'true');
            var sel = optionsContainer.querySelector('.custom-select-option.selected');
            if (sel) {
                // Centra la opción elegida en la lista
                optionsContainer.scrollTop = Math.max(0, sel.offsetTop - optionsContainer.clientHeight / 2 + sel.offsetHeight / 2);
                marcarActiva(sel);
            } else {
                optionsContainer.scrollTop = 0;
            }
        }

        function cerrar() {
            wrapper.classList.remove('open');
            trigger.setAttribute('aria-expanded', 'false');
            opciones().forEach(function (o) { o.classList.remove('is-active'); });
        }

        function elegir(target) {
            opciones().forEach(function (o) { o.classList.remove('selected'); });
            target.classList.add('selected');
            trigger.textContent = target.textContent;
            nativeSelect.value = target.getAttribute('data-value');
            wrapper.classList.remove('is-invalid');
            nativeSelect.dispatchEvent(new Event('change', { bubbles: true }));
            cerrar();
        }

        // Toggle open/close
        trigger.addEventListener('click', function (e) {
            e.stopPropagation();
            if (wrapper.classList.contains('open')) cerrar(); else abrir();
        });

        trigger.addEventListener('keydown', function (e) {
            var abierto = wrapper.classList.contains('open');
            var lista = opciones();
            var activa = optionsContainer.querySelector('.custom-select-option.is-active')
                || optionsContainer.querySelector('.custom-select-option.selected');
            var idx = lista.indexOf(activa);

            if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault();
                if (!abierto) abrir();
                else if (activa) elegir(activa); else cerrar();
            } else if (e.key === 'Escape') {
                cerrar();
            } else if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
                e.preventDefault();
                if (!abierto) { abrir(); return; }
                var sig = e.key === 'ArrowDown' ? Math.min(lista.length - 1, idx + 1) : Math.max(0, idx - 1);
                marcarActiva(lista[sig]);
            } else if (e.key === 'Home' || e.key === 'End') {
                if (!abierto) return;
                e.preventDefault();
                marcarActiva(e.key === 'Home' ? lista[0] : lista[lista.length - 1]);
            } else if (e.key.length === 1 && /\S/.test(e.key)) {
                // Una letra salta a la siguiente opción que empiece por ella
                var letra = e.key.toLowerCase();
                var orden = lista.slice(idx + 1).concat(lista.slice(0, idx + 1));
                var hallada = orden.filter(function (o) {
                    return o.getAttribute('data-value') !== '' && o.textContent.toLowerCase().indexOf(letra) === 0;
                })[0];
                if (hallada) { if (!abierto) abrir(); marcarActiva(hallada); }
            }
        });

        // Option click
        optionsContainer.addEventListener('click', function (e) {
            var target = e.target.closest('.custom-select-option');
            if (!target) return;
            e.stopPropagation();
            elegir(target);
        });

        // Si el formulario intenta enviarse sin elegir una opción obligatoria, se marca el campo
        nativeSelect.addEventListener('invalid', function () {
            wrapper.classList.add('is-invalid');
            trigger.focus();
        });
    }

    function closeAllExcept(except) {
        document.querySelectorAll('.custom-select-wrapper.open').forEach(function (w) {
            if (w !== except) {
                w.classList.remove('open');
                var t = w.querySelector('.custom-select-trigger');
                if (t) t.setAttribute('aria-expanded', 'false');
            }
        });
    }

    // Close on outside click
    document.addEventListener('click', function () {
        closeAllExcept(null);
    });

    // Initialise on DOM ready
    document.addEventListener('DOMContentLoaded', function () {
        document.querySelectorAll('select.styled-select').forEach(buildCustomSelect);
    });

    // Expose for dynamic usage
    window.initCustomSelect = buildCustomSelect;
})();