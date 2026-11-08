-- ========== Quem pode aprovar chamados ==========
-- Antes, qualquer pessoa ativa podia ser aprovadora: bastava o solicitante digitar o e-mail dela no formulário.
-- Agora só aprova quem tem a permissão itsm:aprovacao:aprovar, dada por um de dois papéis:
--   "Aprovador": dado à mão (Sistema → Usuários → Atribuir papel);
--   "Aprovador (grupo do AD)": controlado pela sincronização, para os membros de CO_TI_AD_GRUPO_APROVADORES.
-- Vale para o gestor do AD, o e-mail informado no formulário, o aprovador fixo e o pedido do técnico.

INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, updater, deleted) VALUES
(51121, 'Aprovar chamados', 'itsm:aprovacao:aprovar', 3, 1, 51120, '', '', '', NULL, 0, true, true, true, '1', '1', 0);

INSERT INTO system_role (id, name, code, sort, data_scope, data_scope_dept_ids, status, type, remark, creator, updater, deleted, tenant_id) VALUES
(50009, 'Aprovador', 'aprovador', 18, 5, '', 0, 2, 'Pode aprovar ou recusar pedidos (reset de senha, acessos, criação de conta)', '1', '1', 0, 1),
(50010, 'Aprovador (grupo do AD)', 'aprovador_ad', 19, 5, '', 0, 2, 'Igual ao Aprovador, mas controlado pelo grupo do AD (CO_TI_AD_GRUPO_APROVADORES): não atribua à mão', '1', '1', 0, 1);

INSERT INTO system_role_menu (id, role_id, menu_id, creator, updater, deleted, tenant_id)
SELECT nextval('system_role_menu_seq'), r, m, '1', '1', 0, 1
FROM (VALUES (50009), (50010)) AS papeis(r)
CROSS JOIN (VALUES (51120), (51121)) AS menus(m);

-- quem já aprovou ou tem aprovação pendente continua podendo (nada que já está andando para)
INSERT INTO system_user_role (id, user_id, role_id, creator, updater, deleted, tenant_id)
SELECT nextval('system_user_role_seq'), u, 50009, '1', '1', 0, 1
FROM (SELECT DISTINCT aprovador_id AS u FROM itsm.aprovacao WHERE deleted = 0
      UNION
      SELECT aprovador_id FROM itsm.formulario WHERE deleted = 0 AND aprovador_id IS NOT NULL) AS pessoas
WHERE NOT EXISTS (SELECT 1 FROM system_user_role ur WHERE ur.user_id = pessoas.u AND ur.role_id = 50009 AND ur.deleted = 0);
