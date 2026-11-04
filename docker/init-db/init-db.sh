#!/usr/bin/env bash
# ============================================================================
# init-db.sh - Inicializacion automatica de Oracle para SAReduccion.
#
# FINALIDAD
#   Orquestar la creacion del esquema, los roles, las cuentas, los paquetes
#   PL/SQL y (opcionalmente) los datos de desarrollo en un volumen vacio.
#
# POR QUE ES NECESARIO
#   La imagen gvenzl/oracle-xe ejecuta los archivos de
#   /container-entrypoint-initdb.d como SYS y SIN variables de sustitucion.
#   Los scripts SQL requieren variables (usuarios y contrasenas) y algunos
#   deben ejecutarse como SA_REDUCCION, no como SYS. Este orquestador resuelve
#   ambas cosas: define las variables y aplica cada script con el usuario que
#   corresponde.
#
# REQUISITOS
#   * Solo se ejecuta con un volumen vacio. Si Oracle ya inicializo la base,
#     la imagen no vuelve a invocarlo.
#   * Las variables de .env llegan al contenedor via docker-compose.yml.
#   * SAR_LOAD_DEV_SEED controla la carga de datos ficticios (false en produccion).
#
# ADVERTENCIAS
#   * Un error detiene el arranque del contenedor a proposito: es preferible
#     fallar de forma visible a dejar una base a medias.
#   * El DDL de Oracle confirma transacciones implicitamente, asi que un fallo
#     a mitad de ejecucion no revierte lo ya creado.
# ============================================================================
set -Eeuo pipefail
set +x

readonly init_dir=/opt/sareduccion/init-db
readonly oracle_pdb=${SAR_ORACLE_DATABASE:-XEPDB1}
# Numero de tablas creadas por 02-crear-estructura.sql. Si se agrega o quita
# una tabla, actualizar esta constante y el inventario del script 02.
readonly expected_tables=22

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

# Datos ficticios de desarrollo: se cargan solo si SAR_LOAD_DEV_SEED esta activo.
# En produccion debe quedar desactivado.
sar_seed_activo=false
case "${SAR_LOAD_DEV_SEED:-false}" in
  true|TRUE|True|1|yes|YES|Yes) sar_seed_activo=true ;;
esac

run_as_sys() {
  local script=$1
  sqlplus -L -s / as sysdba <<SQL
WHENEVER OSERROR EXIT FAILURE
WHENEVER SQLERROR EXIT SQL.SQLCODE
SET ECHO OFF
SET VERIFY OFF
SET DEFINE ON
-- Necesario: los scripts contienen lineas en blanco dentro de bloques PL/SQL
-- y de sentencias DDL; sin esto SQL*Plus corta la sentencia en la linea vacia.
SET SQLBLANKLINES ON
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
-- Necesario: los scripts contienen lineas en blanco dentro de bloques PL/SQL
-- y de sentencias DDL; sin esto SQL*Plus corta la sentencia en la linea vacia.
SET SQLBLANKLINES ON
CONNECT ${ORACLE_APP_USER}/"${ORACLE_APP_PASSWORD}"@//localhost:1521/${oracle_pdb}
DEFINE ORACLE_BACKEND_USER = ${ORACLE_BACKEND_USER}
@${init_dir}/${script}
SQL
}

# El numero de cada archivo indica su orden real de ejecucion.
printf 'Inicializando el esquema SAReduccion en %s.\n' "$oracle_pdb"
run_as_sys 01-configurar-esquema.sql
run_as_app 02-crear-estructura.sql
run_as_sys 03-crear-identidades-roles.sql
run_as_sys 04-crear-cuenta-tecnica.sql
run_as_app 05-crear-paquetes-permisos.sql

if [[ "$sar_seed_activo" == true ]]; then
  printf 'Cargando datos de desarrollo (SAR_LOAD_DEV_SEED activo).\n'
  run_as_app 06-seed-desarrollo.sql
else
  printf 'Omitiendo datos de desarrollo (SAR_LOAD_DEV_SEED desactivado).\n'
fi

printf 'Verificando tablas, paquetes, usuarios y roles...\n'
sqlplus -L -s / as sysdba <<SQL
WHENEVER OSERROR EXIT FAILURE
WHENEVER SQLERROR EXIT SQL.SQLCODE
SET ECHO OFF
SET VERIFY OFF
SET SERVEROUTPUT ON
SET SQLBLANKLINES ON
ALTER SESSION SET CONTAINER = ${oracle_pdb};
DEFINE SAR_SEED_ACTIVO = ${sar_seed_activo}
DECLARE
  n NUMBER;
  PROCEDURE assert_count(p_actual NUMBER, p_expected NUMBER, p_name VARCHAR2) IS
  BEGIN
    IF p_actual <> p_expected THEN
      RAISE_APPLICATION_ERROR(-20990, 'Validacion fallida: ' || p_name);
    END IF;
    DBMS_OUTPUT.PUT_LINE('OK: ' || p_name);
  END;
  -- OJO: SQL*Plus devuelve SQLCODE mod 256 como codigo de salida. Un codigo
  -- como -20992 (20992 = 82*256) se traduce a 0 y set -e NO abortaria.
  -- Usar siempre codigos cuyo residuo mod 256 sea distinto de cero.
  PROCEDURE assert_min(p_actual NUMBER, p_min NUMBER, p_name VARCHAR2) IS
  BEGIN
    IF p_actual < p_min THEN
      RAISE_APPLICATION_ERROR(-20993, 'Validacion fallida: ' || p_name);
    END IF;
    DBMS_OUTPUT.PUT_LINE('OK: ' || p_name);
  END;
BEGIN
  SELECT COUNT(*) INTO n FROM DBA_TABLES WHERE OWNER = '${ORACLE_APP_USER}';
  assert_count(n, ${expected_tables}, '${expected_tables} tablas');

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

  -- Datos de aplicacion: deben existir solo si el seed de desarrollo esta activo.
  SELECT COUNT(*) INTO n FROM ${ORACLE_APP_USER}.Estudiantes;
  IF '&&SAR_SEED_ACTIVO' = 'true' THEN
    assert_min(n, 1, 'datos de desarrollo cargados (Estudiantes)');
    SELECT COUNT(*) INTO n FROM ${ORACLE_APP_USER}.Aspirantes;
    assert_min(n, 1, 'datos de desarrollo cargados (Aspirantes)');
  ELSE
    assert_count(n, 0, 'sin datos de desarrollo (Estudiantes)');
  END IF;
END;
/
EXIT SUCCESS
SQL

touch /opt/oracle/oradata/.sareduccion-init.complete
printf 'Inicializacion Oracle de SAReduccion completada.\n'