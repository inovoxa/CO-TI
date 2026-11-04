# Banco e backup do CO-TI nas imagens prontas: a imagem oficial com os scripts de deploy/ dentro, porque o servidor do
# órgão não tem o repositório. Contexto do build: a pasta deploy/. Usada por docker-compose.imagens.yml.
ARG CO_TI_PG_IMAGE=timescale/timescaledb-ha:pg16.15-ts2.30.1
FROM ${CO_TI_PG_IMAGE}
COPY --chmod=755 postgres/050_papel_aplicacao.sh /docker-entrypoint-initdb.d/050_papel_aplicacao.sh
COPY --chmod=755 backup/backup.sh backup/restaurar.sh /backup/
