# 📘 Guía del Proyecto — SAReducción

> **Sistema de Reducción de Monto de Inscripción**
> Instituto Tecnológico de Toluca (ITT)

---

## 📋 Índice

1. [¿Qué hace el proyecto?](#-qué-hace-el-proyecto)
2. [Estructura de carpetas](#-estructura-de-carpetas)
3. [Tecnologías utilizadas](#-tecnologías-utilizadas)
4. [Cómo funciona el proyecto](#-cómo-funciona-el-proyecto)
5. [Entorno Docker — librerías y herramientas incluidas](#-entorno-docker--librerías-y-herramientas-incluidas)
6. [Comandos Docker](#-comandos-docker)
7. [Variables de entorno (.env)](#-variables-de-entorno-env)

---

## 🎯 ¿Qué hace el proyecto?

SAReducción es un sistema web que permite a los **estudiantes del ITT** solicitar una reducción en su monto de inscripción. El sistema cuenta con dos interfaces:

- **Módulo Solicitante** — El alumno inicia sesión, llena su formulario de solicitud y puede consultar el estatus de su trámite.
- **Módulo Administrativo** — El personal del ITT gestiona, revisa y actualiza el estatus de las solicitudes recibidas.

Toda la lógica de negocio vive en un **backend REST** que se comunica con una **base de datos Oracle**, validando credenciales y procesando solicitudes.

---

## 📁 Estructura de carpetas

```
SAReduccion/
│
├── modulo-solicitante/          → Frontend del alumno/aspirante
├── modulo-administrativo/       → Frontend del personal administrativo
├── src/                         → Backend Java (Spring Boot)
├── docker/                      → Infraestructura Docker
├── backups/oracle/              → Respaldos Data Pump (no versionado)
├── Antecedentes/                → Recursos y estilos institucionales del ITT
├── target/                      → Artefactos compilados por Maven (auto-generado)
│
├── docker-compose.yml           → Orquestación de servicios Docker
├── Dockerfile                   → Construcción de la imagen del backend
├── pom.xml                      → Dependencias Maven del backend
├── .env                         → Variables de entorno locales (NO subir a Git)
├── .env.example                 → Plantilla de variables de entorno
├── ARQUITECTURA.md              → Documentación de arquitectura del sistema
└── BaseDeDatos.md               → Documentación del esquema de base de datos
```

---

### 🖥️ `modulo-solicitante/` — Frontend del Alumno

Interfaz web estática servida por Nginx. Contiene las pantallas que usa el estudiante.

| Archivo / Carpeta | Descripción |
|---|---|
| `login.html` | Inicio de sesión de alumno y aspirante |
| `formulario.html` | Formulario para registrar la solicitud de reducción |
| `panel-estatus.html` | Panel y seguimiento del alumno autenticado |
| `panel-aspirante.html` | Panel y seguimiento del aspirante autenticado |
| `css/` | Hojas de estilo del módulo |
| `js/` | Lógica JavaScript (llamadas al backend, validaciones) |
| `img/` | Imágenes e íconos del módulo |

---

### 🛡️ `modulo-administrativo/` — Frontend del Administrador

Interfaz web estática servida por Nginx. Contiene las pantallas que usa el personal del ITT.

| Archivo / Carpeta | Descripción |
|---|---|
| `login.html` | Pantalla de inicio de sesión del administrador |
| `panel-admin.html` | Panel de gestión de solicitudes de alumnos |
| `css/` | Hojas de estilo del módulo |
| `js/` | Lógica JavaScript del panel administrativo |
| `img/` | Imágenes e íconos del módulo |

---

### ☕ `src/` — Backend Java (Spring Boot)

Código fuente del servidor REST. Sigue la estructura estándar de Maven.

```
src/main/java/com/itt/sareduccion/
│
├── SareduccionApplication.java   → Punto de entrada (main) de la aplicación
│
├── auth/                         → Capa de autenticación y seguridad
│   ├── AuthenticationService.java    → Lógica central de login
│   ├── OracleCredentialValidator.java→ Valida credenciales contra Oracle DB
│   ├── LoginAttemptLimiter.java      → Limita intentos de login fallidos
│   ├── AuthRole.java                 → Roles del sistema (STUDENT, ADMIN, APPLICANT)
│   ├── ApplicantPrincipal.java       → Identidad del aspirante autenticado
│   ├── AuthenticatedPrincipal.java   → Interfaz base del usuario autenticado
│   ├── SessionAttributes.java        → Nombres de atributos de la sesión HTTP
│   └── SessionPrincipal.java         → Datos guardados en la sesión HTTP
│
├── controller/                   → Endpoints REST (API HTTP)
│   ├── AuthController.java           → Login, /me y logout de los tres perfiles
│   └── SolicitudAdminController.java → Consulta y resolución de solicitudes
│
├── service/                      → Reglas de negocio
│   ├── SolicitudAdminService.java    → Reglas del panel administrativo
│   └── ReglaNegocioException.java    → Conflicto de negocio (HTTP 409)
│
├── model/                        → Modelos / entidades de datos
│   ├── ApplicantIdentity.java        → Identidad del aspirante
│   ├── ConvocatoriaAdmin.java        → Convocatoria para el panel
│   ├── DeduccionAdmin.java           → Deducción autorizada
│   ├── EstudianteDatos.java          → Datos del estudiante traídos de la BD
│   ├── RespuestaSolicitud.java       → Respuesta de un formulario
│   └── SolicitudAdmin.java           → Solicitud vista por administración
│
└── repository/                   → Acceso a base de datos (JDBC)
    ├── ApplicantRepository.java      → Consultas relacionadas a aspirantes
    ├── AdministradorRepository.java  → Consultas relacionadas a administradores
    └── EstudianteRepository.java     → Consultas de datos de estudiantes

src/main/resources/
└── application.properties        → Configuración del servidor (DB, sesión, pool)
```

---

### 🐳 `docker/` — Infraestructura

| Subcarpeta | Descripción |
|---|---|
| `init-db/` | Scripts SQL numerados que inicializan la BD al levantar Oracle por primera vez |
| `nginx/` | Archivo `nginx.conf` que configura el servidor web para los frontends |

**Scripts de inicialización de BD (`init-db/`):**

| Script | Qué hace |
|---|---|
| `01-configurar-esquema.sql` | Crea el esquema `SA_REDUCCION` y el directorio de respaldos |
| `02-crear-estructura.sql` | Crea tablas, restricciones, índices, secuencias, triggers y catálogos base |
| `03-crear-identidades-roles.sql` | Crea roles y cuentas individuales de estudiante y administrador |
| `04-crear-cuenta-tecnica.sql` | Crea la cuenta técnica `SAR_BACKEND` del backend (JDBC) |
| `05-crear-paquetes-permisos.sql` | Crea los cuatro paquetes PL/SQL y concede `EXECUTE` sobre los públicos |
| `06-seed-desarrollo.sql` | Inserta datos ficticios si `SAR_LOAD_DEV_SEED=true` |
| `init-db.sh` | Orquesta la ejecución en orden y verifica el resultado |

---

### 📚 `Antecedentes/ESTILOS_ITT/`

Recursos de diseño institucional del ITT utilizados como referencia visual para los módulos frontend.

| Subcarpeta | Contenido |
|---|---|
| `css/` | Hojas de estilo oficiales del ITT |
| `fonts/` | Tipografías institucionales |
| `img/` | Logotipos e imágenes oficiales |
| `js/` | Scripts de componentes institucionales |

---

## 🛠️ Tecnologías utilizadas

### Backend

| Tecnología | Versión | Rol |
|---|---|---|
| Java | 8 (1.8) | Lenguaje del backend |
| Spring Boot | 2.7.18 | Framework principal del servidor REST |
| Spring Web (Tomcat) | — | Servidor HTTP embebido y manejo de rutas REST |
| Spring JDBC | — | Acceso a base de datos sin ORM |
| Oracle JDBC (`ojdbc8`) | — | Driver para conectarse a Oracle Database |
| HikariCP | — | Pool de conexiones a la BD (configurado con máx. 10) |
| Lombok | — | Reduce código repetitivo (getters, setters, etc.) |
| Maven | 3.8.8 | Gestor de dependencias y compilación |

### Frontend

| Tecnología | Rol |
|---|---|
| HTML5 | Estructura de las páginas |
| CSS3 (Vanilla) | Estilos de los módulos |
| JavaScript (Vanilla) | Lógica del cliente y llamadas al backend |
| Nginx (Alpine) | Servidor que sirve los archivos estáticos en Docker |

### Base de Datos

| Tecnología | Versión | Rol |
|---|---|---|
| Oracle Database XE | 21c | Motor de base de datos principal |
| Oracle PDB | XEPDB1 | Base de datos contenedora (Pluggable Database) |

### Infraestructura

| Tecnología | Rol |
|---|---|
| Docker | Contenerización de servicios |
| Docker Compose | Orquestación multi-contenedor |
| Nginx | Reverse proxy / servidor de archivos estáticos |

---

## ⚙️ Cómo funciona el proyecto

```
┌──────────────────────┐        HTTP         ┌─────────────────────┐
│  Navegador del       │ ──────────────────▶ │  Nginx (Frontend)   │
│  Alumno / Admin      │                     │  puerto 8081        │
│                      │ ◀────────────────── │  /solicitante       │
└──────────────────────┘    HTML/CSS/JS       │  /administrativo    │
                                             └─────────────────────┘
                                                      │
                                               fetch()
                                                      │ REST API
                                                      ▼
                                             ┌─────────────────────┐
                                             │  Spring Boot API    │
                                             │  puerto 8080        │
                                             │  /api/...           │
                                             └─────────────────────┘
                                                      │
                                                  JDBC (ojdbc8)
                                                      │
                                                      ▼
                                             ┌─────────────────────┐
                                             │  Oracle XE 21c      │
                                             │  puerto 1522        │
                                             │  PDB: XEPDB1        │
                                             └─────────────────────┘
```

1. El **navegador** accede al frontend servido por **Nginx** en el puerto `8081`.
2. El frontend hace llamadas **REST** al backend en el puerto `8080`.
3. El **backend Spring Boot** valida la sesión, procesa la lógica y consulta la **base de datos Oracle**.
4. Oracle verifica las credenciales de estudiante o administrador, y el backend comprueba además que la cuenta tenga exactamente uno de los roles esperados (`ROL_SAR_ESTUDIANTE_SIM` o `ROL_SAR_ADMIN_SIM`).
5. Las sesiones se mantienen con cookies HTTP-only del lado del servidor. Las operaciones de negocio se ejecutan con la cuenta técnica `SAR_BACKEND` a través de los paquetes PL/SQL.

---

## 🐳 Entorno Docker — librerías y herramientas incluidas

> **No necesitas instalar nada manualmente.** Docker provee todo el entorno.

### Servicios del `docker-compose.yml`

| Servicio | Imagen base | Puerto | Incluye |
|---|---|---|---|
| `oracle-db` | `gvenzl/oracle-xe:21-slim` | `1522` | Oracle Database XE 21c completo, scripts de inicialización automáticos |
| `backend` | `maven:3.8.8-eclipse-temurin-8` → `eclipse-temurin:8-jre-alpine` | `8080` | Java 8, Maven 3.8.8, Spring Boot 2.7.18, todas las dependencias del `pom.xml` |
| `frontend` | `nginx:alpine` | `8081` | Nginx Alpine, sirve los módulos HTML estáticos |

### Construcción del backend (multi-stage `Dockerfile`)

```dockerfile
# ETAPA 1 — Compilación
FROM maven:3.8.8-eclipse-temurin-8   # Java 8 + Maven 3.8.8
RUN mvn dependency:go-offline         # Descarga todas las dependencias
RUN mvn clean package -DskipTests     # Compila y genera el .jar

# ETAPA 2 — Runtime mínimo
FROM eclipse-temurin:8-jre-alpine     # Solo el JRE (imagen ligera)
# Ejecuta el .jar con opciones de JVM optimizadas para contenedor
```

### Librerías Java incluidas automáticamente (via `pom.xml`)

| Librería | Descripción |
|---|---|
| `spring-boot-starter-web` | Tomcat embebido + Spring MVC (REST) |
| `spring-boot-starter-jdbc` | Plantillas JDBC de Spring |
| `ojdbc8` | Driver oficial de Oracle para Java 8 |
| `HikariCP` | Pool de conexiones (incluido en Spring Boot) |
| `lombok` | Procesador de anotaciones (solo en compilación) |
| `spring-boot-starter-test` | JUnit, Mockito (solo en pruebas) |

---

## 🚀 Comandos Docker

### Prerequisito — Crear el archivo `.env`

```bash
# Copiar la plantilla y llenar las credenciales
cp .env.example .env
# Editar .env con tus contraseñas
```

---

### Comandos con Docker Compose

```bash
# Levantar SOLO Oracle:
docker compose up -d oracle-db

# Levantar el stack COMPLETO (Oracle + Backend + Frontend):
docker compose --profile full-stack up -d

# Reconstruir el backend (tras cambios en el código Java):
docker compose --profile full-stack up -d --build backend

# Ver el estado de los contenedores:
docker compose --profile full-stack ps

# Ver logs de un servicio específico:
docker compose logs -f oracle-db
docker compose logs -f backend
docker compose logs -f frontend

# Detener todos los servicios (conserva los datos de Oracle):
docker compose down

# Detener Y ELIMINAR los datos de Oracle (cuidado):
docker compose down -v
```

---

### URLs de acceso tras levantar el entorno

| Servicio | URL |
|---|---|
| Frontend — Módulo Solicitante | http://localhost:8081/solicitante |
| Frontend — Módulo Administrativo | http://localhost:8081/administrativo |
| Backend API REST | http://localhost:8080/api |
| Oracle DB (conexión JDBC) | `jdbc:oracle:thin:@localhost:1522/XEPDB1` |

---

## 🔐 Variables de entorno (`.env`)

El archivo `.env` **no se sube a Git** (está en `.gitignore`). Cópialo de `.env.example`:

| Variable | Descripción | Valor por defecto |
|---|---|---|
| `ORACLE_PASSWORD` | Contraseña del usuario SYS/SYSTEM de Oracle | _(requerido)_ |
| `ORACLE_APP_USER` | Propietario del esquema (inicialización) | `SA_REDUCCION` |
| `ORACLE_APP_PASSWORD` | Contraseña del propietario del esquema | _(requerido)_ |
| `ORACLE_BACKEND_USER` | Cuenta técnica JDBC del backend | `SAR_BACKEND` |
| `ORACLE_BACKEND_PASSWORD` | Contraseña de la cuenta técnica | _(requerido)_ |
| `ORACLE_ALUMNO_USER` | Usuario de sesión del estudiante | `ALU_21281181` |
| `ORACLE_ALUMNO_PASSWORD` | Contraseña del usuario estudiante | _(requerido)_ |
| `ORACLE_ADMIN_USER` | Usuario de sesión del administrador | `SA01` |
| `ORACLE_ADMIN_PASSWORD` | Contraseña del usuario administrador | _(requerido)_ |
| `ORACLE_DATABASE` | Nombre del PDB de Oracle | `XEPDB1` |
| `ORACLE_PORT` | Puerto expuesto de Oracle al host | `1522` |
| `SAR_LOAD_DEV_SEED` | Carga los datos ficticios en una instalación nueva | `true` |
| `APP_PORT` | Puerto del backend Spring Boot | `8080` |
| `FRONTEND_PORT` | Puerto del frontend Nginx | `8081` |
| `SPRING_PROFILES_ACTIVE` | Perfil de Spring activo | `dev` |

---

> 📄 Para más detalles sobre la arquitectura del sistema consulta [`ARQUITECTURA.md`](./ARQUITECTURA.md).
> 🗄️ Para el detalle del esquema de base de datos consulta [`BaseDeDatos.md`](./BaseDeDatos.md).
