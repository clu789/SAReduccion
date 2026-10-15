# Base de datos de SAReduccion

## 1. Propósito

La base de datos administra las solicitudes de reducción del monto de inscripción y reinscripción. El modelo utiliza Oracle XE 21c y se construye con los scripts de inicialización:

[`docker/init-db/02-crear-estructura.sql`](docker/init-db/02-crear-estructura.sql)

El script crea tablas, claves primarias, claves foráneas, restricciones, índices, secuencias, triggers y datos iniciales de catálogos. Esta versión elimina la tabla `Usuarios` y utiliza los identificadores de los perfiles para relacionar la identidad con Oracle.

## 2. Ubicación y ejecución

`docker/init-db/init-db.sh` ejecuta los SQL en orden. La imagen conecta los `.sql` de `/container-entrypoint-initdb.d` como `SYS` en `XE`, así que el orquestador cambia a `XEPDB1` y conecta como `SA_REDUCCION` para crear el esquema y los paquetes. Solo el orquestador se monta en el directorio de auto-inicialización; los SQL se montan en una ruta separada para evitar su ejecución implícita con un usuario o contexto incorrecto.

Los scripts se ejecutan solo con un volumen Oracle vacío. Para reconstruir el entorno desde cero:

```bash
docker compose down -v
docker compose up -d oracle-db
```

**Advertencia:** `down -v` elimina los datos persistidos en los volúmenes del proyecto. No debe ejecutarse sobre una base con información que se quiera conservar. Reemplazar el archivo SQL no migra automáticamente una base existente.

Las verificaciones manuales están en `database/tests/` y no se ejecutan automáticamente.

### 2.1 Scripts de instalación, migración y datos

| Script | Propósito | Se ejecuta |
| --- | --- | --- |
| `01-configurar-esquema.sql` | Crea y configura el esquema `SA_REDUCCION` y sus requisitos de tablespace/usuario. | Instalación. |
| `02-crear-estructura.sql` | Tablas, claves, restricciones, índices, secuencias, triggers y catálogos. | Instalación. |
| `03-crear-identidades-roles.sql` | Roles `ROL_SAR_ESTUDIANTE_SIM` y `ROL_SAR_ADMIN_SIM` y cuentas individuales. | Instalación. |
| `04-crear-paquetes-permisos.sql` | Los tres paquetes PL/SQL y sus grants a `SAR_BACKEND`. | Instalación y reinstalación de paquetes. |
| `05-crear-cuenta-tecnica.sql` | Crea/verifica `SAR_BACKEND` con `CREATE SESSION`. | Instalación y migración de cuenta técnica. |
| `06-seed-sareduccion.sql` | Datos de desarrollo: catálogos, convocatorias, montos, estudiantes, administradores y solicitudes de ejemplo. | Datos de desarrollo. |
| `07-migracion-aspirantes.sql` | Migración de la tabla `Aspirantes`: alta de `correo`, retiro de `claveControl` y restricciones asociadas. | Migración de bases existentes. |
| `08-correo-datos-desarrollo.sql` | Asigna correos válidos a los aspirantes de desarrollo para cumplir `ck_aspirantes_correo`. | Datos de desarrollo. |
| `init-db.sh` | Orquestador del orden de ejecución y de las credenciales. | Automático en volumen vacío. |
| `migrate-backend.sh` | Reinstala cuenta técnica, paquetes y grants en una base existente; se detiene ante privilegios inesperados. | Migración. |

Los scripts `06`, `07` y `08` son de datos o migración: no forman parte del esquema mínimo y no deben reejecutarse sobre una base con datos reales sin revisar su idempotencia.

### 2.2 Verificaciones

| Prueba | Alcance |
| --- | --- |
| `database/tests/05-verificar-instalacion.sql` | Existencia y validez de los objetos del esquema. |
| `database/tests/06-probar-permisos.sql` | Privilegios efectivos de `SAR_BACKEND` y de los roles simulados. |
| `database/tests/07-probar-aspirantes.sql` | Escenarios de `PKG_ASPIRANTE`: parámetros nulos, sin coincidencia, año no vigente y coincidencia ambigua entre periodos. |

Estas pruebas son scripts SQL de comprobación manual: se conectan y muestran resultados, pero no se integran en una suite automatizada.

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

Registra aspirantes mediante la clave compuesta `(ficha, periodo, anio)`, sin tabla de usuarios independiente. Un mismo aspirante puede tener registros en periodos o años distintos, por lo que la ficha sola no identifica una fila.

| Atributo | Tipo | Restricciones | Descripción |
| --- | --- | --- | --- |
| `ficha` | `VARCHAR2(18)` | PK (1), NOT NULL | Identificador de registro del aspirante. |
| `periodo` | `NUMBER(2)` | PK (2), NOT NULL, FK `fk_aspirante_periodo` | Periodo del registro; referencia `Periodos`. |
| `anio` | `NUMBER(4)` | PK (3), NOT NULL, CHECK `ck_aspirantes_anio` | Año del registro, entre 2000 y 2200. |
| `Nombre` | `VARCHAR2(50)` | NOT NULL | Nombre del aspirante. |
| `PrimerApellido` | `VARCHAR2(50)` | NOT NULL | Primer apellido. |
| `SegundoApellido` | `VARCHAR2(50)` | NULL | Segundo apellido. |
| `Semestre` | `NUMBER` | NULL, CHECK `ck_aspirantes_semestre` | Semestre entre 1 y 20, o nulo. |
| `Curp` | `VARCHAR2(18)` | NOT NULL, CHECK `ck_aspirantes_curp` | CURP; la restricción exige longitud exacta de 18. |
| `correo` | `VARCHAR2(254)` | NOT NULL, CHECK `ck_aspirantes_correo` | Correo institucional o personal admitido. |

**Clave primaria compuesta.** `pk_aspirantes` es `(ficha, periodo, anio)`. Es la razón por la que la validación de acceso debe obtener las tres columnas: la búsqueda por `ficha` puede devolver más de una fila.

**Columna `correo`.** Es obligatoria para todo registro nuevo. Está pensada como el canal de verificación futuro (código de un solo uso) y hoy se usa como dato de contacto de desarrollo.

**Dominios de correo admitidos.** `ck_aspirantes_correo` aplica una expresión regular insensible a mayúsculas (`'i'`) que exige la forma `usuario@dominio`, con usuario alfanumérico y puntos, y uno de estos siete dominios:

| Dominios admitidos |
| --- |
| `gmail.com`, `hotmail.com`, `outlook.com`, `live.com`, `yahoo.com`, `yahoo.com.mx`, `icloud.com` |

**Eliminación de `claveControl`.** La versión anterior de la tabla incluía una columna `claveControl` pensada como secreto de acceso provisional. Se eliminó junto con el cambio de modelo de autenticación: el aspirante ya no acredita un secreto propio, sino la coincidencia de `ficha` y `Curp`. La migración `07-migracion-aspirantes.sql` aplicó el cambio (alta de `correo`, retiro de `claveControl`) sobre la base existente sin perder filas.

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



### Índices únicos funcionales de `Solicitudes`

Como una fila solo tiene número de control o ficha (nunca ambos), la unicidad por origen se resuelve con dos índices basados en expresiones que anulan la columna que no aplica. Así `NULL` no participa en la comparación y se evita la ambigüedad de un índice único sobre columnas opcionales.

| Índice | Tipo | Expresiones | Evita |
| --- | --- | --- | --- |
| `uk_solicitud_estudiante_conv` | `FUNCTION-BASED NORMAL`, UNIQUE | `CASE WHEN "NUM_CONTROL" IS NOT NULL THEN "NUM_CONTROL" END`, `CASE WHEN "NUM_CONTROL" IS NOT NULL THEN "ID_CONVOCATORIA" END` | Dos solicitudes del mismo estudiante en la misma convocatoria. |
| `uk_solicitud_aspirante_conv` | `FUNCTION-BASED NORMAL`, UNIQUE | `CASE WHEN "FICHA" IS NOT NULL THEN "FICHA" END`, `CASE WHEN "FICHA" IS NOT NULL THEN "ANIO" END`, `CASE WHEN "FICHA" IS NOT NULL THEN "PERIODO" END`, `CASE WHEN "FICHA" IS NOT NULL THEN "ID_CONVOCATORIA" END` | Dos solicitudes del mismo aspirante (misma ficha, año y periodo) en la misma convocatoria. |

Los cuatro segmentos del índice de aspirante reproducen la clave compuesta de `Aspirantes`, de modo que la unicidad se evalúa sobre la identidad completa del aspirante y no solo sobre la ficha.

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
| Healthcheck | `healthcheck.sh` de la imagen más la existencia de `/opt/oracle/oradata/.sareduccion-init.complete`. |

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

Regla práctica: mientras no se use `-v` ni se borre el volumen, recrear contenedores e imágenes es seguro. Un `down -v` sobre esta base obliga a reinstalar el esquema desde cero y a repetir las migraciones y el seed.

## 11. Respaldo y recuperación

Existen dos niveles de protección independientes:

| Nivel | Cobertura | Limitación |
| --- | --- | --- |
| Volumen `sareduccion_oracle_data` | Toda la instancia: ficheros de datos, redo, control files y configuración de la PDB. | Vive en el mismo equipo. No protege ante pérdida del disco ni borrado del volumen. |
| Respaldo lógico con Data Pump (`expdp`) | Los objetos y datos del esquema `SA_REDUCCION`. | No incluye cuentas, roles, grants, directorios ni la configuración de la instancia. |

Ambos son complementarios: el volumen permite arrancar tal cual, y el `.dmp` permite reconstruir el esquema en otra instancia.

### 11.1 Requisitos

- `expdp` e `impdp` están disponibles en `$ORACLE_HOME/bin` (`/opt/oracle/product/21c/dbhomeXE/bin`). La edición Express los incluye; en esta configuración la única limitación relevante es que la exportación de esquema requiere un objeto `DIRECTORY` con permiso de escritura para la cuenta que exporta.
- El directorio físico `/opt/oracle/admin/XE/dpdump` existe en el contenedor. Es la ruta por defecto de `DATA_PUMP_DIR`, que apunta a una subcarpeta distinta.
- Se usa un directorio lógico propio, `SAR_BACKUP_DIR`, que apunta a `/opt/oracle/admin/XE/dpdump`, con `READ, WRITE` concedidos a `SA_REDUCCION`. Así el respaldo no depende de un directorio propiedad de Data Pump ni de privilegios `EXP_FULL_DATABASE`.
- La exportación se ejecuta conectando como `SA_REDUCCION`: es su propio esquema, por lo que no necesita el privilegio de exportación de base completa.

### 11.2 Generar el respaldo

Se exporta el esquema completo (estructura, datos, paquetes, índices, restricciones, secuencias y triggers) a un archivo con marca de fecha y hora:

```bash
TS=$(date +%Y%m%d-%H%M%S)

docker exec -e SARTS="$TS" sareduccion-oracle \
  expdp "SA_REDUCCION/<APP_PASSWORD>@//localhost:1521/XEPDB1" \
    SCHEMAS=SA_REDUCCION \
    DIRECTORY=SAR_BACKUP_DIR \
    DUMPFILE="sa_reduccion_${SARTS}.dmp" \
    LOGFILE="sa_reduccion_${SARTS}.exp.log" \
    REUSE_DUMPFILES=N \
    CONTENT=ALL \
    EXCLUDE=STATISTICS \
    PARALLEL=1
```

Sustituir `<APP_PASSWORD>` por el valor de `ORACLE_APP_PASSWORD` leído desde `.env`; no escribir la contraseña en el repositorio ni en la documentación. El sufijo de tiempo evita sobrescribir respaldos previos y `REUSE_DUMPFILES=N` impide que se reutilice un archivo existente.

Notas de ejecución:

- `EXCLUDE=STATISTICS` y `PARALLEL=1` reducen la memoria necesaria. En equipos con memoria limitada, un `expdp` con proceso worker adicional puede terminar con `ORA-39014` / `ORA-39029` (el worker aborta antes de completar el trabajo).
- `CONTENT=ALL` incluye definición y datos; `CONTENT=METADATA_ONLY` exportaría solo la estructura.
- El proceso escribe el `.dmp` y el `.log` en la ruta del directorio Oracle dentro del contenedor. De ahí se copian al anfitrión.

### 11.3 Extraer el respaldo al anfitrión

Los respaldos se guardan en `backups/oracle/` del repositorio, ruta ya excluida por `.gitignore`:

```bash
mkdir -p backups/oracle

docker cp "sareduccion-oracle:/opt/oracle/admin/XE/dpdump/sa_reduccion_${TS}.dmp" \
  "backups/oracle/sa_reduccion_${TS}.dmp"
docker cp "sareduccion-oracle:/opt/oracle/admin/XE/dpdump/sa_reduccion_${TS}.exp.log" \
  "backups/oracle/sa_reduccion_${TS}.exp.log"
```

### 11.4 Verificar el respaldo

1. Comprobar que el archivo existe en el anfitrión y tiene tamaño mayor que cero.
2. Revisar el log: debe terminar con `successfully completed` y no contener `ORA-`.
3. Calcular el hash del archivo para detectar corrupción posterior:

```bash
sha256sum backups/oracle/sa_reduccion_*.dmp
```

Registrar el hash junto al nombre del archivo permite comprobar más adelante que el respaldo no se alteró.

### 11.5 Restaurar

La restauración se hace con `impdp` sobre un esquema destino. Para no sobrescribir la base actual, el destino debe ser un esquema de pruebas distinto, por ejemplo `SA_REDUCCION_REST`:

```sql
-- Como SYS en XEPDB1
CREATE USER SA_REDUCCION_REST IDENTIFIED BY "<password>";
GRANT CONNECT, RESOURCE, UNLIMITED TABLESPACE TO SA_REDUCCION_REST;
```

```bash
docker exec sareduccion-oracle \
  impdp "SA_REDUCCION/<APP_PASSWORD>@//localhost:1521/XEPDB1" \
    DIRECTORY=SAR_BACKUP_DIR \
    DUMPFILE="sa_reduccion_<TS>.dmp" \
    LOGFILE="restauracion_<TS>.log" \
    REMAP_SCHEMA=SA_REDUCCION:SA_REDUCCION_REST \
    TABLE_EXISTS_ACTION=SKIP
```

Puntos importantes de la restauración:

- `REMAP_SCHEMA` es obligatorio para restaurar en otro esquema; sin él, `impdp` intentaría escribir en `SA_REDUCCION`.
- La cuenta destino debe existir antes de importar y tener cuota o `UNLIMITED TABLESPACE`.
- `impdp` no crea cuentas ni roles: los objetos que dependan de roles hay que prepararlos antes.
- Para reinstalar el esquema original hay que borrar el contenido destino; no debe hacerse sobre la base en uso.
- Tras importar, los objetos entran referenciando el esquema destino y hay que comprobar que no queden objetos inválidos.

### 11.6 Qué no incluye el respaldo

La exportación es de esquema, no de la instancia completa. Queda fuera del `.dmp`:

| Elemento | Motivo | Cómo se recupera |
| --- | --- | --- |
| Cuentas Oracle (`SA_REDUCCION`, `SAR_BACKEND`, `ALU_21281181`, `SA01`) | Los usuarios se definen a nivel de instancia. | Script `03-crear-identidades-roles.sql` y `05-crear-cuenta-tecnica.sql`, con sus contraseñas desde `.env`. |
| Roles `ROL_SAR_ESTUDIANTE_SIM` y `ROL_SAR_ADMIN_SIM` | Son objetos de instancia. | Mismo script `03`. |
| Grants de `EXECUTE` a `SAR_BACKEND` | Los privilegios no viajan en la exportación de esquema. | `04-crear-paquetes-permisos.sql` / `migrate-backend.sh`. |
| Directorio `SAR_BACKUP_DIR` y sus grants | Objeto de instancia. | Debe recrearse a mano para poder repetir un respaldo o una restauración. |
| Contraseñas de cualquier cuenta | Nunca se exportan. | Se toman de `.env`. |
| Datos de otras PDB o del contenedor raíz | Fuera del alcance de `SCHEMAS=SA_REDUCCION`. | No aplica: el proyecto vive solo en `XEPDB1`. |
| Configuración de la instancia (parámetros, `spfile`, redo) | Pertenece al volumen, no al esquema. | El volumen `sareduccion_oracle_data`. |

En resumen: el `.dmp` recupera los objetos y datos de la aplicación, y los scripts de `docker/init-db/` recuperan todo lo que vive fuera del esquema. Ambos, más el volumen, forman la estrategia completa de recuperación.

## 12. Sobre la imagen del diagrama

El diagrama debe corresponder a la versión V5, con los nombres de tablas en plural y sin la tabla `Usuarios`. Cuando se disponga de una imagen actualizada en la misma carpeta, puede incluirse así:

```markdown
![Diagrama de la base de datos](image.png)
```

## 13. Archivos relacionados

- [Script de creación de la base de datos](docker/init-db/02-crear-estructura.sql)
- [Paquetes PL/SQL y permisos](docker/init-db/04-crear-paquetes-permisos.sql)
- [Migración de aspirantes](docker/init-db/07-migracion-aspirantes.sql)
- [Prueba de escenarios de aspirantes](database/tests/07-probar-aspirantes.sql)
- [Configuración Docker](docker-compose.yml)
- [Ejecución del proyecto](README.md)
- [Arquitectura técnica](ARQUITECTURA.md)
