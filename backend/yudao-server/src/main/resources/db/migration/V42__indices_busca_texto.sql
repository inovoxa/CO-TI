-- CO-TI: busca por trecho de texto ("%impressora%") sem varrer a tabela inteira.
-- Os índices trigram atendem LIKE e ILIKE com curinga no começo, que um índice comum não usa.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- fila de chamados: busca pelo título
CREATE INDEX IF NOT EXISTS idx_chamado_titulo_trgm ON itsm.chamado USING gin (titulo gin_trgm_ops) WHERE deleted = 0;

-- campos de pessoa (solicitante, técnico, aprovador): busca por nome ou login enquanto se digita
CREATE INDEX IF NOT EXISTS idx_users_nickname_trgm ON system_users USING gin (nickname gin_trgm_ops) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_users_username_trgm ON system_users USING gin (username gin_trgm_ops) WHERE deleted = 0;
