-- ========== F3a — inventário (CMDB): ativo único alimentado pelos agentes OCS e GLPI Agent ==========
-- Ver docs/specs/inventario-agentes.md. Protocolo conferido no servidor OCS Inventory NG 2.12.6 (GPL-2.0-or-later).

CREATE TABLE cmdb.ativo (
    id                   int8           NOT NULL,
    tipo                 varchar(20)    NOT NULL DEFAULT 'desktop',
    nome                 varchar(255)   NOT NULL,
    situacao             varchar(20)    NOT NULL DEFAULT 'em_uso',
    patrimonio           varchar(100)   NULL,
    numero_serie         varchar(100)   NULL,
    fabricante           varchar(255)   NULL,
    modelo               varchar(255)   NULL,
    uuid                 varchar(64)    NULL,
    so_nome              varchar(255)   NULL,
    so_versao            varchar(100)   NULL,
    so_arquitetura       varchar(30)    NULL,
    so_sem_suporte       bool           NOT NULL DEFAULT false,
    cpu                  varchar(255)   NULL,
    cpu_nucleos          int4           NULL,
    memoria_mb           int4           NULL,
    disco_total_mb       int8           NULL,
    disco_livre_mb       int8           NULL,
    ip                   varchar(64)    NULL,
    mac                  varchar(32)    NULL,
    dominio              varchar(255)   NULL,
    usuario_login        varchar(255)   NULL,     -- último usuário logado, como o agente informou
    usuario_id           int8           NULL,     -- pessoa do portal (vínculo com o AD pelo login)
    dept_id              int8           NULL,     -- unidade: a do usuário, a não ser que alguém tenha escolhido
    dept_manual          bool           NOT NULL DEFAULT false,
    antivirus            varchar(20)    NOT NULL DEFAULT 'desconhecido',
    origem               varchar(20)    NOT NULL DEFAULT 'manual',
    device_id            varchar(255)   NULL,     -- identificador do agente (DEVICEID)
    agente               varchar(255)   NULL,     -- User-Agent / versão do agente
    primeiro_inventario  timestamp      NULL,
    ultimo_inventario    timestamp      NULL,
    ultimo_contato       timestamp      NULL,
    observacao           text           NULL,
    creator              varchar(64)    NULL DEFAULT '',
    create_time          timestamp      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater              varchar(64)    NULL DEFAULT '',
    update_time          timestamp      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted              int2           NOT NULL DEFAULT 0,
    CONSTRAINT pk_cmdb_ativo PRIMARY KEY (id),
    CONSTRAINT ck_cmdb_ativo_tipo CHECK (tipo IN ('desktop', 'notebook', 'servidor', 'virtual', 'impressora', 'monitor', 'rede', 'telefone', 'outro')),
    CONSTRAINT ck_cmdb_ativo_situacao CHECK (situacao IN ('em_uso', 'estoque', 'manutencao', 'baixado')),
    CONSTRAINT ck_cmdb_ativo_antivirus CHECK (antivirus IN ('ativo', 'desatualizado', 'ausente', 'desconhecido')),
    CONSTRAINT ck_cmdb_ativo_origem CHECK (origem IN ('ocs', 'glpi_agent', 'manual'))
);
CREATE UNIQUE INDEX uk_cmdb_ativo_device ON cmdb.ativo (device_id) WHERE deleted = 0 AND device_id IS NOT NULL;
CREATE INDEX idx_cmdb_ativo_dept ON cmdb.ativo (dept_id) WHERE deleted = 0;
CREATE INDEX idx_cmdb_ativo_serie ON cmdb.ativo (numero_serie) WHERE deleted = 0;
CREATE INDEX idx_cmdb_ativo_uuid ON cmdb.ativo (uuid) WHERE deleted = 0;
CREATE INDEX idx_cmdb_ativo_nome ON cmdb.ativo (lower(nome)) WHERE deleted = 0;
CREATE SEQUENCE cmdb.ativo_seq START 1;

-- Peças e periféricos como o agente mandou (processador, memória, disco, volume, rede, vídeo, monitor...)
CREATE TABLE cmdb.ativo_componente (
    id           int8          NOT NULL,
    ativo_id     int8          NOT NULL,
    tipo         varchar(30)   NOT NULL,
    chave        varchar(500)  NOT NULL,   -- o que identifica a peça (série, MAC...) para o histórico de troca
    descricao    varchar(500)  NOT NULL,
    dados        text          NOT NULL DEFAULT '{}',
    creator      varchar(64)   NULL DEFAULT '',
    create_time  timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater      varchar(64)   NULL DEFAULT '',
    update_time  timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted      int2          NOT NULL DEFAULT 0,
    CONSTRAINT pk_cmdb_ativo_componente PRIMARY KEY (id)
);
CREATE INDEX idx_cmdb_componente_ativo ON cmdb.ativo_componente (ativo_id, tipo) WHERE deleted = 0;
CREATE SEQUENCE cmdb.ativo_componente_seq START 1;

-- Catálogo de software (um nome/editora, várias instalações)
CREATE TABLE cmdb.software (
    id                int8          NOT NULL,
    nome              varchar(255)  NOT NULL,
    editora           varchar(255)  NULL,
    chave             varchar(520)  NOT NULL,   -- nome e editora normalizados
    creator           varchar(64)   NULL DEFAULT '',
    create_time       timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater           varchar(64)   NULL DEFAULT '',
    update_time       timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           int2          NOT NULL DEFAULT 0,
    CONSTRAINT pk_cmdb_software PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_cmdb_software_chave ON cmdb.software (chave) WHERE deleted = 0;
CREATE SEQUENCE cmdb.software_seq START 1;

CREATE TABLE cmdb.ativo_software (
    id            int8          NOT NULL,
    ativo_id      int8          NOT NULL,
    software_id   int8          NOT NULL,
    versao        varchar(100)  NOT NULL DEFAULT '',
    instalado_em  date          NULL,
    arquitetura   varchar(20)   NULL,
    pasta         varchar(500)  NULL,
    creator       varchar(64)   NULL DEFAULT '',
    create_time   timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater       varchar(64)   NULL DEFAULT '',
    update_time   timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted       int2          NOT NULL DEFAULT 0,
    CONSTRAINT pk_cmdb_ativo_software PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_cmdb_ativo_software ON cmdb.ativo_software (ativo_id, software_id, versao) WHERE deleted = 0;
CREATE INDEX idx_cmdb_ativo_software_sw ON cmdb.ativo_software (software_id) WHERE deleted = 0;
CREATE SEQUENCE cmdb.ativo_software_seq START 1;

-- O que mudou em cada ativo (peças trocadas, softwares instalados/removidos, campos), somente inclusão
CREATE TABLE cmdb.historico (
    id           int8          NOT NULL,
    ativo_id     int8          NOT NULL,
    quando       timestamp     NOT NULL,
    tipo         varchar(30)   NOT NULL,   -- campo | componente_incluido | componente_removido | software_instalado | software_removido | agente | manual
    campo        varchar(100)  NULL,
    antes        text          NULL,
    depois       text          NULL,
    user_id      int8          NULL,       -- vazio = agente
    creator      varchar(64)   NULL DEFAULT '',
    create_time  timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater      varchar(64)   NULL DEFAULT '',
    update_time  timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted      int2          NOT NULL DEFAULT 0,
    CONSTRAINT pk_cmdb_historico PRIMARY KEY (id)
);
CREATE INDEX idx_cmdb_historico_ativo ON cmdb.historico (ativo_id, quando DESC);
CREATE SEQUENCE cmdb.historico_seq START 1;
REVOKE UPDATE, DELETE, TRUNCATE ON cmdb.historico FROM coti_app;

-- Chamado ↔ ativo
CREATE TABLE cmdb.chamado_ativo (
    id           int8          NOT NULL,
    chamado_id   int8          NOT NULL,
    ativo_id     int8          NOT NULL,
    user_id      int8          NULL,
    criado_em    timestamp     NOT NULL,
    creator      varchar(64)   NULL DEFAULT '',
    create_time  timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater      varchar(64)   NULL DEFAULT '',
    update_time  timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted      int2          NOT NULL DEFAULT 0,
    CONSTRAINT pk_cmdb_chamado_ativo PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_cmdb_chamado_ativo ON cmdb.chamado_ativo (chamado_id, ativo_id) WHERE deleted = 0;
CREATE INDEX idx_cmdb_chamado_ativo_ativo ON cmdb.chamado_ativo (ativo_id) WHERE deleted = 0;
CREATE SEQUENCE cmdb.chamado_ativo_seq START 1;

-- ========== Menus e permissões ==========
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, updater, deleted) VALUES
(53000, 'Inventário',               '', 1, 3, 0, '/inventario', 'ep:monitor', NULL, NULL, 0, true, true, true, '1', '1', 0),
(53010, 'Ativos',                   '', 2, 1, 53000, 'ativos', 'ep:cpu', 'inventario/ativo/index', 'InventarioAtivo', 0, true, true, true, '1', '1', 0),
(53011, 'Ver ativos',               'inventario:ativo:query',  3, 1, 53010, '', '', '', NULL, 0, true, true, true, '1', '1', 0),
(53012, 'Editar ativos',            'inventario:ativo:update', 3, 2, 53010, '', '', '', NULL, 0, true, true, true, '1', '1', 0),
(53020, 'Softwares',                '', 2, 2, 53000, 'softwares', 'ep:files', 'inventario/software/index', 'InventarioSoftware', 0, true, true, true, '1', '1', 0),
(53030, 'Agentes',                  '', 2, 3, 53000, 'agentes', 'ep:connection', 'inventario/agente/index', 'InventarioAgente', 0, true, true, true, '1', '1', 0);

-- Service Desk e NOC usam e editam; Gestão de TI consulta
INSERT INTO system_role_menu (id, role_id, menu_id, creator, updater, deleted, tenant_id)
SELECT nextval('system_role_menu_seq'), r, m, '1', '1', 0, 1
FROM (VALUES (50001), (50002)) AS papeis(r)
CROSS JOIN (VALUES (53000), (53010), (53011), (53012), (53020), (53030)) AS menus(m)
UNION ALL
SELECT nextval('system_role_menu_seq'), 50007, m, '1', '1', 0, 1
FROM (VALUES (53000), (53010), (53011), (53020)) AS menus(m);
