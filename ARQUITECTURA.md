# Arquitectura de SAReduccion

Documento técnico del estado del repositorio. La guía de ejecución está en [README.md](README.md); el detalle de tablas y relaciones está en [BaseDeDatos.md](BaseDeDatos.md).

## Arquitectura general

```text
Navegador
  └── Nginx :8081 (FRONTEND_PORT)
       ├── HTML, CSS y JavaScript estáticos (módulos solicitante y administrativo)
       └── /api/ -> backend :8080 (proxy inverso)
                         ├── JDBC (pool HikariCP) -> Oracle :1521/XEPDB1  (operaciones de aplicación)
                         └── JDBC temporal (DriverManager) -> Oracle :1521/XEPDB1  (solo validación de contraseña)
```

La aplicación se compone de tres capas desplegadas con Docker Compose:

| Capa | Tecnología | Función |
| --- | --- | --- |
| Frontend | HTML, CSS y JavaScript estáticos servidos por Nginx | Formularios, validación en cliente y consumo de la API. |
| Backend | Spring Boot 2.7.18 sobre Java 8, Spring Web y Spring JDBC | API REST de autenticación, gestión de sesión y acceso a Oracle. |
| Datos | Oracle XE 21c (`gvenzl/oracle-xe:21-slim`) | Esquema `SA_REDUCCION`, paquetes PL/SQL y datos. |

## Contenedores y servicios Docker Compose

Los servicios se comunican por la red de Docker Compose `sareduccion-net`:

| Servicio | Imagen/tecnología | Puerto host | Función |
| --- | --- | ---: | --- |
| `oracle-db` | `gvenzl/oracle-xe:21-slim` | `1522` (`ORACLE_PORT`) | Oracle XE 21c; escucha en `1521` dentro de Docker. Persiste en el volumen `sareduccion_oracle_data`. |
| `backend` | Spring Boot 2.7.18, Java 8 | `8080` (`APP_PORT`) | API REST; se activa con el perfil Compose `full-stack`. |
| `frontend` | `nginx:alpine` | `8081` (`FRONTEND_PORT`) | Sirve los módulos estáticos y redirige `/api/` al backend. |

Los puertos del anfitrión se configuran únicamente en `.env` mediante `ORACLE_PORT`, `APP_PORT` y `FRONTEND_PORT`, sin editar `docker-compose.yml`. Los valores de referencia son los de [`.env.example`](.env.example): `1522`, `8080` y `8081`. Un cambio de puerto solo altera la exposición hacia el anfitrión; la comunicación interna entre contenedores sigue usando `1521` (Oracle) y `8080` (backend) dentro de `sareduccion-net`.

`oracle-db` no depende del perfil y arranca por sí solo. `backend` y `frontend` pertenecen al perfil `full-stack` y se levantan con `docker compose --profile full-stack up -d`. `backend` espera a que `oracle-db` esté `healthy` (`condition: service_healthy`).

Nginx monta `modulo-solicitante/` y `modulo-administrativo/` como volúmenes de solo lectura. La raíz pública redirige al login de solicitante. El backend conecta al servicio `oracle-db`, no a `localhost`, con `jdbc:oracle:thin:@//oracle-db:1521/XEPDB1`.

## Comunicación entre frontend, backend y base de datos

El navegador nunca habla directamente con Oracle ni con el backend en otro origen: todas las peticiones van al mismo origen de Nginx, que resuelve `/api/` contra el servicio `backend`.

| Tramo | Protocolo | Detalle |
| --- | --- | --- |
| Navegador → Nginx | HTTP | Recursos estáticos del módulo correspondiente. |
| Navegador → Nginx → Backend | HTTP/JSON | `fetch` con `credentials: 'same-origin'`, de modo que la cookie de sesión viaja siempre. |
| Backend → Oracle | JDBC | Pool `SAReduccionOraclePool` (HikariCP) para las operaciones de aplicación; conexión temporal para validar contraseñas. |

El proxy inverso evita CORS porque el frontend y la API comparten origen. El frontend usa una base común `/api` (`Config.apiBase`) y todas sus llamadas pasan por el wrapper `Http`.

## Backend

El proyecto usa Spring Boot `2.7.18` y Java `8`. `pom.xml` declara Spring Web, Spring JDBC, `ojdbc8` en tiempo de ejecución y Spring Boot Test. HikariCP está incluido por Spring Boot JDBC; `application.properties` configura `HikariDataSource` y el pool `SAReduccionOraclePool`. Las credenciales y URL se reciben mediante `DB_URL`, `DB_USER` y `DB_PASSWORD`, que corresponden a la cuenta técnica `SAR_BACKEND`.

La versión de `ojdbc8` no está fijada directamente en el POM: la administra el parent de Spring Boot.

Paquetes implementados:

| Paquete | Contenido |
| --- | --- |
| `com.itt.sareduccion` | `SareduccionApplication`. |
| `com.itt.sareduccion.controller` | `AuthController`: endpoints de autenticación y sesión. |
| `com.itt.sareduccion.auth` | `AuthenticationService`, `OracleCredentialValidator`, `LoginAttemptLimiter`, `AuthRole`, `SessionPrincipal`, `AuthenticatedPrincipal`, `ApplicantPrincipal`. |
| `com.itt.sareduccion.repository` | `EstudianteRepository`, `AdministradorRepository`, `ApplicantRepository`: invocación de los paquetes PL/SQL con `CallableStatement`. |
| `com.itt.sareduccion.model` | `EstudianteDatos`, `ApplicantIdentity`. |

La API expone únicamente rutas de autenticación. No existen todavía endpoints de negocio (solicitudes, respuestas, deducciones); el frontend sigue mostrando datos simulados en esas vistas.

## Oracle, cuentas y permisos

Oracle XE 21c usa el PDB existente `XEPDB1` y el volumen nombrado `sareduccion_oracle_data`.

| Cuenta | Uso |
| --- | --- |
| `SA_REDUCCION` | Propietario del esquema y de los paquetes; se usa para la instalación y para el respaldo y la restauración lógicos, no como conexión habitual del backend. |
| `SAR_BACKEND` | Cuenta técnica JDBC del backend. |
| `ALU_21281181` | Cuenta Oracle individual de prueba de estudiante y titular del rol `ROL_SAR_ESTUDIANTE_SIM`. |
| `SA01` | Cuenta Oracle individual de prueba de administrador y titular del rol `ROL_SAR_ADMIN_SIM`. |

El propietario crea el esquema y sus objetos. Los roles de estudiante y administrador permiten iniciar sesión Oracle, pero el script de paquetes retira de ellos los grants `EXECUTE`: ambos perfiles operan a través de `SAR_BACKEND` después de que el backend autentica al usuario.

### Privilegios mínimos de la cuenta técnica

`SAR_BACKEND` no tiene privilegios CRUD ni administrativos. Su modelo de permisos es:

| Tipo | Privilegio |
| --- | --- |
| Sistema | `CREATE SESSION`. |
| Objeto | `EXECUTE` sobre `SA_REDUCCION.PKG_ESTUDIANTE`, `SA_REDUCCION.PKG_ADMINISTRADOR` y `SA_REDUCCION.PKG_ASPIRANTE`. |
| Directorio | Ninguno. |

No dispone de `SELECT`, `INSERT`, `UPDATE` ni `DELETE` directos sobre las tablas, ni de roles adicionales. Todo el acceso a datos ocurre a través de los paquetes.

### Paquetes PL/SQL

Los paquetes `SA_REDUCCION.PKG_ESTUDIANTE`, `SA_REDUCCION.PKG_ADMINISTRADOR` y `SA_REDUCCION.PKG_ASPIRANTE` usan `AUTHID DEFINER`. Sus firmas reciben la identidad como parámetro y no usan `SESSION_USER` para decidir qué usuario de aplicación actúa.

`PKG_ESTUDIANTE` recibe `p_numero_control` en `mis_datos`, `mis_solicitudes`, `mis_deducciones`, `crear_solicitud` y `guardar_respuesta`. Valida que exista en `Estudiantes.Numero_Control`; las consultas y escrituras de solicitudes/respuestas se limitan a ese estudiante.

`PKG_ADMINISTRADOR` recibe `p_id_administrador` en `listar_estudiantes`, `listar_solicitudes`, `listar_respuestas`, `listar_deducciones`, `crear_solicitud_estudiante`, `cambiar_estatus_solicitud`, `autorizar_deduccion` y `cambiar_estatus_deduccion`. Valida que exista en `Administradores.idAdministrador` antes de cada operación.

`PKG_ASPIRANTE` expone `validar_aspirante`, único subprograma del paquete:

```text
PKG_ASPIRANTE.validar_aspirante(
  p_ficha     IN  VARCHAR2,
  p_curp      IN  VARCHAR2,
  p_ficha_out OUT VARCHAR2,
  p_periodo   OUT NUMBER,
  p_anio      OUT NUMBER
)
```

Comprueba la coincidencia de `ficha` y `CURP` en `Aspirantes`, restringe la búsqueda al año vigente y devuelve la clave completa del registro. Errores de aplicación que produce:

| Código | Significado |
| --- | --- |
| `-20840` | Parámetros nulos o vacíos. |
| `-20841` | Sin coincidencias. |
| `-20842` | Coincidencia ambigua (varios periodos para la misma ficha). |

Los parámetros de los paquetes son identificadores, no credenciales ni pruebas independientes de autenticación. El backend debe tomarlos exclusivamente del principal de una sesión autenticada, comprobar el perfil para cada endpoint y no aceptar esos valores desde una petición del cliente.

## Autenticación y sesiones

### Estudiantes y administradores (cuentas Oracle)

1. `POST /api/auth/login` valida las credenciales mediante una conexión JDBC temporal (`DriverManager`), comprueba `SESSION_USER` y `SESSION_ROLES`, y no usa el pool `SAR_BACKEND` para validar contraseñas.
2. La regla de estudiante valida `ALU_<Numero_Control>` y comprueba el perfil mediante `PKG_ESTUDIANTE.mis_datos`. La regla administrativa relaciona el username con `idAdministrador` y valida el perfil llamando `PKG_ADMINISTRADOR.listar_estudiantes`; el cursor de listado se cierra sin devolver filas.
3. Una cuenta debe tener exactamente uno de los dos roles (`ROL_SAR_ESTUDIANTE_SIM` o `ROL_SAR_ADMIN_SIM`); con ambos o con ninguno se rechaza.
4. La sesión conserva el principal (usuario Oracle, identidad de perfil y rol) y un token CSRF; nunca conserva la contraseña. El ID de sesión se rota tras el login.

### Aspirantes (autenticación provisional)

La autenticación de aspirantes no usa contraseña ni cuenta Oracle individual. El aspirante acredita su identidad con dos datos de su registro de nuevo ingreso que el backend nunca persiste en sesión:

| Dato | Origen | Validación |
| --- | --- | --- |
| `ficha` | Identificador de registro del aspirante. | Patrón `^[A-Z0-9]{1,18}$` en backend; coincidencia exacta en la tabla. |
| `curp` | Clave Única de Registro de Población. | Patrón `^[A-Z0-9]{18}$` en backend; coincidencia exacta en la tabla. |

La CURP se normaliza a mayúsculas, se envía solo en el cuerpo de la petición de login y **no se almacena en la sesión ni se devuelve en ninguna respuesta**. El `ApplicantPrincipal` resultante contiene únicamente `ficha`, `periodo` y `anio`.

### Diferencias entre ambos modelos

| Aspecto | Cuentas Oracle (estudiante/administrador) | Aspirante (provisional) |
| --- | --- | --- |
| Credencial | Usuario y contraseña de una cuenta Oracle individual. | Ficha y CURP sin contraseña. |
| Verificación | `DriverManager` + `SESSION_USER`/`SESSION_ROLES`. | `PKG_ASPIRANTE.validar_aspirante` a través de `SAR_BACKEND`. |
| Identidad en sesión | Usuario Oracle + identidad de perfil. | Ficha + periodo + año. |
| Rol de sesión | `STUDENT` o `ADMIN`. | `APPLICANT`. |
| Requiere cuenta Oracle | Sí, una por persona. | No. |
| Estado | Estable dentro del alcance actual. | **Provisional**: sustituible por verificación por correo electrónico. |

La autenticación de aspirantes es una medida temporal para habilitar el acceso durante el desarrollo. El riesgo es que ficha y CURP son datos semiestables que pueden conocerse sin ser secretos; por eso se limita el número de intentos y se prevé migrar a verificación por correo electrónico con código de un solo uso.

### Flujo de `POST /api/auth/login/aspirante`

```text
Cliente                         AuthController                 Oracle
  │  POST /api/auth/login/aspirante
  │  X-CSRF-TOKEN: <token>  ─────────►│
  │  { "ficha": ..., "curp": ... }    │
  │                                   │ 1. Valida CSRF contra la sesión
  │                                   │ 2. clearPrincipal(session)
  │                                   │ 3. Normaliza ficha y CURP a mayúsculas
  │                                   │ 4. Consulta LoginAttemptLimiter (IP|ficha)
  │                                   │ 5. Valida patrones de ficha y CURP
  │                                   │ 6. authenticateApplicant ───► PKG_ASPIRANTE
  │                                   │                              .validar_aspirante
  │                                   │ ◄─── ficha, periodo, anio ───
  │                                   │ 7. reset(limiterKey)
  │                                   │ 8. changeSessionId()
  │                                   │ 9. Guarda ApplicantPrincipal
  │  ◄── 200 { role: "APPLICANT", periodo, anio, csrfToken }  │
```

Reglas del flujo:

1. Se exige la cabecera CSRF; si falta o no coincide se responde `403` sin consultar Oracle.
2. Un intento nuevo descarta cualquier identidad previa de la sesión, de modo que un fallo no deja una sesión autenticada en pie.
3. La clave del limitador es `IP + '|' + ficha`, así que un bloqueo por intentos fallidos de una ficha no afecta a otras.
4. El año vigente y la unicidad del registro los determina Oracle con `EXTRACT(YEAR FROM SYSDATE)`; el cliente nunca envía año ni periodo.
5. En éxito se rota el ID de sesión antes de guardar el principal.

Códigos de respuesta:

| Código | Situación |
| --- | --- |
| `200` | Identidad verificada; devuelve rol, ficha, periodo, año y token CSRF. |
| `401` | Ficha o CURP sin coincidencia, parámetros nulos, o formato inválido. |
| `403` | Token CSRF ausente o incorrecto. |
| `429` | Límite de intentos superado. |
| `503` | Oracle no disponible. |

### HttpSession, cookies, CSRF y roles

| Mecanismo | Implementación |
| --- | --- |
| Sesión | `HttpSession` de contenedor, creada por Spring Web; no hay tokens en `localStorage` ni en `sessionStorage`. |
| Rotación de ID | `request.changeSessionId()` tras cada login correcto (protección ante fijación de sesión). |
| Expiración | `server.servlet.session.timeout=30m`. |
| Cookie | `JSESSIONID` con `HttpOnly` y `SameSite=Lax`; `SESSION_COOKIE_SECURE` vale `false` en local y debe ser `true` tras HTTPS. |
| CSRF | Token aleatorio de 32 bytes (Base64 URL) guardado en la sesión y comparado con `MessageDigest.isEqual`. Se obtiene de `GET /api/auth/me`, se devuelve incluso en el `401` anónimo, y se envía en la cabecera `X-CSRF-TOKEN` en `POST /api/auth/login`, `POST /api/auth/login/aspirante` y `POST /api/auth/logout`. |
| Roles | Enumeración `AuthRole` con `STUDENT`, `ADMIN` y `APPLICANT`, expuesta como cadena en las respuestas. |

`GET /api/auth/me` devuelve la identidad de la sesión propia. Para un visitante anónimo responde `401` con el token CSRF necesario para iniciar el flujo. Para un aspirante devuelve `authenticated`, `identity` (la ficha), `role`, `periodo`, `anio` y `csrfToken`, sin CURP ni correo. `POST /api/auth/logout` exige CSRF e invalida la sesión, devolviendo `204`.

Las respuestas distinguen de forma genérica autenticación/perfil inválido, CSRF inválido y servicio temporalmente no disponible, sin revelar errores Oracle.

## Redirección y protección de las interfaces según el rol

El frontend aplica el control de acceso en cliente con el objeto `AuthSession`, que centraliza el destino de cada rol:

| Rol | Interfaz |
| --- | --- |
| `STUDENT` | `panel-estatus.html` |
| `APPLICANT` | `panel-aspirante.html` |
| `ADMIN` | `/administrativo/panel-admin.html` |

Reglas aplicadas:

1. **Páginas protegidas** (`panel-estatus.html`, `panel-aspirante.html`, `formulario.html`) llaman `AuthSession.requireRole(rolEsperado, 'login.html')`. Sin sesión redirigen al login; con un rol distinto redirigen al panel propio de ese rol.
2. **Página de login** llama `AuthSession.redirectByRole(...)`: si ya hay sesión activa, envía al panel del rol correspondiente sin mostrar el formulario.
3. **Post-login**, cada flujo redirige explícitamente al panel del rol que el backend confirmó.
4. El cierre de sesión (`AuthSession.logout`) obtiene un CSRF vigente, invoca `/api/auth/logout` y vuelve al login.

Esta protección es de experiencia de usuario, no de seguridad: la autoridad real está en el backend, que valida la sesión en cada petición. Los endpoints de negocio futuros deberán comprobar el rol en servidor y no confiar en el valor que el cliente haya enviado.

## Inicialización de Oracle

`docker-compose.yml` monta `docker/init-db/init-db.sh` como el inicializador `00-init-db.sh` de la imagen Oracle. En un volumen vacío, el orquestador ejecuta:

1. `01-configurar-esquema.sql`: crea/configura `SA_REDUCCION` como SYS en `XEPDB1`.
2. `02-crear-estructura.sql`: crea tablas, secuencias, triggers, relaciones, índices y catálogos como `SA_REDUCCION`.
3. `03-crear-identidades-roles.sql`: crea roles y cuentas individuales como SYS.
4. `04-crear-cuenta-tecnica.sql`: crea/verifica `SAR_BACKEND` y concede `CREATE SESSION` como SYS.
5. `05-crear-paquetes-permisos.sql`: crea los paquetes y concede sus permisos como `SA_REDUCCION`.
6. `06-seed-desarrollo.sql`: datos ficticios de desarrollo, solo si `SAR_LOAD_DEV_SEED` está activo.

El nombre de cada archivo coincide con su número de orden. Los scripts 01, 03 y 04 se ejecutan como SYS; 02, 05 y 06 como `SA_REDUCCION`, porque los paquetes deben pertenecer a ese usuario para que `AUTHID DEFINER` quede ligado al propietario y no a SYS.

El orquestador es necesario porque la imagen ejecuta los `.sql` de `/container-entrypoint-initdb.d` como SYS y sin variables de sustitución: `01`, `03`, `04` y `05` requieren variables (`ORACLE_APP_USER`, `ORACLE_BACKEND_USER`, contraseñas) que, sin definir, SQL*Plus pediría por teclado y colgaría el arranque. Por eso los SQL se montan en una ruta aparte (`/opt/sareduccion/init-db`) y solo el orquestador va al directorio de auto-inicialización.

Sobre los datos de desarrollo: `06-seed-desarrollo.sql` se carga únicamente cuando `SAR_LOAD_DEV_SEED` vale `true` (valor por defecto en desarrollo). Al fijarlo en `false` la base queda con el esquema completo y los catálogos, pero sin datos de aplicación. La verificación final del orquestador comprueba que la presencia o ausencia de datos corresponda al valor de esa variable. El detalle de los scripts y de su orden está en [BaseDeDatos.md](BaseDeDatos.md).

El inicializador comprueba los objetos, los privilegios y el estado de los datos, y deja el marcador `/opt/oracle/oradata/.sareduccion-init.complete`, que el healthcheck exige.

**La inicialización automática se ejecuta una sola vez**, cuando la imagen crea la base sobre un volumen vacío. Reiniciar o recrear los contenedores sin borrar el volumen no vuelve a ejecutar los scripts, y editar un `.sql` o una variable de `.env` tampoco actualiza una base ya inicializada.

Para aplicar un cambio de estructura o de paquetes sobre una base con información que deba conservarse hacen falta cambios controlados —o scripts de migración incrementales aplicados manualmente—; el proyecto **aún no implementa un mecanismo de migraciones**. La recreación desde cero (`docker compose down -v`) es una operación destructiva que borra `sareduccion_oracle_data` y no forma parte del flujo habitual. El procedimiento y sus precauciones están en [BaseDeDatos.md](BaseDeDatos.md).

El destino futuro es Oracle 12c, pero ni los scripts ni el driver se han validado todavía contra una versión 12c concreta.

## Estado del desarrollo

### Implementado y validado

- Prueba `database/tests/05-verificar-instalacion.sql`: 21 tablas, 15 secuencias, 22 triggers, 33 índices, 3 paquetes y 3 cuerpos válidos, sin objetos inválidos.
- Configuración de `SAR_BACKEND` y prueba `database/tests/06-probar-permisos.sql`: `CREATE SESSION`, `EXECUTE` en los tres paquetes, sin CRUD directo, sin privilegios administrativos y sin grants de paquete a los roles individuales.
- `EstudianteRepository.misDatos()` invoca `PKG_ESTUDIANTE.mis_datos` con `CallableStatement`, parámetros enlazados y lectura de `SYS_REFCURSOR`.
- Login, `/me` y logout para las cuentas `ALU_21281181` y `SA01`: autenticación Oracle temporal, comprobación de roles/perfiles, rotación de sesión y protección CSRF.
- Autenticación de aspirantes con ficha y CURP (`PKG_ASPIRANTE` + `POST /api/auth/login/aspirante`), validada con HTTP real contra el backend desplegado: periodo y año devueltos, rechazo de CURP o ficha incorrectas, exigencia de CSRF, bloqueo por intentos y ausencia de CURP en la respuesta.
- Integración del frontend: pestañas de alumno y aspirante, manejo de CSRF y sesión, panel de aspirante y redirección por rol.
- Prueba `database/tests/07-probar-aspirantes.sql` cubre a nivel Oracle los escenarios que el cliente no puede provocar (año no vigente y coincidencias ambiguas entre periodos).

### Pendiente de desarrollar

- Autorización por perfil en los endpoints de negocio.
- Capa JDBC para las operaciones restantes de los paquetes y manejo de cursores/transacciones.
- Sustituir la autenticación provisional de aspirantes por verificación mediante correo electrónico con código de un solo uso.
- Persistir el rate limiting fuera de la memoria del proceso.
- Pruebas de compatibilidad con la versión objetivo de Oracle 12c.

### Limitaciones actuales

| Limitación | Descripción |
| --- | --- |
| Autenticación provisional de aspirantes | Ficha y CURP no son secretos fuertes; está previsto migrar a verificación por correo electrónico. |
| Rate limiting en memoria | `LoginAttemptLimiter` usa un mapa en memoria del proceso: se reinicia con el backend y no se comparte entre instancias. La clave es `IP + ficha`, por lo que la rotación de fichas desde una misma IP elude parcialmente el límite. |
| CURP no validada contra la fecha de nacimiento | La comprobación es de coincidencia exacta con el registro y de longitud, no de consistencia interna. |
| Sin verificación por correo | No existen todavía envío de correos, códigos de un solo uso ni recuperación de acceso. |
| Sin endpoints de negocio | Las vistas de solicitudes, respuestas y deducciones siguen operando con datos simulados. |
| Protección de rutas solo en cliente | La redirección por rol es de experiencia de usuario; el backend aún no autoriza operaciones de negocio. |
| Sin migraciones incrementales | Los scripts de `docker/init-db/` solo se aplican sobre un volumen vacío; actualizar una base existente exige cambios manuales o reinstalarla. |

## Diagnóstico rápido

| Síntoma | Comprobación o causa probable |
| --- | --- |
| `backend` no aparece | Es un servicio del perfil `full-stack`; iniciar con `docker compose --profile full-stack up -d --build`. |
| Oracle no está `healthy` | Consultar `docker compose logs --tail=150 oracle-db`; el healthcheck espera que termine la inicialización. |
| Spring no conecta con Oracle | Usar el host de red `oracle-db:1521`, no `localhost:1521`; revisar `DB_URL`, `DB_USER` y los logs del backend. |
| `ORA-01017` | Verificar la cuenta y credencial configuradas. La inicialización no cambia las contraseñas de usuarios ya existentes. |
| `ORA-00942` o `ORA-01031` | Comprobar esquema, objeto y grants necesarios; no conceder CRUD directo a `SAR_BACKEND`. |
| `403` en login o logout | Token CSRF ausente, vencido o de otra sesión; recargar la página para obtener uno nuevo desde `/me`. |
| `429` en login de aspirante | Límite de intentos superado para esa combinación de IP y ficha; esperar la ventana configurada. |
| `503` en login | Oracle no disponible o credenciales técnicas incorrectas. |
| Frontend `Exited` | Revisar `docker compose logs frontend` y el montaje de `docker/nginx/nginx.conf`. |

Para inspeccionar servicios y logs: `docker compose ps -a`, `docker compose logs backend` y `docker compose logs frontend`.

## Respaldo y persistencia de datos

La persistencia se apoya en el volumen nombrado `sareduccion_oracle_data` (`oracle_data` en Compose), montado en `/opt/oracle/oradata`. Al vivir fuera de la capa escribible del contenedor, sobrevive a `docker compose down`, a `docker rm` del contenedor y a la reconstrucción de la imagen; solo `down -v` o el borrado explícito del volumen lo destruyen.

Los datos se protegen en dos niveles independientes:

| Nivel | Qué protege | Limitación |
| --- | --- | --- |
| Volumen `sareduccion_oracle_data` | La instancia completa, lista para arrancar. | Vive en el mismo equipo. |
| Respaldo lógico `expdp` en `backups/oracle/` | Los objetos y datos del esquema `SA_REDUCCION`, portables a otra instancia. | No incluye cuentas, roles, grants ni la configuración de la instancia. |

El respaldo lógico escribe en `./backups/oracle` del anfitrión a través del bind mount `/opt/sareduccion/backups`, que corresponde al directorio Oracle `SAR_BACKUP_DIR`; el `.dmp` no requiere `docker cp` y la carpeta está excluida por `.gitignore`. El volumen **no sustituye** al respaldo.

Los comandos de exportación y restauración, la verificación por SHA-256 y el procedimiento de restauración en un entorno aislado están en [BaseDeDatos.md](BaseDeDatos.md).

## Documentación relacionada

- [README de ejecución](README.md)
- [Modelo detallado de base de datos](BaseDeDatos.md)
- [README del módulo administrativo](modulo-administrativo/README.md)
- [README del módulo solicitante](modulo-solicitante/README.md)
