# Guía de notificaciones del equipo 6

Configuración, operación e integración del correo de SAReducción para el encargado
del despliegue y los equipos de desarrollo.

La implementación se mantiene en la rama `equipo-6/notificaciones`. El proveedor
SMTP elegido es Brevo; el encargado administra sus credenciales. El backend utiliza esos valores
desde el entorno del contenedor.

La [demo pública de Vercel](https://sareduccion-notificaciones.vercel.app)
comparte la plantilla HTML con el backend. Usa un folio ficticio y un envío
SMTP real protegido con clave privada y lista de destinatarios. La
[guía de entrega al superadministrador](demo-notificaciones/README.md) explica
Brevo, Vercel, el contenedor Azure y las comprobaciones pendientes en Oracle 12c.

## Índice

- [Flujo y cobertura](#flujo-y-cobertura)
- [Preparación para Azure](#preparación-para-azure)
- [Despliegue con Docker Compose](#despliegue-con-docker-compose)
- [Variables en el contenedor de Azure](#variables-en-el-contenedor-de-azure)
- [Referencia de configuración SMTP](#referencia-de-configuración-smtp)
- [Oracle e integración con dev y main](#oracle-e-integración-con-dev-y-main)
- [Comprobación de los envíos](#comprobación-de-los-envíos)
- [Pruebas locales con Mailpit](#pruebas-locales-con-mailpit)
- [API y archivos de mantenimiento](#api-y-archivos-de-mantenimiento)
- [Diagnóstico](#diagnóstico)
- [Pendientes de integración](#pendientes-de-integración)

## Flujo y cobertura

1. El administrador abre el detalle de una solicitud y elige una acción.
2. El panel selecciona la plantilla correspondiente y muestra su vista previa.
3. **Agregar comentario** permite incluir una observación en el correo.
   El motivo del rechazo es obligatorio.
4. **Confirmar y enviar** registra la decisión e inicia el intento de envío.
   **Volver** permite regresar a las acciones.
5. Oracle confirma la operación. El backend consulta el destinatario y entrega
   el aviso al servidor SMTP.

| Acción | Resultado comunicado | Comentario |
| --- | --- | --- |
| Aprobar o reautorizar | Autorizado | Opcional |
| Rechazar | Rechazado | Obligatorio |
| Aplicar deducción | Aplicado | Opcional |
| Cancelar deducción | Cancelado | Opcional |

La plantilla incluye el número de solicitud, el resultado, el mensaje del evento
y el comentario cuando existe. El envío contiene HTML y una alternativa de texto
simple. La vista previa se actualiza antes de confirmar la acción.

El destinatario se obtiene de `Aspirantes`, relacionando ficha, periodo y año con
la solicitud. La cobertura actual corresponde a aspirantes. El correo institucional
de estudiantes requiere acordar e integrar su fuente de datos.

El formulario **Aviso por correo** permite enviar un mensaje manual independiente
de las decisiones administrativas.

La aceptación por SMTP indica que el servidor recibió el mensaje para su envío.
La entrega final se verifica en el buzón de destino. El éxito de una decisión
administrativa confirma el cambio en Oracle aunque falle la notificación.

## Preparación para Azure

El encargado necesita disponer de estos datos antes de configurar el backend.

| Dato | Quién lo proporciona |
| --- | --- |
| Host, puerto y modo de cifrado SMTP | Proveedor o administrador del correo |
| Usuario y credencial SMTP, cuando se exija autenticación | Proveedor o administrador del correo |
| Remitente o dominio autorizado | Proveedor o administrador del correo |
| Acceso de red desde el backend hasta el destino SMTP | Encargado de Azure |
| Cuenta técnica y destino Oracle del entorno | Encargado de base de datos |

La conexión admite SMTP con usuario y contraseña o un relay autorizado. Un proveedor
que exija OAuth2 o una API HTTP requiere una adaptación.

El perfil adicional `smtp-production` valida host, puerto, remitente, autenticación
y cifrado al arrancar. STARTTLS y TLS implícito son las dos opciones habituales.
El encargado utiliza el modo y el puerto indicados por su proveedor.

En producción, configura `SAR_LOAD_DEV_SEED=false` para una base nueva. Las cuentas
Oracle de ejemplo de `.env.example` deben sustituirse por las del entorno. El
Compose de producción fuerza OTP y cookies seguras, elimina la publicación directa
de Oracle/backend y sirve el sitio con HTTPS. Se necesita un certificado válido
para el dominio y su clave privada antes de arrancar.

## Despliegue con Docker Compose

Todos los comandos de esta sección se ejecutan desde la raíz del repositorio.
Necesitas Docker Compose v2.24.4 o posterior (el perfil de producción usa las
etiquetas `!reset` y `!override`) y acceso al código que incorpora esta rama.

### 1. Preparar el archivo del entorno

Si el despliegue utiliza un archivo local de variables, crea una copia de la
plantilla y completa los datos Oracle y de correo.

```bash
cp .env.example .env.production
```

`.env.production` está excluido de Git. Si ya existe, edita ese archivo conservando
los valores del entorno. El ejemplo siguiente muestra los valores de correo y
producción que debes ajustar en la copia. Sustituye los dominios y las credenciales
de ejemplo por los del proveedor.

```dotenv
SPRING_PROFILES_ACTIVE=prod
SAR_LOAD_DEV_SEED=false
TLS_CERT_PATH=/ruta/absoluta/al/fullchain.pem
TLS_KEY_PATH=/ruta/absoluta/a/privkey.pem

SPRING_MAIL_HOST=smtp-relay.brevo.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=usuario-smtp-indicado-por-brevo
SPRING_MAIL_PASSWORD=clave-smtp-del-entorno
SAR_MAIL_FROM=avisos@dominio.example
SMTP_AUTH=true

SMTP_STARTTLS_ENABLE=true
SMTP_STARTTLS_REQUIRED=true
SMTP_SSL_ENABLE=false
SMTP_TEST_CONNECTION=true
SMTP_ALLOW_PLAINTEXT=false

SMTP_CONNECTION_TIMEOUT_MS=5000
SMTP_READ_TIMEOUT_MS=5000
SMTP_WRITE_TIMEOUT_MS=5000
```

El complemento de correo añade `smtp-production` a `SPRING_PROFILES_ACTIVE`.
`prod` representa el perfil base del entorno. En la integración conserva los
perfiles que haya definido el encargado.

La plantilla de desarrollo establece `SMTP_TEST_CONNECTION=false`. Cambia su valor
a `true` en producción para comprobar la conexión durante el arranque.

### 2. Elegir el modo de cifrado

Para **STARTTLS**, usa el ejemplo anterior con el puerto del proveedor, habitualmente
587. El complemento exige que la negociación STARTTLS sea obligatoria.

Para **TLS implícito**, cambia estas cuatro variables. El puerto habitual es 465.

```dotenv
SPRING_MAIL_PORT=465
SMTP_STARTTLS_ENABLE=false
SMTP_STARTTLS_REQUIRED=false
SMTP_SSL_ENABLE=true
```

Para un relay que autorice el envío sin autenticación, establece `SMTP_AUTH=false`
y deja vacíos `SPRING_MAIL_USERNAME` y `SPRING_MAIL_PASSWORD`. La configuración
normal de producción usa una conexión cifrada. Un relay interno que requiera
texto sin cifrar necesita `SMTP_ALLOW_PLAINTEXT=true` explícito y ambas opciones
de cifrado desactivadas.

### 3. Validar y arrancar

```bash
docker compose --env-file .env.production \
  -f docker-compose.yml -f docker-compose.smtp-production.yml \
  --profile full-stack config --quiet

docker compose --env-file .env.production \
  -f docker-compose.yml -f docker-compose.smtp-production.yml \
  --profile full-stack up -d --build

docker compose --env-file .env.production \
  -f docker-compose.yml -f docker-compose.smtp-production.yml \
  --profile full-stack ps
```

`config --quiet` valida la combinación de archivos y variables. Después del arranque,
consulta los registros del backend.

```bash
docker compose --env-file .env.production \
  -f docker-compose.yml -f docker-compose.smtp-production.yml \
  --profile full-stack logs --tail=100 backend
```

Usa ambos archivos y el mismo `--env-file` en las siguientes operaciones del
despliegue. Después de cambiar las credenciales o el servidor, aplica la nueva
configuración recreando el backend.

```bash
docker compose --env-file .env.production \
  -f docker-compose.yml -f docker-compose.smtp-production.yml \
  --profile full-stack up -d --build backend
```

El archivo principal de Compose levanta Oracle, backend y frontend. Si el encargado
ya tiene una configuración propia del proyecto integrado, debe incorporar las
variables SMTP, TLS, `SESSION_COOKIE_SECURE=true` y `SAR_OTP_ENABLED=true` al
servicio backend y proxy de ese despliegue. El certificado debe corresponder al
dominio público; el puerto 80 redirige al 443 y solo el frontend publica puertos.

## Variables en el contenedor de Azure

Si el encargado utiliza una definición de contenedores en Azure, configura las
variables en el entorno del **backend** e incorpora las credenciales mediante
el mecanismo de secretos elegido para el despliegue.

1. Publica una imagen reconstruida que incluya los recursos y las clases de correo.
2. Establece `SPRING_PROFILES_ACTIVE=prod,smtp-production`, adaptando `prod` al
   perfil base de su aplicación.
3. Configura todas las variables SMTP del ejemplo de STARTTLS o TLS implícito.
4. Mantén las variables de conexión `DB_URL`, `DB_USER` y `DB_PASSWORD` de Oracle.
5. Verifica la salida de red hacia el host y puerto SMTP del proveedor.
6. Arranca una nueva versión del contenedor y realiza la comprobación de entrega
   descrita más adelante.

En este caso, el perfil `smtp-production` debe figurar en la lista de perfiles
del backend. El complemento de Compose lo agrega cuando se usa Compose.
Los valores `SMTP_*` de la referencia siguiente se leen desde las propiedades
de la aplicación y funcionan como variables del contenedor.

## Referencia de configuración SMTP

| Variable | Uso |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | Perfiles activos del backend. Incluir `smtp-production` para validar el correo real. |
| `SPRING_MAIL_HOST` | Nombre DNS o dirección del servidor SMTP. |
| `SPRING_MAIL_PORT` | Puerto indicado por el proveedor. |
| `SPRING_MAIL_USERNAME` | Usuario SMTP cuando la autenticación está habilitada. |
| `SPRING_MAIL_PASSWORD` | Credencial SMTP del usuario. |
| `SAR_MAIL_FROM` | Dirección de remitente autorizada por el proveedor. |
| `SMTP_AUTH` | `true` exige usuario y contraseña. `false` permite relay sin autenticación. |
| `SMTP_STARTTLS_ENABLE` | Habilita STARTTLS. Desactivar al usar TLS implícito. |
| `SMTP_STARTTLS_REQUIRED` | Exige STARTTLS en el Compose principal. El complemento de producción lo ajusta a `SMTP_STARTTLS_ENABLE`. |
| `SMTP_SSL_ENABLE` | Habilita TLS implícito desde la conexión. |
| `SMTP_TEST_CONNECTION` | Comprueba la conexión al arrancar con el perfil de producción. Usar `true` para la puesta en marcha. |
| `SMTP_ALLOW_PLAINTEXT` | Excepción explícita para un relay interno sin cifrado. Valor normal `false`. |
| `SMTP_CONNECTION_TIMEOUT_MS` | Espera de conexión, en milisegundos. Predeterminado `5000`. |
| `SMTP_READ_TIMEOUT_MS` | Espera de lectura. Predeterminado `5000`. |
| `SMTP_WRITE_TIMEOUT_MS` | Espera de escritura. Predeterminado `5000`. |

La aplicación verifica la identidad TLS del servidor con los certificados de
confianza del JRE. Un servidor con CA privada requiere instalar su CA en el
almacén de confianza del contenedor.

Con `smtp-production` activo, el arranque falla ante configuración inválida.
Con `SMTP_TEST_CONNECTION=true`, también falla si la comprobación de conexión SMTP
reporta un error. Esta comprobación abre la conexión y comprueba autenticación
cuando se exige. La primera entrega se verifica mediante un correo de prueba.

`SMTP_TEST_CONNECTION=false` permite omitir la comprobación de red durante el
arranque. La validación de los valores de configuración sigue activa.

Compose permite que las variables exportadas por la terminal prevalezcan sobre
el archivo de entorno. Usa una única fuente de valores para cada despliegue.
Evita mezclar valores contradictorios de `SMTP_*` con propiedades directas
`SPRING_MAIL_PROPERTIES_MAIL_SMTP_*` en la definición del contenedor.

## Oracle e integración con dev y main

La consulta del destinatario utiliza
`PKG_ADMINISTRADOR.consultar_correo_solicitud`, definido en
[docker/init-db/05-crear-paquetes-permisos.sql](docker/init-db/05-crear-paquetes-permisos.sql).

| Conexión | Compose del repositorio | Contenedor con Oracle externo |
| --- | --- | --- |
| Destino | `oracle-db` y el PDB definido en `ORACLE_DATABASE` | `DB_URL` del entorno |
| Usuario técnico | `ORACLE_BACKEND_USER` | `DB_USER` |
| Credencial | `ORACLE_BACKEND_PASSWORD` | `DB_PASSWORD` |

Para una base nueva, el inicializador del proyecto instala el paquete. En una base
existente, el equipo responsable de Oracle incorpora el procedimiento a su versión
consolidada y valida compilación y permisos de la cuenta técnica. El script 05
contiene definiciones completas de paquetes que deben conservar los procedimientos
de todos los equipos.

Esta rama incorpora `origin/main` en `6b160b0b341bb66091c839753855399cfe128a6d`.
Se conservan el envío asíncrono y los controles de seguridad de la versión
integrada. El responsable de integración debe completar estos pasos:

1. Revisar e incorporar la rama a la versión conjunta, conservando las funciones
   y los cambios posteriores de los demás equipos.
2. Mantener las clases de configuración y el perfil `smtp-production` del equipo 6.
3. Configurar los secretos Brevo y el remitente verificado en el backend de Azure.
4. Con el equipo 7, validar los paquetes y los permisos de la cuenta técnica en
   la versión exacta de Oracle 12c. El inicializador del repositorio usa XE 21c;
   las migraciones para la base existente requieren revisión.
5. Validar el flujo con sesión, permisos y CSRF del equipo 1.
6. Comprobar los cambios en Oracle y la recepción de los correos desde Azure.
7. Promover la versión comprobada a `main`, reconstruir la imagen del backend
   y publicar el frontend actualizado.

La guía del equipo 6 cubre la configuración SMTP. La aceptación del despliegue
completo requiere la prueba conjunta del proyecto integrado.

## Comprobación de los envíos

Usa registros ficticios de aspirantes y buzones del equipo autorizados para la
prueba. Elige solicitudes con estados compatibles con cada acción.

| Prueba | Resultado esperado |
| --- | --- |
| Aprobar o reautorizar sin comentario | Estado Autorizado y correo con plantilla, sin bloque de comentario. |
| Aprobar o reautorizar con comentario | El texto aparece en el correo y el estado queda guardado. |
| Rechazar sin motivo | El panel exige el comentario antes de confirmar. |
| Rechazar con motivo | Estado Rechazado y correo que incluye el motivo. |
| Aplicar una deducción autorizada | Solicitud y deducción Aplicado y correo correspondiente. |
| Cancelar una deducción autorizada | Estado Cancelado y aviso de cancelación. |
| Envío manual | El mensaje escrito aparece en el buzón del destinatario esperado. |
| Fallo SMTP después del arranque | La decisión se conserva y el backend registra el fallo del aviso. |

Para probar la tolerancia a fallos, usa el entorno de pruebas después de arrancar
con una conexión SMTP disponible. Si el servidor ya está inaccesible al arrancar
y `SMTP_TEST_CONNECTION=true`, el arranque se detendrá en esa comprobación.

Cada decisión modifica el estado guardado. Aplicado es definitivo. Prepara
solicitudes distintas o usa las transiciones de reenvío permitidas para repetir
los escenarios.

Las pruebas automatizadas se ejecutan con Maven y Java compatibles con el proyecto.

```bash
mvn -Dtest=SmtpProductionConfigurationTest,NotificacionServiceTest,NotificacionControllerTest test
```

Estas pruebas cubren la configuración, los modos de
cifrado, los errores de arranque, las plantillas con y sin comentario, el escape
HTML, la alternativa de texto y el endpoint manual. La validación de configuración
de Compose se realiza con `config --quiet`. Las pruebas con dobles de envío
se complementan con la entrega al proveedor elegido.

La suite integrada sin pruebas `*IntegrationTest` pasó 97 pruebas Java
(0 fallos y 0 errores). La demo Node pasó 6 pruebas de plantillas, validación
y envío con transporte simulado. Estas pruebas no sustituyen la comprobación
con Oracle 12c y el contenedor Azure reales.

Antes de aprobar el despliegue, el encargado debe registrar el commit o la imagen
probados, el modo SMTP, los resultados de los casos anteriores y la recepción
en los buzones de prueba. Las credenciales se conservan en la configuración
del entorno.

## Pruebas locales con Mailpit

Mailpit captura los correos durante el desarrollo local. Para un entorno nuevo,
crea `.env` desde `.env.example`, completa Oracle y activa los datos ficticios
con `SAR_LOAD_DEV_SEED=true`.

Crea `compose.mailpit.yml` junto al Compose principal.

```yaml
services:
  mailpit:
    image: axllent/mailpit:latest
    ports:
      - "127.0.0.1:8025:8025"
```

En `.env`, establece todos estos valores de correo para sustituir la configuración
de cifrado de desarrollo anterior.

```dotenv
SPRING_PROFILES_ACTIVE=dev
SPRING_MAIL_HOST=mailpit
SPRING_MAIL_PORT=1025
SPRING_MAIL_USERNAME=
SPRING_MAIL_PASSWORD=
SAR_MAIL_FROM=sar@example.test
SMTP_AUTH=false
SMTP_STARTTLS_ENABLE=false
SMTP_STARTTLS_REQUIRED=false
SMTP_SSL_ENABLE=false
SMTP_TEST_CONNECTION=false
```

```bash
docker compose --env-file .env \
  -f docker-compose.yml -f compose.mailpit.yml \
  --profile full-stack up -d --build
```

Abre el [panel administrativo](http://localhost:8081/administrativo/login.html)
y el [buzón de pruebas](http://localhost:8025/). El puerto del panel corresponde a
`FRONTEND_PORT`. El backend usa `mailpit:1025` dentro de la red de Compose.

En Apple Silicon, las imágenes seleccionadas por el proyecto pueden requerir
`platform: linux/amd64` en `oracle-db` y `backend` del archivo adicional.
La emulación puede aumentar el tiempo de arranque.

## API y archivos de mantenimiento

El envío manual usa `POST /api/notificaciones`, con sesión de administrador
y la cabecera `X-CSRF-TOKEN` de esa sesión.

```json
{
  "idSolicitud": 10,
  "mensaje": "Aviso de prueba"
}
```

| Resultado del envío manual | HTTP |
| --- | --- |
| SMTP acepta el mensaje | 204 |
| Falta sesión | 401 |
| Rol o CSRF inválido | 403 |
| Identificador o mensaje inválidos | 400 |
| Solicitud sin correo disponible | 422 |
| Falla SMTP o falta configuración de envío | 503 |

Las decisiones utilizan sus endpoints administrativos. Al aplicar una deducción,
`PUT /api/deducciones/{id}/aplicar` acepta este cuerpo opcional.

```json
{
  "comentario": "Su deducción fue aplicada."
}
```

| Archivo | Función |
| --- | --- |
| [NotificacionService.java](src/main/java/com/itt/sareduccion/service/NotificacionService.java) | Prepara el mensaje y solicita el envío SMTP. |
| [SmtpProductionConfiguration.java](src/main/java/com/itt/sareduccion/config/SmtpProductionConfiguration.java) | Valida la configuración real del backend. |
| [aviso-tramite.html](src/main/resources/mail/aviso-tramite.html) | Diseño editable del correo automático. |
| [application-smtp-production.properties](src/main/resources/application-smtp-production.properties) | Perfil adicional de correo. |
| [docker-compose.smtp-production.yml](docker-compose.smtp-production.yml) | Variables de producción para Compose. |
| [SolicitudAdminService.java](src/main/java/com/itt/sareduccion/service/SolicitudAdminService.java) | Inicia el aviso después de confirmar la decisión en Oracle. |
| [AdministradorRepository.java](src/main/java/com/itt/sareduccion/repository/AdministradorRepository.java) | Consulta el destinatario con el paquete Oracle. |
| [panel-admin.html](modulo-administrativo/panel-admin.html) y [app.js](modulo-administrativo/js/app.js) | Acciones, comentario opcional y vista previa. |

Al editar la plantilla, conserva `{{solicitud}}`, `{{estado}}`, `{{color}}`,
`{{mensaje}}` y `{{comentario}}`. El servicio sustituye estos marcadores.
Reconstruye el backend después de cambiar el recurso HTML. Los comentarios
se escapan como HTML y el bloque se omite cuando el comentario está vacío.

## Diagnóstico

Para Compose de producción, utiliza el mismo comando de registros de la sección
de despliegue. Estos son los casos que debe revisar el encargado.

| Síntoma | Comprobación |
| --- | --- |
| Falta host o remitente al validar Compose | Completar `SPRING_MAIL_HOST` y `SAR_MAIL_FROM` en la fuente de variables del despliegue. |
| El backend falla al arrancar con `smtp-production` | Revisar el error de configuración y los campos obligatorios. |
| La comprobación SMTP falla | Revisar DNS, puerto, salida de red, credenciales, certificado y modalidad TLS. |
| STARTTLS y TLS implícito están activos juntos | Elegir el modo indicado por el proveedor. |
| El envío manual devuelve 422 | Revisar la solicitud y su relación con `Aspirantes`, incluyendo periodo, año y correo. |
| El envío manual devuelve 503 | Revisar configuración de envío y respuesta del servidor SMTP. |
| SMTP acepta el mensaje y el buzón no lo muestra | Revisar remitente autorizado, destinatario, carpeta de correo no deseado y diagnóstico del proveedor. |
| Oracle reporta un procedimiento inexistente | Verificar la versión consolidada de `PKG_ADMINISTRADOR` y los permisos de la cuenta técnica. |
| El contenedor usa valores anteriores | Aplicar `up -d` con los mismos archivos y variables para recrear el backend. |
| Falta la plantilla en el arranque | Reconstruir la imagen incluyendo `src/main/resources/mail/aviso-tramite.html`. |

## Pendientes de integración

- Comprobar la entrega Brevo desde el contenedor Azure definitivo.
- Verificar Oracle 12c, sesión, permisos y CSRF en el despliegue conjunto antes
  de promover a `main`.
- Acordar e integrar la fuente de correo institucional de estudiantes.
- El comentario de Aplicado se incluye en el correo. Su guardado en el historial
  Oracle requiere ampliar la operación correspondiente.
- El servicio conserva la decisión cuando falla el envío. Los reintentos
  persistentes requieren un mecanismo de almacenamiento y recuperación.
