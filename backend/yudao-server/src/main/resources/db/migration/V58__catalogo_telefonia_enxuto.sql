-- Os quatro formulários de telefonia tinham as mesmas perguntas. Ficam 4.1 (ramal, linha e aparelho) e 4.3 (celular e chip).
-- 4.2 e 4.4 só são desativados: os chamados antigos e as respostas continuam ligados a eles, e dá para reativar pela tela.
-- As categorias também ficam, porque os chamados e relatórios antigos as usam.
-- O nome na condição evita mexer em formulário que o cliente tenha reaproveitado com o mesmo código.
UPDATE itsm.formulario
SET ativo = FALSE, update_time = CURRENT_TIMESTAMP
WHERE deleted = 0
  AND (codigo, nome) IN (('4.2', 'Telefonia: aparelho telefônico'), ('4.4', 'Telefonia: outros'));

UPDATE itsm.formulario
SET descricao = 'Problemas com ramal, linha fixa ou aparelho telefônico (sem áudio, ruído, sem chamada, defeito ou troca).',
    update_time = CURRENT_TIMESTAMP
WHERE deleted = 0 AND codigo = '4.1' AND nome = 'Telefonia: problemas com ramal';
