-- Remove os formulários 4.2 e 4.4 (a mesma exclusão da tela) e o "Celular e chip" passa de 4.3 para 4.2.
-- Como na tela, formulário que já recebeu respostas não é excluído: nesse caso ele fica desativado (V58), o código
-- continua ocupado e a renumeração não acontece, para não duplicar o 4.2.
UPDATE itsm.formulario f
SET deleted = 1, update_time = CURRENT_TIMESTAMP
WHERE f.deleted = 0
  AND (f.codigo, f.nome) IN (('4.2', 'Telefonia: aparelho telefônico'), ('4.4', 'Telefonia: outros'))
  AND NOT EXISTS (SELECT 1 FROM itsm.formulario_resposta r WHERE r.formulario_id = f.id);

UPDATE itsm.formulario f
SET codigo = '4.2', update_time = CURRENT_TIMESTAMP
WHERE f.deleted = 0 AND f.codigo = '4.3' AND f.nome = 'Telefonia: celular e chip'
  AND NOT EXISTS (SELECT 1 FROM itsm.formulario o WHERE o.deleted = 0 AND o.codigo = '4.2');
