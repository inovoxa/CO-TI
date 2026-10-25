-- ========== F3b — alertas do inventário e software × licenças ==========
-- Ver docs/specs/inventario-alertas-licencas.md

-- Quais itens do catálogo de software contam como uso de uma licença do ITSM
CREATE TABLE cmdb.licenca_software (
    id           int8          NOT NULL,
    licenca_id   int8          NOT NULL,   -- itsm.licenca
    software_id  int8          NOT NULL,   -- cmdb.software
    user_id      int8          NULL,
    creator      varchar(64)   NULL DEFAULT '',
    create_time  timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater      varchar(64)   NULL DEFAULT '',
    update_time  timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted      int2          NOT NULL DEFAULT 0,
    CONSTRAINT pk_cmdb_licenca_software PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_cmdb_licenca_software ON cmdb.licenca_software (licenca_id, software_id) WHERE deleted = 0;
CREATE INDEX idx_cmdb_licenca_software_sw ON cmdb.licenca_software (software_id) WHERE deleted = 0;
CREATE SEQUENCE cmdb.licenca_software_seq START 1;

-- Regras de alerta (uma linha por regra fixa; a equipe liga, ajusta o limite e decide se abre chamado)
CREATE TABLE cmdb.alerta_regra (
    id             int8          NOT NULL,
    codigo         varchar(30)   NOT NULL,
    nome           varchar(100)  NOT NULL,
    ativo          bool          NOT NULL DEFAULT true,
    abrir_chamado  bool          NOT NULL DEFAULT false,
    limite         int4          NULL,       -- disco_cheio: % livre; sem_contato: dias
    categoria_id   int8          NULL,       -- categoria do chamado aberto
    urgencia       int2          NOT NULL DEFAULT 3,
    creator        varchar(64)   NULL DEFAULT '',
    create_time    timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater        varchar(64)   NULL DEFAULT '',
    update_time    timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted        int2          NOT NULL DEFAULT 0,
    CONSTRAINT pk_cmdb_alerta_regra PRIMARY KEY (id),
    CONSTRAINT ck_cmdb_alerta_regra_urgencia CHECK (urgencia BETWEEN 1 AND 5)
);
CREATE UNIQUE INDEX uk_cmdb_alerta_regra ON cmdb.alerta_regra (codigo) WHERE deleted = 0;
CREATE SEQUENCE cmdb.alerta_regra_seq START 10;

INSERT INTO cmdb.alerta_regra (id, codigo, nome, ativo, abrir_chamado, limite, urgencia) VALUES
(1, 'sem_suporte',      'Sistema operacional sem suporte do fabricante', true, false, NULL, 3),
(2, 'antivirus',        'Antivírus ausente ou desatualizado',             true, false, NULL, 4),
(3, 'disco_cheio',      'Disco quase cheio',                              true, false, 10,   3),
(4, 'sem_contato',      'Computador sem comunicação',                     true, false, 30,   2),
(5, 'licenca_excedida', 'Licença com mais instalações que o comprado',    true, false, NULL, 3);

-- Alertas: abertos enquanto a condição existe (fim vazio); o histórico fica
CREATE TABLE cmdb.alerta_ativo (
    id           int8          NOT NULL,
    regra        varchar(30)   NOT NULL,
    objeto       varchar(10)   NOT NULL,    -- ativo | licenca
    objeto_id    int8          NOT NULL,
    titulo       varchar(255)  NOT NULL,
    detalhe      varchar(500)  NULL,
    dept_id      int8          NULL,        -- escopo por unidade
    inicio       timestamp     NOT NULL,
    fim          timestamp     NULL,
    chamado_id   int8          NULL,
    creator      varchar(64)   NULL DEFAULT '',
    create_time  timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater      varchar(64)   NULL DEFAULT '',
    update_time  timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted      int2          NOT NULL DEFAULT 0,
    CONSTRAINT pk_cmdb_alerta_ativo PRIMARY KEY (id),
    CONSTRAINT ck_cmdb_alerta_ativo_objeto CHECK (objeto IN ('ativo', 'licenca'))
);
CREATE UNIQUE INDEX uk_cmdb_alerta_aberto ON cmdb.alerta_ativo (regra, objeto, objeto_id) WHERE deleted = 0 AND fim IS NULL;
CREATE INDEX idx_cmdb_alerta_objeto ON cmdb.alerta_ativo (objeto, objeto_id) WHERE deleted = 0;
CREATE INDEX idx_cmdb_alerta_dept ON cmdb.alerta_ativo (dept_id) WHERE deleted = 0;
CREATE SEQUENCE cmdb.alerta_ativo_seq START 1;

-- ========== Menus e permissões ==========
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, updater, deleted) VALUES
(53040, 'Alertas',                  '', 2, 4, 53000, 'alertas', 'ep:bell', 'inventario/alerta/index', 'InventarioAlerta', 0, true, true, true, '1', '1', 0),
(53041, 'Configurar alertas',       'inventario:alerta:update', 3, 1, 53040, '', '', '', NULL, 0, true, true, true, '1', '1', 0);

INSERT INTO system_role_menu (id, role_id, menu_id, creator, updater, deleted, tenant_id)
SELECT nextval('system_role_menu_seq'), r, m, '1', '1', 0, 1
FROM (VALUES (50001), (50002)) AS papeis(r)
CROSS JOIN (VALUES (53040), (53041)) AS menus(m)
UNION ALL
SELECT nextval('system_role_menu_seq'), 50007, 53040, '1', '1', 0, 1;
