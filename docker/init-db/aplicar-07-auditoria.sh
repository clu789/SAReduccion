#!/bin/bash
# ============================================================================
# Aplica 07-auditoria-seguridad.sql sobre una base YA inicializada, sin borrar
# el volumen de datos. No hace falta en instalaciones nuevas: init-db.sh ya lo
# ejecuta. Uso, desde la raiz del proyecto:
#
#   docker exec sareduccion-oracle bash /opt/sareduccion/init-db/aplicar-07-auditoria.sh
#
# Con "--probar" ejecuta ademas las pruebas de pruebas/07-auditoria-seguridad-test.sql.
# ============================================================================
set -Eeuo pipefail
set +x

readonly init_dir=/opt/sareduccion/init-db
readonly oracle_pdb=${SAR_ORACLE_DATABASE:-XEPDB1}

run_as_app() {
  local script=$1
  sqlplus -L -s /nolog <<SQL
WHENEVER OSERROR EXIT FAILURE
WHENEVER SQLERROR EXIT SQL.SQLCODE
SET ECHO OFF
SET VERIFY OFF
SET SQLBLANKLINES ON
CONNECT ${ORACLE_APP_USER}/"${ORACLE_APP_PASSWORD}"@//localhost:1521/${oracle_pdb}
DEFINE ORACLE_BACKEND_USER = ${ORACLE_BACKEND_USER}
@${init_dir}/${script}
SQL
}

run_as_app 07-auditoria-seguridad.sql
if [[ "${1:-}" == "--probar" ]]; then
  run_as_app pruebas/07-auditoria-seguridad-test.sql
fi
printf 'Auditoria de seguridad aplicada.\n'
