-- ========== F2c (parte 2) — pesquisa de satisfação (CSAT) e relatórios do atendimento ==========

-- Pesquisa ao resolver: o contato responde com uma nota de 1 a 5 no próprio canal
ALTER TABLE atendimento.caixa ADD COLUMN csat_ativo bool NOT NULL DEFAULT false;
ALTER TABLE atendimento.caixa ADD COLUMN csat_mensagem varchar(1000) NULL;

CREATE TABLE atendimento.pesquisa (
    id             int8          NOT NULL,
    conversa_id    int8          NOT NULL,
    caixa_id       int8          NOT NULL,
    agente_id      int8          NULL,       -- quem atendia ao resolver
    contato_id     int8          NOT NULL,
    enviada_em     timestamp     NOT NULL,
    nota           int2          NULL,
    respondida_em  timestamp     NULL,
    creator        varchar(64)   NULL DEFAULT '',
    create_time    timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater        varchar(64)   NULL DEFAULT '',
    update_time    timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted        int2          NOT NULL DEFAULT 0,
    CONSTRAINT pk_atendimento_pesquisa PRIMARY KEY (id),
    CONSTRAINT ck_atendimento_pesquisa_nota CHECK (nota IS NULL OR nota BETWEEN 1 AND 5)
);
CREATE INDEX idx_atendimento_pesquisa_conversa ON atendimento.pesquisa (conversa_id);
CREATE INDEX idx_atendimento_pesquisa_enviada ON atendimento.pesquisa (enviada_em);
CREATE SEQUENCE atendimento.pesquisa_seq START 1;

-- Fatos para os relatórios, gravados quando acontecem (a conversa muda depois: reabre, troca de atendente)
CREATE TABLE atendimento.evento_relatorio (
    id              int8          NOT NULL,
    tipo            varchar(30)   NOT NULL,   -- primeira_resposta | resolucao
    conversa_id     int8          NOT NULL,
    caixa_id        int8          NOT NULL,
    agente_id       int8          NULL,
    valor_seg       int8          NOT NULL,   -- desde a abertura da conversa
    valor_util_seg  int8          NOT NULL,   -- só o tempo dentro do expediente da caixa (sem calendário = igual)
    ocorrido_em     timestamp     NOT NULL,
    creator         varchar(64)   NULL DEFAULT '',
    create_time     timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater         varchar(64)   NULL DEFAULT '',
    update_time     timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         int2          NOT NULL DEFAULT 0,
    CONSTRAINT pk_atendimento_evento_relatorio PRIMARY KEY (id),
    CONSTRAINT ck_atendimento_evento_tipo CHECK (tipo IN ('primeira_resposta', 'resolucao'))
);
CREATE INDEX idx_atendimento_evento_quando ON atendimento.evento_relatorio (ocorrido_em, tipo);
CREATE SEQUENCE atendimento.evento_relatorio_seq START 1;

-- ========== Menus ==========
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, updater, deleted) VALUES
(52040, 'Relatórios',               '', 2, 4, 52000, 'relatorios', 'ep:data-analysis', 'atendimento/relatorio/index', 'AtendimentoRelatorio', 0, true, true, true, '1', '1', 0),
(52041, 'Ver relatórios',           'atendimento:relatorio:query', 3, 1, 52040, '', '', '', NULL, 0, true, true, true, '1', '1', 0);

-- Gestão de TI, Service Desk e NOC acompanham o atendimento
INSERT INTO system_role_menu (id, role_id, menu_id, creator, updater, deleted, tenant_id)
SELECT nextval('system_role_menu_seq'), r, m, '1', '1', 0, 1
FROM (VALUES (50007), (50001), (50002)) AS papeis(r)
CROSS JOIN (VALUES (52040), (52041)) AS menus(m)
UNION ALL
-- Gestão de TI vê o menu Atendimento (pai), que antes não tinha
SELECT nextval('system_role_menu_seq'), 50007, 52000, '1', '1', 0, 1;
