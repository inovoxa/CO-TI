-- ========== Assistente interno de IA (chat do portal) ==========
-- Ver docs/specs/assistente-interno.md. As respostas vêm das ferramentas de consulta, sempre com as permissões e o
-- escopo de unidade de quem pergunta.

CREATE TABLE ia.assistente_sessao (
    id             int8          NOT NULL,
    user_id        int8          NOT NULL,
    titulo         varchar(120)  NOT NULL,
    atualizada_em  timestamp     NOT NULL,
    creator        varchar(64)   NULL DEFAULT '',
    create_time    timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater        varchar(64)   NULL DEFAULT '',
    update_time    timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted        int2          NOT NULL DEFAULT 0,
    CONSTRAINT pk_ia_assistente_sessao PRIMARY KEY (id)
);
CREATE INDEX idx_ia_assistente_sessao_user ON ia.assistente_sessao (user_id, atualizada_em DESC) WHERE deleted = 0;
CREATE SEQUENCE ia.assistente_sessao_seq START 1;

CREATE TABLE ia.assistente_mensagem (
    id           int8          NOT NULL,
    sessao_id    int8          NOT NULL,
    papel        varchar(10)   NOT NULL,   -- user | assistant
    conteudo     text          NOT NULL,
    ferramentas  text          NULL,       -- consultas feitas para responder (nome e argumentos, em JSON)
    erro         bool          NOT NULL DEFAULT false,
    criada_em    timestamp     NOT NULL,
    creator      varchar(64)   NULL DEFAULT '',
    create_time  timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater      varchar(64)   NULL DEFAULT '',
    update_time  timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted      int2          NOT NULL DEFAULT 0,
    CONSTRAINT pk_ia_assistente_mensagem PRIMARY KEY (id),
    CONSTRAINT ck_ia_assistente_mensagem_papel CHECK (papel IN ('user', 'assistant'))
);
CREATE INDEX idx_ia_assistente_mensagem_sessao ON ia.assistente_mensagem (sessao_id, id) WHERE deleted = 0;
CREATE SEQUENCE ia.assistente_mensagem_seq START 1;

-- ========== Menu ==========
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, updater, deleted) VALUES
(54000, 'Assistente IA',            '', 1, 1, 0, '/assistente', 'ep:magic-stick', NULL, NULL, 0, true, true, true, '1', '1', 0),
(54010, 'Perguntar',                '', 2, 1, 54000, 'perguntar', 'ep:chat-dot-round', 'assistente/index', 'AssistenteInterno', 0, true, true, true, '1', '1', 0),
(54011, 'Usar o assistente',        'assistente:usar', 3, 1, 54010, '', '', '', NULL, 0, true, true, true, '1', '1', 0);

-- Todos os perfis da equipe e o perfil comum (2): cada ferramenta ainda confere a permissão do próprio módulo
INSERT INTO system_role_menu (id, role_id, menu_id, creator, updater, deleted, tenant_id)
SELECT nextval('system_role_menu_seq'), r, m, '1', '1', 0, 1
FROM (VALUES (2), (50001), (50002), (50003), (50004), (50005), (50006), (50007), (50008)) AS papeis(r)
CROSS JOIN (VALUES (54000), (54010), (54011)) AS menus(m)
WHERE EXISTS (SELECT 1 FROM system_role WHERE id = r);
