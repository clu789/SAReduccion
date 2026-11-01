# Docker en SAReduccion

## 1. ¿Qué es Docker en este proyecto?

Docker permite ejecutar SAReduccion dentro de contenedores aislados, sin
instalar directamente en el equipo Java, Maven, Oracle Database ni Nginx.
Cada contenedor ejecuta una parte del sistema y Docker Compose coordina su
configuración, red, almacenamiento y orden de arranque.

El entorno completo está formado por tres servicios:

```text
Navegador
    |
    v
Nginx (frontend:8081)
    |  /api/*
    v
Spring Boot (backend:8080)
    |
    v
Oracle XE (oracle-db:1521/XEPDB1)
```

Los servicios de Compose comparten la red `sareduccion-net`. Por eso el
backend se conecta a Oracle usando el nombre del servicio `oracle-db`, no
`localhost`. Dentro de un contenedor, `localhost` siempre representa al mismo
contenedor.

## 2. Archivos relacionados con Docker

| Archivo o carpeta | Función |
| --- | --- |
| [`Dockerfile`](Dockerfile) | Construye la imagen del backend Spring Boot. |
| [`docker-compose.yml`](docker-compose.yml) | Define los servicios, puertos, variables, volúmenes, perfiles y dependencias. |
| [`.env.example`](.env.example) | Plantilla de variables necesarias para ejecutar Compose. |
| [`docker/init-db/init-db.sh`](docker/init-db/init-db.sh) | Orquesta la inicialización de Oracle en una base nueva. |
| [`docker/init-db/`](docker/init-db/) | Contiene los scripts SQL de esquema, cuentas, permisos y datos de desarrollo. |
| [`docker/nginx/nginx.conf`](docker/nginx/nginx.conf) | Configura Nginx para servir los módulos web y redirigir `/api/` al backend. |
| `backups/oracle/` | Carpeta local para respaldos Oracle Data Pump; se monta en el contenedor de Oracle y no se versiona. |

## 3. Servicios y contenedores

### 3.1 `oracle-db`

Usa la imagen `gvenzl/oracle-xe:21-slim` y ejecuta Oracle XE 21c.

- El nombre del contenedor es `sareduccion-oracle`.
- El PDB utilizado por la aplicación es `XEPDB1`.
- El puerto `1521` del contenedor se publica normalmente como `1522` en el equipo.
- El volumen `sareduccion_oracle_data` conserva los datos de Oracle.
- Tiene un `healthcheck` que espera a que Oracle esté disponible y a que la
  inicialización de SAReduccion haya terminado.
- Se reinicia automáticamente mientras Docker esté activo, salvo que se
  detenga explícitamente.

El puerto publicado sirve para conexiones desde el equipo anfitrión, por
ejemplo desde una herramienta SQL. El backend no usa ese puerto: se conecta
internamente a `oracle-db:1521`.

### 3.2 `backend`

Se construye a partir del [`Dockerfile`](Dockerfile) y ejecuta la API REST de
Spring Boot.

- El nombre del contenedor es `sareduccion-backend`.
- El puerto `8080` del contenedor se publica normalmente como `8080`.
- Usa el perfil de Spring indicado por `SPRING_PROFILES_ACTIVE`.
- Recibe la conexión JDBC mediante `DB_URL`, `DB_USER` y `DB_PASSWORD`.
- Solo arranca después de que `oracle-db` reporte un estado saludable.
- Pertenece al perfil de Compose `full-stack`.

La URL interna de base de datos se construye así:

```text
jdbc:oracle:thin:@//oracle-db:1521/XEPDB1
```

### 3.3 `frontend`

Usa la imagen `nginx:alpine` para servir los archivos HTML, CSS, JavaScript e
imágenes de los dos módulos web.

- El nombre del contenedor es `sareduccion-frontend`.
- El puerto `80` del contenedor se publica normalmente como `8081`.
- Monta [`modulo-administrativo/`](modulo-administrativo/) en
  `/administrativo`.
- Monta [`modulo-solicitante/`](modulo-solicitante/) en `/solicitante`.
- Monta [`docker/nginx/nginx.conf`](docker/nginx/nginx.conf) como configuración
  de Nginx.
- También pertenece al perfil `full-stack`.

Nginx redirige la ruta `/` al login del solicitante y funciona como proxy
inverso para las peticiones que comienzan con `/api/`. Así, el navegador se
comunica con un único origen y no necesita conectarse directamente al backend.

## 4. Cómo se construye la imagen del backend

El [`Dockerfile`](Dockerfile) utiliza una construcción de dos etapas:

1. **Compilación**
   - Parte de `maven:3.8.8-eclipse-temurin-8`.
   - Copia primero `pom.xml` y descarga las dependencias para aprovechar la
     caché de Docker.
   - Copia `src/`.
   - Ejecuta `mvn clean package -DskipTests -B`.
   - Genera el archivo JAR de Spring Boot.

2. **Ejecución**
   - Parte de `eclipse-temurin:8-jre-alpine`, una imagen más pequeña que una
     imagen con Maven completo.
   - Copia únicamente el JAR generado.
   - Expone el puerto `8080`.
   - Ejecuta la aplicación con Java 8 y un límite de heap de `512 MB`.

Por esta razón, para ejecutar el backend mediante Docker no es necesario
instalar Java ni Maven en el equipo anfitrión. La opción `--build` vuelve a
construir la imagen cuando se requiere incorporar cambios del código fuente.

## 5. Inicialización de Oracle

La inicialización automática ocurre únicamente cuando Oracle se inicia con un
volumen de datos nuevo. Docker monta `init-db.sh` como
`/container-entrypoint-initdb.d/00-init-db.sh`; ese script es el único
orquestador que se ejecuta automáticamente desde el directorio de entrada de
la imagen.

Los demás SQL se montan como archivos de solo lectura en
`/opt/sareduccion/init-db`. El orquestador los ejecuta en el orden correcto y
con el usuario Oracle correspondiente:

| Orden | Script | Qué hace |
| --- | --- | --- |
| 01 | `01-configurar-esquema.sql` | Crea o configura el esquema `SA_REDUCCION` y el directorio de respaldos. |
| 02 | `02-crear-estructura.sql` | Crea tablas, claves, restricciones, secuencias, triggers y catálogos base. |
| 03 | `03-crear-identidades-roles.sql` | Crea roles y cuentas de estudiante y administrador para pruebas. |
| 04 | `04-crear-cuenta-tecnica.sql` | Crea o verifica la cuenta técnica que utiliza el backend. |
| 05 | `05-crear-paquetes-permisos.sql` | Crea los paquetes PL/SQL y concede al backend solo los permisos necesarios. |
| 06 | `06-seed-desarrollo.sql` | Carga datos ficticios si `SAR_LOAD_DEV_SEED=true`. |

Los scripts 01, 03 y 04 necesitan privilegios de `SYS`. Los scripts 02, 05 y
06 se ejecutan como propietario del esquema para que las tablas y paquetes
pertenezcan a `SA_REDUCCION`.

Si el volumen ya contiene una base inicializada, cambiar un SQL o
`SAR_LOAD_DEV_SEED` no vuelve a ejecutar los scripts. Los cambios posteriores
deben aplicarse de forma controlada sobre la base existente.

## 6. Variables de entorno

Antes de iniciar el proyecto, copia la plantilla:

```powershell
Copy-Item .env.example .env
```

Completa las contraseñas de `.env`. Las variables más importantes son:

| Variable | Uso | Valor habitual |
| --- | --- | --- |
| `ORACLE_PASSWORD` | Contraseña administrativa de Oracle XE. | Definida por el usuario |
| `ORACLE_APP_USER` / `ORACLE_APP_PASSWORD` | Propietario del esquema. | `SA_REDUCCION` / definida por el usuario |
| `ORACLE_BACKEND_USER` / `ORACLE_BACKEND_PASSWORD` | Cuenta técnica JDBC del backend. | `SAR_BACKEND` / definida por el usuario |
| `ORACLE_DATABASE` | Nombre del PDB. | `XEPDB1` |
| `ORACLE_PORT` | Puerto Oracle publicado en el equipo. | `1522` |
| `ORACLE_ALUMNO_*` | Cuenta de estudiante de prueba. | Definida en `.env.example` |
| `ORACLE_ADMIN_*` | Cuenta administrativa de prueba. | Definida en `.env.example` |
| `SAR_LOAD_DEV_SEED` | Carga datos ficticios en una instalación nueva. | `true` |
| `APP_PORT` | Puerto del backend publicado en el equipo. | `8080` |
| `FRONTEND_PORT` | Puerto del frontend publicado en el equipo. | `8081` |
| `SPRING_PROFILES_ACTIVE` | Perfil activo de Spring Boot. | `dev` |

No se deben subir `.env` ni contraseñas reales al repositorio. Para un
entorno de producción, usa `SAR_LOAD_DEV_SEED=false` y credenciales propias.

## 7. Cómo iniciar y administrar Docker

Desde la raíz del proyecto:

```powershell
# Solo Oracle
docker compose up -d oracle-db

# Oracle, backend y frontend; construye el backend
docker compose --profile full-stack up -d --build

# Ver el estado
docker compose --profile full-stack ps

# Ver logs de todos los servicios
docker compose --profile full-stack logs -f

# Ver logs de un servicio
docker compose logs --tail=150 oracle-db

# Reiniciar los servicios
docker compose --profile full-stack restart

# Detener y retirar contenedores, conservando los datos
docker compose --profile full-stack down
```

Cuando los servicios estén listos, las direcciones predeterminadas son:

- Solicitante: <http://localhost:8081/solicitante/login.html>
- Administrativo: <http://localhost:8081/administrativo/login.html>
- API directa: <http://localhost:8080>
- Oracle desde el equipo anfitrión: `localhost:1522/XEPDB1`

Si cambias `FRONTEND_PORT`, `APP_PORT` u `ORACLE_PORT`, sustituye los puertos
anteriores por los valores definidos en `.env`.

## 8. Persistencia, eliminación y respaldos

El volumen `sareduccion_oracle_data` guarda los archivos de datos de Oracle.
Por eso, `docker compose down` elimina los contenedores, pero conserva la
información.

```powershell
# PELIGRO: elimina el volumen y todos los datos de Oracle
docker compose down -v
```

Usa `down -v` solo cuando quieras reinstalar la base desde cero y no necesites
conservar sus datos. El volumen tampoco sustituye a un respaldo. Los respaldos
de Oracle Data Pump se guardan en `backups/oracle/` mediante el montaje:

```text
equipo anfitrión/backups/oracle
    -> /opt/sareduccion/backups
```

Los detalles de exportación y restauración están documentados en
[`BaseDeDatos.md`](BaseDeDatos.md).

## 9. Qué ocurre durante una solicitud

1. El usuario abre una página estática desde Nginx.
2. El JavaScript del módulo realiza una solicitud a una ruta `/api/...`.
3. Nginx recibe la solicitud y la envía al servicio `backend` dentro de la
   red de Compose.
4. Spring Boot procesa la solicitud y usa la cuenta técnica para conectarse a
   `oracle-db`.
5. Oracle ejecuta los paquetes y operaciones autorizadas.
6. La respuesta vuelve por el backend y Nginx hasta el navegador.

El frontend no accede directamente a Oracle. El backend tampoco utiliza la
cuenta propietaria del esquema para sus operaciones normales; usa la cuenta
técnica configurada en `ORACLE_BACKEND_USER`.

## 10. Problemas frecuentes

### Oracle todavía no está listo

El primer arranque puede tardar varios minutos. Consulta los logs y espera a
que el `healthcheck` sea correcto:

```powershell
docker compose logs --tail=200 oracle-db
docker compose --profile full-stack ps
```

### Se cambiaron los scripts SQL, pero no se reflejan

Los scripts se ejecutan solo con un volumen vacío. No elimines el volumen en
una base con información importante; para cambiar el esquema, restaura el
respaldo en una base nueva y vuelve a inicializar desde ahí.

### El backend no conecta con Oracle

Comprueba que:

- `oracle-db` aparece como `healthy`.
- `ORACLE_BACKEND_USER` y `ORACLE_BACKEND_PASSWORD` coinciden con la cuenta
  creada en Oracle.
- `ORACLE_DATABASE` coincide con el PDB (`XEPDB1` por defecto).
- El backend use `oracle-db` como host, no `localhost`.

### El frontend no carga

Comprueba que el contenedor `frontend` está iniciado y que el puerto publicado
no está ocupado:

```powershell
docker compose --profile full-stack ps
docker compose logs --tail=150 frontend
```

