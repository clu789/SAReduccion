# SAReduccion

Sistema para gestionar solicitudes de reducción del monto de inscripción y reinscripción del Instituto Tecnológico de Toluca.

## Tecnologías

- **Oracle Database XE 21c:** almacenamiento de información, usuarios y procedimientos de base de datos.
- **Spring Boot:** backend y servicios de autenticación.
- **Nginx:** servidor del frontend y proxy de las solicitudes `/api/`.
- **Docker Compose:** ejecución y comunicación de los servicios mediante contenedores.

## Requisitos

- Git.
- Docker Engine con Docker Compose v2, o Docker Desktop con Compose v2.
- Docker iniciado y disponible desde la terminal.

No es necesario instalar Java, Maven ni Oracle en el equipo para ejecutar el sistema mediante Docker.

## Preparar el proyecto

Clonar el repositorio y acceder a su carpeta:

```bash
git clone <URL_DEL_REPOSITORIO>
cd SAReduccion
```

<<<<<<< HEAD
Si el archivo `.env` todavía no existe, créalo a partir de `.env.example`:

**Linux, macOS o Git Bash:**

```bash
cp .env.example .env
```

**Windows PowerShell:**

```powershell
Copy-Item .env.example .env
```

Revisa las variables de `.env` antes de iniciar el sistema. En particular:

- `ORACLE_PASSWORD`: contraseña de administración de Oracle.
- `ORACLE_APP_USER` y `ORACLE_APP_PASSWORD`: propietario del esquema.
- `ORACLE_BACKEND_USER` y `ORACLE_BACKEND_PASSWORD`: cuenta de conexión del backend.
- `ORACLE_ALUMNO_USER` y `ORACLE_ALUMNO_PASSWORD`: cuenta de estudiante de prueba.
- `ORACLE_ADMIN_USER` y `ORACLE_ADMIN_PASSWORD`: cuenta administrativa de prueba.
- `SAR_LOAD_DEV_SEED`: controla la carga inicial de datos de desarrollo (`true` o `false`).

Con `SAR_LOAD_DEV_SEED=true`, una instalación nueva incorpora usuarios y solicitudes de ejemplo. Con `false`, se inicializa el esquema sin esos datos de aplicación. Para un entorno de producción, utiliza `false`.
=======
### Crear el archivo `.env`

1. Copia la plantilla (el archivo `.env` nunca se versiona):

   **Linux, macOS o Git Bash:**

   ```bash
   cp .env.example .env
   ```

   **Windows PowerShell:**

   ```powershell
   Copy-Item .env.example .env
   ```

2. Abre `.env` y asigna las contraseñas de Oracle. En particular:

   - `ORACLE_PASSWORD`: contraseña de administración de Oracle.
   - `ORACLE_APP_USER` y `ORACLE_APP_PASSWORD`: propietario del esquema.
   - `ORACLE_BACKEND_USER` y `ORACLE_BACKEND_PASSWORD`: cuenta de conexión del backend.
   - `ORACLE_ALUMNO_USER` y `ORACLE_ALUMNO_PASSWORD`: cuenta de estudiante de prueba.
   - `ORACLE_ADMIN_USER` y `ORACLE_ADMIN_PASSWORD`: cuenta administrativa de prueba.
   - `SAR_LOAD_DEV_SEED`: controla la carga inicial de datos de desarrollo (`true` o `false`).

3. Verifica que Git lo ignore antes de iniciar el sistema:

   ```bash
   git check-ignore -v .env
   ```

**Datos de ejemplo:** con `SAR_LOAD_DEV_SEED=true`, una instalación nueva incorpora usuarios y solicitudes de ejemplo. Con `false`, se inicializa el esquema sin esos datos de aplicación. Para un entorno de producción, utiliza `false`.
>>>>>>> 2ec3c504996abec9bae6eb5525599e2c2c197b2e

**Importante:** la carga inicial se ejecuta únicamente cuando se crea una base de datos nueva. Cambiar esta variable después no elimina ni vuelve a cargar los registros existentes. Evita compartir contraseñas reales o reutilizarlas fuera del entorno de desarrollo.

## Iniciar el sistema

Para levantar **Oracle, backend Spring Boot y frontend Nginx**:

```bash
docker compose --profile full-stack up -d --build
```

En el primer inicio, Oracle crea la base de datos y ejecuta automáticamente los scripts de `docker/init-db/`. Este proceso puede tardar varios minutos. No es necesario ejecutar manualmente scripts SQL ni migraciones para preparar una instalación nueva.

Si solo necesitas iniciar Oracle:

```bash
docker compose up -d oracle-db
```

### Acceso a la aplicación

Con `FRONTEND_PORT=8081`, abre directamente en el navegador:

- **Solicitante:** [http://localhost:8081/solicitante/login.html](http://localhost:8081/solicitante/login.html)
- **Administrativo:** [http://localhost:8081/administrativo/login.html](http://localhost:8081/administrativo/login.html)

Los puertos se configuran en `.env` y pueden cambiarse sin modificar `docker-compose.yml`:

| Servicio | Variable | Puerto de referencia |
| --- | --- | --- |
| Oracle XE | `ORACLE_PORT` | `1522` |
| Backend Spring Boot | `APP_PORT` | `8080` |
| Frontend Nginx | `FRONTEND_PORT` | `8081` |

Si configuras otro valor para `FRONTEND_PORT`, reemplaza `8081` en las direcciones anteriores por el puerto elegido. Nginx sirve las páginas web y redirige `/api/` al backend.

**Estado funcional:** los endpoints de autenticación disponibles son `GET /api/auth/me`, `POST /api/auth/login`, `POST /api/auth/login/aspirante` y `POST /api/auth/logout`. Algunas vistas de solicitudes, respuestas y deducciones todavía utilizan datos simulados, ya que sus endpoints de negocio aún no están implementados.

## Usuarios de prueba

Disponibles cuando se inicializa una base nueva con `SAR_LOAD_DEV_SEED=true`:

| Perfil | Acceso | Datos de inicio de sesión |
| --- | --- | --- |
| Estudiante | Solicitante → Alumno | Número de control `21281181` y contraseña definida en `ORACLE_ALUMNO_PASSWORD` |
| Aspirante | Solicitante → Nuevo ingreso | Ficha `F2026A001` y CURP de ejemplo indicada en `06-seed-desarrollo.sql` |
| Administrador | Administrativo | Usuario `SA01` y contraseña definida en `ORACLE_ADMIN_PASSWORD` |

## Estado y administración de contenedores

Los siguientes comandos funcionan en Linux, macOS y Windows PowerShell:

**Consultar el estado de los servicios:**

```bash
docker compose --profile full-stack ps
```

**Consultar los logs de Oracle:**

```bash
docker compose logs --tail=150 oracle-db
```

**Seguir los logs de los servicios en tiempo real:**

```bash
docker compose --profile full-stack logs -f
```

**Reiniciar los servicios:**

```bash
docker compose --profile full-stack restart
```

**Detener y retirar los contenedores del proyecto:**

```bash
docker compose --profile full-stack down
```

Este último comando **conserva los datos de Oracle** almacenados en el volumen persistente. No utilices la opción `-v` si necesitas conservar la información.

## Persistencia y respaldos de Oracle

La base de datos utiliza el volumen Docker `sareduccion_oracle_data`, que permite conservar los registros al detener o recrear los contenedores. Los scripts de inicialización no se vuelven a ejecutar mientras se reutilice una base ya inicializada.

Además, se pueden generar respaldos independientes mediante **Oracle Data Pump** (`expdp`) y restaurarlos con `impdp`. Los archivos se almacenan en `backups/oracle/` del equipo anfitrión, mediante un directorio montado en el contenedor. Esta carpeta está excluida de Git.

El volumen persistente **no sustituye a un respaldo**. Consulta [BaseDeDatos.md](BaseDeDatos.md) para los comandos completos de exportación, verificación y restauración, así como sus precauciones.

<<<<<<< HEAD
=======
## Actualizar un clon tras reescribir el historial

Si tu copia se clonó antes del `2026-10-19`, los identificadores de commit cambiaron. Guarda o confirma tus cambios locales y ejecuta:

```bash
git fetch origin --prune --force
git switch -C main origin/main
git switch -C Prueba_1 origin/Prueba_1
git switch -C equipo_2 origin/equipo_2
```

`git switch -C` crea la rama si no existe o la mueve a `origin/<rama>`. **Descarta los cambios sin confirmar** de la rama actual; si los necesitas, usa antes `git stash` o `git commit`. Los commits antiguos (`65c183d`, `39fb851`, `57d2935`) ya no existen en el remoto.

Alternativa: re-clonar el repositorio.

>>>>>>> 2ec3c504996abec9bae6eb5525599e2c2c197b2e
## Documentación adicional

- [Docker.md](Docker.md): contenido de Docker, servicios, construcción, inicialización y operación.
- [ARQUITECTURA.md](ARQUITECTURA.md): componentes, tecnologías, comunicación y estructura del sistema.
- [BaseDeDatos.md](BaseDeDatos.md): modelo de datos, usuarios, permisos, inicialización y procedimientos de respaldo y restauración.
