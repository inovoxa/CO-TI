-- ========== F2a — atendimento: caixas de entrada, contatos, conversas e mensagens ==========
-- Referência funcional: Chatwoot 4.19 (código MIT: app/models/inbox, contact, contact_inbox, conversation, message,
-- label, canned_response). Códigos de situação e de tipo de mensagem iguais aos do Chatwoot.

-- Caixa de entrada: um canal (API, WhatsApp, Telegram...) atendido por um grupo de pessoas
CREATE TABLE atendimento.caixa (
    id                    int8          NOT NULL,
    nome                  varchar(100)  NOT NULL,
    canal                 varchar(20)   NOT NULL,
    ativo                 bool          NOT NULL DEFAULT true,
    config                text          NOT NULL DEFAULT '{}',   -- do canal; segredos cifrados com a chave-mestra
    token_hash            varchar(64)   NULL,     -- canal API: SHA-256 do token de entrada (o token é mostrado uma vez)
    webhook_url           varchar(500)  NULL,     -- canal API: para onde vão as respostas
    webhook_segredo       text          NULL,     -- canal API: segredo da assinatura HMAC, cifrado
    saudacao              varchar(2000) NULL,     -- primeira resposta automática numa conversa nova
    equipe_id             int8          NULL,     -- equipe do ITSM que atende (informativo, para o chamado)
    atribuicao_automatica bool          NOT NULL DEFAULT true,
    limite_por_agente     int4          NOT NULL DEFAULT 0,      -- 0 = sem limite (ver docs/specs/atendimento-atribuicao.md)
    reabrir_horas         int4          NOT NULL DEFAULT 24,     -- mensagem até N horas após resolver reabre a mesma conversa
    creator               varchar(64)   NULL DEFAULT '',
    create_time           timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater               varchar(64)   NULL DEFAULT '',
    update_time           timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted               int2          NOT NULL DEFAULT 0,
    CONSTRAINT pk_atendimento_caixa PRIMARY KEY (id),
    CONSTRAINT ck_atendimento_caixa_canal CHECK (canal IN ('api', 'whatsapp_cloud', 'whatsapp_evolution', 'telegram', 'widget')),
    CONSTRAINT ck_atendimento_caixa_numeros CHECK (limite_por_agente >= 0 AND reabrir_horas >= 0)
);
CREATE UNIQUE INDEX uk_atendimento_caixa_token ON atendimento.caixa (token_hash) WHERE deleted = 0 AND token_hash IS NOT NULL;
CREATE SEQUENCE atendimento.caixa_seq START 1;

CREATE TABLE atendimento.caixa_agente (
    id          int8        NOT NULL,
    caixa_id    int8        NOT NULL,
    user_id     int8        NOT NULL,
    creator     varchar(64) NULL DEFAULT '',
    create_time timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater     varchar(64) NULL DEFAULT '',
    update_time timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     int2        NOT NULL DEFAULT 0,
    CONSTRAINT pk_atendimento_caixa_agente PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_atendimento_caixa_agente ON atendimento.caixa_agente (caixa_id, user_id) WHERE deleted = 0;
CREATE INDEX idx_atendimento_caixa_agente_user ON atendimento.caixa_agente (user_id) WHERE deleted = 0;
CREATE SEQUENCE atendimento.caixa_agente_seq START 1;

-- Contato: quem fala com a central. Pode estar ligado a uma pessoa do portal (servidor da empresa).
CREATE TABLE atendimento.contato (
    id                  int8          NOT NULL,
    nome                varchar(150)  NOT NULL,
    telefone            varchar(20)   NULL,      -- E.164: +5516999990000
    email               varchar(255)  NULL,
    identificador       varchar(255)  NULL,      -- id no sistema de origem (canal API)
    user_id             int8          NULL,
    atributos           text          NOT NULL DEFAULT '{}',
    observacao          text          NULL,
    bloqueado           bool          NOT NULL DEFAULT false,
    ultima_atividade_em timestamp     NULL,
    creator             varchar(64)   NULL DEFAULT '',
    create_time         timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater             varchar(64)   NULL DEFAULT '',
    update_time         timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             int2          NOT NULL DEFAULT 0,
    CONSTRAINT pk_atendimento_contato PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_atendimento_contato_telefone ON atendimento.contato (telefone) WHERE deleted = 0 AND telefone IS NOT NULL;
CREATE UNIQUE INDEX uk_atendimento_contato_email ON atendimento.contato (lower(email)) WHERE deleted = 0 AND email IS NOT NULL;
CREATE UNIQUE INDEX uk_atendimento_contato_identificador ON atendimento.contato (identificador) WHERE deleted = 0 AND identificador IS NOT NULL;
CREATE INDEX idx_atendimento_contato_user ON atendimento.contato (user_id) WHERE deleted = 0;
CREATE SEQUENCE atendimento.contato_seq START 1;

-- O contato numa caixa: o endereço dele naquele canal (número do WhatsApp, chat do Telegram, id na API)
CREATE TABLE atendimento.contato_canal (
    id          int8          NOT NULL,
    contato_id  int8          NOT NULL,
    caixa_id    int8          NOT NULL,
    origem_id   varchar(255)  NOT NULL,
    creator     varchar(64)   NULL DEFAULT '',
    create_time timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater     varchar(64)   NULL DEFAULT '',
    update_time timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     int2          NOT NULL DEFAULT 0,
    CONSTRAINT pk_atendimento_contato_canal PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_atendimento_contato_canal ON atendimento.contato_canal (caixa_id, origem_id) WHERE deleted = 0;
CREATE INDEX idx_atendimento_contato_canal_contato ON atendimento.contato_canal (contato_id) WHERE deleted = 0;
CREATE SEQUENCE atendimento.contato_canal_seq START 1;

-- Conversa. status: 0 aberta, 1 resolvida, 2 pendente (com o robô), 3 adiada. prioridade: vazio, 1 baixa .. 4 urgente
CREATE TABLE atendimento.conversa (
    id                    int8          NOT NULL,
    caixa_id              int8          NOT NULL,
    contato_id            int8          NOT NULL,
    contato_canal_id      int8          NOT NULL,
    status                int2          NOT NULL DEFAULT 0,
    agente_id             int8          NULL,
    prioridade            int2          NULL,
    chamado_id            int8          NULL,
    adiada_ate            timestamp     NULL,
    primeira_resposta_em  timestamp     NULL,
    resolvida_em          timestamp     NULL,
    ultima_entrada_em     timestamp     NULL,   -- janela de 24 h do WhatsApp
    ultima_atividade_em   timestamp     NOT NULL,
    atributos             text          NOT NULL DEFAULT '{}',
    creator               varchar(64)   NULL DEFAULT '',
    create_time           timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater               varchar(64)   NULL DEFAULT '',
    update_time           timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted               int2          NOT NULL DEFAULT 0,
    CONSTRAINT pk_atendimento_conversa PRIMARY KEY (id),
    CONSTRAINT ck_atendimento_conversa_status CHECK (status BETWEEN 0 AND 3),
    CONSTRAINT ck_atendimento_conversa_prioridade CHECK (prioridade IS NULL OR prioridade BETWEEN 1 AND 4)
);
CREATE INDEX idx_atendimento_conversa_fila ON atendimento.conversa (caixa_id, status, ultima_atividade_em DESC) WHERE deleted = 0;
CREATE INDEX idx_atendimento_conversa_agente ON atendimento.conversa (agente_id, status) WHERE deleted = 0;
CREATE INDEX idx_atendimento_conversa_contato ON atendimento.conversa (contato_id) WHERE deleted = 0;
CREATE INDEX idx_atendimento_conversa_adiada ON atendimento.conversa (adiada_ate) WHERE deleted = 0 AND status = 3;
CREATE SEQUENCE atendimento.conversa_seq START 1;

-- Mensagem. tipo: 0 entrada (do contato), 1 saída, 2 atividade (registro do sistema), 3 modelo (template)
CREATE TABLE atendimento.mensagem (
    id              int8          NOT NULL,
    conversa_id     int8          NOT NULL,
    tipo            int2          NOT NULL,
    privada         bool          NOT NULL DEFAULT false,   -- nota interna: o contato não vê
    remetente_tipo  varchar(10)   NOT NULL,                 -- contato | agente | robo | sistema
    remetente_id    int8          NULL,
    conteudo        text          NOT NULL DEFAULT '',      -- texto puro (formatação do WhatsApp: *negrito* _itálico_)
    formato         varchar(15)   NOT NULL DEFAULT 'texto',
    anexos          text          NOT NULL DEFAULT '[]',    -- [{url, tipo, nome, tamanho}]
    id_externo      varchar(255)  NULL,
    status          varchar(10)   NOT NULL DEFAULT 'enviada',
    erro            varchar(500)  NULL,
    responde_a      int8          NULL,
    creator         varchar(64)   NULL DEFAULT '',
    create_time     timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater         varchar(64)   NULL DEFAULT '',
    update_time     timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         int2          NOT NULL DEFAULT 0,
    CONSTRAINT pk_atendimento_mensagem PRIMARY KEY (id),
    CONSTRAINT ck_atendimento_mensagem_tipo CHECK (tipo BETWEEN 0 AND 3),
    CONSTRAINT ck_atendimento_mensagem_remetente CHECK (remetente_tipo IN ('contato', 'agente', 'robo', 'sistema')),
    CONSTRAINT ck_atendimento_mensagem_status CHECK (status IN ('pendente', 'enviada', 'entregue', 'lida', 'falhou'))
);
CREATE INDEX idx_atendimento_mensagem_conversa ON atendimento.mensagem (conversa_id, id) WHERE deleted = 0;
-- mesma mensagem recebida duas vezes (o provedor reenvia o webhook) não duplica
CREATE UNIQUE INDEX uk_atendimento_mensagem_externo ON atendimento.mensagem (conversa_id, id_externo) WHERE id_externo IS NOT NULL;
CREATE INDEX idx_atendimento_mensagem_externo ON atendimento.mensagem (id_externo) WHERE id_externo IS NOT NULL;
CREATE SEQUENCE atendimento.mensagem_seq START 1;

-- Fila de envio para o canal (respostas e notificações), com novas tentativas
CREATE TABLE atendimento.envio (
    id                 int8          NOT NULL,
    mensagem_id        int8          NOT NULL,
    status             varchar(10)   NOT NULL DEFAULT 'pendente',   -- pendente | enviando | enviado | erro
    tentativas         int4          NOT NULL DEFAULT 0,
    proxima_tentativa  timestamp     NULL,
    erro               varchar(1000) NULL,
    criado_em          timestamp     NOT NULL,
    enviado_em         timestamp     NULL,
    CONSTRAINT pk_atendimento_envio PRIMARY KEY (id),
    CONSTRAINT ck_atendimento_envio_status CHECK (status IN ('pendente', 'enviando', 'enviado', 'erro'))
);
CREATE INDEX idx_atendimento_envio_fila ON atendimento.envio (status, proxima_tentativa);
CREATE SEQUENCE atendimento.envio_seq START 1;

CREATE TABLE atendimento.etiqueta (
    id          int8          NOT NULL,
    nome        varchar(50)   NOT NULL,
    cor         varchar(9)    NOT NULL DEFAULT '#6b7280',
    descricao   varchar(255)  NULL,
    creator     varchar(64)   NULL DEFAULT '',
    create_time timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater     varchar(64)   NULL DEFAULT '',
    update_time timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     int2          NOT NULL DEFAULT 0,
    CONSTRAINT pk_atendimento_etiqueta PRIMARY KEY (id),
    CONSTRAINT ck_atendimento_etiqueta_cor CHECK (cor ~ '^#[0-9a-fA-F]{6}$')
);
CREATE UNIQUE INDEX uk_atendimento_etiqueta_nome ON atendimento.etiqueta (lower(nome)) WHERE deleted = 0;
CREATE SEQUENCE atendimento.etiqueta_seq START 1;

CREATE TABLE atendimento.conversa_etiqueta (
    conversa_id int8 NOT NULL,
    etiqueta_id int8 NOT NULL,
    CONSTRAINT pk_atendimento_conversa_etiqueta PRIMARY KEY (conversa_id, etiqueta_id)
);

CREATE TABLE atendimento.resposta_pronta (
    id          int8          NOT NULL,
    atalho      varchar(50)   NOT NULL,   -- digitado depois de "/" no campo de resposta
    conteudo    text          NOT NULL,
    creator     varchar(64)   NULL DEFAULT '',
    create_time timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater     varchar(64)   NULL DEFAULT '',
    update_time timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     int2          NOT NULL DEFAULT 0,
    CONSTRAINT pk_atendimento_resposta_pronta PRIMARY KEY (id),
    CONSTRAINT ck_atendimento_resposta_atalho CHECK (atalho ~ '^[a-z0-9_-]+$')
);
CREATE UNIQUE INDEX uk_atendimento_resposta_atalho ON atendimento.resposta_pronta (atalho) WHERE deleted = 0;
CREATE SEQUENCE atendimento.resposta_pronta_seq START 1;

-- Disponibilidade do atendente (o navegador manda sinal a cada minuto)
CREATE TABLE atendimento.agente_status (
    user_id         int8          NOT NULL,
    disponibilidade varchar(10)   NOT NULL DEFAULT 'offline',   -- online | ocupado | offline
    ultimo_sinal    timestamp     NULL,
    ultima_atribuicao timestamp   NULL,
    CONSTRAINT pk_atendimento_agente_status PRIMARY KEY (user_id),
    CONSTRAINT ck_atendimento_agente_disp CHECK (disponibilidade IN ('online', 'ocupado', 'offline'))
);

-- Até onde cada pessoa leu cada conversa (contador de não lidas)
CREATE TABLE atendimento.conversa_leitura (
    conversa_id int8      NOT NULL,
    user_id     int8      NOT NULL,
    lida_ate    int8      NOT NULL,   -- id da última mensagem vista
    CONSTRAINT pk_atendimento_conversa_leitura PRIMARY KEY (conversa_id, user_id)
);

-- ========== Menus, permissões e perfil ==========
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, updater, deleted) VALUES
(52000, 'Atendimento',              '', 1, 1, 0, '/atendimento', 'ep:chat-dot-round', NULL, NULL, 0, true, true, true, '1', '1', 0),
(52010, 'Conversas',                '', 2, 1, 52000, 'conversas', 'ep:chat-line-round', 'atendimento/conversa/index', 'AtendimentoConversa', 0, true, true, true, '1', '1', 0),
(52011, 'Atender conversas',        'atendimento:conversa:query',  3, 1, 52010, '', '', '', NULL, 0, true, true, true, '1', '1', 0),
(52012, 'Ver todas as caixas',      'atendimento:conversa:todas',  3, 2, 52010, '', '', '', NULL, 0, true, true, true, '1', '1', 0),
(52013, 'Atribuir a outras pessoas','atendimento:conversa:atribuir', 3, 3, 52010, '', '', '', NULL, 0, true, true, true, '1', '1', 0),
(52020, 'Contatos',                 '', 2, 2, 52000, 'contatos', 'ep:postcard', 'atendimento/contato/index', 'AtendimentoContato', 0, true, true, true, '1', '1', 0),
(52021, 'Ver contatos',             'atendimento:contato:query',  3, 1, 52020, '', '', '', NULL, 0, true, true, true, '1', '1', 0),
(52022, 'Editar contatos',          'atendimento:contato:update', 3, 2, 52020, '', '', '', NULL, 0, true, true, true, '1', '1', 0),
(52030, 'Configuração',             '', 1, 9, 52000, 'configuracao', 'ep:setting', NULL, NULL, 0, true, true, true, '1', '1', 0),
(52031, 'Caixas de entrada',        '', 2, 1, 52030, 'caixas', 'ep:message-box', 'atendimento/caixa/index', 'AtendimentoCaixa', 0, true, true, true, '1', '1', 0),
(52032, 'Configurar caixas',        'atendimento:caixa:update', 3, 1, 52031, '', '', '', NULL, 0, true, true, true, '1', '1', 0),
(52033, 'Etiquetas e respostas prontas', '', 2, 2, 52030, 'etiquetas', 'ep:collection-tag', 'atendimento/etiqueta/index', 'AtendimentoEtiqueta', 0, true, true, true, '1', '1', 0),
(52034, 'Configurar etiquetas e respostas', 'atendimento:etiqueta:update', 3, 1, 52033, '', '', '', NULL, 0, true, true, true, '1', '1', 0);

INSERT INTO system_role (id, name, code, sort, data_scope, data_scope_dept_ids, status, type, remark, creator, updater, deleted, tenant_id) VALUES
(50008, 'Atendente', 'atendente', 17, 1, '', 0, 2, 'Conversas das caixas de que participa, contatos e abertura de chamado', '1', '1', 0, 1);

INSERT INTO system_role_menu (id, role_id, menu_id, creator, updater, deleted, tenant_id)
-- atendente: conversas e contatos; abre e consulta chamados e a base de conhecimento
SELECT nextval('system_role_menu_seq'), 50008, m, '1', '1', 0, 1
FROM (VALUES (52000), (52010), (52011), (52020), (52021), (52022), (51000), (51010), (51011), (51012), (51100), (51130)) AS t(m)
UNION ALL
-- Service Desk e NOC também atendem (hoje fazem isso no Chatwoot) e distribuem conversas
SELECT nextval('system_role_menu_seq'), r, m, '1', '1', 0, 1
FROM (VALUES (50001), (50002)) AS papeis(r)
CROSS JOIN (VALUES (52000), (52010), (52011), (52013), (52020), (52021), (52022)) AS menus(m);
