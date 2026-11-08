-- ========== Lista de ramais e telefones ==========
-- Lista mantida à mão pela TI, separada do cadastro de usuários: o celular do cadastro é dado pessoal e não entra aqui
-- sem alguém decidir publicá-lo.

CREATE TABLE itsm.telefone (
    id           bigint        NOT NULL,
    nome         varchar(120)  NOT NULL,   -- pessoa, sala ou serviço
    setor        varchar(120)  NULL,
    unidade      varchar(120)  NULL,
    ramal        varchar(20)   NULL,
    telefone     varchar(30)   NULL,
    email        varchar(128)  NULL,
    observacao   varchar(255)  NULL,
    creator      varchar(64)   NULL DEFAULT '',
    create_time  timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater      varchar(64)   NULL DEFAULT '',
    update_time  timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted      int2          NOT NULL DEFAULT 0,
    CONSTRAINT pk_itsm_telefone PRIMARY KEY (id),
    CONSTRAINT ck_itsm_telefone_numero CHECK (ramal IS NOT NULL OR telefone IS NOT NULL)
);
CREATE SEQUENCE itsm.telefone_seq START 1;
CREATE INDEX idx_itsm_telefone_nome ON itsm.telefone (lower(nome)) WHERE deleted = 0;

INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, updater, deleted) VALUES
(59000, 'Ramais e telefones', '', 2, 3, 0, '/telefones', 'ep:phone', 'itsm/telefone/index', 'ItsmTelefone', 0, true, true, false, '1', '1', 0),
(59001, 'Ver a lista', 'itsm:telefone:query', 3, 1, 59000, '', '', '', NULL, 0, true, true, true, '1', '1', 0),
(59002, 'Manter a lista', 'itsm:telefone:update', 3, 2, 59000, '', '', '', NULL, 0, true, true, true, '1', '1', 0);

-- Consulta: os mesmos papéis da base de conhecimento (quase todo mundo). Manutenção: Service Desk e Gestão de TI.
INSERT INTO system_role_menu (id, role_id, menu_id, creator, updater, deleted, tenant_id)
SELECT nextval('system_role_menu_seq'), r, m, '1', '1', 0, 1
FROM (VALUES (2), (50001), (50002), (50003), (50004), (50005), (50007), (50008)) AS papeis(r)
CROSS JOIN (VALUES (59000), (59001)) AS menus(m);

INSERT INTO system_role_menu (id, role_id, menu_id, creator, updater, deleted, tenant_id)
SELECT nextval('system_role_menu_seq'), r, 59002, '1', '1', 0, 1
FROM (VALUES (50002), (50007)) AS papeis(r);
