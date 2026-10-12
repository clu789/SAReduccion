# SAReducción: arquitectura técnica y guía de operación

Sistema para gestionar solicitudes de reducción del monto de inscripción y reinscripción del Instituto Tecnológico de Toluca.

## Diagnóstico del estado actual

El proyecto contiene:

- Frontend estático con Nginx.
- Backend preparado para Spring Boot 2.7.18 y Java 8.
- Base de datos Oracle XE 21c.
- Orquestación mediante Docker Compose.
- Esquema Oracle con tablas, secuencias, triggers y catálogos.

El código Java visible actualmente solo contiene la clase principal de Spring Boot:

```text
src/main/java/com/itt/sareduccion/SareduccionApplication.java
```

No se encontraron controladores `@RestController`, servicios `@Service`, repositorios `@Repository`, configuración de Spring Security ni clases `PasswordEncoder`. Por lo tanto, el frontend y la base de datos están preparados, pero el backend REST todavía no está implementado completamente en este repositorio.

---

## 1. Arquitectura general

```text
                         Red Docker: sareduccion-net
┌─────────────────────────────────────────────────────────────┐
│                                                             │
│  Navegador                                                  │
│      │                                                      │
│      │ http://localhost:8081                               │
│      v                                                      │
│  ┌──────────────────┐                                      │
│  │ frontend         │                                      │
│  │ Nginx             │                                      │
│  │ Puerto interno 80│                                      │
│  └────────┬─────────┘                                      │
│           │ /api/...                                       │
│           v                                                │
│  ┌──────────────────┐                                      │
│  │ backend          │                                      │
│  │ Spring Boot      │                                      │
│  │ Puerto interno   │                                      │
│  │ 8080             │                                      │
│  └────────┬─────────┘                                      │
│           │ JDBC: oracle-db:1521/XEPDB1                    │
│           v                                                │
│  ┌──────────────────┐                                      │
│  │ oracle-db        │                                      │
│  │ Oracle XE 21c    │                                      │
│  │ Puerto interno   │                                      │
│  │ 1521             │                                      │
│  └──────────────────┘                                      │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Puertos

| Componente | Puerto interno | Puerto del host |
|---|---:|---:|
| Nginx | 80 | 8081 |
| Spring Boot | 8080 | 8080 |
| Oracle | 1521 | 1522 |

Direcciones principales:

```text
Frontend solicitante:
http://localhost:8081/solicitante/login.html

Frontend administrativo:
http://localhost:8081/administrativo/login.html

Backend:
http://localhost:8080

Oracle:
localhost:1522
```

### DNS interno de Docker

El servicio definido en `docker-compose.yml` se llama `oracle-db`. Docker registra ese nombre dentro de la red `sareduccion-net`, por lo que el backend puede usar:

```text
oracle-db:1521
```

Desde el backend, `localhost` significa el propio contenedor backend. Por eso esta dirección es incorrecta desde Spring Boot:

```text
localhost:1521
```

La dirección correcta es:

```text
jdbc:oracle:thin:@oracle-db:1521/XEPDB1
```

---

## 2. Nginx

El archivo de configuración es:

```text
docker/nginx/nginx.conf
```

Nginx tiene tres responsabilidades.

### Servir archivos estáticos

```nginx
location / {
    try_files $uri $uri/ =404;
}
```

Entrega archivos HTML, CSS, JavaScript e imágenes desde los módulos montados.

### Redirigir la raíz

```nginx
location = / {
    return 301 $scheme://$http_host/solicitante/login.html;
}
```

Una visita a `http://localhost:8081/` termina en el login del solicitante.

### Proxy hacia Spring Boot

```nginx
location /api/ {
    proxy_pass http://backend:8080/api/;
}
```

Una petición del navegador a:

```text
GET /api/solicitudes
```

se reenvía internamente a:

```text
GET http://backend:8080/api/solicitudes
```

El comentario del archivo menciona CORS, pero la configuración no agrega encabezados `Access-Control-Allow-*`. El diseño evita normalmente CORS porque frontend y API se consumen desde el mismo origen público (`localhost:8081`).

---

## 3. Docker Compose

La configuración completa está en:

```text
docker-compose.yml
```

### Servicio `oracle-db`

```yaml
oracle-db:
  image: gvenzl/oracle-xe:21-slim
  container_name: sareduccion-oracle
  restart: unless-stopped
```

Usa Oracle XE 21c en una imagen ligera, con un nombre fijo y reinicio automático si el proceso falla.

#### Variables de entorno

```yaml
environment:
  ORACLE_PASSWORD: ${ORACLE_PASSWORD}
    ORACLE_APP_USER: ${ORACLE_APP_USER}
    ORACLE_APP_PASSWORD: ${ORACLE_APP_PASSWORD}
    ORACLE_ALUMNO_USER: ${ORACLE_ALUMNO_USER}
    ORACLE_ALUMNO_PASSWORD: ${ORACLE_ALUMNO_PASSWORD}
    ORACLE_ADMIN_USER: ${ORACLE_ADMIN_USER}
    ORACLE_ADMIN_PASSWORD: ${ORACLE_ADMIN_PASSWORD}
```

Los valores se leen de `.env`:

| Variable | Función |
|---|---|
| `ORACLE_PASSWORD` | Contraseña administrativa inicial |
| `ORACLE_DATABASE` | PDB existente de XE 21c, `XEPDB1` (se pasa al inicializador, no al entrypoint de imagen) |
| `ORACLE_APP_USER` | Esquema técnico, `SA_REDUCCION` |
| `ORACLE_APP_PASSWORD` | Contraseña del usuario técnico |
| `ORACLE_ALUMNO_USER` | Usuario Oracle del estudiante, `ALU_21281181` |
| `ORACLE_ADMIN_USER` | Usuario Oracle del administrador, `SA01` |

#### Puerto

```yaml
ports:
  - "${ORACLE_PORT}:1521"
```

Publica el listener de Oracle en el host.

#### Persistencia

```yaml
volumes:
  - oracle_data:/opt/oracle/oradata
```

El volumen nombrado conserva tablas y datos entre reinicios.

#### Scripts de inicialización

```yaml
volumes:
    - ./docker/init-db/init-db.sh:/container-entrypoint-initdb.d/00-init-db.sh:ro
    - ./docker/init-db:/opt/sareduccion/init-db:ro
```

El directorio local se monta dentro del contenedor como directorio de inicialización, en modo solo lectura.

#### Healthcheck

```yaml
healthcheck:
  test: ["CMD-SHELL", "echo 'SELECT 1 FROM DUAL;' | sqlplus -s ..."]
  interval: 30s
  timeout: 10s
  retries: 10
  start_period: 120s
```

Oracle tarda en iniciar porque debe levantar la instancia, abrir el PDB y ejecutar los SQL. El healthcheck espera tanto al listener como al marcador que `init-db.sh` crea tras verificar tablas, paquetes, usuarios y roles.

`start_period: 120s` evita que Docker marque el contenedor como fallido durante el arranque inicial.

### Servicio `backend`

```yaml
backend:
  build:
    context: .
    dockerfile: Dockerfile
```

Docker usa la raíz del proyecto como contexto y construye el backend con `Dockerfile`.

```yaml
environment:
  SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE}
  DB_URL: jdbc:oracle:thin:@oracle-db:1521/${ORACLE_DATABASE}
  DB_USER: ${ORACLE_APP_USER}
  DB_PASSWORD: ${ORACLE_APP_PASSWORD}
```

Spring Boot recibe la conexión mediante variables de entorno.

```yaml
ports:
  - "${APP_PORT}:8080"
```

Con `APP_PORT=8080`, la API queda disponible como `localhost:8080`.

```yaml
depends_on:
  oracle-db:
    condition: service_healthy
```

El backend espera a que Oracle esté saludable antes de iniciar.

```yaml
profiles:
  - full-stack
```

El backend pertenece a un perfil opcional. Por eso:

```bash
docker compose up -d
```

inicia normalmente Oracle y frontend, pero no backend.

Para iniciarlo:

```bash
docker compose --profile full-stack up -d --build
```

### Servicio `frontend`

```yaml
frontend:
  image: nginx:alpine
```

Usa una imagen ligera de Nginx.

```yaml
ports:
  - "8081:80"
```

El puerto 80 interno se publica como 8081 en el host.

```yaml
volumes:
  - ./modulo-administrativo:/usr/share/nginx/html/administrativo:ro
  - ./modulo-solicitante:/usr/share/nginx/html/solicitante:ro
  - ./docker/nginx/nginx.conf:/etc/nginx/conf.d/default.conf:ro
```

Los módulos HTML se sirven directamente desde el host y el contenedor no puede modificarlos.

### Volumen frente a bind mount

Volumen nombrado:

```text
oracle_data:/opt/oracle/oradata
```

Lo administra Docker y contiene los datos reales de Oracle.

Bind mounts:

```text
./docker/init-db
./modulo-administrativo
./modulo-solicitante
./docker/nginx/nginx.conf
```

Apuntan directamente a archivos y carpetas del proyecto.

Modificar un HTML montado puede reflejarse inmediatamente en Nginx. Modificar un SQL no hace que Oracle lo ejecute automáticamente.

### Comandos Compose

```bash
# Oracle y frontend, sin backend
 docker compose up -d

# Todo el stack, construyendo el backend
 docker compose --profile full-stack up -d --build

# Detener sin borrar datos
 docker compose down

# Detener y eliminar el volumen de Oracle
 docker compose down -v
```

`down -v` elimina los datos de Oracle, pero no elimina las imágenes ni los archivos del proyecto.

---

## 4. Dockerfile del backend

El [Dockerfile](Dockerfile) utiliza dos etapas.

### Etapa de compilación

```dockerfile
FROM maven:3.8.8-eclipse-temurin-8 AS build

WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests -B
```

Esta etapa:

1. Usa Maven con Java 8.
2. Copia primero `pom.xml` para aprovechar la caché de dependencias.
3. Descarga dependencias.
4. Copia el código fuente.
5. Compila y genera el JAR.
6. Omite las pruebas mediante `-DskipTests`.

### Etapa de ejecución

```dockerfile
FROM eclipse-temurin:8-jre-alpine
```

La imagen final solo contiene el entorno de ejecución Java, no Maven completo.

```dockerfile
COPY --from=build /app/target/*.jar app.jar
```

Copia el JAR creado en la primera etapa.

```dockerfile
ENV DB_URL=jdbc:oracle:thin:@oracle-db:1521/XEPDB1
ENV DB_USER=sa_reduccion
```

Estos valores son predeterminados. `docker-compose.yml` los sobrescribe con las variables de `.env`.

```dockerfile
EXPOSE 8080
```

Documenta el puerto interno usado por Spring Boot.

```dockerfile
ENTRYPOINT ["java", "-Xmx512m", "-jar", "app.jar"]
```

Inicia el JAR con un máximo de 512 MB de memoria para la JVM.

> Recomendación: no dejar contraseñas reales como valores por defecto en el Dockerfile.

---

## 5. Ciclo de vida de Oracle

La imagen `gvenzl/oracle-xe:21-slim` inicia Oracle XE con el PDB `XEPDB1` ya existente. No se pasa `ORACLE_DATABASE=XEPDB1` al entrypoint de la imagen: al hacerlo, intenta crear un segundo PDB y falla con `ORA-65012`.

Conceptualmente:

```text
Oracle XE
  └── PDB XEPDB1
        └── esquema técnico SA_REDUCCION
```

La cadena JDBC es:

```text
jdbc:oracle:thin:@oracle-db:1521/XEPDB1
```

| Parte | Significado |
|---|---|
| `jdbc` | API estándar de conexión Java |
| `oracle` | Fabricante del controlador |
| `thin` | Controlador JDBC ligero |
| `oracle-db` | Nombre DNS interno de Docker |
| `1521` | Puerto del listener Oracle |
| `XEPDB1` | Nombre del servicio PDB |

### Inicialización única

Los archivos de `/container-entrypoint-initdb.d` solo se ejecutan cuando el directorio de datos está vacío.

Si el volumen ya existe, Oracle conserva el esquema y no vuelve a ejecutar los scripts.

Para reiniciar desde cero:

```bash
docker compose down -v
docker compose --profile full-stack up -d --build
```

> Este procedimiento destruye todos los datos de Oracle.

### Orden de scripts

`init-db.sh` ejecuta los SQL explícitamente, en este orden:

```text
01-configurar-esquema.sql (SYS en XEPDB1)
02-crear-estructura.sql (SA_REDUCCION en XEPDB1)
03-crear-identidades-roles.sql (SYS en XEPDB1)
04-crear-paquetes-permisos.sql (SA_REDUCCION en XEPDB1)
```

Los archivos `.bak` no se ejecutan como scripts de inicialización:

```text
01-init-schema.sql.bak
02-insert-datos-prueba.sql.bak
```

---

## 6. Scripts SQL de inicialización
La descripción de los scripts antiguos que sigue en esta sección es histórica. La inicialización vigente usa los cuatro scripts `docker/init-db/01` a `04` coordinados por `docker/init-db/init-db.sh`; el esquema actual elimina la tabla `Usuarios`. Consulta [BaseDeDatos.md](BaseDeDatos.md) para el modelo vigente.

### `01-grant-dba.sql`

El script `01-grant-dba.sql` fue retirado. `01-configurar-esquema.sql` crea o configura `SA_REDUCCION` con variables de entorno y concede únicamente `CREATE SESSION`, `CREATE TABLE`, `CREATE SEQUENCE`, `CREATE TRIGGER` y `CREATE PROCEDURE`; no concede `DBA`.

### `02-init-shema.sql`

El script crea:

```text
Usuarios
Periodos
Tipo_Solicitudes
Tipo_Preguntas
Estatus_Solicitudes
Estatus_Deducciones
Estudiante
Aspirante
Administradores
Convocatorias
Convocatorias_TipoSolicitudes
Formularios
Preguntas
Formulario_Pregunta
Opciones
Monto_Inscripcion
Monto_Reinscripcion
Solicitudes
Respuestas
Historial_Solicitudes
Deducciones_autorizadas
Historial_Deducciones
```

También crea secuencias y triggers.

### Tablas de seguridad y catálogos

```sql
CREATE TABLE Usuarios (
    id_usuario VARCHAR2(50) PRIMARY KEY,
    password_hash VARCHAR2(255) NOT NULL
);
```

`Usuarios` almacena credenciales de aplicación. No contiene usuarios Oracle.

```sql
CREATE TABLE Periodos (
    idPeriodo NUMBER(2) PRIMARY KEY,
    Descripcion VARCHAR2(50) NOT NULL UNIQUE
);
```

```sql
CREATE TABLE Tipo_Solicitudes (
    id_Tipo_Solicitud NUMBER(10) PRIMARY KEY,
    Nombre VARCHAR2(50) NOT NULL,
    Descripcion VARCHAR2(200)
);
```

```sql
CREATE TABLE Tipo_Preguntas (
    id_Tipo_Pregunta NUMBER(10) PRIMARY KEY,
    Nombre VARCHAR2(50) NOT NULL UNIQUE
);
```

```sql
CREATE TABLE Estatus_Solicitudes (
    id_estatus_solicitud NUMBER(10) PRIMARY KEY,
    Nombre VARCHAR2(100) NOT NULL UNIQUE,
    Descripcion VARCHAR2(200)
);
```

```sql
CREATE TABLE Estatus_Deducciones (
    id_Estatus_Deducciones NUMBER(10) PRIMARY KEY,
    Nombre VARCHAR2(50) NOT NULL,
    Descripcion VARCHAR2(100)
);
```

### Personas

```sql
CREATE TABLE Estudiante (
    Numero_Control VARCHAR2(50) PRIMARY KEY,
    Nombre VARCHAR2(50) NOT NULL,
    PrimerAppellido VARCHAR2(50) NOT NULL,
    SegundoApellido VARCHAR2(50),
    Carrera VARCHAR2(100),
    Semestre NUMBER(2),
    id_usuario VARCHAR2(50),
    FOREIGN KEY (id_usuario) REFERENCES Usuarios(id_usuario),
    UNIQUE (id_usuario),
    CHECK (Semestre IS NULL OR Semestre BETWEEN 1 AND 20)
);
```

```sql
CREATE TABLE Aspirante (
    ficha VARCHAR2(18) NOT NULL,
    periodo NUMBER(2) NOT NULL,
    anio NUMBER(4) NOT NULL,
    claveControl VARCHAR2(50),
    Nombre VARCHAR2(50) NOT NULL,
    PrimerApellido VARCHAR2(50) NOT NULL,
    SegundoApellido VARCHAR2(50),
    Semestre NUMBER(2),
    Curp VARCHAR2(18) NOT NULL,
    correo VARCHAR2(254),
    id_usuario VARCHAR2(50),
    PRIMARY KEY (ficha, periodo, anio),
    FOREIGN KEY (periodo) REFERENCES Periodos(idPeriodo),
    FOREIGN KEY (id_usuario) REFERENCES Usuarios(id_usuario),
    UNIQUE (id_usuario),
    CHECK (anio BETWEEN 2000 AND 9999),
    CHECK (LENGTH(Curp) = 18),
    CHECK (Semestre IS NULL OR Semestre BETWEEN 1 AND 20)
);
```

```sql
CREATE TABLE Administradores (
    idAdministrador VARCHAR2(50) PRIMARY KEY,
    Nombre VARCHAR2(50) NOT NULL,
    PrimerApellido VARCHAR2(50) NOT NULL,
    SegundoApellido VARCHAR2(50),
    id_usuario VARCHAR2(50) NOT NULL,
    FOREIGN KEY (id_usuario) REFERENCES Usuarios(id_usuario),
    UNIQUE (id_usuario)
);
```

Relaciones:

```text
Usuarios 1 ──── 0..1 Estudiante
Usuarios 1 ──── 0..1 Aspirante
Usuarios 1 ──── 0..1 Administradores
Periodos 1 ──── N Aspirante
```

### Convocatorias y montos

```sql
CREATE TABLE Convocatorias (
    id_convocatoria NUMBER(10) PRIMARY KEY,
    fecha_inicio DATE NOT NULL,
    fecha_fin DATE NOT NULL,
    id_periodo NUMBER(2) NOT NULL,
    anio NUMBER(4) NOT NULL,
    FOREIGN KEY (id_periodo) REFERENCES Periodos(idPeriodo),
    CHECK (fecha_fin >= fecha_inicio),
    CHECK (anio BETWEEN 2000 AND 9999)
);
```

```sql
CREATE TABLE Convocatorias_TipoSolicitudes (
    id_convocatoria NUMBER(10) NOT NULL,
    id_Tipo_Solicitud NUMBER(10) NOT NULL,
    monto_deduccion NUMBER(10,2) NOT NULL,
    fecha_asignacion_montos DATE DEFAULT SYSDATE NOT NULL,
    PRIMARY KEY (id_convocatoria, id_Tipo_Solicitud),
    FOREIGN KEY (id_convocatoria)
        REFERENCES Convocatorias(id_convocatoria),
    FOREIGN KEY (id_Tipo_Solicitud)
        REFERENCES Tipo_Solicitudes(id_Tipo_Solicitud),
    CHECK (monto_deduccion >= 0)
);
```

### Formularios

```sql
CREATE TABLE Formularios (
    idFormulario NUMBER(10) PRIMARY KEY,
    Nombre VARCHAR2(100) NOT NULL,
    Descripcion VARCHAR2(200),
    id_Tipo_Solicitud NUMBER(10) NOT NULL,
    FOREIGN KEY (id_Tipo_Solicitud)
        REFERENCES Tipo_Solicitudes(id_Tipo_Solicitud),
    UNIQUE (id_Tipo_Solicitud)
);
```

```sql
CREATE TABLE Preguntas (
    idPregunta NUMBER(10) PRIMARY KEY,
    texto_Pregunta VARCHAR2(4000) NOT NULL,
    obligatoria CHAR(1) DEFAULT 'S' NOT NULL,
    tipo_pregunta NUMBER(10) NOT NULL,
    FOREIGN KEY (tipo_pregunta)
        REFERENCES Tipo_Preguntas(id_Tipo_Pregunta),
    CHECK (obligatoria IN ('S', 'N'))
);
```

```sql
CREATE TABLE Formulario_Pregunta (
    idFormulario NUMBER(10) NOT NULL,
    idPregunta NUMBER(10) NOT NULL,
    orden NUMBER(5) NOT NULL,
    PRIMARY KEY (idFormulario, idPregunta),
    FOREIGN KEY (idFormulario)
        REFERENCES Formularios(idFormulario),
    FOREIGN KEY (idPregunta)
        REFERENCES Preguntas(idPregunta),
    UNIQUE (idFormulario, orden),
    CHECK (orden > 0)
);
```

```sql
CREATE TABLE Opciones (
    id_opcion NUMBER(10) PRIMARY KEY,
    texto_opcion VARCHAR2(255) NOT NULL,
    orden NUMBER(5) NOT NULL,
    id_pregunta NUMBER(10) NOT NULL,
    FOREIGN KEY (id_pregunta)
        REFERENCES Preguntas(idPregunta),
    UNIQUE (id_pregunta, orden),
    CHECK (orden > 0)
);
```

### Solicitudes y respuestas

`Solicitudes` relaciona al solicitante con una convocatoria, formulario, tipo y estado. Puede pertenecer a un estudiante o a un aspirante, pero no a ambos.

`Respuestas` relaciona una solicitud con sus preguntas y opciones.

`Historial_Solicitudes` registra cambios de estado y el administrador involucrado.

`Deducciones_autorizadas` almacena el monto pagado, la deducción y el monto final.

`Historial_Deducciones` conserva cambios de estado de las deducciones.

El DDL exacto y completo de estas tablas está en:

```text
docker/init-db/02-crear-estructura.sql
```

Todas las tablas se crean en el esquema `SA_REDUCCION`, porque el script se ejecuta conectado como ese usuario.

---

## 7. Secuencias y triggers

Oracle utiliza secuencias para generar identificadores:

```sql
CREATE SEQUENCE seq_solicitudes
START WITH 1
INCREMENT BY 1;
```

Los triggers asignan el siguiente valor cuando el identificador llega como `NULL`:

```sql
CREATE OR REPLACE TRIGGER trg_solicitudes_bi
BEFORE INSERT ON Solicitudes
FOR EACH ROW
WHEN (NEW.idSolicitud IS NULL)
BEGIN
    SELECT seq_solicitudes.NEXTVAL
    INTO :NEW.idSolicitud
    FROM dual;
END;
/
```

El esquema también contiene triggers para:

- validar que el formulario corresponda al tipo de solicitud;
- validar respuestas abiertas y de opción;
- comprobar que una opción pertenezca a su pregunta;
- calcular montos de deducción;
- crear historial al cambiar estados;
- propagar cancelaciones;
- actualizar fechas de modificación.

---

## 8. Conexión Spring Boot y Oracle

El archivo activo es:

```text
src/main/resources/application.properties
```

Su contenido es:

```properties
spring.datasource.url=${DB_URL}
spring.datasource.username=${DB_USER}
spring.datasource.password=${DB_PASSWORD}
spring.datasource.driver-class-name=oracle.jdbc.OracleDriver
```

Spring Boot lee las variables del entorno del contenedor.

La dependencia JDBC se encuentra en `pom.xml`:

```xml
<dependency>
    <groupId>com.oracle.database.jdbc</groupId>
    <artifactId>ojdbc8</artifactId>
    <scope>runtime</scope>
</dependency>
```

El proyecto utiliza Spring JDBC, pero no incluye `spring-boot-starter-data-jpa`. Por ello, actualmente no aplica `spring.jpa.hibernate.ddl-auto`.

Si en el futuro se incorporara JPA y el esquema siguiera controlado por scripts SQL, se debería usar:

```properties
spring.jpa.hibernate.ddl-auto=none
```

o, como validación:

```properties
spring.jpa.hibernate.ddl-auto=validate
```

No se recomienda `create` ni `update` cuando la estructura se administra mediante scripts versionados.

Spring Boot incluye HikariCP normalmente a través de sus starters, pero este proyecto no define parámetros personalizados como:

```properties
spring.datasource.hikari.maximum-pool-size=10
```

Por tanto se usan los valores predeterminados.

---

## 9. Autenticación y usuarios

Hay dos tipos de usuario diferentes.

### Usuario Oracle

```text
sa_reduccion
```

Es la cuenta técnica que utiliza Spring Boot para conectarse a Oracle.

### Usuario de la aplicación

Es una fila en la tabla:

```text
Usuarios
```

Ejemplos posibles:

```text
ALU_22280600
SA01
```

Estos identificadores no deben crearse con `CREATE USER` en Oracle. Son usuarios internos de la aplicación.

La tabla actual solo contiene:

```sql
id_usuario
password_hash
```

No existe actualmente una columna `rol`, ni código visible que valide el login.

El frontend administrativo intenta llamar a:

```text
POST /api/auth/login
```

El login del solicitante todavía tiene la llamada real comentada en su JavaScript. Además, no se encontraron `@RestController`, `PasswordEncoder` ni `BCryptPasswordEncoder` en el backend.

Por lo tanto, el método real de autenticación todavía debe implementarse.

### BCrypt

El campo `password_hash VARCHAR2(255)` está preparado para almacenar hashes BCrypt, por ejemplo:

```text
$2a$10$...
```

Una implementación futura debería usar:

```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```

La validación correcta sería:

```java
passwordEncoder.matches(
    passwordIngresada,
    usuario.getPasswordHash()
);
```

Nunca se debe guardar una contraseña como texto plano.

---

## 10. Permisos y seguridad

### Permisos Oracle

`CONNECT` permite conectarse. En instalaciones modernas puede utilizarse:

```sql
GRANT CREATE SESSION TO sa_reduccion;
```

`RESOURCE` es un rol antiguo para creación de objetos.

`DBA` es un privilegio administrativo muy amplio.

En producción, el usuario de la aplicación debería recibir solo los privilegios necesarios, por ejemplo:

```sql
GRANT CREATE SESSION TO sa_reduccion;
GRANT CREATE TABLE TO sa_reduccion;
GRANT CREATE SEQUENCE TO sa_reduccion;
GRANT CREATE TRIGGER TO sa_reduccion;
GRANT CREATE PROCEDURE TO sa_reduccion;
GRANT CREATE VIEW TO sa_reduccion;
ALTER USER sa_reduccion QUOTA 200M ON USERS;
```

La lista exacta depende de si el usuario solo consulta y modifica datos o también crea el esquema.

### Usuarios Oracle adicionales

Solo deben crearse para integraciones técnicas, no para cada estudiante:

```sql
CREATE USER reporte_app
IDENTIFIED BY "PasswordSegura";

GRANT CREATE SESSION TO reporte_app;

GRANT SELECT ON sa_reduccion.Solicitudes
TO reporte_app;

GRANT SELECT ON sa_reduccion.Estudiante
TO reporte_app;
```

Opcionalmente:

```sql
CREATE SYNONYM reporte_app.Solicitudes
FOR sa_reduccion.Solicitudes;
```

### Roles de aplicación

El esquema actual no tiene tablas `Roles`, `Permisos`, `Usuario_Rol` ni `Rol_Permiso`.

La separación actual se basa indirectamente en perfiles:

```text
Usuarios -> Estudiante
Usuarios -> Aspirante
Usuarios -> Administradores
```

Para implementar RBAC formal se recomienda agregar tablas de roles y permisos y validarlas desde Spring Security.

---

## 11. Crear usuarios de aplicación

### Crear `ALU_22280600`

Primero debe existir un hash BCrypt real:

```sql
INSERT INTO Usuarios (
    id_usuario,
    password_hash
)
VALUES (
    'ALU_22280600',
    'HASH_BCRYPT'
);
```

Después se crea el perfil:

```sql
INSERT INTO Estudiante (
    Numero_Control,
    Nombre,
    PrimerAppellido,
    SegundoApellido,
    Carrera,
    Semestre,
    id_usuario
)
VALUES (
    'ALU_22280600',
    'Nombre',
    'Apellido',
    NULL,
    'Ingenieria en Sistemas',
    4,
    'ALU_22280600'
);

COMMIT;
```

### Crear `SA01`

```sql
INSERT INTO Usuarios (
    id_usuario,
    password_hash
)
VALUES (
    'SA01',
    'HASH_BCRYPT'
);

INSERT INTO Administradores (
    idAdministrador,
    Nombre,
    PrimerApellido,
    SegundoApellido,
    id_usuario
)
VALUES (
    'SA01',
    'Administrador',
    'Sistema',
    NULL,
    'SA01'
);

COMMIT;
```

### Generar un hash BCrypt con Java

Cuando el proyecto incluya Spring Security:

```java
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class GenerarHash {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder =
            new BCryptPasswordEncoder();

        System.out.println(
            encoder.encode("MiPasswordSegura")
        );
    }
}
```

### Ejecutar cambios sin borrar la base

Crear un archivo local, por ejemplo `usuarios.sql`, y ejecutar:

```bash
docker compose exec -T oracle-db \
  sqlplus -s sa_reduccion/"CONTRASEÑA"@localhost:1521/XEPDB1 \
  < usuarios.sql
```

También puede copiarse al contenedor:

```bash
docker cp usuarios.sql \
  sareduccion-oracle:/tmp/usuarios.sql
```

Y ejecutarse así:

```bash
docker exec -it sareduccion-oracle \
  sqlplus sa_reduccion/"CONTRASEÑA"@localhost:1521/XEPDB1 \
  @/tmp/usuarios.sql
```

### Ejecutar cambios destruyendo la base

```bash
docker compose down -v
docker compose --profile full-stack up -d --build
```

Esto vuelve a crear Oracle y ejecuta los scripts iniciales.

---

## 12. Flujo funcional esperado

### Solicitante

```text
1. Abre /solicitante/login.html
2. Selecciona alumno o aspirante
3. Envía sus credenciales
4. POST /api/auth/login
5. Consulta el formulario activo
6. Envía respuestas
7. Se crea una solicitud PENDIENTE
8. Puede consultar su estado
9. Puede corregirla si el administrador la devuelve
```

Endpoints esperados:

```text
POST /api/auth/login
GET  /api/solicitudes/mi-solicitud
GET  /api/formularios/activo
POST /api/solicitudes
PUT  /api/solicitudes/{id}
```

### Administrador

```text
1. Abre /administrativo/login.html
2. Inicia sesión
3. Consulta la bandeja
4. Filtra por estado
5. Abre el detalle
6. Revisa respuestas e historial
7. Aprueba o devuelve a corrección
8. Se actualiza el estado
9. Se registra el historial
```

Endpoints esperados:

```text
POST /api/auth/login
GET  /api/solicitudes
GET  /api/solicitudes?estatus=PENDIENTE
GET  /api/solicitudes/estadisticas
GET  /api/solicitudes/{id}
PUT  /api/solicitudes/{id}/aprobar
PUT  /api/solicitudes/{id}/correccion
POST /api/notificaciones
```

Actualmente estos endpoints están documentados y consumidos por el frontend, pero no se encontraron los controladores Java que los implementen.

---

## 13. Solución de problemas

| Error | Causa probable | Diagnóstico | Solución |
|---|---|---|---|
| `ORA-01017` | Usuario o contraseña incorrectos | `docker compose logs oracle-db` | Revisar `.env` y `docker/init-db/01-configurar-esquema.sql` |
| `ORA-00942` | Tabla inexistente o sin permisos | `SELECT table_name FROM user_tables;` | Crear esquema o conceder permisos |
| `ORA-01031` | Privilegios insuficientes | Revisar privilegios del usuario | Conceder solo el permiso necesario |
| `ORA-28000` | Cuenta Oracle bloqueada | Consultar `dba_users` como SYS | Desbloquear la cuenta |
| Backend no aparece | Perfil no activado | `docker compose ps -a` | Usar `--profile full-stack` |
| Oracle `Exited(1)` | Error de credenciales o volumen | `docker compose logs oracle-db` | Corregir `.env` o recrear volumen |
| Frontend `Exited(1)` | Error en Nginx o montaje | `docker compose logs frontend` | Revisar `nginx.conf` y rutas |
| Backend no conecta | Oracle no está listo o JDBC incorrecto | `docker compose logs backend` | Usar `oracle-db:1521/XEPDB1` |
| API devuelve `404` | No existe controlador Spring | `curl -i http://localhost:8080/api` | Implementar `@RestController` |
| Login redirige sin validar | Login simulado en JavaScript | Revisar `modulo-solicitante/js/app.js` | Conectar el formulario con el backend |

Comandos de diagnóstico:

```bash
docker compose ps -a
docker compose logs oracle-db
docker compose logs backend
docker compose logs frontend
docker inspect sareduccion-oracle
```

---

## 14. Comandos útiles

```bash
# Levantar Oracle
docker compose up -d oracle-db

# Levantar frontend
docker compose up -d frontend

# Levantar backend y todo el stack
docker compose --profile full-stack up -d --build

# Ver estado
docker compose ps
docker compose ps -a

# Ver logs
docker compose logs -f oracle-db
docker compose logs -f backend
docker compose logs -f frontend

# Reiniciar sin perder datos
docker compose restart oracle-db
docker compose restart backend
docker compose restart frontend

# Detener sin borrar datos
docker compose down

# Destruir datos de Oracle
docker compose down -v

# Ver la configuración resuelta
docker compose config

# Inspeccionar la red
docker network inspect sareduccion-net

# Entrar al contenedor Oracle
docker exec -it sareduccion-oracle bash

# Conectarse como usuario de aplicación
docker exec -it sareduccion-oracle \
  sqlplus sa_reduccion/"CONTRASEÑA"@localhost:1521/XEPDB1

# Conectarse como SYSDBA
docker exec -it sareduccion-oracle \
  sqlplus sys/"CONTRASEÑA_SYS"@localhost:1521/XEPDB1 as sysdba
```

Dentro de SQL*Plus:

```sql
-- Listar tablas
SELECT table_name
FROM user_tables
ORDER BY table_name;

-- Ver columnas
DESC Usuarios;
DESC Solicitudes;
DESC Estudiante;

-- Consultar datos
SELECT * FROM Usuarios;
SELECT * FROM Estudiante;
SELECT * FROM Administradores;
SELECT * FROM Solicitudes;

-- Ver secuencias
SELECT sequence_name
FROM user_sequences
ORDER BY sequence_name;

-- Ver triggers
SELECT trigger_name, status
FROM user_triggers
ORDER BY trigger_name;
```

Para probar la API:

```bash
curl -i http://localhost:8080/api
```

Puede devolver `404` mientras no existan controladores Spring que implementen esa ruta.

---

## Resumen ejecutivo

1. SAReducción usa Nginx, Spring Boot, Oracle XE y Docker Compose.
2. Nginx sirve HTML, CSS y JavaScript y reenvía `/api` al backend.
3. Oracle se alcanza desde Spring Boot mediante `oracle-db:1521`.
4. `localhost` dentro del backend no representa al contenedor Oracle.
5. El backend solo se inicia mediante el perfil `full-stack`.
6. `oracle_data` conserva la base entre reinicios.
7. Los scripts SQL solo se ejecutan al crear el volumen por primera vez.
8. `down -v` elimina la base y permite repetir la inicialización.
9. `sa_reduccion` es el usuario técnico de Oracle, no un usuario final.
10. `ALU_22280600` y `SA01` deben ser filas de `Usuarios`, no cuentas Oracle.
11. El esquema actual no tiene roles y permisos de aplicación formales.
12. No existe código visible de login, BCrypt, controladores o servicios.
13. `GRANT DBA` es excesivo para producción.
14. La base contiene tablas, secuencias y triggers para solicitudes e historiales.
15. Los cambios sobre una base existente deben aplicarse como migraciones manuales.
16. Antes de desplegar, se debe implementar y probar el backend REST y la autenticación.
