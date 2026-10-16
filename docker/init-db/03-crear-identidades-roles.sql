-- ============================================================================
-- 03-crear-identidades-roles.sql
-- Proyecto: SAReduccion / Oracle XE 21c
--
-- OBJETIVO
--   Crear roles y cuentas Oracle individuales.
--
-- EJECUCION: SQL*Plus o SQLcl, conectado como SYSDBA en XEPDB1.
--   Ejecutar DESPUES de 02-crear-estructura.sql y ANTES de
--   05-crear-paquetes-permisos.sql.
--
-- SEGURIDAD Y REPETIBILIDAD
--   * No contiene DROP USER, DROP ROLE, TRUNCATE ni DELETE.
--   * Si una cuenta existe, NO cambia su contrasena ni su estado.
--   * Si un rol existe, se conserva y se aseguran los GRANT necesarios.
--   * NO concede acceso directo a las tablas de SA_REDUCCION.
--   * Los permisos EXECUTE a los paquetes se otorgan en 05-crear-paquetes-permisos.sql.
--   * Las cuentas y claves se reciben como variables SQL*Plus desde init-db.sh.
--   * La cuenta tecnica SAR_BACKEND se crea en 04-crear-cuenta-tecnica.sql.
--
-- IMPORTANTE: los usuarios Oracle no equivalen a los registros de las tablas
-- ESTUDIANTES y ADMINISTRADORES. Este script NO inserta filas de prueba.
-- ============================================================================

SET ECHO OFF
SET VERIFY OFF
SET FEEDBACK ON
SET SERVEROUTPUT ON SIZE UNLIMITED
WHENEVER OSERROR EXIT FAILURE
WHENEVER SQLERROR EXIT SQL.SQLCODE

PROMPT ===== 03: IDENTIDADES Y ROLES ORACLE =====

-- 1. Verificar la sesion privilegiada, la PDB y el esquema del proyecto.
DECLARE
  v_existe NUMBER;
BEGIN
  IF SYS_CONTEXT('USERENV', 'SESSION_USER') <> 'SYS' THEN
    RAISE_APPLICATION_ERROR(-20301, 'Ejecutar como SYSDBA (SYS).');
  END IF;
  IF SYS_CONTEXT('USERENV', 'CON_NAME') <> 'XEPDB1' THEN
    RAISE_APPLICATION_ERROR(-20302, 'Ejecutar dentro de XEPDB1.');
  END IF;
  SELECT COUNT(*) INTO v_existe
    FROM DBA_USERS WHERE USERNAME = '&&ORACLE_APP_USER';
  IF v_existe <> 1 THEN
    RAISE_APPLICATION_ERROR(-20303, 'No existe el esquema de aplicacion.');
  END IF;
  DBMS_OUTPUT.PUT_LINE('OK: SYS / XEPDB1 / SA_REDUCCION.');
END;
/

-- 2. Crear los roles solamente cuando no existan.
DECLARE
  v_existe NUMBER;
BEGIN
  SELECT COUNT(*) INTO v_existe FROM DBA_ROLES
   WHERE ROLE = 'ROL_SAR_ESTUDIANTE_SIM';
  IF v_existe = 0 THEN
    EXECUTE IMMEDIATE 'CREATE ROLE ROL_SAR_ESTUDIANTE_SIM';
    DBMS_OUTPUT.PUT_LINE('Creado: ROL_SAR_ESTUDIANTE_SIM');
  ELSE
    DBMS_OUTPUT.PUT_LINE('Conservado: ROL_SAR_ESTUDIANTE_SIM');
  END IF;

  SELECT COUNT(*) INTO v_existe FROM DBA_ROLES
   WHERE ROLE = 'ROL_SAR_ADMIN_SIM';
  IF v_existe = 0 THEN
    EXECUTE IMMEDIATE 'CREATE ROLE ROL_SAR_ADMIN_SIM';
    DBMS_OUTPUT.PUT_LINE('Creado: ROL_SAR_ADMIN_SIM');
  ELSE
    DBMS_OUTPUT.PUT_LINE('Conservado: ROL_SAR_ADMIN_SIM');
  END IF;
END;
/

-- 3. Permitir que los titulares de estos roles inicien sesion.
-- Repetir GRANT es seguro; no revoca privilegios existentes.
GRANT CREATE SESSION TO ROL_SAR_ESTUDIANTE_SIM;
GRANT CREATE SESSION TO ROL_SAR_ADMIN_SIM;

-- 4. Crear las cuentas solo cuando no existan. Las claves llegan desde .env.
DECLARE
  n NUMBER;
BEGIN
  SELECT COUNT(*) INTO n FROM DBA_USERS WHERE USERNAME = '&&ORACLE_ALUMNO_USER';
  IF n = 0 THEN
    EXECUTE IMMEDIATE 'CREATE USER &&ORACLE_ALUMNO_USER IDENTIFIED BY "&&ORACLE_ALUMNO_PASSWORD"';
    DBMS_OUTPUT.PUT_LINE('Cuenta de estudiante creada.');
  END IF;

  SELECT COUNT(*) INTO n FROM DBA_USERS WHERE USERNAME = '&&ORACLE_ADMIN_USER';
  IF n = 0 THEN
    EXECUTE IMMEDIATE 'CREATE USER &&ORACLE_ADMIN_USER IDENTIFIED BY "&&ORACLE_ADMIN_PASSWORD"';
    DBMS_OUTPUT.PUT_LINE('Cuenta de administrador creada.');
  END IF;

END;
/

-- 5. Asociar cada cuenta con su rol correspondiente.
-- DEFAULT ROLE activa el rol en las sesiones normales del usuario.
GRANT ROL_SAR_ESTUDIANTE_SIM TO &&ORACLE_ALUMNO_USER;
GRANT ROL_SAR_ADMIN_SIM TO &&ORACLE_ADMIN_USER;
ALTER USER &&ORACLE_ALUMNO_USER DEFAULT ROLE ROL_SAR_ESTUDIANTE_SIM;
ALTER USER &&ORACLE_ADMIN_USER DEFAULT ROLE ROL_SAR_ADMIN_SIM;

-- 6. Verificacion de cuentas, roles y permisos de inicio de sesion.
COLUMN USERNAME FORMAT A20
COLUMN ACCOUNT_STATUS FORMAT A20
SELECT USERNAME, ACCOUNT_STATUS FROM DBA_USERS
 WHERE USERNAME IN ('&&ORACLE_ALUMNO_USER', '&&ORACLE_ADMIN_USER')
 ORDER BY USERNAME;

COLUMN GRANTEE FORMAT A20
COLUMN GRANTED_ROLE FORMAT A28
SELECT GRANTEE, GRANTED_ROLE FROM DBA_ROLE_PRIVS
 WHERE GRANTEE IN ('&&ORACLE_ALUMNO_USER', '&&ORACLE_ADMIN_USER')
 ORDER BY GRANTEE, GRANTED_ROLE;

SELECT GRANTEE, PRIVILEGE FROM DBA_SYS_PRIVS
 WHERE GRANTEE IN ('ROL_SAR_ESTUDIANTE_SIM', 'ROL_SAR_ADMIN_SIM')
   AND PRIVILEGE = 'CREATE SESSION'
 ORDER BY GRANTEE;

PROMPT Script 03 finalizado. Siguiente: 04-crear-cuenta-tecnica.sql.
EXIT SUCCESS
