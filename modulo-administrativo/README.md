# Módulo Administrativo – SAReducción ITT

## Descripción

Interfaz web exclusiva para el personal de la **Subdirección Académica** del Instituto Tecnológico de Toluca. Permite revisar, aprobar, rechazar, cancelar o aplicar deducciones sobre las solicitudes recibidas y administrar convocatorias y montos oficiales.

Se comunica con el backend (Spring Boot) mediante peticiones `fetch()` a la API REST usando [`ITTSecurity`](../SEGURIDAD.md).

---

## Estructura del módulo

```text
modulo-administrativo/
├── login.html                     → Acceso restringido para administradores
├── panel-admin.html               → Bandeja de solicitudes, detalle, respuestas y deducciones
├── convocatorias-admin.html       → Listado, detalle y edición rápida de convocatorias
├── convocatorias-crear-editar.html→ Creación y edición de convocatorias y montos
├── historial-convocatorias.html   → Consulta del historial de cambios de fechas
├── css/
│   ├── itt-base.css               → Variables, componentes y utilidades base ITT
│   ├── administrativo.css         → Estilos del login y panel administrativo
│   ├── convocatorias.css          → Estilos de gestión de convocatorias
│   └── historial-convocatorias.css→ Estilos del historial de convocatorias
├── js/
│   ├── app.js                     → Lógica de login, bandeja, detalle y decisiones
│   ├── convocatorias-admin.js     → Listado y edición de convocatorias
│   ├── convocatorias-crear-editar.js → Alta y validación de convocatorias y montos
│   └── historial-convocatorias.js → Consulta y filtros de historial de convocatorias
└── img/
    ├── logo.png
    ├── header.png
    └── footer.png
```

---

## Páginas y equipos responsables

| Página | Funcionalidad | Equipo |
|---|---|---|
| `login.html` | Autenticación exclusiva de administradores | **Equipo 1** |
| `panel-admin.html` | Bandeja, detalle y resolución de solicitudes y deducciones | **Equipo 2** |
| `convocatorias-admin.html`, `convocatorias-crear-editar.html`, `historial-convocatorias.html` | Gestión, edición de montos/fechas e historial de convocatorias | **Equipo 4** |
| Notificaciones (backend) | Envío de avisos al cambiar estatus | **Equipo 6** |

---

## Tecnologías

- **HTML5** – Estructura semántica
- **CSS3 / Vanilla CSS** – Estilos con variables CSS
- **JavaScript (ES6+)** – Lógica del navegador con `fetch()` (sin jQuery)
- **API REST** – Comunicación con Spring Boot en `/api/...`

---

## Convenciones de JS

Todos los módulos de JS siguen el patrón de objeto literal:
```js
const MiModulo = {
  init() { /* inicialización */ },
  async cargarDatos() { /* fetch */ },
};
```

Inicialización automática:
```js
document.addEventListener('DOMContentLoaded', () => {
  const pagina = document.body.id;
  switch (pagina) {
    case 'pagina-login-admin': LoginAdmin.init(); break;
    case 'pagina-panel-admin': PanelAdmin.init(); break;
  }
});
```

---

## Endpoints API esperados

| Método | Endpoint | Descripción |
|---|---|---|
| `POST` | `/api/auth/login` | Autenticación de administrador |
| `POST` | `/api/auth/logout` | Cierre de sesión |
| `GET`  | `/api/solicitudes` | Listar solicitudes; filtros opcionales `estatus`, `convocatoria` y `solicitante` |
| `GET`  | `/api/solicitudes/estadisticas` | Contadores por estatus |
| `GET`  | `/api/solicitudes/{id}` | Detalle + respuestas + deducción |
| `GET`  | `/api/deducciones` | Listar deducciones con filtro opcional `estatus` |
| `PUT`  | `/api/solicitudes/{id}/aprobar` | Aprobar la solicitud y autorizar su deducción; el comentario es opcional |
| `PUT`  | `/api/solicitudes/{id}/rechazar` | Rechazar la solicitud (o devolverla para corrección); exige comentario |
| `PUT`  | `/api/deducciones/{id}/aplicar` | Aplicar definitivamente una deducción autorizada |
| `PUT`  | `/api/deducciones/{id}/cancelar` | Cancelar una deducción autorizada; el comentario es opcional |
| `GET`  | `/api/admin/convocatorias` | Listar convocatorias registradas con su estado calculado |
| `GET`  | `/api/admin/convocatorias/{id}` | Detalle de convocatoria, montos vigentes y banderas de edición |
| `POST` | `/api/admin/convocatorias` | Crear nueva convocatoria con montos iniciales |
| `PUT`  | `/api/admin/convocatorias/{id}` | Actualizar fechas y montos de una convocatoria |
| `GET`  | `/api/admin/convocatorias/{id}/historial` | Historial de cambios de fechas de la convocatoria |
| `POST` | `/api/notificaciones` | Envío manual de un aviso al solicitante (`ADMIN` + CSRF) |

---

## Notas técnicas y notificaciones

- **Seguridad y sesión:** todas las llamadas pasan por `ITTSecurity` (token CSRF automático y manejo de `401`/`403`) y cumplen la política CSP estricta sin scripts ni estilos en línea (ver [`../SEGURIDAD.md`](../SEGURIDAD.md)).
- **Modal de detalle:** se abre con `Modal.abrir('modal-detalle')` y se cierra con `Modal.cerrar('modal-detalle')`.
- **Estadísticas y filtros:** las tarjetas se cargan en `PanelAdmin.cargarEstadisticas()` y los chips filtran mediante `PanelAdmin.cargarSolicitudes(estatus)`.
- **Decisiones y montos:** el comentario es **obligatorio solo al rechazar** y opcional al aprobar, reautorizar o cancelar. El monto base se elige automáticamente por perfil (inscripción para aspirantes, reinscripción para estudiantes; el de ID más alto si hay varios).
- **Respuestas del formulario:** el modal de respuestas muestra todas las preguntas en orden con su opción seleccionada, especificación mixta u observación (`Sin respuesta` si es opcional y no tiene contenido).
- **Notificaciones por correo:** al elegir una acción se muestra la vista previa de la plantilla (`src/main/resources/mail/aviso-tramite.html`) antes de confirmar, y también existe envío manual mediante `POST /api/notificaciones`. Los avisos automáticos son asíncronos y un fallo SMTP no revierte la decisión en Oracle. Para configuración SMTP/Mailpit, cobertura y pruebas (`NotificacionServiceTest`, `NotificacionControllerTest`), consulta [`../NOTIFICACIONES.md`](../NOTIFICACIONES.md) y [`../PRUEBAS.md`](../PRUEBAS.md).

## Decisiones y plantillas de correo del equipo 6

1. Abre el detalle de una solicitud y elige la acción administrativa.
2. Revisa la plantilla que el panel selecciona automáticamente.
3. Activa **Agregar comentario** si necesitas una observación. Al rechazar,
   el comentario es obligatorio.
4. Pulsa **Confirmar y enviar** para ejecutar la decisión o **Volver** para
   regresar a las acciones.

El aviso utiliza el correo del aspirante asociado a la solicitud. El éxito de
la decisión confirma el cambio en Oracle. El diagnóstico de envío se consulta
en los registros SMTP y la recepción se verifica en el buzón.

La plantilla HTML está en
[src/main/resources/mail/aviso-tramite.html](../src/main/resources/mail/aviso-tramite.html).
El comentario opcional de Aplicado se envía en el correo. Su guardado en el
historial Oracle requiere ampliar el procedimiento correspondiente.

Para configurar el contenedor, integrar Oracle, comprobar los envíos y ejecutar
las pruebas, consulta la [guía de notificaciones del equipo 6](../NOTIFICACIONES.md).
