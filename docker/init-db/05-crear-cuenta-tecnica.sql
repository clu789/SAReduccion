-- ============================================================================
-- 05-crear-cuenta-tecnica.sql
-- Migracion no destructiva: crear/verificar SAR_BACKEND en XEPDB1.
-- Ejecutar como SYSDBA con ORACLE_BACKEND_USER y ORACLE_BACKEND_PASSWORD
-- definidos en SQL*Plus. La cuenta existente nunca cambia de contrasena.
-- ============================================================================
SET ECHO OFF
SET VERIFY OFF
WHENEVER OSERROR EXIT FAILURE
WHENEVER SQLERROR EXIT SQL.SQLCODE

DECLARE
  n NUMBER;
BEGIN
  IF SYS_CONTEXT('USERENV', 'SESSION_USER') <> 'SYS'
     OR SYS_CONTEXT('USERENV', 'CON_NAME') <> 'XEPDB1' THEN
    RAISE_APPLICATION_ERROR(-20981, 'Ejecutar como SYSDBA conectado a XEPDB1');
  END IF;

  SELECT COUNT(*) INTO n FROM DBA_USERS WHERE USERNAME = '&&ORACLE_BACKEND_USER';
  IF n = 0 THEN
    EXECUTE IMMEDIATE 'CREATE USER &&ORACLE_BACKEND_USER IDENTIFIED BY "&&ORACLE_BACKEND_PASSWORD"';
  END IF;

  SELECT COUNT(*) INTO n FROM DBA_ROLE_PRIVS WHERE GRANTEE = '&&ORACLE_BACKEND_USER';
  IF n <> 0 THEN
    RAISE_APPLICATION_ERROR(-20982, 'La cuenta tecnica tiene roles que requieren revision manual');
  END IF;

  SELECT COUNT(*) INTO n FROM DBA_SYS_PRIVS
   WHERE GRANTEE = '&&ORACLE_BACKEND_USER' AND PRIVILEGE <> 'CREATE SESSION';
  IF n <> 0 THEN
    RAISE_APPLICATION_ERROR(-20983, 'La cuenta tecnica tiene privilegios de sistema adicionales');
  END IF;

  SELECT COUNT(*) INTO n FROM DBA_TAB_PRIVS
   WHERE GRANTEE = '&&ORACLE_BACKEND_USER'
     AND NOT (OWNER = 'SA_REDUCCION'
              AND TABLE_NAME IN ('PKG_ESTUDIANTE', 'PKG_ADMINISTRADOR')
              AND PRIVILEGE = 'EXECUTE');
  IF n <> 0 THEN
    RAISE_APPLICATION_ERROR(-20984, 'La cuenta tecnica tiene grants de objeto que requieren revision manual');
  END IF;
END;
/

GRANT CREATE SESSION TO &&ORACLE_BACKEND_USER;
PROMPT Cuenta tecnica creada/verificada; no se alteraron tablas ni datos.
EXIT SUCCESS
