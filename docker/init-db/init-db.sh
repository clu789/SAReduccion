#!/usr/bin/env bash
set -Eeuo pipefail
set +x

readonly init_dir=/opt/sareduccion/init-db
readonly oracle_pdb=${SAR_ORACLE_DATABASE:-XEPDB1}

required_variables=(
  ORACLE_PASSWORD
  ORACLE_APP_USER
  ORACLE_APP_PASSWORD
  ORACLE_BACKEND_USER
  ORACLE_BACKEND_PASSWORD
  ORACLE_ALUMNO_USER
  ORACLE_ALUMNO_PASSWORD
  ORACLE_ADMIN_USER
  ORACLE_ADMIN_PASSWORD
)

for variable in "${required_variables[@]}"; do
  if [[ -z "${!variable:-}" ]]; then
    printf 'Falta la variable requerida: %s\n' "$variable" >&2
    exit 1
  fi
done

if [[ "$oracle_pdb" != XEPDB1 ]]; then
  printf 'Oracle XE 21 usa el PDB existente XEPDB1; ORACLE_DATABASE debe ser XEPDB1.\n' >&2
  exit 1
fi

for variable in ORACLE_APP_USER ORACLE_BACKEND_USER ORACLE_ALUMNO_USER ORACLE_ADMIN_USER; do
  if [[ ! "${!variable}" =~ ^[A-Za-z][A-Za-z0-9_\$#]{0,29}$ ]]; then
    printf 'Nombre de usuario no valido en %s.\n' "$variable" >&2
    exit 1
  fi
done

if [[ "$ORACLE_APP_USER" != SA_REDUCCION || "$ORACLE_BACKEND_USER" != SAR_BACKEND || "$ORACLE_ALUMNO_USER" != ALU_21281181 || "$ORACLE_ADMIN_USER" != SA01 ]]; then
  printf 'Usa SA_REDUCCION, SAR_BACKEND, ALU_21281181 y SA01 para los usuarios Oracle.\n' >&2
  exit 1
fi

for variable in ORACLE_APP_PASSWORD ORACLE_BACKEND_PASSWORD ORACLE_ALUMNO_PASSWORD ORACLE_ADMIN_PASSWORD; do
  password_value=${!variable}
  if [[ ${#password_value} -lt 8 || ! "$password_value" =~ ^[A-Za-z0-9_!%+=.-]+$ ]]; then
    printf 'La clave de %s contiene caracteres no compatibles con SQL*Plus.\n' "$variable" >&2
    exit 1
  fi
done

run_as_sys() {
  local script=$1
  sqlplus -L -s / as sysdba <<SQL
WHENEVER OSERROR EXIT FAILURE
WHENEVER SQLERROR EXIT SQL.SQLCODE
SET ECHO OFF
SET VERIFY OFF
SET DEFINE ON
ALTER SESSION SET CONTAINER = ${oracle_pdb};
DEFINE ORACLE_APP_USER = ${ORACLE_APP_USER}
DEFINE ORACLE_APP_PASSWORD = ${ORACLE_APP_PASSWORD}
DEFINE ORACLE_BACKEND_USER = ${ORACLE_BACKEND_USER}
DEFINE ORACLE_BACKEND_PASSWORD = ${ORACLE_BACKEND_PASSWORD}
DEFINE ORACLE_ALUMNO_USER = ${ORACLE_ALUMNO_USER}
DEFINE ORACLE_ALUMNO_PASSWORD = ${ORACLE_ALUMNO_PASSWORD}
DEFINE ORACLE_ADMIN_USER = ${ORACLE_ADMIN_USER}
DEFINE ORACLE_ADMIN_PASSWORD = ${ORACLE_ADMIN_PASSWORD}
@${init_dir}/${script}
SQL
}

run_as_app() {
  local script=$1
  sqlplus -L -s /nolog <<SQL
WHENEVER OSERROR EXIT FAILURE
WHENEVER SQLERROR EXIT SQL.SQLCODE
SET ECHO OFF
SET VERIFY OFF
CONNECT ${ORACLE_APP_USER}/"${ORACLE_APP_PASSWORD}"@//localhost:1521/${oracle_pdb}
DEFINE ORACLE_BACKEND_USER = ${ORACLE_BACKEND_USER}
@${init_dir}/${script}
SQL
}

printf 'Inicializando el esquema SAReduccion en %s.\n' "$oracle_pdb"
run_as_sys 01-configurar-esquema.sql
run_as_app 02-crear-estructura.sql
run_as_sys 03-crear-identidades-roles.sql
run_as_sys 05-crear-cuenta-tecnica.sql
run_as_app 04-crear-paquetes-permisos.sql

printf 'Verificando tablas, paquetes, usuarios y roles...\n'
sqlplus -L -s / as sysdba <<SQL
WHENEVER OSERROR EXIT FAILURE
WHENEVER SQLERROR EXIT SQL.SQLCODE
SET ECHO OFF
SET VERIFY OFF
SET SERVEROUTPUT ON
ALTER SESSION SET CONTAINER = ${oracle_pdb};
DECLARE
  n NUMBER;
  PROCEDURE assert_count(p_actual NUMBER, p_expected NUMBER, p_name VARCHAR2) IS
  BEGIN
    IF p_actual <> p_expected THEN
      RAISE_APPLICATION_ERROR(-20990, 'Validacion fallida: ' || p_name);
    END IF;
    DBMS_OUTPUT.PUT_LINE('OK: ' || p_name);
  END;
BEGIN
  SELECT COUNT(*) INTO n FROM DBA_TABLES WHERE OWNER = '${ORACLE_APP_USER}';
  assert_count(n, 21, '21 tablas');

  SELECT COUNT(*) INTO n FROM DBA_OBJECTS
   WHERE OWNER = '${ORACLE_APP_USER}'
     AND OBJECT_NAME IN ('PKG_ESTUDIANTE', 'PKG_ADMINISTRADOR', 'PKG_ASPIRANTE')
     AND OBJECT_TYPE IN ('PACKAGE', 'PACKAGE BODY')
     AND STATUS = 'VALID';
  assert_count(n, 6, 'paquetes validos');

  SELECT COUNT(*) INTO n FROM DBA_USERS
    WHERE USERNAME IN ('${ORACLE_ALUMNO_USER}', '${ORACLE_ADMIN_USER}', '${ORACLE_BACKEND_USER}');
  assert_count(n, 3, 'usuarios Oracle');

  SELECT COUNT(*) INTO n FROM DBA_ROLE_PRIVS
   WHERE (GRANTEE = '${ORACLE_ALUMNO_USER}' AND GRANTED_ROLE = 'ROL_SAR_ESTUDIANTE_SIM')
      OR (GRANTEE = '${ORACLE_ADMIN_USER}' AND GRANTED_ROLE = 'ROL_SAR_ADMIN_SIM');
  assert_count(n, 2, 'roles asignados');

  SELECT COUNT(*) INTO n FROM DBA_SYS_PRIVS
   WHERE GRANTEE = '${ORACLE_BACKEND_USER}' AND PRIVILEGE = 'CREATE SESSION';
  assert_count(n, 1, 'CREATE SESSION de la cuenta tecnica');

  SELECT COUNT(*) INTO n FROM DBA_SYS_PRIVS
   WHERE GRANTEE = '${ORACLE_BACKEND_USER}' AND PRIVILEGE <> 'CREATE SESSION';
  assert_count(n, 0, 'sin privilegios de sistema adicionales para la cuenta tecnica');

  SELECT COUNT(*) INTO n FROM DBA_ROLE_PRIVS WHERE GRANTEE = '${ORACLE_BACKEND_USER}';
  assert_count(n, 0, 'sin roles para la cuenta tecnica');

  SELECT COUNT(*) INTO n FROM DBA_TAB_PRIVS
   WHERE GRANTEE = '${ORACLE_BACKEND_USER}' AND OWNER = '${ORACLE_APP_USER}'
     AND TABLE_NAME IN ('PKG_ESTUDIANTE', 'PKG_ADMINISTRADOR', 'PKG_ASPIRANTE') AND PRIVILEGE = 'EXECUTE';
  assert_count(n, 3, 'EXECUTE solo sobre los paquetes autorizados');

  SELECT COUNT(*) INTO n FROM DBA_TAB_PRIVS
   WHERE GRANTEE = '${ORACLE_BACKEND_USER}'
     AND NOT (OWNER = '${ORACLE_APP_USER}'
              AND TABLE_NAME IN ('PKG_ESTUDIANTE', 'PKG_ADMINISTRADOR', 'PKG_ASPIRANTE')
              AND PRIVILEGE = 'EXECUTE');
  assert_count(n, 0, 'sin grants de objetos adicionales para la cuenta tecnica');

  SELECT COUNT(*) INTO n FROM DBA_TAB_PRIVS
   WHERE GRANTEE IN ('ROL_SAR_ESTUDIANTE_SIM', 'ROL_SAR_ADMIN_SIM')
     AND OWNER = '${ORACLE_APP_USER}' AND TABLE_NAME IN ('PKG_ESTUDIANTE', 'PKG_ADMINISTRADOR', 'PKG_ASPIRANTE')
     AND PRIVILEGE = 'EXECUTE';
  assert_count(n, 0, 'sin EXECUTE de paquetes para usuarios individuales');

  SELECT COUNT(*) INTO n FROM DBA_ROLE_PRIVS
   WHERE GRANTEE = '${ORACLE_APP_USER}' AND GRANTED_ROLE = 'DBA';
  assert_count(n, 0, 'SA_REDUCCION sin rol DBA');
END;
/
EXIT SUCCESS
SQL

touch /opt/oracle/oradata/.sareduccion-init.complete
printf 'Inicializacion Oracle de SAReduccion completada.\n'