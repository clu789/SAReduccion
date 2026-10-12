# Guía de Entorno Docker - SAReducción

## Requisitos

- Docker Engine o Docker Desktop con Docker Compose v2.
- Archivo `.env` configurado a partir de `.env.example`.

```bash
cp .env.example .env
```

La inicialización no solicita entradas interactivas. Define contraseñas para Oracle, el esquema, el alumno y el administrador en `.env`; usa valores de al menos ocho caracteres sin espacios, comillas dobles, `&`, `@` ni `/`.

## Oracle XE 21c

Inicia la base de datos desde la raíz del proyecto:

```bash
docker compose up -d oracle-db
```

La primera inicialización crea el esquema `SA_REDUCCION`, sus 21 tablas, secuencias, triggers y catálogos, los usuarios `ALU_21281181` y `SA01`, sus roles, y los paquetes `PKG_ESTUDIANTE` y `PKG_ADMINISTRADOR`. El orquestador `docker/init-db/init-db.sh` ejecuta los cuatro SQL con el usuario y PDB adecuados.

Oracle queda disponible en `localhost:1522`, servicio `XEPDB1` (puerto interno `1521`). El contenedor solo estará `healthy` cuando la inicialización completa haya terminado.

Comprueba el estado y los registros:

```bash
docker compose ps
docker compose logs --tail=150 oracle-db
```

Busca `Inicializacion Oracle de SAReduccion completada.` y el estado `healthy`. Las comprobaciones SQL manuales están en `database/tests/`; no se ejecutan al iniciar.

## Otros servicios

Frontend:

```bash
docker compose up -d frontend
```

Acceso: `http://localhost:8081/solicitante/login.html` o `http://localhost:8081/administrativo/login.html`.

Backend, frontend y Oracle:

```bash
docker compose --profile full-stack up -d --build
```

## Persistencia

Detener servicios sin borrar la base:

```bash
docker compose down
```

Recrear Oracle desde cero, eliminando permanentemente el volumen y sus datos:

```bash
docker compose down -v
docker compose up -d oracle-db
```

Los scripts de `/container-entrypoint-initdb.d` solo corren cuando Oracle inicializa un volumen vacío. Cambiar los SQL o `.env` no modifica una base que ya existe.

## Puertos

- `8081`: frontend Nginx.
- `8080`: backend Spring Boot.
- `1522`: Oracle XE 21c en el host; `1521` dentro de Docker.
