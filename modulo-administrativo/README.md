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
| `login.html` | Autenticación exclusiva de administradores | **Equipo 4** |
| `panel-admin.html` | Bandeja + aprobación / corrección de solicitudes | **Equipo 2** |
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
| `GET`  | `/api/solicitudes` | Listar todas las solicitudes |
| `GET`  | `/api/solicitudes?estatus=PENDIENTE` | Filtrar por estatus |
| `GET`  | `/api/solicitudes/estadisticas` | Contadores por estatus |
| `GET`  | `/api/solicitudes/{id}` | Detalle + respuestas + historial |
| `PUT`  | `/api/solicitudes/{id}/aprobar` | Aprobar solicitud |
| `PUT`  | `/api/solicitudes/{id}/correccion` | Devolver a corrección |
| `POST` | `/api/notificaciones` | Envío manual de notificación |

---

## Notas para los equipos

- El **modal de detalle** se abre con `Modal.abrir('modal-detalle')` y se cierra con `Modal.cerrar('modal-detalle')`.
- Las estadísticas se cargan automáticamente en `PanelAdmin.cargarEstadisticas()`.
- El **Equipo 6** (notificaciones) puede conectar su lógica desde el backend (trigger); el frontend solo provee `Notificaciones.enviarManual()` para casos especiales.
- Los filtros por chip llaman a `PanelAdmin.cargarSolicitudes(estatus)` con el estatus seleccionado.
- El comentario de corrección es **obligatorio** antes de llamar a `PanelAdmin.solicitarCorreccion()`.
