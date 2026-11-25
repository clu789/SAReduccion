# Seguridad e integración (SAReducción)

Mecanismos comunes de autenticación, autorización, CSRF, auditoría y logs de SAReducción. Todo vive en el paquete `com.itt.sareduccion.security` y en `modulo-compartido/`.

**Resumen para quien tiene prisa**

| Si necesitas… | Haz esto |
| --- | --- |
| Proteger un endpoint por rol | Publícalo bajo un prefijo reglado (`/api/admin/**`, `/api/estudiante/**`, `/api/aspirante/**`, `/api/solicitante/**`) o anota el controller con `@RequiereRol`. |
| Saber quién hace la petición | `SessionAttributes.principal(request.getSession(false))` |
| Llamar a la API desde JavaScript | `ITTSecurity.json('/api/...', { method: 'POST', body: {...} })` |
| Escribir en el log | `LOG.info("Solicitud {} aprobada", id)` con SLF4J; la correlación y el enmascarado son automáticos. |

---

## 1. Qué hace el backend en cada petición a `/api/**`

`AuthorizationInterceptor` se ejecuta antes que cualquier controller y aplica, en este orden:

1. **Sesión.** Si la ruta no es pública y no hay sesión autenticada responde `401`.
2. **CSRF.** En `POST`, `PUT`, `PATCH` y `DELETE` exige el token de la sesión en la cabecera `X-CSRF-TOKEN`; si falta o no coincide responde `403`.
3. **Rol.** Si la ruta exige un rol que la sesión no tiene responde `403` y lo registra en la auditoría.

Las únicas rutas públicas son las de autenticación (`/api/auth/...`). **Cualquier otra ruta bajo `/api/` exige sesión aunque no tenga regla de rol.**

Lo que el interceptor **no** hace: validar que un dato pertenezca a quien lo pide (por ejemplo, que la solicitud 25 sea del alumno en sesión). Esa regla sigue en el servicio de cada equipo, usando la identidad de la sesión y nunca un identificador enviado por el cliente.

### Respuesta uniforme de error

```json
{
  "error": "SESSION_EXPIRED",
  "message": "Su sesión expiró. Inicie sesión de nuevo.",
  "redirect": "/solicitante/login.html",
  "correlationId": "5b0c6c1e-..."
}
```

| HTTP | `error` | Significado |
| --- | --- | --- |
| 401 | `SESSION_EXPIRED` | La cookie de sesión ya no es válida (inactividad). |
| 401 | `NOT_AUTHENTICATED` | No se ha iniciado sesión. |
| 401 | `INVALID_CREDENTIALS` | Usuario, contraseña, ficha, CURP o perfil no válidos. |
| 401 | `OTP_INVALID` | Código temporal incorrecto o vencido (`restart: true` si hay que volver a iniciar). |
| 400 | `CAPTCHA_INVALID` | Captcha incorrecto, vencido o ya usado. |
| 403 | `CSRF_INVALID` | Falta el token CSRF o no corresponde a la sesión. |
| 403 | `ACCESS_DENIED` | La sesión no tiene el rol requerido. `redirect` apunta al panel de su rol. |
| 429 | `TOO_MANY_ATTEMPTS` | Bloqueo temporal; cabecera `Retry-After` y campo `retryAfterSeconds`. |
| 503 | `AUTH_UNAVAILABLE` | Oracle o el correo no están disponibles. |
| 500 | `INTERNAL_ERROR` | Error no controlado; el detalle queda en el log con el mismo `correlationId`. |

`error` es un código estable para el frontend y `message` el texto para la persona usuaria. Construye tus propios errores con `SecurityResponses.body(codigo, mensaje)` para mantener el formato.

---

## 2. Proteger endpoints nuevos

### Opción A: usar un prefijo ya reglado (recomendada)

| Prefijo | Roles permitidos |
| --- | --- |
| `/api/admin/**` | `ADMIN` |
| `/api/estudiante/**` | `STUDENT` |
| `/api/aspirante/**` | `APPLICANT` |
| `/api/solicitante/**` | `STUDENT`, `APPLICANT` |
| `/api/solicitudes/**`, `/api/convocatorias/**`, `/api/montos/**`, `/api/deducciones/**`, `/api/notificaciones/**` | `ADMIN` (endpoints administrativos existentes) |

```java
@RestController
@RequestMapping("/api/admin/reportes")   // ya queda restringido a ADMIN
public class ReporteController {

    @GetMapping
    public List<Reporte> listar(HttpServletRequest request) {
        SessionPrincipal principal = SessionAttributes.principal(request.getSession(false));
        return servicio.listar(principal.getIdentity());   // identidad de la sesión, no del cliente
    }
}
```

### Opción B: anotar el controller o el método

```java
@RestController
@RequestMapping("/api/notificaciones")
@RequiereRol(AuthRole.ADMIN)                       // toda la clase
public class NotificacionController {

    @GetMapping("/mias")
    @RequiereRol({AuthRole.STUDENT, AuthRole.APPLICANT})   // el método tiene prioridad
    public List<Aviso> mias(HttpServletRequest request) { ... }
}
```

### Opción C: regla por configuración

En `application.properties`, sin tocar código. Tiene prioridad sobre las reglas de la opción A:

```properties
sar.security.role-rules=/api/reportes/**=ADMIN;/api/formularios/**=ADMIN,STUDENT,APPLICANT
```

Prioridad: anotación del método, anotación de la clase, `sar.security.role-rules`, reglas de prefijo. Sin ninguna, basta con estar autenticado.

Ya no hace falta comprobar el rol ni el CSRF dentro de cada método; los controllers existentes que lo hacen siguen funcionando igual.

### Datos de la sesión

`SessionPrincipal` expone `getRole()` (`STUDENT`, `ADMIN`, `APPLICANT`), `getIdentity()` (número de control, id de administrador o ficha), `getUsername()` y, para aspirantes, `getPeriodo()` y `getAnio()`.

---

## 3. Consumir la API desde JavaScript

Incluye el cliente común antes del script de tu módulo:

```html
<link rel="stylesheet" href="/compartido/css/itt-security.css" />
<script src="/compartido/js/itt-security.js"></script>
```

```js
// GET
const solicitudes = await ITTSecurity.json('/api/solicitante/solicitudes');

// POST, PUT, PATCH, DELETE: el token CSRF se agrega solo
await ITTSecurity.json('/api/solicitante/solicitudes/25/retirar', { method: 'PUT', body: { motivo } });

// Errores de negocio: Error con .status, .code y .payload
try {
  await ITTSecurity.json('/api/admin/solicitudes/25/aprobar', { method: 'PUT', body: datos });
} catch (error) {
  if (error.status === 409) mostrar(error.message);
}
```

`ITTSecurity.fetch(url, opciones)` hace lo mismo pero devuelve el `Response` (útil para descargar archivos o enviar `FormData`).

Lo que resuelve por ti:

- **CSRF:** obtiene el token de `/api/auth/me`, lo envía en `X-CSRF-TOKEN` y, si el servidor responde `CSRF_INVALID`, lo renueva y reintenta una vez.
- **Sesión expirada (401):** muestra un aviso accesible y redirige al inicio de sesión que indica `redirect`.
- **Acceso denegado (403):** muestra el aviso con el mensaje del servidor.
- Pasa `{ sinAviso: true }` si prefieres manejar tú el 401/403.

Si tu módulo mantiene su propio envoltorio de `fetch`, lo mínimo es enviar la cabecera `X-CSRF-TOKEN` con el `csrfToken` de `GET /api/auth/me` en toda operación que modifique datos. En formularios HTML clásicos también se acepta el campo oculto `_csrf`.

### Proteger una pantalla

```js
document.addEventListener('DOMContentLoaded', async () => {
  const sesion = await ITTSecurity.protegerPagina(['STUDENT', 'APPLICANT']);
  if (!sesion) return;            // ya se redirigió
  iniciarPantalla(sesion);        // sesion.identity, sesion.role, sesion.home
});
```

Además activa el aviso de inactividad: un minuto antes de que la sesión expire muestra una advertencia y, al cumplirse el plazo, cierra la sesión.

### Navegación compartida

```html
<div data-itt-nav></div>
```

Dibuja una barra con el menú «Vistas» (las pantallas del rol en sesión), la identidad y «Cerrar sesión». Es navegable con teclado (`Tab`, `Enter`, `Esc`). Para agregar tus pantallas al menú:

```js
ITTSecurity.Navegacion.registrar('ADMIN', { titulo: 'Convocatorias', href: '/administrativo/convocatorias-admin.html' });
```

La barra compartida y la protección de sesión ya están integradas en las pantallas protegidas de ambos módulos (`panel-estatus.html`, `panel-aspirante.html`, `formulario.html`, `panel-admin.html`, `convocatorias-admin.html`, `convocatorias-crear-editar.html` y `historial-convocatorias.html`). Para `STUDENT` y `APPLICANT` incluye su panel y `/solicitante/formulario.html`; para `ADMIN` incluye las cuatro vistas administrativas. Los envoltorios HTTP de ambos módulos usan `ITTSecurity` para centralizar CSRF, reintento del token y avisos de autorización.

### Captcha

```html
<div id="mi-captcha" data-itt-captcha></div>
```

```js
const captcha = ITTSecurity.captchaDe('#mi-captcha');
enviar({ ...datos, captcha: captcha.valor() });
captcha.recargar();   // tras cada intento: el desafío es de un solo uso
```

Ofrece una imagen y, con «Usar pregunta accesible», una operación redactada en texto que un lector de pantalla puede leer.

---

## 4. Autenticación

| Endpoint | Uso |
| --- | --- |
| `GET /api/auth/me` | Estado de la sesión y token CSRF. Sin sesión responde `401` con `csrfToken`. |
| `GET /api/auth/captcha?tipo=imagen\|accesible` | Desafío nuevo. |
| `POST /api/auth/login` | Estudiante o administrador: `{username, password, captcha, perfil}` con `perfil` = `ESTUDIANTE` o `ADMINISTRADOR`. |
| `POST /api/auth/login/aspirante` | `{ficha, curp, captcha}`. Con OTP activo responde `202 {otpRequired: true, destino}`. |
| `POST /api/auth/login/aspirante/otp` | `{codigo}`. Completa el acceso del aspirante. |
| `POST /api/auth/login/aspirante/otp/reenviar` | Envía un código nuevo (una vez por minuto). |
| `POST /api/auth/logout` | Invalida la sesión y expira la cookie. |

Orden de controles en cada inicio de sesión: CSRF, bloqueo temporal, captcha y, solo entonces, credenciales. Al autenticar se cambia el identificador de sesión (protección contra fijación de sesión).

**Sesión.** `HttpSession` con cookie `HttpOnly`, `SameSite=Lax` y `Secure` cuando `SESSION_COOKIE_SECURE=true`. Expira por inactividad (`SAR_SESSION_TIMEOUT`, 30 minutos por omisión). No hay JWT ni otro sistema de tokens.

**Intentos repetidos.** 5 fallos por usuario (perfil + identificador) o 20 por IP en 15 minutos producen un bloqueo temporal que se libera solo. Se configura con `sar.auth.max-attempts`, `sar.auth.max-attempts-per-origin` y `sar.auth.attempt-window-seconds`. El contador vive en memoria: se reinicia con el backend y no se comparte entre réplicas.

### Código temporal de aspirantes (OTP)

Está implementado y desactivado por omisión (`SAR_OTP_ENABLED=false`). Cuando se habilita, `com.itt.sareduccion.security.CorreoOtpDelivery` implementa `OtpDelivery` usando `JavaMailSender` siempre que `SPRING_MAIL_HOST` y `SAR_MAIL_FROM` estén configurados (si falla el envío, el acceso responde `503`). Para configurar el servidor SMTP o Mailpit en desarrollo, consulta [`NOTIFICACIONES.md`](NOTIFICACIONES.md).

Para probarlo sin servidor de correo, en `.env` define `SAR_OTP_ENABLED=true` y `SAR_OTP_LOG_CODE=true`; el código aparece en `docker compose logs backend` como `OTP-DEV`. No usar `SAR_OTP_LOG_CODE` fuera de desarrollo.

---

## 5. Auditoría en Oracle

`docker/init-db/07-auditoria-seguridad.sql` crea la tabla `SEG_AUDITORIA_ACCESOS` y el paquete `PKG_SEGURIDAD`. `registrar_evento` usa `PRAGMA AUTONOMOUS_TRANSACTION`: el evento se confirma aunque la transacción de negocio haga `ROLLBACK`, y nunca confirma la transacción de quien lo llama. La cuenta `SAR_BACKEND` solo tiene `EXECUTE` sobre el paquete; no puede leer ni modificar la bitácora.

Eventos: `LOGIN_EXITOSO`, `LOGIN_FALLIDO`, `LOGOUT`, `INTENTO_BLOQUEADO`, `SESION_EXPIRADA`, `CAPTCHA_FALLIDO`, `OTP_ENVIADO`, `OTP_FALLIDO`, `ACCESO_DENEGADO`.

El backend ya registra todos. Si tu módulo necesita auditar un evento de acceso:

```java
auditService.registrar(SecurityEvent.ACCESO_DENEGADO, principal.getIdentity(),
        principal.getRole().getPerfil(), request.getRemoteAddr(), "Solicitud ajena");
```

Nunca se guardan contraseñas, CURP, correos ni códigos.

**Instalación.** En una base nueva lo ejecuta `init-db.sh`. Sobre una base ya inicializada, sin borrar datos:

```bash
docker exec sareduccion-oracle bash /opt/sareduccion/init-db/aplicar-07-auditoria.sh
# con pruebas del paquete:
docker exec sareduccion-oracle bash /opt/sareduccion/init-db/aplicar-07-auditoria.sh --probar
```

Consulta (como `SA_REDUCCION`):

```sql
SELECT fecha_evento, evento, tipo_perfil, identificador_usuario, direccion_ip, detalle, correlation_id
  FROM SEG_AUDITORIA_ACCESOS ORDER BY fecha_evento DESC FETCH FIRST 20 ROWS ONLY;
```

Retención: `JOB_SEG_DEPURAR_AUDITORIA` (`DBMS_SCHEDULER`) ejecuta automáticamente `PRC_SEG_DEPURAR_AUDITORIA(365)` cada día a las 03:00 (borra lo anterior a 365 días; mínimo permitido 30). También puede invocarse manualmente como `SA_REDUCCION` con `EXEC PRC_SEG_DEPURAR_AUDITORIA(365)`.

---

## 6. Estándar de logs

**Formato.** Una línea por evento, en consola y en archivo:

```
2026-11-15T10:15:30.123-06:00 level=INFO  corr=5b0c6c1e-... ip=187.190.1.20 thread=http-nio-8080-exec-3 logger=c.i.s.service.SolicitudAdminService msg="Solicitud 25 aprobada"
```

**Cómo escribir.** Solo SLF4J, con parámetros (`{}`), nunca concatenando ni con `System.out`:

```java
private static final Logger LOG = LoggerFactory.getLogger(MiServicio.class);

LOG.info("Solicitud {} aprobada por {}", idSolicitud, idAdministrador);
LOG.warn("Convocatoria {} cerrada; solicitud rechazada", idConvocatoria);
LOG.error("No se pudo generar el reporte {}", idReporte, excepcion);   // la excepción va al final, sin {}
```

**Correlación.** `CorrelationIdFilter` asigna un identificador a cada petición, lo pone en el MDC (`corr=`) y lo devuelve en la cabecera `X-Correlation-ID` y en el campo `correlationId` de los errores. No hay que hacer nada para que aparezca en tus logs. Si lanzas trabajo en otro hilo, copia el contexto:

```java
Map<String, String> contexto = MDC.getCopyOfContextMap();
executor.submit(() -> {
    if (contexto != null) MDC.setContextMap(contexto);
    try { tarea(); } finally { MDC.clear(); }
});
```

**Niveles.**

| Nivel | Cuándo |
| --- | --- |
| `ERROR` | Fallo que impide completar la operación y requiere atención (excepción no esperada, Oracle caído). |
| `WARN` | Anomalía recuperable o intento rechazado (regla de negocio violada, correo no enviado, acceso denegado). |
| `INFO` | Hitos de negocio y de sesión (solicitud creada, aprobada, inicio de sesión). Una línea por hito. |
| `DEBUG` | Diagnóstico de desarrollo. Solo se escribe con el perfil `dev`. |

**Errores.** Captura solo lo que puedas resolver. Lo demás déjalo propagar: `GlobalExceptionHandler` lo registra una vez con su traza y responde `500 INTERNAL_ERROR` sin exponer detalles. No registres y relances la misma excepción (sale duplicada).

**Datos sensibles.** El conversor `%mmsg` enmascara automáticamente contraseñas, tokens, CSRF, códigos, CURP y correos (`password=****`, `MOPA**************`, `j***@dominio.mx`). Aun así, no los pases al log: el enmascarado es una red de seguridad. Para identificadores usa `SensitiveDataMasker.identifier(numeroControl)` (`******81`). Nunca registres cuerpos completos de peticiones ni respuestas.

**Rotación y retención.** `logs/sareduccion.log` rota a diario y al llegar a 10 MB, comprimido; se conservan 30 días con un tope de 500 MB. El directorio se cambia con `SAR_LOG_DIR`.

---

## 7. Proxy, IP real, cookies y cabeceras

- **IP del cliente.** Nginx sobrescribe `X-Forwarded-For` y `X-Real-IP` con la IP de la conexión, de modo que el cliente no puede falsearlas. El backend (`server.forward-headers-strategy=native`) solo acepta esas cabeceras cuando la conexión viene de un proxy de confianza (`SAR_TRUSTED_PROXIES`; por omisión la red interna de Docker). En tu código usa siempre `request.getRemoteAddr()`; no leas `X-Forwarded-For` directamente.
- **Puerto 8080.** En desarrollo el backend también está publicado en el host. En un despliegue real no debe exponerse: todo el tráfico entra por Nginx.
- **Cabeceras.** La API responde con `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Content-Security-Policy: default-src 'none'`, `Cache-Control: no-store` y, por HTTPS, `Strict-Transport-Security`. Las páginas reciben de Nginx `nosniff`, `X-Frame-Options`, `Referrer-Policy` y una CSP que solo permite recursos propios y los CDN de fuentes e iconos en uso.
- **CSP de las páginas.** `docker/nginx/nginx.conf` aplica CSP estricta sin `'unsafe-inline'` en `script-src` ni `style-src`; todo código y estilo vive en archivos `.js`/`.css` externos con `addEventListener`.
- **HTTPS.** Al desplegar con TLS: `SESSION_COOKIE_SECURE=true` y descomentar la línea de HSTS en `docker/nginx/nginx.conf`.

---

## 8. Pruebas

Las pruebas del Equipo 1 no necesitan Oracle:

```bash
docker run --rm -v "${PWD}:/app" -w /app maven:3.8.8-eclipse-temurin-8 mvn -B test "-Dtest=Security*Test"
```

| Clase | Cubre |
| --- | --- |
| `SecurityFlowTest` | CSRF en login y en operaciones de negocio, captcha (incorrecto, un solo uso, accesible), fijación de sesión, logout, sesión expirada, autorización por rol y por anotación, bloqueo por usuario y por IP, OTP de aspirantes, cabeceras y correlación. |
| `SecurityUnitTest` | Limitador de intentos, captcha, enmascarado de logs, reglas de rol y OTP. |

Las pruebas `*IntegrationTest` existentes siguen requiriendo Oracle levantado y sus variables de entorno.

---

## Documentación relacionada

- [Arquitectura técnica](ARQUITECTURA.md)
- [Base de datos](BASE_DE_DATOS.md)
- [Notificaciones por correo](NOTIFICACIONES.md)
- [Guía de pruebas](PRUEBAS.md)
