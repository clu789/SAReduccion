-- ============================================================================
-- 08-correo-datos-desarrollo.sql
-- Actualizacion EXPLICITA de los correos ficticios de desarrollo de Aspirantes.
-- NO es una migracion estructural; son datos controlados de prueba.
--
-- EJECUCION (manual, sobre la base existente, ANTES de 07-migracion-aspirantes.sql):
--   sqlplus SA_REDUCCION/"<clave>"@//localhost:1521/XEPDB1 @08-correo-datos-desarrollo.sql
--
-- Solo afecta los 8 registros ficticios del seed (anio 2026). No borra filas,
-- no cambia claves ni crea cuentas. Los dominios elegidos cubren los siete
-- permitidos por ck_aspirantes_correo para poder probarlos.
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
    RAISE_APPLICATION_ERROR(-20880,'Ejecutar como SA_REDUCCION en XEPDB1');
  END IF;

  UPDATE Aspirantes SET correo = 'andrea.morales.demo@gmail.com'
   WHERE ficha='F2026A001' AND periodo=2 AND anio=2026;
  UPDATE Aspirantes SET correo = 'emilio.rojas.demo@hotmail.com'
   WHERE ficha='F2026A002' AND periodo=2 AND anio=2026;
  UPDATE Aspirantes SET correo = 'paola.vargas.demo@outlook.com'
   WHERE ficha='F2026A003' AND periodo=2 AND anio=2026;
  UPDATE Aspirantes SET correo = 'diego.fuentes.demo@live.com'
   WHERE ficha='F2026B004' AND periodo=1 AND anio=2026;
  UPDATE Aspirantes SET correo = 'lucia.mendoza.demo@yahoo.com.mx'
   WHERE ficha='F2026B005' AND periodo=1 AND anio=2026;
  UPDATE Aspirantes SET correo = 'mateo.serrano.demo@yahoo.com'
   WHERE ficha='F2026B006' AND periodo=1 AND anio=2026;
  UPDATE Aspirantes SET correo = 'renata.aguilar.demo@icloud.com'
   WHERE ficha='F2026B007' AND periodo=1 AND anio=2026;
  UPDATE Aspirantes SET correo = 'hector.campos.demo@gmail.com'
   WHERE ficha='F2026B008' AND periodo=1 AND anio=2026;

  COMMIT;

  SELECT COUNT(*) INTO n FROM Aspirantes
   WHERE correo IS NULL
      OR NOT REGEXP_LIKE(correo,
           '^[A-Za-z0-9_%+-]+(\.[A-Za-z0-9_%+-]+)*@(yahoo\.com\.mx|gmail\.com|hotmail\.com|outlook\.com|live\.com|yahoo\.com|icloud\.com)$','i');
  IF n <> 0 THEN
    RAISE_APPLICATION_ERROR(-20881,'Quedaron correos invalidos tras la actualizacion');
  END IF;
  DBMS_OUTPUT.PUT_LINE('OK: correos de desarrollo actualizados y validados');
END;
/
EXIT SUCCESS
