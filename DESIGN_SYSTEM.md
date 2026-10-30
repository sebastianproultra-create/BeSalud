# BeSalud Design System

## Objetivo y principios

BeSalud usa una interfaz única, accesible y adaptable. La identidad elegida es **verde bosque y dorado, Newsreader para títulos y Plus Jakarta Sans para texto**; es la estética que ya predominaba en el proyecto.

- Reutilizar tokens y componentes; no duplicar estilos por vista.
- Mantener las reglas y bindings de Thymeleaf existentes.
- Diseñar primero para móvil y no permitir desbordamiento horizontal del documento.
- Mantener estados de foco visibles, contraste legible y compatibilidad con movimiento reducido.

## Archivos y orden de carga

Los archivos compartidos están en `src/main/resources/static/`:

| Archivo | Responsabilidad |
|---|---|
| `css/tokens.css` | Paleta, tipografía, escalas, radios, sombras, capas y breakpoints documentados. |
| `css/base.css` | Reset, cuerpo, medios, foco y accesibilidad. |
| `css/components.css` | Botones, campos, tarjetas, tablas, alertas, badges y paginación. |
| `css/layout.css` | Contenedor, estructura de página y footer. |
| `css/navbar.css` | Estilos compartidos por las navbars de cada rol. |
| `js/navbar.js` | Menús móviles, Escape, cierre al navegar y enlace activo. |
| `templates/fragments/navbar.html` | Selector de navbar según el rol autenticado. |
| `templates/fragments/navbar-admin.html` | Navegación del administrador. |
| `templates/fragments/navbar-doctor.html` | Navegación del médico. |
| `templates/fragments/navbar-paciente.html` | Navegación del paciente. |
| `templates/fragments/navbar-publica.html` | Navegación para usuarios anónimos. |

Todas las plantillas cargan `fragments/layout :: design-system`, en este orden:

1. CSS propio de la vista que aún sea necesario.
2. `tokens.css`
3. `base.css`
4. `components.css`
5. `layout.css`
6. `navbar.css`
7. `navbar.js` con `defer`

Las fuentes se importan desde `tokens.css`. No se requiere framework ni dependencia nueva.

### Compatibilidad con hojas heredadas

Las hojas por vista siguen conteniendo reglas específicas de estructura para conservar las pantallas existentes. Aunque las tipografías se normalizaron a tokens y se eliminaron `!important`, no se ha convertido todavía cada literal de color, espaciado, radio y sombra de esas hojas. No copies esos valores en nuevas reglas; completa su migración a tokens cuando se toque cada componente.

## Tokens

| Categoría | Variable | Uso/valor |
|---|---|---|
| Color | `--color-primary` | Verde bosque `#002f24` |
| Color | `--color-primary-hover` | Verde de interacción `#0d4738` |
| Color | `--color-primary-soft` | Fondo verde suave `#eaf7f0` |
| Color | `--color-secondary` | Verde secundario |
| Color | `--color-accent`, `--color-accent-soft` | Dorado y dorado claro |
| Color | `--color-background` | Fondo menta claro |
| Color | `--color-surface`, `--color-surface-muted` | Superficie y superficie secundaria |
| Color | `--color-text`, `--color-text-muted` | Texto principal y secundario |
| Color | `--color-border`, `--color-border-strong` | Bordes |
| Estado | `--color-success`, `--color-danger`, `--color-warning`, `--color-info` | Estados semánticos |
| Tipografía | `--font-heading` | Newsreader |
| Tipografía | `--font-body` | Plus Jakarta Sans |
| Tipografía de iconos | `--font-symbols`, `--font-mono` | Material Symbols Outlined y ui-monospace |
| Tamaño | `--font-size-xs`, `--font-size-sm`, `--font-size-base`, `--font-size-lg`, `--font-size-xl`, `--font-size-2xl` | Escala tipográfica |
| Espaciado | `--space-1` a `--space-12`, `--space-page` | Escala de separación y margen de página |
| Radio | `--radius-sm`, `--radius-md`, `--radius-lg`, `--radius-pill` | Bordes |
| Sombra | `--shadow-sm`, `--shadow-md`, `--shadow-lg` | Elevación |
| Capa | `--z-content`, `--z-navbar`, `--z-overlay`, `--z-modal` | Orden de apilamiento |
| Transición | `--transition-fast`, `--transition-base` | Duración de transiciones |
| Breakpoint | `--breakpoint-sm`, `--breakpoint-md`, `--breakpoint-lg`, `--breakpoint-xl` | 480, 768, 1024 y 1280 px. CSS no admite variables custom en `@media`; sus equivalentes se documentan junto a cada consulta. |

Los nombres anteriores (`--primary-color`, `--text-dark`, etc.) se mantienen como alias de compatibilidad para los estilos legados.

## Componentes

Convención: BEM con prefijo `bs-`. Los modificadores se escriben `--nombre`.

### Botones

```html
<button class="bs-btn bs-btn--primary" type="submit">Guardar</button>
<a class="bs-btn bs-btn--secondary" th:href="@{/citas}">Volver</a>
<button class="bs-btn bs-btn--danger bs-btn--small" type="button">Cancelar</button>
```

Modificadores disponibles: `bs-btn--primary`, `bs-btn--secondary`, `bs-btn--danger`, `bs-btn--ghost`, `bs-btn--small`, `bs-btn--large`.

### Formularios

```html
<div class="bs-field">
    <label class="bs-field__label" for="email">Correo</label>
    <input class="bs-field__control" id="email" type="email" th:field="*{email}">
    <p class="bs-field__error" th:if="${#fields.hasErrors('email')}" th:errors="*{email}"></p>
</div>
```

### Tarjetas, tablas, alertas y badges

```html
<section class="bs-card"><h2>Resumen</h2><p>Contenido</p></section>
<div class="bs-table-wrap">
    <table class="bs-table"><thead><tr><th>Paciente</th></tr></thead><tbody>...</tbody></table>
</div>
<div class="bs-alert bs-alert--success" role="status">Cambios guardados.</div>
<span class="bs-badge bs-badge--warning">Pendiente</span>
```

Los modificadores de estado disponibles son `success`, `danger`, `warning` e `info`.

## Layout y navbar

Cada vista debe conservar su `<head>` y contenido, incluir el fragmento de diseño compartido en el `<head>`, y reemplazar la cabecera por el selector de navbar:

```html
<head>
    <meta charset="UTF-8">
    <title>Mi vista - BeSalud</title>
    <th:block th:replace="~{fragments/layout :: design-system}"></th:block>
</head>
<body>
    <header th:replace="~{fragments/navbar :: navbar}"></header>
    <main class="bs-container">
        <!-- Contenido de la vista -->
    </main>
    <footer th:replace="~{fragments/layout :: app-footer}"></footer>
</body>
```

El selector incluye la navbar correspondiente al rol, y usa la variante pública para usuarios anónimos. Cada navbar de rol incluye la marca, navegación y cierre de sesión por POST con CSRF. Todas comparten estilos y comportamiento responsive; `navbar.js` actualiza `aria-current="page"` y gestiona cada menú. Para agregar un enlace:

En escritorio, la barra ocupa el ancho disponible; los enlaces, el nombre de usuario y el cierre de sesión usan el mismo tamaño de texto y permanecen en una sola línea. La marca conserva una separación uniforme respecto al menú. En pantallas de hasta 1280 px, la navegación se presenta en el menú colapsable.

1. Confirma el endpoint en un controlador existente y el rol autorizado.
2. Añade el `<a>` al fragmento correspondiente (`navbar-admin`, `navbar-doctor` o `navbar-paciente`) usando `th:href="@{/ruta}"`.
3. Añade `data-route="/ruta"` para el estado activo; usa `data-route-exact` cuando la ruta raíz sea prefijo de otras.
4. No dupliques los estilos ni el comportamiento responsive; los comparten los fragmentos de rol.

## Responsive y accesibilidad

- Breakpoints: 480 px (móvil estrecho), 768 px (tablet), 1024 px (escritorio) y 1280 px (pantalla amplia).
- El menú horizontal colapsa hasta 1280 px para acomodar enlaces por rol; el botón anuncia su estado con `aria-expanded`, se controla por teclado y cierra con Escape o al navegar.
- En móvil, formularios y contenido deben quedar en una columna; controles táctiles deben medir al menos 44 px.
- Tablas deben estar dentro de contenedores con desplazamiento horizontal, sin desplazar el `body`.
- Verificar la vista a 360, 768 y 1280 px, incluyendo errores, estados vacíos, modales y selectores de fecha/hora.
- No eliminar etiquetas accesibles, labels, estados de foco ni atributos ARIA.

## Prohibido

- Estilos inline, atributos `style`, bloques `<style>` en vistas o CSS `!important`.
- Colores, tipografías, espaciados, radios o sombras hardcodeados fuera de los tokens.
- Estilar por ID, redefinir tokens desde una vista o duplicar componentes compartidos.
- Crear navbars fuera de los fragmentos autorizados por rol o enlaces a endpoints no confirmados.
- Cambiar `th:*`, `sec:*`, bindings, validaciones, condicionales o iteraciones al migrar estilos.

## Checklist para una vista nueva

- [ ] Carga el fragmento `design-system` y la navbar compartida.
- [ ] Usa tokens y componentes existentes; no añade estilos inline ni `<style>`.
- [ ] Usa `th:href` y `th:src` en rutas y recursos.
- [ ] Conserva `th:object`, `th:field`, errores, mensajes, condicionales e iteraciones.
- [ ] Revisa 360, 768 y 1280 px; no hay scroll horizontal en `body`.
- [ ] Comprueba foco por teclado, labels, botones táctiles y estados accesibles.
- [ ] Prueba navegación, rol, logout/CSRF y formularios de la vista.
