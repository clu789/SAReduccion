#!/usr/bin/env bash
# ============================================================
# start-dev.sh  –  Script de inicio rápido para desarrollo
# SAReducción – Instituto Tecnológico de Toluca
#
# Uso:
#   bash docker/scripts/start-dev.sh          → solo Oracle
#   bash docker/scripts/start-dev.sh --full   → Oracle + Spring Boot
# ============================================================

set -e

COMPOSE_FILE="$(cd "$(dirname "$0")/../.." && pwd)/docker-compose.yml"
ENV_FILE="$(cd "$(dirname "$0")/../.." && pwd)/.env"

# Colores
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

echo ""
echo -e "${CYAN}══════════════════════════════════════════════════════${NC}"
echo -e "${CYAN}   SAReducción – Entorno de Desarrollo Docker         ${NC}"
echo -e "${CYAN}   Instituto Tecnológico de Toluca                    ${NC}"
echo -e "${CYAN}══════════════════════════════════════════════════════${NC}"
echo ""

# Verificar que Docker está corriendo
if ! docker info > /dev/null 2>&1; then
    echo -e "${RED}✖ Docker no está corriendo. Inícialo primero.${NC}"
    exit 1
fi

# Verificar que existe .env
if [ ! -f "$ENV_FILE" ]; then
    echo -e "${RED}✖ No se encontró el archivo .env en la raíz del proyecto.${NC}"
    echo -e "  Crea uno copiando .env.example y ajusta las credenciales."
    exit 1
fi

# ── Opción: stack completo ────────────────────────────────────
if [ "$1" == "--full" ]; then
    echo -e "${YELLOW}▶ Levantando stack completo: Oracle + Spring Boot...${NC}"
    docker compose --env-file "$ENV_FILE" \
                   --profile full-stack \
                   -f "$COMPOSE_FILE" up -d --build
else
    echo -e "${YELLOW}▶ Levantando solo Oracle XE (modo desarrollo local)...${NC}"
    docker compose --env-file "$ENV_FILE" \
                   -f "$COMPOSE_FILE" up -d oracle-db
fi

# ── Esperar a que Oracle esté listo ──────────────────────────
echo ""
echo -e "${YELLOW}⏳ Esperando a que Oracle esté listo (puede tardar 1-2 min la primera vez)...${NC}"

RETRIES=20
COUNT=0
until docker compose -f "$COMPOSE_FILE" exec oracle-db \
    healthcheck 2>/dev/null || [ $COUNT -eq $RETRIES ]; do
    printf "."
    sleep 10
    COUNT=$((COUNT+1))
done
echo ""

# ── Mostrar información de conexión ──────────────────────────
source "$ENV_FILE"

echo ""
echo -e "${GREEN}✔ Entorno de desarrollo listo.${NC}"
echo ""
echo -e "${CYAN}┌─────────────────────────────────────────────────────┐${NC}"
echo -e "${CYAN}│  Oracle Database                                     │${NC}"
echo -e "${CYAN}│    Host:      localhost:${ORACLE_PORT}                       │${NC}"
echo -e "${CYAN}│    PDB:       ${ORACLE_DATABASE}                            │${NC}"
echo -e "${CYAN}│    Usuario:   ${ORACLE_APP_USER}                    │${NC}"
echo -e "${CYAN}│    Password:  (ver .env)                             │${NC}"
echo -e "${CYAN}│                                                      │${NC}"
echo -e "${CYAN}│  JDBC URL:                                           │${NC}"
echo -e "${CYAN}│    jdbc:oracle:thin:@localhost:${ORACLE_PORT}/${ORACLE_DATABASE}    │${NC}"
if [ "$1" == "--full" ]; then
echo -e "${CYAN}│                                                      │${NC}"
echo -e "${CYAN}│  Spring Boot API                                     │${NC}"
echo -e "${CYAN}│    http://localhost:${APP_PORT}/api                      │${NC}"
fi
echo -e "${CYAN}└─────────────────────────────────────────────────────┘${NC}"
echo ""
echo -e "Para detener:  ${YELLOW}docker compose down${NC}"
echo -e "Para limpiar:  ${YELLOW}docker compose down -v${NC}  (elimina datos)"
echo ""
