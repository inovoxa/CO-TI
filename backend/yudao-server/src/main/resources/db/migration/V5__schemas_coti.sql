-- Schemas dos módulos do CO-TI. As tabelas do núcleo (yudao: system_*, infra_*, monitor_unit) ficam em public.
-- Extensões: pgvector (IA/RAG) e TimescaleDB (heartbeats e métricas) — a imagem do docker-compose já traz as duas.
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS timescaledb;

CREATE SCHEMA IF NOT EXISTS itsm;        -- chamados, problemas, mudanças, SLA, catálogo (GLPI)
CREATE SCHEMA IF NOT EXISTS cmdb;        -- ativo único, compartilhado por ITSM e inventário
CREATE SCHEMA IF NOT EXISTS atendimento; -- caixas de entrada, conversas, contatos (Chatwoot)
CREATE SCHEMA IF NOT EXISTS monitor;     -- monitores e heartbeats (Uptime Kuma)
CREATE SCHEMA IF NOT EXISTS logs;        -- streams, pipelines e alertas (as mensagens ficam no OpenSearch)
CREATE SCHEMA IF NOT EXISTS cofre;       -- cofre de senhas
CREATE SCHEMA IF NOT EXISTS ia;          -- embeddings, assistentes, sessões
CREATE SCHEMA IF NOT EXISTS identidade;  -- vínculo com o AD, MFA, auditoria encadeada
