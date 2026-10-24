-- ========== ITSM — SLA, calendários, escalonamento e satisfação (GLPI: glpi_calendars, glpi_slas, glpi_slalevels) ==========

CREATE TABLE itsm.calendario (
    id          int8         NOT NULL,
    nome        varchar(100) NOT NULL,
    descricao   varchar(500) NULL,
    creator     varchar(64)  NULL DEFAULT '',
    create_time timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater     varchar(64)  NULL DEFAULT '',
    update_time timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     int2         NOT NULL DEFAULT 0,
    CONSTRAINT pk_itsm_calendario PRIMARY KEY (id)
);
CREATE SEQUENCE itsm.calendario_seq START 100;

-- Períodos de expediente (GLPI: glpi_calendarsegments). dia_semana ISO: 1 = segunda ... 7 = domingo
CREATE TABLE itsm.calendario_periodo (
    id            int8        NOT NULL,
    calendario_id int8        NOT NULL,
    dia_semana    int2        NOT NULL,
    inicio        time        NOT NULL,
    fim           time        NOT NULL,   -- 00:00 = meia-noite do fim do dia
    creator       varchar(64) NULL DEFAULT '',
    create_time   timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater       varchar(64) NULL DEFAULT '',
    update_time   timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted       int2        NOT NULL DEFAULT 0,
    CONSTRAINT pk_itsm_calendario_periodo PRIMARY KEY (id),
    CONSTRAINT ck_itsm_periodo_dia CHECK (dia_semana BETWEEN 1 AND 7),
    CONSTRAINT ck_itsm_periodo_horas CHECK (fim = '00:00' OR fim > inicio)
);
CREATE INDEX idx_itsm_periodo_calendario ON itsm.calendario_periodo (calendario_id) WHERE deleted = 0;
CREATE SEQUENCE itsm.calendario_periodo_seq START 1;

-- Feriados (GLPI: glpi_holidays) e em quais calendários valem (glpi_calendars_holidays)
CREATE TABLE itsm.feriado (
    id          int8         NOT NULL,
    nome        varchar(100) NOT NULL,
    data_inicio date         NOT NULL,
    data_fim    date         NOT NULL,
    perpetuo    bool         NOT NULL DEFAULT false,
    creator     varchar(64)  NULL DEFAULT '',
    create_time timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater     varchar(64)  NULL DEFAULT '',
    update_time timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     int2         NOT NULL DEFAULT 0,
    CONSTRAINT pk_itsm_feriado PRIMARY KEY (id),
    CONSTRAINT ck_itsm_feriado_datas CHECK (data_fim >= data_inicio OR perpetuo)
);
CREATE SEQUENCE itsm.feriado_seq START 100;

CREATE TABLE itsm.calendario_feriado (
    id            int8        NOT NULL,
    calendario_id int8        NOT NULL,
    feriado_id    int8        NOT NULL,
    creator       varchar(64) NULL DEFAULT '',
    create_time   timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater       varchar(64) NULL DEFAULT '',
    update_time   timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted       int2        NOT NULL DEFAULT 0,
    CONSTRAINT pk_itsm_calendario_feriado PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_itsm_calendario_feriado ON itsm.calendario_feriado (calendario_id, feriado_id) WHERE deleted = 0;
CREATE SEQUENCE itsm.calendario_feriado_seq START 1;

-- SLA (GLPI: glpi_slas). tipo: 1 = atendimento (TTO), 0 = solução (TTR), os mesmos códigos do GLPI
CREATE TABLE itsm.sla (
    id               int8         NOT NULL,
    nome             varchar(100) NOT NULL,
    tipo             int2         NOT NULL,
    tempo            int4         NOT NULL,
    unidade          varchar(10)  NOT NULL,
    calendario_id    int8         NULL,          -- vazio = 24 h por dia, 7 dias por semana
    fim_expediente   bool         NOT NULL DEFAULT false,
    descricao        varchar(500) NULL,
    ativo            bool         NOT NULL DEFAULT true,
    creator          varchar(64)  NULL DEFAULT '',
    create_time      timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater          varchar(64)  NULL DEFAULT '',
    update_time      timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted          int2         NOT NULL DEFAULT 0,
    CONSTRAINT pk_itsm_sla PRIMARY KEY (id),
    CONSTRAINT ck_itsm_sla_tipo CHECK (tipo IN (0, 1)),
    CONSTRAINT ck_itsm_sla_unidade CHECK (unidade IN ('minuto', 'hora', 'dia', 'mes')),
    CONSTRAINT ck_itsm_sla_tempo CHECK (tempo >= 0),
    CONSTRAINT ck_itsm_sla_fim CHECK (NOT fim_expediente OR unidade IN ('dia', 'mes'))
);
CREATE SEQUENCE itsm.sla_seq START 1;

-- Níveis de escalonamento (GLPI: glpi_slalevels). tempo_seg relativo ao prazo: negativo = antes de vencer
CREATE TABLE itsm.sla_nivel (
    id                int8         NOT NULL,
    sla_id            int8         NOT NULL,
    nome              varchar(100) NOT NULL,
    tempo_seg         int8         NOT NULL,
    atribuir_equipe_id int8        NULL,
    atribuir_tecnico_id int8       NULL,
    nova_prioridade   int2         NULL,
    ativo             bool         NOT NULL DEFAULT true,
    creator           varchar(64)  NULL DEFAULT '',
    create_time       timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater           varchar(64)  NULL DEFAULT '',
    update_time       timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           int2         NOT NULL DEFAULT 0,
    CONSTRAINT pk_itsm_sla_nivel PRIMARY KEY (id),
    CONSTRAINT ck_itsm_sla_nivel_prioridade CHECK (nova_prioridade IS NULL OR nova_prioridade BETWEEN 1 AND 6)
);
CREATE INDEX idx_itsm_sla_nivel_sla ON itsm.sla_nivel (sla_id) WHERE deleted = 0;
CREATE SEQUENCE itsm.sla_nivel_seq START 1;

-- Níveis já executados em cada chamado (não repete o mesmo escalonamento)
CREATE TABLE itsm.sla_nivel_execucao (
    id           int8      NOT NULL,
    chamado_id   int8      NOT NULL,
    nivel_id     int8      NOT NULL,
    executado_em timestamp NOT NULL,
    CONSTRAINT pk_itsm_sla_nivel_execucao PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_itsm_sla_nivel_execucao ON itsm.sla_nivel_execucao (chamado_id, nivel_id);
CREATE SEQUENCE itsm.sla_nivel_execucao_seq START 1;

-- SLA padrão por prioridade (quando a categoria não define)
CREATE TABLE itsm.sla_prioridade (
    prioridade         int2 NOT NULL,
    sla_atendimento_id int8 NULL,
    sla_solucao_id     int8 NULL,
    CONSTRAINT pk_itsm_sla_prioridade PRIMARY KEY (prioridade),
    CONSTRAINT ck_itsm_sla_prioridade CHECK (prioridade BETWEEN 1 AND 6)
);
INSERT INTO itsm.sla_prioridade (prioridade) VALUES (1), (2), (3), (4), (5), (6);

ALTER TABLE itsm.categoria ADD COLUMN sla_atendimento_id int8 NULL;
ALTER TABLE itsm.categoria ADD COLUMN sla_solucao_id int8 NULL;

-- Pesquisa de satisfação (GLPI: glpi_ticketsatisfactions), nota de 1 a 5
CREATE TABLE itsm.satisfacao (
    id            int8          NOT NULL,
    chamado_id    int8          NOT NULL,
    enviada_em    timestamp     NOT NULL,
    respondida_em timestamp     NULL,
    nota          int2          NULL,
    comentario    varchar(2000) NULL,
    CONSTRAINT pk_itsm_satisfacao PRIMARY KEY (id),
    CONSTRAINT ck_itsm_satisfacao_nota CHECK (nota IS NULL OR nota BETWEEN 1 AND 5)
);
CREATE UNIQUE INDEX uk_itsm_satisfacao_chamado ON itsm.satisfacao (chamado_id);
CREATE SEQUENCE itsm.satisfacao_seq START 1;

-- Calendário de exemplo: comercial, com os feriados nacionais fixos
INSERT INTO itsm.calendario (id, nome, descricao) VALUES (1, 'Comercial', 'Segunda a sexta, 08:00–12:00 e 13:00–18:00');
INSERT INTO itsm.calendario_periodo (id, calendario_id, dia_semana, inicio, fim)
SELECT nextval('itsm.calendario_periodo_seq'), 1, d, p.i, p.f
FROM generate_series(1, 5) AS d
CROSS JOIN (VALUES ('08:00'::time, '12:00'::time), ('13:00'::time, '18:00'::time)) AS p(i, f);

INSERT INTO itsm.feriado (id, nome, data_inicio, data_fim, perpetuo) VALUES
(1, 'Confraternização Universal', '2000-01-01', '2000-01-01', true),
(2, 'Tiradentes', '2000-04-21', '2000-04-21', true),
(3, 'Dia do Trabalho', '2000-05-01', '2000-05-01', true),
(4, 'Independência', '2000-09-07', '2000-09-07', true),
(5, 'Nossa Senhora Aparecida', '2000-10-12', '2000-10-12', true),
(6, 'Finados', '2000-11-02', '2000-11-02', true),
(7, 'Proclamação da República', '2000-11-15', '2000-11-15', true),
(8, 'Dia da Consciência Negra', '2000-11-20', '2000-11-20', true),
(9, 'Natal', '2000-12-25', '2000-12-25', true);
INSERT INTO itsm.calendario_feriado (id, calendario_id, feriado_id)
SELECT nextval('itsm.calendario_feriado_seq'), 1, id FROM itsm.feriado;

-- ========== Menus e permissões ==========
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, updater, deleted) VALUES
(51060, 'SLA',                '', 2, 4, 51020, 'sla', 'ep:timer', 'itsm/sla/index', 'ItsmSla', 0, true, true, true, '1', '1', 0),
(51061, 'Ver SLA',            'itsm:sla:query',  3, 1, 51060, '', '', '', NULL, 0, true, true, true, '1', '1', 0),
(51062, 'Configurar SLA',     'itsm:sla:update', 3, 2, 51060, '', '', '', NULL, 0, true, true, true, '1', '1', 0),
(51070, 'Calendários',        '', 2, 5, 51020, 'calendarios', 'ep:calendar', 'itsm/calendario/index', 'ItsmCalendario', 0, true, true, true, '1', '1', 0),
(51071, 'Ver calendários',    'itsm:calendario:query',  3, 1, 51070, '', '', '', NULL, 0, true, true, true, '1', '1', 0),
(51072, 'Configurar calendários', 'itsm:calendario:update', 3, 2, 51070, '', '', '', NULL, 0, true, true, true, '1', '1', 0);

INSERT INTO system_role_menu (id, role_id, menu_id, creator, updater, deleted, tenant_id)
SELECT nextval('system_role_menu_seq'), r, m, '1', '1', 0, 1
FROM (VALUES (50001), (50002)) AS papeis(r) CROSS JOIN (VALUES (51061), (51071)) AS menus(m);
