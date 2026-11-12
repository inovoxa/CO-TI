-- Anexos do chat expiram: o arquivo é apagado do disco depois do prazo da tela Infraestrutura → Visão geral e a linha fica
-- marcada, para a conversa mostrar "anexo expirado".
ALTER TABLE chat.anexo ADD COLUMN expirado_em timestamp NULL;
CREATE INDEX idx_chat_anexo_limpeza ON chat.anexo (create_time) WHERE expirado_em IS NULL;
