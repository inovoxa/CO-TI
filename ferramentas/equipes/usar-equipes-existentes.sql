-- Para a instalação que já tem as equipes abaixo: cada área do catálogo passa a usar uma delas, no lugar da equipe vazia
-- criada na atualização (V67). Equipe que não existir é ignorada, e área que alguém já mudou pela tela não é tocada.
-- Uso: docker exec -i <contêiner do postgres> psql -U coti -d coti -v ON_ERROR_STOP=1 < usar-equipes-existentes.sql
BEGIN;

UPDATE itsm.categoria c
SET equipe_id = destino.id, update_time = CURRENT_TIMESTAMP
FROM (VALUES ('Usuários e acessos', 'N1 - Service Desk'),
             ('Suporte de informática', 'N2 - Suporte técnico'),
             ('Sistemas da saúde', 'Sistemas'),
             ('Telefonia', 'Redes e Telefonia'),
             ('Sistemas administrativos', 'Sistemas')) AS m(area, equipe)
JOIN itsm.equipe destino ON destino.deleted = 0 AND destino.nome = m.equipe
JOIN itsm.equipe criada ON criada.deleted = 0 AND criada.nome = m.area
WHERE c.deleted = 0 AND c.parent_id = 0 AND c.nome = m.area AND c.equipe_id = criada.id;

-- Só desativa a equipe criada na atualização que ninguém usa: sem pessoas, sem chamados e sem categoria apontando para ela
UPDATE itsm.equipe e
SET ativo = false, update_time = CURRENT_TIMESTAMP
WHERE e.deleted = 0 AND e.ativo
  AND e.nome IN ('Usuários e acessos', 'Suporte de informática', 'Sistemas da saúde', 'Telefonia', 'Sistemas administrativos')
  AND NOT EXISTS (SELECT 1 FROM itsm.equipe_membro m WHERE m.equipe_id = e.id AND m.deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM itsm.chamado_ator a WHERE a.equipe_id = e.id AND a.deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM itsm.categoria c WHERE c.equipe_id = e.id AND c.deleted = 0);

COMMIT;
