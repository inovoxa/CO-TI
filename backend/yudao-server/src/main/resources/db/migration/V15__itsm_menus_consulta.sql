-- NOC e Service Desk já tinham a permissão de consultar categorias, equipes, origens, SLA e calendários,
-- mas não as páginas no menu. Inclui as páginas (só leitura: os botões de criar/editar dependem das permissões).
INSERT INTO system_role_menu (id, role_id, menu_id, creator, updater, deleted, tenant_id)
SELECT nextval('system_role_menu_seq'), r, m, '1', '1', 0, 1
FROM (VALUES (50001), (50002)) AS papeis(r)
CROSS JOIN (VALUES (51020), (51030), (51040), (51050), (51060), (51070)) AS menus(m)
WHERE NOT EXISTS (SELECT 1 FROM system_role_menu x WHERE x.role_id = papeis.r AND x.menu_id = menus.m AND x.deleted = 0);
