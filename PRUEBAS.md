# Guía de pruebas — SAReducción

Checklist práctico de todas las pruebas automatizadas, manuales, de correo SMTP y de seguridad que puedes ejecutar sobre el estado actual del proyecto (rama `main`).

---

## 1. Preparación del entorno (Docker + Oracle XE)

### Levantar la pila completa (`oracle-db`, `backend`, `frontend`)
```powershell
docker compose --profile full-stack up -d
```

Si quieres probar la **inicialización limpia** de la base de datos (todos los scripts SQL, los 5 paquetes PL/SQL incluido `PKG_FORMULARIO`, permisos de `SAR_BACKEND` y datos de demostración):
> **Advertencia:** `down -v` borra el volumen `sareduccion_oracle_data` y reinicia los datos de prueba.

```powershell
docker compose --profile full-stack down -v
docker compose --profile full-stack up -d --build
```

### Verificar que todo arrancó sano
```powershell
docker ps --filter name=sareduccion
docker logs sareduccion-oracle | Select-String "OK:|Seed de demostracion"
docker logs --tail 30 sareduccion-backend
```
Debes ver `sareduccion-oracle` en estado `healthy`, los `OK:` de `init-db.sh` (`10` objetos de paquete `VALID`, `5` grants `EXECUTE` sobre paquetes autorizados para `SAR_BACKEND`) y `Started SareduccionApplication`.

### URLs locales y credenciales de demostración (`06-seed-desarrollo.sql`)
- **Portal Solicitante (Nginx):** `http://localhost:8081/solicitante/login.html`
- **Portal Administrativo (Nginx):** `http://localhost:8081/administrativo/login.html`
- **Backend directo:** `http://localhost:8080`
- **Oracle XE:** `localhost:1522/XEPDB1`

| Perfil | Usuario / Ficha | Credencial / CURP | Estado inicial en seed |
| --- | --- | --- | --- |
| **Administrador** | `SA01` | Variable `ORACLE_ADMIN_PASSWORD` en `.env` | Activo en `Administradores` |
| **Estudiante** | `ALU_21281181` | Variable `ORACLE_ALUMNO_PASSWORD` en `.env` | Solicitud `PENDIENTE` |
| **Aspirante (doble periodo)** | `F2026A001` | CURP `MOPA080512MDFRNN01` | Solicitud `PENDIENTE` (resuelve a periodo abierto `2/2026`) |
| **Aspirante (rechazada)** | `F2026B004` | CURP `FULD080617HDFRRG04` | Solicitud `RECHAZADO` (lista para probar reenvío) |
| **Aspirante (retirada)** | `F2026A003` | CURP `VASP080305MDFRTT03` | Solicitud `RETIRADO` (lista para probar reenvío) |
| **Aspirante (cancelada)** | `F2026B005` | CURP `MEGL080827MDFNLC05` | Solicitud `CANCELADO` |
| **Aspirante (autorizada)** | `F2026A002` | CURP `ROLE080921HDFJZM02` | Deducción `AUTORIZADO` |
| **Aspirante (aplicada)** | `F2026B006` | CURP `SERM080106HDFRYY06` | Deducción `APLICADO` |

---

## 2. Pruebas automatizadas rápidas (sin base de datos)

### A. Pruebas unitarias de Java (87 pruebas en 12 clases)
```powershell
mvn -o test "-Dtest=!*IntegrationTest"
```
*(Si `mvn` no está en el `PATH` global en Windows, puedes usar el binario incluido en NetBeans: `& "C:\Program Files\NetBeans-20\netbeans\java\maven\bin\mvn.cmd" -o test "-Dtest=!*IntegrationTest"`.)*
Validan:
- Seguridad (`SecurityUnitTest`, `SecurityFlowTest`): CSRF, captcha de imagen y texto accesible, limitador de intentos por usuario e IP, rotación de sesión, cabeceras de seguridad, `@RequiereRol` e interceptación de rutas (`/api/admin/**`, `/api/notificaciones/**`, `/api/solicitante/**`).
- Correo OTP y notificaciones (`CorreoOtpDeliveryTest`, `NotificacionServiceTest`, `NotificacionControllerTest`): activación condicional del bean SMTP, construcción HTML/texto plano segura ante XSS y propagación de `MDC` en envío asíncrono.
- Reglas de negocio y controladores (`SolicitudAdminServiceTest`, `ConvocatoriaGestionServiceTest`, `ConvocatoriaGestionControllerTest`, `SolicitanteServiceTest`, `ValidadorRespuestasTest`, `TraductorErroresOracleTest`, `RutasUnificadasControllerTest`): traducción de códigos ORA positivos/negativos (`20870`, `20915` -> `404`), cálculo de estado/`activa`, eliminación de rutas duplicadas (`404`) y respuestas de formulario.

### B. Sintaxis JS y pruebas JSDOM de ambos módulos (106 verificaciones)
Requieren **Node.js 16 o superior** y simulan las respuestas del backend sin necesitar Oracle, Docker ni navegador.
- [`modulo-solicitante/pruebas/formulario.test.js`](modulo-solicitante/pruebas/formulario.test.js) (64 verificaciones): preguntas `ABIERTA`, `OPCION_UNICA` y `MIXTA`, validaciones en vivo, envío con token CSRF, manejo de errores (`400`, `401`, `403`, `409`, `500`), solicitud existente, flujo de reenvío (precarga, cambio de tipo, estados no permitidos), aspirantes, aislamiento de roles y XSS.
- [`modulo-solicitante/pruebas/panel-solicitante.test.js`](modulo-solicitante/pruebas/panel-solicitante.test.js) (23 verificaciones): validación de login alumno/aspirante, flujo OTP de aspirante (`202` + reenvío `429` + confirmación), perfil sin CURP, convocatorias, retiro con CSRF, acciones por estado, XSS y aislamiento de roles.
- [`modulo-administrativo/pruebas/panel-admin.test.js`](modulo-administrativo/pruebas/panel-admin.test.js) (19 verificaciones): bandeja, filtros (incluido `Retiradas`), ordenamiento, modal de respuestas, vista previa de correo, rechazo obligatorio, aplicación con comentario opcional, aviso manual (`422`), `<dialog>` de convocatorias, alta con validaciones, historial comparativo y XSS.

```powershell
node --check modulo-compartido/js/itt-security.js modulo-administrativo/js/app.js modulo-administrativo/js/convocatorias-admin.js modulo-administrativo/js/convocatorias-crear-editar.js modulo-administrativo/js/historial-convocatorias.js modulo-administrativo/pruebas/panel-admin.test.js modulo-solicitante/js/app.js modulo-solicitante/js/formulario.js modulo-solicitante/pruebas/formulario.test.js modulo-solicitante/pruebas/panel-solicitante.test.js modulo-solicitante/pruebas/run-all.js modulo-solicitante/pruebas/rendimiento-e2e.js

cd modulo-solicitante/pruebas
npm test
```
*(Si es la primera vez en un equipo limpio, ejecuta antes `npm install` dentro de `modulo-solicitante/pruebas` para instalar `jsdom`.)*

---

## 3. Pruebas automatizadas de integración (contra Oracle XE vivo)

Con Docker levantado (`sareduccion-oracle` en estado `healthy`), ejecuta desde la raíz leyendo las credenciales de desarrollo desde `.env`:

```powershell
$vars = @{}
Get-Content .env | ForEach-Object {
  if ($_ -match '^([A-Z0-9_]+)=([^#\r\n]*)') { $vars[$matches[1]] = $matches[2].Trim() }
}
$env:DB_URL = "jdbc:oracle:thin:@//localhost:1522/$($vars['ORACLE_DATABASE'])"
$env:DB_USER = $vars['ORACLE_BACKEND_USER']
$env:DB_PASSWORD = $vars['ORACLE_BACKEND_PASSWORD']
$env:SAR_AUTH_STUDENT_PASSWORD = $vars['ORACLE_ALUMNO_PASSWORD']
$env:SAR_AUTH_ADMIN_PASSWORD = $vars['ORACLE_ADMIN_PASSWORD']
$env:SAR_JDBC_TEST_ADMIN_ID = "SA01"

mvn -o test "-Dtest=AuthenticationIntegrationTest,ApplicantAuthenticationIntegrationTest,SolicitudAdminServiceIntegrationTest"
```
*(Si `mvn` no está en el `PATH`, sustituye `mvn` por la ruta de tu instalación o la de NetBeans.)*
Deben pasar **15 de 15** pruebas (`AuthenticationIntegrationTest`, `ApplicantAuthenticationIntegrationTest` y `SolicitudAdminServiceIntegrationTest`).

> **Nota sobre otras pruebas en BD:**
> - `EstudianteRepositoryIntegrationTest` solo corre si defines `SAR_JDBC_TEST_NUMERO_CONTROL` con un estudiante específico de pruebas JDBC (`"ESTUDIANTE DE PRUEBA"`), distinto del seed.
> - **Suite PL/SQL de auditoría y reglas de negocio:** Con Docker levantado, puedes ejecutar como `SA_REDUCCION` sin dejar datos residuales (usa `ROLLBACK` final):
>   - [`docker/init-db/pruebas/07-auditoria-seguridad-test.sql`](docker/init-db/pruebas/07-auditoria-seguridad-test.sql) (8 comprobaciones de `PKG_SEGURIDAD` y `JOB_SEG_DEPURAR_AUDITORIA`).
>   - [`docker/init-db/pruebas/reglas-negocio-test.sql`](docker/init-db/pruebas/reglas-negocio-test.sql) (22 comprobaciones del ciclo completo de solicitudes, respuestas, retiro, reenvío, rechazo, autorización, cancelación, recálculo al aplicar, inmutabilidad de historiales, traslape de convocatorias y desambiguación de aspirantes).

### C. Pruebas E2E en vivo, cabeceras CSP y benchmark de rendimiento (`rendimiento-e2e.js`)
Con la pila completa levantada (`http://localhost:8081`), ejecuta desde la raíz (lee las credenciales de `.env`):
```powershell
node modulo-solicitante/pruebas/rendimiento-e2e.js
```
Valida cabeceras HTTP y CSP estricta sin `unsafe-inline` en Nginx, rechazos `401`/`403`/`404`, login real con captcha accesible para los tres perfiles y tandas concurrentes (25 peticiones, 5 simultáneas) sobre los endpoints principales.

---

## 4. Pruebas manuales funcionales en navegador (`http://localhost:8081`)

### A. Flujo de Estudiante (`ALU_21281181`)
1. Ir a `http://localhost:8081/solicitante/login.html` e iniciar sesión en la pestaña **Estudiante** con `ALU_21281181` y tu `ORACLE_ALUMNO_PASSWORD`.
2. **Captcha:** Probar el enlace de *pregunta accesible* en el widget de captcha y verificar que un captcha erróneo bloquea el login antes de validar contraseña.
3. **Panel (`panel-estatus.html`):** Verificar datos de perfil (`21281181`, carrera, semestre), tarjeta de la solicitud `PENDIENTE` e historial.
4. **Retiro y Reenvío:**
   - Pulsar **Retirar solicitud** y confirmar en el modal. Debe pasar a `RETIRADO` y habilitar **Corregir y reenviar**.
   - Pulsar **Corregir y reenviar**: abre `formulario.html?modo=reenvio&solicitud=...` con respuestas precargadas.
   - Probar validaciones en vivo (dejar obligatoria vacía, elegir opción `Otro (especifique)` en pregunta mixta para comprobar que aparece/desaparece el campo de especificación).
   - Confirmar y enviar; volver al panel y verificar que vuelve a `PENDIENTE` sin crear una solicitud nueva (conserva el mismo folio).
5. **Aislamiento de rol:** Intentar abrir `http://localhost:8081/administrativo/panel-admin.html` en la misma sesión; debe redirigir al panel de solicitante.

### B. Flujo de Aspirante (`F2026A001` y `F2026B004`)
1. En `login.html`, cambiar a la pestaña **Aspirante**:
   - Probar `F2026A001` + `MOPA080512MDFRNN01`: verificar que entra a `panel-aspirante.html` mostrando `Periodo 2 / 2026` (desambiguado frente a periodo `1`).
   - Probar una CURP inválida o un captcha incorrecto; después de 5 intentos fallidos seguidos con la misma ficha o cuenta debe responder bloqueo temporal (`429`).
2. Cerrar sesión e ingresar con `F2026B004` + `FULD080617HDFRRG04`:
   - Verificar que muestra la solicitud `RECHAZADO` y permite corregir y reenviar en `formulario.html`.

### C. Flujo de Administrador (`SA01`)
1. Ingresar en `http://localhost:8081/administrativo/login.html` con `SA01` y `ORACLE_ADMIN_PASSWORD`.
2. **Bandeja (`panel-admin.html`):**
   - Probar tarjetas de estadísticas y todos los chips de filtro (`Todas`, `Pendientes`, `Autorizadas`, `Rechazadas`, `Canceladas`, `Aplicadas`, **`Retiradas`**), el filtro por convocatoria, el buscador por folio/nombre y el ordenamiento de columnas.
   - Abrir detalle (`👁`) y respuestas (`📄`) de varias solicitudes.
   - En **Ver respuestas**, comprobar que aparezcan todas las preguntas del formulario en orden. Las respuestas de opción deben mostrar la opción elegida y las respuestas mixtas deben incluir su especificación; una observación opcional sin contenido debe aparecer como "Sin respuesta".
3. **Ciclo de decisión:**
   - En una solicitud `PENDIENTE` (ej. `F2026B007` o `21281189`): aprobar sin seleccionar un monto (comentario opcional). El backend deberá usar automáticamente inscripción para aspirantes o reinscripción para estudiantes y el monto con ID más alto de ese tipo. La solicitud deberá cambiar a `AUTORIZADO`, generar deducción y permitir luego **Aplicar deducción** (estado final inmutable `APLICADO`) o **Cancelar deducción** (pasa a `CANCELADO` y habilita **Reautorizar**).
   - En otra solicitud `PENDIENTE`: probar **Rechazar** sin comentario (debe exigir motivo obligatorio) y luego con motivo (cambia a `RECHAZADO`).
4. **Módulo de Convocatorias (Equipo 4) y barra común:**
   - Usar el menú **`Vistas ▾`** de la barra superior en `panel-admin.html` para navegar entre:
     - `convocatorias-admin.html` (listado, búsqueda, filtros por año/estado, modal de detalle y modal de edición).
     - `convocatorias-crear-editar.html` (probar validación de orden de fechas, montos con >2 decimales, fechas superpuestas con la `900002` y creación de una convocatoria futura sin solapamiento, p. ej. año `2027`).
     - `historial-convocatorias.html` (tras editar una fecha de la convocatoria creada, verificar que aparece el cambio comparativo `valor anterior -> valor nuevo`, filtros por administrador/campo/rango y modal de detalle).

---

## 5. Pruebas reales de correo SMTP (OTP y notificaciones) con Mailpit

1. Inicia un servidor SMTP local de captura (**Mailpit**, ya disponible en Docker) dentro de la misma red `sareduccion-net`:
   ```powershell
   docker run -d --rm --name sareduccion-mailpit --network sareduccion-net -p 8025:8025 -p 1025:1025 axllent/mailpit:latest
   ```
2. Agrega temporalmente estas variables al final de tu `.env`:
   ```env
   SPRING_MAIL_HOST=sareduccion-mailpit
   SPRING_MAIL_PORT=1025
   SAR_MAIL_FROM=sar@toluca.tecnm.mx
   SAR_OTP_ENABLED=true
   ```
3. Recrea solo el contenedor `backend` para tomar la nueva configuración:
   ```powershell
   docker compose --profile full-stack up -d backend
   ```
4. Prueba los tres flujos reales y observa los correos en **`http://localhost:8025`**:
   - **OTP de aspirante:** Inicia sesión con `F2026A001` / `MOPA080512MDFRNN01`. La interfaz mostrará el paso de código temporal (HTTP `202`); copia el código de 6 dígitos que llegó a Mailpit, ingrésalo y verifica que completa la sesión. Prueba también **Reenviar código**.
   - **Aviso manual:** Como `SA01`, abre el detalle de una solicitud de **aspirante** (p. ej. `F2026A001`), escribe un mensaje en *Enviar aviso* y confirma que llega a Mailpit (si pruebas con una solicitud de estudiante sin correo institucional, debe avisar `422` sin correo disponible).
   - **Aviso automático:** Aprueba, rechaza, cancela o aplica una deducción de una solicitud de **aspirante** (sin comentario y con `<script>` en el comentario para comprobar que el correo HTML lo escapa correctamente y llega de forma asíncrona).
5. **Limpieza al terminar:** Quita/comenta las 4 variables temporales de `.env`, ejecuta `docker compose --profile full-stack up -d backend` y detén Mailpit con `docker rm -f sareduccion-mailpit`.

---

## 6. Pruebas rápidas de seguridad, rutas unificadas y auditoría

Con los contenedores corriendo, puedes validar rápidamente desde PowerShell:

```powershell
# 1) Rutas sin sesión -> 401
curl.exe -s -o NUL -w "POST /api/notificaciones -> %{http_code}`n" -X POST http://localhost:8080/api/notificaciones
curl.exe -s -o NUL -w "GET /api/admin/convocatorias -> %{http_code}`n" http://localhost:8080/api/admin/convocatorias

# 2) Cabeceras de seguridad y CSP en vistas HTML (debe incluir https://cdn.jsdelivr.net)
curl.exe -s -I http://localhost:8081/administrativo/convocatorias-admin.html
```

- **Rutas eliminadas (`404` tras unificación):** Ya autenticada una sesión válida (desde DevTools → Red/Consola o script con cookies), verificar que devuelven `404`:
  - `GET /api/convocatorias`
  - `PUT /api/solicitudes/1/correccion`
  - `GET /api/solicitante/formulario/perfil`
  - `GET /api/solicitante/convocatorias-abiertas`
  - `PUT /api/solicitante/solicitudes/1/reenviar`
  - `GET /api/admin/convocatorias/999999/historial` y `PUT /api/admin/convocatorias/999999`
- **Prueba XSS en interfaz:** Enviar como comentario de rechazo o respuesta libre `<img src=x onerror="alert(1)">` y recargar tanto el panel administrativo como el panel del solicitante: debe renderizarse como texto plano, sin ejecutar alerta ni insertar HTML.
- **Bitácora de auditoría en Oracle:** Consultar en Oracle los registros generados por `PKG_SEGURIDAD`:
  ```powershell
  docker exec -i sareduccion-oracle sqlplus -L -s SA_REDUCCION/<ORACLE_APP_PASSWORD>@//localhost:1521/XEPDB1
  ```
  ```sql
  SELECT id_auditoria, fecha_evento, evento, tipo_perfil, identificador_usuario, direccion_ip, detalle, correlation_id
    FROM SEG_AUDITORIA_ACCESOS
   ORDER BY fecha_evento DESC FETCH FIRST 20 ROWS ONLY;
  ```

---

## 7. Resultados de verificación registrados (25 de noviembre de 2026)

| Suite / Verificación | Resultado | Detalle observado |
| --- | --- | --- |
| Java unitarias (`!*IntegrationTest`) | **87 / 87** | 12 clases, 0 fallos, 0 omitidas |
| Java integración Oracle XE | **15 / 15** | `AuthenticationIntegrationTest`, `ApplicantAuthenticationIntegrationTest`, `SolicitudAdminServiceIntegrationTest` |
| Sintaxis JS + JSDOM (`run-all.js`) | **106 / 106** | `formulario.test.js` (64), `panel-solicitante.test.js` (23), `panel-admin.test.js` (19) |
| PL/SQL `PKG_SEGURIDAD` | **8 / 8** | Transacción autónoma, truncado, validaciones, `JOB_SEG_DEPURAR_AUDITORIA` habilitado |
| PL/SQL reglas de negocio | **22 / 22** | Ciclo completo + inmutabilidad + `ROLLBACK` limpio |
| E2E en vivo + CSP + rendimiento | **22 / 22** | `p50` de `8.9 ms` a `13.1 ms`; `p95` de `12.4 ms` a `31.8 ms` (25 reqs, 5 concurrentes, 100% HTTP 200) sobre seed de desarrollo |
| OTP + SMTP en vivo (Mailpit) | **Exitoso** | Aspirante `F2026A001` (`202` -> código 6 dígitos en Mailpit -> `200 APPLICANT`) + aviso manual `204` recibido |
| Respaldo y restauración (`expdp`/`impdp`) | **Exitoso** | `expdp` 23 tablas (SHA-256 verificado); `impdp` en esquema temporal `SA_REDUCCION_REST` (23 tablas, 6/6 paquetes `VALID`, 17 secuencias, 24 triggers) |
| Compatibilidad navegadores (`RNF-32`) | **Parcial** | Auditoría estática de estándares web superada (sin APIs experimentales; `<dialog>` y `replaceChildren` estándar); pendiente prueba manual en Chrome/Firefox/Safari físicos |

**Latencias medidas sobre el entorno Docker local (`rendimiento-e2e.js`, seed de 17 solicitudes):**
- `GET /api/solicitudes`: `min=7.4ms`, `p50=12.4ms`, `p95=31.8ms`, `max=40.5ms` (`25/25` HTTP 200)
- `GET /api/solicitudes/estadisticas`: `min=7.8ms`, `p50=10.5ms`, `p95=15.2ms`, `max=16.4ms` (`25/25` HTTP 200)
- `GET /api/admin/convocatorias`: `min=6.3ms`, `p50=8.9ms`, `p95=12.4ms`, `max=12.7ms` (`25/25` HTTP 200)
- `GET /api/solicitante/convocatorias`: `min=9.0ms`, `p50=10.8ms`, `p95=14.7ms`, `max=16.4ms` (`25/25` HTTP 200)
- `GET /api/solicitante/solicitudes`: `min=8.9ms`, `p50=12.4ms`, `p95=17.0ms`, `max=18.2ms` (`25/25` HTTP 200)
- `GET /api/solicitante/formulario?convocatoria=900002&tipo=1`: `min=11.0ms`, `p50=13.1ms`, `p95=18.6ms`, `max=19.2ms` (`25/25` HTTP 200)

---

## 8. Escenarios que NO aplican aún (pendientes fuera de alcance)

- **Notificaciones por correo a estudiantes:** Aún no existe tabla/servicio institucional de correos para alumnos.
- **Generación de recibos y sistema de pagos:** El flujo termina en deducción `APLICADO`.
- **Validación en Oracle 12c y despliegue productivo (TLS/HSTS):** Pendiente de entrega de infraestructura oficial.

---

## Documentación relacionada

- [Guía de ejecución rápida](README.md)
- [Seguridad e integración](SEGURIDAD.md)
- [Notificaciones por correo](NOTIFICACIONES.md)
- [Matriz de requerimientos](REQUERIMIENTOS.md)
