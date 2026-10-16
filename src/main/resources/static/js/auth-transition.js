(function () {
    const storageKey = 'besalud-auth-entry';

    try {
        const entryDirection = sessionStorage.getItem(storageKey);
        if (entryDirection === 'from-left' || entryDirection === 'from-right') {
            document.documentElement.dataset.authEntry = entryDirection;
            sessionStorage.removeItem(storageKey);
        }
    } catch (error) {}

    document.addEventListener('DOMContentLoaded', function () {
        document.querySelectorAll('[data-auth-direction]').forEach(function (link) {
            link.addEventListener('click', function (event) {
                if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey ||
                    window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
                    return;
                }

                const destination = new URL(link.href, window.location.href);
                if (destination.origin !== window.location.origin || destination.pathname === window.location.pathname) {
                    return;
                }

                event.preventDefault();
                const direction = link.dataset.authDirection === 'forward' ? 'forward' : 'backward';
                try {
                    sessionStorage.setItem(storageKey, direction === 'forward' ? 'from-right' : 'from-left');
                } catch (error) {
                    window.location.assign(destination.href);
                    return;
                }

                document.body.classList.add('is-leaving-' + direction);
                window.setTimeout(function () {
                    window.location.assign(destination.href);
                }, 240);
            });
        });
    });
})();