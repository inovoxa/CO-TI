#!/bin/sh
# Sem certificado em /etc/nginx/tls (segredos/tls no servidor), gera um autoassinado para o HTTPS já funcionar.
# Ele vale 2 anos e o navegador vai avisar: troque pelo certificado da empresa (cert.pem com a cadeia, key.pem).
set -eu
case "${CO_TI_ATRAS_DE_PROXY:-false}" in true | 1 | sim) exit 0 ;; esac   # o HTTPS é do proxy
DIR=/etc/nginx/tls
[ -s "$DIR/cert.pem" ] && [ -s "$DIR/key.pem" ] && exit 0

mkdir -p "$DIR"
NOMES="DNS:localhost,IP:127.0.0.1"
# CO_TI_TLS_NOMES: nomes e IPs pelos quais o portal é acessado, separados por vírgula (ex.: coti.pma.local,10.0.1.20)
for n in $(echo "${CO_TI_TLS_NOMES:-}" | tr ',' ' '); do
    case "$n" in
        localhost | 127.0.0.1) ;;
        *[!0-9.]*) NOMES="$NOMES,DNS:$n" ;;
        *) NOMES="$NOMES,IP:$n" ;;
    esac
done
PRIMEIRO=$(echo "${CO_TI_TLS_NOMES:-localhost}" | cut -d, -f1)
openssl req -x509 -newkey rsa:2048 -sha256 -days 730 -nodes \
    -keyout "$DIR/key.pem" -out "$DIR/cert.pem" \
    -subj "/CN=$PRIMEIRO/O=CO-TI" -addext "subjectAltName=$NOMES" 2>/dev/null
chmod 600 "$DIR/key.pem"
echo "CO-TI: certificado autoassinado gerado para $NOMES (troque pelo da empresa em segredos/tls)"
