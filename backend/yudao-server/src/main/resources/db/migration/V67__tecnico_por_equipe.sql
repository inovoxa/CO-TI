-- Técnico só vê o que é da equipe dele. Ninguém perde acesso nesta atualização: todo papel que já consulta chamados recebe
-- "ver todos"; só o novo papel Técnico (e papéis criados depois, se não a receberem) fica limitado às suas equipes.
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, updater, deleted) VALUES
(51029, 'Ver chamados de todas as equipes', 'itsm:chamado:ver-todos', 3, 12, 51010, '', '', '', NULL, 0, true, true, true, '1', '1', 0);

INSERT INTO system_role_menu (id, role_id, menu_id, creator, updater, deleted, tenant_id)
SELECT nextval('system_role_menu_seq'), rm.role_id, 51029, '1', '1', 0, 1
FROM (SELECT DISTINCT role_id FROM system_role_menu WHERE menu_id = 51011 AND deleted = 0) rm;

-- Papel Técnico: as mesmas telas do Service Desk, sem "ver todos"
INSERT INTO system_role (id, name, code, sort, data_scope, data_scope_dept_ids, status, type, remark, creator, updater, deleted, tenant_id) VALUES
(50012, 'Técnico', 'tecnico', 21, 1, '', 0, 2, 'Atende e só vê os chamados atribuídos a ele ou às equipes de que faz parte', '1', '1', 0, 1);

INSERT INTO system_role_menu (id, role_id, menu_id, creator, updater, deleted, tenant_id)
SELECT nextval('system_role_menu_seq'), 50012, rm.menu_id, '1', '1', 0, 1
FROM system_role_menu rm
WHERE rm.role_id = 50002 AND rm.deleted = 0 AND rm.menu_id <> 51029;

-- Uma equipe por área do catálogo. A categoria principal indica a equipe, e as filhas herdam dela.
INSERT INTO itsm.equipe (id, nome, descricao, creator, updater)
SELECT nextval('itsm.equipe_seq'), a.nome, 'Recebe os chamados da área ' || a.nome, '1', '1'
FROM (VALUES ('Usuários e acessos'), ('Suporte de informática'), ('Sistemas da saúde'), ('Telefonia'), ('Sistemas administrativos')) AS a(nome)
WHERE NOT EXISTS (SELECT 1 FROM itsm.equipe e WHERE e.deleted = 0 AND e.nome = a.nome);

INSERT INTO itsm.categoria (id, parent_id, nome, nome_completo, nivel, ordem, creator, updater)
SELECT nextval('itsm.categoria_seq'), 0, a.nome, a.nome, 1, 0, '1', '1'
FROM (VALUES ('Usuários e acessos'), ('Suporte de informática'), ('Sistemas da saúde'), ('Telefonia'), ('Sistemas administrativos')) AS a(nome)
WHERE NOT EXISTS (SELECT 1 FROM itsm.categoria c WHERE c.deleted = 0 AND c.parent_id = 0 AND c.nome = a.nome);

UPDATE itsm.categoria c
SET equipe_id = e.id, update_time = CURRENT_TIMESTAMP
FROM itsm.equipe e
WHERE c.deleted = 0 AND c.parent_id = 0 AND c.equipe_id IS NULL AND c.tecnico_id IS NULL
  AND e.deleted = 0 AND e.nome = c.nome
  AND c.nome IN ('Usuários e acessos', 'Suporte de informática', 'Sistemas da saúde', 'Telefonia', 'Sistemas administrativos');
