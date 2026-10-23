# Build do frontend CO-TI (Vue 3 + Element Plus) servido pelo nginx
FROM node:22.12.0-alpine AS build
WORKDIR /src
RUN npm install -g pnpm@10.18.3
COPY package.json pnpm-lock.yaml ./
RUN --mount=type=cache,target=/root/.local/share/pnpm/store pnpm install --frozen-lockfile
COPY . .
RUN pnpm build:coti

FROM nginx:1.27.3-alpine
COPY --from=build /src/dist-coti /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
