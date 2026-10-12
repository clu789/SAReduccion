# Base de datos de SAReduccion

## 1. Propósito

La base de datos administra las solicitudes de reducción del monto de inscripción y reinscripción. El modelo utiliza Oracle XE 21c y se construye con los scripts de inicialización:

[`docker/init-db/02-crear-estructura.sql`](docker/init-db/02-crear-estructura.sql)

El script crea tablas, claves primarias, claves foráneas, restricciones, índices, secuencias, triggers y datos iniciales de catálogos. Esta versión elimina la tabla `Usuarios` y utiliza los identificadores de los perfiles para relacionar la identidad con Oracle.

## 2. Ubicación y ejecución

`docker/init-db/init-db.sh` ejecuta los cuatro SQL en orden. La imagen conecta los `.sql` de `/container-entrypoint-initdb.d` como `SYS` en `XE`, así que el orquestador cambia a `XEPDB1` y conecta como `SA_REDUCCION` para crear el esquema y los paquetes. Solo el orquestador se monta en el directorio de auto-inicialización; los SQL se montan en una ruta separada para evitar su ejecución implícita con un usuario o contexto incorrecto.

Los scripts se ejecutan solo con un volumen Oracle vacío. Para reconstruir el entorno desde cero:

```bash
docker compose down -v
docker compose up -d oracle-db
```

**Advertencia:** `down -v` elimina los datos persistidos en los volúmenes del proyecto. No debe ejecutarse sobre una base con información que se quiera conservar. Reemplazar el archivo SQL no migra automáticamente una base existente.

Las verificaciones manuales están en `database/tests/` y no se ejecutan automáticamente.

## 3. Modelo de datos

**PK**: clave primaria; **FK**: clave foránea; **UK**: unicidad; **NULL**: opcional; **NOT NULL**: obligatorio. Las restricciones indicadas corresponden al script V5.

### 3.1 Catálogos y seguridad

#### Tabla: `Periodos`

Almacena los registros de `Periodos` y sus relaciones definidas en el esquema.

| Atributo | Tipo | Restricciones | Descripción |
| --- | --- | --- | --- |

#### Tabla: `Tipo_Solicitudes`

Almacena los registros de `Tipo_Solicitudes` y sus relaciones definidas en el esquema.

| Atributo | Tipo | Restricciones | Descripción |
| --- | --- | --- | --- |

#### Tabla: `Tipo_Preguntas`

Almacena los registros de `Tipo_Preguntas` y sus relaciones definidas en el esquema.

| Atributo | Tipo | Restricciones | Descripción |
| --- | --- | --- | --- |

#### Tabla: `Estatus_Solicitudes`

Estados iniciales:

| Atributo | Tipo | Restricciones | Descripción |
| --- | --- | --- | --- |

#### Tabla: `Estatus_Deducciones`

Almacena los registros de `Estatus_Deducciones` y sus relaciones definidas en el esquema.

| Atributo | Tipo | Restricciones | Descripción |
| --- | --- | --- | --- |

### 3.2 Personas y perfiles

#### Tabla: `Estudiantes`

Almacena los estudiantes identificados por su número de control, sin columna `id_usuario`.

| Atributo | Tipo | Restricciones | Descripción |
| --- | --- | --- | --- |

#### Tabla: `Aspirantes`

Registra aspirantes mediante la clave compuesta `(ficha, periodo, anio)`, sin tabla de usuarios independiente.

| Atributo | Tipo | Restricciones | Descripción |
| --- | --- | --- | --- |

#### Tabla: `Administradores`

Registra al personal administrador identificado mediante `idAdministrador`, sin columna `id_usuario`.

| Atributo | Tipo | Restricciones | Descripción |
| --- | --- | --- | --- |

### 3.3 Convocatorias y montos

#### Tabla: `Convocatorias`

Almacena los registros de `Convocatorias` y sus relaciones definidas en el esquema.

| Atributo | Tipo | Restricciones | Descripción |
| --- | --- | --- | --- |
| `fecha_inicio` | `DATE` | NOT NULL, CHECK | Fecha en que inicia la convocatoria. |
| `fecha_fin` | `DATE` | NOT NULL, CHECK | Fecha en que termina la convocatoria. |

#### Tabla: `Convocatorias_TipoSolicitudes`

Almacena los registros de `Convocatorias_TipoSolicitudes` y sus relaciones definidas en el esquema.

| Atributo | Tipo | Restricciones | Descripción |
| --- | --- | --- | --- |
| `fecha_asignacion_montos` | `DATE` | NOT NULL, DEFAULT `SYSDATE` | Fecha en que se asigno el monto. |

#### Tabla: `Monto_Inscripciones`

Almacena los registros de `Monto_Inscripciones` y sus relaciones definidas en el esquema.

| Atributo | Tipo | Restricciones | Descripción |
| --- | --- | --- | --- |

#### Tabla: `Monto_Reinscripciones`

Almacena los registros de `Monto_Reinscripciones` y sus relaciones definidas en el esquema.

| Atributo | Tipo | Restricciones | Descripción |
| --- | --- | --- | --- |

### 3.4 Formularios y preguntas

#### Tabla: `Formularios`

Almacena los registros de `Formularios` y sus relaciones definidas en el esquema.

| Atributo | Tipo | Restricciones | Descripción |
| --- | --- | --- | --- |

#### Tabla: `Preguntas`

Almacena los registros de `Preguntas` y sus relaciones definidas en el esquema.

| Atributo | Tipo | Restricciones | Descripción |
| --- | --- | --- | --- |

#### Tabla: `Formulario_Preguntas`

Almacena los registros de `Formulario_Preguntas` y sus relaciones definidas en el esquema.

| Atributo | Tipo | Restricciones | Descripción |
| --- | --- | --- | --- |

#### Tabla: `Opciones`

Almacena los registros de `Opciones` y sus relaciones definidas en el esquema.

| Atributo | Tipo | Restricciones | Descripción |
| --- | --- | --- | --- |

### 3.5 Solicitudes y respuestas

#### Tabla: `Solicitudes`

La restriccion `ck_solicitud_origen` exige una de estas dos opciones:

| Atributo | Tipo | Restricciones | Descripción |
| --- | --- | --- | --- |
| `fecha_Solicitud` | `DATE` | NOT NULL, DEFAULT `SYSDATE` | Fecha de creacion de la solicitud. |
| `fecha_actualizacion` | `DATE` | NOT NULL, DEFAULT `SYSDATE` | Fecha de la ultima actualizacion. |

La restricción `ck_solicitud_origen` exige que una solicitud pertenezca a un estudiante o a un aspirante, nunca a ambos. `uk_solicitud_estudiante_conv_tipo` impide duplicar la combinación de estudiante, convocatoria y tipo de solicitud.

#### Tabla: `Respuestas`

El indice `ux_respuesta_opcion` evita repetir la misma opcion para una pregunta dentro de una solicitud. El trigger `trg_respuestas_validacion` revisa que el texto u opcion correspondan al tipo de pregunta.

| Atributo | Tipo | Restricciones | Descripción |
| --- | --- | --- | --- |

El índice único `ux_respuesta_solicitud_pregunta` permite una sola fila de respuesta por pregunta y solicitud. El trigger de validación verifica la pertenencia de la pregunta al formulario y el tipo de respuesta.

### 3.6 Historial y deducciones

#### Tabla: `Historial_Solicitudes`

Almacena los registros de `Historial_Solicitudes` y sus relaciones definidas en el esquema.

| Atributo | Tipo | Restricciones | Descripción |
| --- | --- | --- | --- |
| `fecha_Registro` | `DATE` | NOT NULL, DEFAULT `SYSDATE` | Fecha del registro. |

#### Tabla: `Deducciones_autorizadas`

La restriccion `ck_da_monto_origen` obliga a usar exactamente un monto: inscripcion o reinscripcion. El trigger `trg_deducciones_calculo` calcula:

| Atributo | Tipo | Restricciones | Descripción |
| --- | --- | --- | --- |
| `fecha_registro` | `DATE` | NOT NULL, DEFAULT `SYSDATE` | Fecha de registro. |
| `fecha_autorizacion` | `DATE` | NULL | Fecha de autorizacion. |
| `fecha_actualizacion` | `DATE` | NOT NULL, DEFAULT `SYSDATE` | Fecha de la ultima actualizacion. |

`ck_da_monto_origen` exige elegir exclusivamente un monto de inscripción o reinscripción; `ck_da_montos` exige `0 <= monto_deduccion <= monto_pago`, y `ck_da_monto_final` comprueba `monto_final = monto_pago - monto_deduccion`. La deducción es única por solicitud.

#### Tabla: `Historial_Deducciones`

Almacena los registros de `Historial_Deducciones` y sus relaciones definidas en el esquema.

| Atributo | Tipo | Restricciones | Descripción |
| --- | --- | --- | --- |
| `fecha_registro` | `DATE` | NOT NULL, DEFAULT `SYSDATE` | Fecha del registro. |

## 4. Relaciones principales

```text
Periodos ── Aspirantes
         └── Convocatorias ── Convocatorias_TipoSolicitudes ── Tipo_Solicitudes
Estudiantes ── Solicitudes ── Respuestas ── Preguntas ── Tipo_Preguntas
Aspirantes ────┘                 └── Opciones
Formularios ── Formulario_Preguntas ── Preguntas
Solicitudes ── Historial_Solicitudes ── Administradores
          └── Deducciones_autorizadas ── Historial_Deducciones
                    ├── Monto_Inscripciones
                    ├── Monto_Reinscripciones
                    └── Estatus_Deducciones
```

## 5. Secuencias y triggers

El script define 15 secuencias y triggers de asignación de identificadores. `Periodos`, `Estudiantes`, `Aspirantes` y `Administradores` utilizan las claves definidas en sus tablas sin secuencia propia.

Los triggers de negocio comprueban la correspondencia entre formulario y solicitud, el periodo y año del aspirante frente a la convocatoria, la pertenencia y el formato de las respuestas, y el cálculo de la deducción.

En la creación de una deducción se exige que la solicitud esté `AUTORIZADO` (2) y que la deducción se registre inicialmente como `AUTORIZADO` (2). Las transiciones permitidas de la deducción son `2 → 4`, `2 → 5` y `4 → 2`; esta última exige que la convocatoria esté vigente. También se conservan los datos originales de registro y autorización y se validan los montos.

Los triggers de historial registran cambios de estatus y la sincronización actualiza el estado de la solicitud cuando cambia el de la deducción. Otro trigger actualiza la fecha de modificación.

### 5.1 Datos iniciales de catálogos

| Catálogo | Valores insertados |
| --- | --- |
| `Periodos` | `1: Agosto-Diciembre`; `2: Enero-Junio` |
| `Estatus_Solicitudes` | `1: PENDIENTE`; `2: AUTORIZADO`; `3: RECHAZADO`; `4: CANCELADO`; `5: APLICADO` |
| `Estatus_Deducciones` | `2: AUTORIZADO`; `4: CANCELADO`; `5: APLICADO` |
| `Tipo_Preguntas` | `1: ABIERTA`; `2: OPCION_UNICA`; `3: MIXTA` |

## 6. Flujo general

1. Se identifica al estudiante por `Numero_Control`, al aspirante por `(ficha, periodo, anio)` o al administrador por `idAdministrador`.
2. Se configuran los periodos, las convocatorias, los tipos de solicitud, los formularios y los montos.
3. Se registra la solicitud con su convocatoria, tipo, formulario y perfil de origen.
4. Se guardan y validan las respuestas correspondientes al formulario.
5. Se revisa la solicitud y se registra su cambio de estado.
6. Si se autoriza, se registra la deducción y se calculan sus montos.
7. Los cambios de estado de la deducción se validan, se registran en el historial y se sincronizan con la solicitud.

## 7. Sobre la imagen del diagrama

El diagrama debe corresponder a la versión V5, con los nombres de tablas en plural y sin la tabla `Usuarios`. Cuando se disponga de una imagen actualizada en la misma carpeta, puede incluirse así:

```markdown
![Diagrama de la base de datos](image.png)
```

## 8. Archivos relacionados

- [Script de creación de la base de datos](docker/init-db/02-crear-estructura.sql)
- [Configuración Docker](docker-compose.yml)
- [Ejecución del proyecto](README.md)
- [Arquitectura técnica](ARQUITECTURA.md)
