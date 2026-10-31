# SAReduccion

Sistema web para gestionar las solicitudes de reducción del monto de inscripción y reinscripción de estudiantes del Instituto Tecnológico de Toluca.

## 1. Requisitos

Para ejecutar el proyecto se necesita:

- Docker Engine o Docker Desktop.
- Docker Compose v2.

## 2. Configurar las variables de entorno

Crear el archivo `.env` a partir del ejemplo:

```bash
cp .env.example .env
```

Editar `.env` y configurar las contraseñas de Oracle y las demás variables necesarias.

## 3. Ejecutar el proyecto

Para construir las imágenes e iniciar los contenedores de Oracle, backend y frontend, ejecutar:

```bash
docker compose --profile full-stack up -d --build
```

La primera inicialización de Oracle puede tardar varios minutos. Docker Compose esperará a que la base de datos esté saludable antes de iniciar el backend.

Para comprobar el estado de los contenedores:

```bash
docker compose --profile full-stack ps
```

**Acceso a la aplicación**

Una vez iniciados los servicios, ingresar desde el navegador:

- **Solicitante:** http://localhost:8081/solicitante/login.html
- **Administrativo:** http://localhost:8081/administrativo/login.html

Los puertos se configuran en `.env` y pueden cambiarse sin modificar `docker-compose.yml`:

| Servicio | Variable | Puerto de referencia |
| --- | --- | --- |
| Oracle XE | `ORACLE_PORT` | `1522` |
| Backend Spring Boot | `APP_PORT` | `8080` |
| Frontend Nginx | `FRONTEND_PORT` | `8081` |

Si configuras otro valor para `FRONTEND_PORT`, reemplaza `8081` en las direcciones anteriores por el puerto elegido. Nginx sirve las páginas web y redirige `/api/` al backend.

## Usuarios de prueba

Disponibles cuando se inicializa una base nueva con `SAR_LOAD_DEV_SEED=true`:

| Perfil | Acceso | Datos de inicio de sesión |
| :--- | :--- | :--- |
| **Estudiante** | Solicitante → Alumno | Número de control `21281181` y contraseña definida en `ORACLE_ALUMNO_PASSWORD` |
| **Aspirante** | Solicitante → Nuevo ingreso | Ficha `F2026A001` y CURP `MOPA080512MDFRNN01` |
| **Administrador** | Administrativo | Usuario `SA01` y contraseña definida en `ORACLE_ADMIN_PASSWORD` |


### Problemas durante la inicialización

Si Oracle no alcanza el estado `healthy` o los servicios no inician correctamente, revisar primero sus registros:

```bash
docker compose logs -f oracle-db
```

Si el problema está relacionado con la inicialización de Oracle, se puede intentar iniciar los servicios por separado:

**1. Iniciar Oracle:**

```bash
docker compose up -d oracle-db
```

**2. Comprobar su estado:**

```bash
docker compose ps
```

Esperar hasta que Oracle aparezca como `healthy`.

**3. Iniciar el resto de los servicios:**

```bash
docker compose --profile full-stack up -d --build
```

Si Oracle permanece en estado `unhealthy`, es necesario revisar los errores de los registros antes de continuar. Iniciar los servicios por separado no corrige errores en los scripts SQL ni problemas de recursos.

## 4. Actualizar repositorio clonado

En caso de requerir actualizar un repositorio clonado anteriormente, sincroniza las ramas locales con las versiones actuales del repositorio remoto.

**1. Obtener los cambios del repositorio remoto:**

```bash
git fetch origin --prune --force
```

**2. Actualizar las ramas locales:**

```bash
git switch -C main origin/main
git switch -C Prueba_1 origin/Prueba_1
git switch -C equipo_2 origin/equipo_2
```

Estos comandos actualizan las ramas locales para que apunten a las versiones actuales del repositorio remoto.

**Nota:** Si existen cambios locales o commits sin publicar, guárdalos antes de ejecutar estos comandos, ya que podrían quedar fuera de las ramas actualizadas.

## 5. Comandos útiles

**Iniciar los servicios sin reconstruir las imágenes:**

```bash
docker compose --profile full-stack up -d
```

**Consultar el estado de los contenedores:**

```bash
docker compose --profile full-stack ps
```

**Consultar los registros de los servicios:**

```bash
docker compose --profile full-stack logs -f
```

**Consultar los registros de Oracle:**

```bash
docker compose logs -f oracle-db
```

**Detener y eliminar los contenedores del proyecto:**

```bash
docker compose --profile full-stack down
```

Este comando conserva los datos almacenados en los volúmenes de Docker.

## 6. Documentación adicional

Para consultar información más detallada del proyecto:

- [Arquitectura técnica](ARQUITECTURA.md): estructura del sistema, servicios, autenticación y estado del desarrollo.
- [Base de datos](BaseDeDatos.md): modelo de datos, paquetes PL/SQL, permisos y respaldo/restauración.
- [Docker](Docker.md): servicios, volúmenes, inicialización de Oracle y problemas frecuentes.
- [Guía del proyecto](GUIA_PROYECTO.md): estructura de carpetas, tecnologías y comandos.
- [Asignación de equipos](asignacion_Modulos.md): responsabilidades por módulo y reglas funcionales acordadas.
- [Base de datos](BaseDeDatos.md): modelo de datos, tablas, relaciones y configuración de Oracle.