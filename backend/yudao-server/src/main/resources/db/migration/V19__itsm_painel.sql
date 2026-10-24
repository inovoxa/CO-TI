-- ========== ITSM F1d — painel do Service Desk (GLPI: Dashboard e Stat) ==========
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, updater, deleted) VALUES
(51140, 'Painel', '', 2, 0, 51000, 'painel', 'ep:data-analysis', 'itsm/painel/index', 'ItsmPainel', 0, true, true, true, '1', '1', 0);

INSERT INTO system_role_menu (id, role_id, menu_id, creator, updater, deleted, tenant_id)
SELECT nextval('system_role_menu_seq'), r, 51140, '1', '1', 0, 1 FROM (VALUES (50001), (50002)) AS papeis(r);
