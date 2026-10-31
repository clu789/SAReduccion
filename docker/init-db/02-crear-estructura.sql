-- ============================================================================
-- 02-crear-estructura.sql
-- Proyecto: SAReduccion - Oracle XE 21c / esquema SA_REDUCCION
-- Define la estructura y las reglas de integridad del sistema.
--
-- OBJETIVO
--   Crear 22 tablas, 16 secuencias, triggers, índices y catálogos base.
--   Este archivo NO crea cuentas ni concede permisos a otros perfiles.
--
-- EJECUCIÓN
--   SQL*Plus o SQLcl conectado COMO SA_REDUCCION a la PDB XEPDB1.
--   Ejecutar después de 01-configurar-esquema.sql.
--   Ejemplo: sqlplus SA_REDUCCION@//localhost:1521/XEPDB1
--            @02-crear-estructura.sql
--   La contraseña debe proporcionarse de forma segura (no incluirla en scripts).
--
-- ADVERTENCIAS
--   * SOLO para un esquema vacío: no es repetible y aborta si detecta tablas.
--   * En Oracle, el DDL confirma transacciones implícitamente: un fallo a mitad
--     de ejecución NO revierte las tablas creadas previamente.
--   * Revisar los errores de compilación de triggers en la salida.
--   * Las reglas de negocio se completan con los paquetes de
--     05-crear-paquetes-permisos.sql; sin ellos el esquema no debe recibir
--     operaciones de la aplicación.
--   * Requiere ejecutarse con SET SQLBLANKLINES ON (lo aplica init-db.sh),
--     porque el archivo contiene líneas en blanco dentro de bloques PL/SQL.
-- ============================================================================

SET ECHO OFF
SET SERVEROUTPUT ON
SET VERIFY OFF
WHENEVER SQLERROR EXIT SQL.SQLCODE

-- Verificación preventiva: impedir creación bajo SYS u otro esquema/PDB.
DECLARE
    v_usuario VARCHAR2(128) := SYS_CONTEXT('USERENV', 'SESSION_USER');
    v_contenedor VARCHAR2(128) := SYS_CONTEXT('USERENV', 'CON_NAME');
    v_tablas NUMBER;
BEGIN
    -- Codigos -20090/-20091 (no -20011/-20012): estos son exclusivos del
    -- script de arranque (corre una sola vez al crear el esquema) y no deben
    -- coincidir con los que usa trg_respuestas_validacion en produccion.
    IF v_usuario <> 'SA_REDUCCION' THEN
        RAISE_APPLICATION_ERROR(-20090,
            'Conectarse como SA_REDUCCION; sesion actual: ' || v_usuario);
    END IF;
    IF v_contenedor <> 'XEPDB1' THEN
        RAISE_APPLICATION_ERROR(-20091,
            'Conectarse a XEPDB1; contenedor actual: ' || v_contenedor);
    END IF;
    SELECT COUNT(*) INTO v_tablas FROM USER_TABLES;
    IF v_tablas > 0 THEN
        RAISE_APPLICATION_ERROR(-20013,
            'El esquema ya tiene ' || v_tablas || ' tablas. No repetir el script.');
    END IF;
    DBMS_OUTPUT.PUT_LINE('Validación correcta: esquema vacío SA_REDUCCION / XEPDB1.');
END;
/

-- ================================================================
-- BASE DE DATOS: Servicio Administrativo de Reducción del monto
--               de inscripción
-- Motor: Oracle Database
-- ================================================================

-- ================================================================
-- TABLAS CATÁLOGO / SEGURIDAD
-- ================================================================

-- ------------------------------------------------------------------------
-- TABLA PERIODOS: Catálogo de periodos académicos utilizado por convocatorias y aspirantes.
-- ------------------------------------------------------------------------
CREATE TABLE Periodos (
    id_periodo        NUMBER(2)     CONSTRAINT pk_periodos PRIMARY KEY,
    descripcion       VARCHAR2(50)  NOT NULL,
    CONSTRAINT uk_periodos_descripcion UNIQUE (descripcion)
);

-- ------------------------------------------------------------------------
-- TABLA TIPO_SOLICITUDES: Clasificación de los tipos de solicitud disponibles.
-- ------------------------------------------------------------------------
CREATE TABLE Tipo_Solicitudes (
    id_tipo_solicitud NUMBER(10)    CONSTRAINT pk_tipo_solicitudes PRIMARY KEY,
    nombre            VARCHAR2(50)  NOT NULL,
    descripcion       VARCHAR2(200)
);

-- ------------------------------------------------------------------------
-- TABLA TIPO_PREGUNTAS: Catálogo de modalidades de preguntas para formularios.
-- ------------------------------------------------------------------------
CREATE TABLE Tipo_Preguntas (
    id_tipo_pregunta NUMBER(10)    CONSTRAINT pk_tipo_preguntas PRIMARY KEY,
    nombre           VARCHAR2(50)  NOT NULL,
    descripcion      VARCHAR2(400) NOT NULL,
    CONSTRAINT uk_tipo_preguntas_nombre UNIQUE (nombre)
);

-- ------------------------------------------------------------------------
-- TABLA ESTATUS_SOLICITUDES: Estados posibles de una solicitud.
-- ------------------------------------------------------------------------
CREATE TABLE Estatus_Solicitudes (
    id_estatus_solicitud NUMBER(10)    CONSTRAINT pk_estatus_solicitudes PRIMARY KEY,
    nombre               VARCHAR2(100) NOT NULL,
    descripcion          VARCHAR2(200),
    CONSTRAINT uk_estatus_solicitudes_nombre UNIQUE (nombre)
);

-- ------------------------------------------------------------------------
-- TABLA ESTATUS_DEDUCCIONES: Estados posibles de una deducción autorizada.
-- ------------------------------------------------------------------------
CREATE TABLE Estatus_Deducciones (
    id_estatus_deducciones NUMBER(10)   CONSTRAINT pk_estatus_deducciones PRIMARY KEY,
    nombre                 VARCHAR2(50) NOT NULL,
    descripcion            VARCHAR2(300),
    CONSTRAINT uk_estatus_deducciones_nombre UNIQUE (nombre)
);

-- ================================================================
-- PERSONAS / PERFILES
-- ================================================================

-- ------------------------------------------------------------------------
-- TABLA ESTUDIANTES: Datos de estudiantes identificados por número de control.
-- ------------------------------------------------------------------------
CREATE TABLE Estudiantes (
    numero_control   VARCHAR2(10)  CONSTRAINT pk_estudiantes PRIMARY KEY,
    nombre           VARCHAR2(50)  NOT NULL,
    primer_apellido  VARCHAR2(50)  NOT NULL,
    segundo_apellido VARCHAR2(50),
    carrera          VARCHAR2(100),
    semestre         NUMBER(2),
    CONSTRAINT ck_estudiantes_semestre CHECK (semestre IS NULL OR semestre BETWEEN 1 AND 20)
);

-- ------------------------------------------------------------------------
-- TABLA ASPIRANTES: Datos de aspirantes identificados por ficha, periodo y año.
-- ------------------------------------------------------------------------
CREATE TABLE Aspirantes (
    ficha            VARCHAR2(18)  NOT NULL,
    periodo          NUMBER(2)     NOT NULL,
    anio             NUMBER(4)     NOT NULL,
    nombre           VARCHAR2(50)  NOT NULL,
    primer_apellido  VARCHAR2(50)  NOT NULL,
    segundo_apellido VARCHAR2(50),
    semestre         NUMBER(2),
    curp             VARCHAR2(18)  NOT NULL,
    correo           VARCHAR2(254) NOT NULL,
    CONSTRAINT pk_aspirantes PRIMARY KEY (ficha, periodo, anio),
    CONSTRAINT fk_aspirante_periodo
        FOREIGN KEY (periodo) REFERENCES Periodos(id_periodo),
    CONSTRAINT ck_aspirantes_anio CHECK (anio BETWEEN 2000 AND 2200),
    CONSTRAINT ck_aspirantes_curp CHECK (LENGTH(curp) = 18),
    CONSTRAINT ck_aspirantes_semestre CHECK (semestre IS NULL OR semestre BETWEEN 1 AND 20),
    CONSTRAINT ck_aspirantes_correo CHECK (
        REGEXP_LIKE(
            correo,
            '^[A-Za-z0-9_%+-]+(\.[A-Za-z0-9_%+-]+)*@(yahoo\.com\.mx|gmail\.com|hotmail\.com|outlook\.com|live\.com|yahoo\.com|icloud\.com)$',
            'i')
    )
);

-- ------------------------------------------------------------------------
-- TABLA ADMINISTRADORES: Datos del personal administrador.
-- ------------------------------------------------------------------------
CREATE TABLE Administradores (
    id_administrador VARCHAR2(10) CONSTRAINT pk_administradores PRIMARY KEY,
    nombre           VARCHAR2(50) NOT NULL,
    primer_apellido  VARCHAR2(50) NOT NULL,
    segundo_apellido VARCHAR2(50)
);

-- ================================================================
-- CONVOCATORIAS
-- ================================================================

-- ------------------------------------------------------------------------
-- TABLA CONVOCATORIAS: Periodos de apertura y cierre de solicitudes.
-- ------------------------------------------------------------------------
CREATE TABLE Convocatorias (
    id_convocatoria             NUMBER(10)   CONSTRAINT pk_convocatorias PRIMARY KEY,
    fecha_inicio_solicitudes    DATE         NOT NULL,
    fecha_fin_solicitudes       DATE         NOT NULL,
    fecha_limite_autorizacion   DATE         NOT NULL,
    fecha_limite_aplicacion     DATE         NOT NULL,
    id_periodo                  NUMBER(2)    NOT NULL,
    anio                        NUMBER(4)    NOT NULL,
    CONSTRAINT fk_convocatoria_periodo
        FOREIGN KEY (id_periodo) REFERENCES Periodos(id_periodo),
    CONSTRAINT ck_convocatoria_fechas CHECK (
        TRUNC(fecha_inicio_solicitudes) <= TRUNC(fecha_fin_solicitudes)
        AND TRUNC(fecha_fin_solicitudes) <= TRUNC(fecha_limite_autorizacion)
        AND TRUNC(fecha_limite_autorizacion) <= TRUNC(fecha_limite_aplicacion)
    ),
    CONSTRAINT ck_convocatoria_anio CHECK (anio BETWEEN 2000 AND 2200),
    CONSTRAINT uk_convocatoria_periodo_anio UNIQUE (id_periodo, anio)
);

-- ------------------------------------------------------------------------
-- TABLA HISTORIAL_CONVOCATORIAS: conserva cambios confirmados de las cuatro
-- fechas. La creación inicial no produce historial. No almacena comentarios.
-- ------------------------------------------------------------------------
CREATE TABLE Historial_Convocatorias (
    id_historial_convocatoria  NUMBER(10)   CONSTRAINT pk_historial_conv PRIMARY KEY,
    id_convocatoria            NUMBER(10)   NOT NULL,
    id_administrador           VARCHAR2(10) NOT NULL,
    fecha_registro             DATE         DEFAULT SYSDATE NOT NULL,
    inicio_anterior            DATE         NOT NULL,
    fin_anterior               DATE         NOT NULL,
    autorizacion_anterior      DATE         NOT NULL,
    aplicacion_anterior        DATE         NOT NULL,
    inicio_nuevo               DATE         NOT NULL,
    fin_nuevo                  DATE         NOT NULL,
    autorizacion_nueva         DATE         NOT NULL,
    aplicacion_nueva           DATE         NOT NULL,
    CONSTRAINT fk_hconv_conv FOREIGN KEY (id_convocatoria)
        REFERENCES Convocatorias(id_convocatoria),
    CONSTRAINT fk_hconv_admin FOREIGN KEY (id_administrador)
        REFERENCES Administradores(id_administrador)
);

-- ------------------------------------------------------------------------
-- TABLA CONVOCATORIAS_TIPOSOLICITUDES: Relación entre convocatorias y tipos de solicitudes.
-- ------------------------------------------------------------------------
CREATE TABLE Convocatorias_TipoSolicitudes (
    id_convocatoria         NUMBER(10)   NOT NULL,
    id_tipo_solicitud       NUMBER(10)   NOT NULL,
    monto_deduccion         NUMBER(10,2) NOT NULL,
    fecha_asignacion_montos DATE DEFAULT SYSDATE NOT NULL,
    CONSTRAINT pk_conv_tipo_sol PRIMARY KEY (id_convocatoria, id_tipo_solicitud),
    CONSTRAINT fk_cts_convocatoria
        FOREIGN KEY (id_convocatoria) REFERENCES Convocatorias(id_convocatoria),
    CONSTRAINT fk_cts_tipo_solicitud
        FOREIGN KEY (id_tipo_solicitud) REFERENCES Tipo_Solicitudes(id_tipo_solicitud),
    CONSTRAINT ck_cts_monto_deduccion CHECK (monto_deduccion >= 0)
);

-- ================================================================
-- FORMULARIOS Y PREGUNTAS
-- ================================================================

-- ------------------------------------------------------------------------
-- TABLA FORMULARIOS: Formularios vinculados al proceso de solicitud.
-- ------------------------------------------------------------------------
CREATE TABLE Formularios (
    id_formulario      NUMBER(10)    CONSTRAINT pk_formularios PRIMARY KEY,
    nombre             VARCHAR2(100) NOT NULL,
    descripcion        VARCHAR2(200),
    instrucciones      VARCHAR2(4000),
    id_tipo_solicitud  NUMBER(10)    NOT NULL,
    CONSTRAINT fk_formulario_tipo_solicitud
        FOREIGN KEY (id_tipo_solicitud) REFERENCES Tipo_Solicitudes(id_tipo_solicitud),
    CONSTRAINT uk_formulario_tipo_solicitud UNIQUE (id_tipo_solicitud)
);

-- ------------------------------------------------------------------------
-- TABLA PREGUNTAS: Banco de preguntas que pueden formar parte de formularios.
-- ------------------------------------------------------------------------
CREATE TABLE Preguntas (
    id_pregunta     NUMBER(10)     CONSTRAINT pk_preguntas PRIMARY KEY,
    texto_pregunta  VARCHAR2(4000) NOT NULL,
    obligatoria     CHAR(1)        DEFAULT 'S' NOT NULL,
    tipo_pregunta   NUMBER(10)     NOT NULL,
    CONSTRAINT fk_pregunta_tipo
        FOREIGN KEY (tipo_pregunta) REFERENCES Tipo_Preguntas(id_tipo_pregunta),
    CONSTRAINT ck_pregunta_obligatoria CHECK (obligatoria IN ('S','N'))
);

-- ------------------------------------------------------------------------
-- TABLA FORMULARIO_PREGUNTAS: Relación de preguntas asignadas a formularios.
-- ------------------------------------------------------------------------
CREATE TABLE Formulario_Preguntas (
    id_formulario NUMBER(10) NOT NULL,
    id_pregunta   NUMBER(10) NOT NULL,
    orden         NUMBER(5)  NOT NULL,
    CONSTRAINT pk_formulario_preguntas PRIMARY KEY (id_formulario, id_pregunta),
    CONSTRAINT fk_fp_formulario
        FOREIGN KEY (id_formulario) REFERENCES Formularios(id_formulario),
    CONSTRAINT fk_fp_pregunta
        FOREIGN KEY (id_pregunta) REFERENCES Preguntas(id_pregunta),
    CONSTRAINT uk_fp_orden UNIQUE (id_formulario, orden),
    CONSTRAINT ck_fp_orden CHECK (orden > 0)
);

-- ------------------------------------------------------------------------
-- TABLA OPCIONES: Opciones de respuesta asociadas a preguntas.
-- ------------------------------------------------------------------------
CREATE TABLE Opciones (
    id_opcion               NUMBER(10)    CONSTRAINT pk_opciones PRIMARY KEY,
    texto_opcion            VARCHAR2(255) NOT NULL,
    orden                   NUMBER(5)     NOT NULL,
    id_pregunta             NUMBER(10)    NOT NULL,
    requiere_especificacion NUMBER(1) DEFAULT 0 NOT NULL,
    CONSTRAINT ck_opcion_especificacion CHECK (requiere_especificacion IN (0,1)),
    CONSTRAINT fk_opcion_pregunta
        FOREIGN KEY (id_pregunta) REFERENCES Preguntas(id_pregunta),
    CONSTRAINT uk_opcion_orden UNIQUE (id_pregunta, orden),
    CONSTRAINT ck_opcion_orden CHECK (orden > 0)
);

-- ================================================================
-- MONTOS
-- ================================================================

-- ------------------------------------------------------------------------
-- TABLA MONTO_INSCRIPCIONES: Importes asociados a inscripción.
-- ------------------------------------------------------------------------
CREATE TABLE Monto_Inscripciones (
    id_monto_inscripcion NUMBER(10) CONSTRAINT pk_monto_inscripciones PRIMARY KEY,
    monto_pago           NUMBER(10,2) NOT NULL,
    CONSTRAINT ck_monto_inscripciones CHECK (monto_pago >= 0)
);

-- ------------------------------------------------------------------------
-- TABLA MONTO_REINSCRIPCIONES: Importes asociados a reinscripción.
-- ------------------------------------------------------------------------
CREATE TABLE Monto_Reinscripciones (
    id_monto_reinscripcion NUMBER(10) CONSTRAINT pk_monto_reinscripciones PRIMARY KEY,
    monto_pago             NUMBER(10,2) NOT NULL,
    CONSTRAINT ck_monto_reinscripciones CHECK (monto_pago >= 0)
);

-- ================================================================
-- SOLICITUDES
-- ================================================================
-- ------------------------------------------------------------------------
-- TABLA SOLICITUDES: Solicitudes presentadas por estudiantes o aspirantes.
-- Cada solicitud pertenece exclusivamente a un estudiante o aspirante.
-- ------------------------------------------------------------------------
CREATE TABLE Solicitudes (
    id_solicitud         NUMBER(10) CONSTRAINT pk_solicitudes PRIMARY KEY,
    id_estatus_solicitud NUMBER(10) NOT NULL,
    id_convocatoria      NUMBER(10) NOT NULL,
    num_control          VARCHAR2(10),
    ficha                VARCHAR2(18),
    periodo              NUMBER(2),
    anio                 NUMBER(4),
    fecha_solicitud      DATE DEFAULT SYSDATE NOT NULL,
    fecha_actualizacion  DATE DEFAULT SYSDATE NOT NULL,
    id_tipo_solicitud    NUMBER(10) NOT NULL,
    id_formulario        NUMBER(10) NOT NULL,

    CONSTRAINT fk_solicitud_estatus
        FOREIGN KEY (id_estatus_solicitud)
        REFERENCES Estatus_Solicitudes(id_estatus_solicitud),

    CONSTRAINT fk_solicitud_convocatoria
        FOREIGN KEY (id_convocatoria)
        REFERENCES Convocatorias(id_convocatoria),

    CONSTRAINT fk_solicitud_estudiante
        FOREIGN KEY (num_control)
        REFERENCES Estudiantes(numero_control),

    CONSTRAINT fk_solicitud_aspirante
        FOREIGN KEY (ficha, periodo, anio)
        REFERENCES Aspirantes(ficha, periodo, anio),

    CONSTRAINT fk_solicitud_tipo
        FOREIGN KEY (id_tipo_solicitud)
        REFERENCES Tipo_Solicitudes(id_tipo_solicitud),

    CONSTRAINT fk_solicitud_formulario
        FOREIGN KEY (id_formulario)
        REFERENCES Formularios(id_formulario),

    -- Garantiza que la solicitud pertenezca a un estudiante o aspirante,
    -- pero nunca a ambos simultáneamente.
    CONSTRAINT ck_solicitud_origen CHECK (
        (num_control IS NOT NULL
         AND ficha IS NULL AND periodo IS NULL AND anio IS NULL)
        OR
        (num_control IS NULL
         AND ficha IS NOT NULL AND periodo IS NOT NULL AND anio IS NOT NULL)
    ),

    -- Limita el año del aspirante al rango permitido; es nulo para estudiantes.
    CONSTRAINT ck_solicitud_anio
        CHECK (anio IS NULL OR anio BETWEEN 2000 AND 2200)
);


-- ------------------------------------------------------------------------
-- ÍNDICE ÚNICO: SOLICITUDES DE ESTUDIANTES
-- Impide que un estudiante registre más de una solicitud en la misma
-- convocatoria, independientemente del tipo de solicitud.
-- Solo considera registros con número de control; excluye aspirantes.
-- ------------------------------------------------------------------------
CREATE UNIQUE INDEX uk_solicitud_estudiante_conv
ON Solicitudes (
    CASE WHEN num_control IS NOT NULL THEN num_control END,
    CASE WHEN num_control IS NOT NULL THEN id_convocatoria END
);


-- ------------------------------------------------------------------------
-- ÍNDICE ÚNICO: SOLICITUDES DE ASPIRANTES
-- Impide que un aspirante registre más de una solicitud en la misma
-- convocatoria, identificándolo mediante ficha, periodo y año.
-- Solo considera registros con ficha; excluye estudiantes.
-- ------------------------------------------------------------------------
CREATE UNIQUE INDEX uk_solicitud_aspirante_conv
ON Solicitudes (
    CASE WHEN ficha IS NOT NULL THEN ficha END,
    CASE WHEN ficha IS NOT NULL THEN periodo END,
    CASE WHEN ficha IS NOT NULL THEN anio END,
    CASE WHEN ficha IS NOT NULL THEN id_convocatoria END
);


-- ================================================================
-- RESPUESTAS
-- ================================================================

-- ------------------------------------------------------------------------
-- TABLA RESPUESTAS: Respuestas registradas para preguntas de solicitudes.
-- ------------------------------------------------------------------------
CREATE TABLE Respuestas (
    id_respuesta    NUMBER(10) CONSTRAINT pk_respuestas PRIMARY KEY,
    texto_respuesta VARCHAR2(4000),
    id_solicitud    NUMBER(10) NOT NULL,
    id_pregunta     NUMBER(10) NOT NULL,
    id_opcion       NUMBER(10),
    CONSTRAINT fk_respuesta_solicitud
        FOREIGN KEY (id_solicitud) REFERENCES Solicitudes(id_solicitud),
    CONSTRAINT fk_respuesta_pregunta
        FOREIGN KEY (id_pregunta) REFERENCES Preguntas(id_pregunta),
    CONSTRAINT fk_respuesta_opcion
        FOREIGN KEY (id_opcion) REFERENCES Opciones(id_opcion)
);

CREATE UNIQUE INDEX ux_respuesta_solicitud_pregunta
    ON Respuestas (id_solicitud, id_pregunta);

-- ================================================================
-- HISTORIAL DE SOLICITUDES
-- ================================================================

-- ------------------------------------------------------------------------
-- TABLA HISTORIAL_SOLICITUDES: Historial de cambios de estado de las solicitudes.
-- ------------------------------------------------------------------------
CREATE TABLE Historial_Solicitudes (
    id_historial       NUMBER(10) CONSTRAINT pk_historial_solicitudes PRIMARY KEY,
    comentario         VARCHAR2(500),
    fecha_registro     DATE DEFAULT SYSDATE NOT NULL,
    id_solicitud       NUMBER(10) NOT NULL,
    id_estatus         NUMBER(10) NOT NULL,
    id_administrador   VARCHAR2(10),
    id_tipo_solicitud  NUMBER(10) NOT NULL,
    CONSTRAINT fk_hs_solicitud
        FOREIGN KEY (id_solicitud) REFERENCES Solicitudes(id_solicitud),
    CONSTRAINT fk_hs_estatus
        FOREIGN KEY (id_estatus) REFERENCES Estatus_Solicitudes(id_estatus_solicitud),
    CONSTRAINT fk_hs_administrador
        FOREIGN KEY (id_administrador) REFERENCES Administradores(id_administrador),
    CONSTRAINT fk_hs_tipo_solicitud
        FOREIGN KEY (id_tipo_solicitud) REFERENCES Tipo_Solicitudes(id_tipo_solicitud),
    CONSTRAINT chk_historial_administrador CHECK (
        (id_estatus IN (1,6) AND id_administrador IS NULL)
        OR (id_estatus IN (2,3,4,5) AND id_administrador IS NOT NULL)
    )
);

-- ================================================================
-- DEDUCCIONES AUTORIZADAS
-- ================================================================

-- ------------------------------------------------------------------------
-- TABLA DEDUCCIONES_AUTORIZADAS: Deducciones autorizadas y su seguimiento.
-- ------------------------------------------------------------------------
CREATE TABLE Deducciones_autorizadas (
    id_deduccion_autorizada NUMBER(10) CONSTRAINT pk_deducciones_autorizadas PRIMARY KEY,
    estatus_deduccion       NUMBER(10) NOT NULL,
    monto_final             NUMBER(10,2) NOT NULL,
    fecha_registro          DATE DEFAULT SYSDATE NOT NULL,
    id_solicitud            NUMBER(10) NOT NULL,
    id_administrador        VARCHAR2(10),
    id_convocatoria         NUMBER(10) NOT NULL,
    id_tipo_solicitud       NUMBER(10) NOT NULL,
    fecha_actualizacion     DATE DEFAULT SYSDATE NOT NULL,
    id_monto_inscripcion    NUMBER(10),
    id_monto_reinscripcion  NUMBER(10),
    monto_pago              NUMBER(10,2) NOT NULL,
    monto_deduccion         NUMBER(10,2) NOT NULL,
    CONSTRAINT fk_da_estatus
        FOREIGN KEY (estatus_deduccion) REFERENCES Estatus_Deducciones(id_estatus_deducciones),
    CONSTRAINT fk_da_solicitud
        FOREIGN KEY (id_solicitud) REFERENCES Solicitudes(id_solicitud),
    CONSTRAINT fk_da_administrador
        FOREIGN KEY (id_administrador) REFERENCES Administradores(id_administrador),
    CONSTRAINT fk_da_cts
        FOREIGN KEY (id_convocatoria, id_tipo_solicitud)
        REFERENCES Convocatorias_TipoSolicitudes(id_convocatoria, id_tipo_solicitud),
    CONSTRAINT fk_da_monto_inscripcion
        FOREIGN KEY (id_monto_inscripcion) REFERENCES Monto_Inscripciones(id_monto_inscripcion),
    CONSTRAINT fk_da_monto_reinscripcion
        FOREIGN KEY (id_monto_reinscripcion) REFERENCES Monto_Reinscripciones(id_monto_reinscripcion),
    CONSTRAINT uk_da_solicitud UNIQUE (id_solicitud),
    CONSTRAINT ck_da_monto_origen CHECK (
        (id_monto_inscripcion IS NOT NULL AND id_monto_reinscripcion IS NULL)
        OR
        (id_monto_inscripcion IS NULL AND id_monto_reinscripcion IS NOT NULL)
    ),
    CONSTRAINT ck_da_montos CHECK (
        monto_pago >= 0
        AND monto_deduccion >= 0
        AND monto_deduccion <= monto_pago
    ),
    CONSTRAINT ck_da_monto_final CHECK (monto_final = monto_pago - monto_deduccion)
);

-- ------------------------------------------------------------------------
-- TABLA HISTORIAL_DEDUCCIONES: Historial de movimientos de las deducciones.
-- ------------------------------------------------------------------------
CREATE TABLE Historial_Deducciones (
    id_historial_deduccion  NUMBER(10) CONSTRAINT pk_historial_deducciones PRIMARY KEY,
    id_deduccion_autorizada NUMBER(10) NOT NULL,
    id_estatus              NUMBER(10) NOT NULL,
    id_administrador        VARCHAR2(10),
    id_tipo_solicitud       NUMBER(10) NOT NULL,
    monto_pago              NUMBER(10,2) NOT NULL,
    monto_deduccion         NUMBER(10,2) NOT NULL,
    monto_final             NUMBER(10,2) NOT NULL,
    fecha_registro          DATE DEFAULT SYSDATE NOT NULL,
    CONSTRAINT ck_hd_montos CHECK (monto_pago >= 0 AND monto_deduccion >= 0 AND monto_final = monto_pago - monto_deduccion),
    CONSTRAINT fk_hd_tipo FOREIGN KEY (id_tipo_solicitud) REFERENCES Tipo_Solicitudes(id_tipo_solicitud),
    CONSTRAINT fk_hd_deduccion
        FOREIGN KEY (id_deduccion_autorizada) REFERENCES Deducciones_autorizadas(id_deduccion_autorizada),
    CONSTRAINT fk_hd_estatus
        FOREIGN KEY (id_estatus) REFERENCES Estatus_Deducciones(id_estatus_deducciones),
    CONSTRAINT fk_hd_administrador
        FOREIGN KEY (id_administrador) REFERENCES Administradores(id_administrador),
    -- A diferencia de Historial_Solicitudes, aqui no existe ningun flujo de
    -- alumno ni estatus "automatico": autorizar/cancelar/aplicar deduccion
    -- son siempre acciones de administrador, asi que el campo nunca debe
    -- quedar NULL.
    CONSTRAINT chk_hd_administrador CHECK (id_administrador IS NOT NULL)
);

-- ================================================================
-- SECUENCIAS
-- ================================================================

-- Secuencia seq_tipo_solicitudes: inicia en 3 porque este script inserta los ids 1 y 2.
CREATE SEQUENCE seq_tipo_solicitudes START WITH 3 INCREMENT BY 1;
-- Secuencia seq_tipo_preguntas: proporciona identificadores automáticos para nuevos registros.
CREATE SEQUENCE seq_tipo_preguntas START WITH 4 INCREMENT BY 1;
-- Secuencia seq_estatus_solicitudes: proporciona identificadores automáticos para nuevos registros.
CREATE SEQUENCE seq_estatus_solicitudes START WITH 6 INCREMENT BY 1;
-- Secuencia seq_estatus_deducciones: proporciona identificadores automáticos para nuevos registros.
CREATE SEQUENCE seq_estatus_deducciones START WITH 6 INCREMENT BY 1;
-- Secuencia seq_convocatorias: proporciona identificadores automáticos para nuevos registros.
CREATE SEQUENCE seq_convocatorias START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE seq_historial_convocatorias START WITH 1 INCREMENT BY 1;
-- Secuencia seq_formularios: inicia en 3 porque este script inserta los ids 1 y 2.
CREATE SEQUENCE seq_formularios START WITH 3 INCREMENT BY 1;
-- Secuencia seq_preguntas: proporciona identificadores automáticos para nuevos registros.
CREATE SEQUENCE seq_preguntas START WITH 1 INCREMENT BY 1;
-- Secuencia seq_opciones: proporciona identificadores automáticos para nuevos registros.
CREATE SEQUENCE seq_opciones START WITH 1 INCREMENT BY 1;
-- Secuencia seq_monto_inscripciones: proporciona identificadores automáticos para nuevos registros.
CREATE SEQUENCE seq_monto_inscripciones START WITH 1 INCREMENT BY 1;
-- Secuencia seq_monto_reinscripciones: proporciona identificadores automáticos para nuevos registros.
CREATE SEQUENCE seq_monto_reinscripciones START WITH 1 INCREMENT BY 1;
-- Secuencia seq_solicitudes: proporciona identificadores automáticos para nuevos registros.
CREATE SEQUENCE seq_solicitudes START WITH 1 INCREMENT BY 1;
-- Secuencia seq_respuestas: proporciona identificadores automáticos para nuevos registros.
CREATE SEQUENCE seq_respuestas START WITH 1 INCREMENT BY 1;
-- Secuencia seq_historial_solicitudes: proporciona identificadores automáticos para nuevos registros.
CREATE SEQUENCE seq_historial_solicitudes START WITH 1 INCREMENT BY 1;
-- Secuencia seq_deducciones_autorizadas: proporciona identificadores automáticos para nuevos registros.
CREATE SEQUENCE seq_deducciones_autorizadas START WITH 1 INCREMENT BY 1;
-- Secuencia seq_historial_deducciones: proporciona identificadores automáticos para nuevos registros.
CREATE SEQUENCE seq_historial_deducciones START WITH 1 INCREMENT BY 1;

-- ================================================================
-- TRIGGERS DE IDs
-- ================================================================

-- ------------------------------------------------------------------------
-- TRIGGER trg_tipo_solicitudes_bi: Asigna el siguiente identificador de su secuencia cuando no se proporciona uno.
-- ------------------------------------------------------------------------
CREATE OR REPLACE TRIGGER trg_tipo_solicitudes_bi
BEFORE INSERT ON Tipo_Solicitudes
FOR EACH ROW
WHEN (NEW.id_tipo_solicitud IS NULL)
BEGIN
    SELECT seq_tipo_solicitudes.NEXTVAL INTO :NEW.id_tipo_solicitud FROM dual;
END;
/

-- ------------------------------------------------------------------------
-- TRIGGER trg_tipo_preguntas_bi: Asigna el siguiente identificador de su secuencia cuando no se proporciona uno.
-- ------------------------------------------------------------------------
CREATE OR REPLACE TRIGGER trg_tipo_preguntas_bi
BEFORE INSERT ON Tipo_Preguntas
FOR EACH ROW
WHEN (NEW.id_tipo_pregunta IS NULL)
BEGIN
    SELECT seq_tipo_preguntas.NEXTVAL INTO :NEW.id_tipo_pregunta FROM dual;
END;
/

-- ------------------------------------------------------------------------
-- TRIGGER trg_estatus_solicitudes_bi: Asigna el siguiente identificador de su secuencia cuando no se proporciona uno.
-- ------------------------------------------------------------------------
CREATE OR REPLACE TRIGGER trg_estatus_solicitudes_bi
BEFORE INSERT ON Estatus_Solicitudes
FOR EACH ROW
WHEN (NEW.id_estatus_solicitud IS NULL)
BEGIN
    SELECT seq_estatus_solicitudes.NEXTVAL INTO :NEW.id_estatus_solicitud FROM dual;
END;
/

-- ------------------------------------------------------------------------
-- TRIGGER trg_estatus_deducciones_bi: Asigna el siguiente identificador de su secuencia cuando no se proporciona uno.
-- ------------------------------------------------------------------------
CREATE OR REPLACE TRIGGER trg_estatus_deducciones_bi
BEFORE INSERT ON Estatus_Deducciones
FOR EACH ROW
WHEN (NEW.id_estatus_deducciones IS NULL)
BEGIN
    SELECT seq_estatus_deducciones.NEXTVAL INTO :NEW.id_estatus_deducciones FROM dual;
END;
/

-- ------------------------------------------------------------------------
-- TRIGGER trg_convocatorias_bi: Asigna el siguiente identificador de su secuencia cuando no se proporciona uno.
-- ------------------------------------------------------------------------
CREATE OR REPLACE TRIGGER trg_convocatorias_bi
BEFORE INSERT ON Convocatorias
FOR EACH ROW
WHEN (NEW.id_convocatoria IS NULL)
BEGIN
    SELECT seq_convocatorias.NEXTVAL INTO :NEW.id_convocatoria FROM dual;
END;
/

-- ------------------------------------------------------------------------
-- TRIGGER trg_hist_convocatorias_bi: identificador del historial de fechas.
CREATE OR REPLACE TRIGGER trg_hist_convocatorias_bi
BEFORE INSERT ON Historial_Convocatorias FOR EACH ROW
BEGIN
    IF :NEW.id_historial_convocatoria IS NULL THEN
        :NEW.id_historial_convocatoria := seq_historial_convocatorias.NEXTVAL;
    END IF;
END;
/

-- TRIGGER trg_formularios_bi: Asigna el siguiente identificador de su secuencia cuando no se proporciona uno.
-- ------------------------------------------------------------------------
CREATE OR REPLACE TRIGGER trg_formularios_bi
BEFORE INSERT ON Formularios
FOR EACH ROW
WHEN (NEW.id_formulario IS NULL)
BEGIN
    SELECT seq_formularios.NEXTVAL INTO :NEW.id_formulario FROM dual;
END;
/

-- ------------------------------------------------------------------------
-- TRIGGER trg_preguntas_bi: Asigna el siguiente identificador de su secuencia cuando no se proporciona uno.
-- ------------------------------------------------------------------------
CREATE OR REPLACE TRIGGER trg_preguntas_bi
BEFORE INSERT ON Preguntas
FOR EACH ROW
WHEN (NEW.id_pregunta IS NULL)
BEGIN
    SELECT seq_preguntas.NEXTVAL INTO :NEW.id_pregunta FROM dual;
END;
/

-- ------------------------------------------------------------------------
-- TRIGGER trg_opciones_bi: Asigna el siguiente identificador de su secuencia cuando no se proporciona uno.
-- ------------------------------------------------------------------------
CREATE OR REPLACE TRIGGER trg_opciones_bi
BEFORE INSERT ON Opciones
FOR EACH ROW
WHEN (NEW.id_opcion IS NULL)
BEGIN
    SELECT seq_opciones.NEXTVAL INTO :NEW.id_opcion FROM dual;
END;
/

-- ------------------------------------------------------------------------
-- TRIGGER trg_monto_inscripciones_bi: Asigna el siguiente identificador de su secuencia cuando no se proporciona uno.
-- ------------------------------------------------------------------------
CREATE OR REPLACE TRIGGER trg_monto_inscripciones_bi
BEFORE INSERT ON Monto_Inscripciones
FOR EACH ROW
WHEN (NEW.id_monto_inscripcion IS NULL)
BEGIN
    SELECT seq_monto_inscripciones.NEXTVAL INTO :NEW.id_monto_inscripcion FROM dual;
END;
/

-- ------------------------------------------------------------------------
-- TRIGGER trg_monto_reinscripcion_bi: Asigna el siguiente identificador de su secuencia cuando no se proporciona uno.
-- ------------------------------------------------------------------------
CREATE OR REPLACE TRIGGER trg_monto_reinscripcion_bi
BEFORE INSERT ON Monto_Reinscripciones
FOR EACH ROW
WHEN (NEW.id_monto_reinscripcion IS NULL)
BEGIN
    SELECT seq_monto_reinscripciones.NEXTVAL INTO :NEW.id_monto_reinscripcion FROM dual;
END;
/

-- ------------------------------------------------------------------------
-- TRIGGER trg_solicitudes_bi: Asigna el siguiente identificador de su secuencia cuando no se proporciona uno.
-- ------------------------------------------------------------------------
CREATE OR REPLACE TRIGGER trg_solicitudes_bi
BEFORE INSERT ON Solicitudes
FOR EACH ROW
WHEN (NEW.id_solicitud IS NULL)
BEGIN
    SELECT seq_solicitudes.NEXTVAL INTO :NEW.id_solicitud FROM dual;
END;
/

-- ------------------------------------------------------------------------
-- TRIGGER trg_respuestas_bi: Asigna el siguiente identificador de su secuencia cuando no se proporciona uno.
-- ------------------------------------------------------------------------
CREATE OR REPLACE TRIGGER trg_respuestas_bi
BEFORE INSERT ON Respuestas
FOR EACH ROW
WHEN (NEW.id_respuesta IS NULL)
BEGIN
    SELECT seq_respuestas.NEXTVAL INTO :NEW.id_respuesta FROM dual;
END;
/

-- ------------------------------------------------------------------------
-- TRIGGER trg_hist_solicitudes_bi: Asigna el siguiente identificador de su secuencia cuando no se proporciona uno.
-- ------------------------------------------------------------------------
CREATE OR REPLACE TRIGGER trg_hist_solicitudes_bi
BEFORE INSERT ON Historial_Solicitudes
FOR EACH ROW
WHEN (NEW.id_historial IS NULL)
BEGIN
    SELECT seq_historial_solicitudes.NEXTVAL INTO :NEW.id_historial FROM dual;
END;
/

-- ------------------------------------------------------------------------
-- TRIGGER trg_deducciones_bi: Asigna el siguiente identificador de su secuencia cuando no se proporciona uno.
-- ------------------------------------------------------------------------
CREATE OR REPLACE TRIGGER trg_deducciones_bi
BEFORE INSERT ON Deducciones_autorizadas
FOR EACH ROW
WHEN (NEW.id_deduccion_autorizada IS NULL)
BEGIN
    SELECT seq_deducciones_autorizadas.NEXTVAL INTO :NEW.id_deduccion_autorizada FROM dual;
END;
/

-- ------------------------------------------------------------------------
-- TRIGGER trg_hist_deducciones_bi: Asigna el siguiente identificador de su secuencia cuando no se proporciona uno.
-- ------------------------------------------------------------------------
CREATE OR REPLACE TRIGGER trg_hist_deducciones_bi
BEFORE INSERT ON Historial_Deducciones
FOR EACH ROW
WHEN (NEW.id_historial_deduccion IS NULL)
BEGIN
    SELECT seq_historial_deducciones.NEXTVAL INTO :NEW.id_historial_deduccion FROM dual;
END;
/

-- ================================================================
-- INICIO - REGLAS DE NEGOCIO VALIDADAS POR EL ESQUEMA
-- Los triggers siguientes impiden estados, importes y fechas inválidos aunque
-- el DML provenga del propietario del esquema: inmutabilidad de solicitudes y
-- de deducciones aplicadas, transiciones de estado permitidas, correspondencia
-- entre formulario, tipo y convocatoria, y convocatorias sin traslape.
-- No sustituyen la autenticación ni la autorización del backend.
-- ================================================================

-- Impide cambiar el propietario, la convocatoria y la fecha de origen de una
-- solicitud; exige que el formulario corresponda al tipo de solicitud, que el
-- periodo del aspirante coincida con el de la convocatoria y que las
-- transiciones de estado sigan la secuencia permitida.
CREATE OR REPLACE TRIGGER trg_solicitudes_validacion
BEFORE INSERT OR UPDATE ON Solicitudes FOR EACH ROW
DECLARE
    v_tipo NUMBER;
    v_periodo NUMBER;
    v_anio NUMBER;
BEGIN
    SELECT id_tipo_solicitud INTO v_tipo FROM Formularios
     WHERE id_formulario = :NEW.id_formulario;
    IF v_tipo <> :NEW.id_tipo_solicitud THEN
        RAISE_APPLICATION_ERROR(-20001,'El formulario no corresponde al tipo de solicitud');
    END IF;
    SELECT id_periodo, anio INTO v_periodo, v_anio FROM Convocatorias
     WHERE id_convocatoria = :NEW.id_convocatoria;
    IF :NEW.ficha IS NOT NULL AND (:NEW.periodo <> v_periodo OR :NEW.anio <> v_anio) THEN
        RAISE_APPLICATION_ERROR(-20003,'Periodo y anio del aspirante no coinciden con la convocatoria');
    END IF;
    IF UPDATING THEN
        IF :OLD.id_estatus_solicitud = 5 THEN
            RAISE_APPLICATION_ERROR(-20004,'Una solicitud APLICADO es inmutable');
        END IF;
        IF :OLD.id_convocatoria <> :NEW.id_convocatoria
           OR NVL(:OLD.num_control,'#') <> NVL(:NEW.num_control,'#')
           OR NVL(:OLD.ficha,'#') <> NVL(:NEW.ficha,'#')
           OR NVL(:OLD.periodo,-1) <> NVL(:NEW.periodo,-1)
           OR NVL(:OLD.anio,-1) <> NVL(:NEW.anio,-1)
           OR :OLD.fecha_solicitud <> :NEW.fecha_solicitud THEN
            RAISE_APPLICATION_ERROR(-20005,'No se permite cambiar propietario, convocatoria ni fecha de origen');
        END IF;
        IF :NEW.id_estatus_solicitud <> :OLD.id_estatus_solicitud THEN
            IF NOT ((:OLD.id_estatus_solicitud=1 AND :NEW.id_estatus_solicitud IN (2,3,6))
                OR (:OLD.id_estatus_solicitud IN (3,4,6) AND :NEW.id_estatus_solicitud=1)
                OR (:OLD.id_estatus_solicitud=2 AND :NEW.id_estatus_solicitud IN (4,5))
                OR (:OLD.id_estatus_solicitud=4 AND :NEW.id_estatus_solicitud=2)) THEN
                RAISE_APPLICATION_ERROR(-20006,'Transicion de solicitud no permitida');
            END IF;
        END IF;
        IF :NEW.id_tipo_solicitud <> :OLD.id_tipo_solicitud
           OR :NEW.id_formulario <> :OLD.id_formulario THEN
            IF :OLD.id_estatus_solicitud NOT IN (3,4,6)
               OR :NEW.id_estatus_solicitud <> 1 THEN
                RAISE_APPLICATION_ERROR(-20007,'El tipo/formulario solo cambia al reenviar');
            END IF;
        END IF;
    END IF;
    :NEW.fecha_actualizacion := SYSDATE;
END;
/

-- Una respuesta debe corresponder al formulario y al tipo de pregunta.
-- Las preguntas opcionales se omiten sin insertar fila.
CREATE OR REPLACE TRIGGER trg_respuestas_validacion
BEFORE INSERT OR UPDATE ON Respuestas FOR EACH ROW
DECLARE
    v_tipo NUMBER;
    v_formulario NUMBER;
    v_count NUMBER;
    v_opcion_pregunta NUMBER;
    v_especificacion NUMBER;
BEGIN
    SELECT tipo_pregunta INTO v_tipo FROM Preguntas WHERE id_pregunta=:NEW.id_pregunta;
    SELECT id_formulario INTO v_formulario FROM Solicitudes WHERE id_solicitud=:NEW.id_solicitud;
    SELECT COUNT(*) INTO v_count FROM Formulario_Preguntas
      WHERE id_formulario=v_formulario AND id_pregunta=:NEW.id_pregunta;
    IF v_count=0 THEN RAISE_APPLICATION_ERROR(-20009,'Pregunta ajena al formulario'); END IF;
    IF v_tipo=1 THEN
        IF TRIM(:NEW.texto_respuesta) IS NULL OR :NEW.id_opcion IS NOT NULL THEN
            RAISE_APPLICATION_ERROR(-20010,'ABIERTA requiere texto y no admite opcion');
        END IF;
    ELSIF v_tipo IN (2,3) THEN
        IF :NEW.id_opcion IS NULL THEN
            RAISE_APPLICATION_ERROR(-20011,'Debe seleccionarse una opcion');
        END IF;
        SELECT id_pregunta, requiere_especificacion
          INTO v_opcion_pregunta, v_especificacion
          FROM Opciones WHERE id_opcion=:NEW.id_opcion;
        IF v_opcion_pregunta <> :NEW.id_pregunta THEN
            RAISE_APPLICATION_ERROR(-20012,'Opcion ajena a la pregunta');
        END IF;
        IF v_tipo=2 THEN
            IF v_especificacion=1 OR :NEW.texto_respuesta IS NOT NULL THEN
                RAISE_APPLICATION_ERROR(-20014,'OPCION_UNICA no admite especificacion');
            END IF;
        ELSIF v_especificacion=1 THEN
            IF TRIM(:NEW.texto_respuesta) IS NULL THEN
                RAISE_APPLICATION_ERROR(-20017,'Esta opcion requiere especificacion');
            END IF;
        ELSIF :NEW.texto_respuesta IS NOT NULL THEN
            RAISE_APPLICATION_ERROR(-20018,'La opcion seleccionada no admite texto adicional');
        END IF;
    ELSE
        RAISE_APPLICATION_ERROR(-20016,'Tipo de pregunta no valido');
    END IF;
END;
/

-- Evita modificar importes de deducciones aplicadas o sus identidades.
-- El cálculo y registro histórico se realizan en PKG_ADMINISTRADOR.
CREATE OR REPLACE TRIGGER trg_deducciones_proteccion
BEFORE INSERT OR UPDATE ON Deducciones_autorizadas FOR EACH ROW
BEGIN
    IF INSERTING THEN
        IF :NEW.estatus_deduccion<>2 THEN
            RAISE_APPLICATION_ERROR(-20023,'Una deduccion nueva debe iniciar AUTORIZADO');
        END IF;
    ELSE
        IF :OLD.estatus_deduccion=5 THEN
            RAISE_APPLICATION_ERROR(-20025,'Deduccion APLICADO inmutable');
        END IF;
        IF :OLD.id_solicitud<>:NEW.id_solicitud OR
           :OLD.id_convocatoria<>:NEW.id_convocatoria OR
           :OLD.fecha_registro<>:NEW.fecha_registro THEN
            RAISE_APPLICATION_ERROR(-20024,'Origen y fecha de registro de deduccion inmutables');
        END IF;
        IF :NEW.estatus_deduccion<>:OLD.estatus_deduccion AND NOT
           ((:OLD.estatus_deduccion=2 AND :NEW.estatus_deduccion IN (4,5)) OR
            (:OLD.estatus_deduccion=4 AND :NEW.estatus_deduccion=2)) THEN
            RAISE_APPLICATION_ERROR(-20026,'Transicion de deduccion no permitida');
        END IF;
        IF (:NEW.id_tipo_solicitud<>:OLD.id_tipo_solicitud OR
            :NEW.monto_pago<>:OLD.monto_pago OR
            :NEW.monto_deduccion<>:OLD.monto_deduccion OR
            :NEW.monto_final<>:OLD.monto_final) AND
           :NEW.estatus_deduccion NOT IN (2,5) THEN
            RAISE_APPLICATION_ERROR(-20027,'Importes solo modificables al autorizar o aplicar');
        END IF;
    END IF;
    :NEW.fecha_actualizacion := SYSDATE;
END;
/

-- Los tres historiales solo admiten INSERT: cualquier UPDATE o DELETE aborta
-- la sentencia, de modo que cada movimiento queda como registro permanente.
CREATE OR REPLACE TRIGGER trg_hist_solicitudes_inmutable
BEFORE UPDATE OR DELETE ON Historial_Solicitudes
BEGIN
    RAISE_APPLICATION_ERROR(-20070,'Historial de solicitudes inmutable');
END;
/
CREATE OR REPLACE TRIGGER trg_hist_deducciones_inmutable
BEFORE UPDATE OR DELETE ON Historial_Deducciones
BEGIN
    RAISE_APPLICATION_ERROR(-20071,'Historial de deducciones inmutable');
END;
/
CREATE OR REPLACE TRIGGER trg_hist_convocatorias_inmutable
BEFORE UPDATE OR DELETE ON Historial_Convocatorias
BEGIN
    RAISE_APPLICATION_ERROR(-20072,'Historial de convocatorias inmutable');
END;
/

-- Protege la modificación directa de montos oficiales después del plazo.
CREATE OR REPLACE TRIGGER trg_cts_plazo
BEFORE INSERT OR UPDATE OR DELETE ON Convocatorias_TipoSolicitudes
FOR EACH ROW
DECLARE
    v_limite DATE;
    v_convocatoria NUMBER;
BEGIN
    IF DELETING THEN
        v_convocatoria := :OLD.id_convocatoria;
    ELSE
        v_convocatoria := :NEW.id_convocatoria;
    END IF;
    SELECT fecha_limite_autorizacion INTO v_limite FROM Convocatorias
    WHERE id_convocatoria=v_convocatoria;
    IF TRUNC(SYSDATE)>TRUNC(v_limite) THEN
        RAISE_APPLICATION_ERROR(-20073,'Plazo de configuracion de deducciones cerrado');
    END IF;
END;
/

-- Serializa los cambios de convocatorias tomando bloqueo sobre PERIODOS
-- (fila fija) y rechaza cualquier intervalo que se superponga con otro.
-- PKG_ADMINISTRADOR valida además que las fechas nuevas no excluyan
-- solicitudes ni movimientos ya registrados.
CREATE OR REPLACE TRIGGER trg_convocatorias_intervalo
FOR INSERT OR UPDATE OF fecha_inicio_solicitudes,fecha_fin_solicitudes,
    fecha_limite_autorizacion,fecha_limite_aplicacion ON Convocatorias
COMPOUND TRIGGER
    TYPE t_ids IS TABLE OF Convocatorias.id_convocatoria%TYPE INDEX BY PLS_INTEGER;
    v_ids t_ids;
    v_n PLS_INTEGER := 0;
    BEFORE STATEMENT IS
        v_dummy NUMBER;
    BEGIN
        -- Bloqueo global de configuración de periodos; previene inserciones
        -- concurrentes que superpongan intervalos.
        SELECT id_periodo INTO v_dummy FROM Periodos WHERE id_periodo=1 FOR UPDATE;
    END BEFORE STATEMENT;
    BEFORE EACH ROW IS
    BEGIN
        IF UPDATING AND TRUNC(SYSDATE)>TRUNC(:OLD.fecha_limite_aplicacion) THEN
            RAISE_APPLICATION_ERROR(-20074,'Convocatoria cerrada definitivamente');
        END IF;
    END BEFORE EACH ROW;
    AFTER EACH ROW IS
    BEGIN
        v_n:=v_n+1;
        v_ids(v_n):=:NEW.id_convocatoria;
    END AFTER EACH ROW;
    AFTER STATEMENT IS
        v_count NUMBER;
    BEGIN
        FOR i IN 1..v_n LOOP
            SELECT COUNT(*) INTO v_count
            FROM Convocatorias a JOIN Convocatorias b
              ON a.id_convocatoria<>b.id_convocatoria
             AND TRUNC(a.fecha_inicio_solicitudes)<=TRUNC(b.fecha_limite_aplicacion)
             AND TRUNC(b.fecha_inicio_solicitudes)<=TRUNC(a.fecha_limite_aplicacion)
            WHERE a.id_convocatoria=v_ids(i);
            IF v_count>0 THEN
                RAISE_APPLICATION_ERROR(-20075,'Convocatorias con intervalos superpuestos');
            END IF;
        END LOOP;
    END AFTER STATEMENT;
END trg_convocatorias_intervalo;
/

-- ================================================================
-- FIN - REGLAS DE NEGOCIO VALIDADAS POR EL ESQUEMA
-- ================================================================


-- ================================================================
-- DATOS INICIALES DE CATÁLOGOS
-- ================================================================

INSERT INTO Periodos (id_periodo, descripcion) VALUES (1, 'Agosto-Diciembre');
INSERT INTO Periodos (id_periodo, descripcion) VALUES (2, 'Enero-Junio');

INSERT INTO Estatus_Solicitudes (id_estatus_solicitud, nombre, descripcion) VALUES
(1,'PENDIENTE','Solicitud enviada por el estudiante o aspirante, en espera de revision por un administrador');
INSERT INTO Estatus_Solicitudes (id_estatus_solicitud, nombre, descripcion) VALUES
(2,'AUTORIZADO','Solicitud revisada y aprobada por un administrador, con una deduccion autorizada asociada');
INSERT INTO Estatus_Solicitudes (id_estatus_solicitud, nombre, descripcion) VALUES
(3,'RECHAZADO','Solicitud rechazada por un administrador; puede corregirse y reenviarse durante la recepcion de solicitudes');
INSERT INTO Estatus_Solicitudes (id_estatus_solicitud, nombre, descripcion) VALUES
(4,'CANCELADO','Solicitud cuya deduccion fue cancelada; puede reenviarse en plazo o reautorizarse administrativamente');
INSERT INTO Estatus_Solicitudes (id_estatus_solicitud, nombre, descripcion) VALUES
(5,'APLICADO','Solicitud cuya deduccion se aplico al importe correspondiente; estado definitivo');
INSERT INTO Estatus_Solicitudes (id_estatus_solicitud, nombre, descripcion) VALUES
(6,'RETIRADO','Solicitud retirada por el solicitante dentro del periodo permitido para corregirla y reenviarla');

INSERT INTO Estatus_Deducciones (id_estatus_deducciones, nombre, descripcion) VALUES
(2,'AUTORIZADO','Deduccion aprobada por un administrador, pendiente de aplicacion al importe correspondiente');
INSERT INTO Estatus_Deducciones (id_estatus_deducciones, nombre, descripcion) VALUES
(4,'CANCELADO','Deduccion previamente autorizada y cancelada por un administrador; puede reautorizarse dentro del plazo');
INSERT INTO Estatus_Deducciones (id_estatus_deducciones, nombre, descripcion) VALUES
(5,'APLICADO','Deduccion aplicada utilizando los importes oficiales vigentes en ese momento; no admite modificaciones posteriores');

INSERT INTO Tipo_Preguntas (id_tipo_pregunta, nombre, descripcion) VALUES
(1,'ABIERTA','Permite responder con texto libre, sin seleccionar opciones predefinidas');
INSERT INTO Tipo_Preguntas (id_tipo_pregunta, nombre, descripcion) VALUES
(2,'OPCION_UNICA','Permite seleccionar una sola respuesta entre las opciones previamente definidas');
INSERT INTO Tipo_Preguntas (id_tipo_pregunta, nombre, descripcion) VALUES
(3,'MIXTA','Permite seleccionar una opcion predefinida y escribir texto solo si esa opcion requiere especificacion');
COMMIT;

INSERT INTO Tipo_Solicitudes (id_tipo_solicitud,nombre,descripcion)
VALUES (1,'Hijo de trabajador','Deducción para hijos de personal interno');
INSERT INTO Tipo_Solicitudes (id_tipo_solicitud,nombre,descripcion)
VALUES (2,'Hijo de homologado','Deducción para hijos de personal homologado');

INSERT INTO Formularios (id_formulario,nombre,descripcion,instrucciones,id_tipo_solicitud)
VALUES (1,'Hijos de trabajadores',
        'Formulario de hijos de personal interno',
        'Responda todas las preguntas obligatorias. Revise la convocatoria y presente en Servicios Escolares los documentos que esta indique. Requisitos:',1);
INSERT INTO Formularios (id_formulario,nombre,descripcion,instrucciones,id_tipo_solicitud)
VALUES (2,'Hijos de homologados',
        'Formulario de hijos de personal homologado',
        'Responda todas las preguntas obligatorias. Revise la convocatoria y presente en Servicios Escolares los documentos que esta indique. Requisitos:',2);


-- ================================================================
-- FIN DEL SCRIPT
-- ================================================================

-- Verificación informativa del inventario creado.
PROMPT Inventario de objetos del esquema:
SELECT OBJECT_TYPE, COUNT(*) AS CANTIDAD
FROM USER_OBJECTS
WHERE OBJECT_TYPE IN ('TABLE','INDEX','SEQUENCE','TRIGGER')
GROUP BY OBJECT_TYPE
ORDER BY OBJECT_TYPE;

-- Los triggers pueden existir pero estar inválidos por errores de compilación.
PROMPT Objetos invalidos (debe devolver cero filas):
SELECT OBJECT_NAME, OBJECT_TYPE, STATUS
FROM USER_OBJECTS
WHERE STATUS <> 'VALID';

PROMPT Fin de 02-crear-estructura.sql. Revisar el resultado antes de continuar.
EXIT SUCCESS
