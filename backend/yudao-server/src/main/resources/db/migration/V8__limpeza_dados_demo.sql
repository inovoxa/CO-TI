-- CO-TI: remove os dados de demonstração do RuoYi-Vue-Pro e deixa uma instalação limpa.
-- Usa exclusão lógica (deleted = 1) e status = 1 para não quebrar chaves estrangeiras do yudao.

-- Estrutura: uma empresa raiz (id 100). As filiais vêm do AD (coti.ad.dept-raiz-id = 100) ou do cadastro.
UPDATE system_dept SET name = 'Empresa', leader_user_id = NULL, phone = '', email = '' WHERE id = 100;
UPDATE system_dept SET deleted = 1 WHERE id <> 100;

-- Usuários: só o administrador local de contingência (id 1). A senha padrão é trocada na primeira partida
-- (coti.seguranca.senha-inicial-admin); o backend não sobe enquanto ela for a pública "admin123".
UPDATE system_users SET nickname = 'Administrador', dept_id = 100, remark = 'Conta local de contingência',
                        email = '', mobile = '', avatar = '', post_ids = NULL
 WHERE id = 1;
UPDATE system_users SET deleted = 1, status = 1 WHERE id <> 1;
UPDATE system_user_role SET deleted = 1 WHERE user_id <> 1;
UPDATE system_user_post SET deleted = 1;

-- Perfis: administrador e usuário comum, mais os perfis operacionais do CO-TI.
UPDATE system_role SET name = 'Administrador', remark = 'Acesso total' WHERE id = 1;
UPDATE system_role SET name = 'Usuário', remark = 'Acesso básico', data_scope = 5 WHERE id = 2;
UPDATE system_role SET deleted = 1 WHERE id NOT IN (1, 2);
UPDATE system_role_menu SET deleted = 1 WHERE role_id NOT IN (1);

UPDATE system_post SET deleted = 1;
UPDATE system_tenant SET name = 'Empresa' WHERE id = 1;
UPDATE system_tenant SET deleted = 1 WHERE id <> 1;

-- Menus: esconde os módulos do RuoYi que o CO-TI não usa (status = 1 pode ser revertido) e os links externos.
UPDATE system_menu SET status = 1
 WHERE parent_id = 0 AND id NOT IN (1, 2, 50000);

-- Perfis operacionais (data_scope: 1 = tudo, 4 = o próprio departamento e abaixo)
INSERT INTO system_role (id, name, code, sort, data_scope, data_scope_dept_ids, status, type, remark, creator, updater, deleted, tenant_id) VALUES
(50001, 'Operador do NOC',     'noc',            10, 1, '', 0, 2, 'Todos os módulos do Centro de Operações', '1', '1', 0, 1),
(50002, 'Service Desk',        'service_desk',   11, 1, '', 0, 2, 'Visão geral, chamados, inventário e links', '1', '1', 0, 1),
(50003, 'SOC (segurança)',     'soc',            12, 1, '', 0, 2, 'Visão geral, logs, câmeras, links e mensagens', '1', '1', 0, 1),
(50004, 'Portaria',            'portaria',       13, 1, '', 0, 2, 'Câmeras e acesso', '1', '1', 0, 1),
(50005, 'Gestor da unidade',   'gestor_unidade', 14, 4, '', 0, 2, 'Só a própria unidade', '1', '1', 0, 1),
(50006, 'Telão',               'telao',          15, 1, '', 0, 2, 'Usuário da TV da sala de operações', '1', '1', 0, 1);

INSERT INTO system_role_menu (id, role_id, menu_id, creator, updater, deleted, tenant_id)
SELECT nextval('system_role_menu_seq'), r.role_id, r.menu_id, '1', '1', 0, 1
FROM (VALUES
    -- NOC: tudo
    (50001, 50000), (50001, 50010), (50001, 50011), (50001, 50020), (50001, 50021), (50001, 50030), (50001, 50031),
    (50001, 50032), (50001, 50040), (50001, 50041), (50001, 50050), (50001, 50051), (50001, 50060), (50001, 50061),
    (50001, 50070), (50001, 50080),
    -- Service Desk
    (50002, 50000), (50002, 50010), (50002, 50011), (50002, 50030), (50002, 50031), (50002, 50032), (50002, 50040),
    (50002, 50041), (50002, 50050), (50002, 50051),
    -- SOC
    (50003, 50000), (50003, 50010), (50003, 50011), (50003, 50020), (50003, 50021), (50003, 50040), (50003, 50041),
    (50003, 50060), (50003, 50061), (50003, 50070),
    -- Portaria
    (50004, 50000), (50004, 50020), (50004, 50021),
    -- Gestor da unidade
    (50005, 50000), (50005, 50010), (50005, 50011), (50005, 50030), (50005, 50031), (50005, 50050), (50005, 50051),
    -- Telão (sem página no menu: só as permissões de leitura usadas por /telao)
    (50006, 50011), (50006, 50041), (50006, 50021)
) AS r(role_id, menu_id);
