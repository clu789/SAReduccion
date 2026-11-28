# Notificaciones del Equipo 6: demo y despliegue real

Esta rama contiene el servicio de correo del proyecto Spring Boot y una demo
independiente para mostrar sus plantillas en Vercel. Ambos leen la misma plantilla
[aviso-tramite.html](../src/main/resources/mail/aviso-tramite.html).

Demo publicada: <https://sareduccion-notificaciones.vercel.app>.

## Qué hace cada despliegue

| Entorno | Comportamiento |
| --- | --- |
| Demo de Vercel | Permite elegir Autorizado, Rechazado, Aplicado o Cancelado, revisar el correo e intentar un envío real con Brevo a buzones autorizados. El folio es ficticio y no modifica Oracle. |
| Proyecto completo en Azure | El panel administrativo guarda la decisión en Oracle. Después, Spring Boot obtiene el correo del aspirante e inicia el aviso en segundo plano. Conserva la sesión, los roles y CSRF del proyecto integrado. |

El comentario es opcional, salvo al rechazar. Cuando se omite, se utiliza la
plantilla del estado sin un bloque vacío. La aceptación SMTP confirma que Brevo
recibió el mensaje; la entrega final se comprueba en el buzón y en Brevo.

## Configuración de Brevo

1. En **Configuración → SMTP y API → SMTP**, obtener el usuario SMTP y generar
   una clave exclusiva para SAReduccion. La contraseña de la cuenta y una clave
   API HTTP no sustituyen la clave SMTP.
2. En **Remitentes, dominio, IP**, verificar el remitente. Para producción,
   configurar el dominio institucional y los registros de autenticación que
   indique Brevo. La cuenta actual tiene un remitente de pruebas verificado.
3. Configurar `smtp-relay.brevo.com`, puerto `587` y STARTTLS obligatorio.
4. Revisar **Seguridad → IP autorizadas**. Si el bloqueo SMTP está activo,
   autorizar las IP de salida del servidor. La URL pública del sitio no es su
   dirección de salida SMTP.

Vercel usa IP de salida variables sin el servicio adicional Static IPs.
Autorizar una IP observada no garantiza los siguientes envíos. Para conservar
el bloqueo SMTP, usar salida fija o un relay con salida fija administrado por
el responsable. Desactivar ese bloqueo requiere autorización del dueño de
Brevo y afecta a todas las claves SMTP de la cuenta; el bloqueo API se administra
por separado. No se contrata infraestructura adicional con esta rama.

## Publicar la demo en Vercel

Crear un proyecto independiente del despliegue completo. Usar la **raíz del
repositorio** como Root Directory; así se incluye la plantilla compartida.

| Ajuste | Valor |
| --- | --- |
| Framework | Other |
| Node.js | 22.x |
| Install Command | `npm ci` |
| Build Command | `npm run build:demo` |
| Output Directory | `demo-notificaciones/dist` |
| API | `api/notificaciones.js` |

Estos valores se incluyen en [vercel.json](../vercel.json) y
[package.json](../package.json). Vercel publica HTML/CSS/JS y una función Node
que usa Nodemailer. El contenedor Java y Oracle se despliegan en Azure.

Agregar las siguientes variables al entorno **Production** del proyecto.
Guardar la credencial SMTP y la clave de demostración como secretos.

```dotenv
SPRING_MAIL_HOST=smtp-relay.brevo.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=usuario-smtp-indicado-por-brevo
SPRING_MAIL_PASSWORD=clave-smtp-exclusiva
SAR_MAIL_FROM=remitente-verificado@dominio.example
SMTP_STARTTLS_ENABLE=true
SMTP_SSL_ENABLE=false
DEMO_ACCESS_CODE=una-clave-privada-larga-y-aleatoria
DEMO_RECIPIENTS=buzon-autorizado@dominio.example
```

`DEMO_RECIPIENTS` acepta una lista separada por comas. El servidor rechaza
cualquier otro destinatario. `DEMO_ACCESS_CODE` se comparte por un canal privado
con quien mostrará la demo y se escribe en el formulario. Estas dos variables
son exclusivas de la demo; el backend Java usa su autenticación administrativa.
La demo exige usuario y clave SMTP y cifrado TLS.

Después de cambiar variables, crear un nuevo despliegue. El indicador
**configuración cargada** comprueba que existen valores válidos; la conexión y
la entrega se comprueban con un envío.

Desde una terminal autenticada con Vercel CLI:

```bash
npm ci
npm run test:demo
npm run build:demo
npx vercel link --project sareduccion-notificaciones
npx vercel deploy --prod
```

La conexión automática entre GitHub y Vercel requiere permisos sobre el
repositorio y su instalación de GitHub. Si esa conexión no está disponible,
los despliegues por CLI siguen siendo posibles; un `git push` por sí solo no
actualiza la demo en ese caso.

## Probar en una laptop o en clase

1. Abrir el enlace público de producción.
2. Seleccionar **Autorizado** y revisar la vista previa sin comentario.
3. Activar **Agregar comentario** y comprobar que aparece dentro del correo.
4. Seleccionar **Rechazado**: el motivo se vuelve obligatorio.
5. Mostrar **Aplicado** y **Cancelado** con sus mensajes correspondientes.
6. Escribir un buzón de la lista autorizada y la clave privada de demostración.
7. Enviar una prueba y comprobar el mensaje en el buzón, incluida la carpeta
   de correo no deseado. Revisar el evento del proveedor si no aparece.

Para ejecutar localmente, crear `.env.demo.local` en la raíz con las variables
anteriores y ejecutar `npm run dev:demo`. Abrir
<http://127.0.0.1:18106>. El archivo está excluido de Git. Reiniciar el proceso
después de cambiar sus variables. La conexión SMTP local también está sujeta
a la autorización de IP de Brevo.

## Configurar el proyecto completo en Azure

El superadministrador debe integrar `equipo-6/notificaciones`, reconstruir las
imágenes Java y Nginx del proyecto y configurar el entorno del **backend**.
Las credenciales se suministran mediante secretos del servicio de contenedores,
por ejemplo referencias a secretos en Azure Container Apps.

```dotenv
SPRING_PROFILES_ACTIVE=prod,smtp-production
SPRING_MAIL_HOST=smtp-relay.brevo.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=usuario-smtp-indicado-por-brevo
SPRING_MAIL_PASSWORD=clave-smtp-del-entorno
SAR_MAIL_FROM=remitente-verificado@dominio.example
SMTP_AUTH=true
SMTP_STARTTLS_ENABLE=true
SMTP_STARTTLS_REQUIRED=true
SMTP_SSL_ENABLE=false
SMTP_TEST_CONNECTION=true
SMTP_ALLOW_PLAINTEXT=false
SMTP_CONNECTION_TIMEOUT_MS=5000
SMTP_READ_TIMEOUT_MS=5000
SMTP_WRITE_TIMEOUT_MS=5000
SESSION_COOKIE_SECURE=true
SAR_LOAD_DEV_SEED=false
DB_URL=jdbc:oracle:thin:@//servidor-oracle:1521/servicio
DB_USER=cuenta-tecnica-del-backend
DB_PASSWORD=secreto-oracle
```

Conservar los demás perfiles y controles de seguridad acordados por el equipo
integrador. Configurar HTTPS, proxy de confianza y salida TCP hacia Brevo:587.
Si se conserva el bloqueo de IP en Brevo, autorizar la salida estable de Azure.
El Dockerfile de esta rama permite que Spring lea `SPRING_PROFILES_ACTIVE`
directamente del entorno. Se retiró el argumento que transmitía literalmente
`${SPRING_PROFILES_ACTIVE}` desde el ENTRYPOINT de tipo exec, donde Docker no
expande variables. Comprobar los perfiles activos en el registro de arranque.

El perfil `smtp-production` valida host, remitente, autenticación y TLS al
arrancar; con `SMTP_TEST_CONNECTION=true` también verifica la conexión SMTP.
Esta comprobación no envía un correo ni verifica por sí sola la entrega final.
La guía [NOTIFICACIONES.md](../NOTIFICACIONES.md) incluye la alternativa Compose.

### Oracle 12c y responsabilidades del equipo de base de datos

La demo funciona sin Oracle. El proyecto completo necesita una base preparada
por el equipo 7 y estas condiciones verificadas en el servidor real:

- `DB_URL` con el servicio correcto y acceso desde el contenedor Java.
- Cuenta técnica con permisos de ejecución de los paquetes consolidados.
- `PKG_ADMINISTRADOR.consultar_correo_solicitud` compilado y accesible, con el
  correo del aspirante relacionado por ficha, periodo y año.
- Versión exacta de Oracle 12c. El código integrado usa `JSON_TABLE`, disponible
  desde 12.1.0.2; confirmar esa versión o una posterior y probar los paquetes.
- Migraciones revisadas para esa base existente. El inicializador Docker del
  repositorio está orientado a Oracle XE 21c/XEPDB1 y no debe ejecutarse sin
  adaptación contra la base compartida de producción.

La integración de la rama pasa las pruebas Java sin base de datos. La
compatibilidad del conjunto con Oracle 12c y el flujo completo de Azure
requieren una prueba con esa instancia; no se han certificado solo con la demo.

## Verificación del proyecto integrado

Con datos ficticios y buzones autorizados, probar aprobación, rechazo con
motivo, aplicación y cancelación. Verificar estado e historial en Oracle y
correo correspondiente. Probar sesión, rol administrativo y CSRF. Confirmar
también el envío de OTP si el encargado activa `SAR_OTP_ENABLED=true`.

Al fallar SMTP, la decisión ya registrada se conserva y el backend registra el
fallo. El servicio actual no incluye cola persistente ni reintentos durables.
El correo de estudiantes aún necesita una fuente institucional acordada; la
consulta de destinatario implementada cubre aspirantes. El comentario de
Aplicado se incluye en el correo, pero su persistencia en el historial Oracle
requiere ampliar esa operación.

## Verificación de esta entrega

- Vercel publicó la página, la plantilla compartida y la API con HTTPS.
- Brevo aceptó dos pruebas desde Vercel: Rechazado con comentario y Aplicado
  sin comentario, dirigidas únicamente al buzón autorizado por el equipo.
- La API devolvió 401 para clave incorrecta, 403 para destinatario ajeno y 400
  para rechazo sin motivo.
- La interfaz se comprobó en pantalla estrecha y escritorio (1280 px), sin
  desbordamiento horizontal ni errores de consola.
- La configuración de Compose de producción se validó con `config --quiet`.
- Pasaron 97 pruebas Java sin integración Oracle y 6 pruebas de la demo Node.

La aceptación SMTP de ambas pruebas está comprobada. También se confirmó en
Gmail la recepción del aviso Rechazado con su comentario y plantilla. La imagen Docker final y el recorrido con Oracle 12c se validan en el
entorno de Azure del superadministrador. La demo fue publicada por CLI; la
conexión automática GitHub–Vercel necesita permisos del propietario del
repositorio.

## Mantenimiento y pruebas

```bash
npm run test:demo
mvn '-Dtest=!*IntegrationTest' test
```

La demo verifica los cuatro estados, el comentario obligatorio, el escape HTML,
la lista de destinatarios, autenticación y manejo de errores SMTP. Las pruebas
Java de integración requieren Oracle y se ejecutan aparte según
[PRUEBAS.md](../PRUEBAS.md). Mantener la plantilla canónica y reconstruir ambos
despliegues cuando cambie. No copiar secretos a JavaScript del navegador, a
commits, capturas, grabaciones ni documentación pública.

Referencias de configuración: [SMTP de Brevo](https://developers.brevo.com/docs/smtp-integration),
[bloqueo de IP de Brevo](https://help.brevo.com/hc/en-us/articles/5740111683858),
[IP de salida de Vercel](https://vercel.com/docs/networking/static-ips) y
[variables de Azure Container Apps](https://learn.microsoft.com/en-us/azure/container-apps/environment-variables).
