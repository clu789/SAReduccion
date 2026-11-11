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
| `POST` | `/api/notificaciones` | Envío manual de un aviso al aspirante |

---

## Notas para los equipos

- El **modal de detalle** se abre con `Modal.abrir('modal-detalle')` y se cierra con `Modal.cerrar('modal-detalle')`.
- Las estadísticas se cargan automáticamente en `PanelAdmin.cargarEstadisticas()`.
- El detalle de la solicitud incluye un formulario de aviso por correo. Requiere una sesión de administrador y el token CSRF. La API recibe `idSolicitud` y `mensaje` y devuelve `204` cuando SMTP acepta el aviso.
- Para probarlo, configura `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT` y `SAR_MAIL_FROM` en el `.env` local. Si el servidor de prueba lo requiere, agrega `SMTP_AUTH=true`, `SMTP_STARTTLS_ENABLE=true`, `SPRING_MAIL_USERNAME` y `SPRING_MAIL_PASSWORD`. Usa un servidor SMTP que capture mensajes y una solicitud ficticia de aspirante.
- Si la base ya fue inicializada, actualiza el paquete `PKG_ADMINISTRADOR` con el script `docker/init-db/05-crear-paquetes-permisos.sql` siguiendo el proceso de integración de Oracle. Docker solo ejecuta los scripts de inicialización sobre una base nueva.
- La consulta usa el correo de `Aspirantes`. Una solicitud sin correo disponible devuelve `422`. Una falla de SMTP devuelve `503`.
- Los filtros por chip llaman a `PanelAdmin.cargarSolicitudes(estatus)` con el estatus seleccionado.
- El comentario es **obligatorio solo al rechazar**; es **opcional** al aprobar, reautorizar y cancelar la deducción. El monto base siempre es obligatorio en aprobar y reautorizar.
- El detalle devuelve `historial` vacío: falta el procedimiento de consulta en la base de datos.

El backend también envía avisos automáticos al aprobar (incluida la reautorización), rechazar o cancelar una deducción. El cambio de estado se confirma en Oracle antes de consultar el destinatario y enviar el correo. Las incidencias de correo se registran sin revertir la decisión administrativa.
