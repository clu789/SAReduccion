# SAReduccion

Sistema Administrativo de Reduccion del monto de inscripcion del Instituto Tecnologico de Toluca.

El proyecto administra solicitudes de reduccion para estudiantes de reinscripcion y aspirantes de nuevo ingreso. Actualmente contiene dos modulos frontend estaticos, un backend Spring Boot en preparacion y un entorno Docker para Oracle, Nginx y el backend.

## 1. Tecnologias confirmadas

| Area | Tecnologia | Uso actual |
| --- | --- | --- |
| Lenguaje backend | Java 8 | Version configurada en `pom.xml` y Dockerfile. |
| Framework backend | Spring Boot 2.7.18 | Aplicacion backend y API REST. |
| API web | Spring Web | Dependencia `spring-boot-starter-web`. |
| Acceso a datos | Spring JDBC | Dependencia `spring-boot-starter-jdbc`. |
| Driver de base de datos | Oracle JDBC `ojdbc8` | Conexion de Java con Oracle. |
| Base de datos | Oracle XE 21c Slim | Servicio `oracle-db` para desarrollo. |
| Frontend | HTML5 | Paginas de los modulos administrativo y solicitante. |
| Estilos | CSS3 | Hojas de estilo propias de cada modulo. |
| JavaScript | JavaScript ES6+ | Interaccion, validaciones y peticiones `fetch()`. |
| Iconos | Font Awesome 4.7 por CDN | Iconos usados en las paginas HTML. |
| Servidor frontend | Nginx Alpine | Sirve los archivos estaticos y hace proxy de `/api/`. |
| Construccion | Apache Maven 3.8.8 | Descarga dependencias, compila y crea el JAR. |
| Contenedores | Docker | Empaqueta y ejecuta servicios aislados. |
| Orquestacion local | Docker Compose | Levanta Oracle, backend y frontend. |
| Utilidad Java | Lombok | Reduce codigo repetitivo en las clases Java. |
| Pruebas Java | Spring Boot Test | Dependencia disponible para pruebas. |
| Control de versiones | Git | Repositorio del proyecto. |

## 2. Herramientas del proyecto

- **Maven:** administra las dependencias y construye el backend desde `pom.xml`.
- **Docker Compose:** coordina los servicios definidos en `docker-compose.yml`.
- **Dockerfile:** construye el backend en dos etapas: compilacion con Maven y ejecucion con Java Runtime.
- **Oracle SQL:** los scripts de `docker/init-db/` crean el esquema y los datos iniciales.
- **Nginx:** sirve los modulos estaticos y redirige las peticiones `/api/` al backend.
- **Git:** registra los cambios del codigo y la configuracion.

Postman, Spring Security, Spring Data JPA, JasperReports y Spring Validation no estan configurados actualmente en `pom.xml`. Pueden incorporarse despues si el alcance los requiere, pero no se documentan como tecnologias activas.

## 3. Arquitectura actual

```text
Navegador
   |
   v
Nginx :8081
   |\
   | \__ Archivos estaticos
   |
   \____ /api/ -> Backend Spring Boot :8080
                         |
                         v
                  Oracle XE :1521
```

### Frontend

El frontend esta dividido en dos modulos independientes:

- `modulo-solicitante/`: login, captura de formulario y consulta del estatus.
- `modulo-administrativo/`: login administrativo, bandeja y revision de solicitudes.

Los modulos utilizan HTML, CSS y JavaScript sin un framework frontend. Las peticiones al backend se realizan con `fetch()`.

### Backend

El proyecto Spring Boot se encuentra en `src/main/`. Actualmente contiene la clase principal de arranque:

```text
src/main/java/com/itt/sareduccion/SareduccionApplication.java
```

La estructura de controladores, servicios y repositorios esta contemplada para el desarrollo de la API, pero debe considerarse pendiente hasta que existan esas clases en `src/main`.

### Base de datos

Oracle XE se ejecuta en Docker. El esquema se crea con:

[`docker/init-db/02-init-shema.sql`](docker/init-db/02-init-shema.sql)

La documentacion detallada de las tablas se encuentra en [BaseDeDatos.md](BaseDeDatos.md).

## 4. Servicios Docker

### `oracle-db`

- Imagen: `gvenzl/oracle-xe:21-slim`.
- Contenedor: `sareduccion-oracle`.
- Puerto local: `1521`.
- Volumen: `sareduccion_oracle_data`.
- Ejecuta los scripts de `docker/init-db/` durante la primera inicializacion.

### `backend`

- Se construye con `Dockerfile`.
- Imagen de compilacion: Maven 3.8.8 con Eclipse Temurin 8.
- Imagen de ejecucion: Eclipse Temurin 8 JRE Alpine.
- Puerto local: `8080`.
- Se activa con el perfil Compose `full-stack`.

### `frontend`

- Imagen: `nginx:alpine`.
- Contenedor: `sareduccion-frontend`.
- Puerto local: `8081`.
- Sirve `/administrativo` y `/solicitante`.
- Usa `docker/nginx/nginx.conf` para el proxy hacia el backend.

## 5. Requisitos

- Docker Engine o Docker Desktop.
- Docker Compose v2, disponible como `docker compose`.
- Git.
- Para ejecutar el backend fuera de Docker: JDK 8 y Maven.

## 6. Configuracion

Las variables de entorno se definen en `.env` en la raiz del proyecto. Entre ellas se encuentran:

```text
ORACLE_PASSWORD
ORACLE_DATABASE
ORACLE_APP_USER
ORACLE_APP_PASSWORD
ORACLE_PORT
APP_PORT
SPRING_PROFILES_ACTIVE
```

No se deben publicar contrasenas reales ni credenciales de produccion en el repositorio. Para desarrollo se deben usar credenciales locales.

## 7. Ejecucion

### Solo la base de datos

```bash
docker compose up oracle-db -d
```

Oracle puede tardar varios minutos en estar disponible la primera vez.

### Solo el frontend

```bash
docker compose up frontend -d
```

Accesos:

- Solicitante: http://localhost:8081/solicitante/login.html
- Administrativo: http://localhost:8081/administrativo/login.html

### Backend y base de datos

```bash
docker compose --profile full-stack up --build
```

La API queda disponible en `http://localhost:8080` cuando existan los controladores implementados.

### Todo el entorno

```bash
docker compose up --build
```

El servicio backend requiere el perfil `full-stack`; por eso, para levantar backend, frontend y base de datos se recomienda:

```bash
docker compose --profile full-stack up --build
```

### Detener servicios

```bash
docker compose down
```

Para eliminar tambien la base de datos de desarrollo y volver a ejecutar los scripts SQL:

```bash
docker compose down -v
docker compose --profile full-stack up --build
```

## 8. Construccion manual del backend

Desde la raiz del proyecto:

```bash
mvn clean package
java -jar target/sareduccion-0.0.1-SNAPSHOT.jar
```

Para compilar sin ejecutar pruebas:

```bash
mvn clean package -DskipTests
```

El Dockerfile usa el mismo proceso durante su etapa de compilacion.

## 9. API y frontend

Los modulos frontend estan preparados para consumir rutas como las siguientes:

| Metodo | Ruta | Proposito |
| --- | --- | --- |
| `POST` | `/api/auth/login` | Iniciar sesion. |
| `GET` | `/api/solicitudes` | Consultar solicitudes. |
| `GET` | `/api/solicitudes/{id}` | Consultar el detalle de una solicitud. |
| `POST` | `/api/solicitudes` | Crear una solicitud. |
| `PUT` | `/api/solicitudes/{id}` | Actualizar una solicitud. |

Estas rutas son contratos esperados por el frontend. Deben implementarse en controladores Spring Boot antes de considerarlas disponibles.

## 10. Seguridad y pendientes

El proyecto tiene configuradas variables de entorno para la conexion a Oracle, pero todavia deben implementarse y verificarse:

- Autenticacion y autorizacion de usuarios.
- Proteccion de endpoints.
- Validacion de datos en el backend.
- Manejo centralizado de errores.
- Pruebas de los endpoints.
- Almacenamiento y descarga de documentos.
- Integracion con servicios institucionales.

La validacion en JavaScript del frontend no sustituye la validacion del backend.

## 11. Estructura principal

```text
SAReduccion/
├── docker-compose.yml
├── Dockerfile
├── pom.xml
├── .env
├── readme.md
├── BaseDeDatos.md
├── docker/
│   ├── init-db/
│   │   └── 02-init-shema.sql
│   └── nginx/
│       └── nginx.conf
├── modulo-administrativo/
├── modulo-solicitante/
└── src/
    └── main/
        └── java/com/itt/sareduccion/
```

## 12. Documentacion relacionada

- [Base de datos](BaseDeDatos.md)
- [Guia de Docker](README-DOCKER.md)
- [Modulo administrativo](modulo-administrativo/README.md)
- [Modulo solicitante](modulo-solicitante/README.md)
