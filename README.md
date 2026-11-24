# SAReduccion

Sistema web para gestionar las solicitudes de reducción del monto de inscripción y reinscripción para hijos o cónyuges de trabajadores del Tecnológico Nacional de México y personal homologado en el Instituto Tecnológico de Toluca.

## 1. Requisitos

Para ejecutar el proyecto se necesita:

- Docker Engine o Docker Desktop.
- Docker Compose v2.

## 2. Configurar las variables de entorno

Crear el archivo local `.env` a partir de la plantilla [`.env.example`](.env.example):

**Linux, macOS o Git Bash:**

```bash
cp .env.example .env
```

**Windows PowerShell:**

```powershell
Copy-Item .env.example .env
```

Editar `.env` y configurar las contraseñas de Oracle y las demás variables necesarias. El archivo `.env` es local y está excluido por `.gitignore`: no debe subirse ni compartirse en el repositorio.

## 3. Ejecutar el proyecto

Para construir las imágenes e iniciar los contenedores de Oracle, backend y frontend, ejecutar desde la raíz del proyecto:

```bash
docker compose --profile full-stack up -d --build
```

La primera inicialización de Oracle puede tardar varios minutos. Docker Compose esperará a que la base de datos alcance el estado `healthy` antes de iniciar el backend.

Para comprobar el estado de los contenedores:

```bash
docker compose --profile full-stack ps
```

### Acceso a la aplicación

Una vez iniciados los servicios, ingresar desde el navegador:

- **Solicitante:** <http://localhost:8081/solicitante/login.html>
- **Administrativo:** <http://localhost:8081/administrativo/login.html>

Los puertos se configuran en `.env` sin editar [`docker-compose.yml`](docker-compose.yml):

| Servicio | Variable | Puerto por defecto |
| --- | --- | --- |
| Oracle XE 21c | `ORACLE_PORT` | `1522` |
| Backend Spring Boot | `APP_PORT` | `8080` |
| Frontend Nginx | `FRONTEND_PORT` | `8081` |

Si cambias `FRONTEND_PORT`, sustituye `8081` en las direcciones anteriores por el puerto configurado. Nginx sirve las vistas estáticas y redirige `/api/` al backend.

## 4. Usuarios de prueba

Disponibles al inicializar una base nueva con `SAR_LOAD_DEV_SEED=true` (otros escenarios de aspirantes del seed están documentados en [`PRUEBAS.md`](PRUEBAS.md)):

| Perfil | Acceso | Datos de inicio de sesión |
| --- | --- | --- |
| **Estudiante** | Solicitante → Alumno | Usuario `ALU_21281181` (o número de control `21281181` según el formato admitido por la pestaña) y contraseña `ORACLE_ALUMNO_PASSWORD` |
| **Aspirante** | Solicitante → Nuevo ingreso | Ficha `F2026A001` y CURP `MOPA080512MDFRNN01` |
| **Administrador** | Administrativo | Usuario `SA01` y contraseña `ORACLE_ADMIN_PASSWORD` |

### Problemas durante la inicialización

Si Oracle no alcanza el estado `healthy` o los servicios no inician correctamente, revisar primero sus registros:

```bash
docker compose logs -f oracle-db
```

Para aislar un fallo de inicialización de Oracle:

1. Iniciar Oracle por separado y esperar a que quede saludable:

   ```bash
   docker compose up -d oracle-db --wait --wait-timeout 600
   ```

2. Comprobar su estado:

   ```bash
   docker compose ps
   ```

   Si Oracle pasa a `unhealthy`, revisar los logs con `docker compose logs --tail=200 oracle-db`. Iniciar el resto de servicios por separado no corrige errores en los scripts SQL.

3. Iniciar el resto de servicios cuando Oracle esté `healthy`:

   ```bash
   docker compose --profile full-stack up -d --build
   ```

4. Si Oracle quedó a medio inicializar sobre un volumen nuevo y no hay datos que conservar, reiniciar el volumen de datos:

   ```bash
   docker compose --profile full-stack down -v
   ```

   **Advertencia:** `down -v` elimina el volumen `sareduccion_oracle_data` y todos los datos almacenados en Oracle.

## 5. Comandos útiles

```bash
# Iniciar los servicios sin reconstruir las imágenes
docker compose --profile full-stack up -d

# Reconstruir solo el backend tras cambios en Java
docker compose --profile full-stack up -d --build backend

# Consultar el estado de los contenedores
docker compose --profile full-stack ps

# Consultar registros
docker compose --profile full-stack logs -f
docker compose logs -f oracle-db
docker compose logs -f backend

# Detener los contenedores conservando los datos de Oracle
docker compose --profile full-stack down
```

## 6. Documentación del proyecto

| Documento | Contenido |
| --- | --- |
| [`ARQUITECTURA.md`](ARQUITECTURA.md) | Estructura de carpetas, capas, contenedores Docker, endpoints y autenticación |
| [`BASE_DE_DATOS.md`](BASE_DE_DATOS.md) | Esquema Oracle, 23 tablas, paquetes PL/SQL, permisos y respaldo/restauración |
| [`SEGURIDAD.md`](SEGURIDAD.md) | Autorización por rol, CSRF, captcha, OTP, auditoría en Oracle y estándar de logs |
| [`NOTIFICACIONES.md`](NOTIFICACIONES.md) | Correo SMTP, Mailpit, plantillas HTML y avisos automáticos o manuales |
| [`PRUEBAS.md`](PRUEBAS.md) | Pruebas unitarias Java, JSDOM, integración Oracle XE y verificación manual |
| [`REQUERIMIENTOS.md`](REQUERIMIENTOS.md) | Matriz de requerimientos funcionales, no funcionales y reglas de negocio |
| [`ASIGNACION_MODULOS.md`](ASIGNACION_MODULOS.md) | Distribución de los 7 equipos, integrantes, responsabilidades y reglas acordadas |
| [`modulo-solicitante/README.md`](modulo-solicitante/README.md) | Vistas, endpoints y pruebas del módulo de estudiantes y aspirantes |
| [`modulo-administrativo/README.md`](modulo-administrativo/README.md) | Vistas, convenciones y endpoints del módulo administrativo |
