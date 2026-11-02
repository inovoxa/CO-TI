-- ========== Cofre de senhas ==========
-- Pastas compartilhadas por pessoa. Senhas e notas ficam cifradas com a chave-mestra (AES-256-GCM) e o id do segredo
-- entra como dado autenticado: o texto cifrado copiado para outra linha não abre.

CREATE TABLE cofre.pasta (
    id           bigint        NOT NULL,
    nome         varchar(120)  NOT NULL,
    descricao    varchar(500)  NULL,
    creator      varchar(64)   NULL DEFAULT '',
    create_time  timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater      varchar(64)   NULL DEFAULT '',
    update_time  timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted      int2          NOT NULL DEFAULT 0,
    CONSTRAINT pk_cofre_pasta PRIMARY KEY (id)
);
CREATE SEQUENCE cofre.pasta_seq START 1;
CREATE UNIQUE INDEX uk_cofre_pasta_nome ON cofre.pasta (lower(nome)) WHERE deleted = 0;

-- papel: 1 vê as senhas, 2 também cadastra e altera, 3 também decide quem entra na pasta
CREATE TABLE cofre.pasta_membro (
    id           bigint        NOT NULL,
    pasta_id     bigint        NOT NULL,
    user_id      bigint        NOT NULL,
    papel        int2          NOT NULL,
    creator      varchar(64)   NULL DEFAULT '',
    create_time  timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater      varchar(64)   NULL DEFAULT '',
    update_time  timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted      int2          NOT NULL DEFAULT 0,
    CONSTRAINT pk_cofre_pasta_membro PRIMARY KEY (id),
    CONSTRAINT ck_cofre_pasta_membro_papel CHECK (papel IN (1, 2, 3))
);
CREATE SEQUENCE cofre.pasta_membro_seq START 1;
CREATE UNIQUE INDEX uk_cofre_pasta_membro ON cofre.pasta_membro (pasta_id, user_id) WHERE deleted = 0;
CREATE INDEX idx_cofre_pasta_membro_user ON cofre.pasta_membro (user_id) WHERE deleted = 0;

CREATE TABLE cofre.segredo (
    id                bigint        NOT NULL,
    pasta_id          bigint        NOT NULL,
    nome              varchar(200)  NOT NULL,
    usuario           varchar(200)  NULL,
    url               varchar(500)  NULL,
    senha_cifrada     text          NOT NULL,
    notas_cifradas    text          NULL,
    senha_alterada_em timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    creator           varchar(64)   NULL DEFAULT '',
    create_time       timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater           varchar(64)   NULL DEFAULT '',
    update_time       timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           int2          NOT NULL DEFAULT 0,
    CONSTRAINT pk_cofre_segredo PRIMARY KEY (id)
);
-- o id vem da sequência antes do insert, porque entra na cifragem
CREATE SEQUENCE cofre.segredo_seq START 1;
CREATE INDEX idx_cofre_segredo_pasta ON cofre.segredo (pasta_id) WHERE deleted = 0;

-- Menu e permissões. Ter o menu não abre nenhuma senha: só vê a pasta quem é membro dela.
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, updater, deleted) VALUES
(58000, 'Cofre de senhas', '', 2, 8, 0, '/cofre', 'ep:lock', 'cofre/index', 'CofreSenhas', 0, true, true, false, '1', '1', 0),
(58001, 'Usar o cofre', 'cofre:segredo:usar', 3, 1, 58000, '', '', '', NULL, 0, true, true, true, '1', '1', 0),
(58002, 'Administrar membros de todas as pastas', 'cofre:pasta:administrar', 3, 2, 58000, '', '', '', NULL, 0, true, true, true, '1', '1', 0);

-- Dado à mão (Sistema → Usuários → Atribuir papel). A administração das pastas fica só com o administrador do sistema.
INSERT INTO system_role (id, name, code, sort, data_scope, data_scope_dept_ids, status, type, remark, creator, updater, deleted, tenant_id) VALUES
(50011, 'Cofre de senhas', 'cofre', 20, 5, '', 0, 2, 'Pode usar o cofre; só vê as pastas em que for incluído', '1', '1', 0, 1);

INSERT INTO system_role_menu (id, role_id, menu_id, creator, updater, deleted, tenant_id)
SELECT nextval('system_role_menu_seq'), 50011, m, '1', '1', 0, 1
FROM (VALUES (58000), (58001)) AS menus(m);
