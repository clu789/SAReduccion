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
| `GET`  | `/api/solicitante/perfil` | Perfil del estudiante o aspirante en sesión (Equipo 3) |
| `GET`  | `/api/solicitante/convocatorias` | Convocatorias en periodo de recepción (Equipo 3) |
| `GET`  | `/api/solicitante/solicitudes` | Solicitudes propias con historial y deducción (Equipo 3) |
| `GET`  | `/api/solicitante/solicitudes/{id}` | Detalle de una solicitud propia (Equipo 3) |
| `PUT`  | `/api/solicitante/solicitudes/{id}/retirar` | Retira una solicitud `PENDIENTE` (Equipo 3) |
| `PUT`  | `/api/solicitante/solicitudes/{id}/reenviar` | Reenvía una solicitud `RECHAZADO`, `RETIRADO` o `CANCELADO` (Equipo 3, lo invoca el formulario del Equipo 5) |
| `GET`  | `/api/formularios/activo` | Preguntas del formulario vigente (Equipo 5) |
| `POST` | `/api/solicitudes` | Crear nueva solicitud con sus respuestas (Equipo 5) |

Los endpoints de autenticación y los de `/api/solicitante` están implementados. La creación de la solicitud y la consulta del formulario corresponden al Equipo 5.

---

## API del panel del solicitante (Equipo 3)

Implementación: `SolicitanteController` → `SolicitanteService` → `SolicitanteRepository` → `PKG_ESTUDIANTE` / `PKG_ASPIRANTE`.

### Reglas comunes

- Requieren sesión de rol `STUDENT` o `APPLICANT`. La identidad (número de control, o ficha + periodo + año) se toma **siempre de la sesión**; el cliente nunca la envía.
- Las solicitudes de otra persona responden `404`, igual que una inexistente, para no revelar su existencia.
- Las operaciones `PUT` exigen el encabezado `X-CSRF-TOKEN` con el valor devuelto por `GET /api/auth/me`.
- Las fechas se devuelven en formato ISO-8601 (`2026-11-09T10:15:00`).
- Las respuestas nunca incluyen CURP, correo, contraseñas ni la identidad del administrador que comentó.
- Los logs técnicos registran operación, tipo de solicitante, identificador enmascarado (`***181`), folio y resultado.

### Errores

| Código | `codigo` | Cuándo |
|---|---|---|
| `401` | `SESION_NO_VALIDA` | No hay sesión, expiró o el rol no es estudiante/aspirante |
| `403` | `CSRF_INVALIDO` | Falta o no coincide `X-CSRF-TOKEN` |
| `404` | `NO_ENCONTRADA` | La solicitud no existe o no pertenece al usuario |
| `409` | `REGLA_NEGOCIO` | Estatus no permitido, periodo de recepción cerrado, respuestas incompletas |
| `500` | — | Error técnico; mensaje genérico sin detalle de Oracle |

Formato: `{ "error": "Mensaje para el usuario", "codigo": "REGLA_NEGOCIO" }`.

### `GET /api/solicitante/perfil`

```json
{
  "tipo": "ESTUDIANTE",
  "identificador": "21281181",
  "nombre": "Ana",
  "primerApellido": "López",
  "segundoApellido": "Pérez",
  "nombreCompleto": "Ana López Pérez",
  "carrera": "Ingeniería en Sistemas Computacionales",
  "semestre": 5,
  "periodo": null,
  "periodoDescripcion": null,
  "anio": null
}
```

Para aspirantes `tipo` es `ASPIRANTE`, `identificador` es la ficha, `carrera` es `null` y se llenan `periodo`, `periodoDescripcion` y `anio`.

### `GET /api/solicitante/convocatorias`

Convocatorias cuyo periodo de recepción incluye hoy. Para aspirantes solo las de su periodo y año.

```json
[{
  "idConvocatoria": 900002,
  "periodo": 2, "periodoDescripcion": "Agosto - Diciembre", "anio": 2026,
  "fechaInicio": "2026-11-02", "fechaFin": "2026-11-23",
  "idTipoSolicitud": 1, "tipoSolicitud": "Reducción por promedio", "tipoDescripcion": "…",
  "idFormulario": 1, "formulario": "Formulario general",
  "montoDeduccion": 1500.00,
  "idSolicitudExistente": null,
  "puedeIniciar": true
}]
```

`puedeIniciar` es `false` si ya existe una solicitud para esa convocatoria (`idSolicitudExistente`). El botón «Iniciar solicitud» abre `formulario.html?convocatoria=…&tipo=…&formulario=…`.

### `GET /api/solicitante/solicitudes` y `GET /api/solicitante/solicitudes/{id}`

```json
{
  "idSolicitud": 10, "idConvocatoria": 900002,
  "periodo": 2, "periodoDescripcion": "Agosto - Diciembre", "anio": 2026,
  "idTipoSolicitud": 1, "tipoSolicitud": "Reducción por promedio",
  "idFormulario": 1,
  "idEstatus": 3, "estatus": "RECHAZADO",
  "fechaSolicitud": "…", "fechaActualizacion": "…",
  "fechaInicioRecepcion": "…", "fechaFinRecepcion": "…",
  "recepcionAbierta": true,
  "puedeRetirar": false,
  "puedeReenviar": true,
  "historial": [
    { "idEstatus": 3, "estatus": "RECHAZADO", "comentario": "Falta comprobante",
      "administrativo": true, "fecha": "…" }
  ],
  "deduccion": { "idDeduccion": 5, "idEstatus": 2, "estatus": "AUTORIZADO",
                 "montoPago": 3000.00, "montoDeduccion": 1500.00, "montoFinal": 1500.00 }
}
```

La lista devuelve un arreglo de estos objetos. `deduccion` es `null` si no existe.

- `puedeRetirar`: estatus `PENDIENTE` **y** recepción abierta.
- `puedeReenviar`: estatus `RECHAZADO`, `RETIRADO` o `CANCELADO` **y** recepción abierta.
- Una solicitud `AUTORIZADO` nunca ofrece retiro ni cancelación al solicitante.

### `PUT /api/solicitante/solicitudes/{id}/retirar`

Sin cuerpo. Responde `200` con `{ "mensaje": "Solicitud retirada correctamente." }`; el panel recarga la lista. La base de datos vuelve a validar estatus y periodo (`ORA-20859`, `ORA-20860`).

### `PUT /api/solicitante/solicitudes/{id}/reenviar`

```json
{
  "idTipoSolicitud": 1,
  "idFormulario": 1,
  "respuestas": [
    { "idPregunta": 3, "idOpcion": 7, "textoRespuesta": null },
    { "idPregunta": 4, "idOpcion": null, "textoRespuesta": "Texto libre" }
  ]
}
```

El tipo y formulario deben pertenecer a la convocatoria de la solicitud. Las respuestas sin texto ni opción (preguntas opcionales sin contestar) se omiten; Oracle valida que estén todas las obligatorias. Las respuestas reemplazan a las anteriores y la solicitud vuelve a `PENDIENTE`. Responde `200` con `{ "mensaje": "Solicitud reenviada correctamente." }`.

**Coordinación con Equipo 5:** el panel enlaza a `formulario.html?reenviar={id}&convocatoria=…&tipo=…&formulario=…`. Cuando el formulario reciba `reenviar`, debe enviar a este endpoint (con `Http.put`, que ya agrega el token CSRF) en lugar de `POST /api/solicitudes`.

### Procedimientos PL/SQL utilizados

| Endpoint | Estudiante (`PKG_ESTUDIANTE`) | Aspirante (`PKG_ASPIRANTE`) |
|---|---|---|
| perfil | `mis_datos` | `mis_datos` (sin CURP ni correo) |
| convocatorias | `convocatorias_disponibles` | `convocatorias_disponibles` |
| solicitudes | `mis_solicitudes`, `mi_historial`, `mis_deducciones` | ídem |
| retirar | `retirar_solicitud` | `retirar_solicitud` |
| reenviar | `reenviar_solicitud` | `reenviar_solicitud` |

### Sesión en el navegador

- `Http` envía `X-CSRF-TOKEN` en `POST`/`PUT` y, ante un `401` fuera de `/auth/`, muestra el modal de sesión expirada y redirige a `login.html?sesion=expirada`.
- `SesionInactividad` avisa 5 minutos antes de los 30 minutos de inactividad (el mismo tiempo que la sesión del servidor). «Continuar» renueva la sesión con `GET /api/auth/me`.

---

## Notas para los equipos

- Los campos del formulario con clase `form-control-itt` reciben estilos automáticamente.
- Para mostrar errores de validación: usar `Validacion.marcarError(campo, 'mensaje')`.
- Para hacer peticiones HTTP: usar `Http.get(endpoint)` o `Http.post(endpoint, body)`.
- Para mostrar alertas: usar `Alerta.mostrar('id-contenedor', 'tipo', 'mensaje')`.
- El spinner se muestra/oculta con `Spinner.show()` / `Spinner.hide()`.
