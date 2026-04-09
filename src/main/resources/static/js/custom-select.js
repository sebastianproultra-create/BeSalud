/**
 * BeSalud – Custom Select Dropdown
 * Replaces native <select> elements with a styled dropdown.
 * Automatically initialises on DOMContentLoaded for every
 * select that has the class "styled-select".
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

        var optionsContainer = document.createElement('div');
        optionsContainer.className = 'custom-select-options';

        // Build options
        var options = nativeSelect.options;
        for (var i = 0; i < options.length; i++) {
            var opt = document.createElement('div');
            opt.className = 'custom-select-option';
            if (options[i].selected) opt.classList.add('selected');
            opt.setAttribute('data-value', options[i].value);
            opt.textContent = options[i].textContent;
            optionsContainer.appendChild(opt);
        }

        // Set initial trigger text
        var selectedOpt = nativeSelect.options[nativeSelect.selectedIndex];
        trigger.textContent = selectedOpt ? selectedOpt.textContent : '';

        // Insert into DOM
        nativeSelect.parentNode.insertBefore(wrapper, nativeSelect);
        wrapper.appendChild(nativeSelect);
        wrapper.appendChild(trigger);
        wrapper.appendChild(optionsContainer);

        // Toggle open/close
        trigger.addEventListener('click', function (e) {
            e.stopPropagation();
            closeAllExcept(wrapper);
            wrapper.classList.toggle('open');
        });

        trigger.addEventListener('keydown', function (e) {
            if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault();
                trigger.click();
            }
        });

        // Option click
        optionsContainer.addEventListener('click', function (e) {
            var target = e.target.closest('.custom-select-option');
            if (!target) return;

            // Update selection
            var allOpts = optionsContainer.querySelectorAll('.custom-select-option');
            allOpts.forEach(function (o) { o.classList.remove('selected'); });
            target.classList.add('selected');

            trigger.textContent = target.textContent;
            nativeSelect.value = target.getAttribute('data-value');

            // Fire change event on the native select
            var changeEvt = new Event('change', { bubbles: true });
            nativeSelect.dispatchEvent(changeEvt);

            wrapper.classList.remove('open');
        });
    }

    function closeAllExcept(except) {
        document.querySelectorAll('.custom-select-wrapper.open').forEach(function (w) {
            if (w !== except) w.classList.remove('open');
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
