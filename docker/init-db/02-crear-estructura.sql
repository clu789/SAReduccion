-- ============================================================================
-- 02-crear-estructura.sql
-- Proyecto: SAReduccion - Oracle XE 21c / esquema SA_REDUCCION
-- Define la estructura y las reglas de integridad del sistema.
--
-- OBJETIVO
--   Crear 21 tablas, 15 secuencias, triggers, índices y catálogos base.
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
    IF v_usuario <> 'SA_REDUCCION' THEN
        RAISE_APPLICATION_ERROR(-20011,
            'Conectarse como SA_REDUCCION; sesion actual: ' || v_usuario);
    END IF;
    IF v_contenedor <> 'XEPDB1' THEN
        RAISE_APPLICATION_ERROR(-20012,
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
    idPeriodo        NUMBER(2)     CONSTRAINT pk_periodos PRIMARY KEY,
    Descripcion      VARCHAR2(50)  NOT NULL,
    CONSTRAINT uk_periodos_descripcion UNIQUE (Descripcion)
);

-- ------------------------------------------------------------------------
-- TABLA TIPO_SOLICITUDES: Clasificación de los tipos de solicitud disponibles.
-- ------------------------------------------------------------------------
CREATE TABLE Tipo_Solicitudes (
    id_Tipo_Solicitud NUMBER(10)    CONSTRAINT pk_tipo_solicitudes PRIMARY KEY,
    Nombre            VARCHAR2(50)  NOT NULL,
    Descripcion       VARCHAR2(200)
);

-- ------------------------------------------------------------------------
-- TABLA TIPO_PREGUNTAS: Catálogo de modalidades de preguntas para formularios.
-- ------------------------------------------------------------------------
CREATE TABLE Tipo_Preguntas (
    id_Tipo_Pregunta NUMBER(10)    CONSTRAINT pk_tipo_preguntas PRIMARY KEY,
    Nombre           VARCHAR2(50)  NOT NULL,
    CONSTRAINT uk_tipo_preguntas_nombre UNIQUE (Nombre)
);

-- ------------------------------------------------------------------------
-- TABLA ESTATUS_SOLICITUDES: Estados posibles de una solicitud.
-- ------------------------------------------------------------------------
CREATE TABLE Estatus_Solicitudes (
    id_estatus_solicitud NUMBER(10)    CONSTRAINT pk_estatus_solicitudes PRIMARY KEY,
    Nombre               VARCHAR2(100) NOT NULL,
    Descripcion          VARCHAR2(200),
    CONSTRAINT uk_estatus_solicitudes_nombre UNIQUE (Nombre)
);

-- ------------------------------------------------------------------------
-- TABLA ESTATUS_DEDUCCIONES: Estados posibles de una deducción autorizada.
-- ------------------------------------------------------------------------
CREATE TABLE Estatus_Deducciones (
    id_Estatus_Deducciones NUMBER(10)   CONSTRAINT pk_estatus_deducciones PRIMARY KEY,
    Nombre                 VARCHAR2(50) NOT NULL,
    Descripcion            VARCHAR2(100),
    CONSTRAINT uk_estatus_deducciones_nombre UNIQUE (Nombre)
);

-- ================================================================
-- PERSONAS / PERFILES
-- ================================================================

-- ------------------------------------------------------------------------
-- TABLA ESTUDIANTES: Datos de estudiantes identificados por número de control.
-- ------------------------------------------------------------------------
CREATE TABLE Estudiantes (
    Numero_Control   VARCHAR2(10)  CONSTRAINT pk_estudiantes PRIMARY KEY,
    Nombre           VARCHAR2(50)  NOT NULL,
    PrimerAppellido  VARCHAR2(50)  NOT NULL,
    SegundoApellido  VARCHAR2(50),
    Carrera          VARCHAR2(100),
    Semestre         NUMBER(2),
    CONSTRAINT ck_estudiantes_semestre CHECK (Semestre IS NULL OR Semestre BETWEEN 1 AND 20)
);

-- ------------------------------------------------------------------------
-- TABLA ASPIRANTES: Datos de aspirantes identificados por ficha, periodo y año.
-- ------------------------------------------------------------------------
CREATE TABLE Aspirantes (
    ficha           VARCHAR2(18)  NOT NULL,
    periodo         NUMBER(2)     NOT NULL,
    anio            NUMBER(4)     NOT NULL,
    Nombre          VARCHAR2(50)  NOT NULL,
    PrimerApellido  VARCHAR2(50)  NOT NULL,
    SegundoApellido VARCHAR2(50),
    Semestre        NUMBER(2),
    Curp            VARCHAR2(18)  NOT NULL,
    correo          VARCHAR2(254) NOT NULL,
    CONSTRAINT pk_aspirantes PRIMARY KEY (ficha, periodo, anio),
    CONSTRAINT fk_aspirante_periodo
        FOREIGN KEY (periodo) REFERENCES Periodos(idPeriodo),
    CONSTRAINT ck_aspirantes_anio CHECK (anio BETWEEN 2000 AND 2200),
    CONSTRAINT ck_aspirantes_curp CHECK (LENGTH(Curp) = 18),
    CONSTRAINT ck_aspirantes_semestre CHECK (Semestre IS NULL OR Semestre BETWEEN 1 AND 20),
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
    idAdministrador VARCHAR2(10) CONSTRAINT pk_administradores PRIMARY KEY,
    Nombre          VARCHAR2(50) NOT NULL,
    PrimerApellido  VARCHAR2(50) NOT NULL,
    SegundoApellido VARCHAR2(50)
);

-- ================================================================
-- CONVOCATORIAS
-- ================================================================

-- ------------------------------------------------------------------------
-- TABLA CONVOCATORIAS: Periodos de apertura y cierre de solicitudes.
-- ------------------------------------------------------------------------
CREATE TABLE Convocatorias (
    id_convocatoria NUMBER(10) CONSTRAINT pk_convocatorias PRIMARY KEY,
    fecha_inicio    DATE       NOT NULL,
    fecha_fin       DATE       NOT NULL,
    id_periodo      NUMBER(2)  NOT NULL,
    anio            NUMBER(4)  NOT NULL,
    CONSTRAINT fk_convocatoria_periodo
        FOREIGN KEY (id_periodo) REFERENCES Periodos(idPeriodo),
    CONSTRAINT ck_convocatoria_fechas CHECK (fecha_fin >= fecha_inicio),
    CONSTRAINT ck_convocatoria_anio CHECK (anio BETWEEN 2000 AND 2200),
    CONSTRAINT uk_convocatoria_periodo_anio UNIQUE (id_periodo, anio)
);

-- ------------------------------------------------------------------------
-- TABLA CONVOCATORIAS_TIPOSOLICITUDES: Relación entre convocatorias y tipos de solicitudes.
-- ------------------------------------------------------------------------
CREATE TABLE Convocatorias_TipoSolicitudes (
    id_convocatoria         NUMBER(10)   NOT NULL,
    id_Tipo_Solicitud       NUMBER(10)   NOT NULL,
    monto_deduccion         NUMBER(10,2) NOT NULL,
    fecha_asignacion_montos DATE DEFAULT SYSDATE NOT NULL,
    CONSTRAINT pk_conv_tipo_sol PRIMARY KEY (id_convocatoria, id_Tipo_Solicitud),
    CONSTRAINT fk_cts_convocatoria
        FOREIGN KEY (id_convocatoria) REFERENCES Convocatorias(id_convocatoria),
    CONSTRAINT fk_cts_tipo_solicitud
        FOREIGN KEY (id_Tipo_Solicitud) REFERENCES Tipo_Solicitudes(id_Tipo_Solicitud),
    CONSTRAINT ck_cts_monto_deduccion CHECK (monto_deduccion >= 0)
);

-- ================================================================
-- FORMULARIOS Y PREGUNTAS
-- ================================================================

-- ------------------------------------------------------------------------
-- TABLA FORMULARIOS: Formularios vinculados al proceso de solicitud.
-- ------------------------------------------------------------------------
CREATE TABLE Formularios (
    idFormulario      NUMBER(10)    CONSTRAINT pk_formularios PRIMARY KEY,
    Nombre            VARCHAR2(100) NOT NULL,
    Descripcion       VARCHAR2(200),
    id_Tipo_Solicitud NUMBER(10)    NOT NULL,
    CONSTRAINT fk_formulario_tipo_solicitud
        FOREIGN KEY (id_Tipo_Solicitud) REFERENCES Tipo_Solicitudes(id_Tipo_Solicitud),
    CONSTRAINT uk_formulario_tipo_solicitud UNIQUE (id_Tipo_Solicitud)
);

-- ------------------------------------------------------------------------
-- TABLA PREGUNTAS: Banco de preguntas que pueden formar parte de formularios.
-- ------------------------------------------------------------------------
CREATE TABLE Preguntas (
    idPregunta     NUMBER(10)     CONSTRAINT pk_preguntas PRIMARY KEY,
    texto_Pregunta VARCHAR2(4000) NOT NULL,
    obligatoria    CHAR(1)        DEFAULT 'S' NOT NULL,
    tipo_pregunta  NUMBER(10)     NOT NULL,
    CONSTRAINT fk_pregunta_tipo
        FOREIGN KEY (tipo_pregunta) REFERENCES Tipo_Preguntas(id_Tipo_Pregunta),
    CONSTRAINT ck_pregunta_obligatoria CHECK (obligatoria IN ('S','N'))
);

-- ------------------------------------------------------------------------
-- TABLA FORMULARIO_PREGUNTAS: Relación de preguntas asignadas a formularios.
-- ------------------------------------------------------------------------
CREATE TABLE Formulario_Preguntas (
    idFormulario NUMBER(10) NOT NULL,
    idPregunta   NUMBER(10) NOT NULL,
    orden        NUMBER(5)  NOT NULL,
    CONSTRAINT pk_formulario_preguntas PRIMARY KEY (idFormulario, idPregunta),
    CONSTRAINT fk_fp_formulario
        FOREIGN KEY (idFormulario) REFERENCES Formularios(idFormulario),
    CONSTRAINT fk_fp_pregunta
        FOREIGN KEY (idPregunta) REFERENCES Preguntas(idPregunta),
    CONSTRAINT uk_fp_orden UNIQUE (idFormulario, orden),
    CONSTRAINT ck_fp_orden CHECK (orden > 0)
);

-- ------------------------------------------------------------------------
-- TABLA OPCIONES: Opciones de respuesta asociadas a preguntas.
-- ------------------------------------------------------------------------
CREATE TABLE Opciones (
    id_opcion    NUMBER(10)    CONSTRAINT pk_opciones PRIMARY KEY,
    texto_opcion VARCHAR2(255) NOT NULL,
    orden        NUMBER(5)     NOT NULL,
    id_pregunta  NUMBER(10)    NOT NULL,
    CONSTRAINT fk_opcion_pregunta
        FOREIGN KEY (id_pregunta) REFERENCES Preguntas(idPregunta),
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
    idSolicitud          NUMBER(10) CONSTRAINT pk_solicitudes PRIMARY KEY,
    id_estatus_solicitud NUMBER(10) NOT NULL,
    id_convocatoria      NUMBER(10) NOT NULL,
    num_control          VARCHAR2(10),
    ficha                VARCHAR2(18),
    periodo              NUMBER(2),
    anio                 NUMBER(4),
    fecha_Solicitud      DATE DEFAULT SYSDATE NOT NULL,
    fecha_actualizacion  DATE DEFAULT SYSDATE NOT NULL,
    id_Tipo_Solicitud    NUMBER(10) NOT NULL,
    idFormulario         NUMBER(10) NOT NULL,

    CONSTRAINT fk_solicitud_estatus
        FOREIGN KEY (id_estatus_solicitud)
        REFERENCES Estatus_Solicitudes(id_estatus_solicitud),

    CONSTRAINT fk_solicitud_convocatoria
        FOREIGN KEY (id_convocatoria)
        REFERENCES Convocatorias(id_convocatoria),

    CONSTRAINT fk_solicitud_estudiante
        FOREIGN KEY (num_control)
        REFERENCES Estudiantes(Numero_Control),

    CONSTRAINT fk_solicitud_aspirante
        FOREIGN KEY (ficha, periodo, anio)
        REFERENCES Aspirantes(ficha, periodo, anio),

    CONSTRAINT fk_solicitud_tipo
        FOREIGN KEY (id_Tipo_Solicitud)
        REFERENCES Tipo_Solicitudes(id_Tipo_Solicitud),

    CONSTRAINT fk_solicitud_formulario
        FOREIGN KEY (idFormulario)
        REFERENCES Formularios(idFormulario),

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
        FOREIGN KEY (id_solicitud) REFERENCES Solicitudes(idSolicitud),
    CONSTRAINT fk_respuesta_pregunta
        FOREIGN KEY (id_pregunta) REFERENCES Preguntas(idPregunta),
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
    fecha_Registro     DATE DEFAULT SYSDATE NOT NULL,
    id_solicitud       NUMBER(10) NOT NULL,
    id_estatus         NUMBER(10) NOT NULL,
    id_administrador   VARCHAR2(10),
    id_Tipo_solicitud  NUMBER(10) NOT NULL,
    CONSTRAINT fk_hs_solicitud
        FOREIGN KEY (id_solicitud) REFERENCES Solicitudes(idSolicitud),
    CONSTRAINT fk_hs_estatus
        FOREIGN KEY (id_estatus) REFERENCES Estatus_Solicitudes(id_estatus_solicitud),
    CONSTRAINT fk_hs_administrador
        FOREIGN KEY (id_administrador) REFERENCES Administradores(idAdministrador),
    CONSTRAINT fk_hs_tipo_solicitud
        FOREIGN KEY (id_Tipo_solicitud) REFERENCES Tipo_Solicitudes(id_Tipo_Solicitud)
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
    fecha_autorizacion      DATE,
    id_convocatoria         NUMBER(10) NOT NULL,
    id_Tipo_Solicitud       NUMBER(10) NOT NULL,
    fecha_actualizacion     DATE DEFAULT SYSDATE NOT NULL,
    comentario              VARCHAR2(500),
    id_monto_inscripcion    NUMBER(10),
    id_monto_reinscripcion  NUMBER(10),
    monto_pago              NUMBER(10,2) NOT NULL,
    monto_deduccion         NUMBER(10,2) NOT NULL,
    CONSTRAINT fk_da_estatus
        FOREIGN KEY (estatus_deduccion) REFERENCES Estatus_Deducciones(id_Estatus_Deducciones),
    CONSTRAINT fk_da_solicitud
        FOREIGN KEY (id_solicitud) REFERENCES Solicitudes(idSolicitud),
    CONSTRAINT fk_da_administrador
        FOREIGN KEY (id_administrador) REFERENCES Administradores(idAdministrador),
    CONSTRAINT fk_da_cts
        FOREIGN KEY (id_convocatoria, id_Tipo_Solicitud)
        REFERENCES Convocatorias_TipoSolicitudes(id_convocatoria, id_Tipo_Solicitud),
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
    id_Historial_Deduccion  NUMBER(10) CONSTRAINT pk_historial_deducciones PRIMARY KEY,
    id_deduccion_autorizada NUMBER(10) NOT NULL,
    id_estatus              NUMBER(10) NOT NULL,
    id_administrador        VARCHAR2(10),
    comentario              VARCHAR2(500),
    fecha_registro          DATE DEFAULT SYSDATE NOT NULL,
    CONSTRAINT fk_hd_deduccion
        FOREIGN KEY (id_deduccion_autorizada) REFERENCES Deducciones_autorizadas(id_deduccion_autorizada),
    CONSTRAINT fk_hd_estatus
        FOREIGN KEY (id_estatus) REFERENCES Estatus_Deducciones(id_Estatus_Deducciones),
    CONSTRAINT fk_hd_administrador
        FOREIGN KEY (id_administrador) REFERENCES Administradores(idAdministrador)
);

-- ================================================================
-- SECUENCIAS
-- ================================================================

-- Secuencia seq_tipo_solicitudes: proporciona identificadores automáticos para nuevos registros.
CREATE SEQUENCE seq_tipo_solicitudes START WITH 1 INCREMENT BY 1;
-- Secuencia seq_tipo_preguntas: proporciona identificadores automáticos para nuevos registros.
CREATE SEQUENCE seq_tipo_preguntas START WITH 4 INCREMENT BY 1;
-- Secuencia seq_estatus_solicitudes: proporciona identificadores automáticos para nuevos registros.
CREATE SEQUENCE seq_estatus_solicitudes START WITH 6 INCREMENT BY 1;
-- Secuencia seq_estatus_deducciones: proporciona identificadores automáticos para nuevos registros.
CREATE SEQUENCE seq_estatus_deducciones START WITH 6 INCREMENT BY 1;
-- Secuencia seq_convocatorias: proporciona identificadores automáticos para nuevos registros.
CREATE SEQUENCE seq_convocatorias START WITH 1 INCREMENT BY 1;
-- Secuencia seq_formularios: proporciona identificadores automáticos para nuevos registros.
CREATE SEQUENCE seq_formularios START WITH 1 INCREMENT BY 1;
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
WHEN (NEW.id_Tipo_Solicitud IS NULL)
BEGIN
    SELECT seq_tipo_solicitudes.NEXTVAL INTO :NEW.id_Tipo_Solicitud FROM dual;
END;
/

-- ------------------------------------------------------------------------
-- TRIGGER trg_tipo_preguntas_bi: Asigna el siguiente identificador de su secuencia cuando no se proporciona uno.
-- ------------------------------------------------------------------------
CREATE OR REPLACE TRIGGER trg_tipo_preguntas_bi
BEFORE INSERT ON Tipo_Preguntas
FOR EACH ROW
WHEN (NEW.id_Tipo_Pregunta IS NULL)
BEGIN
    SELECT seq_tipo_preguntas.NEXTVAL INTO :NEW.id_Tipo_Pregunta FROM dual;
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
WHEN (NEW.id_Estatus_Deducciones IS NULL)
BEGIN
    SELECT seq_estatus_deducciones.NEXTVAL INTO :NEW.id_Estatus_Deducciones FROM dual;
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
-- TRIGGER trg_formularios_bi: Asigna el siguiente identificador de su secuencia cuando no se proporciona uno.
-- ------------------------------------------------------------------------
CREATE OR REPLACE TRIGGER trg_formularios_bi
BEFORE INSERT ON Formularios
FOR EACH ROW
WHEN (NEW.idFormulario IS NULL)
BEGIN
    SELECT seq_formularios.NEXTVAL INTO :NEW.idFormulario FROM dual;
END;
/

-- ------------------------------------------------------------------------
-- TRIGGER trg_preguntas_bi: Asigna el siguiente identificador de su secuencia cuando no se proporciona uno.
-- ------------------------------------------------------------------------
CREATE OR REPLACE TRIGGER trg_preguntas_bi
BEFORE INSERT ON Preguntas
FOR EACH ROW
WHEN (NEW.idPregunta IS NULL)
BEGIN
    SELECT seq_preguntas.NEXTVAL INTO :NEW.idPregunta FROM dual;
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
WHEN (NEW.idSolicitud IS NULL)
BEGIN
    SELECT seq_solicitudes.NEXTVAL INTO :NEW.idSolicitud FROM dual;
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
WHEN (NEW.id_Historial_Deduccion IS NULL)
BEGIN
    SELECT seq_historial_deducciones.NEXTVAL INTO :NEW.id_Historial_Deduccion FROM dual;
END;
/

-- ================================================================
-- TRIGGER: VALIDACIÓN DE SOLICITUD
-- ================================================================

-- ------------------------------------------------------------------------
-- TRIGGER trg_solicitudes_validacion: Comprueba que la convocatoria, el tipo de solicitud y el formulario sean compatibles con el solicitante.
-- ------------------------------------------------------------------------
CREATE OR REPLACE TRIGGER trg_solicitudes_validacion
BEFORE INSERT OR UPDATE ON Solicitudes
FOR EACH ROW
DECLARE
    v_tipo_formulario      Formularios.id_Tipo_Solicitud%TYPE;
    v_periodo_convocatoria Convocatorias.id_periodo%TYPE;
    v_anio_convocatoria    Convocatorias.anio%TYPE;
    v_periodo_aspirante    Aspirantes.periodo%TYPE;
    v_anio_aspirante       Aspirantes.anio%TYPE;
BEGIN
    SELECT id_Tipo_Solicitud
      INTO v_tipo_formulario
      FROM Formularios
     WHERE idFormulario = :NEW.idFormulario;

    IF v_tipo_formulario <> :NEW.id_Tipo_Solicitud THEN
        RAISE_APPLICATION_ERROR(
            -20001,
            'El formulario no corresponde al tipo de solicitud.'
        );
    END IF;

    IF :NEW.num_control IS NULL THEN
        SELECT periodo, anio
          INTO v_periodo_aspirante, v_anio_aspirante
          FROM Aspirantes
         WHERE ficha = :NEW.ficha
           AND periodo = :NEW.periodo
           AND anio = :NEW.anio;

        SELECT id_periodo, anio
          INTO v_periodo_convocatoria, v_anio_convocatoria
          FROM Convocatorias
         WHERE id_convocatoria = :NEW.id_convocatoria;

        IF v_periodo_aspirante <> v_periodo_convocatoria
           OR v_anio_aspirante <> v_anio_convocatoria THEN
            RAISE_APPLICATION_ERROR(
                -20003,
                'El periodo y anio del aspirante deben coincidir con los de la convocatoria.'
            );
        END IF;
    END IF;

    :NEW.fecha_actualizacion := SYSDATE;
EXCEPTION
    WHEN NO_DATA_FOUND THEN
        RAISE_APPLICATION_ERROR(
            -20002,
            'El formulario, aspirante o convocatoria indicada no existe.'
        );
END;
/

-- ================================================================
-- TRIGGER: VALIDACIÓN DE RESPUESTAS
-- ================================================================

-- ------------------------------------------------------------------------
-- TRIGGER trg_respuestas_validacion: Verifica que cada respuesta corresponda a una pregunta válida del formulario y respete su modalidad.
-- ------------------------------------------------------------------------
CREATE OR REPLACE TRIGGER trg_respuestas_validacion
BEFORE INSERT OR UPDATE ON Respuestas
FOR EACH ROW
DECLARE
    v_tipo_pregunta        Preguntas.tipo_pregunta%TYPE;
    v_formulario_solicitud Solicitudes.idFormulario%TYPE;
    v_pregunta_en_formulario NUMBER;
BEGIN
    SELECT tipo_pregunta
      INTO v_tipo_pregunta
      FROM Preguntas
     WHERE idPregunta = :NEW.id_pregunta;

    SELECT idFormulario
      INTO v_formulario_solicitud
      FROM Solicitudes
     WHERE idSolicitud = :NEW.id_solicitud;

    SELECT COUNT(*)
      INTO v_pregunta_en_formulario
      FROM Formulario_Preguntas
     WHERE idFormulario = v_formulario_solicitud
       AND idPregunta = :NEW.id_pregunta;

    IF v_pregunta_en_formulario = 0 THEN
        RAISE_APPLICATION_ERROR(
            -20009,
            'La pregunta no pertenece al formulario de la solicitud.'
        );
    END IF;

    IF v_tipo_pregunta = 1 THEN
        IF :NEW.texto_respuesta IS NULL OR :NEW.id_opcion IS NOT NULL THEN
            RAISE_APPLICATION_ERROR(
                -20010,
                'Una pregunta ABIERTA requiere texto y no permite una opción.'
            );
        END IF;

    ELSIF v_tipo_pregunta = 2 THEN
        IF :NEW.id_opcion IS NULL OR :NEW.texto_respuesta IS NOT NULL THEN
            RAISE_APPLICATION_ERROR(
                -20011,
                'Una pregunta OPCION_UNICA requiere una opción y no permite texto adicional.'
            );
        END IF;

    ELSIF v_tipo_pregunta = 3 THEN
        IF :NEW.id_opcion IS NULL THEN
            RAISE_APPLICATION_ERROR(
                -20012,
                'Una pregunta MIXTA requiere seleccionar una opción.'
            );
        END IF;

    ELSE
        RAISE_APPLICATION_ERROR(
            -20016,
            'El tipo de pregunta indicado no es válido.'
        );
    END IF;

    IF :NEW.id_opcion IS NOT NULL THEN
        DECLARE
            v_pregunta_opcion Opciones.id_pregunta%TYPE;
            v_texto_opcion    Opciones.texto_opcion%TYPE;
        BEGIN
            SELECT id_pregunta, texto_opcion
              INTO v_pregunta_opcion, v_texto_opcion
              FROM Opciones
             WHERE id_opcion = :NEW.id_opcion;

            IF v_pregunta_opcion <> :NEW.id_pregunta THEN
                RAISE_APPLICATION_ERROR(
                    -20014,
                    'La opción seleccionada no pertenece a la pregunta.'
                );
            END IF;

            IF v_tipo_pregunta = 3 THEN
                IF UPPER(TRIM(v_texto_opcion)) = 'OTRO' THEN
                    IF :NEW.texto_respuesta IS NULL THEN
                        RAISE_APPLICATION_ERROR(
                            -20017,
                            'La opción OTRO requiere una respuesta de texto.'
                        );
                    END IF;
                ELSE
                    IF :NEW.texto_respuesta IS NOT NULL THEN
                        RAISE_APPLICATION_ERROR(
                            -20018,
                            'Una opción normal de una pregunta MIXTA no permite texto adicional.'
                        );
                    END IF;
                END IF;
            END IF;
        EXCEPTION
            WHEN NO_DATA_FOUND THEN
                RAISE_APPLICATION_ERROR(
                    -20015,
                    'La opción indicada no existe.'
                );
        END;
    END IF;
EXCEPTION
    WHEN NO_DATA_FOUND THEN
        RAISE_APPLICATION_ERROR(
            -20008,
            'La solicitud o la pregunta indicada no existe.'
        );
END;
/

-- ================================================================
-- TRIGGER: CÁLCULO Y VALIDACIÓN DE DEDUCCIÓN
-- ================================================================

-- ------------------------------------------------------------------------
-- TRIGGER trg_deducciones_calculo: Valida los montos y estados de la deducción, calcula el importe final y controla los cambios permitidos.
-- ------------------------------------------------------------------------
CREATE OR REPLACE TRIGGER trg_deducciones_calculo
BEFORE INSERT OR UPDATE ON Deducciones_autorizadas
FOR EACH ROW
DECLARE
    v_estatus_solicitud Solicitudes.id_estatus_solicitud%TYPE;
    v_num_control       Solicitudes.num_control%TYPE;
    v_convocatoria      Solicitudes.id_convocatoria%TYPE;
    v_tipo_solicitud    Solicitudes.id_Tipo_Solicitud%TYPE;
    v_monto_pago        NUMBER(10,2);
    v_monto_deduccion   NUMBER(10,2);
BEGIN
    SELECT id_estatus_solicitud,
           num_control,
           id_convocatoria,
           id_Tipo_Solicitud
      INTO v_estatus_solicitud,
           v_num_control,
           v_convocatoria,
           v_tipo_solicitud
      FROM Solicitudes
     WHERE idSolicitud = :NEW.id_solicitud;

    IF INSERTING THEN

        IF v_estatus_solicitud <> 2 THEN
            RAISE_APPLICATION_ERROR(
                -20019,
                'Solo se puede crear una deduccion para una solicitud AUTORIZADA.'
            );
        END IF;

        IF :NEW.estatus_deduccion <> 2 THEN
            RAISE_APPLICATION_ERROR(
                -20023,
                'Una deduccion nueva debe iniciar con estatus AUTORIZADO.'
            );
        END IF;

        :NEW.id_convocatoria   := v_convocatoria;
        :NEW.id_Tipo_Solicitud := v_tipo_solicitud;

        IF v_num_control IS NOT NULL THEN
            IF :NEW.id_monto_reinscripcion IS NULL
               OR :NEW.id_monto_inscripcion IS NOT NULL THEN
                RAISE_APPLICATION_ERROR(
                    -20020,
                    'Una solicitud de estudiante debe utilizar un monto de reinscripcion.'
                );
            END IF;

            SELECT monto_pago
              INTO v_monto_pago
              FROM Monto_Reinscripciones
             WHERE id_monto_reinscripcion = :NEW.id_monto_reinscripcion;

        ELSE
            IF :NEW.id_monto_inscripcion IS NULL
               OR :NEW.id_monto_reinscripcion IS NOT NULL THEN
                RAISE_APPLICATION_ERROR(
                    -20021,
                    'Una solicitud de aspirante debe utilizar un monto de inscripcion.'
                );
            END IF;

            SELECT monto_pago
              INTO v_monto_pago
              FROM Monto_Inscripciones
             WHERE id_monto_inscripcion = :NEW.id_monto_inscripcion;
        END IF;

        SELECT monto_deduccion
          INTO v_monto_deduccion
          FROM Convocatorias_TipoSolicitudes
         WHERE id_convocatoria = v_convocatoria
           AND id_Tipo_Solicitud = v_tipo_solicitud;

        :NEW.monto_pago      := v_monto_pago;
        :NEW.monto_deduccion := v_monto_deduccion;
        :NEW.monto_final     := v_monto_pago - v_monto_deduccion;

        IF :NEW.fecha_autorizacion IS NULL THEN
            :NEW.fecha_autorizacion := SYSDATE;
        END IF;

    ELSE
        -- Conserva las fechas de registro y autorización para mantener la trazabilidad.
        IF :NEW.fecha_registro <> :OLD.fecha_registro
           OR NVL(:NEW.fecha_autorizacion, DATE '1900-01-01') <>
              NVL(:OLD.fecha_autorizacion, DATE '1900-01-01') THEN
            RAISE_APPLICATION_ERROR(-20025,
                'Las fechas originales de registro y autorizacion son inmutables.');
        END IF;

        -- Transiciones permitidas: 2->4, 2->5 y 4->2.
        IF :NEW.estatus_deduccion <> :OLD.estatus_deduccion THEN
            IF NOT (
                (:OLD.estatus_deduccion = 2 AND :NEW.estatus_deduccion IN (4,5))
                OR (:OLD.estatus_deduccion = 4 AND :NEW.estatus_deduccion = 2)
            ) THEN
                RAISE_APPLICATION_ERROR(-20026,
                    'Transicion de estatus de deduccion no permitida.');
            END IF;

            IF :OLD.estatus_deduccion = 4 AND :NEW.estatus_deduccion = 2 THEN
                DECLARE
                    v_inicio Convocatorias.fecha_inicio%TYPE;
                    v_fin    Convocatorias.fecha_fin%TYPE;
                BEGIN
                    SELECT fecha_inicio, fecha_fin
                      INTO v_inicio, v_fin
                      FROM Convocatorias
                     WHERE id_convocatoria = v_convocatoria;

                    IF SYSDATE < TRUNC(v_inicio)
                       OR SYSDATE >= TRUNC(v_fin) + 1 THEN
                        RAISE_APPLICATION_ERROR(-20027,
                            'No se puede reautorizar: convocatoria fuera de vigencia.');
                    END IF;
                END;
            END IF;
        END IF;

        IF :OLD.id_solicitud <> :NEW.id_solicitud
           OR :OLD.id_convocatoria <> :NEW.id_convocatoria
           OR :OLD.id_Tipo_Solicitud <> :NEW.id_Tipo_Solicitud
           OR NVL(:OLD.id_monto_inscripcion, -1) <>
              NVL(:NEW.id_monto_inscripcion, -1)
           OR NVL(:OLD.id_monto_reinscripcion, -1) <>
              NVL(:NEW.id_monto_reinscripcion, -1)
           OR :OLD.monto_pago <> :NEW.monto_pago
           OR :OLD.monto_deduccion <> :NEW.monto_deduccion
           OR :OLD.monto_final <> :NEW.monto_final THEN

            RAISE_APPLICATION_ERROR(
                -20024,
                'Los datos historicos y de origen de una deduccion no pueden modificarse.'
            );
        END IF;
    END IF;

EXCEPTION
    WHEN NO_DATA_FOUND THEN
        RAISE_APPLICATION_ERROR(
            -20022,
            'No existe el monto o la configuracion de deduccion correspondiente a la solicitud.'
        );
END;
/

-- ================================================================
-- TRIGGER: HISTORIAL DE SOLICITUDES
-- ================================================================

-- ------------------------------------------------------------------------
-- TRIGGER trg_solicitudes_historial: Registra los cambios de estatus de las solicitudes para su seguimiento.
-- ------------------------------------------------------------------------
CREATE OR REPLACE TRIGGER trg_solicitudes_historial
AFTER UPDATE OF id_estatus_solicitud ON Solicitudes
FOR EACH ROW
WHEN (OLD.id_estatus_solicitud <> NEW.id_estatus_solicitud)
BEGIN
    INSERT INTO Historial_Solicitudes (
        comentario,
        fecha_Registro,
        id_solicitud,
        id_estatus,
        id_administrador,
        id_Tipo_solicitud
    ) VALUES (
        NULL,
        SYSDATE,
        :NEW.idSolicitud,
        :NEW.id_estatus_solicitud,
        NULL,
        :NEW.id_Tipo_Solicitud
    );
END;
/

-- ================================================================
-- TRIGGER: HISTORIAL DE DEDUCCIONES
-- ================================================================

-- ------------------------------------------------------------------------
-- TRIGGER trg_deducciones_historial: Registra los cambios de estatus de las deducciones para su seguimiento.
-- ------------------------------------------------------------------------
CREATE OR REPLACE TRIGGER trg_deducciones_historial
AFTER UPDATE OF estatus_deduccion ON Deducciones_autorizadas
FOR EACH ROW
WHEN (OLD.estatus_deduccion <> NEW.estatus_deduccion)
BEGIN
    INSERT INTO Historial_Deducciones (
        id_deduccion_autorizada,
        id_estatus,
        id_administrador,
        comentario,
        fecha_registro
    ) VALUES (
        :NEW.id_deduccion_autorizada,
        :NEW.estatus_deduccion,
        :NEW.id_administrador,
        NULL,
        SYSDATE
    );
END;
/

-- ================================================================
-- TRIGGER: SINCRONIZACION DEL ESTATUS DE SOLICITUD CON DEDUCCION
-- ================================================================

-- ------------------------------------------------------------------------
-- TRIGGER trg_deduccion_cancelacion: Sincroniza el estado de la solicitud cuando cambia el estado de su deducción.
-- ------------------------------------------------------------------------
CREATE OR REPLACE TRIGGER trg_deduccion_cancelacion
AFTER UPDATE OF estatus_deduccion ON Deducciones_autorizadas
FOR EACH ROW
WHEN (OLD.estatus_deduccion <> NEW.estatus_deduccion
      AND NEW.estatus_deduccion IN (2,4,5))
DECLARE
    v_id_historial Historial_Solicitudes.id_historial%TYPE;
BEGIN
    -- La solicitud sigue el estado de la deduccion: 2, 4 o 5.
    UPDATE Solicitudes
       SET id_estatus_solicitud = :NEW.estatus_deduccion,
           fecha_actualizacion = SYSDATE
     WHERE idSolicitud = :NEW.id_solicitud;

    SELECT MAX(id_historial)
      INTO v_id_historial
      FROM Historial_Solicitudes
     WHERE id_solicitud = :NEW.id_solicitud
       AND id_estatus = :NEW.estatus_deduccion;

    UPDATE Historial_Solicitudes
       SET id_administrador = :NEW.id_administrador,
           comentario = :NEW.comentario
     WHERE id_historial = v_id_historial;
END;
/

-- ================================================================
-- TRIGGER: FECHA DE ACTUALIZACIÓN DE DEDUCCIÓN
-- ================================================================

-- ------------------------------------------------------------------------
-- TRIGGER trg_deducciones_fecha_update: Actualiza la fecha de modificación de una deducción.
-- ------------------------------------------------------------------------
CREATE OR REPLACE TRIGGER trg_deducciones_fecha_update
BEFORE UPDATE ON Deducciones_autorizadas
FOR EACH ROW
BEGIN
    :NEW.fecha_actualizacion := SYSDATE;
END;
/

-- ================================================================
-- DATOS INICIALES DE CATÁLOGOS
-- ================================================================

INSERT INTO Periodos (idPeriodo, Descripcion) VALUES (1, 'Agosto-Diciembre');
INSERT INTO Periodos (idPeriodo, Descripcion) VALUES (2, 'Enero-Junio');

INSERT INTO Estatus_Solicitudes (id_estatus_solicitud, Nombre, Descripcion)
VALUES (1, 'PENDIENTE', 'Solicitud pendiente de revisión');
INSERT INTO Estatus_Solicitudes (id_estatus_solicitud, Nombre, Descripcion)
VALUES (2, 'AUTORIZADO', 'Solicitud autorizada');
INSERT INTO Estatus_Solicitudes (id_estatus_solicitud, Nombre, Descripcion)
VALUES (3, 'RECHAZADO', 'Solicitud rechazada');
INSERT INTO Estatus_Solicitudes (id_estatus_solicitud, Nombre, Descripcion)
VALUES (4, 'CANCELADO', 'Solicitud cancelada');
INSERT INTO Estatus_Solicitudes (id_estatus_solicitud, Nombre, Descripcion)
VALUES (5, 'APLICADO', 'Solicitud aplicada/pagada');

INSERT INTO Estatus_Deducciones (id_Estatus_Deducciones, Nombre, Descripcion)
VALUES (2, 'AUTORIZADO', 'Deducción autorizada');
INSERT INTO Estatus_Deducciones (id_Estatus_Deducciones, Nombre, Descripcion)
VALUES (4, 'CANCELADO', 'Deducción cancelada');
INSERT INTO Estatus_Deducciones (id_Estatus_Deducciones, Nombre, Descripcion)
VALUES (5, 'APLICADO', 'Deducción aplicada/pagada');

INSERT INTO Tipo_Preguntas (id_Tipo_Pregunta, Nombre)
VALUES (1, 'ABIERTA');
INSERT INTO Tipo_Preguntas (id_Tipo_Pregunta, Nombre)
VALUES (2, 'OPCION_UNICA');
INSERT INTO Tipo_Preguntas (id_Tipo_Pregunta, Nombre)
VALUES (3, 'MIXTA');
COMMIT;

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
