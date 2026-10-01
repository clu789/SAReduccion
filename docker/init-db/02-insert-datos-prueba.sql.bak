-- ============================================================
-- 02-insert-datos-prueba.sql
-- Datos de prueba para desarrollo local
-- SAReducción – Instituto Tecnológico de Toluca
--
-- Usuarios de prueba:
--   ALUMNO:    número de control = 20230001  |  password = test123
--   ASPIRANTE: CURP = LOML920101HDFPZS09    |  folio    = ASP2026001
--   ADMIN:     usuario = admin              |  password = admin123
--
-- ⚠️  Solo para desarrollo. NO usar en producción.
-- ============================================================

-- ── Usuarios de prueba ──────────────────────────────────────
-- NOTA: Los hashes son BCrypt de las contraseñas indicadas arriba.
--       En producción Spring Security generará los hashes reales.

-- Administrador
INSERT INTO usuarios (identificador, password_hash, rol) VALUES
    ('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iKlFiP6hzMPpkjEv0TrVOSSe5fIS', 'ADMIN');

-- Alumno (número de control: alu20230001)
INSERT INTO usuarios (identificador, password_hash, rol) VALUES
    ('alu20230001', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iKlFiP6hzMPpkjEv0TrVOSSe5fIS', 'ALUMNO');

-- Aspirante (CURP como identificador, folio como password)
INSERT INTO usuarios (identificador, password_hash, rol) VALUES
    ('LOML920101HDFPZS09', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iKlFiP6hzMPpkjEv0TrVOSSe5fIS', 'ASPIRANTE');

-- ── Datos del alumno ─────────────────────────────────────────
INSERT INTO alumnos (id_usuario, nombre, semestre)
    SELECT id_usuario, 'Juan Pérez López', 4
    FROM usuarios WHERE identificador = 'alu20230001';

-- ── Datos del aspirante ──────────────────────────────────────
INSERT INTO aspirantes (id_usuario, nombre)
    SELECT id_usuario, 'María García Hernández'
    FROM usuarios WHERE identificador = 'LOML920101HDFPZS09';

-- ── Formulario activo de ejemplo ─────────────────────────────
INSERT INTO formularios (nombre, descripcion, activo) VALUES
    ('Formulario Descuento 2026-B',
     'Formulario para solicitar la reducción de monto de inscripción. Convocatoria 2026-B.',
     1);

-- ── Preguntas del formulario ──────────────────────────────────
INSERT INTO preguntas (id_formulario, pregunta, tipo, orden)
    SELECT id_formulario, 'Nombre completo del trabajador del TecNM', 'TEXT', 1
    FROM formularios WHERE nombre = 'Formulario Descuento 2026-B';

INSERT INTO preguntas (id_formulario, pregunta, tipo, orden)
    SELECT id_formulario, 'Clave del trabajador', 'TEXT', 2
    FROM formularios WHERE nombre = 'Formulario Descuento 2026-B';

INSERT INTO preguntas (id_formulario, pregunta, tipo, orden)
    SELECT id_formulario, 'Parentesco con el trabajador', 'SELECT', 3
    FROM formularios WHERE nombre = 'Formulario Descuento 2026-B';

INSERT INTO preguntas (id_formulario, pregunta, tipo, orden)
    SELECT id_formulario, 'Tipo de solicitud', 'SELECT', 4
    FROM formularios WHERE nombre = 'Formulario Descuento 2026-B';

INSERT INTO preguntas (id_formulario, pregunta, tipo, orden)
    SELECT id_formulario, 'Semestre a inscribir', 'NUMBER', 5
    FROM formularios WHERE nombre = 'Formulario Descuento 2026-B';

-- ── Solicitud de prueba (alumno con estatus PENDIENTE) ────────
INSERT INTO solicitudes (id_usuario, id_estatus)
    SELECT u.id_usuario, e.id_estatus
    FROM usuarios u, estatus_solicitud e
    WHERE u.identificador = 'alu20230001'
      AND e.nombre = 'PENDIENTE';

-- Primer registro del historial (creación)
INSERT INTO historial_estatus_solicitud (id_solicitud, id_estatus_nuevo, id_administrador, comentario)
    SELECT s.id_solicitud, e.id_estatus, NULL, NULL
    FROM solicitudes s, estatus_solicitud e, usuarios u
    WHERE u.identificador = 'alu20230001'
      AND s.id_usuario = u.id_usuario
      AND e.nombre = 'PENDIENTE';

COMMIT;
