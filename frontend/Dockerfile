# Build do frontend CO-TI (Vue 3 + Element Plus) servido pelo nginx
FROM node:22.12.0-alpine AS build
WORKDIR /src
RUN npm install -g pnpm@10.18.3
COPY package.json pnpm-lock.yaml ./
RUN --mount=type=cache,target=/root/.local/share/pnpm/store pnpm install --frozen-lockfile
COPY . .
RUN pnpm build:coti

FROM nginx:1.27.3-alpine
# openssl: gera o certificado autoassinado quando não há o da empresa
RUN apk add --no-cache openssl
COPY --from=build /src/dist-coti /usr/share/nginx/html
COPY nginx/agentes.conf nginx/portal.conf nginx/proxy.conf.template /etc/nginx/coti/
COPY nginx/default.conf.template /etc/nginx/templates/
COPY --chmod=755 nginx/15-modo-proxy.sh nginx/40-certificado.sh /docker-entrypoint.d/
# só a porta do HTTPS entra no modelo; $host, $uri etc. ficam para o nginx
ENV NGINX_ENVSUBST_FILTER=^CO_TI_ CO_TI_HTTPS_PORT=443
RUN rm -f /etc/nginx/conf.d/default.conf
EXPOSE 80 443
