-- Portal de autoatendimento: "Meus chamados" para todo mundo (as permissões são conferidas por participação).
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, updater, deleted) VALUES
(51100, 'Meus chamados', '', 2, 1, 0, '/meus-chamados', 'ep:chat-line-square', 'itsm/portal/index', 'ItsmPortal', 0, true, true, false, '1', '1', 0);

INSERT INTO system_role_menu (id, role_id, menu_id, creator, updater, deleted, tenant_id)
SELECT nextval('system_role_menu_seq'), r, 51100, '1', '1', 0, 1
FROM (VALUES (2), (50001), (50002), (50003), (50004), (50005)) AS t(r);

-- Perfil "Usuário" (2): só o portal
UPDATE system_role SET remark = 'Abre e acompanha os próprios chamados' WHERE id = 2;
