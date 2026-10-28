(function () {
    'use strict';

    var input = document.getElementById('password');
    var button = document.getElementById('togglePassword');
    var icon = document.getElementById('passwordIcon');

    if (!input || !button || !icon) {
        console.error('No se pudo inicializar el control para mostrar la contraseña.');
        return;
    }

    button.addEventListener('click', function () {
        var showPassword = input.type === 'password';

        input.type = showPassword ? 'text' : 'password';
        button.setAttribute('aria-pressed', String(showPassword));
        button.setAttribute('aria-label', showPassword ? 'Ocultar contraseña' : 'Mostrar contraseña');
        icon.textContent = showPassword ? 'visibility_off' : 'visibility';
    });
})();
