document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('form').forEach(function (form) {
        form.addEventListener('submit', function () {
            var btn = form.querySelector('button[type="submit"]');
            if (!btn) return;
            btn.disabled = true;
            btn.setAttribute('aria-busy', 'true');
            var original = btn.textContent.trim();
            btn.dataset.original = original;
            btn.textContent = 'Procesando…';
        });
    });
});
