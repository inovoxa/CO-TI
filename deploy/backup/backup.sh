#!/bin/bash
# Backup do CO-TI: banco (pg_dump no formato custom) e anexos, todo dia em CO_TI_BACKUP_HORA, guardando
# CO_TI_BACKUP_DIAS dias em /backups (./backups no servidor). Copie essa pasta para fora do servidor.
#
#   docker compose up -d backup                 # agenda diária
#   docker compose run --rm backup agora        # um backup já
#
# Restauração: deploy/backup/restaurar.sh
set -euo pipefail

DESTINO=/backups
HORA=${CO_TI_BACKUP_HORA:-02:30}
DIAS=${CO_TI_BACKUP_DIAS:-14}
export PGPASSWORD="$CO_TI_DB_PASSWORD"

log() { echo "$(date '+%F %T') $*"; }

fazer_backup() {
    local quando banco anexos
    quando=$(date +%Y%m%d-%H%M)
    banco="$DESTINO/coti-$quando.dump"
    anexos="$DESTINO/arquivos-$quando.tar.gz"
    log "backup do banco em $banco"
    # .parcial até terminar: um backup interrompido nunca parece completo
    pg_dump -h "${CO_TI_DB_HOST:-postgres}" -U coti -d coti -Fc -Z 6 -f "$banco.parcial"
    pg_restore -l "$banco.parcial" > /dev/null   # o arquivo é legível
    mv "$banco.parcial" "$banco"
    log "anexos em $anexos"
    tar -C /arquivos -czf "$anexos.parcial" .
    mv "$anexos.parcial" "$anexos"
    find "$DESTINO" -maxdepth 1 -type f \( -name 'coti-*.dump' -o -name 'arquivos-*.tar.gz' -o -name '*.parcial' \) \
        -mtime +"$((DIAS - 1))" -print -delete | sed 's/^/apagado (mais de '"$DIAS"' dias): /'
    # situação do último backup, para conferir de fora (e para um monitor do tipo "arquivo recente")
    printf '{"quando":"%s","banco":"%s","bytesBanco":%s,"anexos":"%s","bytesAnexos":%s}\n' \
        "$(date -Iseconds)" "$(basename "$banco")" "$(stat -c %s "$banco")" \
        "$(basename "$anexos")" "$(stat -c %s "$anexos")" > "$DESTINO/ultimo.json"
    log "ok: banco $(du -h "$banco" | cut -f1), anexos $(du -h "$anexos" | cut -f1)"
}

segundos_ate() {
    local agora alvo
    agora=$(date +%s)
    alvo=$(date -d "today $1" +%s)
    [ "$alvo" -le "$agora" ] && alvo=$(date -d "tomorrow $1" +%s)
    echo $((alvo - agora))
}

mkdir -p "$DESTINO"
if [ "${1:-}" = "restaurar" ]; then
    shift
    exec bash "$(dirname "$0")/restaurar.sh" "$@"
fi
if [ "${1:-}" = "agora" ]; then
    fazer_backup
    exit 0
fi

log "backup diário às $HORA, guardando $DIAS dias"
while true; do
    sleep "$(segundos_ate "$HORA")"
    # uma falha não para a agenda: registra e tenta de novo no dia seguinte
    fazer_backup || log "ERRO: backup falhou (veja acima)"
done
