# Notificaciones por correo · Equipo 6

Esta guía permite configurar, integrar y probar los avisos del trámite.
La implementación está en la rama equipo-6/notificaciones.

## Funcionamiento desde el panel

1. El administrador abre el detalle de una solicitud y elige Aprobar,
   Reautorizar, Rechazar, Cancelar deducción o Aplicar deducción.
2. El panel selecciona la plantilla del resultado y muestra su vista previa.
3. Agregar comentario permite incluir una observación. El motivo del rechazo
   es obligatorio. En las demás acciones se puede usar directamente la plantilla.
4. Confirmar y enviar ejecuta la decisión. Volver permite elegir otra acción.
5. El backend confirma el cambio de estado en Oracle, consulta el destinatario
   y solicita el envío al servidor SMTP.

El correo contiene el número de solicitud, su resultado y el comentario cuando
se haya escrito. Incluye HTML y una alternativa de texto simple. Las plantillas
cubren Autorizado, Rechazado, Aplicado y Cancelado.

La consulta del destinatario usa Aspirantes, relacionando ficha, periodo y año
con la solicitud. La cobertura actual corresponde a aspirantes. Una solicitud
de estudiante puede carecer de destinatario en esta implementación.

También existe el envío manual independiente mediante Aviso por correo.

## Correo en AWS y contenedores de producción

Activa el perfil adicional smtp-production junto a los perfiles propios del
despliegue. El perfil valida la configuración efectiva antes de aceptar tráfico
y, por defecto, comprueba la conexión SMTP al arrancar. Las credenciales de
producción se suministran en las variables del contenedor, mediante la
configuración de secretos del despliegue en AWS.

El proveedor lo elige el encargado. La conexión admite usuario y contraseña
o relay autorizado sin autenticación. Para proveedores que exijan OAuth2
o una API HTTP se necesita una integración adicional.

| Variable | Valor para STARTTLS | Valor para TLS implícito |
| --- | --- | --- |
| SPRING_PROFILES_ACTIVE | prod,smtp-production | prod,smtp-production |
| SPRING_MAIL_HOST | Host del proveedor | Host del proveedor |
| SPRING_MAIL_PORT | Puerto del proveedor, normalmente 587 | Puerto del proveedor, normalmente 465 |
| SPRING_MAIL_USERNAME | Usuario SMTP | Usuario SMTP |
| SPRING_MAIL_PASSWORD | Credencial SMTP | Credencial SMTP |
| SAR_MAIL_FROM | Remitente autorizado | Remitente autorizado |
| SMTP_AUTH | true | true |
| SMTP_STARTTLS_ENABLE | true | false |
| SMTP_STARTTLS_REQUIRED | true | false |
| SMTP_SSL_ENABLE | false | true |
| SMTP_TEST_CONNECTION | true | true |

Para relay sin autenticación, configura SMTP_AUTH=false y deja usuario y
contraseña vacíos. La identidad TLS del servidor se verifica con los
certificados de confianza del JRE. Un certificado privado requiere instalar
su CA en el almacén de confianza del contenedor.

Los tiempos de conexión, lectura y escritura se controlan con
SMTP_CONNECTION_TIMEOUT_MS, SMTP_READ_TIMEOUT_MS y SMTP_WRITE_TIMEOUT_MS.
El valor predeterminado de cada uno es 5000 milisegundos.

Si el encargado usa Compose, puede combinar su configuración con el complemento
de esta rama. Las variables de su entorno deben contener el host y remitente
reales. El complemento agrega smtp-production a SPRING_PROFILES_ACTIVE.

```bash
docker compose -f docker-compose.yml -f docker-compose.smtp-production.yml \
  --profile full-stack config --quiet
docker compose -f docker-compose.yml -f docker-compose.smtp-production.yml \
  --profile full-stack up -d --build
```

Para ECS u otro orquestador, agrega las variables directamente al contenedor
backend y publica la imagen reconstruida con los recursos de esta rama.
El complemento configura el correo. La red, Oracle, HTTPS, cookies y el seed
del despliegue se coordinan con el encargado del proyecto. En el entorno real
establece SAR_LOAD_DEV_SEED=false.

La verificación de conexión al arranque usa SMTP_TEST_CONNECTION=true.
Si falla, revisa host, puerto, salida de red desde AWS, credenciales y cifrado.
SMTP_TEST_CONNECTION=false permite arrancar sin comprobar la conectividad
en ese momento. La validación local de los campos sigue activa.
La comprobación de conexión no envía mensajes y no garantiza entrega final.

Para permitir SMTP sin cifrado en un relay interno que lo requiera, el encargado
debe establecer SMTP_ALLOW_PLAINTEXT=true. La configuración normal usa TLS.
Para el proveedor elegido, confirma el remitente o dominio autorizado, las
restricciones de destinatarios de prueba y los límites de envío. Comprueba
un mensaje real desde el contenedor antes de aprobar el despliegue.

Prueba la configuración y los avisos antes de integrar.

```bash
mvn -Dtest=SmtpProductionConfigurationTest,NotificacionServiceTest,NotificacionControllerTest test
```

Las notificaciones automáticas actuales conservan el cambio administrativo si
falla el correo. No existe reintento persistente que sobreviva a reinicios.
Al integrar con dev, conserva su implementación de envío en segundo plano.
La cobertura de correo institucional de estudiantes y el historial del
comentario de Aplicado requieren coordinación con el equipo responsable de
los datos.

## Configuración del entorno

Se necesita Docker Desktop o Docker Engine con Compose. Desde la raíz del
repositorio, crea el archivo local de configuración.

```bash
cp .env.example .env
```

Completa las variables Oracle de esa plantilla. Para una base nueva de pruebas,
SAR_LOAD_DEV_SEED=true permite cargar los datos ficticios.

El archivo .env queda en tu equipo. Las credenciales reales se configuran en el
entorno del despliegue.

### Prueba local con Mailpit

Mailpit captura los mensajes que envía la aplicación. Crea compose.mailpit.yml
junto a docker-compose.yml con este contenido.

```yaml
services:
  mailpit:
    image: axllent/mailpit:latest
    ports:
      - "127.0.0.1:8025:8025"
```

Agrega estas variables al .env.

```dotenv
SPRING_MAIL_HOST=mailpit
SPRING_MAIL_PORT=1025
SAR_MAIL_FROM=sar@example.test
SMTP_AUTH=false
SMTP_STARTTLS_ENABLE=false
```

El backend usa mailpit como nombre del servicio dentro de la red de Compose.
El puerto 1025 corresponde a SMTP y el 8025 a la interfaz del buzón.

Inicia la aplicación y el buzón con ambos archivos.

```bash
docker compose -f docker-compose.yml -f compose.mailpit.yml \
  --profile full-stack up -d --build
docker compose -f docker-compose.yml -f compose.mailpit.yml \
  --profile full-stack ps
```

Espera a que Oracle esté saludable y el backend haya iniciado. Abre
http://localhost:8081/administrativo/login.html y http://localhost:8025/.
Si FRONTEND_PORT cambia en .env, usa ese puerto para el panel.

En Apple Silicon, las imágenes Oracle XE y Java 8 Alpine del proyecto pueden
requerir platform: linux/amd64 en los servicios oracle-db y backend del archivo
adicional de Compose. El arranque con emulación puede tardar varios minutos.

### Conexión con el correo institucional

Solicita al responsable del correo el host, puerto, modalidad de cifrado,
usuario y contraseña o credencial de aplicación, y los remitentes permitidos.
Para un servidor que admita STARTTLS y autenticación en el puerto 587, la
configuración tiene esta forma. Sustituye los valores de ejemplo por los
proporcionados por la institución.

```dotenv
SPRING_MAIL_HOST=smtp.institucion.example
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=cuenta-de-envio
SPRING_MAIL_PASSWORD=credencial-del-servidor
SAR_MAIL_FROM=avisos@institucion.example
SMTP_AUTH=true
SMTP_STARTTLS_ENABLE=true
```

El remitente debe estar autorizado por el servidor. Si el proveedor exige
OAuth2 u otro mecanismo, se requiere adaptar la integración. Para TLS
implícito, el backend admite la propiedad de Spring
SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_ENABLE=true, que debe agregarse al bloque
environment del servicio backend en Compose. Usa el puerto indicado por el
proveedor y ajusta STARTTLS según su configuración.

Después de editar las variables, recrea el backend usando los mismos archivos
de Compose del despliegue. Para el archivo principal, ejecuta lo siguiente.

```bash
docker compose --profile full-stack up -d --build backend
```

Cambiar el .env y reiniciar el contenedor existente puede conservar su entorno
anterior. El comando up recrea el servicio cuando detecta la nueva configuración.

## Conexión con Oracle e integración en main

Compose proporciona al backend estas variables.

- DB_URL apunta al servicio oracle-db y al PDB definido en ORACLE_DATABASE.
- DB_USER toma ORACLE_BACKEND_USER.
- DB_PASSWORD toma ORACLE_BACKEND_PASSWORD.

En una instalación externa, configura DB_URL, DB_USER y DB_PASSWORD con la
cuenta técnica y el destino correspondientes.

La notificación utiliza el procedimiento
PKG_ADMINISTRADOR.consultar_correo_solicitud, definido en
docker/init-db/05-crear-paquetes-permisos.sql. En una base nueva, el inicializador
del proyecto instala el paquete.

En una base existente, el equipo de integración debe incorporar el procedimiento
a la versión consolidada de PKG_ADMINISTRADOR y validar su compilación y permisos.
El script 05 contiene definiciones completas de paquetes. Al combinar ramas,
consolida primero las modificaciones de todos los equipos para conservar sus
procedimientos. El arranque habitual de Docker conserva la base existente.

Para integrar en main, incluye el backend, la interfaz y el recurso HTML,
reconstruye el backend y publica los archivos actualizados del frontend.
Comprueba el funcionamiento conjunto con los controles de seguridad del equipo 1.

## Plantilla y archivos principales

- src/main/resources/mail/aviso-tramite.html contiene el diseño HTML editable.
  Mantén los marcadores {{solicitud}}, {{estado}}, {{color}}, {{mensaje}} y
  {{comentario}}. El servicio sustituye sus valores al enviar.
- NotificacionService.java selecciona el resultado, escapa el comentario y envía
  HTML y texto simple con JavaMailSender.
- SolicitudAdminService.java dispara el aviso después de registrar la decisión.
- SolicitudAdminController.java recibe el comentario opcional al aplicar.
- AdministradorRepository.java consulta el correo mediante el paquete Oracle.
- modulo-administrativo/panel-admin.html y js/app.js muestran las acciones,
  la opción de comentario y la vista previa.

Al editar el recurso HTML, reconstruye el backend para incluirlo en el JAR.

## API y resultados del envío

POST /api/notificaciones recibe este cuerpo para un aviso manual.

```json
{
  "idSolicitud": 10,
  "mensaje": "Aviso de prueba"
}
```

Requiere sesión de administrador y X-CSRF-TOKEN válido. Las decisiones usan sus
endpoints administrativos existentes. PUT /api/deducciones/{id}/aplicar acepta
un cuerpo opcional con comentario.

| Resultado del aviso manual | HTTP |
| --- | --- |
| SMTP acepta el aviso | 204 |
| Falta sesión | 401 |
| Rol o CSRF inválido | 403 |
| Identificador o mensaje inválidos | 400 |
| Solicitud sin correo disponible | 422 |
| SMTP falla o falta configuración de envío | 503 |

En un aviso automático, el éxito de la decisión confirma la operación en Oracle.
Un fallo SMTP queda registrado y la decisión permanece guardada. La aplicación
actual no mantiene una cola de reintentos. La aceptación por SMTP se verifica
en Mailpit o en los registros del servidor de correo. La entrega final depende
del servidor y del buzón de destino.

El comentario de Aplicado viaja en el correo. El procedimiento actual de
aplicación no lo guarda en el historial Oracle. Las otras acciones conservan
su almacenamiento previo de comentarios.

## Comprobación antes de presentar o integrar

1. Entra como administrador con las credenciales de prueba definidas en .env.
2. Usa una solicitud ficticia de aspirante con estado compatible con la acción.
3. Prueba Autorizar con y sin comentario y verifica el mensaje en Mailpit.
4. Prueba Rechazar y comprueba que exija el motivo y lo incluya en el correo.
5. Aplica una deducción autorizada y comprueba la plantilla Aplicado.
6. Vuelve a consultar las solicitudes para confirmar los estados.
7. Detén Mailpit durante una prueba y confirma que se registre el fallo SMTP.

Cada decisión cambia el estado guardado. Usa solicitudes ficticias diferentes
o las transiciones de reenvío permitidas para repetir los escenarios.
Aplicado es un estado definitivo.

Pruebas automatizadas de las plantillas y del envío manual, con Maven y Java
compatibles con el proyecto.

```bash
mvn -Dtest=NotificacionServiceTest,NotificacionControllerTest test
```

Los casos comprueban las plantillas con y sin comentario, HTML escapado,
alternativa de texto simple y las respuestas del endpoint manual.

Para diagnosticar errores en la instalación principal, consulta los registros.

```bash
docker compose logs --tail=100 backend
docker compose logs --tail=100 oracle-db
```

Si el aviso carece de destinatario, revisa la relación de la solicitud con
Aspirantes y su correo. Si falla SMTP, revisa el host, el puerto, el remitente,
las credenciales y el cifrado. Si Oracle informa que falta el procedimiento,
revisa la actualización consolidada de PKG_ADMINISTRADOR.
