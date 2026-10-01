-- ============================================================
-- 01-init-schema.sql
-- Script de inicialización de la base de datos de desarrollo
-- SAReducción – Instituto Tecnológico de Toluca
--
-- Este script se ejecuta AUTOMÁTICAMENTE la primera vez
-- que se levanta el contenedor Oracle.
--
-- Basado en: BaseDeDatos.md
-- ============================================================

-- Conectar al PDB de la aplicación
-- (el usuario APP_USER ya fue creado por la imagen de Docker)

-- ────────────────────────────────────────────────────────────
-- MÓDULO 1: AUTENTICACIÓN E IDENTIDAD
-- ────────────────────────────────────────────────────────────

CREATE TABLE usuarios (
    id_usuario      NUMBER          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    identificador   VARCHAR2(50)    NOT NULL UNIQUE,   -- alu+NC / CURP / ID admin
    password_hash   VARCHAR2(255)   NOT NULL,           -- BCrypt hash
    rol             VARCHAR2(20)    NOT NULL            -- ADMIN | ALUMNO | ASPIRANTE
);

CREATE TABLE alumnos (
    id_alumno   NUMBER          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_usuario  NUMBER          NOT NULL UNIQUE,
    nombre      VARCHAR2(150)   NOT NULL,
    semestre    NUMBER(2)       NOT NULL,
    CONSTRAINT fk_alumno_usuario FOREIGN KEY (id_usuario)
        REFERENCES usuarios(id_usuario)
);

CREATE TABLE aspirantes (
    id_aspirante    NUMBER          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_usuario      NUMBER          NOT NULL UNIQUE,
    nombre          VARCHAR2(150)   NOT NULL,
    CONSTRAINT fk_aspirante_usuario FOREIGN KEY (id_usuario)
        REFERENCES usuarios(id_usuario)
);

-- ────────────────────────────────────────────────────────────
-- MÓDULO 2: GESTIÓN DE SOLICITUDES
-- ────────────────────────────────────────────────────────────

CREATE TABLE estatus_solicitud (
    id_estatus  NUMBER          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre      VARCHAR2(50)    NOT NULL UNIQUE,    -- PENDIENTE | CORRECCION | APROBADO
    descripcion VARCHAR2(255)
);

-- Catálogo inicial de estatus
INSERT INTO estatus_solicitud (nombre, descripcion) VALUES
    ('PENDIENTE',   'Solicitud recibida y en espera de revisión.');
INSERT INTO estatus_solicitud (nombre, descripcion) VALUES
    ('CORRECCION',  'Solicitud devuelta al alumno para corrección.');
INSERT INTO estatus_solicitud (nombre, descripcion) VALUES
    ('APROBADO',    'Solicitud aprobada por la Subdirección Académica.');
INSERT INTO estatus_solicitud (nombre, descripcion) VALUES
    ('RECHAZADO',   'Solicitud rechazada definitivamente.');

CREATE TABLE solicitudes (
    id_solicitud        NUMBER          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_usuario          NUMBER          NOT NULL,
    id_estatus          NUMBER          NOT NULL,
    fecha_solicitud     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP NOT NULL,
    fecha_actualizacion TIMESTAMP,
    CONSTRAINT fk_solicitud_usuario FOREIGN KEY (id_usuario)
        REFERENCES usuarios(id_usuario),
    CONSTRAINT fk_solicitud_estatus FOREIGN KEY (id_estatus)
        REFERENCES estatus_solicitud(id_estatus)
);

CREATE TABLE historial_estatus_solicitud (
    id_historial        NUMBER          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_solicitud        NUMBER          NOT NULL,
    id_estatus_nuevo    NUMBER          NOT NULL,
    id_administrador    NUMBER,                         -- NULL si lo hace el alumno
    comentario          CLOB,
    fecha               TIMESTAMP       DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_historial_solicitud FOREIGN KEY (id_solicitud)
        REFERENCES solicitudes(id_solicitud),
    CONSTRAINT fk_historial_estatus   FOREIGN KEY (id_estatus_nuevo)
        REFERENCES estatus_solicitud(id_estatus),
    CONSTRAINT fk_historial_admin     FOREIGN KEY (id_administrador)
        REFERENCES usuarios(id_usuario)
);

-- ────────────────────────────────────────────────────────────
-- MÓDULO 3: FORMULARIOS DINÁMICOS
-- ────────────────────────────────────────────────────────────

CREATE TABLE formularios (
    id_formulario   NUMBER          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre          VARCHAR2(150)   NOT NULL,
    descripcion     CLOB,
    activo          NUMBER(1)       DEFAULT 1 NOT NULL   -- 1=activo, 0=inactivo
);

CREATE TABLE preguntas (
    id_pregunta     NUMBER          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_formulario   NUMBER          NOT NULL,
    pregunta        CLOB            NOT NULL,
    tipo            VARCHAR2(30)    NOT NULL,            -- TEXT | NUMBER | SELECT
    orden           NUMBER(3)       DEFAULT 1,
    CONSTRAINT fk_pregunta_formulario FOREIGN KEY (id_formulario)
        REFERENCES formularios(id_formulario)
);

CREATE TABLE respuestas (
    id_respuesta    NUMBER          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_solicitud    NUMBER          NOT NULL,
    id_pregunta     NUMBER          NOT NULL,
    respuesta       CLOB,
    fecha_respuesta TIMESTAMP       DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_respuesta_solicitud FOREIGN KEY (id_solicitud)
        REFERENCES solicitudes(id_solicitud),
    CONSTRAINT fk_respuesta_pregunta  FOREIGN KEY (id_pregunta)
        REFERENCES preguntas(id_pregunta)
);

-- Índices para mejorar consultas frecuentes
CREATE INDEX idx_solicitudes_usuario  ON solicitudes(id_usuario);
CREATE INDEX idx_solicitudes_estatus  ON solicitudes(id_estatus);
CREATE INDEX idx_historial_solicitud  ON historial_estatus_solicitud(id_solicitud);
CREATE INDEX idx_respuestas_solicitud ON respuestas(id_solicitud);

COMMIT;
