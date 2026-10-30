-- Senha provisória por e-mail (pedido feito na tela de login): fica cifrada na fila só até o envio
ALTER TABLE itsm.email_saida ADD COLUMN IF NOT EXISTS segredo text;

-- Assistente da tela de login ("Esqueci minha senha"): caixa do canal widget com o fluxo do catálogo (só o 1.2).
-- Para desligar, desative a caixa em Atendimento > Caixas de entrada: o link some da tela de login.
INSERT INTO atendimento.caixa (id, nome, canal, ativo, config, atribuicao_automatica, reabrir_horas, fluxo_ativo, avisos_ativo,
                               creator, updater)
SELECT nextval('atendimento.caixa_seq'), 'Assistente da tela de login', 'widget', true, '{}', true, 0, true, false, '1', '1'
WHERE NOT EXISTS (SELECT 1 FROM atendimento.caixa WHERE canal = 'widget' AND deleted = 0);
