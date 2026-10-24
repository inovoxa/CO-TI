-- ========== F2c (parte 1) — horário de atendimento, macros e automações ==========
-- Ver docs/specs/atendimento-automacoes.md

-- Horário: calendário do Service Desk (expediente e feriados) e mensagem automática fora dele
ALTER TABLE atendimento.caixa ADD COLUMN calendario_id int8 NULL;
ALTER TABLE atendimento.caixa ADD COLUMN mensagem_fora_horario varchar(2000) NULL;

CREATE TABLE atendimento.macro (
    id            int8          NOT NULL,
    nome          varchar(100)  NOT NULL,
    visibilidade  varchar(10)   NOT NULL DEFAULT 'pessoal',   -- pessoal | todos
    dono_id       int8          NULL,
    acoes         text          NOT NULL DEFAULT '[]',
    creator       varchar(64)   NULL DEFAULT '',
    create_time   timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater       varchar(64)   NULL DEFAULT '',
    update_time   timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted       int2          NOT NULL DEFAULT 0,
    CONSTRAINT pk_atendimento_macro PRIMARY KEY (id),
    CONSTRAINT ck_atendimento_macro_vis CHECK (visibilidade IN ('pessoal', 'todos'))
);
CREATE SEQUENCE atendimento.macro_seq START 1;

CREATE TABLE atendimento.automacao (
    id               int8          NOT NULL,
    nome             varchar(100)  NOT NULL,
    descricao        varchar(500)  NULL,
    evento           varchar(30)   NOT NULL,
    condicoes        text          NOT NULL DEFAULT '[]',
    acoes            text          NOT NULL DEFAULT '[]',
    ativa            bool          NOT NULL DEFAULT true,
    ordem            int4          NOT NULL DEFAULT 0,
    execucoes        int8          NOT NULL DEFAULT 0,
    ultima_execucao  timestamp     NULL,
    creator          varchar(64)   NULL DEFAULT '',
    create_time      timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater          varchar(64)   NULL DEFAULT '',
    update_time      timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted          int2          NOT NULL DEFAULT 0,
    CONSTRAINT pk_atendimento_automacao PRIMARY KEY (id),
    CONSTRAINT ck_atendimento_automacao_evento CHECK (evento IN ('conversa_criada', 'mensagem_recebida', 'conversa_reaberta', 'conversa_resolvida'))
);
CREATE INDEX idx_atendimento_automacao_evento ON atendimento.automacao (evento) WHERE deleted = 0 AND ativa;
CREATE SEQUENCE atendimento.automacao_seq START 1;

-- ========== Menus ==========
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, updater, deleted) VALUES
(52025, 'Macros',                   '', 2, 3, 52000, 'macros', 'ep:magic-stick', 'atendimento/macro/index', 'AtendimentoMacro', 0, true, true, true, '1', '1', 0),
(52026, 'Macros para todos',        'atendimento:macro:todos', 3, 1, 52025, '', '', '', NULL, 0, true, true, true, '1', '1', 0),
(52035, 'Automações',               '', 2, 3, 52030, 'automacoes', 'ep:operation', 'atendimento/automacao/index', 'AtendimentoAutomacao', 0, true, true, true, '1', '1', 0),
(52036, 'Configurar automações',    'atendimento:automacao:update', 3, 1, 52035, '', '', '', NULL, 0, true, true, true, '1', '1', 0);

-- atendentes (e Service Desk / NOC, que também atendem) criam as próprias macros; SD e NOC fazem macros para todos
INSERT INTO system_role_menu (id, role_id, menu_id, creator, updater, deleted, tenant_id)
SELECT nextval('system_role_menu_seq'), r, 52025, '1', '1', 0, 1 FROM (VALUES (50008), (50001), (50002)) AS t(r)
UNION ALL
SELECT nextval('system_role_menu_seq'), r, 52026, '1', '1', 0, 1 FROM (VALUES (50001), (50002)) AS t(r);
