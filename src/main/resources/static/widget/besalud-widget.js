/*
 * Widget de agenda de BeSalud para la web de un consultorio.
 *
 * El paciente se registra, elige horario, paga el abono y ve o cancela sus citas sin salir de la web del
 * consultorio: el widget habla con /api/v1/c/{consultorio}/** y dibuja todo aquí, con los colores del sitio.
 *
 * Uso:
 *   <script src="https://api.besalud.co/widget/besalud-widget.js" data-consultorio="mi-consultorio" defer></script>
 *   <a href="#" data-besalud="agendar">Agendar</a>          (data-doctor="ID" para un médico concreto)
 *   <a href="#" data-besalud="mis-citas">Mis citas</a>
 * data-titulo="Nombre" en el script cambia el título de la ventana (por defecto, el del consultorio).
 *
 * Colores (opcionales, en el CSS del sitio): --bsw-primario, --bsw-primario-texto, --bsw-fondo, --bsw-texto,
 * --bsw-suave, --bsw-borde, --bsw-radio, --bsw-fuente, --bsw-fuente-titulo.
 *
 * Datos prellenados (como en Calendly o Cal.com): data-email, data-nombre, data-apellido, data-telefono y
 * data-motivo en el botón, o BeSalud.abrir("agendar", { doctor, email, nombre, apellido, telefono, motivo }).
 *
 * Eventos (como calendly.event_scheduled o bookingSuccessful de Cal.com), en window:
 *   besalud:listo, besalud:abierto, besalud:cerrado, besalud:sesion-iniciada, besalud:turno-apartado,
 *   besalud:cita-confirmada, besalud:pago-fallido, besalud:cita-cancelada
 *   window.addEventListener("besalud:cita-confirmada", (e) => console.log(e.detail.referencia));
 *   BeSalud.on("cita-confirmada", (detalle) => ...)   // devuelve una función para dejar de escuchar
 * Los eventos no llevan datos personales del paciente (ni nombre, ni correo, ni documento).
 */
(function () {
  "use strict";

  const script = document.currentScript;
  const cfg = window.CONSULTORIO || {};
  const API = (script.dataset.api || cfg.apiBase || new URL(script.src).origin).replace(/\/+$/, "");
  const CID = script.dataset.consultorio || cfg.id;
  const DOCTOR_PREFERIDO = script.dataset.doctor || cfg.doctorId || null;
  // Nombre que se muestra arriba de la ventana; sin él, el del consultorio registrado en BeSalud.
  const TITULO = script.dataset.titulo || cfg.titulo || null;
  if (!CID) {
    console.warn("[BeSalud] Falta data-consultorio en el script del widget.");
    return;
  }
  const BASE = API + "/api/v1/c/" + encodeURIComponent(CID);
  const CLAVE_SESION = "besalud:" + CID;

  const pesos = new Intl.NumberFormat("es-CO", { style: "currency", currency: "COP", maximumFractionDigits: 0 });
  const diaCorto = new Intl.DateTimeFormat("es-CO", { weekday: "short", day: "numeric", month: "short" });
  const diaLargo = new Intl.DateTimeFormat("es-CO", { weekday: "long", day: "numeric", month: "long" });
  const aFecha = (s) => { const [a, m, d] = s.split("-").map(Number); return new Date(a, m - 1, d); };
  const horaBonita = (s) => {
    const [h, m] = s.split(":").map(Number);
    return (h % 12 || 12) + ":" + String(m).padStart(2, "0") + (h < 12 ? " a. m." : " p. m.");
  };
  const fechaBonita = (s) => { const t = diaLargo.format(aFecha(s)); return t.charAt(0).toUpperCase() + t.slice(1); };

  // ── Sesión (solo en esta pestaña) ─────────────────────────────────────────
  let sesion = leerSesion();

  function leerSesion() {
    try {
      const s = JSON.parse(sessionStorage.getItem(CLAVE_SESION));
      return s && s.exp > Date.now() ? s : null;
    } catch (e) { return null; }
  }
  function guardarSesion(r) {
    sesion = { token: r.token, exp: Date.now() + (r.expiraEnSegundos - 60) * 1000, paciente: r.paciente };
    try { sessionStorage.setItem(CLAVE_SESION, JSON.stringify(sesion)); } catch (e) { /* modo privado: vive en memoria */ }
  }
  function cerrarSesion() {
    sesion = null;
    try { sessionStorage.removeItem(CLAVE_SESION); } catch (e) { /* nada que borrar */ }
  }

  // ── Eventos para la web del consultorio ───────────────────────────────────
  function emitir(nombre, detalle) {
    window.dispatchEvent(new CustomEvent("besalud:" + nombre, { detail: Object.assign({ consultorio: CID }, detalle) }));
  }
  function on(nombre, callback) {
    const oyente = (e) => callback(e.detail);
    window.addEventListener("besalud:" + nombre, oyente);
    return () => window.removeEventListener("besalud:" + nombre, oyente);
  }

  // Clave para reintentos seguros (Idempotency-Key): la misma mientras sea el mismo intento de reserva.
  function nuevaClave() {
    if (window.crypto && crypto.randomUUID) return crypto.randomUUID();
    return Date.now().toString(36) + "-" + Math.random().toString(36).slice(2, 12);
  }

  async function api(ruta, opciones) {
    const { metodo = "GET", cuerpo, cabeceras } = opciones || {};
    const headers = Object.assign({ "ngrok-skip-browser-warning": "true" }, cabeceras);
    if (cuerpo !== undefined) headers["Content-Type"] = "application/json";
    if (sesion) headers.Authorization = "Bearer " + sesion.token;
    let res;
    try {
      res = await fetch(BASE + ruta, { method: metodo, headers, body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo) });
    } catch (e) {
      throw new Error("No pudimos conectar con el sistema de citas. Revisa tu conexión e intenta de nuevo.");
    }
    let datos = null;
    try { datos = await res.json(); } catch (e) { /* respuesta sin cuerpo */ }
    if (res.status === 401 && sesion) cerrarSesion();
    if (!res.ok) {
      const error = new Error((datos && datos.error) || (res.status === 429
        ? "Demasiadas solicitudes desde tu conexión. Espera unos minutos e intenta de nuevo."
        : "Algo salió mal (" + res.status + "). Intenta de nuevo."));
      error.status = res.status;
      throw error;
    }
    return datos;
  }

  // ── DOM sin innerHTML para datos (todo texto va como textContent) ─────────
  function h(tag, attrs, ...hijos) {
    const el = document.createElement(tag);
    for (const [k, v] of Object.entries(attrs || {})) {
      if (v === null || v === undefined || v === false) continue;
      if (k === "class") el.className = v;
      else if (k.startsWith("on")) el.addEventListener(k.slice(2), v);
      else if (k === "text") el.textContent = v;
      else el.setAttribute(k, v === true ? "" : v);
    }
    for (const hijo of hijos.flat()) {
      if (hijo === null || hijo === undefined || hijo === false) continue;
      el.append(hijo instanceof Node ? hijo : document.createTextNode(String(hijo)));
    }
    return el;
  }

  // ── Contenedor (Shadow DOM: los estilos del sitio no rompen el widget ni al revés) ──
  const host = h("div", { id: "besalud-widget" });
  const raiz = host.attachShadow({ mode: "open" });
  raiz.innerHTML = `<style>${ESTILOS()}</style>
    <div class="capa" hidden>
      <div class="fondo" data-cerrar></div>
      <div class="caja" role="dialog" aria-modal="true" aria-labelledby="bsw-titulo">
        <header class="cabeza">
          <strong id="bsw-titulo" class="titulo"></strong>
          <div class="cuenta"></div>
          <button type="button" class="cerrar" data-cerrar aria-label="Cerrar">×</button>
        </header>
        <div class="cuerpo" aria-live="polite"></div>
      </div>
    </div>`;
  const capa = raiz.querySelector(".capa");
  const cuerpo = raiz.querySelector(".cuerpo");
  const titulo = raiz.querySelector(".titulo");
  const barraCuenta = raiz.querySelector(".cuenta");
  raiz.querySelectorAll("[data-cerrar]").forEach((b) => b.addEventListener("click", cerrar));

  let consultorio = null;
  let temporizador = null;
  let desbordeAnterior = "";
  let prellenado = {};

  function pintar(...nodos) {
    clearInterval(temporizador);
    cuerpo.replaceChildren(...nodos.filter(Boolean));
    pintarCuenta();
    const primero = cuerpo.querySelector("input, select, textarea, button");
    if (primero) primero.focus({ preventScroll: true });
    cuerpo.scrollTop = 0;
  }

  function pintarCuenta() {
    barraCuenta.replaceChildren();
    if (!sesion) return;
    barraCuenta.append(
      h("span", { class: "hola", text: "Hola, " + sesion.paciente.nombre }),
      h("button", { type: "button", class: "enlace", onclick: verMisCitas, text: "Mis citas" }),
      h("button", { type: "button", class: "enlace", onclick: () => { cerrarSesion(); agendar(); }, text: "Salir" })
    );
  }

  function cargando(texto) {
    pintar(h("div", { class: "centro" }, h("div", { class: "giro", "aria-hidden": "true" }), h("p", { text: texto })));
  }

  function aviso(texto, tipo) {
    return h("p", { class: "aviso " + (tipo || "error"), role: tipo === "ok" ? "status" : "alert", text: texto });
  }

  function error(e, reintentar) {
    pintar(h("div", { class: "centro" },
      h("div", { class: "icono mal", "aria-hidden": "true", text: "!" }),
      h("p", { text: e.message }),
      reintentar && h("button", { type: "button", class: "btn", onclick: reintentar, text: "Intentar de nuevo" })));
  }

  /** abrir("agendar" | "mis-citas", idDelMedico) o abrir(vista, { doctor, email, nombre, apellido, telefono, motivo }). */
  async function abrir(vista, opciones) {
    const o = typeof opciones === "string" ? { doctor: opciones } : (opciones || {});
    const doctorId = o.doctor || null;
    prellenado = {};
    ["email", "nombre", "apellido", "telefono", "motivo"].forEach((k) => {
      if (typeof o[k] === "string" && o[k].trim()) prellenado[k] = o[k].trim().slice(0, k === "motivo" ? 500 : 120);
    });
    if (capa.hidden) {
      desbordeAnterior = document.body.style.overflow;
      document.body.style.overflow = "hidden";
      capa.hidden = false;
      emitir("abierto", { vista: vista === "mis-citas" ? "mis-citas" : "agendar" });
    }
    cargando("Cargando…");
    try {
      if (!consultorio) consultorio = await api("");
      titulo.textContent = TITULO || consultorio.nombre;
    } catch (e) {
      return error(e, () => abrir(vista, doctorId));
    }
    if (vista === "mis-citas") verMisCitas();
    else if (vista !== "retorno") agendar(doctorId);
  }

  function cerrar() {
    clearInterval(temporizador);
    if (capa.hidden) return;
    capa.hidden = true;
    document.body.style.overflow = desbordeAnterior;
    emitir("cerrado", {});
  }

  // ── 1. Cuenta ─────────────────────────────────────────────────────────────
  function pedirCuenta(despues, pestana) {
    const nuevo = pestana === "registro";
    const tabs = h("div", { class: "tabs", role: "tablist" },
      h("button", { type: "button", role: "tab", "aria-selected": String(!nuevo), class: nuevo ? "" : "activa",
        onclick: () => pedirCuenta(despues, "sesion"), text: "Ya tengo cuenta" }),
      h("button", { type: "button", role: "tab", "aria-selected": String(nuevo), class: nuevo ? "activa" : "",
        onclick: () => pedirCuenta(despues, "registro"), text: "Es mi primera vez" }));
    const msg = h("div");
    const campo = (etiqueta, nombre, tipo, extra) => h("label", { class: "campo" }, etiqueta,
      h("input", Object.assign({ name: nombre, type: tipo || "text", required: true, value: prellenado[nombre] }, extra || {})));

    const form = nuevo
      ? h("form", { novalidate: true },
          h("div", { class: "fila" }, campo("Nombre", "nombre", "text", { autocomplete: "given-name" }),
            campo("Apellido", "apellido", "text", { autocomplete: "family-name" })),
          h("div", { class: "fila" }, campo("Documento", "identificacion", "text", { inputmode: "numeric" }),
            campo("Celular", "telefono", "tel", { autocomplete: "tel" })),
          campo("Correo electrónico", "email", "email", { autocomplete: "email" }),
          h("div", { class: "fila" }, campo("Contraseña", "password", "password", { autocomplete: "new-password" }),
            campo("Confirmar", "confirmar", "password", { autocomplete: "new-password" })),
          h("p", { class: "nota", text: "Mínimo 8 caracteres con mayúscula, número y símbolo." }),
          h("label", { class: "check" }, h("input", { type: "checkbox", name: "aceptaPrivacidad" }),
            " Acepto el tratamiento de mis datos personales (",
            h("a", { href: API + "/privacidad", target: "_blank", rel: "noopener", text: "política" }), ")."),
          msg,
          h("button", { type: "submit", class: "btn ancho", text: "Crear cuenta y elegir horario" }))
      : h("form", { novalidate: true },
          campo("Correo electrónico", "email", "email", { autocomplete: "email" }),
          campo("Contraseña", "password", "password", { autocomplete: "current-password" }),
          msg,
          h("button", { type: "submit", class: "btn ancho", text: "Entrar y elegir horario" }));

    form.addEventListener("submit", async (e) => {
      e.preventDefault();
      const d = Object.fromEntries(new FormData(form));
      msg.replaceChildren();
      if (nuevo) {
        const falta = ["nombre", "apellido", "identificacion", "telefono", "email", "password"].some((k) => !String(d[k] || "").trim());
        if (falta) return msg.append(aviso("Completa todos los campos."));
        if (d.password !== d.confirmar) return msg.append(aviso("Las contraseñas no coinciden."));
        if (!d.aceptaPrivacidad) return msg.append(aviso("Debes aceptar el tratamiento de datos."));
      } else if (!d.email || !d.password) {
        return msg.append(aviso("Escribe tu correo y tu contraseña."));
      }
      const boton = form.querySelector("button[type=submit]");
      boton.disabled = true;
      try {
        const r = nuevo
          ? await api("/pacientes", { metodo: "POST", cuerpo: {
              nombre: d.nombre.trim(), apellido: d.apellido.trim(), identificacion: d.identificacion.trim(),
              telefono: d.telefono.trim(), email: d.email.trim(), password: d.password, aceptaPrivacidad: true } })
          : await api("/sesion", { metodo: "POST", cuerpo: { email: d.email.trim(), password: d.password } });
        guardarSesion(r);
        emitir("sesion-iniciada", { nuevo });
        despues();
      } catch (err) {
        boton.disabled = false;
        msg.append(aviso(err.message));
        if (err.status === 409) msg.append(h("button", { type: "button", class: "enlace",
          onclick: () => pedirCuenta(despues, "sesion"), text: "Ir a «Ya tengo cuenta»" }));
      }
    });

    pintar(h("h2", { text: "Agenda tu cita" }),
      h("p", { class: "sub", text: nuevo ? "Crea tu cuenta en un minuto." : "Entra con tu correo y contraseña." }),
      tabs, form);
  }

  // ── 2. Médico y horario ───────────────────────────────────────────────────
  function agendar(doctorId) {
    if (!sesion) return pedirCuenta(() => agendar(doctorId), "sesion");
    const medicos = consultorio.medicos;
    if (!medicos.length) return error(new Error("Por ahora no hay médicos con agenda en línea."));
    const elegido = medicos.find((m) => m.id === (doctorId || DOCTOR_PREFERIDO));
    if (elegido || medicos.length === 1) return elegirHorario(elegido || medicos[0]);
    pintar(h("h2", { text: "¿Con quién quieres tu cita?" }),
      h("div", { class: "lista" }, medicos.map((m) => h("button", { type: "button", class: "opcion",
        onclick: () => elegirHorario(m) }, h("strong", { text: m.nombre }), h("small", { text: m.especialidad || "" })))));
  }

  async function elegirHorario(medico, mensaje) {
    cargando("Buscando horarios disponibles…");
    let dias;
    try {
      dias = (await api("/disponibilidad?doctorId=" + encodeURIComponent(medico.id))).dias;
    } catch (e) {
      return error(e, () => elegirHorario(medico));
    }
    const fechas = Object.keys(dias);
    const encabezado = [
      h("div", { class: "medico" }, h("strong", { text: medico.nombre }), h("small", { text: medico.especialidad || "" })),
      mensaje && aviso(mensaje)
    ];
    if (!fechas.length)
      return pintar(...encabezado, h("div", { class: "centro" },
        h("p", { text: "No hay horarios libres en los próximos 30 días. Llámanos y te ayudamos." })));

    let fecha = fechas[0];
    let hora = null;
    const chipsDias = h("div", { class: "dias", role: "listbox", "aria-label": "Día" });
    const rejillaHoras = h("div", { class: "horas", role: "listbox", "aria-label": "Hora" });
    const motivo = h("textarea", { name: "motivo", rows: "3", maxlength: "500",
      placeholder: "Ej.: control de crecimiento, fiebre desde ayer…" });
    if (prellenado.motivo) motivo.value = prellenado.motivo;
    // Una clave por intento: si la red falla y el paciente reintenta, no se aparta un segundo turno.
    let clave = nuevaClave();
    let datosClave = "";
    const msg = h("div");
    const continuar = h("button", { type: "submit", class: "btn ancho", disabled: true,
      text: "Continuar al pago" });

    function pintarDias() {
      chipsDias.replaceChildren(...fechas.map((f) => h("button", { type: "button", role: "option",
        "aria-selected": String(f === fecha), class: "chip" + (f === fecha ? " sel" : ""),
        onclick: () => { fecha = f; hora = null; pintarDias(); } },
        h("span", { text: diaCorto.format(aFecha(f)) }), h("small", { text: dias[f].length + " libres" }))));
      rejillaHoras.replaceChildren(...dias[fecha].map((t) => h("button", { type: "button", role: "option",
        "aria-selected": String(t === hora), class: "hora" + (t === hora ? " sel" : ""),
        onclick: () => { hora = t; pintarDias(); }, text: horaBonita(t) })));
      continuar.disabled = !hora;
    }
    pintarDias();

    const form = h("form", { novalidate: true },
      h("h3", { text: "Día" }), chipsDias,
      h("h3", { text: "Hora" }), rejillaHoras,
      h("label", { class: "campo" }, "Motivo de la consulta", motivo),
      h("p", { class: "nota", text: "Para confirmar pagas un abono en línea; el resto lo pagas en el consultorio." }),
      msg, continuar);
    form.addEventListener("submit", async (e) => {
      e.preventDefault();
      msg.replaceChildren();
      if (!motivo.value.trim()) return msg.append(aviso("Cuéntanos brevemente el motivo de la consulta."));
      continuar.disabled = true;
      const datos = [fecha, hora, motivo.value.trim()].join("|");
      if (datos !== datosClave) { clave = nuevaClave(); datosClave = datos; }
      try {
        const reserva = await api("/citas", { metodo: "POST", cabeceras: { "Idempotency-Key": clave },
          cuerpo: { doctorId: medico.id, fecha, hora, motivo: motivo.value.trim(), retorno: location.href.split("#")[0] } });
        emitir("turno-apartado", { referencia: reserva.referencia, medicoId: medico.id, fecha: reserva.fecha,
          hora: reserva.hora, abono: reserva.abono });
        pagar(reserva, medico);
      } catch (err) {
        if (err.status === 401) return pedirCuenta(() => elegirHorario(medico), "sesion");
        if (err.status === 409) return elegirHorario(medico, err.message);
        continuar.disabled = false;
        msg.append(aviso(err.message));
      }
    });
    pintar(...encabezado, form);
  }

  // ── 3. Pago del abono ─────────────────────────────────────────────────────
  function pagar(reserva, medico) {
    const reloj = h("strong", { class: "reloj" });
    let restantes = reserva.segundosParaPagar;
    const tic = () => {
      reloj.textContent = Math.floor(restantes / 60) + ":" + String(restantes % 60).padStart(2, "0");
      if (restantes-- <= 0) {
        clearInterval(temporizador);
        elegirHorario(medico, "Se acabó el tiempo para pagar y el horario quedó libre. Elige de nuevo.");
      }
    };
    const msg = h("div");
    const resumen = h("dl", { class: "resumen" },
      h("dt", { text: "Profesional" }), h("dd", { text: reserva.medico }),
      h("dt", { text: "Fecha" }), h("dd", { text: fechaBonita(reserva.fecha) + ", " + horaBonita(reserva.hora) }),
      h("dt", { text: "Abono ahora" }), h("dd", { class: "grande", text: pesos.format(reserva.abono) }),
      h("dt", { text: "En el consultorio" }), h("dd", { text: pesos.format(reserva.saldo) }));

    let acciones;
    if (reserva.pago.tipo === "wompi") {
      acciones = h("button", { type: "button", class: "btn ancho", onclick: () => { location.href = reserva.pago.url; },
        text: "Pagar " + pesos.format(reserva.abono) + " con Wompi" });
    } else {
      const enviar = async (aprobar, boton) => {
        boton.disabled = true;
        try {
          const estado = await api("/pagos/" + encodeURIComponent(reserva.referencia) + "/simulado",
            { metodo: "POST", cuerpo: { aprobar } });
          resultado(estado, medico);
        } catch (err) {
          boton.disabled = false;
          msg.replaceChildren(aviso(err.message));
        }
      };
      acciones = h("div", { class: "pruebas" },
        h("p", { class: "aviso info", text: "Modo pruebas: no se cobra dinero real." }),
        h("button", { type: "button", class: "btn ancho", onclick: (e) => enviar(true, e.currentTarget),
          text: "Pagar " + pesos.format(reserva.abono) + " (aprobado)" }),
        h("button", { type: "button", class: "btn secundario ancho", onclick: (e) => enviar(false, e.currentTarget),
          text: "Simular pago rechazado" }));
    }

    pintar(h("h2", { text: "Confirma con tu abono" }),
      h("p", { class: "sub" }, "Te guardamos este horario por ", reloj, "."),
      resumen, msg, acciones,
      h("button", { type: "button", class: "enlace centrado", onclick: () => elegirHorario(medico), text: "Cambiar horario" }));
    tic();
    temporizador = setInterval(tic, 1000);
  }

  // ── 4. Resultado ──────────────────────────────────────────────────────────
  function resultado(estado, medico) {
    const volver = () => (medico ? elegirHorario(medico) : agendar());
    if (estado.estado === "PROCESANDO" || estado.estado === "ESPERANDO_PAGO") return esperarPago(estado.referencia, null, medico);
    if (estado.estado === "CONFIRMADA") {
      emitir("cita-confirmada", { referencia: estado.referencia, medico: estado.medico, fecha: estado.fecha,
        hora: estado.hora, abono: estado.abono });
      return pintar(h("div", { class: "centro" },
        h("div", { class: "icono ok", "aria-hidden": "true", text: "✓" }),
        h("h2", { text: "¡Tu cita está confirmada!" }),
        h("p", { text: "Te esperamos el " + fechaBonita(estado.fecha).toLowerCase() + " a las " + horaBonita(estado.hora) + "." })),
        h("dl", { class: "resumen" },
          h("dt", { text: "Profesional" }), h("dd", { text: estado.medico }),
          h("dt", { text: "Abono pagado" }), h("dd", { text: pesos.format(estado.abono) + (estado.medioPago ? " · " + estado.medioPago : "") }),
          h("dt", { text: "En el consultorio" }), h("dd", { text: pesos.format(estado.saldo) }),
          h("dt", { text: "Referencia" }), h("dd", { text: estado.referencia })),
        h("p", { class: "nota", text: "Si no puedes ir, cancela antes del " + fechaHoraBonita(estado.cancelarHasta) + " y te devolvemos el abono." }),
        h("button", { type: "button", class: "btn ancho", onclick: verMisCitas, text: "Ver mis citas" }),
        h("button", { type: "button", class: "enlace centrado", onclick: cerrar, text: "Cerrar" }));
    }

    emitir("pago-fallido", { referencia: estado.referencia, estado: estado.estado });
    const textos = {
      RECHAZADA: ["El pago no fue aprobado", "No se hizo ningún cobro. Puedes intentarlo de nuevo."],
      CONFLICTO: ["Ese horario se ocupó", "Recibimos tu pago pero el horario ya no estaba libre: te devolveremos el abono."],
      EXPIRADA: ["Se venció el tiempo para pagar", "El horario quedó libre. Elige uno nuevo."]
    };
    const [t, d] = textos[estado.estado] || ["No pudimos confirmar tu pago", "Intenta de nuevo en unos minutos."];
    pintar(h("div", { class: "centro" },
      h("div", { class: "icono mal", "aria-hidden": "true", text: "!" }),
      h("h2", { text: t }), h("p", { text: d }),
      h("button", { type: "button", class: "btn", onclick: volver, text: "Elegir horario" })));
  }

  function fechaHoraBonita(iso) {
    const [f, t] = iso.split("T");
    return fechaBonita(f).toLowerCase() + ", " + horaBonita(t.slice(0, 5));
  }

  // Al volver de Wompi (o mientras PSE/Nequi confirman) se consulta hasta tener respuesta.
  function esperarPago(referencia, transaccion, medico) {
    cargando("Confirmando tu pago…");
    let intentos = 0;
    const consultar = async () => {
      try {
        const q = transaccion && intentos === 0 ? "?id=" + encodeURIComponent(transaccion) : "";
        const estado = await api("/pagos/" + encodeURIComponent(referencia) + q);
        if ((estado.estado === "PROCESANDO" || estado.estado === "ESPERANDO_PAGO") && ++intentos < 40) {
          temporizador = setTimeout(consultar, 3000);
          return;
        }
        resultado(estado.estado === "ESPERANDO_PAGO" ? Object.assign(estado, { estado: "EXPIRADA" }) : estado, medico);
      } catch (e) {
        if (e.status === 401) return pedirCuenta(() => esperarPago(referencia, transaccion, medico), "sesion");
        error(e, () => esperarPago(referencia, transaccion, medico));
      }
    };
    consultar();
  }

  // ── Mis citas ─────────────────────────────────────────────────────────────
  async function verMisCitas() {
    if (!sesion) return pedirCuenta(verMisCitas, "sesion");
    cargando("Cargando tus citas…");
    let citas;
    try {
      citas = await api("/citas");
    } catch (e) {
      if (e.status === 401) return pedirCuenta(verMisCitas, "sesion");
      return error(e, verMisCitas);
    }
    const tarjeta = (c) => {
      const zona = h("div", { class: "acciones" });
      if (c.puedeCancelar) {
        const preguntar = () => zona.replaceChildren(
          h("p", { class: "nota", text: c.abono == null ? "¿Seguro que quieres cancelar?"
            : c.devolucionSiCancela ? "Se te devolverá el abono de " + pesos.format(c.abono) + "."
            : "Ya pasó el plazo: el abono de " + pesos.format(c.abono) + " no se devuelve." }),
          h("button", { type: "button", class: "btn peligro", onclick: async (e) => {
            e.currentTarget.disabled = true;
            try {
              const r = await api("/citas/" + encodeURIComponent(c.id) + "/cancelar", { metodo: "POST" });
              emitir("cita-cancelada", { citaId: c.id, devolucion: !!(r && r.devolucion) });
              verMisCitas();
            } catch (err) { zona.replaceChildren(aviso(err.message)); }
          }, text: "Sí, cancelar" }),
          h("button", { type: "button", class: "enlace", onclick: () => zona.replaceChildren(boton), text: "No" }));
        const boton = h("button", { type: "button", class: "btn secundario", onclick: preguntar, text: "Cancelar cita" });
        zona.append(boton);
      }
      return h("li", { class: "cita" },
        h("div", { class: "cita-cabeza" }, h("strong", { text: fechaBonita(c.fecha) + " · " + horaBonita(c.hora) }),
          h("span", { class: "estado e-" + c.estado.toLowerCase().replace(/\s+/g, "-"), text: c.estado })),
        h("small", { text: c.medico + (c.especialidad ? " · " + c.especialidad : "") }),
        c.motivo && h("p", { class: "motivo", text: c.motivo }),
        zona);
    };
    pintar(h("h2", { text: "Mis citas" }),
      citas.length ? h("ul", { class: "citas" }, citas.map(tarjeta))
        : h("p", { class: "sub", text: "Aún no tienes citas en este consultorio." }),
      h("button", { type: "button", class: "btn ancho", onclick: () => agendar(), text: "Agendar una cita" }));
  }

  // ── Arranque ──────────────────────────────────────────────────────────────
  document.addEventListener("click", (e) => {
    const disparador = e.target.closest("[data-besalud]");
    if (!disparador) return;
    e.preventDefault();
    const d = disparador.dataset;
    abrir(d.besalud, { doctor: d.doctor, email: d.email, nombre: d.nombre, apellido: d.apellido,
      telefono: d.telefono, motivo: d.motivo });
  });
  document.addEventListener("keydown", (e) => { if (e.key === "Escape" && !capa.hidden) cerrar(); });

  function iniciar() {
    document.body.append(host);
    // Regreso desde Wompi: ?bs_ref=REFERENCIA&id=TRANSACCION
    const params = new URLSearchParams(location.search);
    const ref = params.get("bs_ref");
    if (ref) {
      const transaccion = params.get("id");
      params.delete("bs_ref");
      params.delete("id");
      history.replaceState(null, "", location.pathname + (params.toString() ? "?" + params : "") + location.hash);
      abrir("retorno").then(() => esperarPago(ref, transaccion, null));
    }
  }
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", iniciar);
  else iniciar();

  window.BeSalud = { abrir, cerrar, cerrarSesion, on, version: "2" };
  emitir("listo", {});

  function ESTILOS() {
    return `
    :host { all: initial; }
    .capa { --p: var(--bsw-primario, #1f6f5c); --pt: var(--bsw-primario-texto, #fff); --f: var(--bsw-fondo, #fff);
      --t: var(--bsw-texto, #1d2433); --s: var(--bsw-suave, #f3f5f7); --b: var(--bsw-borde, #dfe3e8);
      --r: var(--bsw-radio, 16px); --fu: var(--bsw-fuente, system-ui, -apple-system, "Segoe UI", sans-serif);
      --ft: var(--bsw-fuente-titulo, var(--fu)); --ok: #1f8a5b; --mal: #c0392b; --info: #2563a8;
      position: fixed; inset: 0; z-index: 2147483000; display: grid; place-items: center; padding: 16px;
      font-family: var(--fu); color: var(--t); font-size: 16px; line-height: 1.5; }
    .capa[hidden] { display: none; }
    .fondo { position: absolute; inset: 0; background: rgba(15, 23, 42, .55); }
    .caja { position: relative; width: min(560px, 100%); max-height: min(92vh, 860px); display: flex; flex-direction: column;
      background: var(--f); border-radius: var(--r); box-shadow: 0 24px 60px rgba(15, 23, 42, .3); overflow: hidden; }
    .cabeza { display: flex; align-items: center; gap: 12px; padding: 14px 16px 14px 20px; border-bottom: 1px solid var(--b); }
    .titulo { font-family: var(--ft); font-size: 1.05rem; flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .cuenta { display: flex; align-items: center; gap: 10px; font-size: .85rem; }
    .hola { opacity: .75; }
    .cerrar { width: 36px; height: 36px; border-radius: 50%; border: 0; background: var(--s); color: var(--t);
      font-size: 1.4rem; line-height: 1; cursor: pointer; flex: none; }
    .cuerpo { padding: 20px; overflow-y: auto; }
    h2 { font-family: var(--ft); font-size: 1.45rem; line-height: 1.2; margin: 0 0 4px; }
    h3 { font-size: .8rem; text-transform: uppercase; letter-spacing: .06em; opacity: .7; margin: 18px 0 8px; }
    .sub { margin: 0 0 16px; opacity: .8; }
    .nota { font-size: .85rem; opacity: .75; margin: 10px 0; }
    form { display: grid; gap: 10px; }
    .fila { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
    .campo { display: grid; gap: 4px; font-size: .88rem; font-weight: 600; }
    input:not([type=checkbox]), textarea { font: inherit; font-weight: 400; color: var(--t); background: var(--f);
      border: 1.5px solid var(--b); border-radius: calc(var(--r) * .6); padding: 10px 12px; min-height: 44px; width: 100%; box-sizing: border-box; }
    textarea { resize: vertical; }
    input:focus, textarea:focus, button:focus-visible { outline: 3px solid color-mix(in srgb, var(--p) 45%, transparent); outline-offset: 1px; }
    .check { display: flex; gap: 8px; align-items: flex-start; font-size: .88rem; }
    .check input { margin-top: 4px; width: 18px; height: 18px; accent-color: var(--p); }
    a { color: var(--p); }
    .btn { font: inherit; font-weight: 700; border: 0; border-radius: calc(var(--r) * .7); padding: 12px 18px; min-height: 46px;
      background: var(--p); color: var(--pt); cursor: pointer; }
    .btn:disabled { opacity: .5; cursor: not-allowed; }
    .btn.secundario { background: var(--s); color: var(--t); }
    .btn.peligro { background: var(--mal); color: #fff; }
    .ancho { width: 100%; margin-top: 6px; }
    .enlace { font: inherit; background: none; border: 0; color: var(--p); text-decoration: underline; cursor: pointer; padding: 6px; }
    .centrado { display: block; margin: 8px auto 0; }
    .tabs { display: grid; grid-template-columns: 1fr 1fr; background: var(--s); border-radius: calc(var(--r) * .7); padding: 4px; margin-bottom: 16px; }
    .tabs button { font: inherit; font-weight: 600; border: 0; background: none; padding: 10px; border-radius: calc(var(--r) * .55); cursor: pointer; color: var(--t); }
    .tabs .activa { background: var(--f); box-shadow: 0 1px 4px rgba(0, 0, 0, .12); }
    .aviso { margin: 4px 0; padding: 10px 12px; border-radius: 10px; font-size: .9rem; }
    .aviso.error { background: color-mix(in srgb, var(--mal) 12%, var(--f)); color: var(--mal); }
    .aviso.info { background: color-mix(in srgb, var(--info) 10%, var(--f)); color: var(--info); }
    .lista { display: grid; gap: 10px; }
    .opcion { font: inherit; text-align: left; display: grid; gap: 2px; padding: 14px 16px; border: 1.5px solid var(--b);
      border-radius: var(--r); background: var(--f); color: var(--t); cursor: pointer; }
    .opcion:hover { border-color: var(--p); }
    .medico { display: grid; padding: 12px 14px; background: var(--s); border-radius: var(--r); }
    small { opacity: .75; }
    .dias { display: flex; gap: 8px; overflow-x: auto; padding-bottom: 6px; scroll-snap-type: x mandatory; }
    .chip { font: inherit; flex: none; display: grid; gap: 2px; text-align: center; min-width: 86px; padding: 10px; scroll-snap-align: start;
      border: 1.5px solid var(--b); border-radius: calc(var(--r) * .7); background: var(--f); color: var(--t); cursor: pointer; text-transform: capitalize; }
    .chip small { font-size: .72rem; text-transform: none; }
    .horas { display: grid; grid-template-columns: repeat(auto-fill, minmax(96px, 1fr)); gap: 8px; }
    .hora { font: inherit; min-height: 44px; border: 1.5px solid var(--b); border-radius: calc(var(--r) * .6); background: var(--f); color: var(--t); cursor: pointer; }
    .chip.sel, .hora.sel { background: var(--p); border-color: var(--p); color: var(--pt); }
    .resumen { display: grid; grid-template-columns: auto 1fr; gap: 8px 16px; margin: 14px 0; padding: 14px 16px; background: var(--s); border-radius: var(--r); }
    .resumen dt { opacity: .7; font-size: .9rem; }
    .resumen dd { margin: 0; text-align: right; font-weight: 600; }
    .resumen .grande { font-size: 1.2rem; color: var(--p); }
    .reloj { font-variant-numeric: tabular-nums; }
    .pruebas { display: grid; gap: 6px; }
    .centro { text-align: center; padding: 12px 0; }
    .icono { width: 64px; height: 64px; margin: 0 auto 12px; border-radius: 50%; display: grid; place-items: center; font-size: 2rem; font-weight: 800; color: #fff; }
    .icono.ok { background: var(--ok); }
    .icono.mal { background: var(--mal); }
    .giro { width: 36px; height: 36px; margin: 16px auto; border-radius: 50%; border: 4px solid var(--s); border-top-color: var(--p); animation: giro 0.8s linear infinite; }
    @keyframes giro { to { transform: rotate(360deg); } }
    .citas { list-style: none; padding: 0; margin: 0 0 12px; display: grid; gap: 10px; }
    .cita { border: 1.5px solid var(--b); border-radius: var(--r); padding: 12px 14px; display: grid; gap: 4px; }
    .cita-cabeza { display: flex; justify-content: space-between; gap: 8px; align-items: baseline; flex-wrap: wrap; }
    .estado { font-size: .75rem; font-weight: 700; padding: 2px 10px; border-radius: 999px; background: var(--s); }
    .e-pendiente { background: color-mix(in srgb, var(--p) 15%, var(--f)); color: var(--p); }
    .e-cancelada { color: var(--mal); }
    .motivo { margin: 2px 0 0; font-size: .9rem; }
    .acciones { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; margin-top: 6px; }
    .acciones .nota { flex-basis: 100%; margin: 0; }
    @media (max-width: 480px) {
      .capa { padding: 0; place-items: end stretch; }
      .caja { width: 100%; max-height: 94vh; border-radius: var(--r) var(--r) 0 0; }
      .fila { grid-template-columns: 1fr; }
    }
    @media (prefers-reduced-motion: reduce) { .giro { animation-duration: 2s; } }`;
  }
})();
