# Módulo Solicitante – SAReducción ITT

## Descripción

Interfaz web para **alumnos de reinscripción** y **aspirantes de nuevo ingreso** que deseen solicitar la reducción de monto de inscripción.

Se comunica con el backend (Spring Boot) mediante peticiones `fetch()` a la API REST.

---

## Estructura del proyecto

```
modulo-solicitante/
├── login.html          → Inicio de sesión (alumno / aspirante)
├── panel-estatus.html  → Panel y estado de la solicitud (alumno)
├── panel-aspirante.html→ Panel y estado de la solicitud (aspirante)
├── formulario.html     → Captura de la solicitud (stepper 3 pasos)
├── css/
│   ├── itt-base.css    → Variables, componentes y utilidades base ITT
│   └── solicitante.css → Estilos específicos del módulo
├── js/
│   └── app.js          → Lógica del módulo (login, estatus, formulario)
└── img/
    ├── logo.png
    ├── header.png
    └── footer.png
```

---

## Páginas y equipos responsables

| Página | Funcionalidad | Equipo |
|---|---|---|
| `login.html` | Autenticación de alumno y de aspirante | **Equipo 1** |
| `panel-estatus.html` | Consulta del estado del trámite (alumno) | **Equipo 3** |
| `panel-aspirante.html` | Consulta del estado del trámite (aspirante) | **Equipo 3** |
| `formulario.html` | Captura y envío de la solicitud | **Equipo 5** |

---

## Tecnologías

- **HTML5** – Estructura semántica
- **CSS3 / Vanilla CSS** – Estilos con variables CSS (sin frameworks adicionales)
- **JavaScript (ES6+)** – Lógica del navegador con `fetch()` (sin jQuery)
- **Bootstrap / Font Awesome** – Se puede agregar vía CDN si se requieren componentes extra
- **API REST** – Comunicación con Spring Boot en `/api/...`

---

## Convenciones de JS

Todos los módulos de JS siguen el patrón:
```js
const MiModulo = {
  init() { /* inicialización */ },
  // métodos...
};
```

La inicialización automática se hace detectando `document.body.id`:
```js
document.addEventListener('DOMContentLoaded', () => {
  const pagina = document.body.id;
  switch (pagina) {
    case 'pagina-login-estudiante': LoginEstudiante.init(); break;
    // ...
  }
});
```

---

## Endpoints API esperados

| Método | Endpoint | Descripción |
|---|---|---|
| `GET`  | `/api/auth/me` | Identidad de la sesión y token CSRF |
| `POST` | `/api/auth/login` | Autenticación de alumno |
| `POST` | `/api/auth/login/aspirante` | Autenticación de aspirante con ficha y CURP |
| `POST` | `/api/auth/logout` | Cierre de sesión |
| `GET`  | `/api/solicitudes/mi-solicitud` | Solicitud activa del usuario |
| `GET`  | `/api/formularios/activo` | Preguntas del formulario vigente |
| `POST` | `/api/solicitudes` | Crear nueva solicitud con sus respuestas |
| `PUT`  | `/api/solicitudes/{id}` | Reenviar respuestas de una solicitud `RECHAZADO`, `RETIRADO` o `CANCELADO` |

Solo los endpoints de autenticación están implementados. Las operaciones de solicitud están planificadas en el frontend y pendientes en el backend; la base de datos ya las ofrece mediante `PKG_ESTUDIANTE` y `PKG_ASPIRANTE`.

---

## Notas para los equipos

- Los campos del formulario con clase `form-control-itt` reciben estilos automáticamente.
- Para mostrar errores de validación: usar `Validacion.marcarError(campo, 'mensaje')`.
- Para hacer peticiones HTTP: usar `Http.get(endpoint)` o `Http.post(endpoint, body)`.
- Para mostrar alertas: usar `Alerta.mostrar('id-contenedor', 'tipo', 'mensaje')`.
- El spinner se muestra/oculta con `Spinner.show()` / `Spinner.hide()`.
