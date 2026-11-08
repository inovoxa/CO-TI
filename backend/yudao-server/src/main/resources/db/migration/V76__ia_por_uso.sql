-- IA separada por uso: o Assistente IA do portal e o Atendimento (robô e copiloto) têm cada um o seu provedor principal e uma
-- reserva opcional, e o Assistente pode ser ligado e desligado à parte. Sem principal definido, vale o que valia antes: todos os
-- provedores ativos, pela prioridade.
ALTER TABLE atendimento.ia_config ADD COLUMN interno_ativo bool NOT NULL DEFAULT false;
ALTER TABLE atendimento.ia_config ADD COLUMN provedor_interno_id int8 NULL;
ALTER TABLE atendimento.ia_config ADD COLUMN provedor_interno_reserva_id int8 NULL;
ALTER TABLE atendimento.ia_config ADD COLUMN provedor_assistente_reserva_id int8 NULL;
ALTER TABLE atendimento.ia_config ADD COLUMN provedor_copiloto_reserva_id int8 NULL;
-- o Assistente continua como estava: ligado se a IA já estava ligada
UPDATE atendimento.ia_config SET interno_ativo = ativo;

-- As credenciais da IA ficam na Infraestrutura
UPDATE system_menu SET parent_id = 2, sort = 6, updater = '1', update_time = now() WHERE id = 52037;
UPDATE system_menu SET sort = 7 WHERE id = 193;
UPDATE system_menu SET sort = 8 WHERE id = 10;

-- Monitor do Redis sai do menu: era só uma tela de consulta. O Redis continua em uso (cache, sessões e travas das rotinas).
WITH RECURSIVE sai AS (
    SELECT id FROM system_menu WHERE id = 17
    UNION ALL
    SELECT m.id FROM system_menu m JOIN sai ON m.parent_id = sai.id
), marcados AS (
    UPDATE system_menu SET deleted = 1, updater = '1', update_time = now() WHERE id IN (SELECT id FROM sai) RETURNING id
)
UPDATE system_role_menu SET deleted = 1, updater = '1', update_time = now() WHERE menu_id IN (SELECT id FROM marcados);
