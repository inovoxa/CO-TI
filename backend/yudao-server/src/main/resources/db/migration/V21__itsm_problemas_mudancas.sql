-- ========== ITSM F1e — problemas e mudanças (GLPI: glpi_problems, glpi_changes e tabelas ligadas) ==========
-- Os códigos de situação são os do GLPI (CommonITILObject e Change), o que facilita importar uma base existente.

-- Problema: causa de um ou mais incidentes. status: 1 novo, 7 aceito, 2 em análise, 3 planejado, 4 pendente,
-- 5 solucionado, 8 em observação, 6 fechado
CREATE TABLE itsm.problema (
    id               int8         NOT NULL,
    titulo           varchar(255) NOT NULL,
    descricao        text         NOT NULL DEFAULT '',
    status           int2         NOT NULL DEFAULT 1,
    urgencia         int2         NOT NULL DEFAULT 3,
    impacto          int2         NOT NULL DEFAULT 3,
    prioridade       int2         NOT NULL DEFAULT 3,
    categoria_id     int8         NULL,
    dept_id          int8         NULL,
    aberto_em        timestamp    NOT NULL,
    prazo            timestamp    NULL,
    solucionado_em   timestamp    NULL,
    fechado_em       timestamp    NULL,
    impactos         text         NULL,   -- GLPI: impactcontent
    causas           text         NULL,   -- GLPI: causecontent
    sintomas         text         NULL,   -- GLPI: symptomcontent
    tempo_gasto_seg  int8         NOT NULL DEFAULT 0,
    registrado_por   int8         NULL,
    id_externo       varchar(64)  NULL,   -- id no sistema de origem (importação)
    creator          varchar(64)  NULL DEFAULT '',
    create_time      timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater          varchar(64)  NULL DEFAULT '',
    update_time      timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted          int2         NOT NULL DEFAULT 0,
    CONSTRAINT pk_itsm_problema PRIMARY KEY (id),
    CONSTRAINT ck_itsm_problema_status CHECK (status BETWEEN 1 AND 8),
    CONSTRAINT ck_itsm_problema_escalas CHECK (urgencia BETWEEN 1 AND 5 AND impacto BETWEEN 1 AND 5 AND prioridade BETWEEN 1 AND 6)
);
CREATE INDEX idx_itsm_problema_status ON itsm.problema (status) WHERE deleted = 0;
CREATE INDEX idx_itsm_problema_dept ON itsm.problema (dept_id) WHERE deleted = 0;
CREATE SEQUENCE itsm.problema_seq START 1;

-- Mudança. tipo: 1 padrão (pré-aprovada), 2 normal (precisa de aprovação), 3 emergencial.
-- status: 1 nova, 9 em avaliação, 10 em aprovação, 7 aceita, 4 pendente, 11 em teste, 12 em qualificação,
-- 5 aplicada, 8 em revisão, 6 fechada, 13 recusada, 14 cancelada
CREATE TABLE itsm.mudanca (
    id                 int8         NOT NULL,
    titulo             varchar(255) NOT NULL,
    descricao          text         NOT NULL DEFAULT '',
    tipo               int2         NOT NULL DEFAULT 2,
    status             int2         NOT NULL DEFAULT 1,
    urgencia           int2         NOT NULL DEFAULT 3,
    impacto            int2         NOT NULL DEFAULT 3,
    prioridade         int2         NOT NULL DEFAULT 3,
    categoria_id       int8         NULL,
    dept_id            int8         NULL,
    aberto_em          timestamp    NOT NULL,
    prazo              timestamp    NULL,
    inicio_previsto    timestamp    NULL,
    fim_previsto       timestamp    NULL,
    solucionado_em     timestamp    NULL,   -- aplicada em
    fechado_em         timestamp    NULL,
    analise_impacto    text         NULL,   -- GLPI: impactcontent
    plano_controle     text         NULL,   -- GLPI: controlistcontent
    plano_implantacao  text         NULL,   -- GLPI: rolloutplancontent
    plano_retorno      text         NULL,   -- GLPI: backoutplancontent
    checklist          text         NULL,   -- GLPI: checklistcontent
    validacao          int2         NOT NULL DEFAULT 1,   -- 1 nenhuma, 2 aguardando, 3 aprovada, 4 recusada
    tempo_gasto_seg    int8         NOT NULL DEFAULT 0,
    registrado_por     int8         NULL,
    id_externo         varchar(64)  NULL,
    creator            varchar(64)  NULL DEFAULT '',
    create_time        timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater            varchar(64)  NULL DEFAULT '',
    update_time        timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted            int2         NOT NULL DEFAULT 0,
    CONSTRAINT pk_itsm_mudanca PRIMARY KEY (id),
    CONSTRAINT ck_itsm_mudanca_status CHECK (status BETWEEN 1 AND 14 AND status <> 3 AND status <> 2),
    CONSTRAINT ck_itsm_mudanca_tipo CHECK (tipo BETWEEN 1 AND 3),
    CONSTRAINT ck_itsm_mudanca_validacao CHECK (validacao BETWEEN 1 AND 4),
    CONSTRAINT ck_itsm_mudanca_janela CHECK (fim_previsto IS NULL OR inicio_previsto IS NULL OR fim_previsto > inicio_previsto),
    CONSTRAINT ck_itsm_mudanca_escalas CHECK (urgencia BETWEEN 1 AND 5 AND impacto BETWEEN 1 AND 5 AND prioridade BETWEEN 1 AND 6)
);
CREATE INDEX idx_itsm_mudanca_status ON itsm.mudanca (status) WHERE deleted = 0;
CREATE INDEX idx_itsm_mudanca_dept ON itsm.mudanca (dept_id) WHERE deleted = 0;
CREATE INDEX idx_itsm_mudanca_janela ON itsm.mudanca (inicio_previsto, fim_previsto) WHERE deleted = 0;
CREATE SEQUENCE itsm.mudanca_seq START 1;

-- Participantes de problemas e mudanças (GLPI: glpi_problems_users, glpi_changes_users, *_groups).
-- objeto: 'problema' | 'mudanca'. tipo: 1 solicitante, 2 técnico, 3 observador
CREATE TABLE itsm.itil_ator (
    id          int8        NOT NULL,
    objeto      varchar(10) NOT NULL,
    objeto_id   int8        NOT NULL,
    tipo        int2        NOT NULL,
    user_id     int8        NULL,
    equipe_id   int8        NULL,
    creator     varchar(64) NULL DEFAULT '',
    create_time timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater     varchar(64) NULL DEFAULT '',
    update_time timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     int2        NOT NULL DEFAULT 0,
    CONSTRAINT pk_itsm_itil_ator PRIMARY KEY (id),
    CONSTRAINT ck_itsm_itil_ator_objeto CHECK (objeto IN ('problema', 'mudanca')),
    CONSTRAINT ck_itsm_itil_ator_tipo CHECK (tipo IN (1, 2, 3)),
    CONSTRAINT ck_itsm_itil_ator_um CHECK (num_nonnulls(user_id, equipe_id) = 1)
);
CREATE INDEX idx_itsm_itil_ator_objeto ON itsm.itil_ator (objeto, objeto_id) WHERE deleted = 0;
CREATE INDEX idx_itsm_itil_ator_user ON itsm.itil_ator (user_id, tipo) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_itsm_itil_ator_user ON itsm.itil_ator (objeto, objeto_id, tipo, user_id) WHERE deleted = 0 AND user_id IS NOT NULL;
CREATE UNIQUE INDEX uk_itsm_itil_ator_equipe ON itsm.itil_ator (objeto, objeto_id, tipo, equipe_id) WHERE deleted = 0 AND equipe_id IS NOT NULL;
CREATE SEQUENCE itsm.itil_ator_seq START 1;

-- Linha do tempo: acompanhamentos, tarefas e soluções (GLPI: ITILFollowup, ProblemTask/ChangeTask, ITILSolution).
-- Só a equipe técnica vê problemas e mudanças, então não há itens privados.
CREATE TABLE itsm.itil_interacao (
    id               int8        NOT NULL,
    objeto           varchar(10) NOT NULL,
    objeto_id        int8        NOT NULL,
    tipo             varchar(20) NOT NULL,
    user_id          int8        NULL,
    conteudo         text        NOT NULL DEFAULT '',
    estado_tarefa    int2        NULL,
    tecnico_id       int8        NULL,
    inicio           timestamp   NULL,
    fim              timestamp   NULL,
    tempo_gasto_seg  int8        NULL,
    creator          varchar(64) NULL DEFAULT '',
    create_time      timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater          varchar(64) NULL DEFAULT '',
    update_time      timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted          int2        NOT NULL DEFAULT 0,
    CONSTRAINT pk_itsm_itil_interacao PRIMARY KEY (id),
    CONSTRAINT ck_itsm_itil_interacao_objeto CHECK (objeto IN ('problema', 'mudanca')),
    CONSTRAINT ck_itsm_itil_interacao_tipo CHECK (tipo IN ('acompanhamento', 'tarefa', 'solucao'))
);
CREATE INDEX idx_itsm_itil_interacao_objeto ON itsm.itil_interacao (objeto, objeto_id, create_time) WHERE deleted = 0;
CREATE SEQUENCE itsm.itil_interacao_seq START 1;

-- Histórico (GLPI: glpi_logs), somente inserção
CREATE TABLE itsm.itil_historico (
    id           int8          NOT NULL,
    objeto       varchar(10)   NOT NULL,
    objeto_id    int8          NOT NULL,
    momento      timestamp     NOT NULL,
    user_id      int8          NULL,
    usuario      varchar(100)  NULL,
    campo        varchar(64)   NOT NULL,
    valor_antigo varchar(1000) NULL,
    valor_novo   varchar(1000) NULL,
    CONSTRAINT pk_itsm_itil_historico PRIMARY KEY (id)
);
CREATE INDEX idx_itsm_itil_historico_objeto ON itsm.itil_historico (objeto, objeto_id, id);
CREATE SEQUENCE itsm.itil_historico_seq START 1;
REVOKE UPDATE, DELETE, TRUNCATE ON itsm.itil_historico FROM coti_app;

-- Vínculos (GLPI: Problem_Ticket, Change_Ticket, Change_Problem).
-- objeto é sempre problema ou mudança; alvo é o outro lado. Mudança ↔ problema fica gravado com objeto = mudanca.
CREATE TABLE itsm.itil_vinculo (
    id          int8        NOT NULL,
    objeto      varchar(10) NOT NULL,
    objeto_id   int8        NOT NULL,
    alvo        varchar(10) NOT NULL,
    alvo_id     int8        NOT NULL,
    creator     varchar(64) NULL DEFAULT '',
    create_time timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater     varchar(64) NULL DEFAULT '',
    update_time timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     int2        NOT NULL DEFAULT 0,
    CONSTRAINT pk_itsm_itil_vinculo PRIMARY KEY (id),
    CONSTRAINT ck_itsm_itil_vinculo_par CHECK ((objeto = 'problema' AND alvo = 'chamado')
        OR (objeto = 'mudanca' AND alvo IN ('chamado', 'problema')))
);
CREATE UNIQUE INDEX uk_itsm_itil_vinculo ON itsm.itil_vinculo (objeto, objeto_id, alvo, alvo_id) WHERE deleted = 0;
CREATE INDEX idx_itsm_itil_vinculo_alvo ON itsm.itil_vinculo (alvo, alvo_id) WHERE deleted = 0;
CREATE SEQUENCE itsm.itil_vinculo_seq START 1;

-- Aprovação de mudança (GLPI: glpi_changevalidations). status: 2 aguardando, 3 aprovada, 4 recusada
CREATE TABLE itsm.mudanca_aprovacao (
    id                  int8          NOT NULL,
    mudanca_id          int8          NOT NULL,
    aprovador_id        int8          NOT NULL,
    solicitado_por      int8          NULL,
    rodada              int4          NOT NULL DEFAULT 1,   -- reaberta depois de recusada = nova rodada
    status              int2          NOT NULL DEFAULT 2,
    comentario_pedido   varchar(2000) NULL,
    comentario_resposta varchar(2000) NULL,
    pedido_em           timestamp     NOT NULL,
    respondido_em       timestamp     NULL,
    creator             varchar(64)   NULL DEFAULT '',
    create_time         timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater             varchar(64)   NULL DEFAULT '',
    update_time         timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             int2          NOT NULL DEFAULT 0,
    CONSTRAINT pk_itsm_mudanca_aprovacao PRIMARY KEY (id),
    CONSTRAINT ck_itsm_mudanca_aprovacao_status CHECK (status IN (2, 3, 4))
);
CREATE INDEX idx_itsm_mudanca_aprovacao_mudanca ON itsm.mudanca_aprovacao (mudanca_id) WHERE deleted = 0;
CREATE INDEX idx_itsm_mudanca_aprovacao_aprovador ON itsm.mudanca_aprovacao (aprovador_id, status) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_itsm_mudanca_aprovacao_pendente ON itsm.mudanca_aprovacao (mudanca_id, aprovador_id) WHERE deleted = 0 AND status = 2;
CREATE SEQUENCE itsm.mudanca_aprovacao_seq START 1;

-- ========== Menus e permissões ==========
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, updater, deleted) VALUES
(51160, 'Problemas',          '', 2, 2, 51000, 'problemas', 'ep:warning', 'itsm/problema/index', 'ItsmProblema', 0, true, true, true, '1', '1', 0),
(51161, 'Ver problemas',      'itsm:problema:query',  3, 1, 51160, '', '', '', NULL, 0, true, true, true, '1', '1', 0),
(51162, 'Registrar e atender problemas', 'itsm:problema:update', 3, 2, 51160, '', '', '', NULL, 0, true, true, true, '1', '1', 0),
(51163, 'Excluir problema',   'itsm:problema:delete', 3, 3, 51160, '', '', '', NULL, 0, true, true, true, '1', '1', 0),
(51170, 'Mudanças',           '', 2, 3, 51000, 'mudancas', 'ep:switch', 'itsm/mudanca/index', 'ItsmMudanca', 0, true, true, true, '1', '1', 0),
(51171, 'Ver mudanças',       'itsm:mudanca:query',  3, 1, 51170, '', '', '', NULL, 0, true, true, true, '1', '1', 0),
(51172, 'Registrar e conduzir mudanças', 'itsm:mudanca:update', 3, 2, 51170, '', '', '', NULL, 0, true, true, true, '1', '1', 0),
(51173, 'Excluir mudança',    'itsm:mudanca:delete', 3, 3, 51170, '', '', '', NULL, 0, true, true, true, '1', '1', 0);

-- NOC e Service Desk registram e conduzem; excluir fica para o administrador
INSERT INTO system_role_menu (id, role_id, menu_id, creator, updater, deleted, tenant_id)
SELECT nextval('system_role_menu_seq'), r, m, '1', '1', 0, 1
FROM (VALUES (50001), (50002)) AS papeis(r)
CROSS JOIN (VALUES (51160), (51161), (51162), (51170), (51171), (51172)) AS menus(m);
