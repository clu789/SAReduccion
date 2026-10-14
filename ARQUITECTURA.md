# Arquitectura de SAReduccion

Documento técnico del estado del repositorio. La guía de ejecución está en [README.md](README.md); el detalle de tablas y relaciones está en [BaseDeDatos.md](BaseDeDatos.md).

## Arquitectura general

```text
Navegador
  └── Nginx :8081
       ├── HTML, CSS y JavaScript estáticos
       └── /api/ -> backend :8080 (proxy)
                         └── JDBC -> Oracle :1521/XEPDB1
```

Los servicios se comunican por la red de Docker Compose `sareduccion-net`:

| Servicio | Imagen/tecnología | Puerto host | Función |
| --- | --- | ---: | --- |
| `oracle-db` | `gvenzl/oracle-xe:21-slim` | `1522` | Oracle XE 21c; escucha en `1521` dentro de Docker. |
| `backend` | Spring Boot 2.7.18, Java 8 | `8080` | API prevista; se activa con el perfil Compose `full-stack`. |
| `frontend` | `nginx:alpine` | `8081` | Sirve los módulos estáticos y redirige `/api/` al backend. |

Nginx monta `modulo-solicitante/` y `modulo-administrativo/`. La raíz pública redirige al login de solicitante. El backend conecta al servicio `oracle-db`, no a `localhost`, con `jdbc:oracle:thin:@//oracle-db:1521/XEPDB1`.

## Backend

El proyecto usa Spring Boot `2.7.18` y Java `8`. `pom.xml` declara Spring Web, Spring JDBC, `ojdbc8` en tiempo de ejecución y Spring Boot Test. HikariCP está incluido por Spring Boot JDBC; `application.properties` configura `HikariDataSource` y el pool `SAReduccionOraclePool`. Las credenciales y URL se reciben mediante `DB_URL`, `DB_USER` y `DB_PASSWORD`.

La versión de `ojdbc8` no está fijada directamente en el POM: la administra el parent de Spring Boot. El backend Java contiene actualmente solo `SareduccionApplication`; no hay controladores REST, servicios, repositorios JDBC, autenticación, autorización ni `HttpSession` implementados. Spring Web y JDBC son dependencias disponibles, no evidencia de endpoints funcionales.

El frontend ya envía peticiones `fetch` a `/api` con credenciales del mismo origen, pero algunas llamadas siguen simuladas. Los contratos descritos en los READMEs de módulos son expectativas, no endpoints que deban considerarse disponibles.

## Oracle, cuentas y permisos

Oracle XE 21c usa el PDB existente `XEPDB1` y el volumen nombrado `sareduccion_oracle_data`.

| Cuenta | Uso |
| --- | --- |
| `SA_REDUCCION` | Propietario del esquema y de los paquetes; se usa para instalación y actualización, no como conexión habitual del backend. |
| `SAR_BACKEND` | Cuenta técnica JDBC. El modelo de permisos le concede `CREATE SESSION` y `EXECUTE` solo sobre los paquetes autorizados, sin privilegios CRUD directos sobre tablas. |
| `ALU_21281181` | Cuenta Oracle individual de prueba de estudiante y titular del rol `ROL_SAR_ESTUDIANTE_SIM`. |
| `SA01` | Cuenta Oracle individual de prueba de administrador y titular del rol `ROL_SAR_ADMIN_SIM`. |

El propietario crea el esquema y sus objetos. Los roles de estudiante y administrador permiten iniciar sesión Oracle, pero el script actualizado retira de ellos los grants `EXECUTE` de los paquetes. Los dos perfiles se ejecutarán a través del backend técnico después de autenticar al usuario.

### Paquetes PL/SQL

Los paquetes `SA_REDUCCION.PKG_ESTUDIANTE` y `SA_REDUCCION.PKG_ADMINISTRADOR` usan `AUTHID DEFINER`. Sus firmas reciben la identidad final como parámetro y no usan `SESSION_USER` para decidir qué usuario de aplicación actúa.

`PKG_ESTUDIANTE` recibe `p_numero_control` en `mis_datos`, `mis_solicitudes`, `mis_deducciones`, `crear_solicitud` y `guardar_respuesta`. Valida que exista en `Estudiantes.Numero_Control`; las consultas y escrituras de solicitudes/respuestas se limitan a ese estudiante.

`PKG_ADMINISTRADOR` recibe `p_id_administrador` en `listar_estudiantes`, `listar_solicitudes`, `listar_respuestas`, `listar_deducciones`, `crear_solicitud_estudiante`, `cambiar_estatus_solicitud`, `autorizar_deduccion` y `cambiar_estatus_deduccion`. Valida que exista en `Administradores.idAdministrador` antes de cada operación.

Los parámetros son identificadores, no credenciales ni pruebas independientes de autenticación. El backend debe tomarlos exclusivamente del principal de una sesión autenticada, comprobar el perfil para cada endpoint y no aceptar esos valores desde una petición del cliente.

## Autenticación y sesiones

La primera versión para estudiantes y administradores está implementada en Spring Boot:

1. `POST /api/auth/login` valida las credenciales mediante una conexión JDBC temporal (`DriverManager`), comprueba `SESSION_USER` y `SESSION_ROLES`, y no usa el pool `SAR_BACKEND` para validar contraseñas.
2. La regla de estudiante valida `ALU_<Numero_Control>` y comprueba el perfil mediante `PKG_ESTUDIANTE.mis_datos`. La regla administrativa relaciona el username con `idAdministrador` y valida el perfil llamando `PKG_ADMINISTRADOR.listar_estudiantes`; el cursor de listado se cierra sin devolver filas.
3. La sesión conserva el principal (usuario Oracle, identidad de perfil y rol) y un token CSRF; nunca conserva la contraseña. El ID de sesión se rota tras el login.
4. `GET /api/auth/me` devuelve la identidad propia de la sesión; para una sesión anónima devuelve `401` y un token CSRF para iniciar el flujo.
5. `POST /api/auth/logout` exige el token CSRF y luego invalida la sesión.

Las respuestas distinguen de forma genérica autenticación/perfil inválido, CSRF inválido y servicio temporalmente no disponible, sin revelar errores Oracle. La cookie de sesión es `HttpOnly` y `SameSite=Lax`; `SESSION_COOKIE_SECURE` vale `false` localmente y debe configurarse como `true` tras HTTPS en producción. Las operaciones `POST` requieren `X-CSRF-TOKEN` obtenido de `/me`.

La primera integración de autenticación de estudiante y administrador se probó contra las cuentas existentes. Aún no se implementan los endpoints de negocio ni la autorización de operaciones fuera de estas rutas de autenticación. La autenticación de aspirantes sigue pendiente; la tabla `Aspirantes` contiene `ficha`, `Curp`, `periodo` y `anio`, y la convocatoria define sus fechas.

## Inicialización y actualización de Oracle

`docker-compose.yml` monta `docker/init-db/init-db.sh` como el inicializador `00-init-db.sh` de la imagen Oracle. En un volumen vacío, el orquestador ejecuta:

1. `01-configurar-esquema.sql`: crea/configura `SA_REDUCCION` como SYS en `XEPDB1`.
2. `02-crear-estructura.sql`: crea tablas, secuencias, triggers, relaciones y catálogos como `SA_REDUCCION`.
3. `03-crear-identidades-roles.sql`: crea roles y cuentas individuales como SYS.
4. `05-crear-cuenta-tecnica.sql`: crea/verifica `SAR_BACKEND` y concede `CREATE SESSION` como SYS.
5. `04-crear-paquetes-permisos.sql`: crea los paquetes y concede sus permisos como `SA_REDUCCION`.

El inicializador comprueba los objetos y privilegios, y deja un marcador de finalización para el healthcheck. La imagen ejecuta la inicialización automática solo cuando Oracle inicializa un volumen vacío; modificar SQL o `.env` no migra automáticamente la base existente.

Para bases ya inicializadas, `05-crear-cuenta-tecnica.sql` y `migrate-backend.sh` crean/verifican `SAR_BACKEND` sin cambiar la contraseña de una cuenta existente y vuelven a instalar los paquetes/grants; el migrador se detiene si detecta privilegios adicionales que requieren revisión. La migración se aplicó a la base existente:

```bash
docker exec sareduccion-oracle bash /opt/sareduccion/init-db/migrate-backend.sh
```

Se conservaron el contenedor y el volumen. No se ejecutó `docker compose down -v` ni se recreó la base.

El destino futuro es Oracle 12c, pero ni los scripts ni el driver han sido validados aún contra la versión exacta de 12c prevista.

## Estado del desarrollo

### Implementado y validado

- La prueba `database/tests/05-verificar-instalacion.sql`: 21 tablas, 15 secuencias, 22 triggers, 2 paquetes y 2 cuerpos válidos, sin objetos inválidos.
- La migración de `SAR_BACKEND` y la prueba `database/tests/06-probar-permisos.sql`: `CREATE SESSION`, `EXECUTE` en ambos paquetes, sin CRUD directo, privilegios administrativos ni grants antiguos a roles individuales.
- `EstudianteRepository.misDatos()` invoca `PKG_ESTUDIANTE.mis_datos` con `CallableStatement`, parámetros enlazados y lectura de `SYS_REFCURSOR`. `EstudianteRepositoryIntegrationTest` pasó usando `SAR_BACKEND` desde Maven/Java 8 en la red Docker y verificó los campos devueltos.
- Login, `/me` y logout para las cuentas existentes `ALU_21281181` y `SA01`: autenticación Oracle temporal, comprobación de roles/perfiles, rotación de sesión y protección CSRF.
- Se conserva el estudiante ficticio `PRUEBA_JDBC_20261014` para pruebas posteriores.
- Oracle XE sigue `healthy` y el volumen persistente continúa montado.

### Pendiente de desarrollar

- Integrar el frontend con los endpoints de autenticación y enviar/retener el token CSRF.
- Aplicar autorización por perfil a futuros endpoints de negocio.
- Capa JDBC para las operaciones restantes de los paquetes y manejo de cursores/transacciones.
- Autenticación de aspirantes.
- Pruebas negativas de autenticación/CSRF y compatibilidad con la versión objetivo de Oracle 12c.
- Pruebas de compatibilidad con la versión objetivo de Oracle 12c.

## Diagnóstico rápido

| Síntoma | Comprobación o causa probable |
| --- | --- |
| `backend` no aparece | Es un servicio del perfil `full-stack`; iniciar con `docker compose --profile full-stack up -d --build`. |
| Oracle no está `healthy` | Consultar `docker compose logs --tail=150 oracle-db`; el healthcheck espera que termine la inicialización. |
| Spring no conecta con Oracle | Usar el host de red `oracle-db:1521`, no `localhost:1521`; revisar `DB_URL`, `DB_USER` y los logs del backend. |
| `ORA-01017` | Verificar la cuenta y credencial configuradas. La inicialización no cambia las contraseñas de usuarios ya existentes. |
| `ORA-00942` o `ORA-01031` | Comprobar esquema, objeto y grants necesarios; no conceder CRUD directo a `SAR_BACKEND`. |
| Frontend `Exited` | Revisar `docker compose logs frontend` y el montaje de `docker/nginx/nginx.conf`. |
| Ruta `/api` devuelve `404` | Actualmente no hay controladores REST implementados; no es evidencia de un login funcional. |

Para inspeccionar servicios y logs: `docker compose ps -a`, `docker compose logs backend` y `docker compose logs frontend`.

## Documentación relacionada

- [README de ejecución](README.md)
- [Modelo detallado de base de datos](BaseDeDatos.md)
- [README del módulo administrativo](modulo-administrativo/README.md)
- [README del módulo solicitante](modulo-solicitante/README.md)
