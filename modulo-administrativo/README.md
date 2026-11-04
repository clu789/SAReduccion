# Módulo Administrativo – SAReducción ITT

## Descripción

Interfaz web exclusiva para el personal de la **Subdirección Académica** del Instituto Tecnológico de Toluca. Permite revisar, aprobar o devolver a corrección las solicitudes de reducción de monto de inscripción.

Se comunica con el backend (Spring Boot) mediante peticiones `fetch()` a la API REST.

---

## Estructura del proyecto

```
modulo-administrativo/
├── login.html          → Acceso restringido para administradores
├── panel-admin.html    → Bandeja de solicitudes + modal de detalle
├── css/
│   ├── itt-base.css    → Variables, componentes y utilidades base ITT
│   └── administrativo.css → Estilos específicos del módulo
├── js/
│   └── app.js          → Lógica del módulo (login, bandeja, decisiones)
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
| `GET`  | `/api/convocatorias` | Convocatorias disponibles para los filtros |
| `GET`  | `/api/deducciones` | Listar deducciones con filtro opcional `estatus` |
| `PUT`  | `/api/solicitudes/{id}/aprobar` | Aprobar la solicitud y autorizar su deducción; el comentario es opcional |
| `PUT`  | `/api/solicitudes/{id}/rechazar` | Rechazar la solicitud; exige comentario |
| `PUT`  | `/api/solicitudes/{id}/correccion` | Devolver a corrección; hoy comparte la transición del rechazo |
| `PUT`  | `/api/deducciones/{id}/cancelar` | Cancelar una deducción autorizada; el comentario es opcional |
| `POST` | `/api/notificaciones` | Envío manual de notificación (**aún no implementado**, equipo 6) |

---

## Notas para los equipos

- El **modal de detalle** se abre con `Modal.abrir('modal-detalle')` y se cierra con `Modal.cerrar('modal-detalle')`.
- Las estadísticas se cargan automáticamente en `PanelAdmin.cargarEstadisticas()`.
- El **Equipo 6** (notificaciones) debe implementar el servicio y el endpoint `/api/notificaciones`; el frontend solo provee `Notificaciones.enviarManual()` para casos especiales.
- Los filtros por chip llaman a `PanelAdmin.cargarSolicitudes(estatus)` con el estatus seleccionado.
- El comentario es **obligatorio solo al rechazar**; es **opcional** al aprobar, reautorizar y cancelar la deducción. El monto base siempre es obligatorio en aprobar y reautorizar.
- El detalle devuelve `historial` vacío: falta el procedimiento de consulta en la base de datos.
