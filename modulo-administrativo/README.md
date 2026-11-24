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
| `PUT` | `/api/deducciones/{id}/aplicar` | Aplicar la deducción y enviar el aviso; acepta comentario opcional |
| `POST` | `/api/notificaciones` | Envío manual de un aviso al aspirante |

---

## Notas para los equipos

- El **modal de detalle** se abre con `Modal.abrir('modal-detalle')` y se cierra con `Modal.cerrar('modal-detalle')`.
- Las estadísticas se cargan automáticamente en `PanelAdmin.cargarEstadisticas()`.
- El detalle de la solicitud incluye un formulario de aviso por correo. Requiere una sesión de administrador y el token CSRF. La API recibe `idSolicitud` y `mensaje` y devuelve `204` cuando SMTP acepta el aviso.
- La [guía de notificaciones](../NOTIFICACIONES.md) contiene la configuración SMTP completa, los ejemplos para AWS y el entorno local de pruebas.
- En una base existente, integra `consultar_correo_solicitud` en la versión consolidada de `PKG_ADMINISTRADOR`, conservando los procedimientos de los otros equipos. La [guía de integración](../NOTIFICACIONES.md#oracle-e-integración-con-dev-y-main) detalla la dependencia de Oracle.
- La consulta usa el correo de `Aspirantes`. Una solicitud sin correo disponible devuelve `422`. Una falla de SMTP devuelve `503`.
- Los filtros por chip llaman a `PanelAdmin.cargarSolicitudes(estatus)` con el estatus seleccionado.
- El comentario es obligatorio al rechazar y opcional al aprobar, reautorizar, cancelar o aplicar. En esta rama, aprobar y reautorizar requieren seleccionar el monto base. La versión integrada de `dev` incorpora su selección automática.
- El detalle incluye los historiales de solicitud y deducción que devuelven los procedimientos Oracle.

El backend también envía avisos automáticos al aprobar (incluida la reautorización), rechazar, cancelar o aplicar una deducción. El cambio de estado se confirma en Oracle antes de consultar el destinatario y enviar el correo. Las incidencias de correo se registran sin revertir la decisión administrativa.

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
