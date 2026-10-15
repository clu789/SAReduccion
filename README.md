# SAReduccion

Sistema para gestionar solicitudes de reducción del monto de inscripción y reinscripción del Instituto Tecnológico de Toluca.

## Requisitos

- Git.
- Docker Engine o Docker Desktop con Docker Compose v2 (`docker compose`).

No se requiere instalar Java ni Maven para levantar el entorno con contenedores.

## Preparar el proyecto

```bash
git clone <URL_DEL_REPOSITORIO>
cd SAReduccion
cp .env.example .env
```

En `.env` deben estar definidas las siguientes variables, ya preparadas en `.env.example`:

- `ORACLE_PASSWORD`
- `ORACLE_APP_USER` y `ORACLE_APP_PASSWORD`
- `ORACLE_BACKEND_USER` y `ORACLE_BACKEND_PASSWORD`
- `ORACLE_ALUMNO_USER` y `ORACLE_ALUMNO_PASSWORD`
- `ORACLE_ADMIN_USER` y `ORACLE_ADMIN_PASSWORD`

Define valores privados para las variables de contraseña. Conserva los nombres de usuario de ejemplo, que corresponden a las cuentas que espera el inicializador. `ORACLE_DATABASE`, `ORACLE_PORT`, `APP_PORT` y `SPRING_PROFILES_ACTIVE` ya tienen valores de desarrollo configurados. No publiques `.env` ni compartas contraseñas reales.

## Iniciar

Para levantar únicamente Oracle XE:

```bash
docker compose up -d oracle-db
```

Para levantar Oracle, frontend Nginx y backend Spring Boot:

```bash
docker compose --profile full-stack up -d --build
```

La aplicación frontend queda disponible en:

- Solicitante: <http://localhost:8081/solicitante/login.html>
- Administrativo: <http://localhost:8081/administrativo/login.html>

Oracle se publica en `localhost:1522`. El backend usa el puerto `8080` e implementa actualmente los endpoints de autenticación (`/api/auth/me`, `/api/auth/login`, `/api/auth/login/aspirante` y `/api/auth/logout`). Las vistas de solicitudes, respuestas y deducciones siguen mostrando datos simulados porque sus endpoints de negocio aún no existen.

## Respaldo de Oracle

Además del volumen persistente, el esquema puede respaldarse con Oracle Data Pump:

```bash
docker exec -e SARTS="$(date +%Y%m%d-%H%M%S)" sareduccion-oracle \
  expdp "SA_REDUCCION/<APP_PASSWORD>@//localhost:1521/XEPDB1" \
    SCHEMAS=SA_REDUCCION DIRECTORY=SAR_BACKUP_DIR \
    DUMPFILE="sa_reduccion_${SARTS}.dmp" LOGFILE="sa_reduccion_${SARTS}.exp.log" \
    REUSE_DUMPFILES=N CONTENT=ALL EXCLUDE=STATISTICS PARALLEL=1
```

Los archivos se copian luego a `backups/oracle/` con `docker cp`. La carpeta está excluida por `.gitignore`: nunca deben subirse los `.dmp` al repositorio. El procedimiento completo, la verificación por hash y la restauración con `impdp` están en [BaseDeDatos.md](BaseDeDatos.md).

## Estado y logs

```bash
docker compose ps
docker compose logs --tail=150 oracle-db
docker compose logs -f
```

En la primera inicialización, Oracle debe terminar los scripts antes de quedar `healthy`.

## Detener

```bash
docker compose down
```

Este comando detiene los servicios y conserva el volumen Oracle `sareduccion_oracle_data`. Oracle ejecuta la inicialización automática solo cuando el volumen está vacío; los reinicios normales conservan sus datos.

Consulta la [arquitectura técnica](ARQUITECTURA.md) y el [detalle del modelo de base de datos](BaseDeDatos.md) para más información.
