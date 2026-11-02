-- ========== Exportar relatórios em planilha ==========
-- A exportação leva dados de pessoas para fora do portal, então tem permissão própria. Quem já consulta chamados ou
-- ativos recebe a permissão de exportar, para a mudança não tirar nada de ninguém; retire do perfil se não quiser.

INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, updater, deleted) VALUES
(51028, 'Exportar chamados e indicadores', 'itsm:chamado:exportar', 3, 10, 51010, '', '', '', NULL, 0, true, true, true, '1', '1', 0),
(53013, 'Exportar ativos', 'inventario:ativo:exportar', 3, 3, 53010, '', '', '', NULL, 0, true, true, true, '1', '1', 0);

INSERT INTO system_role_menu (id, role_id, menu_id, creator, updater, deleted, tenant_id)
SELECT nextval('system_role_menu_seq'), role_id, novo, '1', '1', 0, 1
FROM (
    SELECT DISTINCT rm.role_id, v.novo
    FROM system_role_menu rm
    JOIN (VALUES (51011, 51028), (53011, 53013)) AS v(consulta, novo) ON rm.menu_id = v.consulta
    WHERE rm.deleted = 0
) AS quem;
