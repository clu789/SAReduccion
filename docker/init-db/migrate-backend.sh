#!/usr/bin/env bash
set -Eeuo pipefail
set +x

readonly init_dir=/opt/sareduccion/init-db
readonly oracle_pdb=${SAR_ORACLE_DATABASE:-XEPDB1}

required_variables=(ORACLE_APP_USER ORACLE_APP_PASSWORD ORACLE_BACKEND_USER ORACLE_BACKEND_PASSWORD)
for variable in "${required_variables[@]}"; do
  if [[ -z "${!variable:-}" ]]; then
    printf 'Falta la variable requerida: %s\n' "$variable" >&2
    exit 1
  fi
done

if [[ "$oracle_pdb" != XEPDB1 || "$ORACLE_APP_USER" != SA_REDUCCION || "$ORACLE_BACKEND_USER" != SAR_BACKEND ]]; then
  printf 'La migracion requiere XEPDB1, SA_REDUCCION y SAR_BACKEND.\n' >&2
  exit 1
fi

for variable in ORACLE_APP_PASSWORD ORACLE_BACKEND_PASSWORD; do
  password_value=${!variable}
  if [[ ${#password_value} -lt 8 || ! "$password_value" =~ ^[A-Za-z0-9_!%+=.-]+$ ]]; then
    printf 'La clave de %s contiene caracteres no compatibles con SQL*Plus.\n' "$variable" >&2
    exit 1
  fi
done

sqlplus -L -s / as sysdba <<SQL
WHENEVER OSERROR EXIT FAILURE
WHENEVER SQLERROR EXIT SQL.SQLCODE
SET ECHO OFF
SET VERIFY OFF
SET DEFINE ON
ALTER SESSION SET CONTAINER = ${oracle_pdb};
DEFINE ORACLE_BACKEND_USER = ${ORACLE_BACKEND_USER}
DEFINE ORACLE_BACKEND_PASSWORD = ${ORACLE_BACKEND_PASSWORD}
@${init_dir}/05-crear-cuenta-tecnica.sql
SQL

sqlplus -L -s /nolog <<SQL
WHENEVER OSERROR EXIT FAILURE
WHENEVER SQLERROR EXIT SQL.SQLCODE
SET ECHO OFF
SET VERIFY OFF
SET DEFINE ON
CONNECT ${ORACLE_APP_USER}/"${ORACLE_APP_PASSWORD}"@//localhost:1521/${oracle_pdb}
DEFINE ORACLE_BACKEND_USER = ${ORACLE_BACKEND_USER}
@${init_dir}/04-crear-paquetes-permisos.sql
SQL

printf 'Migracion tecnica aplicada sin recrear el esquema ni el volumen.\n'
