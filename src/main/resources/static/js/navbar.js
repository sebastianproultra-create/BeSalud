(() => {
    const toggle = document.querySelector("[data-navbar-toggle]");
    const menu = document.querySelector("[data-navbar-menu]");
    if (!toggle || !menu) return;

    const closeMenu = () => {
        toggle.setAttribute("aria-expanded", "false");
        toggle.setAttribute("aria-label", "Abrir menú de navegación");
        menu.classList.remove("is-open");
    };

    toggle.addEventListener("click", () => {
        const isExpanded = toggle.getAttribute("aria-expanded") === "true";
        toggle.setAttribute("aria-expanded", String(!isExpanded));
        toggle.setAttribute(
            "aria-label",
            isExpanded ? "Abrir menú de navegación" : "Cerrar menú de navegación"
        );
        menu.classList.toggle("is-open", !isExpanded);
    });

    menu.addEventListener("click", (event) => {
        if (event.target.closest("a")) closeMenu();
    });

    document.addEventListener("keydown", (event) => {
        if (event.key === "Escape" && toggle.getAttribute("aria-expanded") === "true") {
            closeMenu();
            toggle.focus();
        }
    });

    const currentPath = window.location.pathname.replace(/\/+$/, "") || "/";
    menu.querySelectorAll("[data-route]").forEach((link) => {
        const route = link.dataset.route;
        const matches = link.hasAttribute("data-route-exact")
            ? currentPath === route
            : route === "/"
            ? currentPath === "/"
            : currentPath === route || currentPath.startsWith(`${route}/`);
        if (matches) {
            link.classList.add("is-active");
            link.setAttribute("aria-current", "page");
        }
    });
})();
