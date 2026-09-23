# Servicio Administrativo de Reducción del Monto de Inscripción

Aplicación web del Instituto Tecnológico de Toluca destinada a gestionar el proceso de solicitud y consulta relacionado con el apoyo para inscripción y reinscripción de hijos y/o cónyuge de trabajadores del TecNM y sistemas homologados.

El proyecto surge como una evolución tecnológica del sistema anterior, reemplazando la arquitectura basada en Java EE, JSP, Servlets, Apache Ant y Payara Server por una arquitectura basada en Spring Boot y una API REST.

---

# 1. Descripción del proyecto

El sistema permitirá gestionar digitalmente el proceso administrativo relacionado con las solicitudes de apoyo para inscripción y reinscripción.

De acuerdo con la convocatoria, el proceso contempla la captura en línea de la solicitud y la presentación posterior de documentación. Para alumnos de reinscripción y residencia profesional se contempla, entre otros documentos, el kardex vigente del estudiante y documentación relacionada con el trabajador.

El sistema deberá permitir, de acuerdo con el alcance definido para el proyecto:

* Captura de solicitudes.
* Consulta de información del alumno.
* Consulta del estado de una solicitud.
* Registro y validación de información.
* Gestión de documentos requeridos.
* Consulta de información administrativa.
* Generación de documentos o comprobantes cuando sea necesario.
* Comunicación entre frontend y backend mediante una API REST.

---

# 2. Objetivos tecnológicos

La nueva implementación tiene como objetivos:

1. Sustituir la arquitectura Java EE utilizada anteriormente.
2. Eliminar la dependencia de Payara Server como servidor de aplicaciones.
3. Utilizar Spring Boot como plataforma principal del backend.
4. Exponer las operaciones del sistema mediante una API REST.
5. Mantener compatibilidad con Java 8.
6. Mantener Oracle como sistema gestor de base de datos mientras la aplicación dependa de la infraestructura institucional existente.
7. Separar las responsabilidades del backend mediante una arquitectura por capas.
8. Utilizar tecnologías frontend independientes del servidor de aplicaciones.
9. Facilitar la ejecución del entorno de desarrollo mediante Docker.
10. Utilizar herramientas modernas de construcción, control de versiones y pruebas.

---

# 3. Arquitectura general

La aplicación utilizará una arquitectura cliente-servidor.

```text
┌──────────────────────────────────────┐
│              FRONTEND                │
│                                      │
│ HTML + CSS + JavaScript              │
│ Bootstrap + Font Awesome             │
└──────────────────┬───────────────────┘
                   │
                   │ HTTP / JSON
                   ▼
┌──────────────────────────────────────┐
│             BACKEND                  │
│                                      │
│ Spring Boot                          │
│                                      │
│ REST Controllers                     │
│        ↓                             │
│ Services                             │
│        ↓                             │
│ Repository / DAO                     │
└──────────────────┬───────────────────┘
                   │
                   │ JDBC
                   ▼
┌──────────────────────────────────────┐
│          ORACLE DATABASE             │
│                                      │
│ Tablas / consultas / procedimientos  │
└──────────────────────────────────────┘
```

El frontend realizará peticiones HTTP al backend y recibirá información principalmente en formato JSON.

El backend será responsable de la lógica de negocio, validación, seguridad, acceso a datos y generación de respuestas.

---

# 4. Backend

## Spring Boot

Spring Boot será la plataforma principal para el desarrollo del backend.

Se utilizará para:

* Crear la aplicación web.
* Administrar los componentes de la aplicación.
* Crear la API REST.
* Configurar el acceso a la base de datos.
* Administrar dependencias.
* Proporcionar el servidor web embebido.
* Gestionar configuración y componentes de Spring.

La aplicación podrá ejecutarse como un archivo JAR utilizando el servidor web embebido.

```text
java -jar aplicacion.jar
```

---

# 5. Versión de Java

## JDK 8

El proyecto utilizará:

```text
Java 8
```

La decisión se debe a la compatibilidad con el entorno tecnológico existente.

La versión seleccionada de Spring Boot será:

```text
Spring Boot 2.7.18
```

Spring Boot 2.7.18 requiere Java 8 y permite utilizar servidores Servlet embebidos como Tomcat 9.

Esta decisión deberá revisarse si posteriormente el proyecto puede migrar a una versión más reciente de Java.

---

# 6. API REST

La comunicación entre frontend y backend se realizará mediante una API REST.

Ejemplo conceptual:

```text
POST /api/solicitudes
GET  /api/solicitudes/{id}
PUT  /api/solicitudes/{id}
DELETE /api/solicitudes/{id}
```

Las respuestas utilizarán JSON cuando corresponda.

Ejemplo:

```json
{
    "id": 25,
    "estado": "EN_REVISION",
    "fechaSolicitud": "2026-01-08"
}
```

Los antiguos Servlets del sistema serán sustituidos por controladores de Spring.

Conceptualmente:

```text
Servlet
   ↓
REST Controller
```

No se implementarán Servlets manualmente como mecanismo principal de control de las peticiones.

---

# 7. Arquitectura interna del backend

El backend utilizará separación de responsabilidades.

```text
REST Controller
       ↓
Service
       ↓
Repository / DAO
       ↓
Database
```

## Controller

Responsable de:

* Recibir peticiones HTTP.
* Validar parámetros básicos.
* Invocar servicios.
* Construir respuestas HTTP.

## Service

Responsable de:

* Implementar reglas de negocio.
* Coordinar operaciones.
* Validar procesos.
* Coordinar diferentes repositorios.

## Repository / DAO

Responsable de:

* Acceder a la base de datos.
* Ejecutar consultas.
* Ejecutar procedimientos almacenados cuando sean necesarios.
* Convertir resultados de base de datos a objetos Java.

---

# 8. Acceso a datos

El sistema utilizará Oracle Database como sistema gestor de base de datos mientras se mantenga la integración con la infraestructura institucional existente.

La implementación anterior utilizaba JDBC, Oracle y procedimientos almacenados. Por lo tanto, antes de realizar una migración completa a JPA/Hibernate se deberá identificar qué operaciones actuales dependen directamente de Oracle.

La tecnología de acceso a datos podrá utilizar:

```text
Spring JDBC
```

cuando se requiera trabajar directamente con consultas o procedimientos almacenados.

Si se desarrollan nuevas entidades y operaciones que no dependan de procedimientos existentes, podrá evaluarse:

```text
Spring Data JPA
```

La elección entre JDBC y JPA deberá realizarse por módulo y no asumirse automáticamente para todo el sistema.

---

# 9. Base de datos

## Oracle Database

Oracle será la base de datos principal del proyecto debido a la dependencia existente con el sistema institucional.

La versión exacta de Oracle deberá determinarse con base en el ambiente donde será ejecutada la aplicación.

No se asumirá que Oracle 12c XE es la versión de producción hasta verificar el ambiente institucional.

---

# 10. Frontend

El frontend será inicialmente desarrollado utilizando tecnologías web estándar:

```text
HTML
CSS
JavaScript
Bootstrap
Font Awesome
```

No se utilizará inicialmente un framework frontend como:

* React
* Angular
* Vue

La decisión podrá revisarse posteriormente si el tamaño o complejidad de la interfaz lo justifica.

---

# 11. HTML

HTML será utilizado para definir la estructura de las páginas:

* Formularios.
* Tablas.
* Botones.
* Menús.
* Secciones.
* Contenedores.
* Elementos de navegación.

---

# 12. CSS

CSS será utilizado para:

* Personalizar la apariencia.
* Ajustar tamaños.
* Espaciado.
* Tipografía.
* Componentes específicos del sistema.

Bootstrap será utilizado como base para el diseño visual y responsivo.

---

# 13. JavaScript

JavaScript será utilizado para la lógica ejecutada en el navegador.

Entre sus responsabilidades estarán:

* Validación de formularios.
* Manejo de eventos.
* Actualización de elementos de la interfaz.
* Comunicación con la API REST.
* Procesamiento de respuestas JSON.
* Control de modales y componentes dinámicos.

La comunicación con el backend utilizará principalmente:

```javascript
fetch()
```

Por lo tanto, jQuery no será una dependencia obligatoria del nuevo sistema.

---

# 14. Bootstrap

Bootstrap será utilizado para facilitar la creación de una interfaz:

* Responsiva.
* Consistente.
* Adaptable a diferentes tamaños de pantalla.

Se utilizará para componentes como:

* Formularios.
* Botones.
* Tablas.
* Modales.
* Alertas.
* Navegación.

---

# 15. Font Awesome

Font Awesome será utilizado para proporcionar iconos a los elementos de la interfaz.

Ejemplos:

* Usuarios.
* Solicitudes.
* Documentos.
* Alertas.
* Acciones.
* Navegación.

---

# 16. JSON

JSON será el formato principal para intercambiar información entre frontend y backend.

Flujo general:

```text
JavaScript
    ↓
HTTP Request
    ↓
Spring Boot REST Controller
    ↓
Service
    ↓
Database
    ↓
Service
    ↓
REST Controller
    ↓
JSON
    ↓
JavaScript
```

La utilización de JSON permite separar la interfaz de usuario de la lógica del servidor.

---

# 17. Seguridad

El sistema deberá contemplar mecanismos de seguridad para proteger:

* Autenticación.
* Sesiones o tokens.
* Información personal.
* Solicitudes.
* Documentos.
* Endpoints protegidos.

Se evaluará la utilización de:

```text
Spring Security
```

La implementación específica dependerá del mecanismo de autenticación disponible en el entorno institucional.

---

# 18. Validación

Las entradas recibidas por la API deberán validarse antes de ejecutar operaciones.

Se evaluará el uso de:

```text
Spring Validation
```

Las validaciones deberán aplicarse tanto a:

* Datos recibidos desde el frontend.
* Parámetros de las peticiones.
* Información enviada a la base de datos.

La validación del frontend no sustituye la validación del backend.

---

# 19. Generación de documentos

El sistema anterior utiliza JasperReports e iText para generar y procesar documentos PDF.

Estas tecnologías podrán conservarse si el nuevo alcance requiere:

* Vouchers.
* Comprobantes.
* Reportes.
* Constancias.
* Documentos descargables.

La generación de documentos deberá integrarse como parte del backend.

Flujo:

```text
Solicitud
   ↓
Spring Boot
   ↓
Datos
   ↓
JasperReports / PDF
   ↓
Documento
   ↓
Usuario
```

---

# 20. Maven

Maven será utilizado como sistema de construcción y administración de dependencias.

Responsabilidades:

* Descargar dependencias.
* Compilar el proyecto.
* Ejecutar pruebas.
* Empaquetar la aplicación.
* Ejecutar plugins.
* Generar el archivo JAR.

El proyecto utilizará Maven en lugar de Apache Ant.

Ejemplo:

```bash
mvn clean
mvn test
mvn package
```

---

# 21. Servidor web

No se utilizará Payara Server como servidor principal.

Spring Boot utilizará un servidor Servlet embebido, normalmente Tomcat.

Arquitectura:

```text
Spring Boot
     ↓
Tomcat embebido
     ↓
HTTP
```

Esto elimina la necesidad de instalar y configurar Payara para ejecutar el backend.

---

# 22. Docker

Docker será utilizado principalmente para facilitar la creación de ambientes reproducibles de desarrollo.

El objetivo no es sustituir Spring Boot, sino facilitar la ejecución de los servicios que necesite el proyecto.

Dependiendo del ambiente disponible, Docker podrá utilizarse para:

* Base de datos de desarrollo.
* Servicios auxiliares.
* Backend.
* Entorno completo de desarrollo.

La base de datos institucional de producción no deberá introducirse automáticamente en Docker.

---

# 23. Docker Compose

Docker Compose podrá utilizarse para levantar múltiples servicios de desarrollo.

Ejemplo conceptual:

```text
Docker Compose
       │
       ├── Database
       │
       └── Backend
```

En un ambiente donde Spring Boot se ejecute directamente en la computadora del desarrollador:

```text
Computadora
│
├── Spring Boot
│
└── Docker
      └── Oracle / Base de datos de desarrollo
```

Esto permite que cada integrante pueda iniciar el ambiente de desarrollo sin realizar manualmente toda la configuración.

---

# 24. Git

Git será utilizado para el control de versiones.

El repositorio deberá permitir:

* Controlar cambios.
* Trabajar mediante ramas.
* Revisar modificaciones.
* Recuperar versiones anteriores.
* Integrar cambios de diferentes integrantes.

Se recomienda utilizar ramas para funcionalidades independientes.

---

# 25. Pruebas de API

Se utilizará una herramienta como:

```text
Postman
```

o una alternativa equivalente para probar los endpoints REST.

Ejemplo:

```text
POST /api/solicitudes
GET  /api/solicitudes/25
PUT  /api/solicitudes/25
```

Las pruebas deberán verificar:

* Código HTTP.
* Estructura JSON.
* Validaciones.
* Errores.
* Autenticación.
* Comportamiento esperado.

---

# 26. Tecnologías principales

| Área                 | Tecnología                     | Propósito                       |
| -------------------- | ------------------------------ | ------------------------------- |
| Lenguaje             | Java 8                         | Lenguaje principal              |
| Framework backend    | Spring Boot 2.7.18             | Desarrollo del backend          |
| API                  | REST                           | Comunicación cliente-servidor   |
| Servidor             | Tomcat embebido                | Servidor HTTP                   |
| Build                | Maven                          | Compilación y dependencias      |
| Base de datos        | Oracle                         | Persistencia de información     |
| Acceso a datos       | Spring JDBC / JPA según módulo | Comunicación con Oracle         |
| Frontend             | HTML                           | Estructura                      |
| Estilos              | CSS                            | Personalización visual          |
| UI                   | Bootstrap                      | Componentes y diseño responsivo |
| Scripts              | JavaScript                     | Lógica del navegador            |
| Iconos               | Font Awesome                   | Iconografía                     |
| Comunicación         | JSON                           | Intercambio de datos            |
| Seguridad            | Spring Security                | Autenticación y autorización    |
| Validación           | Spring Validation              | Validación de datos             |
| Reportes             | JasperReports                  | Generación de reportes          |
| PDF                  | Biblioteca PDF según necesidad | Generación de documentos        |
| Control de versiones | Git                            | Control del código              |
| Contenedores         | Docker                         | Ambientes reproducibles         |
| Orquestación local   | Docker Compose                 | Ejecución de servicios          |
| Pruebas API          | Postman                        | Pruebas de endpoints            |

---

# 27. Tecnologías descartadas de la arquitectura anterior

Las siguientes tecnologías formaban parte de la implementación anterior, pero no serán componentes principales de la nueva arquitectura:

```text
Java EE
JSP
Servlets manuales
Apache Ant
Payara Server
GlassFish
web.xml como mecanismo principal de configuración
jQuery
WAR como formato principal de despliegue
```

Esto no significa que todas desaparezcan de manera absoluta del ecosistema Java, sino que dejan de ser componentes principales de la arquitectura propuesta.

---

# 28. Equivalencia entre arquitectura anterior y nueva

| Arquitectura anterior | Nueva arquitectura      |
| --------------------- | ----------------------- |
| Java EE               | Spring Boot             |
| Servlet               | REST Controller         |
| JSP                   | HTML + JavaScript       |
| AJAX/jQuery           | Fetch API               |
| DAO                   | Repository / DAO        |
| Bean                  | Entity / DTO            |
| web.xml               | Configuración de Spring |
| Payara                | Tomcat embebido         |
| WAR                   | JAR ejecutable          |
| Apache Ant            | Maven                   |
| JDBC Oracle           | Spring JDBC / JPA       |
| Gson                  | Jackson / JSON          |
| Bootstrap             | Bootstrap               |
| Font Awesome          | Font Awesome            |
| JasperReports         | JasperReports           |

---

# 29. Flujo general de funcionamiento

```text
Usuario
   ↓
Navegador
   ↓
HTML + CSS + JavaScript
   ↓
Fetch API
   ↓
HTTP / JSON
   ↓
Spring Boot
   ↓
REST Controller
   ↓
Service
   ↓
Repository / DAO
   ↓
Oracle
   ↓
Repository / DAO
   ↓
Service
   ↓
REST Controller
   ↓
JSON / PDF
   ↓
Navegador
   ↓
Usuario
```

---

# 30. Flujo de una solicitud

Ejemplo conceptual:

```text
1. El alumno abre el sistema.
2. El frontend muestra el formulario.
3. El alumno captura la información.
4. JavaScript valida los datos básicos.
5. JavaScript envía una petición HTTP.
6. Spring Boot recibe la petición.
7. El Controller recibe los datos.
8. El Service aplica las reglas de negocio.
9. El Repository/DAO consulta o modifica Oracle.
10. Oracle devuelve el resultado.
11. El Service procesa el resultado.
12. El Controller genera una respuesta.
13. La API devuelve JSON.
14. JavaScript actualiza la interfaz.
```

---

# 31. Principios de desarrollo

El proyecto deberá seguir los siguientes principios:

* Separación de responsabilidades.
* Bajo acoplamiento entre componentes.
* Validación en backend.
* Uso de DTO para intercambio de datos cuando sea necesario.
* Manejo centralizado de errores.
* Configuración externa de credenciales.
* No almacenar contraseñas o credenciales directamente en el código.
* Uso de variables de entorno para información sensible.
* Control de versiones mediante Git.
* Pruebas de los endpoints principales.
* Documentación de la API.

---

# 32. Decisiones pendientes

Antes de comenzar el desarrollo deberán definirse:

1. Versión exacta de Oracle disponible.
2. Forma de autenticación.
3. Si se utilizarán procedimientos almacenados existentes.
4. Módulos que utilizarán JDBC.
5. Módulos que podrán utilizar JPA.
6. Requerimientos exactos de generación de PDF.
7. Ubicación y almacenamiento de documentos.
8. Si el frontend será servido por Spring Boot o desplegado de forma independiente.
9. Configuración de Docker para desarrollo.
10. Configuración de conexión con la base institucional.

Estas decisiones deberán realizarse antes de implementar los módulos que dependan de ellas.

---

# 33. Resumen de la arquitectura propuesta

```text
                    USUARIO
                       │
                       ▼
              ┌─────────────────┐
              │    FRONTEND     │
              │                 │
              │ HTML            │
              │ CSS             │
              │ JavaScript      │
              │ Bootstrap       │
              │ Font Awesome    │
              └────────┬────────┘
                       │
                    HTTP/JSON
                       │
                       ▼
              ┌─────────────────┐
              │   SPRING BOOT   │
              │                 │
              │ REST Controller │
              │       ↓         │
              │    Service      │
              │       ↓         │
              │ Repository/DAO  │
              └────────┬────────┘
                       │
                      JDBC
                       │
                       ▼
              ┌─────────────────┐
              │     ORACLE      │
              │                 │
              │ Datos           │
              │ Consultas       │
              │ Procedimientos  │
              └─────────────────┘

       Herramientas de soporte:

       Git
       Maven
       Docker
       Docker Compose
       Postman
```
