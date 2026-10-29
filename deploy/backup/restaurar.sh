#!/bin/bash
# Restaura um backup do CO-TI num banco VAZIO (servidor novo ou volume recriado). Pare o backend antes:
#
#   docker compose stop backend
#   docker compose run --rm backup restaurar coti-20261029-0230.dump [arquivos-20261029-0230.tar.gz]
#   docker compose start backend
#
# O banco do TimescaleDB precisa do pre_restore/post_restore em volta do pg_restore.
# Lembre: os segredos cifrados (MFA, cofre) só abrem com a mesma CO_TI_CHAVE_MESTRA do .env de origem.
set -euo pipefail

BANCO=/backups/${1:?informe o arquivo .dump (em ./backups)}
ANEXOS=${2:+/backups/$2}
export PGPASSWORD="$CO_TI_DB_PASSWORD"
psql() { command psql -h "${CO_TI_DB_HOST:-postgres}" -U coti -d coti -v ON_ERROR_STOP=1 -q "$@"; }

[ -f "$BANCO" ] || { echo "não achei $BANCO" >&2; exit 1; }
tabelas=$(psql -At -c "SELECT count(*) FROM pg_tables WHERE schemaname NOT IN ('pg_catalog','information_schema','_timescaledb_catalog','_timescaledb_config','_timescaledb_internal','_timescaledb_cache','timescaledb_information','timescaledb_experimental')")
if [ "$tabelas" != "0" ]; then
    echo "O banco coti já tem $tabelas tabelas: restaure num banco vazio (recrie o volume pgdata)." >&2
    exit 1
fi

echo "restaurando $BANCO"
psql -c "CREATE EXTENSION IF NOT EXISTS timescaledb" -c "SELECT timescaledb_pre_restore()"
# sem -j: em paralelo, as hypertables (logs, batidas) voltam vazias por causa da ordem do catálogo do TimescaleDB.
# --exit-on-error: qualquer erro para a restauração (antes, os erros passavam e os logs sumiam sem aviso)
pg_restore -h "${CO_TI_DB_HOST:-postgres}" -U coti -d coti --no-owner --role=coti --exit-on-error "$BANCO"
psql -c "SELECT timescaledb_post_restore()"
# os GRANTs para coti_app vêm no próprio dump; o papel é criado pelo 050_papel_aplicacao.sh na criação do volume

if [ -n "$ANEXOS" ]; then
    echo "restaurando anexos de $ANEXOS"
    tar -C /arquivos -xzf "$ANEXOS"
fi
echo "pronto: suba o backend (docker compose start backend)"
