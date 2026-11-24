-- Inventário: TAG do agente (OCS: ACCOUNTINFO TAG, /TAG= na instalação; GLPI Agent: "tag") e o painel do inventário.

ALTER TABLE cmdb.ativo ADD COLUMN tag varchar(100) NULL;
CREATE INDEX idx_cmdb_ativo_tag ON cmdb.ativo (tag) WHERE deleted = 0 AND tag IS NOT NULL;

-- Painel: primeiro item do menu Inventário, para quem já vê os ativos
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, updater, deleted) VALUES
(53005, 'Painel', '', 2, 0, 53000, 'painel', 'ep:data-analysis', 'inventario/painel/index', 'InventarioPainel', 0, true, true, true, '1', '1', 0);

INSERT INTO system_role_menu (id, role_id, menu_id, creator, updater, deleted, tenant_id)
SELECT nextval('system_role_menu_seq'), quem.role_id, 53005, '1', '1', 0, 1
FROM (SELECT DISTINCT role_id FROM system_role_menu WHERE menu_id = 53010 AND deleted = 0) AS quem;
