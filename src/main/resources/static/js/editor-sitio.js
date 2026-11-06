/* Editor de páginas web de los médicos (admin): vista previa en vivo mientras se escribe y selector móvil/escritorio. */
(function () {
    "use strict";

    const form = document.querySelector("[data-sitio-editor]");
    const marco = document.querySelector("[data-sitio-marco]");
    if (!form || !marco) return;

    // La vista previa se trae con fetch y se pinta con srcdoc: así no hace falta permitir que BeSalud se
    // enmarque (X-Frame-Options sigue en DENY para todo). Solo se pinta la respuesta más reciente.
    let ultima = 0;
    const actualizar = async () => {
        const datos = new URLSearchParams(new FormData(form));
        datos.delete("_csrf");
        const turno = ++ultima;
        try {
            const res = await fetch(marco.dataset.base + "?" + datos.toString(), { credentials: "same-origin" });
            if (!res.ok) return;
            const html = await res.text();
            if (turno === ultima) marco.srcdoc = html;
        } catch (e) {
            // Sin conexión: se queda la última vista previa
        }
    };

    let espera;
    form.addEventListener("input", () => {
        clearTimeout(espera);
        espera = setTimeout(actualizar, 500);
    });

    // Al cambiar de estilo se propone su color; el médico puede cambiarlo después.
    form.addEventListener("change", (e) => {
        if (e.target.name === "plantilla") {
            const color = form.querySelector('input[name="color"][value="' + e.target.dataset.colorDefecto + '"]');
            if (color) color.checked = true;
        }
        clearTimeout(espera);
        actualizar();
    });

    actualizar();

    const contenedor = document.querySelector("[data-sitio-contenedor]");
    const botones = document.querySelectorAll("[data-sitio-dispositivo]");
    botones.forEach((boton) => boton.addEventListener("click", () => {
        botones.forEach((b) => b.setAttribute("aria-pressed", String(b === boton)));
        contenedor.classList.toggle("sit-vista__marco--escritorio", boton.dataset.sitioDispositivo === "escritorio");
    }));
})();
