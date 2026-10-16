-- ============================================================================
-- 01-configurar-esquema.sql
-- Proyecto: SAReduccion - Oracle XE 21c
-- Objetivo: Crear/configurar el usuario SA_REDUCCION y otorgar privilegios.
--
-- EJECUCION: SQL*Plus / SQLcl conectado como SYSDBA en la PDB XEPDB1.
-- ============================================================================

SET ECHO OFF
SET SERVEROUTPUT ON
SET VERIFY OFF
WHENEVER SQLERROR EXIT SQL.SQLCODE

-- 1. Comprobar que se ejecuta en la base pluggable y con el usuario correcto.
DECLARE
    v_contenedor VARCHAR2(128);
    v_usuario    VARCHAR2(128);
BEGIN
    v_contenedor := SYS_CONTEXT('USERENV', 'CON_NAME');
    v_usuario    := SYS_CONTEXT('USERENV', 'SESSION_USER');

    IF v_contenedor <> 'XEPDB1' THEN
        RAISE_APPLICATION_ERROR(-20001,
            'Se requiere XEPDB1; contenedor actual: ' || v_contenedor);
    END IF;

    IF v_usuario <> 'SYS' THEN
        RAISE_APPLICATION_ERROR(-20002,
            'Este script debe ejecutarse como SYSDBA (usuario SYS).');
    END IF;

    DBMS_OUTPUT.PUT_LINE('Validacion de entorno correcta: SYS / XEPDB1.');
END;
/

-- 2. Crear el usuario si no existe, o actualizar su contraseña si ya existe.
DECLARE
    v_existe NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_existe
      FROM DBA_USERS
     WHERE USERNAME = '&&ORACLE_APP_USER';

    IF v_existe = 0 THEN
        EXECUTE IMMEDIATE 'CREATE USER &&ORACLE_APP_USER IDENTIFIED BY "&&ORACLE_APP_PASSWORD" DEFAULT TABLESPACE USERS TEMPORARY TABLESPACE TEMP';
        DBMS_OUTPUT.PUT_LINE('Usuario de aplicacion creado.');
    ELSE
        EXECUTE IMMEDIATE 'ALTER USER &&ORACLE_APP_USER IDENTIFIED BY "&&ORACLE_APP_PASSWORD"';
        DBMS_OUTPUT.PUT_LINE('Usuario de aplicacion existente configurado.');
    END IF;
END;
/

-- 3. Otorgar privilegios solicitados y cuota en USERS.
GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE, CREATE TRIGGER, CREATE PROCEDURE TO &&ORACLE_APP_USER;
ALTER USER &&ORACLE_APP_USER QUOTA UNLIMITED ON USERS;

-- 4. Directorio de Oracle Data Pump para los respaldos logicos del esquema.
-- Se crea como SYS porque CREATE DIRECTORY es un privilegio de instancia.
-- La ruta corresponde al bind mount definido en docker-compose.yml, de modo
-- que expdp/impdp escriben y leen directamente en el equipo anfitrion.
-- Requiere el privilegio de sistema CREATE ANY DIRECTORY (incluido en SYSDBA).
DECLARE
    v_existe NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_existe
      FROM DBA_DIRECTORIES WHERE DIRECTORY_NAME = 'SAR_BACKUP_DIR';

    IF v_existe = 0 THEN
        EXECUTE IMMEDIATE
            'CREATE DIRECTORY SAR_BACKUP_DIR AS ''/opt/sareduccion/backups''';
        DBMS_OUTPUT.PUT_LINE('Directorio de respaldo creado.');
    ELSE
        EXECUTE IMMEDIATE
            'CREATE OR REPLACE DIRECTORY SAR_BACKUP_DIR AS ''/opt/sareduccion/backups''';
        DBMS_OUTPUT.PUT_LINE('Directorio de respaldo configurado.');
    END IF;
END;
/

GRANT READ, WRITE ON DIRECTORY SAR_BACKUP_DIR TO &&ORACLE_APP_USER;

PROMPT Configuracion del esquema completada.

EXIT SUCCESS