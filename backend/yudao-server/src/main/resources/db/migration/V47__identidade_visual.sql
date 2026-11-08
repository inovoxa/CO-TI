-- Identidade visual: nome e logo da instância, alteráveis em Sistema > Identidade visual (sem logo, vale a do CO-TI)
INSERT INTO infra_config (id, category, type, name, config_key, value, visible, remark, creator, updater, deleted)
SELECT nextval('infra_config_seq'), 'coti', 1, n, k, v, true, 'Alterado em Sistema > Identidade visual', '1', '1', 0
FROM (VALUES ('Nome da instância', 'coti.marca.nome', 'Centro de Operações de TI'),
             ('Logo da instância', 'coti.marca.logo', '')) AS c(n, k, v)
WHERE NOT EXISTS (SELECT 1 FROM infra_config WHERE config_key = c.k AND deleted = 0);

INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, updater, deleted) VALUES
(57000, 'Identidade visual',     '', 2, 8, 1, 'identidade-visual', 'ep:picture', 'system/marca/index', 'SystemMarca', 0, true, true, true, '1', '1', 0),
(57001, 'Alterar nome e logo',   'infra:marca:update', 3, 1, 57000, '', '', '', NULL, 0, true, true, true, '1', '1', 0)
ON CONFLICT (id) DO NOTHING;
-- só o administrador (que vê tudo): os outros perfis não têm o menu Sistema
