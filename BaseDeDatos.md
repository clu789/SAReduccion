# Base de datos de SAReduccion

## 1. Propósito

La base de datos administra las solicitudes de reducción del monto de inscripción y reinscripción. El modelo utiliza Oracle XE 21c y se construye con los scripts de inicialización:

[`docker/init-db/02-crear-estructura.sql`](docker/init-db/02-crear-estructura.sql)

El script crea 21 tablas con sus claves primarias, claves foráneas, restricciones, índices, secuencias y triggers, además de los datos iniciales de catálogo. La identidad de cada perfil se resuelve con los identificadores propios de `Estudiantes`, `Aspirantes` y `Administradores`, sin una tabla de usuarios intermedia.

## 2. Instalación y ejecución

[`docker/init-db/init-db.sh`](docker/init-db/init-db.sh) es el único orquestador y ejecuta los SQL en orden. La imagen de Oracle lo lanza desde `/container-entrypoint-initdb.d` como `SYS` en la raíz del contenedor; el orquestador cambia a `XEPDB1` y aplica cada script con el usuario que corresponde (SYS o `SA_REDUCCION`), resolviendo además las variables de sustitución. Los demás SQL se montan en `/opt/sareduccion/init-db`, fuera del directorio de auto-inicialización, para que la imagen no los ejecute por su cuenta con el usuario y el contexto equivocados.

**Los scripts solo se ejecutan con un volumen Oracle vacío**, es decir, la primera vez que se crea la base. Reemplazar un archivo SQL no actualiza una base existente: los cambios posteriores deben aplicarse de forma controlada sobre la base en uso, porque el proyecto todavía no implementa un mecanismo de migraciones incrementales.

**Advertencia:** `docker compose down -v` **elimina** `sareduccion_oracle_data` y con él todos los datos. No es un paso habitual de trabajo ni una forma de aplicar cambios: solo se justifica cuando se quiere reinstalar el esquema desde cero y no hay información que conservar. Si existe un respaldo reciente, este se puede restaurar después (sección 11).

### 2.1 Scripts de inicialización

| Script | Propósito | Se ejecuta |
| --- | --- | --- |
| `01-configurar-esquema.sql` | Crea/configura el usuario `SA_REDUCCION` con cuota en `USERS`, y crea el directorio `SAR_BACKUP_DIR` con `READ, WRITE` para el esquema. | Instalación (SYS). |
| `02-crear-estructura.sql` | Tablas, claves, restricciones, índices, secuencias, triggers y catálogos base. | Instalación (`SA_REDUCCION`). |
| `03-crear-identidades-roles.sql` | Roles `ROL_SAR_ESTUDIANTE_SIM` y `ROL_SAR_ADMIN_SIM`, cuentas individuales de prueba y su rol por defecto. | Instalación (SYS). |
| `04-crear-cuenta-tecnica.sql` | Crea/verifica `SAR_BACKEND` y le concede `CREATE SESSION`. | Instalación (SYS). |
| `05-crear-paquetes-permisos.sql` | Los tres paquetes PL/SQL, la retirada de `EXECUTE` a los roles y los grants a `SAR_BACKEND`. | Instalación (`SA_REDUCCION`). |
| `06-seed-desarrollo.sql` | Datos ficticios: tipos de solicitud, convocatorias, montos, formularios, estudiantes, administradores, aspirantes, solicitudes, respuestas, historiales y deducciones. | Instalación, solo si `SAR_LOAD_DEV_SEED` está activo. |
| `init-db.sh` | Orquestador: fija el orden, resuelve las variables de sustitución, aplica cada script con el usuario correcto y verifica el resultado. | Automático en volumen vacío. |

El nombre de cada archivo coincide con su número de orden. El orquestador aplica los scripts 01, 03 y 04 como SYS (requieren privilegios de instancia) y los 02, 05 y 06 como `SA_REDUCCION`; los paquetes deben pertenecer a ese usuario para que `AUTHID DEFINER` quede ligado al propietario del esquema.

`06-seed-desarrollo.sql` es el único script condicionado: con `SAR_LOAD_DEV_SEED=false` se omite y la base queda sin datos de aplicación. Los catálogos `Periodos`, `Estatus_Solicitudes`, `Estatus_Deducciones` y `Tipo_Preguntas` sí se insertan siempre, porque forman parte de `02-crear-estructura.sql`. `Tipo_Solicitudes`, en cambio, lo aporta el seed.

### 2.2 Verificaciones

| Prueba | Alcance |
| --- | --- |
| `docker/init-db/init-db.sh` (bloque final) | Automática: 21 tablas, 6 objetos de paquete válidos, 3 usuarios, 2 roles asignados, privilegios de `SAR_BACKEND` y presencia/ausencia de datos según `SAR_LOAD_DEV_SEED`. |
| `database/tests/05-verificar-instalacion.sql` | Existencia y validez de los objetos del esquema. |
| `database/tests/06-probar-permisos.sql` | Privilegios efectivos de `SAR_BACKEND` y de los roles simulados. |
| `database/tests/07-probar-aspirantes.sql` | Escenarios de `PKG_ASPIRANTE`: parámetros nulos, sin coincidencia, año no vigente y coincidencia ambigua entre periodos. |

Las pruebas de `database/tests/` son scripts SQL de comprobación manual: se conectan y muestran resultados, pero no se integran en una suite automatizada. Se conservan porque cubren reglas que no pueden probarse desde el backend (la cuenta técnica no tiene `INSERT`), en particular el rechazo de coincidencias ambiguas entre periodos.

## 3. Modelo de datos

**PK**: clave primaria; **FK**: clave foránea; **UK**: unicidad; **NULL**: opcional; **NOT NULL**: obligatorio; **CHECK**: restricción de comprobación. Todo lo indicado corresponde a [`docker/init-db/02-crear-estructura.sql`](docker/init-db/02-crear-estructura.sql). El esquema consta de 21 tablas.

### 3.0 Diagrama entidad-relación

![Diagrama entidad-relación del esquema SA_REDUCCION](DataBase.png)

### 3.1 Catálogos y seguridad

#### Tabla: `Periodos`

Catálogo de periodos académicos, referenciado por `Convocatorias` y `Aspirantes`.

| Atributo | Tipo | Restricciones | Descripción |
| --- | --- | --- | --- |
| `idPeriodo` | `NUMBER(2)` | PK `pk_periodos` | Identificador del periodo. |
| `Descripcion` | `VARCHAR2(50)` | NOT NULL, UK `uk_periodos_descripcion` | Nombre del periodo. |

No usa secuencia: los identificadores se insertan explícitamente (1 y 2).

#### Tabla: `Tipo_Solicitudes`

Clasificación de las solicitudes. El catálogo es cerrado y solo admite dos valores, ambos cargados por el seed: `900001` **Hijos de trabajadores** y `900002` **Hijos de homologados**.

Estos dos tipos corresponden a las categorías de personal que define `asignacion_Modulos.md` para configurar la reducción por convocatoria:

| `id_Tipo_Solicitud` | `Nombre` | Categoría de `asignacion_Modulos.md` |
| ---: | --- | --- |
| `900001` | Hijos de trabajadores | Personal **interno**. |
| `900002` | Hijos de homologados | Personal **homologado**. |

No existen otros tipos válidos. El seed rechaza con rollback cualquier fila adicional en la tabla y cualquier solicitud, historial o deducción que use un identificador distinto de estos dos. Los identificadores `900001` y `900002` están reservados para datos de desarrollo; las secuencias de la tabla no se modifican.

| Atributo | Tipo | Restricciones | Descripción |
| --- | --- | --- | --- |
| `id_Tipo_Solicitud` | `NUMBER(10)` | PK `pk_tipo_solicitudes` | Identificador del tipo. Lo asigna `trg_tipo_solicitudes_bi` desde `seq_tipo_solicitudes`. |
| `Nombre` | `VARCHAR2(50)` | NOT NULL | Nombre del tipo de solicitud. |
| `Descripcion` | `VARCHAR2(200)` | NULL | Detalle del tipo. |

Sus filas las aporta `06-seed-desarrollo.sql`, no `02-crear-estructura.sql`.

#### Tabla: `Tipo_Preguntas`

Modalidades admitidas para las preguntas de un formulario.

| Atributo | Tipo | Restricciones | Descripción |
| --- | --- | --- | --- |
| `id_Tipo_Pregunta` | `NUMBER(10)` | PK `pk_tipo_preguntas` | Identificador del tipo. Lo asigna `trg_tipo_preguntas_bi` desde `seq_tipo_preguntas` (inicia en 4). |
| `Nombre` | `VARCHAR2(50)` | NOT NULL, UK `uk_tipo_preguntas_nombre` | `ABIERTA`, `OPCION_UNICA` o `MIXTA`. |

#### Tabla: `Estatus_Solicitudes`

Estados posibles de una solicitud.

| Atributo | Tipo | Restricciones | Descripción |
| --- | --- | --- | --- |
| `id_estatus_solicitud` | `NUMBER(10)` | PK `pk_estatus_solicitudes` | Identificador del estado. Lo asigna `trg_estatus_solicitudes_bi` desde `seq_estatus_solicitudes` (inicia en 6). |
| `Nombre` | `VARCHAR2(100)` | NOT NULL, UK `uk_estatus_solicitudes_nombre` | `PENDIENTE`, `AUTORIZADO`, `RECHAZADO`, `CANCELADO` o `APLICADO`. |
| `Descripcion` | `VARCHAR2(200)` | NULL | Detalle del estado. |

#### Tabla: `Estatus_Deducciones`

Estados posibles de una deducción autorizada. Solo se usan los valores 2, 4 y 5.

| Atributo | Tipo | Restricciones | Descripción |
| --- | --- | --- | --- |
| `id_Estatus_Deducciones` | `NUMBER(10)` | PK `pk_estatus_deducciones` | Identificador del estado. Lo asigna `trg_estatus_deducciones_bi` desde `seq_estatus_deducciones` (inicia en 6). |
| `Nombre` | `VARCHAR2(50)` | NOT NULL, UK `uk_estatus_deducciones_nombre` | `AUTORIZADO`, `CANCELADO` o `APLICADO`. |
| `Descripcion` | `VARCHAR2(100)` | NULL | Detalle del estado. |

### 3.2 Personas y perfiles

#### Tabla: `Estudiantes`

Padrón de estudiantes, identificados por su número de control.

| Atributo | Tipo | Restricciones | Descripción |
| --- | --- | --- | --- |
| `Numero_Control` | `VARCHAR2(10)` | PK `pk_estudiantes` | Número de control; identifica al estudiante. |
| `Nombre` | `VARCHAR2(50)` | NOT NULL | Nombre o nombres. |
| `PrimerAppellido` | `VARCHAR2(50)` | NOT NULL | Primer apellido. El nombre de la columna está escrito así en el esquema. |
| `SegundoApellido` | `VARCHAR2(50)` | NULL | Segundo apellido. |
| `Carrera` | `VARCHAR2(100)` | NULL | Programa educativo. |
| `Semestre` | `NUMBER(2)` | NULL, CHECK `ck_estudiantes_semestre` | Semestre entre 1 y 20, o nulo. |

No usa secuencia: la clave es el propio número de control. `PKG_ESTUDIANTE` valida este identificador antes de cada operación.

#### Tabla: `Aspirantes`

Registra aspirantes de nuevo ingreso mediante la clave compuesta `(ficha, periodo, anio)`. Un mismo aspirante puede tener registros en periodos o años distintos, por lo que la ficha sola no identifica una fila.

| Atributo | Tipo | Restricciones | Descripción |
| --- | --- | --- | --- |
| `ficha` | `VARCHAR2(18)` | PK (1), NOT NULL | Identificador de registro del aspirante. |
| `periodo` | `NUMBER(2)` | PK (2), NOT NULL, FK `fk_aspirante_periodo` -> `Periodos` | Periodo del registro. |
| `anio` | `NUMBER(4)` | PK (3), NOT NULL, CHECK `ck_aspirantes_anio` | Año del registro, entre 2000 y 2200. |
| `Nombre` | `VARCHAR2(50)` | NOT NULL | Nombre o nombres. |
| `PrimerApellido` | `VARCHAR2(50)` | NOT NULL | Primer apellido. |
| `SegundoApellido` | `VARCHAR2(50)` | NULL | Segundo apellido. |
| `Semestre` | `NUMBER(2)` | NULL, CHECK `ck_aspirantes_semestre` | Semestre entre 1 y 20, o nulo. |
| `Curp` | `VARCHAR2(18)` | NOT NULL, CHECK `ck_aspirantes_curp` | CURP; la restricción exige longitud exacta de 18. |
| `correo` | `VARCHAR2(254)` | NOT NULL, CHECK `ck_aspirantes_correo` | Correo de contacto admitido. |

**Clave primaria compuesta.** `pk_aspirantes` es `(ficha, periodo, anio)`. Es la razón por la que la validación de acceso debe obtener las tres columnas: la búsqueda por `ficha` puede devolver más de una fila.

**Columna `correo`.** Es obligatoria para todo registro nuevo y está prevista como canal de verificación futuro (código de un solo uso); hoy se usa como dato de contacto de desarrollo.

**Dominios de correo admitidos.** `ck_aspirantes_correo` aplica una expresión regular insensible a mayúsculas (`'i'`) que exige la forma `usuario@dominio`, con usuario alfanumérico y puntos, y uno de estos siete dominios:

| Dominios admitidos |
| --- |
| `yahoo.com.mx`, `gmail.com`, `hotmail.com`, `outlook.com`, `live.com`, `yahoo.com`, `icloud.com` |

#### Tabla: `Administradores`

Padrón del personal administrativo.

| Atributo | Tipo | Restricciones | Descripción |
| --- | --- | --- | --- |
| `idAdministrador` | `VARCHAR2(10)` | PK `pk_administradores` | Identificador del administrador; coincide con el nombre de su cuenta Oracle de prueba (`SA01`). |
| `Nombre` | `VARCHAR2(50)` | NOT NULL | Nombre o nombres. |
| `PrimerApellido` | `VARCHAR2(50)` | NOT NULL | Primer apellido. |
| `SegundoApellido` | `VARCHAR2(50)` | NULL | Segundo apellido. |

No usa secuencia. `PKG_ADMINISTRADOR` valida este identificador antes de cada operación.

### 3.3 Convocatorias y montos

#### Tabla: `Convocatorias`

Periodos de apertura y cierre durante los que se admiten solicitudes.

| Atributo | Tipo | Restricciones | Descripción |
| --- | --- | --- | --- |
| `id_convocatoria` | `NUMBER(10)` | PK `pk_convocatorias` | Identificador de la convocatoria. Lo asigna `trg_convocatorias_bi` desde `seq_convocatorias`. |
| `fecha_inicio` | `DATE` | NOT NULL | Fecha en que inicia la convocatoria. |
| `fecha_fin` | `DATE` | NOT NULL, CHECK `ck_convocatoria_fechas` | Fecha en que termina; debe ser mayor o igual que `fecha_inicio`. |
| `id_periodo` | `NUMBER(2)` | NOT NULL, FK `fk_convocatoria_periodo` -> `Periodos` | Periodo al que pertenece. |
| `anio` | `NUMBER(4)` | NOT NULL, CHECK `ck_convocatoria_anio`, UK `uk_convocatoria_periodo_anio` | Año, entre 2000 y 2200. La unicidad es por `(id_periodo, anio)`. |

#### Tabla: `Convocatorias_TipoSolicitudes`

Relaciona cada convocatoria con los tipos de solicitud que ofrece y fija el monto de deducción aplicable. Es la tabla donde el administrador configura la **cantidad de reducción por categoría de personal**: una fila para personal interno (`900001`) y otra para personal homologado (`900002`), tal como se acordó en `asignacion_Modulos.md`.

El monto es único por tipo y convocatoria: no se distingue entre inscripción y reinscripción. Esa diferencia no se configura aquí, sino que se resuelve al calcular la deducción con `Monto_Inscripciones` (aspirante) o `Monto_Reinscripciones` (estudiante).

| Atributo | Tipo | Restricciones | Descripción |
| --- | --- | --- | --- |
| `id_convocatoria` | `NUMBER(10)` | PK (1), FK `fk_cts_convocatoria` -> `Convocatorias` | Convocatoria. |
| `id_Tipo_Solicitud` | `NUMBER(10)` | PK (2), FK `fk_cts_tipo_solicitud` -> `Tipo_Solicitudes` | Tipo de solicitud ofrecido (categoría de personal). |
| `monto_deduccion` | `NUMBER(10,2)` | NOT NULL, CHECK `ck_cts_monto_deduccion` | Monto de deducción configurado; debe ser mayor o igual que cero. |
| `fecha_asignacion_montos` | `DATE` | NOT NULL, DEFAULT `SYSDATE` | Fecha en que se asignó el monto. |

#### Tabla: `Monto_Inscripciones`

Catálogo de importes de inscripción utilizados al calcular una deducción de aspirante.

| Atributo | Tipo | Restricciones | Descripción |
| --- | --- | --- | --- |
| `id_monto_inscripcion` | `NUMBER(10)` | PK `pk_monto_inscripciones` | Identificador del monto. Lo asigna `trg_monto_inscripciones_bi` desde `seq_monto_inscripciones`. |
| `monto_pago` | `NUMBER(10,2)` | NOT NULL, CHECK `ck_monto_inscripciones` | Importe de inscripción; debe ser mayor o igual que cero. |

#### Tabla: `Monto_Reinscripciones`

Catálogo de importes de reinscripción utilizados al calcular una deducción de estudiante.

| Atributo | Tipo | Restricciones | Descripción |
| --- | --- | --- | --- |
| `id_monto_reinscripcion` | `NUMBER(10)` | PK `pk_monto_reinscripciones` | Identificador del monto. Lo asigna `trg_monto_reinscripcion_bi` desde `seq_monto_reinscripciones`. |
| `monto_pago` | `NUMBER(10,2)` | NOT NULL, CHECK `ck_monto_reinscripciones` | Importe de reinscripción; debe ser mayor o igual que cero. |

### 3.4 Formularios y preguntas

#### Tabla: `Formularios`

Formulario asociado a cada tipo de solicitud. La relación es uno a uno.

| Atributo | Tipo | Restricciones | Descripción |
| --- | --- | --- | --- |
| `idFormulario` | `NUMBER(10)` | PK `pk_formularios` | Identificador del formulario. Lo asigna `trg_formularios_bi` desde `seq_formularios`. |
| `Nombre` | `VARCHAR2(100)` | NOT NULL | Nombre del formulario. |
| `Descripcion` | `VARCHAR2(200)` | NULL | Detalle del formulario. |
| `id_Tipo_Solicitud` | `NUMBER(10)` | NOT NULL, FK `fk_formulario_tipo_solicitud` -> `Tipo_Solicitudes`, UK `uk_formulario_tipo_solicitud` | Tipo de solicitud al que pertenece. La unicidad impide dos formularios para el mismo tipo. |

#### Tabla: `Preguntas`

Banco de preguntas que pueden asignarse a los formularios.

| Atributo | Tipo | Restricciones | Descripción |
| --- | --- | --- | --- |
| `idPregunta` | `NUMBER(10)` | PK `pk_preguntas` | Identificador de la pregunta. Lo asigna `trg_preguntas_bi` desde `seq_preguntas`. |
| `texto_Pregunta` | `VARCHAR2(4000)` | NOT NULL | Enunciado de la pregunta. |
| `obligatoria` | `CHAR(1)` | NOT NULL, DEFAULT `'S'`, CHECK `ck_pregunta_obligatoria` | Solo admite `'S'` o `'N'`. |
| `tipo_pregunta` | `NUMBER(10)` | NOT NULL, FK `fk_pregunta_tipo` -> `Tipo_Preguntas` | Modalidad de respuesta. Determina las reglas que aplica `trg_respuestas_validacion`. |

#### Tabla: `Formulario_Preguntas`

Asigna preguntas a un formulario y fija su orden de presentación.

| Atributo | Tipo | Restricciones | Descripción |
| --- | --- | --- | --- |
| `idFormulario` | `NUMBER(10)` | PK (1), FK `fk_fp_formulario` -> `Formularios` | Formulario. |
| `idPregunta` | `NUMBER(10)` | PK (2), FK `fk_fp_pregunta` -> `Preguntas` | Pregunta incluida. |
| `orden` | `NUMBER(5)` | NOT NULL, CHECK `ck_fp_orden`, UK `uk_fp_orden` | Posición dentro del formulario; debe ser mayor que cero y no se repite en el mismo formulario. |

#### Tabla: `Opciones`

Opciones de respuesta asociadas a una pregunta de tipo `OPCION_UNICA` o `MIXTA`.

| Atributo | Tipo | Restricciones | Descripción |
| --- | --- | --- | --- |
| `id_opcion` | `NUMBER(10)` | PK `pk_opciones` | Identificador de la opción. Lo asigna `trg_opciones_bi` desde `seq_opciones`. |
| `texto_opcion` | `VARCHAR2(255)` | NOT NULL | Texto mostrado. La opción `OTRO` tiene un tratamiento especial en `trg_respuestas_validacion`. |
| `orden` | `NUMBER(5)` | NOT NULL, CHECK `ck_opcion_orden`, UK `uk_opcion_orden` | Posición dentro de la pregunta; debe ser mayor que cero y no se repite en la misma pregunta. |
| `id_pregunta` | `NUMBER(10)` | NOT NULL, FK `fk_opcion_pregunta` -> `Preguntas` | Pregunta a la que pertenece. |

### 3.5 Solicitudes y respuestas

#### Tabla: `Solicitudes`

Solicitud presentada por un estudiante o por un aspirante, siempre en una convocatoria, un tipo de solicitud y un formulario concretos.

| Atributo | Tipo | Restricciones | Descripción |
| --- | --- | --- | --- |
| `idSolicitud` | `NUMBER(10)` | PK `pk_solicitudes` | Identificador de la solicitud. Lo asigna `trg_solicitudes_bi` desde `seq_solicitudes`. |
| `id_estatus_solicitud` | `NUMBER(10)` | NOT NULL, FK `fk_solicitud_estatus` -> `Estatus_Solicitudes` | Estado actual. Toda solicitud se crea en `PENDIENTE` (1). |
| `id_convocatoria` | `NUMBER(10)` | NOT NULL, FK `fk_solicitud_convocatoria` -> `Convocatorias` | Convocatoria en la que se presenta. |
| `num_control` | `VARCHAR2(10)` | NULL, FK `fk_solicitud_estudiante` -> `Estudiantes` | Número de control, solo si la solicitud es de estudiante. |
| `ficha` | `VARCHAR2(18)` | NULL | Ficha, solo si la solicitud es de aspirante. |
| `periodo` | `NUMBER(2)` | NULL | Periodo del aspirante (solo si `ficha` no es nula). |
| `anio` | `NUMBER(4)` | NULL, CHECK `ck_solicitud_anio` | Año del aspirante, entre 2000 y 2200. Nulo para estudiantes. |
| `fecha_Solicitud` | `DATE` | NOT NULL, DEFAULT `SYSDATE` | Fecha de creación. |
| `fecha_actualizacion` | `DATE` | NOT NULL, DEFAULT `SYSDATE` | Última actualización; la refresca `trg_solicitudes_validacion` en cada inserción o modificación. |
| `id_Tipo_Solicitud` | `NUMBER(10)` | NOT NULL, FK `fk_solicitud_tipo` -> `Tipo_Solicitudes` | Tipo de solicitud. |
| `idFormulario` | `NUMBER(10)` | NOT NULL, FK `fk_solicitud_formulario` -> `Formularios` | Formulario que responde la solicitud. |

**Clave foránea compuesta.** `fk_solicitud_aspirante` es `(ficha, periodo, anio) -> Aspirantes(ficha, periodo, anio)`. Como `num_control`, `ficha`, `periodo` y `anio` son opcionales, la integridad se completa con la restricción `ck_solicitud_origen`.

**Restricción `ck_solicitud_origen`.** Exige que la solicitud pertenezca a un estudiante o a un aspirante, nunca a ambos:

```text
(num_control IS NOT NULL AND ficha IS NULL AND periodo IS NULL AND anio IS NULL)
OR
(num_control IS NULL AND ficha IS NOT NULL AND periodo IS NOT NULL AND anio IS NOT NULL)
```

**Trigger `trg_solicitudes_validacion`.** Antes de insertar o modificar comprueba que el formulario corresponda al tipo de solicitud y, cuando la solicitud es de aspirante, que el periodo y el año del aspirante coincidan con los de la convocatoria. Un fallo lanza `-20003` (desajuste periodo/año) o `-20002` (registro inexistente).

**Trigger `trg_solicitudes_historial`.** Después de un cambio de `id_estatus_solicitud` inserta una fila en `Historial_Solicitudes`.

#### Índices únicos funcionales de `Solicitudes`

Como una fila solo tiene número de control o ficha (nunca ambos), la unicidad por origen se resuelve con dos índices basados en expresiones que anulan la columna que no aplica. Al ser `NULL` en la columna ausente, el valor no participa en la comparación y se evita la ambigüedad de un índice único sobre columnas opcionales.

| Índice | Tipo | Expresiones | Evita |
| --- | --- | --- | --- |
| `uk_solicitud_estudiante_conv` | `FUNCTION-BASED NORMAL`, UNIQUE | `CASE WHEN "NUM_CONTROL" IS NOT NULL THEN "NUM_CONTROL" END`, `CASE WHEN "NUM_CONTROL" IS NOT NULL THEN "ID_CONVOCATORIA" END` | Dos solicitudes del mismo estudiante en la misma convocatoria. |
| `uk_solicitud_aspirante_conv` | `FUNCTION-BASED NORMAL`, UNIQUE | `CASE WHEN "FICHA" IS NOT NULL THEN "FICHA" END`, `CASE WHEN "FICHA" IS NOT NULL THEN "PERIODO" END`, `CASE WHEN "FICHA" IS NOT NULL THEN "ANIO" END`, `CASE WHEN "FICHA" IS NOT NULL THEN "ID_CONVOCATORIA" END` | Dos solicitudes del mismo aspirante (misma ficha, periodo y año) en la misma convocatoria. |

Los cuatro segmentos del índice de aspirante reproducen la clave compuesta de `Aspirantes`, de modo que la unicidad se evalúa sobre la identidad completa del aspirante y no solo sobre la ficha. Ambos límites son independientes del tipo de solicitud: un mismo origen no puede repetir convocatoria aunque cambie el tipo.

Estos dos índices son la implementación en base de datos de la regla acordada en `asignacion_Modulos.md`: **una sola solicitud por solicitante y convocatoria**. Corregir respuestas debe actualizar el registro existente, no crear otro; cualquier intento de alta duplicada falla con `ORA-00001`.

#### Tabla: `Respuestas`

Respuesta de una solicitud a una de las preguntas de su formulario.

| Atributo | Tipo | Restricciones | Descripción |
| --- | --- | --- | --- |
| `id_respuesta` | `NUMBER(10)` | PK `pk_respuestas` | Identificador de la respuesta. Lo asigna `trg_respuestas_bi` desde `seq_respuestas`. |
| `texto_respuesta` | `VARCHAR2(4000)` | NULL | Respuesta escrita; obligatoria en preguntas `ABIERTA` y en la opción `OTRO` de una `MIXTA`. |
| `id_solicitud` | `NUMBER(10)` | NOT NULL, FK `fk_respuesta_solicitud` -> `Solicitudes` | Solicitud respondida. |
| `id_pregunta` | `NUMBER(10)` | NOT NULL, FK `fk_respuesta_pregunta` -> `Preguntas` | Pregunta respondida. |
| `id_opcion` | `NUMBER(10)` | NULL, FK `fk_respuesta_opcion` -> `Opciones` | Opción elegida; obligatoria en preguntas `OPCION_UNICA` y `MIXTA`. |

**Índice único `ux_respuesta_solicitud_pregunta`.** Sobre `(id_solicitud, id_pregunta)`: admite una sola respuesta por pregunta y solicitud. Es un índice único explícito, no una restricción `UNIQUE` declarada en la tabla.

**Trigger `trg_respuestas_validacion`.** Antes de insertar o modificar verifica que la pregunta pertenezca al formulario de la solicitud y que la forma de la respuesta corresponda al tipo de pregunta:

| Tipo de pregunta | Regla exigida |
| --- | --- |
| `ABIERTA` (1) | Debe traer texto y no admite `id_opcion`. |
| `OPCION_UNICA` (2) | Debe traer `id_opcion` y no admite texto adicional. |
| `MIXTA` (3) | Debe traer `id_opcion`; solo la opción `OTRO` admite texto adicional. |

También comprueba que la opción pertenezca a la pregunta. Los rechazos usan los códigos `-20008` a `-20018`.

### 3.6 Historial y deducciones

#### Tabla: `Historial_Solicitudes`

Bitácora de los cambios de estado de una solicitud.

| Atributo | Tipo | Restricciones | Descripción |
| --- | --- | --- | --- |
| `id_historial` | `NUMBER(10)` | PK `pk_historial_solicitudes` | Identificador del registro. Lo asigna `trg_hist_solicitudes_bi` desde `seq_historial_solicitudes`. |
| `comentario` | `VARCHAR2(500)` | NULL | Comentario del cambio; lo completa `trg_deduccion_cancelacion` cuando el cambio proviene de una deducción. |
| `fecha_Registro` | `DATE` | NOT NULL, DEFAULT `SYSDATE` | Fecha del movimiento. |
| `id_solicitud` | `NUMBER(10)` | NOT NULL, FK `fk_hs_solicitud` -> `Solicitudes` | Solicitud afectada. |
| `id_estatus` | `NUMBER(10)` | NOT NULL, FK `fk_hs_estatus` -> `Estatus_Solicitudes` | Estado registrado. La columna se llama `id_estatus`, no `id_estatus_solicitud`. |
| `id_administrador` | `VARCHAR2(10)` | NULL, FK `fk_hs_administrador` -> `Administradores` | Administrador que ejecutó el cambio; nulo si lo originó un trigger. |
| `id_Tipo_solicitud` | `NUMBER(10)` | NOT NULL, FK `fk_hs_tipo_solicitud` -> `Tipo_Solicitudes` | Tipo de la solicitud al momento del cambio. |

#### Tabla: `Deducciones_autorizadas`

Deducción autorizada sobre una solicitud ya aprobada, con su seguimiento y sus montos.

| Atributo | Tipo | Restricciones | Descripción |
| --- | --- | --- | --- |
| `id_deduccion_autorizada` | `NUMBER(10)` | PK `pk_deducciones_autorizadas` | Identificador de la deducción. Lo asigna `trg_deducciones_bi` desde `seq_deducciones_autorizadas`. |
| `estatus_deduccion` | `NUMBER(10)` | NOT NULL, FK `fk_da_estatus` -> `Estatus_Deducciones` | Estado actual. Toda deducción nueva se crea `AUTORIZADO` (2). |
| `monto_final` | `NUMBER(10,2)` | NOT NULL, CHECK `ck_da_monto_final` | Resultado de `monto_pago - monto_deduccion`; lo calcula `trg_deducciones_calculo`. |
| `fecha_registro` | `DATE` | NOT NULL, DEFAULT `SYSDATE` | Fecha de registro; inmutable tras la creación. |
| `id_solicitud` | `NUMBER(10)` | NOT NULL, FK `fk_da_solicitud` -> `Solicitudes`, UK `uk_da_solicitud` | Solicitud deducida. La unicidad impide más de una deducción por solicitud. |
| `id_administrador` | `VARCHAR2(10)` | NULL, FK `fk_da_administrador` -> `Administradores` | Administrador que autorizó. |
| `fecha_autorizacion` | `DATE` | NULL | Fecha de autorización; la fija `trg_deducciones_calculo` y no puede modificarse. |
| `id_convocatoria` | `NUMBER(10)` | NOT NULL | Convocatoria de la solicitud; la copia el trigger. |
| `id_Tipo_Solicitud` | `NUMBER(10)` | NOT NULL | Tipo de la solicitud; lo copia el trigger. |
| `fecha_actualizacion` | `DATE` | NOT NULL, DEFAULT `SYSDATE` | Última modificación; la refresca `trg_deducciones_fecha_update`. |
| `comentario` | `VARCHAR2(500)` | NULL | Comentario de la resolución. |
| `id_monto_inscripcion` | `NUMBER(10)` | NULL, FK `fk_da_monto_inscripcion` -> `Monto_Inscripciones` | Monto de inscripción; solo en deducciones de aspirante. |
| `id_monto_reinscripcion` | `NUMBER(10)` | NULL, FK `fk_da_monto_reinscripcion` -> `Monto_Reinscripciones` | Monto de reinscripción; solo en deducciones de estudiante. |
| `monto_pago` | `NUMBER(10,2)` | NOT NULL, CHECK `ck_da_montos` | Importe base tomado del catálogo de montos; lo asigna el trigger. |
| `monto_deduccion` | `NUMBER(10,2)` | NOT NULL, CHECK `ck_da_montos` | Deducción tomada de `Convocatorias_TipoSolicitudes`; la asigna el trigger. |

**Clave foránea compuesta `fk_da_cts`.** `(id_convocatoria, id_Tipo_Solicitud)` -> `Convocatorias_TipoSolicitudes`. Garantiza que la configuración de montos exista para esa convocatoria y ese tipo.

**Restricciones de montos.**

| Restricción | Regla |
| --- | --- |
| `ck_da_monto_origen` | Exige exactamente un origen: o `id_monto_inscripcion` o `id_monto_reinscripcion`, nunca ambos ni ninguno. |
| `ck_da_montos` | `monto_pago >= 0`, `monto_deduccion >= 0` y `monto_deduccion <= monto_pago`. |
| `ck_da_monto_final` | `monto_final = monto_pago - monto_deduccion`. |

**Trigger `trg_deducciones_calculo`.** Solo permite crear la deducción si la solicitud está `AUTORIZADO` (2) y la deducción nace como `AUTORIZADO` (2). Según el origen toma el monto de `Monto_Reinscripciones` (estudiante) o de `Monto_Inscripciones` (aspirante), copia convocatoria y tipo, recupera `monto_deduccion` de `Convocatorias_TipoSolicitudes` y calcula `monto_final`. En las modificaciones conserva `fecha_registro` y `fecha_autorizacion`, bloquea cambios en los datos de origen (código `-20024`) y aplica esta máquina de estados:

| Transición | Condición |
| --- | --- |
| `2 -> 4` | Cancelar la deducción. |
| `2 -> 5` | Marcar la deducción como aplicada. |
| `4 -> 2` | Reautorizar; solo si la convocatoria está vigente. |

Cualquier otra transición lanza `-20026`.

#### Tabla: `Historial_Deducciones`

Bitácora de los cambios de estado de una deducción.

| Atributo | Tipo | Restricciones | Descripción |
| --- | --- | --- | --- |
| `id_Historial_Deduccion` | `NUMBER(10)` | PK `pk_historial_deducciones` | Identificador del registro. Lo asigna `trg_hist_deducciones_bi` desde `seq_historial_deducciones`. |
| `id_deduccion_autorizada` | `NUMBER(10)` | NOT NULL, FK `fk_hd_deduccion` -> `Deducciones_autorizadas` | Deducción afectada. |
| `id_estatus` | `NUMBER(10)` | NOT NULL, FK `fk_hd_estatus` -> `Estatus_Deducciones` | Estado registrado. |
| `id_administrador` | `VARCHAR2(10)` | NULL, FK `fk_hd_administrador` -> `Administradores` | Administrador que ejecutó el cambio. |
| `comentario` | `VARCHAR2(500)` | NULL | Comentario del cambio. |
| `fecha_registro` | `DATE` | NOT NULL, DEFAULT `SYSDATE` | Fecha del movimiento. |

### 3.7 Reglas de negocio acordadas y su implementación

`asignacion_Modulos.md` fija reglas que deben validarse en el backend y en la base de datos, no solo en la interfaz. Esta tabla contrasta cada regla con lo que hoy existe en Oracle; sirve para saber qué falta antes de cerrar los módulos.

| Regla acordada | Implementación actual en la base | Estado |
| --- | --- | --- |
| Una sola solicitud por solicitante y convocatoria. | Índices únicos `uk_solicitud_estudiante_conv` y `uk_solicitud_aspirante_conv`. | Implementada. |
| La reducción se configura por convocatoria según categoría interno/homologado, sin distinguir inscripción y reinscripción. | `Convocatorias_TipoSolicitudes.monto_deduccion` por `(id_convocatoria, id_Tipo_Solicitud)`; los montos base viven en `Monto_Inscripciones` / `Monto_Reinscripciones`. | Implementada. |
| Solo se puede solicitar dentro de la vigencia de la convocatoria. | `PKG_ESTUDIANTE.crear_solicitud` y `PKG_ADMINISTRADOR.crear_solicitud_estudiante` exigen `TRUNC(SYSDATE)` entre `fecha_inicio` y `fecha_fin`, además de la combinación convocatoria/tipo/formulario (`-20812`, `-20822`). | Implementada. |
| Corregir respuestas no genera una solicitud adicional. | `PKG_ESTUDIANTE.guardar_respuesta` hace *upsert* sobre `(id_solicitud, id_pregunta)`, y los índices únicos impiden una segunda solicitud. | Implementada parcialmente: el *upsert* existe, pero la condición de estado no coincide (ver siguiente fila). |
| El solicitante puede modificar respuestas solo si la solicitud está `RECHAZADA` o `CANCELADA`. | `PKG_ESTUDIANTE.guardar_respuesta` exige `id_estatus_solicitud = 1` (`PENDIENTE`) y convocatoria vigente (`-20813`). Es lo contrario de la regla acordada. | **Pendiente**: hay que cambiar el paquete y los triggers para admitir 3 y 4, y decidir el estado posterior al reenvío. |
| El solicitante cancela su solicitud solo si está `PENDIENTE`; el administrador es el único que cancela una `AUTORIZADA`. | No existe procedimiento de cancelación para el solicitante. El administrador rechaza o autoriza con `cambiar_estatus_solicitud` (solo `1 -> 2` o `1 -> 3`); la cancelación de una autorizada se logra cancelando su deducción (`2 -> 4`), y `trg_deduccion_cancelacion` sincroniza el estado de la solicitud. | **Parcial**: falta la operación de cancelación del solicitante y formalizar la transición de la solicitud a `CANCELADO` (4) desde el administrador. |
| El estado al que vuelve una solicitud rechazada o cancelada tras corregir y reenviar está **pendiente de definición**. | No está modelado. `PKG_ADMINISTRADOR.cambiar_estatus_solicitud` solo permite `1 -> 2` o `1 -> 3`, y `trg_deduccion_cancelacion` mueve entre 2, 4 y 5; ninguna operación de paquete devuelve una solicitud de `RECHAZADO` (3) o `CANCELADO` (4) a otro estado. | **Bloqueada** hasta que se defina el flujo. |
| El aspirante también puede crear solicitudes, corregirlas y cancelarlas. | `PKG_ASPIRANTE` solo expone `validar_aspirante`; no hay operaciones de negocio para aspirantes. | **Pendiente**. |
| Los controles de autorización se aplican en servidor. | Los paquetes reciben la identidad como parámetro y validan que exista; `SAR_BACKEND` no tiene acceso directo a tablas. La autorización por rol y por pertenencia se aplica en el backend. | Implementada en la base; la cobertura por endpoint es parcial. |

Regla de mantenimiento: si un equipo cambia alguna de estas reglas en su rama, debe actualizar esta sección junto con el script oficial, para que `ARQUITECTURA.md`, `BaseDeDatos.md` y los paquetes sigan describiendo el mismo comportamiento.

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

El script define 15 secuencias y 22 triggers, además de tres índices únicos propios (`uk_solicitud_estudiante_conv`, `uk_solicitud_aspirante_conv` y `ux_respuesta_solicitud_pregunta`). En total el esquema queda con 33 índices, contando los que Oracle crea para cada clave primaria y cada restricción de unicidad.

### 5.1 Secuencias

Cada secuencia alimenta el trigger de asignación de identificadores de su tabla. Las que parten de un valor distinto de 1 dejan espacio a los identificadores de catálogo ya insertados.

| Secuencia | Valor inicial | Tabla |
| --- | ---: | --- |
| `seq_tipo_solicitudes` | 1 | `Tipo_Solicitudes` |
| `seq_tipo_preguntas` | 4 | `Tipo_Preguntas` |
| `seq_estatus_solicitudes` | 6 | `Estatus_Solicitudes` |
| `seq_estatus_deducciones` | 6 | `Estatus_Deducciones` |
| `seq_convocatorias` | 1 | `Convocatorias` |
| `seq_formularios` | 1 | `Formularios` |
| `seq_preguntas` | 1 | `Preguntas` |
| `seq_opciones` | 1 | `Opciones` |
| `seq_monto_inscripciones` | 1 | `Monto_Inscripciones` |
| `seq_monto_reinscripciones` | 1 | `Monto_Reinscripciones` |
| `seq_solicitudes` | 1 | `Solicitudes` |
| `seq_respuestas` | 1 | `Respuestas` |
| `seq_historial_solicitudes` | 1 | `Historial_Solicitudes` |
| `seq_deducciones_autorizadas` | 1 | `Deducciones_autorizadas` |
| `seq_historial_deducciones` | 1 | `Historial_Deducciones` |

`Periodos`, `Estudiantes`, `Aspirantes` y `Administradores` no usan secuencia: sus claves se definen en la propia tabla.

### 5.2 Triggers de identificadores

Quince triggers `BEFORE INSERT ... FOR EACH ROW` asignan el `NEXTVAL` de la secuencia correspondiente solo cuando la clave llega nula, de modo que un valor explícito siempre prevalece. Siguen el patrón `trg_<tabla>_bi`.

### 5.3 Triggers de negocio

| Trigger | Momento | Efecto |
| --- | --- | --- |
| `trg_solicitudes_validacion` | `BEFORE INSERT OR UPDATE` en `Solicitudes` | Comprueba que el formulario corresponda al tipo de solicitud y que el periodo y el año del aspirante coincidan con los de la convocatoria; actualiza `fecha_actualizacion`. |
| `trg_respuestas_validacion` | `BEFORE INSERT OR UPDATE` en `Respuestas` | Verifica la pertenencia de la pregunta al formulario y que la forma de la respuesta corresponda al tipo de pregunta. |
| `trg_deducciones_calculo` | `BEFORE INSERT OR UPDATE` en `Deducciones_autorizadas` | Valida estados y montos, calcula `monto_final`, copia convocatoria y tipo, conserva las fechas originales y aplica las transiciones de estado permitidas. |
| `trg_solicitudes_historial` | `AFTER UPDATE OF id_estatus_solicitud` en `Solicitudes` | Inserta la fila correspondiente en `Historial_Solicitudes` cuando el estado cambia. |
| `trg_deducciones_historial` | `AFTER UPDATE OF estatus_deduccion` en `Deducciones_autorizadas` | Inserta la fila correspondiente en `Historial_Deducciones` cuando el estado cambia. |
| `trg_deduccion_cancelacion` | `AFTER UPDATE OF estatus_deduccion` en `Deducciones_autorizadas` | Sincroniza `Solicitudes.id_estatus_solicitud` con el estado de la deducción y completa administrador y comentario en el último registro de historial. |
| `trg_deducciones_fecha_update` | `BEFORE UPDATE` en `Deducciones_autorizadas` | Refresca `fecha_actualizacion`. |

Reglas de la deducción: solo se crea sobre una solicitud `AUTORIZADO` (2) y nace como `AUTORIZADO` (2). Las transiciones permitidas son `2 -> 4`, `2 -> 5` y `4 -> 2`; la reautorización exige que la convocatoria esté vigente. Los datos de origen, los montos y las fechas de registro y autorización son inmutables.

### 5.4 Datos iniciales de catálogos

Los inserta `02-crear-estructura.sql` y están presentes incluso sin datos de desarrollo.

| Catálogo | Valores insertados |
| --- | --- |
| `Periodos` | `1: Agosto-Diciembre`; `2: Enero-Junio` |
| `Estatus_Solicitudes` | `1: PENDIENTE`; `2: AUTORIZADO`; `3: RECHAZADO`; `4: CANCELADO`; `5: APLICADO` |
| `Estatus_Deducciones` | `2: AUTORIZADO`; `4: CANCELADO`; `5: APLICADO` |
| `Tipo_Preguntas` | `1: ABIERTA`; `2: OPCION_UNICA`; `3: MIXTA` |

## 6. Flujo general

1. Se identifica al estudiante por `Numero_Control`, al aspirante por `(ficha, periodo, anio)` o al administrador por `idAdministrador`.
2. Se configuran los periodos, las convocatorias, los dos tipos de solicitud, los formularios y los montos de reducción por categoría (equipo 4).
3. Se registra la solicitud con su convocatoria, tipo, formulario y perfil de origen. Debe estar dentro de la vigencia y ser la única de ese solicitante en esa convocatoria.
4. Se guardan y validan las respuestas correspondientes al formulario (equipo 5). Corregir respuestas reutiliza el mismo registro de solicitud.
5. Se revisa la solicitud y se registra su cambio de estado; el administrador puede autorizar o rechazar una solicitud pendiente (equipo 2).
6. Si se autoriza, se registra la deducción y se calculan sus montos.
7. Los cambios de estado de la deducción se validan, se registran en el historial y se sincronizan con la solicitud.
8. El solicitante consulta el estado y los comentarios (equipo 3). La cancelación por el solicitante solo procede en `PENDIENTE`; la de una solicitud `AUTORIZADA` corresponde al administrador.

Las reglas de los pasos 3, 4, 5 y 8 todavía no están cubiertas por completo en PL/SQL: el detalle y el estado de cada una están en la sección 3.7.

## 7. Paquetes PL/SQL

El esquema expone la lógica de acceso a datos mediante tres paquetes con `AUTHID DEFINER`: se ejecutan con los privilegios del propietario, de modo que `SAR_BACKEND` no necesita permisos sobre las tablas. Sus firmas reciben la identidad como parámetro; ninguno decide el usuario a partir de `SESSION_USER`.

| Paquete | Ámbito | Identificador de entrada |
| --- | --- | --- |
| `PKG_ESTUDIANTE` | Operaciones del estudiante sobre sus propias solicitudes y deducciones. | `p_numero_control`. |
| `PKG_ADMINISTRADOR` | Listados y resoluciones administrativas. | `p_id_administrador`. |
| `PKG_ASPIRANTE` | Validación de identidad del aspirante. | `p_ficha` + `p_curp`. |

### 7.1 `PKG_ESTUDIANTE`

Subprogramas: `mis_datos`, `mis_solicitudes`, `mis_deducciones`, `crear_solicitud` y `guardar_respuesta`. Cada uno valida que `p_numero_control` exista en `Estudiantes.Numero_Control` y limita las consultas y escrituras a ese estudiante.

### 7.2 `PKG_ADMINISTRADOR`

Subprogramas: `listar_estudiantes`, `listar_solicitudes`, `listar_respuestas`, `listar_deducciones`, `crear_solicitud_estudiante`, `cambiar_estatus_solicitud`, `autorizar_deduccion` y `cambiar_estatus_deduccion`. Valida que `p_id_administrador` exista en `Administradores.idAdministrador` antes de cada operación.

### 7.3 `PKG_ASPIRANTE`

Un único subprograma, `validar_aspirante`:

| Parámetro | Modo | Tipo | Descripción |
| --- | --- | --- | --- |
| `p_ficha` | IN | `VARCHAR2` | Ficha del aspirante. |
| `p_curp` | IN | `VARCHAR2` | CURP del aspirante. |
| `p_ficha_out` | OUT | `VARCHAR2` | Ficha confirmada, tal como figura en la tabla. |
| `p_periodo` | OUT | `NUMBER` | Periodo del registro encontrado. |
| `p_anio` | OUT | `NUMBER` | Año del registro encontrado. |

En caso de rechazo, el paquete lanza error de aplicación en lugar de devolver filas vacías:

| Código | Mensaje | Causa |
| --- | --- | --- |
| `-20840` | Parámetros requeridos | `p_ficha` o `p_curp` nulos o vacíos. |
| `-20841` | Sin coincidencia | La combinación ficha + CURP no existe para el año vigente. |
| `-20842` | Coincidencia ambigua | Existe más de un registro para esa ficha en el año vigente. |

El backend traduce los tres códigos a un rechazo de autenticación genérico, de modo que un intento fallido no revele si la ficha existe.

### 7.4 Cobertura por módulo y equipo

Correspondencia entre los equipos de `asignacion_Modulos.md` y los objetos de base de datos que cada uno toca. El equipo 7 revisa e integra los cambios; no los desarrolla por defecto.

| Equipo | Objetos de base de datos | Situación |
| --- | --- | --- |
| 1 — Autenticación y seguridad | `Aspirantes` (validación de `ficha` + `curp`), `PKG_ASPIRANTE`, cuentas y roles de los scripts 03 y 04. | Operativo. Falta la tabla/estructura de auditoría de accesos y la verificación por correo. |
| 2 — Panel administrativo | `Solicitudes`, `Respuestas`, `Deducciones_autorizadas`, `Historial_*`, `PKG_ADMINISTRADOR`. | Operativo para listar, aprobar, rechazar y cancelar deducciones. Falta el listado de historial y la cancelación de solicitudes autorizadas como operación explícita. |
| 3 — Panel del estudiante y aspirante | `Estudiantes`, `Solicitudes`, `Deducciones_autorizadas`, `PKG_ESTUDIANTE`. | Parcial: existen `mis_datos`, `mis_solicitudes` y `mis_deducciones`; faltan cancelación, edición en estados `RECHAZADO`/`CANCELADO` y todo el flujo de negocio del aspirante. |
| 4 — Convocatorias y montos | `Convocatorias`, `Convocatorias_TipoSolicitudes`, `Monto_Inscripciones`, `Monto_Reinscripciones`. | Estructura disponible; no hay procedimientos ni endpoints de alta o modificación de convocatorias. |
| 5 — Formularios | `Formularios`, `Preguntas`, `Formulario_Preguntas`, `Opciones`, `Respuestas`, `trg_respuestas_validacion`, `PKG_ESTUDIANTE.guardar_respuesta`. | Estructura y validación de forma operativas; falta la lectura del formulario por convocatoria/tipo y la edición en los estados acordados. |
| 6 — Notificaciones | Ninguno todavía. No hay tablas, paquetes ni cola de envíos. | Pendiente. |
| 7 — Base de datos | Scripts 01 a 06, `database/tests/`, respaldos y esta documentación. | Operativo. |

Cada cambio de estructura o de PL/SQL que proponga un equipo debe llegar como modificación de los scripts oficiales de `docker/init-db/`, y el equipo 7 lo valida antes de integrarlo.

## 8. Privilegios mínimos de `SAR_BACKEND`

`SAR_BACKEND` es la cuenta con la que el backend atiende las peticiones. Su modelo de permisos busca que un fallo de la capa de aplicación no pueda escalar a un acceso directo a datos.

| Tipo | Privilegio concedido |
| --- | --- |
| Sistema | `CREATE SESSION`. |
| Objeto (paquetes) | `EXECUTE` sobre `PKG_ESTUDIANTE`, `PKG_ADMINISTRADOR` y `PKG_ASPIRANTE`. |
| Tablas | Ninguno. |
| Roles | Ninguno. |
| Directorios | Ninguno. |

Consecuencias del modelo:

- No puede leer ni escribir tablas directamente, por lo que toda operación pasa por la validación de los paquetes.
- No puede crear objetos ni ejercer tareas administrativas.
- No puede ejecutar Data Pump: el respaldo lógico requiere una cuenta con acceso a un directorio y se realiza con `SA_REDUCCION`.

La prueba `database/tests/06-probar-permisos.sql` verifica que no existan grants CRUD ni privilegios administrativos, y que los roles simulados de estudiante y administrador no conserven `EXECUTE` sobre los paquetes.

## 9. Autenticación de aspirantes en la base de datos

La validación de identidad del aspirante se resuelve íntegramente en Oracle:

1. El backend invoca `PKG_ASPIRANTE.validar_aspirante(ficha, curp, ...)` con parámetros enlazados.
2. El paquete contrasta la combinación contra `Aspirantes` restringiendo el año al vigente. El año se obtiene en el propio paquete con `EXTRACT(YEAR FROM SYSDATE)`, de modo que la vigencia no depende del reloj del cliente ni de un valor enviado por el navegador.
3. Si hay exactamente una fila, devuelve `ficha`, `periodo` y `anio`; el backend construye con ello la identidad de sesión.
4. La CURP solo se usa durante la comparación: no se devuelve, no se registra y no se guarda en la sesión.

### 9.1 Motivos de rechazo

| Situación | Resultado |
| --- | --- |
| Ficha o CURP nulas o vacías | `-20840`. |
| Formato no válido (ficha o CURP) | Rechazo en el backend, antes de consultar Oracle. |
| Sin fila para la combinación en el año vigente | `-20841`. |
| Registro existente en un año distinto al actual | `-20841`. |
| Varias filas para la ficha en el año vigente | `-20842`. |
| Intentos repetidos desde la misma IP y ficha | Rechazo en el backend por límite de intentos. |

### 9.2 Rechazo de coincidencias ambiguas entre periodos

`Aspirantes` usa `(ficha, periodo, anio)` como clave primaria, así que una misma ficha puede tener un registro en el periodo 1 y otro en el periodo 2 del mismo año. `validar_aspirante` no elige uno arbitrariamente: si la comparación de ficha y CURP devuelve más de una fila, lanza `-20842` y rechaza el acceso. La consulta nunca usa `ROWNUM` ni un `MAX` para desempatar, porque hacerlo permitiría que un aspirante entrara con la identidad de otro registro del mismo año.

El escenario de coincidencia ambigua no puede provocarse desde el backend actual (la cuenta técnica no tiene `INSERT`), por lo que se verifica a nivel Oracle con `database/tests/07-probar-aspirantes.sql`.

## 10. Persistencia en Docker

Los archivos de datos de Oracle no viven dentro del contenedor. `docker-compose.yml` monta el volumen nombrado `sareduccion_oracle_data` (declarado como `oracle_data`) en `/opt/oracle/oradata`, que es el directorio donde la imagen `gvenzl/oracle-xe:21-slim` guarda sus ficheros de base de datos, incluidos los datafiles del PDB `XEPDB1`.

| Elemento | Valor |
| --- | --- |
| Contenedor | `sareduccion-oracle`. |
| Imagen | `gvenzl/oracle-xe:21-slim`. |
| Volumen de datos | `sareduccion_oracle_data`, montado en `/opt/oracle/oradata`. |
| Política de reinicio | `unless-stopped`. |
| Montajes adicionales | `docker/init-db/init-db.sh` y `docker/init-db/` en modo solo lectura; no contienen datos. |
| Directorio de respaldos | `./backups/oracle` del repositorio montado en `/opt/sareduccion/backups`, que es la ruta de `SAR_BACKUP_DIR`. Independiente del volumen de datos. |
| Healthcheck | `healthcheck.sh` de la imagen más la existencia de `/opt/oracle/oradata/.sareduccion-init.complete`. Intervalo 30 s, timeout 30 s, 20 reintentos y `start_period` de 900 s. |

El `start_period` de 900 s es intencional: la primera inicialización (crear la base y aplicar `docker/init-db/`) puede tardar 8-10 minutos en equipos con poca memoria o con intercambio a disco activo. Durante ese periodo los fallos del healthcheck no consumen reintentos, de modo que Compose no cancela el servicio `backend` con `dependency failed to start` antes de que Oracle termine.

El volumen es un recurso independiente del contenedor y de la imagen: sobrevive a que el contenedor se elimine o a que la imagen se reconstruya. Esta separación es la que permite recrear los contenedores sin perder la base.

### 10.1 Qué operaciones conservan los datos

| Operación | Efecto sobre los datos | Comentario |
| --- | --- | --- |
| `docker stop` / `docker start` | Conserva | Detiene y reinicia el mismo contenedor. |
| `docker compose stop` / `start` | Conserva | Igual que el anterior, sobre los servicios de Compose. |
| `docker compose down` | Conserva | Elimina contenedores y red; el volumen `sareduccion_oracle_data` no se toca. |
| `docker rm <contenedor>` | Conserva | Al eliminar un contenedor no se eliminan los volúmenes nombrados que usa. |
| `docker image rm` | Conserva | Solo elimina la imagen; los datos están en el volumen. |
| `docker compose up -d --build` | Conserva | Recrea contenedores y reconstruye imágenes; el volumen permanece y Oracle lo reutiliza. |
| `docker compose down -v` | **Elimina** | Borra los volúmenes nombrados declarados en el proyecto, incluido `sareduccion_oracle_data`. |
| `docker volume rm sareduccion_oracle_data` | **Elimina** | Borrado explícito del volumen. |
| `docker volume prune` | **Puede eliminar** | Borra volúmenes sin contenedor asociado. No debe ejecutarse en este entorno. |

Regla práctica: mientras no se use `-v` ni se borre el volumen, recrear contenedores e imágenes es seguro. Un `down -v` sobre esta base obliga a reinstalar el esquema desde cero y a repetir el seed; si no hay respaldo, los datos se pierden.

## 11. Respaldo y recuperación

Existen dos niveles de protección independientes:

| Nivel | Cobertura | Limitación |
| --- | --- | --- |
| Volumen `sareduccion_oracle_data` | Toda la instancia: ficheros de datos, redo, control files y configuración de la PDB. | Vive en el mismo equipo. No protege ante pérdida del disco ni borrado del volumen. |
| Respaldo lógico con Data Pump (`expdp`) | Los objetos y datos del esquema `SA_REDUCCION`. | No incluye cuentas, roles, grants, directorios ni la configuración de la instancia. |

Ambos son complementarios: el volumen permite arrancar tal cual, y el `.dmp` permite reconstruir el esquema en otra instancia.

### 11.1 Requisitos

- `expdp` e `impdp` están disponibles en `$ORACLE_HOME/bin` (`/opt/oracle/product/21c/dbhomeXE/bin`). La edición Express los incluye.
- Se usa un directorio lógico propio, `SAR_BACKUP_DIR`, que apunta a `/opt/sareduccion/backups`, con `READ, WRITE` concedidos a `SA_REDUCCION`. No depende de `DATA_PUMP_DIR` (que apunta a una subcarpeta distinta de propiedad de Data Pump) ni del privilegio `EXP_FULL_DATABASE`.
- La exportación se ejecuta conectando como `SA_REDUCCION`: es su propio esquema, por lo que no necesita el privilegio de exportación de base completa.
- La ruta `/opt/sareduccion/backups` está montada desde el anfitrión mediante un bind mount en `docker-compose.yml`:

```yaml
- ./backups/oracle:/opt/sareduccion/backups
```

Esto es lo que hace que el respaldo sea **independiente del contenedor y del volumen de datos**: `expdp` escribe directamente en `backups/oracle/` del repositorio, sin necesidad de `docker cp`. El directorio está excluido por `.gitignore` y sobrevive a `docker compose down`, a `docker rm` del contenedor y al borrado del volumen.

El directorio Oracle lo crea `01-configurar-esquema.sql` (sección 4), que se ejecuta como SYS porque `CREATE DIRECTORY` es un privilegio de instancia, y después concede `READ, WRITE` al propietario del esquema. Una instalación limpia ya lo incluye; no hay ningún paso manual.

### 11.2 Generar el respaldo

Se exporta el esquema completo (estructura, datos, paquetes, índices, restricciones, secuencias y triggers) a un archivo con marca de fecha y hora que aparece directamente en `backups/oracle/`.

La contraseña se lee de `.env` y se entrega al contenedor como variable de entorno, no como argumento del comando: así no queda en el historial del shell ni en la lista de procesos del anfitrión. `expdp` la recibe al expandir la variable ya dentro del contenedor.

**Linux, macOS o Git Bash:**

```bash
# 1. Cargar las variables de .env en la sesion actual.
set -a
. ./.env
set +a

# 2. Exportar el esquema.
TS=$(date +%Y%m%d-%H%M%S)
docker exec -e SARTS="$TS" -e ORACLE_PW="$ORACLE_APP_PASSWORD" sareduccion-oracle \
  sh -c 'expdp "SA_REDUCCION/${ORACLE_PW}@//localhost:1521/XEPDB1" \
    SCHEMAS=SA_REDUCCION \
    DIRECTORY=SAR_BACKUP_DIR \
    DUMPFILE="sa_reduccion_${SARTS}.dmp" \
    LOGFILE="sa_reduccion_${SARTS}.exp.log" \
    REUSE_DUMPFILES=N \
    CONTENT=ALL \
    EXCLUDE=STATISTICS \
    PARALLEL=1'
```

**Windows PowerShell:**

```powershell
# 1. Cargar las variables de .env en la sesion actual.
Get-Content .env | ForEach-Object {
    if ($_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*?)\s*$') {
        [Environment]::SetEnvironmentVariable($matches[1], $matches[2], 'Process')
    }
}

# 2. Exportar el esquema. Las comillas simples mantienen las variables
#    para que se expandan dentro del contenedor.
$TS = Get-Date -Format 'yyyyMMdd-HHmmss'
docker exec -e SARTS=$TS -e ORACLE_PW=$env:ORACLE_APP_PASSWORD sareduccion-oracle `
  sh -c 'expdp "SA_REDUCCION/${ORACLE_PW}@//localhost:1521/XEPDB1" SCHEMAS=SA_REDUCCION DIRECTORY=SAR_BACKUP_DIR DUMPFILE="sa_reduccion_${SARTS}.dmp" LOGFILE="sa_reduccion_${SARTS}.exp.log" REUSE_DUMPFILES=N CONTENT=ALL EXCLUDE=STATISTICS PARALLEL=1'
```

Parámetros utilizados:

| Parámetro | Efecto |
| --- | --- |
| `SCHEMAS=SA_REDUCCION` | Exporta solo ese esquema; no requiere `EXP_FULL_DATABASE`. |
| `DIRECTORY=SAR_BACKUP_DIR` | Directorio lógico que apunta al bind mount del anfitrión. |
| `DUMPFILE` / `LOGFILE` | Nombres con marca de tiempo; evitan sobrescribir respaldos previos. |
| `REUSE_DUMPFILES=N` | Falla si el `.dmp` ya existe, en lugar de reutilizarlo. |
| `CONTENT=ALL` | Incluye definición y datos. |
| `EXCLUDE=STATISTICS` | Omite estadísticas del optimizador. |
| `PARALLEL=1` | Un solo worker, para reducir el consumo de memoria. |

Notas:

- La exportación se ejecuta conectando como `SA_REDUCCION`, que es el propietario del esquema.
- `expdp` **no acepta `as sysdba`** dentro de `USERID`: produce `LRM-00108: invalid positional parameter value 'sysdba'`. Conectarse como el propietario del esquema.
- `PARALLEL=1` y `EXCLUDE=STATISTICS` evitan que el proceso worker aborte por falta de memoria (`ORA-39014` / `ORA-39029`). En un equipo con recursos limitados conviene detener el contenedor `sareduccion-backend` durante el respaldo.
- El respaldo tarda varios minutos. `expdp` no devuelve el prompt hasta terminar; el avance se sigue en el `.exp.log`.
- No hace falta copiar nada: el `.dmp` y el `.exp.log` ya están en el anfitrión.

### 11.3 Verificar el respaldo

1. El archivo existe en `backups/oracle/` y tiene un tamaño mayor que cero.
2. El log termina con `successfully completed` y no contiene `ORA-`.
3. Se registra el hash SHA-256 para poder detectar alteraciones posteriores.

**Linux, macOS o Git Bash:**

```bash
ls -l backups/oracle/
grep -E "successfully completed|ORA-" backups/oracle/sa_reduccion_<TS>.exp.log
sha256sum backups/oracle/sa_reduccion_<TS>.dmp | tee backups/oracle/sa_reduccion_<TS>.dmp.sha256
sha256sum -c backups/oracle/sa_reduccion_<TS>.dmp.sha256
```

**Windows PowerShell:**

```powershell
Get-ChildItem backups/oracle
Select-String -Path backups/oracle/sa_reduccion_<TS>.exp.log -Pattern 'successfully completed|ORA-'
Get-FileHash -Algorithm SHA256 backups/oracle/sa_reduccion_<TS>.dmp
```

`sha256sum -c` (Linux) y la comparación del `Hash` devuelto por `Get-FileHash` (Windows) permiten confirmar más adelante que el archivo no cambió. Conservar el `.sha256` junto al `.dmp`.

### 11.4 Restaurar en un entorno aislado

La restauración se hace con `impdp`. Para no comprometer la base en uso, el procedimiento recomendado es levantar una **instancia independiente** con su propio contenedor, volumen y red, importar ahí y conservar `sareduccion_oracle_data` intacto. El contenedor aislado reutiliza el mismo `docker-compose.yml` y el mismo `.env`, así que **debe cargarse `.env` en la sesión igual que en el paso 1 de la sección 11.2** antes de ejecutar los comandos siguientes. Sustituir `<ClaveDestino1>` y `<TS>` por la contraseña que se elija para el esquema destino y por la marca de tiempo del respaldo.

#### 11.4.1 Requisitos y privilegios

- **`impdp` se ejecuta conectado como el propietario del esquema destino.** Si se conecta como `SA_REDUCCION` y se pide `REMAP_SCHEMA` hacia otro esquema, Oracle exige `IMP_FULL_DATABASE`; sin ese privilegio la importación falla. Conectarse directamente como el usuario destino evita el problema.
- **El esquema destino debe existir antes de importar** y tener `CREATE SESSION` y cuota (`UNLIMITED TABLESPACE` o cuota en `USERS`).
- **El esquema destino necesita `READ, WRITE` sobre `SAR_BACKUP_DIR`**, porque de ahí se leen el `.dmp` y el log.
- `impdp` **no crea usuarios, roles ni directorios**: hay que prepararlos antes.
- Como en `expdp`, `impdp` **no acepta `as sysdba`** dentro de `USERID` (`LRM-00108`).

> **Nunca** restaurar con `REMAP_SCHEMA=SA_REDUCCION:SA_REDUCCION` sobre la base en uso: sobrescribiría el esquema original.

#### 11.4.2 Levantar el entorno aislado

El archivo de ajuste renombra contenedor, volumen y red. No se redefine `ports`: Compose fusiona las listas de puertos, así que el puerto publicado se cambia con `ORACLE_PORT`.

**Linux, macOS o Git Bash:**

```bash
# Contenedor, volumen y red propios; el volumen original no se toca.
cat > /tmp/sar-rest.yml <<'YAML'
services:
  oracle-db:
    container_name: sar-rest-test
volumes:
  oracle_data:
    name: sar_rest_test_volume
networks:
  default:
    name: sar-rest-net
YAML

ORACLE_PORT=1523 docker compose -p sarrest \
  -f docker-compose.yml -f /tmp/sar-rest.yml up -d oracle-db
```

**Windows PowerShell:**

```powershell
@'
services:
  oracle-db:
    container_name: sar-rest-test
volumes:
  oracle_data:
    name: sar_rest_test_volume
networks:
  default:
    name: sar-rest-net
'@ | Set-Content -Encoding ascii "$env:TEMP\sar-rest.yml"

$env:ORACLE_PORT = '1523'
docker compose -p sarrest -f docker-compose.yml -f "$env:TEMP\sar-rest.yml" up -d oracle-db
```

El contenedor aislado ejecuta su propia inicialización sobre `sar_rest_test_volume` y queda con el directorio `SAR_BACKUP_DIR` apuntando a `./backups/oracle` del anfitrión, de modo que ve los `.dmp` sin copiar archivos. Hay que esperar a que termine:

```bash
docker inspect --format '{{.State.Health.Status}}' sar-rest-test
```

#### 11.4.3 Crear el esquema destino

**Linux, macOS o Git Bash:**

```bash
docker exec -i -e SYS_PW="$ORACLE_PASSWORD" sar-rest-test \
  sh -c 'sqlplus -s "SYS/${SYS_PW}@//localhost:1521/XEPDB1 as sysdba"' <<'SQL'
SET ECHO OFF
CREATE USER SA_REDUCCION_REST IDENTIFIED BY "<ClaveDestino1>";
GRANT CREATE SESSION, RESOURCE, UNLIMITED TABLESPACE TO SA_REDUCCION_REST;
GRANT READ, WRITE ON DIRECTORY SAR_BACKUP_DIR TO SA_REDUCCION_REST;
EXIT
SQL
```

**Windows PowerShell:**

```powershell
@'
SET ECHO OFF
CREATE USER SA_REDUCCION_REST IDENTIFIED BY "<ClaveDestino1>";
GRANT CREATE SESSION, RESOURCE, UNLIMITED TABLESPACE TO SA_REDUCCION_REST;
GRANT READ, WRITE ON DIRECTORY SAR_BACKUP_DIR TO SA_REDUCCION_REST;
EXIT
'@ | docker exec -i -e SYS_PW=$env:ORACLE_PASSWORD sar-rest-test sh -c 'sqlplus -s "SYS/${SYS_PW}@//localhost:1521/XEPDB1 as sysdba"'
```

#### 11.4.4 Importar

**Linux, macOS o Git Bash:**

```bash
docker exec -i -e REST_PW='<ClaveDestino1>' -e SARTS="$TS" sar-rest-test \
  sh -c 'impdp "SA_REDUCCION_REST/${REST_PW}@//localhost:1521/XEPDB1" \
    DIRECTORY=SAR_BACKUP_DIR \
    DUMPFILE="sa_reduccion_${SARTS}.dmp" \
    LOGFILE="restauracion_${SARTS}.log" \
    REMAP_SCHEMA=SA_REDUCCION:SA_REDUCCION_REST'
```

**Windows PowerShell:**

```powershell
docker exec -i -e REST_PW='<ClaveDestino1>' -e SARTS=$TS sar-rest-test sh -c 'impdp "SA_REDUCCION_REST/${REST_PW}@//localhost:1521/XEPDB1" DIRECTORY=SAR_BACKUP_DIR DUMPFILE="sa_reduccion_${SARTS}.dmp" LOGFILE="restauracion_${SARTS}.log" REMAP_SCHEMA=SA_REDUCCION:SA_REDUCCION_REST'
```

El log queda en `backups/oracle/restauracion_<TS>.log` y debe terminar con `successfully completed`, sin `ORA-` ni `UDI-` de error. Opcionalmente se añade `TABLE_EXISTS_ACTION=SKIP` si el esquema destino pudiera tener ya las tablas.

#### 11.4.5 Verificar la restauración

```sql
-- Conectado como SA_REDUCCION_REST en la instancia aislada.
SELECT COUNT(*) AS tablas   FROM user_tables    WHERE table_name NOT LIKE 'SYS_%';
SELECT COUNT(*) AS indices  FROM user_indexes   WHERE table_name NOT LIKE 'SYS_%';
SELECT COUNT(*) AS invalidos FROM user_objects  WHERE status <> 'VALID';
```

Resultados esperados: **21 tablas**, **33 índices** y **0 objetos inválidos**, más los conteos de filas que se quieran contrastar con el origen.

> Durante la importación, Data Pump crea una **tabla maestra transitoria** llamada `SYS_IMPORT_FULL_01` con sus propios índices. Por eso un `COUNT(*)` sin filtrar sobre `user_tables` devuelve 22 y sobre `user_indexes` devuelve 43. El filtro `table_name NOT LIKE 'SYS_%'` da las cifras reales.

Los objetos restaurados pertenecen a `SA_REDUCCION_REST`: eso es exactamente lo que hace `REMAP_SCHEMA`. El esquema `SA_REDUCCION` original no se lee ni se modifica en ningún paso.

#### 11.4.6 Desmontar el entorno aislado

```bash
docker compose -p sarrest -f docker-compose.yml -f /tmp/sar-rest.yml down -v
docker network rm sar-rest-net
docker volume ls | grep sareduccion_oracle_data
```

El `down -v` **debe** ejecutarse con el nombre de proyecto `-p sarrest` y el archivo de ajuste: así elimina únicamente `sar_rest_test_volume`. La última comprobación confirma que `sareduccion_oracle_data` sigue existiendo. Nunca ejecutar `docker compose down -v` sobre el proyecto original.

### 11.5 Qué no incluye el respaldo

La exportación es de esquema, no de la instancia completa. Queda fuera del `.dmp`:

| Elemento | Motivo | Cómo se recupera |
| --- | --- | --- |
| Cuentas Oracle (`SA_REDUCCION`, `SAR_BACKEND`, `ALU_21281181`, `SA01`) | Los usuarios se definen a nivel de instancia. | Scripts `03-crear-identidades-roles.sql` y `04-crear-cuenta-tecnica.sql`, con sus contraseñas desde `.env`. |
| Roles `ROL_SAR_ESTUDIANTE_SIM` y `ROL_SAR_ADMIN_SIM` | Son objetos de instancia. | Mismo script `03`. |
| Grants de `EXECUTE` a `SAR_BACKEND` | Los privilegios no viajan en la exportación de esquema. | `05-crear-paquetes-permisos.sql`. |
| Directorio `SAR_BACKUP_DIR` y sus grants | Objeto de instancia. | Lo crea `01-configurar-esquema.sql`; una instalación limpia ya lo incluye. |
| Contraseñas de cualquier cuenta | Nunca se exportan. | Se toman de `.env`. |
| Datos de otras PDB o del contenedor raíz | Fuera del alcance de `SCHEMAS=SA_REDUCCION`. | No aplica: el proyecto vive solo en `XEPDB1`. |
| Configuración de la instancia (parámetros, `spfile`, redo) | Pertenece al volumen, no al esquema. | El volumen `sareduccion_oracle_data`. |

En resumen: el `.dmp` recupera los objetos y datos de la aplicación, y los scripts de `docker/init-db/` recuperan todo lo que vive fuera del esquema. Ambos, más el volumen, forman la estrategia completa de recuperación.

## 12. Archivos relacionados

- [Configuración del esquema](docker/init-db/01-configurar-esquema.sql)
- [Estructura de la base de datos](docker/init-db/02-crear-estructura.sql)
- [Identidades y roles](docker/init-db/03-crear-identidades-roles.sql)
- [Cuenta técnica](docker/init-db/04-crear-cuenta-tecnica.sql)
- [Paquetes PL/SQL y permisos](docker/init-db/05-crear-paquetes-permisos.sql)
- [Datos de desarrollo](docker/init-db/06-seed-desarrollo.sql)
- [Orquestador de inicialización](docker/init-db/init-db.sh)
- [Verificación de instalación](database/tests/05-verificar-instalacion.sql)
- [Prueba de permisos](database/tests/06-probar-permisos.sql)
- [Prueba de escenarios de aspirantes](database/tests/07-probar-aspirantes.sql)
- [Configuración de Docker Compose](docker-compose.yml)
- [Plantilla de variables de entorno](.env.example)
- [Ejecución del proyecto](README.md)
- [Arquitectura técnica](ARQUITECTURA.md)
