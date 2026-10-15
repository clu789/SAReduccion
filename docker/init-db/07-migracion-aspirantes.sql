-- ============================================================================
-- 07-migracion-aspirantes.sql
-- Migracion ESTRUCTURAL no destructiva de la tabla Aspirantes en XEPDB1.
--
-- EJECUCION (manual, sobre la base existente):
--   sqlplus SA_REDUCCION/"<clave>"@//localhost:1521/XEPDB1 @07-migracion-aspirantes.sql
--
-- REQUISITO PREVIO: ejecutar antes 08-correo-datos-desarrollo.sql para que
-- todos los correos existentes sean validos y no nulos. Este script aborta si
-- detecta correos nulos/invalidos o anios fuera de rango, antes de tocar nada.
--
-- Hace, en orden:
--   1. Elimina la columna obsoleta CLAVECONTROL (solo si aun existe).
--   2. Alinea ck_aspirantes_anio al rango 2000-2200 del esquema actual.
--   3. Hace correo obligatorio (NOT NULL).
--   4. Agrega ck_aspirantes_correo (dominios permitidos).
-- No elimina filas, no cambia la clave primaria (ficha, periodo, anio),
-- no toca indices de Solicitudes ni restaura restricciones UNIQUE antiguas.
-- ============================================================================
SET ECHO OFF
SET VERIFY OFF
SET SERVEROUTPUT ON
WHENEVER OSERROR EXIT FAILURE
WHENEVER SQLERROR EXIT SQL.SQLCODE

DECLARE
  n NUMBER;
BEGIN
  IF SYS_CONTEXT('USERENV','SESSION_USER') <> 'SA_REDUCCION'
     OR SYS_CONTEXT('USERENV','CON_NAME') <> 'XEPDB1' THEN
    RAISE_APPLICATION_ERROR(-20970,'Ejecutar como SA_REDUCCION en XEPDB1');
  END IF;

  -- 1) Verificaciones previas: no imprimir valores, solo conteos.
  SELECT COUNT(*) INTO n FROM Aspirantes
   WHERE correo IS NULL
      OR NOT REGEXP_LIKE(correo,
           '^[A-Za-z0-9_%+-]+(\.[A-Za-z0-9_%+-]+)*@(yahoo\.com\.mx|gmail\.com|hotmail\.com|outlook\.com|live\.com|yahoo\.com|icloud\.com)$','i');
  IF n <> 0 THEN
    RAISE_APPLICATION_ERROR(-20971,'Existen correos nulos o invalidos; ejecute 08-correo-datos-desarrollo.sql primero');
  END IF;

  SELECT COUNT(*) INTO n FROM Aspirantes WHERE anio NOT BETWEEN 2000 AND 2200;
  IF n <> 0 THEN
    RAISE_APPLICATION_ERROR(-20972,'Existen anios fuera del rango 2000-2200');
  END IF;

  -- 2) Eliminar CLAVECONTROL solo si aun existe (verificado sin dependencias).
  SELECT COUNT(*) INTO n FROM user_tab_columns
   WHERE table_name='ASPIRANTES' AND column_name='CLAVECONTROL';
  IF n > 0 THEN
    EXECUTE IMMEDIATE 'ALTER TABLE Aspirantes DROP COLUMN claveControl';
    DBMS_OUTPUT.PUT_LINE('CLAVECONTROL eliminada');
  ELSE
    DBMS_OUTPUT.PUT_LINE('CLAVECONTROL ya no existia');
  END IF;

  -- 3) Alinear rango de anio a 2000-2200.
  SELECT COUNT(*) INTO n FROM user_constraints
   WHERE table_name='ASPIRANTES' AND constraint_name='CK_ASPIRANTES_ANIO';
  IF n > 0 THEN
    EXECUTE IMMEDIATE 'ALTER TABLE Aspirantes DROP CONSTRAINT ck_aspirantes_anio';
  END IF;
  EXECUTE IMMEDIATE 'ALTER TABLE Aspirantes ADD CONSTRAINT ck_aspirantes_anio CHECK (anio BETWEEN 2000 AND 2200)';
  DBMS_OUTPUT.PUT_LINE('ck_aspirantes_anio alineada a 2000-2200');

  -- 4) Correo obligatorio.
  EXECUTE IMMEDIATE 'ALTER TABLE Aspirantes MODIFY (correo NOT NULL)';
  DBMS_OUTPUT.PUT_LINE('correo NOT NULL aplicado');

  -- 5) Restriccion de dominio de correo (idempotente).
  SELECT COUNT(*) INTO n FROM user_constraints
   WHERE table_name='ASPIRANTES' AND constraint_name='CK_ASPIRANTES_CORREO';
  IF n = 0 THEN
    EXECUTE IMMEDIATE 'ALTER TABLE Aspirantes ADD CONSTRAINT ck_aspirantes_correo CHECK (REGEXP_LIKE(correo,''^[A-Za-z0-9_%+-]+(\.[A-Za-z0-9_%+-]+)*@(yahoo\.com\.mx|gmail\.com|hotmail\.com|outlook\.com|live\.com|yahoo\.com|icloud\.com)$'',''i''))';
    DBMS_OUTPUT.PUT_LINE('ck_aspirantes_correo agregada');
  ELSE
    DBMS_OUTPUT.PUT_LINE('ck_aspirantes_correo ya existia');
  END IF;

  DBMS_OUTPUT.PUT_LINE('OK: migracion estructural de Aspirantes completada');
END;
/

PROMPT ===== ESTADO FINAL DE Aspirantes =====
COLUMN column_name FORMAT A20
SELECT column_name, data_type, data_length, nullable FROM user_tab_columns
 WHERE table_name='ASPIRANTES' ORDER BY column_id;
COLUMN constraint_name FORMAT A28
SELECT constraint_name, constraint_type, status FROM user_constraints
 WHERE table_name='ASPIRANTES' ORDER BY constraint_name;
EXIT SUCCESS
