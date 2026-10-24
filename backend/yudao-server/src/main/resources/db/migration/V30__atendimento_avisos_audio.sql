-- ========== F2e (parte 2) — avisos de volta pelo canal e transcrição de áudio ==========
-- Substitui os workflows 13 (notificar resposta), 14 (resultado do AD pelo WhatsApp) e 20 (aviso ao superior) do n8n.

-- Avisos do chamado aberto pela conversa (andamento, solução, aprovação). Fora da janela de 24 h do WhatsApp oficial,
-- o aviso vai pelo modelo aprovado escolhido aqui (uma variável: o texto do aviso).
ALTER TABLE atendimento.caixa ADD COLUMN avisos_ativo bool NOT NULL DEFAULT true;
ALTER TABLE atendimento.caixa ADD COLUMN aviso_modelo_id int8 NULL;

-- Modelo de transcrição (/audio/transcriptions) do provedor; vazio = o provedor não transcreve áudio
ALTER TABLE atendimento.ia_provedor ADD COLUMN modelo_audio varchar(150) NULL;
