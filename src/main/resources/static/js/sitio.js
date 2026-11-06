/* Página web alojada del médico (/sitio/{slug}): menú móvil y, en la vista previa del editor, aviso en
   los botones de la agenda (ahí no se carga el widget). */
(function () {
    "use strict";

    const boton = document.querySelector("[data-sitio-menu]");
    const menu = document.getElementById("sitio-menu");
    if (boton && menu) {
        const cerrar = () => {
            boton.setAttribute("aria-expanded", "false");
            menu.classList.remove("sitio-menu--abierto");
        };
        boton.addEventListener("click", () => {
            const abierto = boton.getAttribute("aria-expanded") === "true";
            boton.setAttribute("aria-expanded", String(!abierto));
            menu.classList.toggle("sitio-menu--abierto", !abierto);
        });
        menu.addEventListener("click", (e) => { if (e.target.closest("a")) cerrar(); });
        document.addEventListener("keydown", (e) => { if (e.key === "Escape") cerrar(); });
    }

    if (document.body.dataset.vistaPrevia !== "true") return;
    const toast = document.querySelector("[data-sitio-toast]");
    let temporizador;
    document.addEventListener("click", (e) => {
        if (!e.target.closest("[data-besalud]")) return;
        e.preventDefault();
        toast.hidden = false;
        clearTimeout(temporizador);
        temporizador = setTimeout(() => { toast.hidden = true; }, 2600);
    });
})();
