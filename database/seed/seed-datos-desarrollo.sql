-- Datos ficticios para desarrollo local. No crea cuentas Oracle ni modifica filas existentes.
-- Ejecutar conectado como SA_REDUCCION en XEPDB1.
SET DEFINE OFF
SET SERVEROUTPUT ON
WHENEVER OSERROR EXIT FAILURE ROLLBACK
WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK

BEGIN
  IF SYS_CONTEXT('USERENV', 'SESSION_USER') <> 'SA_REDUCCION'
     OR SYS_CONTEXT('USERENV', 'CON_NAME') <> 'XEPDB1' THEN
    RAISE_APPLICATION_ERROR(-20960, 'Conectarse como SA_REDUCCION a XEPDB1');
  END IF;
END;
/

-- El perfil de la prueba JDBC existente PRUEBA_JDBC_20261014 se conserva sin cambios.
INSERT INTO SA_REDUCCION.Estudiantes (Numero_Control, Nombre, PrimerApellido, SegundoApellido, Carrera, Semestre)
SELECT '21281181', 'Sofia', 'López', 'Hernández', 'Ingenieria en Sistemas Computacionales', 5 FROM dual
 WHERE NOT EXISTS (SELECT 1 FROM SA_REDUCCION.Estudiantes WHERE Numero_Control = '21281181');

INSERT INTO SA_REDUCCION.Estudiantes (Numero_Control, Nombre, PrimerApellido, SegundoApellido, Carrera, Semestre)
SELECT 'C21281182', 'Juan', 'Hernandez', 'Cruz', 'Ingenieria Industrial', 6 FROM dual
 WHERE NOT EXISTS (SELECT 1 FROM SA_REDUCCION.Estudiantes WHERE Numero_Control = 'C21281182');

INSERT INTO SA_REDUCCION.Estudiantes (Numero_Control, Nombre, PrimerApellido, SegundoApellido, Carrera, Semestre)
SELECT 'B21281183', 'Valeria', 'Lopez', 'Mendoza', 'Ingenieria en Tecnologias de la Informacion', 4 FROM dual
 WHERE NOT EXISTS (SELECT 1 FROM SA_REDUCCION.Estudiantes WHERE Numero_Control = 'B21281183');

INSERT INTO SA_REDUCCION.Estudiantes (Numero_Control, Nombre, PrimerApellido, SegundoApellido, Carrera, Semestre)
SELECT 'BC21281184', 'Carlos Eduardo', 'Ramirez', 'Torres', 'Ingenieria Electromecanica', 8 FROM dual
 WHERE NOT EXISTS (SELECT 1 FROM SA_REDUCCION.Estudiantes WHERE Numero_Control = 'BC21281184');

INSERT INTO SA_REDUCCION.Estudiantes (Numero_Control, Nombre, PrimerApellido, SegundoApellido, Carrera, Semestre)
SELECT '21281185', 'Fernanda', 'Castillo', 'Vega', 'Ingenieria Quimica', 3 FROM dual
 WHERE NOT EXISTS (SELECT 1 FROM SA_REDUCCION.Estudiantes WHERE Numero_Control = '21281185');

INSERT INTO SA_REDUCCION.Estudiantes (Numero_Control, Nombre, PrimerApellido, SegundoApellido, Carrera, Semestre)
SELECT '21281186', 'Miguel Angel', 'Navarro', 'Ruiz', 'Ingenieria Quimica', 7 FROM dual
 WHERE NOT EXISTS (SELECT 1 FROM SA_REDUCCION.Estudiantes WHERE Numero_Control = '21281186');

INSERT INTO SA_REDUCCION.Estudiantes (Numero_Control, Nombre, PrimerApellido, SegundoApellido, Carrera, Semestre)
SELECT '21281187', 'Daniela', 'Ortega', 'Flores', 'Ingenieria en Logistica', 2 FROM dual
 WHERE NOT EXISTS (SELECT 1 FROM SA_REDUCCION.Estudiantes WHERE Numero_Control = '21281187');

INSERT INTO SA_REDUCCION.Estudiantes (Numero_Control, Nombre, PrimerApellido, SegundoApellido, Carrera, Semestre)
SELECT '21281188', 'Jorge Luis', 'Sanchez', 'Reyes', 'Ingenieria en Gestion Empresarial', 9 FROM dual
 WHERE NOT EXISTS (SELECT 1 FROM SA_REDUCCION.Estudiantes WHERE Numero_Control = '21281188');

INSERT INTO SA_REDUCCION.Estudiantes (Numero_Control, Nombre, PrimerApellido, SegundoApellido, Carrera, Semestre)
SELECT '21281189', 'Mariana Isabel', 'Cervantes', 'Silva', 'Ingenieria Ambiental', 5 FROM dual
 WHERE NOT EXISTS (SELECT 1 FROM SA_REDUCCION.Estudiantes WHERE Numero_Control = '21281189');

INSERT INTO SA_REDUCCION.Administradores (idAdministrador, Nombre, PrimerApellido, SegundoApellido)
SELECT 'SA01', 'Laura Beatriz', 'Ramirez', 'Ortega' FROM dual
 WHERE NOT EXISTS (SELECT 1 FROM SA_REDUCCION.Administradores WHERE idAdministrador = 'SA01');

INSERT INTO SA_REDUCCION.Administradores (idAdministrador, Nombre, PrimerApellido, SegundoApellido)
SELECT 'SA02', 'Mario Alberto', 'Salgado', 'Perez' FROM dual
 WHERE NOT EXISTS (SELECT 1 FROM SA_REDUCCION.Administradores WHERE idAdministrador = 'SA02');

COMMIT;

PROMPT ===== DATOS DE DESARROLLO APLICADOS =====
SELECT 'ESTUDIANTES_DE_SEED=' || COUNT(*)
  FROM SA_REDUCCION.Estudiantes
 WHERE Numero_Control IN (
   '21281181', 'C21281182', 'B21281183', 'BC21281184',
   '21281185', '21281186', '21281187', '21281188', '21281189'
 );

SELECT 'ADMINISTRADORES_DE_SEED=' || COUNT(*)
  FROM SA_REDUCCION.Administradores
 WHERE idAdministrador IN ('SA01', 'SA02');

PROMPT La ejecucion repetida omite identificadores ya existentes y no los actualiza.
EXIT SUCCESS