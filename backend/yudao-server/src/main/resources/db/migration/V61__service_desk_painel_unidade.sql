-- O Service Desk abre o mapa da Visão geral, mas o painel da unidade consulta links e equipamentos com permissões que o
-- papel não tinha: apareciam dois alertas de "sem permissão" e a mensagem "Nenhum monitor de link" em toda unidade.
INSERT INTO system_role_menu (id, role_id, menu_id, creator, updater, deleted, tenant_id)
SELECT nextval('system_role_menu_seq'), 50002, m.id, '1', '1', 0, 1
FROM system_menu m
WHERE m.deleted = 0 AND m.permission IN ('system:monitor-links:query', 'system:monitor-inventario:query')
  AND NOT EXISTS (SELECT 1 FROM system_role_menu rm WHERE rm.role_id = 50002 AND rm.menu_id = m.id AND rm.deleted = 0);
