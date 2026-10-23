-- ========== ITSM — núcleo de chamados (estrutura equivalente às tabelas ITIL do GLPI 12) ==========

-- Canais de entrada (GLPI: glpi_requesttypes)
CREATE TABLE itsm.origem (
    id           int8         NOT NULL,
    nome         varchar(100) NOT NULL,
    padrao_portal bool        NOT NULL DEFAULT false,
    padrao_email bool         NOT NULL DEFAULT false,
    ativo        bool         NOT NULL DEFAULT true,
    creator      varchar(64)  NULL DEFAULT '',
    create_time  timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater      varchar(64)  NULL DEFAULT '',
    update_time  timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted      int2         NOT NULL DEFAULT 0,
    CONSTRAINT pk_itsm_origem PRIMARY KEY (id)
);
CREATE SEQUENCE itsm.origem_seq START 100;
INSERT INTO itsm.origem (id, nome, padrao_portal, padrao_email) VALUES
(1, 'Portal', true, false), (2, 'E-mail', false, true), (3, 'Telefone', false, false),
(4, 'WhatsApp', false, false), (5, 'Presencial', false, false), (6, 'Monitoramento', false, false),
(7, 'Outro', false, false);

-- Equipes técnicas (GLPI: glpi_groups com is_assign)
CREATE TABLE itsm.equipe (
    id          int8         NOT NULL,
    nome        varchar(100) NOT NULL,
    descricao   varchar(500) NULL,
    email       varchar(128) NULL,
    grupo_ad    varchar(255) NULL,
    ativo       bool         NOT NULL DEFAULT true,
    creator     varchar(64)  NULL DEFAULT '',
    create_time timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater     varchar(64)  NULL DEFAULT '',
    update_time timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     int2         NOT NULL DEFAULT 0,
    CONSTRAINT pk_itsm_equipe PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_itsm_equipe_nome ON itsm.equipe (lower(nome)) WHERE deleted = 0;
CREATE SEQUENCE itsm.equipe_seq START 1;

CREATE TABLE itsm.equipe_membro (
    id          int8        NOT NULL,
    equipe_id   int8        NOT NULL,
    user_id     int8        NOT NULL,
    supervisor  bool        NOT NULL DEFAULT false,
    creator     varchar(64) NULL DEFAULT '',
    create_time timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater     varchar(64) NULL DEFAULT '',
    update_time timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     int2        NOT NULL DEFAULT 0,
    CONSTRAINT pk_itsm_equipe_membro PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_itsm_equipe_membro ON itsm.equipe_membro (equipe_id, user_id) WHERE deleted = 0;
CREATE INDEX idx_itsm_equipe_membro_user ON itsm.equipe_membro (user_id) WHERE deleted = 0;
CREATE SEQUENCE itsm.equipe_membro_seq START 1;

-- Categorias em árvore (GLPI: glpi_itilcategories)
CREATE TABLE itsm.categoria (
    id              int8          NOT NULL,
    parent_id       int8          NOT NULL DEFAULT 0,
    nome            varchar(100)  NOT NULL,
    nome_completo   varchar(1000) NOT NULL,
    nivel           int4          NOT NULL DEFAULT 1,
    codigo          varchar(64)   NULL,
    descricao       varchar(1000) NULL,
    tecnico_id      int8          NULL,
    equipe_id       int8          NULL,
    visivel_portal  bool          NOT NULL DEFAULT true,
    para_incidente  bool          NOT NULL DEFAULT true,
    para_requisicao bool          NOT NULL DEFAULT true,
    ativo           bool          NOT NULL DEFAULT true,
    ordem           int4          NOT NULL DEFAULT 0,
    creator         varchar(64)   NULL DEFAULT '',
    create_time     timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater         varchar(64)   NULL DEFAULT '',
    update_time     timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         int2          NOT NULL DEFAULT 0,
    CONSTRAINT pk_itsm_categoria PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_itsm_categoria_nome ON itsm.categoria (parent_id, lower(nome)) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_itsm_categoria_codigo ON itsm.categoria (codigo) WHERE deleted = 0 AND codigo IS NOT NULL;
CREATE SEQUENCE itsm.categoria_seq START 1;

-- Chamado (GLPI: glpi_tickets)
CREATE TABLE itsm.chamado (
    id                   int8          NOT NULL,
    titulo               varchar(255)  NOT NULL,
    descricao            text          NOT NULL DEFAULT '',
    tipo                 int2          NOT NULL DEFAULT 1,
    status               int2          NOT NULL DEFAULT 1,
    urgencia             int2          NOT NULL DEFAULT 3,
    impacto              int2          NOT NULL DEFAULT 3,
    prioridade           int2          NOT NULL DEFAULT 3,
    categoria_id         int8          NULL,
    origem_id            int8          NULL,
    dept_id              int8          NULL,
    aberto_em            timestamp     NOT NULL,
    assumido_em          timestamp     NULL,
    solucionado_em       timestamp     NULL,
    fechado_em           timestamp     NULL,
    sla_atendimento_id   int8          NULL,
    sla_solucao_id       int8          NULL,
    prazo_atendimento    timestamp     NULL,
    prazo_solucao        timestamp     NULL,
    inicio_espera        timestamp     NULL,
    espera_seg           int8          NOT NULL DEFAULT 0,
    espera_sla_seg       int8          NOT NULL DEFAULT 0,
    tempo_assumir_seg    int8          NULL,
    tempo_solucao_seg    int8          NULL,
    tempo_fechamento_seg int8          NULL,
    tempo_gasto_seg      int8          NOT NULL DEFAULT 0,
    registrado_por       int8          NULL,
    origem_automatica    varchar(64)   NULL,
    id_externo           varchar(255)  NULL,
    creator              varchar(64)   NULL DEFAULT '',
    create_time          timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater              varchar(64)   NULL DEFAULT '',
    update_time          timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted              int2          NOT NULL DEFAULT 0,
    CONSTRAINT pk_itsm_chamado PRIMARY KEY (id),
    CONSTRAINT ck_itsm_chamado_status CHECK (status BETWEEN 1 AND 6),
    CONSTRAINT ck_itsm_chamado_tipo CHECK (tipo IN (1, 2)),
    CONSTRAINT ck_itsm_chamado_escalas CHECK (urgencia BETWEEN 1 AND 5 AND impacto BETWEEN 1 AND 5 AND prioridade BETWEEN 1 AND 6)
);
CREATE INDEX idx_itsm_chamado_status ON itsm.chamado (status) WHERE deleted = 0;
CREATE INDEX idx_itsm_chamado_dept ON itsm.chamado (dept_id) WHERE deleted = 0;
CREATE INDEX idx_itsm_chamado_aberto_em ON itsm.chamado (aberto_em DESC) WHERE deleted = 0;
CREATE INDEX idx_itsm_chamado_prazo ON itsm.chamado (prazo_solucao) WHERE deleted = 0 AND status < 5;
-- um alerta automático aberto por vez para o mesmo objeto monitorado
CREATE UNIQUE INDEX uk_itsm_chamado_externo_aberto ON itsm.chamado (origem_automatica, id_externo)
    WHERE deleted = 0 AND status < 5 AND id_externo IS NOT NULL;
CREATE SEQUENCE itsm.chamado_seq START 1;

-- Atores (GLPI: glpi_tickets_users + glpi_groups_tickets)
CREATE TABLE itsm.chamado_ator (
    id          int8         NOT NULL,
    chamado_id  int8         NOT NULL,
    tipo        int2         NOT NULL,
    user_id     int8         NULL,
    equipe_id   int8         NULL,
    email       varchar(255) NULL,
    notificar   bool         NOT NULL DEFAULT true,
    creator     varchar(64)  NULL DEFAULT '',
    create_time timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater     varchar(64)  NULL DEFAULT '',
    update_time timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     int2         NOT NULL DEFAULT 0,
    CONSTRAINT pk_itsm_chamado_ator PRIMARY KEY (id),
    CONSTRAINT ck_itsm_chamado_ator_tipo CHECK (tipo IN (1, 2, 3)),
    CONSTRAINT ck_itsm_chamado_ator_um CHECK (num_nonnulls(user_id, equipe_id, email) = 1)
);
CREATE INDEX idx_itsm_ator_chamado ON itsm.chamado_ator (chamado_id) WHERE deleted = 0;
CREATE INDEX idx_itsm_ator_user ON itsm.chamado_ator (user_id, tipo) WHERE deleted = 0;
CREATE INDEX idx_itsm_ator_equipe ON itsm.chamado_ator (equipe_id, tipo) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_itsm_ator_user ON itsm.chamado_ator (chamado_id, tipo, user_id) WHERE deleted = 0 AND user_id IS NOT NULL;
CREATE UNIQUE INDEX uk_itsm_ator_equipe ON itsm.chamado_ator (chamado_id, tipo, equipe_id) WHERE deleted = 0 AND equipe_id IS NOT NULL;
CREATE SEQUENCE itsm.chamado_ator_seq START 1;

-- Linha do tempo: acompanhamentos, tarefas e soluções
CREATE TABLE itsm.interacao (
    id               int8        NOT NULL,
    chamado_id       int8        NOT NULL,
    tipo             varchar(20) NOT NULL,
    user_id          int8        NULL,
    conteudo         text        NOT NULL DEFAULT '',
    privado          bool        NOT NULL DEFAULT false,
    origem_id        int8        NULL,
    estado_tarefa    int2        NULL,
    tecnico_id       int8        NULL,
    equipe_id        int8        NULL,
    inicio           timestamp   NULL,
    fim              timestamp   NULL,
    tempo_gasto_seg  int8        NULL,
    status_solucao   int2        NULL,
    avaliado_por     int8        NULL,
    avaliado_em      timestamp   NULL,
    creator          varchar(64) NULL DEFAULT '',
    create_time      timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater          varchar(64) NULL DEFAULT '',
    update_time      timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted          int2        NOT NULL DEFAULT 0,
    CONSTRAINT pk_itsm_interacao PRIMARY KEY (id),
    CONSTRAINT ck_itsm_interacao_tipo CHECK (tipo IN ('acompanhamento', 'tarefa', 'solucao'))
);
CREATE INDEX idx_itsm_interacao_chamado ON itsm.interacao (chamado_id, create_time) WHERE deleted = 0;
CREATE SEQUENCE itsm.interacao_seq START 1;

-- Histórico de alterações (GLPI: glpi_logs), somente inserção
CREATE TABLE itsm.historico (
    id           int8         NOT NULL,
    chamado_id   int8         NOT NULL,
    momento      timestamp    NOT NULL,
    user_id      int8         NULL,
    usuario      varchar(100) NULL,
    campo        varchar(64)  NOT NULL,
    valor_antigo varchar(1000) NULL,
    valor_novo   varchar(1000) NULL,
    CONSTRAINT pk_itsm_historico PRIMARY KEY (id)
);
CREATE INDEX idx_itsm_historico_chamado ON itsm.historico (chamado_id, id);
CREATE SEQUENCE itsm.historico_seq START 1;
REVOKE UPDATE, DELETE, TRUNCATE ON itsm.historico FROM coti_app;

-- ========== Menus e permissões ==========
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, updater, deleted) VALUES
(51000, 'Service Desk',        '', 1, 2, 0,     '/servicedesk', 'ep:headset', NULL, NULL, 0, true, true, true, '1', '1', 0),
(51010, 'Chamados',            '', 2, 1, 51000, 'chamados', 'ep:tickets', 'itsm/chamado/index', 'ItsmChamado', 0, true, true, true, '1', '1', 0),
(51011, 'Ver chamados',        'itsm:chamado:query',        3, 1, 51010, '', '', '', NULL, 0, true, true, true, '1', '1', 0),
(51012, 'Abrir chamado',       'itsm:chamado:create',       3, 2, 51010, '', '', '', NULL, 0, true, true, true, '1', '1', 0),
(51013, 'Editar chamado',      'itsm:chamado:update',       3, 3, 51010, '', '', '', NULL, 0, true, true, true, '1', '1', 0),
(51014, 'Atribuir técnicos',   'itsm:chamado:atribuir',     3, 4, 51010, '', '', '', NULL, 0, true, true, true, '1', '1', 0),
(51015, 'Atender (assumir, tarefas, solução)', 'itsm:chamado:atender', 3, 5, 51010, '', '', '', NULL, 0, true, true, true, '1', '1', 0),
(51016, 'Ver itens privados',  'itsm:chamado:ver-privado',  3, 6, 51010, '', '', '', NULL, 0, true, true, true, '1', '1', 0),
(51017, 'Fechar sem aprovação','itsm:chamado:fechar',       3, 7, 51010, '', '', '', NULL, 0, true, true, true, '1', '1', 0),
(51018, 'Reabrir fechado',     'itsm:chamado:reabrir',      3, 8, 51010, '', '', '', NULL, 0, true, true, true, '1', '1', 0),
(51019, 'Excluir chamado',     'itsm:chamado:delete',       3, 9, 51010, '', '', '', NULL, 0, true, true, true, '1', '1', 0),
(51020, 'Configuração',        '', 1, 9, 51000, 'configuracao', 'ep:setting', NULL, NULL, 0, true, true, true, '1', '1', 0),
(51030, 'Categorias',          '', 2, 1, 51020, 'categorias', 'ep:folder-opened', 'itsm/categoria/index', 'ItsmCategoria', 0, true, true, true, '1', '1', 0),
(51031, 'Ver categorias',      'itsm:categoria:query',  3, 1, 51030, '', '', '', NULL, 0, true, true, true, '1', '1', 0),
(51032, 'Criar categoria',     'itsm:categoria:create', 3, 2, 51030, '', '', '', NULL, 0, true, true, true, '1', '1', 0),
(51033, 'Editar categoria',    'itsm:categoria:update', 3, 3, 51030, '', '', '', NULL, 0, true, true, true, '1', '1', 0),
(51034, 'Excluir categoria',   'itsm:categoria:delete', 3, 4, 51030, '', '', '', NULL, 0, true, true, true, '1', '1', 0),
(51040, 'Equipes',             '', 2, 2, 51020, 'equipes', 'ep:user-filled', 'itsm/equipe/index', 'ItsmEquipe', 0, true, true, true, '1', '1', 0),
(51041, 'Ver equipes',         'itsm:equipe:query',  3, 1, 51040, '', '', '', NULL, 0, true, true, true, '1', '1', 0),
(51042, 'Criar equipe',        'itsm:equipe:create', 3, 2, 51040, '', '', '', NULL, 0, true, true, true, '1', '1', 0),
(51043, 'Editar equipe',       'itsm:equipe:update', 3, 3, 51040, '', '', '', NULL, 0, true, true, true, '1', '1', 0),
(51044, 'Excluir equipe',      'itsm:equipe:delete', 3, 4, 51040, '', '', '', NULL, 0, true, true, true, '1', '1', 0),
(51050, 'Origens',             '', 2, 3, 51020, 'origens', 'ep:position', 'itsm/origem/index', 'ItsmOrigem', 0, true, true, true, '1', '1', 0),
(51051, 'Ver origens',         'itsm:origem:query',  3, 1, 51050, '', '', '', NULL, 0, true, true, true, '1', '1', 0),
(51052, 'Criar origem',        'itsm:origem:create', 3, 2, 51050, '', '', '', NULL, 0, true, true, true, '1', '1', 0),
(51053, 'Editar origem',       'itsm:origem:update', 3, 3, 51050, '', '', '', NULL, 0, true, true, true, '1', '1', 0),
(51054, 'Excluir origem',      'itsm:origem:delete', 3, 4, 51050, '', '', '', NULL, 0, true, true, true, '1', '1', 0);

-- Service Desk e NOC atendem; gestor da unidade só acompanha
INSERT INTO system_role_menu (id, role_id, menu_id, creator, updater, deleted, tenant_id)
SELECT nextval('system_role_menu_seq'), r, m, '1', '1', 0, 1
FROM (VALUES (50002), (50001)) AS papeis(r)
CROSS JOIN (VALUES (51000), (51010), (51011), (51012), (51013), (51014), (51015), (51016), (51017),
                   (51031), (51041), (51051)) AS menus(m)
UNION ALL
SELECT nextval('system_role_menu_seq'), 50005, m, '1', '1', 0, 1 FROM (VALUES (51000), (51010), (51011), (51012)) AS t(m);
