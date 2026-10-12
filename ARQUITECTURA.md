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

## Autenticación y sesiones: pendiente

La arquitectura prevista es:

1. Validar inicialmente las credenciales Oracle del estudiante o administrador y determinar su perfil real.
2. Crear en Spring Boot una `HttpSession` con la identidad y el perfil, sin guardar la contraseña.
3. Autorizar cada operación según el perfil autenticado.
4. Ejecutar las operaciones posteriores con el pool JDBC de `SAR_BACKEND`, pasando al paquete la identidad tomada de la sesión.
5. Autenticar aspirantes por ficha, CURP, año y periodo vigente de convocatoria, sin crearles cuentas Oracle individuales.

Ninguno de esos flujos está implementado todavía. La tabla `Aspirantes` contiene `ficha`, `Curp`, `periodo` y `anio`, y la convocatoria define sus fechas; falta desarrollar y probar la validación del aspirante. También están pendientes los endpoints de login, consulta, operaciones de negocio y cierre de sesión.

## Inicialización y actualización de Oracle

`docker-compose.yml` monta `docker/init-db/init-db.sh` como el inicializador `00-init-db.sh` de la imagen Oracle. En un volumen vacío, el orquestador ejecuta:

1. `01-configurar-esquema.sql`: crea/configura `SA_REDUCCION` como SYS en `XEPDB1`.
2. `02-crear-estructura.sql`: crea tablas, secuencias, triggers, relaciones y catálogos como `SA_REDUCCION`.
3. `03-crear-identidades-roles.sql`: crea roles y cuentas individuales y técnicas como SYS.
4. `04-crear-paquetes-permisos.sql`: crea los paquetes y grants como `SA_REDUCCION`.

El inicializador comprueba los objetos y privilegios, y deja un marcador de finalización para el healthcheck. La imagen ejecuta la inicialización automática solo cuando Oracle inicializa un volumen vacío; modificar SQL o `.env` no migra automáticamente la base existente.

Para bases ya inicializadas se prepararon `05-crear-cuenta-tecnica.sql` y `migrate-backend.sh`. El migrador crea o verifica `SAR_BACKEND` sin cambiar la contraseña de una cuenta existente y vuelve a instalar los paquetes/grants; se detiene si detecta privilegios adicionales que requieren revisión. **Esta migración a la arquitectura `SAR_BACKEND` está pendiente de ejecución y validación en la base existente.** No se debe borrar ni recrear el volumen para aplicarla. Los detalles operativos están en [README.md](README.md) y el estado de la migración, en la tabla siguiente.

Cuando se autorice aplicar la migración, después de definir `ORACLE_BACKEND_USER` y `ORACLE_BACKEND_PASSWORD` en `.env`, los comandos preparados son:

```bash
docker compose up -d --no-deps --force-recreate oracle-db
docker compose exec -T oracle-db bash /opt/sareduccion/init-db/migrate-backend.sh
```

El primer comando recrea el contenedor para cargar el entorno actualizado, no elimina el volumen. El segundo ejecuta el script de migración dentro de Oracle. No ejecutar `docker compose down -v`.

El destino futuro es Oracle 12c, pero ni los scripts ni el driver han sido validados aún contra la versión exacta de 12c prevista.

## Estado del desarrollo

### Implementado y validado previamente

- La estructura base reportada de Oracle XE 21c: 21 tablas, 15 secuencias, 22 triggers y los paquetes originales.
- Las cuentas individuales de prueba, roles y esquema según la inicialización existente.
- Frontend estático, proxy Nginx y configuración Docker Compose del entorno.

La validación previa corresponde al estado anterior a la adaptación de `SAR_BACKEND`; no demuestra que los cambios nuevos estén instalados en la base actual.

### Implementado en archivos, pendiente de aplicar o verificar

- Firmas nuevas y validaciones de identidad de los paquetes PL/SQL.
- Cuenta `SAR_BACKEND`, permisos mínimos y configuración del pool JDBC.
- Scripts de instalación para volúmenes nuevos y migración no destructiva para la base existente.

No se ejecutaron la migración ni SQL en esta actualización documental. `database/tests/05-verificar-instalacion.sql` comprueba conteos y paquetes. `database/tests/06-probar-permisos.sql` todavía espera los antiguos grants a roles individuales y debe actualizarse antes de usarse para validar el modelo nuevo.

### Pendiente de desarrollar

- Autenticación inicial con Oracle, determinación del perfil y `HttpSession`.
- Autorización por perfil en los endpoints.
- Capa JDBC para invocar los paquetes y manejar cursores/transacciones.
- Autenticación de aspirantes y cierre de sesión.
- Controladores REST funcionales, validación de entradas y manejo centralizado de errores.
- Pruebas de integración de la nueva ruta de autenticación y de los paquetes.
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
