#!/bin/bash
# Gera as imagens do CO-TI para instalar no servidor do órgão sem levar o repositório.
#
#   deploy/imagens/gerar-imagens.sh                  # só compila; a versão é o commit atual
#   deploy/imagens/gerar-imagens.sh --versao 1.2.0
#   deploy/imagens/gerar-imagens.sh --publicar       # envia ao registro (faça docker login antes)
#   deploy/imagens/gerar-imagens.sh --exportar DIR   # grava DIR/co-ti-VERSAO.tar.gz para levar em mídia
#
# Variável: CO_TI_REGISTRO (padrão ghcr.io/inovoxa). Recusa árvore com alterações sem commit, para que cada versão
# corresponda a um commit: é esse commit que atende um pedido de código-fonte (GPLv3).
set -euo pipefail

raiz=$(git -C "$(dirname "$0")" rev-parse --show-toplevel)
registro=${CO_TI_REGISTRO:-ghcr.io/inovoxa}
versao=""
publicar=0
exportar=""
while [ $# -gt 0 ]; do
    case "$1" in
        --versao) versao=$2; shift 2 ;;
        --publicar) publicar=1; shift ;;
        --exportar) exportar=$2; shift 2 ;;
        *) echo "opção desconhecida: $1" >&2; exit 2 ;;
    esac
done

if [ -n "$(git -C "$raiz" status --porcelain)" ]; then
    echo "Há alterações sem commit: faça o commit antes de gerar as imagens." >&2
    exit 1
fi
commit=$(git -C "$raiz" rev-parse HEAD)
versao=${versao:-$(git -C "$raiz" rev-parse --short=8 HEAD)}

export DOCKER_BUILDKIT=1
gerar() { # nome, contexto, argumentos extras
    local nome=$1 contexto=$2
    shift 2
    echo "== $registro/$nome:$versao"
    docker build --label "org.opencontainers.image.revision=$commit" \
        --label "org.opencontainers.image.version=$versao" \
        --label "org.opencontainers.image.licenses=GPL-3.0" \
        -t "$registro/$nome:$versao" "$@" "$contexto"
}
gerar co-ti-backend "$raiz/backend"
gerar co-ti-frontend "$raiz/frontend"
gerar co-ti-postgres "$raiz/deploy" -f "$raiz/deploy/imagens/postgres.Dockerfile"

imagens=("$registro/co-ti-backend:$versao" "$registro/co-ti-frontend:$versao" "$registro/co-ti-postgres:$versao")
if [ "$publicar" = 1 ]; then
    for imagem in "${imagens[@]}"; do docker push "$imagem"; done
fi
if [ -n "$exportar" ]; then
    mkdir -p "$exportar"
    docker save "${imagens[@]}" | gzip > "$exportar/co-ti-$versao.tar.gz"
    echo "Arquivo: $exportar/co-ti-$versao.tar.gz (no servidor: docker load -i co-ti-$versao.tar.gz)"
fi
echo "Versão: $versao (commit $commit). No servidor: CO_TI_VERSAO=$versao"
