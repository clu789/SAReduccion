# Sistema de Gestión de Descuentos – Subdirección Académica

El presente documento detalla la estructura y propósito de las entidades para el módulo de descuentos.

## 1. Módulo de Autenticación e Identidad

Este módulo incluye tablas que simulan información existente en el sistema escolar. Validará accesos y consultará información sin pedirle al usuario que vuelva a ingresarla.

### Tabla: usuarios
**Propósito:** Controlar el inicio de sesión y definir los permisos. 

| Atributo | Tipo | Restricciones | Descripción |
| :--- | :--- | :--- | :--- |
| `id_usuario` | INT | PK, Auto-increment | Llave primaria. |
| `identificador` | VARCHAR(50) | UNIQUE, NOT NULL | Credencial de acceso (alu + Número de control para alumnos, CURP para aspirantes, ID para el admin). |
| `password_hash` | VARCHAR(255) | NOT NULL | Contraseña encriptada (Para aspirantes su folio). |
| `rol` | VARCHAR(20) | NOT NULL | Define las vistas del sistema: `ADMIN`, `ALUMNO` o `ASPIRANTE`. |

**Relaciones:** 1:1 con `alumnos` o `aspirantes`. 1:N con `solicitudes` e `historial_estatus_solicitud`.

### Tabla: alumnos
**Propósito:** Almacena los datos fijos de los estudiantes de reinscripción.

| Atributo | Tipo | Restricciones | Descripción |
| :--- | :--- | :--- | :--- |
| `id_alumno` | INT | PK, Auto-increment | Llave primaria. |
| `id_usuario` | INT | UNIQUE, NOT NULL | FK a `usuarios.id_usuario`. |
| `nombre` | VARCHAR(150) | NOT NULL | Nombre completo. |
| `semestre` | INT | NOT NULL | Semestre a cursar. |

### Tabla: aspirantes
**Propósito:** Almacena los datos fijos de los solicitantes de nuevo ingreso.

| Atributo | Tipo | Restricciones | Descripción |
| :--- | :--- | :--- | :--- |
| `id_aspirante` | INT | PK, Auto-increment | Llave primaria. |
| `id_usuario` | INT | UNIQUE, NOT NULL | FK a `usuarios.id_usuario`. |
| `nombre` | VARCHAR(150) | NOT NULL | Nombre completo. |

---

## 2. Módulo de Gestión de Solicitudes

Se registra el trámite y se auditan los cambios de estado (aprobaciones o rechazos de la Subdirección).

### Tabla: estatus_solicitud
**Propósito:** Catálogo estático para evitar valores "quemados" (hardcodeados) en el código.

| Atributo | Tipo | Restricciones | Descripción |
| :--- | :--- | :--- | :--- |
| `id_estatus` | INT | PK, Auto-increment | Ej. 1, 2, 3... |
| `nombre` | VARCHAR(50) | UNIQUE, NOT NULL | Clave del estado (ej. `PENDIENTE`, `CORRECCION`, `APROBADO`). |
| `descripcion` | VARCHAR(255) | NULL | Texto explicativo para la interfaz de usuario. |

**Relaciones:** 1:N con `solicitudes` e `historial_estatus_solicitud`.

### Tabla: solicitudes
**Propósito:** Asocia a la persona que inició sesión con su petición actual.

| Atributo | Tipo | Restricciones | Descripción |
| :--- | :--- | :--- | :--- |
| `id_solicitud` | INT | PK, Auto-increment | Folio interno único del trámite. |
| `id_usuario` | INT | NOT NULL | FK a `usuarios.id_usuario`. Identifica quién lo solicitó. |
| `id_estatus` | INT | NOT NULL | FK a `estatus_solicitud.id_estatus`. Muestra el estado en tiempo real. |
| `fecha_solicitud` | DATETIME | NOT NULL | Cuándo se creó el trámite. |
| `fecha_actualizacion` | DATETIME | NULL | Última vez que cambió de estado o se editaron respuestas. |

**Relaciones:** 1:N con `respuestas` e `historial_estatus_solicitud`.

### Tabla: historial_estatus_solicitud
**Propósito:** Registra las solicitudes realilzadas.

| Atributo | Tipo | Restricciones | Descripción |
| :--- | :--- | :--- | :--- |
| `id_historial` | INT | PK, Auto-increment | Identificador del movimiento. |
| `id_solicitud` | INT | NOT NULL | FK a `solicitudes.id_solicitud`. |
| `id_estatus_nuevo` | INT | NOT NULL | FK a `estatus_solicitud.id_estatus`. A qué estado pasó. |
| `id_administrador` | INT | NULL | FK a `usuarios.id_usuario`. Qué subdirector dictaminó el cambio. |
| `comentario` | TEXT | NULL | Instrucciones de corrección para el alumno. |
| `fecha` | DATETIME | NOT NULL | Timestamp exacto del cambio. |

---

## 3. Módulo de Formularios Dinámicos

Evita modificar la base de datos si la Subdirección decide pedir información distinta en el futuro.

### Tabla: formularios
**Propósito:** Agrupa las preguntas activas para una convocatoria.

| Atributo | Tipo | Restricciones | Descripción |
| :--- | :--- | :--- | :--- |
| `id_formulario` | INT | PK, Auto-increment | Llave primaria. |
| `nombre` | VARCHAR(150) | NOT NULL | Ej. "Formulario de Descuento 2026-B". |
| `descripcion` | TEXT | NULL | Indicaciones generales. |
| `activo` | BOOLEAN | NOT NULL, Default: true | Si está en `false`, no se muestran sus preguntas. |

**Relaciones:** 1:N con `preguntas`.

### Tabla: preguntas
**Propósito:** Define cada campo que se debe renderizar.

| Atributo | Tipo | Restricciones | Descripción |
| :--- | :--- | :--- | :--- |
| `id_pregunta` | INT | PK, Auto-increment | Llave primaria. |
| `id_formulario` | INT | NOT NULL | FK a `formularios.id_formulario`. |
| `pregunta` | TEXT | NOT NULL | La etiqueta visible (ej. "Claves del trabajador"). |
| `tipo` | VARCHAR(30) | NOT NULL | Componente UI: `TEXT`, `NUMBER`, `SELECT`, etc. |


**Relaciones:** 1:N con `respuestas`.

### Tabla: respuestas
**Propósito:** Guarda la información que capturó el usuario en el formulario.

| Atributo | Tipo | Restricciones | Descripción |
| :--- | :--- | :--- | :--- |
| `id_respuesta` | INT | PK, Auto-increment | Llave primaria. |
| `id_solicitud` | INT | NOT NULL | FK a `solicitudes.id_solicitud`. |
| `id_pregunta` | INT | NOT NULL | FK a `preguntas.id_pregunta`. |
| `respuesta` | TEXT | NULL | Lo que escribió el usuario (guardado como texto plano). |
| `fecha_respuesta` | DATETIME | NOT NULL | Fecha de guardado. |



![alt text](image.png)


# 4. Flujo del Sistema y Registro de Datos

## Paso 1: Autenticación y Simulación de Identidad
El sistema asume que las personas ya existen antes de interactuar con el módulo.
* **Acción:** El alumno o aspirante inicia sesión en el portal.
* **Base de Datos:** La tabla `usuarios` actúa como el motor de autenticación validando el `identificador` (matrícula o CURP). Las tablas `alumnos` o `aspirantes` contienen los datos fijos (nombre, semestre). 
## Paso 2: Generación del Trámite
El usuario el usuario llena el cuestionario dinámico y presiona enviar. En una sola transacción de base de datos ocurren tres procesos simultáneos:
* **`INSERT` en `solicitudes`:** Se crea el registro usando el `id_usuario` en sesión y asignando el estado inicial "PENDIENTE".
* **`INSERT` en `respuestas`:** Se guardan múltiples filas vinculando el texto capturado con cada pregunta del formulario.
* **`INSERT` en `historial_estatus_solicitud`:** Se crea el primer registro de la bitácora marcando el paso a "PENDIENTE" (con comentario en `NULL` y administrador en `NULL`).

## Paso 3: Revisión (Corrección)
El Subdirector abre la bandeja de pendientes, analiza la solicitus y realiza un comentario devolviendo el trámite.
* **`UPDATE` en `solicitudes`:** Se actualiza el estado de la solicitud principal a "CORRECCION".
* **`INSERT` en `historial_estatus_solicitud`:** Se crea una nueva fila grabando el nuevo estado, el `id_usuario` del Subdirector, la fecha y el texto del comentario justificando el rechazo (ej. "Sube nuevamente el comprobante").

## Paso 4: Subsanación del Usuario
El usuario entra al sistema, lee el comentario que dejó el Subdirector y corrige su información.
* **`UPDATE` en `respuestas`:** Se sobrescribe únicamente el dato que estaba mal.
* **`UPDATE` en `solicitudes`:** Se regresa el estado del registro principal a "PENDIENTE".
* **`INSERT` en `historial_estatus_solicitud`:** Se crea una tercera fila indicando que el trámite regresó a la bandeja de revisión (comentario en `NULL` y administrador en `NULL`).

## Paso 5: Aprobación Definitiva
El Subdirector revisa los datos corregidos, valida que coincidan y otorga el descuento.
* **`UPDATE` en `solicitudes`:** Se actualiza el estado del registro principal a "APROBADO".
* **`INSERT` en `historial_estatus_solicitud`:** Se crea una cuarta y última fila. Este evento dispara la notificación y se aplica el descuento.

---

### Nota: (Ejemplo de un trámite con corrección)

A lo largo del flujo anterior, la tabla principal siempre conservó **un solo registro** que se fue actualizando, mientras que la tabla de auditoría acumuló el rastro de todos los movimientos:

| Etapa del flujo | Tabla `solicitudes` (Se actualiza) | Tabla `historial` (Se insertan filas nuevas) |
| :--- | :--- | :--- |
| **1. Creación** | Estado actual: `PENDIENTE` | **Fila 1:** `PENDIENTE` <br> Admin: `NULL` <br> Comentario: `NULL` |
| **2. Rechazo** | Estado actual: `CORRECCION` | **Fila 2:** `CORRECCION` <br> Admin: `ID_5` (Subdirector) <br> Comentario: *"Falta firma"* |
| **3. Corrección** | Estado actual: `PENDIENTE` | **Fila 3:** `PENDIENTE` <br> Admin: `NULL` (Alumno) <br> Comentario: `NULL` |
| **4. Aprobación** | Estado actual: `APROBADO` | **Fila 4:** `APROBADO` <br> Admin: `ID_5` (Subdirector) <br> Comentario: `NULL` |