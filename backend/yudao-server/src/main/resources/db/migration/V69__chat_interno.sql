-- Chat interno. Cada mensagem tem um número sequencial na conversa: é por ele que a tela busca o que perdeu ao reconectar
-- e conta as não lidas. Mensagem apagada ou editada guarda o texto anterior, para apuração.
CREATE SCHEMA IF NOT EXISTS chat;
GRANT USAGE ON SCHEMA chat TO coti_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA chat GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO coti_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA chat GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO coti_app;

CREATE TABLE chat.conversa (
    id                  int8         NOT NULL,
    tipo                varchar(8)   NOT NULL,          -- direta | grupo
    nome                varchar(100) NULL,              -- grupo
    par_chave           varchar(50)  NULL,              -- direta: "menorId:maiorId", garante uma conversa por par
    ultima_seq          int8         NOT NULL DEFAULT 0,
    ultima_mensagem_em  timestamp    NULL,
    criado_por          int8         NOT NULL,
    creator             varchar(64)  NULL DEFAULT '',
    create_time         timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater             varchar(64)  NULL DEFAULT '',
    update_time         timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             int2         NOT NULL DEFAULT 0,
    CONSTRAINT pk_chat_conversa PRIMARY KEY (id),
    CONSTRAINT ck_chat_conversa_tipo CHECK (tipo IN ('direta', 'grupo'))
);
CREATE UNIQUE INDEX uk_chat_conversa_par ON chat.conversa (par_chave) WHERE par_chave IS NOT NULL AND deleted = 0;
CREATE SEQUENCE chat.conversa_seq START 1;

CREATE TABLE chat.participante (
    id              int8        NOT NULL,
    conversa_id     int8        NOT NULL,
    user_id         int8        NOT NULL,
    papel           varchar(8)  NOT NULL DEFAULT 'membro',  -- admin | membro
    ultima_lida_seq int8        NOT NULL DEFAULT 0,
    desde_seq       int8        NOT NULL DEFAULT 0,         -- só vê as mensagens depois de entrar
    creator         varchar(64) NULL DEFAULT '',
    create_time     timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater         varchar(64) NULL DEFAULT '',
    update_time     timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         int2        NOT NULL DEFAULT 0,
    CONSTRAINT pk_chat_participante PRIMARY KEY (id),
    CONSTRAINT ck_chat_participante_papel CHECK (papel IN ('admin', 'membro'))
);
CREATE UNIQUE INDEX uk_chat_participante ON chat.participante (conversa_id, user_id) WHERE deleted = 0;
CREATE INDEX idx_chat_participante_user ON chat.participante (user_id) WHERE deleted = 0;
CREATE SEQUENCE chat.participante_seq START 1;

CREATE TABLE chat.mensagem (
    id           int8          NOT NULL,
    conversa_id  int8          NOT NULL,
    seq          int8          NOT NULL,
    remetente_id int8          NOT NULL,
    tipo         varchar(8)    NOT NULL DEFAULT 'texto',  -- texto | sistema
    conteudo     text          NOT NULL DEFAULT '',
    resposta_id  int8          NULL,
    mencoes      varchar(1000) NULL,                      -- números das pessoas citadas, separados por vírgula
    cliente_id   varchar(40)   NULL,                      -- gerado pela tela: reenviar não duplica a mensagem
    editada_em   timestamp     NULL,
    apagada_em   timestamp     NULL,
    creator      varchar(64)   NULL DEFAULT '',
    create_time  timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater      varchar(64)   NULL DEFAULT '',
    update_time  timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted      int2          NOT NULL DEFAULT 0,
    CONSTRAINT pk_chat_mensagem PRIMARY KEY (id),
    CONSTRAINT ck_chat_mensagem_tipo CHECK (tipo IN ('texto', 'sistema'))
);
CREATE UNIQUE INDEX uk_chat_mensagem_seq ON chat.mensagem (conversa_id, seq);
CREATE UNIQUE INDEX uk_chat_mensagem_cliente ON chat.mensagem (conversa_id, remetente_id, cliente_id) WHERE cliente_id IS NOT NULL;
CREATE SEQUENCE chat.mensagem_seq START 1;

CREATE TABLE chat.mensagem_historico (
    id          int8        NOT NULL,
    mensagem_id int8        NOT NULL,
    acao        varchar(8)  NOT NULL,   -- editada | apagada
    conteudo    text        NOT NULL,   -- o texto antes da mudança
    user_id     int8        NOT NULL,
    quando      timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    creator     varchar(64) NULL DEFAULT '',
    create_time timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater     varchar(64) NULL DEFAULT '',
    update_time timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     int2        NOT NULL DEFAULT 0,
    CONSTRAINT pk_chat_mensagem_historico PRIMARY KEY (id)
);
CREATE INDEX idx_chat_historico_mensagem ON chat.mensagem_historico (mensagem_id);
CREATE SEQUENCE chat.mensagem_historico_seq START 1;

CREATE TABLE chat.anexo (
    id           int8         NOT NULL,
    conversa_id  int8         NOT NULL,
    mensagem_id  int8         NULL,      -- vazio até a mensagem ser enviada
    nome         varchar(200) NOT NULL,
    tipo_mime    varchar(100) NOT NULL,
    tamanho      int8         NOT NULL,
    caminho      varchar(200) NOT NULL,  -- gerado pelo servidor, nunca a partir do nome enviado
    imagem       bool         NOT NULL DEFAULT false,
    enviado_por  int8         NOT NULL,
    creator      varchar(64)  NULL DEFAULT '',
    create_time  timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater      varchar(64)  NULL DEFAULT '',
    update_time  timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted      int2         NOT NULL DEFAULT 0,
    CONSTRAINT pk_chat_anexo PRIMARY KEY (id)
);
CREATE INDEX idx_chat_anexo_mensagem ON chat.anexo (mensagem_id) WHERE mensagem_id IS NOT NULL;
CREATE SEQUENCE chat.anexo_seq START 1;

INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, updater, deleted) VALUES
(59200, 'Chat', '', 2, 2, 0, '/chat', 'ep:chat-dot-round', 'chat/index', 'Chat', 0, true, true, false, '1', '1', 0),
(59201, 'Usar o chat', 'chat:usar', 3, 1, 59200, '', '', '', NULL, 0, true, true, true, '1', '1', 0);

-- Todo servidor com login conversa; o telão é uma tela fixa, não uma pessoa
INSERT INTO system_role_menu (id, role_id, menu_id, creator, updater, deleted, tenant_id)
SELECT nextval('system_role_menu_seq'), r.id, m.id, '1', '1', 0, 1
FROM system_role r
CROSS JOIN (VALUES (59200), (59201)) AS m(id)
WHERE r.deleted = 0 AND r.code <> 'telao';
