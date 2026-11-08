#!/bin/sh
# CO_TI_ATRAS_DE_PROXY=true: outro proxy (Traefik do Coolify, por exemplo) faz o HTTPS. Serve tudo em HTTP na 80 com o
# IP real do cliente (proxy.conf.template) e não gera o autoassinado. Roda antes do 20-envsubst-on-templates.sh.
set -eu
case "${CO_TI_ATRAS_DE_PROXY:-false}" in
    true | 1 | sim)
        cp /etc/nginx/coti/proxy.conf.template /etc/nginx/templates/default.conf.template
        echo "CO-TI: modo atrás de proxy (HTTP na 80, HTTPS no proxy)"
        ;;
esac
