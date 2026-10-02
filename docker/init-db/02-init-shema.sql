-- ================================================================
-- BASE DE DATOS: Servicio Administrativo de Reducción del monto
--               de inscripción
-- Motor: Oracle Database
-- ================================================================
-- NOTAS DE DISEÑO
-- 1. Un Formulario pertenece a un Tipo_Solicitud.
-- 2. Un Tipo_Solicitud puede tener un Formulario asignado.
-- 3. NO se impone una relación Formulario + Convocatoria.
-- 4. Una Solicitud pertenece a un estudiante O a un aspirante, nunca a ambos.
-- 5. Aspirante usa PK compuesta: (ficha, periodo, anio).
-- 6. Convocatorias_TipoSolicitudes usa PK compuesta.
-- 7. Los IDs de estatus de solicitudes y deducciones conservan los valores:
--      1 PENDIENTE, 2 AUTORIZADO, 3 RECHAZADO,
--      4 CANCELADO, 5 APLICADO.
-- 8. MULTIPLE se mantiene como tipo de pregunta disponible. Una pregunta
--    MULTIPLE puede tener varias respuestas de opción para una solicitud.
-- ================================================================

-- ================================================================
-- TABLAS CATÁLOGO / SEGURIDAD
-- ================================================================

CREATE TABLE Usuarios (
    id_usuario       VARCHAR2(50)  CONSTRAINT pk_usuarios PRIMARY KEY,
    password_hash    VARCHAR2(255) NOT NULL
);

CREATE TABLE Periodos (
    idPeriodo        NUMBER(2)     CONSTRAINT pk_periodos PRIMARY KEY,
    Descripcion      VARCHAR2(50)  NOT NULL,
    CONSTRAINT uk_periodos_descripcion UNIQUE (Descripcion)
);

CREATE TABLE Tipo_Solicitudes (
    id_Tipo_Solicitud NUMBER(10)    CONSTRAINT pk_tipo_solicitudes PRIMARY KEY,
    Nombre            VARCHAR2(50)  NOT NULL,
    Descripcion       VARCHAR2(200)
);

CREATE TABLE Tipo_Preguntas (
    id_Tipo_Pregunta NUMBER(10)    CONSTRAINT pk_tipo_preguntas PRIMARY KEY,
    Nombre           VARCHAR2(50)  NOT NULL,
    CONSTRAINT uk_tipo_preguntas_nombre UNIQUE (Nombre)
);

CREATE TABLE Estatus_Solicitudes (
    id_estatus_solicitud NUMBER(10)    CONSTRAINT pk_estatus_solicitudes PRIMARY KEY,
    Nombre               VARCHAR2(100) NOT NULL,
    Descripcion           VARCHAR2(200),
    CONSTRAINT uk_estatus_solicitudes_nombre UNIQUE (Nombre)
);

CREATE TABLE Estatus_Deducciones (
    id_Estatus_Deducciones NUMBER(10)   CONSTRAINT pk_estatus_deducciones PRIMARY KEY,
    Nombre                 VARCHAR2(50) NOT NULL,
    Descripcion             VARCHAR2(100),
    CONSTRAINT uk_estatus_deducciones_nombre UNIQUE (Nombre)
);

-- ================================================================
-- PERSONAS / PERFILES
-- ================================================================

CREATE TABLE Estudiante (
    Numero_Control   VARCHAR2(50)  CONSTRAINT pk_estudiante PRIMARY KEY,
    Nombre           VARCHAR2(50)  NOT NULL,
    PrimerAppellido  VARCHAR2(50)  NOT NULL,
    SegundoApellido  VARCHAR2(50),
    Carrera          VARCHAR2(100),
    Semestre         NUMBER(2),
    id_usuario       VARCHAR2(50),
    CONSTRAINT fk_estudiante_usuario
        FOREIGN KEY (id_usuario) REFERENCES Usuarios(id_usuario),
    CONSTRAINT uk_estudiante_usuario UNIQUE (id_usuario),
    CONSTRAINT ck_estudiante_semestre CHECK (Semestre IS NULL OR Semestre BETWEEN 1 AND 20)
);

CREATE TABLE Aspirante (
    ficha           VARCHAR2(18)  NOT NULL,
    periodo         NUMBER(2)     NOT NULL,
    anio            NUMBER(4)     NOT NULL,
    claveControl    VARCHAR2(50),
    Nombre          VARCHAR2(50)  NOT NULL,
    PrimerApellido  VARCHAR2(50)  NOT NULL,
    SegundoApellido VARCHAR2(50),
    Semestre        NUMBER(2),
    Curp            VARCHAR2(18)  NOT NULL,
    correo          VARCHAR2(254),
    id_usuario      VARCHAR2(50),
    CONSTRAINT pk_aspirante PRIMARY KEY (ficha, periodo, anio),
    CONSTRAINT fk_aspirante_periodo
        FOREIGN KEY (periodo) REFERENCES Periodos(idPeriodo),
    CONSTRAINT fk_aspirante_usuario
        FOREIGN KEY (id_usuario) REFERENCES Usuarios(id_usuario),
    CONSTRAINT uk_aspirante_usuario UNIQUE (id_usuario),
    CONSTRAINT ck_aspirante_anio CHECK (anio BETWEEN 2000 AND 9999),
    CONSTRAINT ck_aspirante_curp CHECK (LENGTH(Curp) = 18),
    CONSTRAINT ck_aspirante_semestre CHECK (Semestre IS NULL OR Semestre BETWEEN 1 AND 20)
);

CREATE TABLE Administradores (
    idAdministrador VARCHAR2(50) CONSTRAINT pk_administradores PRIMARY KEY,
    Nombre          VARCHAR2(50) NOT NULL,
    PrimerApellido  VARCHAR2(50) NOT NULL,
    SegundoApellido VARCHAR2(50),
    id_usuario      VARCHAR2(50) NOT NULL,
    CONSTRAINT fk_administrador_usuario
        FOREIGN KEY (id_usuario) REFERENCES Usuarios(id_usuario),
    CONSTRAINT uk_administrador_usuario UNIQUE (id_usuario)
);

-- ================================================================
-- CONVOCATORIAS
-- ================================================================

CREATE TABLE Convocatorias (
    id_convocatoria NUMBER(10) CONSTRAINT pk_convocatorias PRIMARY KEY,
    fecha_inicio    DATE       NOT NULL,
    fecha_fin       DATE       NOT NULL,
    id_periodo      NUMBER(2)  NOT NULL,
    anio            NUMBER(4)  NOT NULL,
    CONSTRAINT fk_convocatoria_periodo
        FOREIGN KEY (id_periodo) REFERENCES Periodos(idPeriodo),
    CONSTRAINT ck_convocatoria_fechas CHECK (fecha_fin >= fecha_inicio),
    CONSTRAINT ck_convocatoria_anio CHECK (anio BETWEEN 2000 AND 9999)
);

CREATE TABLE Convocatorias_TipoSolicitudes (
    id_convocatoria    NUMBER(10) NOT NULL,
    id_Tipo_Solicitud  NUMBER(10) NOT NULL,
    monto_deduccion    NUMBER(10,2) NOT NULL,
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

CREATE TABLE Formularios (
    idFormulario      NUMBER(10)    CONSTRAINT pk_formularios PRIMARY KEY,
    Nombre            VARCHAR2(100) NOT NULL,
    Descripcion       VARCHAR2(200),
    id_Tipo_Solicitud NUMBER(10)    NOT NULL,
    CONSTRAINT fk_formulario_tipo_solicitud
        FOREIGN KEY (id_Tipo_Solicitud) REFERENCES Tipo_Solicitudes(id_Tipo_Solicitud),
    CONSTRAINT uk_formulario_tipo_solicitud UNIQUE (id_Tipo_Solicitud)
);

CREATE TABLE Preguntas (
    idPregunta       NUMBER(10)     CONSTRAINT pk_preguntas PRIMARY KEY,
    texto_Pregunta  VARCHAR2(4000) NOT NULL,
    obligatoria      CHAR(1)        DEFAULT 'S' NOT NULL,
    tipo_pregunta    NUMBER(10)     NOT NULL,
    CONSTRAINT fk_pregunta_tipo
        FOREIGN KEY (tipo_pregunta) REFERENCES Tipo_Preguntas(id_Tipo_Pregunta),
    CONSTRAINT ck_pregunta_obligatoria CHECK (obligatoria IN ('S','N'))
);

CREATE TABLE Formulario_Pregunta (
    idFormulario NUMBER(10) NOT NULL,
    idPregunta   NUMBER(10) NOT NULL,
    orden        NUMBER(5)  NOT NULL,
    CONSTRAINT pk_formulario_pregunta PRIMARY KEY (idFormulario, idPregunta),
    CONSTRAINT fk_fp_formulario
        FOREIGN KEY (idFormulario) REFERENCES Formularios(idFormulario),
    CONSTRAINT fk_fp_pregunta
        FOREIGN KEY (idPregunta) REFERENCES Preguntas(idPregunta),
    CONSTRAINT uk_fp_orden UNIQUE (idFormulario, orden),
    CONSTRAINT ck_fp_orden CHECK (orden > 0)
);

CREATE TABLE Opciones (
    id_opcion            NUMBER(10)     CONSTRAINT pk_opciones PRIMARY KEY,
    texto_opcion         VARCHAR2(255) NOT NULL,
    orden                NUMBER(5)     NOT NULL,
    id_pregunta          NUMBER(10)    NOT NULL,
    CONSTRAINT fk_opcion_pregunta
        FOREIGN KEY (id_pregunta) REFERENCES Preguntas(idPregunta),
    CONSTRAINT uk_opcion_orden UNIQUE (id_pregunta, orden),
    CONSTRAINT ck_opcion_orden CHECK (orden > 0)
);

-- ================================================================
-- MONTOS
-- ================================================================

CREATE TABLE Monto_Inscripcion (
    id_monto_inscripcion NUMBER(10) CONSTRAINT pk_monto_inscripcion PRIMARY KEY,
    monto_pago           NUMBER(10,2) NOT NULL,
    CONSTRAINT ck_monto_inscripcion CHECK (monto_pago >= 0)
);

CREATE TABLE Monto_Reinscripcion (
    id_monto_reinscripcion NUMBER(10) CONSTRAINT pk_monto_reinscripcion PRIMARY KEY,
    monto_pago              NUMBER(10,2) NOT NULL,
    CONSTRAINT ck_monto_reinscripcion CHECK (monto_pago >= 0)
);

-- ================================================================
-- SOLICITUDES
-- ================================================================

CREATE TABLE Solicitudes (
    idSolicitud          NUMBER(10) CONSTRAINT pk_solicitudes PRIMARY KEY,
    id_estatus_solicitud NUMBER(10) NOT NULL,
    id_convocatoria      NUMBER(10) NOT NULL,
    num_control          VARCHAR2(50),
    ficha                VARCHAR2(18),
    periodo              NUMBER(2),
    anio                 NUMBER(4),
    fecha_Solicitud      DATE DEFAULT SYSDATE NOT NULL,
    fecha_actualizacion  DATE DEFAULT SYSDATE NOT NULL,
    id_Tipo_Solicitud    NUMBER(10) NOT NULL,
    idFormulario         NUMBER(10) NOT NULL,
    CONSTRAINT fk_solicitud_estatus
        FOREIGN KEY (id_estatus_solicitud) REFERENCES Estatus_Solicitudes(id_estatus_solicitud),
    CONSTRAINT fk_solicitud_convocatoria
        FOREIGN KEY (id_convocatoria) REFERENCES Convocatorias(id_convocatoria),
    CONSTRAINT fk_solicitud_estudiante
        FOREIGN KEY (num_control) REFERENCES Estudiante(Numero_Control),
    CONSTRAINT fk_solicitud_aspirante
        FOREIGN KEY (ficha, periodo, anio) REFERENCES Aspirante(ficha, periodo, anio),
    CONSTRAINT fk_solicitud_tipo
        FOREIGN KEY (id_Tipo_Solicitud) REFERENCES Tipo_Solicitudes(id_Tipo_Solicitud),
    CONSTRAINT fk_solicitud_formulario
        FOREIGN KEY (idFormulario) REFERENCES Formularios(idFormulario),
    CONSTRAINT ck_solicitud_origen CHECK (
        (num_control IS NOT NULL AND ficha IS NULL AND periodo IS NULL AND anio IS NULL)
        OR
        (num_control IS NULL AND ficha IS NOT NULL AND periodo IS NOT NULL AND anio IS NOT NULL)
    ),
    CONSTRAINT ck_solicitud_anio CHECK (anio IS NULL OR anio BETWEEN 2000 AND 9999)
);

-- ================================================================
-- RESPUESTAS
-- ================================================================

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

-- Evita repetir la misma opción para una pregunta MULTIPLE dentro de una solicitud.
CREATE UNIQUE INDEX ux_respuesta_opcion
    ON Respuestas (id_solicitud, id_pregunta, id_opcion);

-- ================================================================
-- HISTORIAL DE SOLICITUDES
-- ================================================================

CREATE TABLE Historial_Solicitudes (
    id_historial          NUMBER(10) CONSTRAINT pk_historial_solicitudes PRIMARY KEY,
    comentario            VARCHAR2(500),
    fecha_Registro        DATE DEFAULT SYSDATE NOT NULL,
    id_solicitud          NUMBER(10) NOT NULL,
    id_estatus            NUMBER(10) NOT NULL,
    id_administrador      VARCHAR2(50),
    id_Tipo_solicitud     NUMBER(10) NOT NULL,
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

CREATE TABLE Deducciones_autorizadas (
    id_deduccion_autorizada NUMBER(10) CONSTRAINT pk_deducciones_autorizadas PRIMARY KEY,
    estatus_deduccion       NUMBER(10) NOT NULL,
    monto_final             NUMBER(10,2),
    fecha_registro          DATE DEFAULT SYSDATE NOT NULL,
    id_solicitud            NUMBER(10) NOT NULL,
    id_administrador        VARCHAR2(50),
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
        FOREIGN KEY (id_monto_inscripcion) REFERENCES Monto_Inscripcion(id_monto_inscripcion),
    CONSTRAINT fk_da_monto_reinscripcion
        FOREIGN KEY (id_monto_reinscripcion) REFERENCES Monto_Reinscripcion(id_monto_reinscripcion),
    CONSTRAINT uk_da_solicitud UNIQUE (id_solicitud),
    CONSTRAINT ck_da_monto_origen CHECK (
        (id_monto_inscripcion IS NOT NULL AND id_monto_reinscripcion IS NULL)
        OR
        (id_monto_inscripcion IS NULL AND id_monto_reinscripcion IS NOT NULL)
    ),
    CONSTRAINT ck_da_montos CHECK (monto_pago >= 0 AND monto_deduccion >= 0),
    CONSTRAINT ck_da_monto_final CHECK (monto_final = monto_pago - monto_deduccion)
);

CREATE TABLE Historial_Deducciones (
    id_Historial_Deduccion NUMBER(10) CONSTRAINT pk_historial_deducciones PRIMARY KEY,
    id_deduccion_autorizada NUMBER(10) NOT NULL,
    id_estatus              NUMBER(10) NOT NULL,
    id_administrador        VARCHAR2(50),
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
-- Se usan para generar IDs en versiones de Oracle donde no se quiera
-- depender de columnas IDENTITY.

CREATE SEQUENCE seq_tipo_solicitudes START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE seq_tipo_preguntas START WITH 5 INCREMENT BY 1;
CREATE SEQUENCE seq_estatus_solicitudes START WITH 6 INCREMENT BY 1;
CREATE SEQUENCE seq_estatus_deducciones START WITH 6 INCREMENT BY 1;
CREATE SEQUENCE seq_convocatorias START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE seq_formularios START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE seq_preguntas START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE seq_opciones START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE seq_monto_inscripcion START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE seq_monto_reinscripcion START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE seq_solicitudes START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE seq_respuestas START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE seq_historial_solicitudes START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE seq_deducciones_autorizadas START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE seq_historial_deducciones START WITH 1 INCREMENT BY 1;

-- ================================================================
-- TRIGGERS DE IDs
-- ================================================================

CREATE OR REPLACE TRIGGER trg_tipo_solicitudes_bi
BEFORE INSERT ON Tipo_Solicitudes
FOR EACH ROW
WHEN (NEW.id_Tipo_Solicitud IS NULL)
BEGIN
    SELECT seq_tipo_solicitudes.NEXTVAL INTO :NEW.id_Tipo_Solicitud FROM dual;
END;
/

CREATE OR REPLACE TRIGGER trg_tipo_preguntas_bi
BEFORE INSERT ON Tipo_Preguntas
FOR EACH ROW
WHEN (NEW.id_Tipo_Pregunta IS NULL)
BEGIN
    SELECT seq_tipo_preguntas.NEXTVAL INTO :NEW.id_Tipo_Pregunta FROM dual;
END;
/

CREATE OR REPLACE TRIGGER trg_estatus_solicitudes_bi
BEFORE INSERT ON Estatus_Solicitudes
FOR EACH ROW
WHEN (NEW.id_estatus_solicitud IS NULL)
BEGIN
    SELECT seq_estatus_solicitudes.NEXTVAL INTO :NEW.id_estatus_solicitud FROM dual;
END;
/

CREATE OR REPLACE TRIGGER trg_estatus_deducciones_bi
BEFORE INSERT ON Estatus_Deducciones
FOR EACH ROW
WHEN (NEW.id_Estatus_Deducciones IS NULL)
BEGIN
    SELECT seq_estatus_deducciones.NEXTVAL INTO :NEW.id_Estatus_Deducciones FROM dual;
END;
/

CREATE OR REPLACE TRIGGER trg_convocatorias_bi
BEFORE INSERT ON Convocatorias
FOR EACH ROW
WHEN (NEW.id_convocatoria IS NULL)
BEGIN
    SELECT seq_convocatorias.NEXTVAL INTO :NEW.id_convocatoria FROM dual;
END;
/

CREATE OR REPLACE TRIGGER trg_formularios_bi
BEFORE INSERT ON Formularios
FOR EACH ROW
WHEN (NEW.idFormulario IS NULL)
BEGIN
    SELECT seq_formularios.NEXTVAL INTO :NEW.idFormulario FROM dual;
END;
/

CREATE OR REPLACE TRIGGER trg_preguntas_bi
BEFORE INSERT ON Preguntas
FOR EACH ROW
WHEN (NEW.idPregunta IS NULL)
BEGIN
    SELECT seq_preguntas.NEXTVAL INTO :NEW.idPregunta FROM dual;
END;
/

CREATE OR REPLACE TRIGGER trg_opciones_bi
BEFORE INSERT ON Opciones
FOR EACH ROW
WHEN (NEW.id_opcion IS NULL)
BEGIN
    SELECT seq_opciones.NEXTVAL INTO :NEW.id_opcion FROM dual;
END;
/

CREATE OR REPLACE TRIGGER trg_monto_inscripcion_bi
BEFORE INSERT ON Monto_Inscripcion
FOR EACH ROW
WHEN (NEW.id_monto_inscripcion IS NULL)
BEGIN
    SELECT seq_monto_inscripcion.NEXTVAL INTO :NEW.id_monto_inscripcion FROM dual;
END;
/

CREATE OR REPLACE TRIGGER trg_monto_reinscripcion_bi
BEFORE INSERT ON Monto_Reinscripcion
FOR EACH ROW
WHEN (NEW.id_monto_reinscripcion IS NULL)
BEGIN
    SELECT seq_monto_reinscripcion.NEXTVAL INTO :NEW.id_monto_reinscripcion FROM dual;
END;
/

CREATE OR REPLACE TRIGGER trg_solicitudes_bi
BEFORE INSERT ON Solicitudes
FOR EACH ROW
WHEN (NEW.idSolicitud IS NULL)
BEGIN
    SELECT seq_solicitudes.NEXTVAL INTO :NEW.idSolicitud FROM dual;
END;
/

CREATE OR REPLACE TRIGGER trg_respuestas_bi
BEFORE INSERT ON Respuestas
FOR EACH ROW
WHEN (NEW.id_respuesta IS NULL)
BEGIN
    SELECT seq_respuestas.NEXTVAL INTO :NEW.id_respuesta FROM dual;
END;
/

CREATE OR REPLACE TRIGGER trg_hist_solicitudes_bi
BEFORE INSERT ON Historial_Solicitudes
FOR EACH ROW
WHEN (NEW.id_historial IS NULL)
BEGIN
    SELECT seq_historial_solicitudes.NEXTVAL INTO :NEW.id_historial FROM dual;
END;
/

CREATE OR REPLACE TRIGGER trg_deducciones_bi
BEFORE INSERT ON Deducciones_autorizadas
FOR EACH ROW
WHEN (NEW.id_deduccion_autorizada IS NULL)
BEGIN
    SELECT seq_deducciones_autorizadas.NEXTVAL INTO :NEW.id_deduccion_autorizada FROM dual;
END;
/

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

CREATE OR REPLACE TRIGGER trg_solicitudes_validacion
BEFORE INSERT OR UPDATE ON Solicitudes
FOR EACH ROW
DECLARE
    v_tipo_formulario Formularios.id_Tipo_Solicitud%TYPE;
BEGIN
    -- Un formulario debe pertenecer al mismo tipo de solicitud indicado
    -- en la solicitud.
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

    :NEW.fecha_actualizacion := SYSDATE;
EXCEPTION
    WHEN NO_DATA_FOUND THEN
        RAISE_APPLICATION_ERROR(
            -20002,
            'El formulario indicado no existe.'
        );
END;
/

-- ================================================================
-- TRIGGER: VALIDACIÓN DE RESPUESTAS
-- ================================================================

CREATE OR REPLACE TRIGGER trg_respuestas_validacion
BEFORE INSERT OR UPDATE ON Respuestas
FOR EACH ROW
DECLARE
    v_tipo_pregunta Preguntas.tipo_pregunta%TYPE;
BEGIN
    SELECT tipo_pregunta
      INTO v_tipo_pregunta
      FROM Preguntas
     WHERE idPregunta = :NEW.id_pregunta;

    /*
       Tipos establecidos inicialmente:
       1 = ABIERTA       -> texto requerido, opción nula
       2 = OPCION_UNICA  -> opción requerida, texto nulo
       3 = MIXTA         -> opción requerida y texto opcional
       4 = MULTIPLE      -> opción requerida, texto nulo

       El catálogo puede conservar estos IDs o modificarse, pero las reglas
       de este trigger deben mantenerse alineadas con los IDs del catálogo.
    */
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

    ELSIF v_tipo_pregunta = 4 THEN
        IF :NEW.id_opcion IS NULL OR :NEW.texto_respuesta IS NOT NULL THEN
            RAISE_APPLICATION_ERROR(
                -20013,
                'Una pregunta MULTIPLE requiere una opción y no permite texto adicional.'
            );
        END IF;
    END IF;

    -- Verifica que la opción realmente pertenezca a la pregunta.
    IF :NEW.id_opcion IS NOT NULL THEN
        DECLARE
            v_pregunta_opcion Opciones.id_pregunta%TYPE;
        BEGIN
            SELECT id_pregunta
              INTO v_pregunta_opcion
              FROM Opciones
             WHERE id_opcion = :NEW.id_opcion;

            IF v_pregunta_opcion <> :NEW.id_pregunta THEN
                RAISE_APPLICATION_ERROR(
                    -20014,
                    'La opción seleccionada no pertenece a la pregunta.'
                );
            END IF;
        EXCEPTION
            WHEN NO_DATA_FOUND THEN
                RAISE_APPLICATION_ERROR(
                    -20015,
                    'La opción indicada no existe.'
                );
        END;
    END IF;
END;
/

-- ================================================================
-- TRIGGER: CÁLCULO Y VALIDACIÓN DE DEDUCCIÓN
-- Copia los montos vigentes al momento de autorizar la deducción.
-- Así, cambios posteriores en los catálogos no alteran el histórico.
-- ================================================================

CREATE OR REPLACE TRIGGER trg_deducciones_calculo
BEFORE INSERT OR UPDATE ON Deducciones_autorizadas
FOR EACH ROW
DECLARE
    v_num_control       Solicitudes.num_control%TYPE;
    v_ficha             Solicitudes.ficha%TYPE;
    v_periodo           Solicitudes.periodo%TYPE;
    v_anio              Solicitudes.anio%TYPE;
    v_convocatoria      Solicitudes.id_convocatoria%TYPE;
    v_tipo_solicitud    Solicitudes.id_Tipo_Solicitud%TYPE;
    v_monto_pago        NUMBER(10,2);
    v_monto_deduccion   NUMBER(10,2);
BEGIN
    SELECT num_control, ficha, periodo, anio, id_convocatoria, id_Tipo_Solicitud
      INTO v_num_control, v_ficha, v_periodo, v_anio, v_convocatoria, v_tipo_solicitud
      FROM Solicitudes
     WHERE idSolicitud = :NEW.id_solicitud;

    -- La deducción siempre debe corresponder a la solicitud.
    :NEW.id_convocatoria   := v_convocatoria;
    :NEW.id_Tipo_Solicitud := v_tipo_solicitud;

    -- El monto de pago se obtiene del catálogo correspondiente.
    IF v_num_control IS NOT NULL THEN
        IF :NEW.id_monto_reinscripcion IS NULL OR :NEW.id_monto_inscripcion IS NOT NULL THEN
            RAISE_APPLICATION_ERROR(
                -20020,
                'Una solicitud de estudiante debe utilizar un monto de reinscripción.'
            );
        END IF;

        SELECT monto_pago
          INTO v_monto_pago
          FROM Monto_Reinscripcion
         WHERE id_monto_reinscripcion = :NEW.id_monto_reinscripcion;
    ELSE
        IF :NEW.id_monto_inscripcion IS NULL OR :NEW.id_monto_reinscripcion IS NOT NULL THEN
            RAISE_APPLICATION_ERROR(
                -20021,
                'Una solicitud de aspirante debe utilizar un monto de inscripción.'
            );
        END IF;

        SELECT monto_pago
          INTO v_monto_pago
          FROM Monto_Inscripcion
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

EXCEPTION
    WHEN NO_DATA_FOUND THEN
        RAISE_APPLICATION_ERROR(
            -20022,
            'No existe el monto o la configuración de deducción correspondiente a la solicitud.'
        );
END;
/

-- ================================================================
-- TRIGGER: HISTORIAL DE SOLICITUDES
-- Registra el nuevo estatus cuando cambia.
-- ================================================================

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
-- Registra el nuevo estatus cuando cambia.
-- ================================================================

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
-- TRIGGER: CANCELACIÓN DE DEDUCCIÓN
-- Si una deducción pasa a CANCELADO (4), se propaga a la solicitud.
-- ================================================================

CREATE OR REPLACE TRIGGER trg_deduccion_cancelacion
AFTER UPDATE OF estatus_deduccion ON Deducciones_autorizadas
FOR EACH ROW
WHEN (OLD.estatus_deduccion <> NEW.estatus_deduccion AND NEW.estatus_deduccion = 4)
DECLARE
    v_id_historial Historial_Solicitudes.id_historial%TYPE;
BEGIN
    UPDATE Solicitudes
       SET id_estatus_solicitud = 4,
           fecha_actualizacion = SYSDATE
     WHERE idSolicitud = :NEW.id_solicitud;

    -- El trigger de Solicitudes ya creó el registro de historial.
    -- Se completan los datos que provienen de la cancelación de la deducción.
    SELECT MAX(id_historial)
      INTO v_id_historial
      FROM Historial_Solicitudes
     WHERE id_solicitud = :NEW.id_solicitud
       AND id_estatus = 4;

    UPDATE Historial_Solicitudes
       SET id_administrador = :NEW.id_administrador,
           comentario = :NEW.comentario
     WHERE id_historial = v_id_historial;
END;
/

-- ================================================================
-- TRIGGER: FECHA DE ACTUALIZACIÓN DE DEDUCCIÓN
-- ================================================================

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
INSERT INTO Tipo_Preguntas (id_Tipo_Pregunta, Nombre)
VALUES (4, 'MULTIPLE');

COMMIT;

-- ================================================================
-- FIN DEL SCRIPT
-- ================================================================
