-- Movimento do estoque ligado ao chamado que o originou.
ALTER TABLE itsm.estoque_movimento ADD COLUMN chamado_id int8 NULL;
CREATE INDEX idx_itsm_estoque_mov_chamado ON itsm.estoque_movimento (chamado_id) WHERE chamado_id IS NOT NULL;

-- Retirada de celular ou chip corporativo pede a aprovação do gestor; defeito, devolução e furto não esperam.
-- Sem gestor no AD, o chamado abre mesmo assim, com um aviso para pedir a aprovação à mão.
INSERT INTO itsm.regra (id, nome, descricao, ordem, combinacao, criterios, acoes, creator, updater)
SELECT nextval('itsm.regra_seq'), 'Retirada de celular corporativo', 'Pede a aprovação do gestor nas retiradas do formulário 4.2', 100, 'E',
       '[{"campo":"titulo","operador":"comeca","valor":"Celular corporativo: Retirada"}]',
       '[{"acao":"pedir_aprovacao_gestor","valor":""}]', '1', '1'
WHERE NOT EXISTS (SELECT 1 FROM itsm.regra r WHERE r.deleted = 0 AND r.nome = 'Retirada de celular corporativo');
