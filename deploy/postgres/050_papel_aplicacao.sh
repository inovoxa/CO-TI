#!/bin/sh
# Roda só na criação do volume. Cria o papel com que a APLICAÇÃO conecta (coti_app).
# O dono das tabelas (coti) fica só com o Flyway: assim quem comprometer a aplicação não consegue
# alterar estrutura nem desligar o trigger que protege a trilha de auditoria.
set -e
if [ -z "$CO_TI_DB_APP_PASSWORD" ]; then
    echo "CO_TI_DB_APP_PASSWORD não definida" >&2
    exit 1
fi
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -v senha="$CO_TI_DB_APP_PASSWORD" <<'EOSQL'
CREATE ROLE coti_app LOGIN PASSWORD :'senha';
EOSQL
