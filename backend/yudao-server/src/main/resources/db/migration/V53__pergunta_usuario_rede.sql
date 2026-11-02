-- ========== Pergunta "usuário de rede" nos formulários do catálogo ==========
-- Novo tipo de pergunta "usuario": no portal vira uma lista de pessoas com busca (nome, login e setor) e a resposta
-- guardada é o login de rede; no WhatsApp a pessoa digita o login e o CO-TI confere se a conta existe.
-- O "colega que já possui o perfil desejado" (1.1, 1.3 e 1.7) passa a usar esse tipo.
UPDATE itsm.formulario f
SET definicao = (
        SELECT jsonb_set(f.definicao::jsonb, '{secoes}', jsonb_agg(
                   jsonb_set(s, '{perguntas}', (
                       SELECT jsonb_agg(CASE WHEN p->>'name' = 'perfil_referencia'
                                             THEN jsonb_set(p, '{type}', '"usuario"') ELSE p END ORDER BY pi)
                       FROM jsonb_array_elements(s->'perguntas') WITH ORDINALITY AS pp(p, pi)))
                   ORDER BY si))::text
        FROM jsonb_array_elements(f.definicao::jsonb->'secoes') WITH ORDINALITY AS ss(s, si)),
    update_time = CURRENT_TIMESTAMP
WHERE f.deleted = 0
  AND f.definicao::jsonb @? '$.secoes[*].perguntas[*] ? (@.name == "perfil_referencia" && @.type == "text")';
