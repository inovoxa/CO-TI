-- ========== ITSM F1c — aprovações, regras, catálogo de formulários e execução no AD ==========

-- ---------- Aprovações (GLPI: glpi_ticketvalidations). status: 2 aguardando, 3 aprovada, 4 recusada ----------
ALTER TABLE itsm.chamado ADD COLUMN validacao int2 NOT NULL DEFAULT 1;   -- 1 nenhuma, 2 aguardando, 3 aprovada, 4 recusada
ALTER TABLE itsm.chamado ADD CONSTRAINT ck_itsm_chamado_validacao CHECK (validacao BETWEEN 1 AND 4);

CREATE TABLE itsm.aprovacao (
    id                  int8          NOT NULL,
    chamado_id          int8          NOT NULL,
    aprovador_id        int8          NOT NULL,
    solicitado_por      int8          NULL,          -- vazio = sistema (regra ou formulário)
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
    CONSTRAINT pk_itsm_aprovacao PRIMARY KEY (id),
    CONSTRAINT ck_itsm_aprovacao_status CHECK (status IN (2, 3, 4))
);
CREATE INDEX idx_itsm_aprovacao_chamado ON itsm.aprovacao (chamado_id) WHERE deleted = 0;
CREATE INDEX idx_itsm_aprovacao_aprovador ON itsm.aprovacao (aprovador_id, status) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_itsm_aprovacao_pendente ON itsm.aprovacao (chamado_id, aprovador_id) WHERE deleted = 0 AND status = 2;
CREATE SEQUENCE itsm.aprovacao_seq START 1;

-- ---------- Regras de negócio (GLPI: glpi_rules / glpi_rulecriterias / glpi_ruleactions, RuleTicket) ----------
CREATE TABLE itsm.regra (
    id          int8         NOT NULL,
    nome        varchar(100) NOT NULL,
    descricao   varchar(500) NULL,
    ordem       int4         NOT NULL DEFAULT 0,
    ativo       bool         NOT NULL DEFAULT true,
    combinacao  varchar(3)   NOT NULL DEFAULT 'E',   -- E = todos os critérios; OU = qualquer um
    parar       bool         NOT NULL DEFAULT false, -- não avalia as regras seguintes se esta casar
    criterios   text         NOT NULL DEFAULT '[]',  -- [{campo, operador, valor}]
    acoes       text         NOT NULL DEFAULT '[]',  -- [{acao, valor}]
    creator     varchar(64)  NULL DEFAULT '',
    create_time timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater     varchar(64)  NULL DEFAULT '',
    update_time timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     int2         NOT NULL DEFAULT 0,
    CONSTRAINT pk_itsm_regra PRIMARY KEY (id),
    CONSTRAINT ck_itsm_regra_combinacao CHECK (combinacao IN ('E', 'OU'))
);
CREATE SEQUENCE itsm.regra_seq START 1;

-- ---------- Catálogo de serviços / formulários (GLPI: Glpi\Form) ----------
CREATE TABLE itsm.formulario (
    id               int8          NOT NULL,
    codigo           varchar(32)   NULL,           -- ex.: "1.1", aparece no menu do WhatsApp
    nome             varchar(150)  NOT NULL,
    descricao        varchar(2000) NULL,
    grupo            varchar(100)  NOT NULL,       -- seção do catálogo, ex.: "Usuários e Acessos"
    icone            varchar(64)   NULL,
    categoria_id     int8          NULL,
    tipo             int2          NOT NULL DEFAULT 2,
    urgencia         int2          NOT NULL DEFAULT 3,
    impacto          int2          NOT NULL DEFAULT 3,
    titulo_modelo    varchar(255)  NULL,           -- ex.: "Criação de usuário: {nome_completo}"
    definicao        text          NOT NULL,       -- {secoes:[{nome, perguntas:[{name,label,type,required,values,regex}]}]}
    aprovacao        varchar(16)   NOT NULL DEFAULT 'nenhuma', -- nenhuma | gestor | campo | fixo
    aprovador_campo  varchar(64)   NULL,           -- pergunta com o e-mail do aprovador (aprovacao = campo)
    aprovador_id     int8          NULL,           -- aprovacao = fixo
    acao_ad          varchar(16)   NULL,           -- Criar | Reset | Reativar | Desativar | AcessoPasta | Transferir | CriarEmail
    ativo            bool          NOT NULL DEFAULT true,
    ordem            int4          NOT NULL DEFAULT 0,
    creator          varchar(64)   NULL DEFAULT '',
    create_time      timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater          varchar(64)   NULL DEFAULT '',
    update_time      timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted          int2          NOT NULL DEFAULT 0,
    CONSTRAINT pk_itsm_formulario PRIMARY KEY (id),
    CONSTRAINT ck_itsm_formulario_aprovacao CHECK (aprovacao IN ('nenhuma', 'gestor', 'campo', 'fixo')),
    CONSTRAINT ck_itsm_formulario_acao CHECK (acao_ad IS NULL OR acao_ad IN ('Criar', 'Reset', 'Reativar', 'Desativar', 'AcessoPasta', 'Transferir', 'CriarEmail'))
);
CREATE UNIQUE INDEX uk_itsm_formulario_codigo ON itsm.formulario (codigo) WHERE deleted = 0 AND codigo IS NOT NULL;
CREATE SEQUENCE itsm.formulario_seq START 1;

-- Respostas enviadas (uma por chamado aberto pelo formulário)
CREATE TABLE itsm.formulario_resposta (
    id            int8        NOT NULL,
    formulario_id int8        NOT NULL,
    chamado_id    int8        NOT NULL,
    user_id       int8        NOT NULL,
    respostas     text        NOT NULL,   -- {name: valor}
    criado_em     timestamp   NOT NULL,
    CONSTRAINT pk_itsm_formulario_resposta PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_itsm_resposta_chamado ON itsm.formulario_resposta (chamado_id);
CREATE SEQUENCE itsm.formulario_resposta_seq START 1;

-- ---------- Execução de operações no AD (substitui chamados_log + WF14 do n8n) ----------
-- status: aguardando_aprovacao | pronta | executando | sucesso | erro | recusada | cancelada
CREATE TABLE itsm.execucao_ad (
    id               int8          NOT NULL,
    chamado_id       int8          NOT NULL,
    acao             varchar(16)   NOT NULL,
    dados            text          NOT NULL,   -- JSON enviado ao script (respostas do formulário)
    status           varchar(24)   NOT NULL,
    tentativas       int4          NOT NULL DEFAULT 0,
    proxima_tentativa timestamp    NULL,
    resultado        text          NULL,       -- JSON devolvido pelo script, SEM a senha
    mensagem         varchar(2000) NULL,
    senha_cifrada    varchar(512)  NULL,       -- senha provisória cifrada (AES-GCM, chave-mestra)
    senha_vista_em   timestamp     NULL,       -- revelada uma vez ao solicitante e apagada
    criado_em        timestamp     NOT NULL,
    executado_em     timestamp     NULL,
    CONSTRAINT pk_itsm_execucao_ad PRIMARY KEY (id),
    CONSTRAINT ck_itsm_execucao_status CHECK (status IN ('aguardando_aprovacao', 'pronta', 'executando', 'sucesso', 'erro', 'recusada', 'cancelada'))
);
CREATE UNIQUE INDEX uk_itsm_execucao_chamado ON itsm.execucao_ad (chamado_id);
CREATE INDEX idx_itsm_execucao_status ON itsm.execucao_ad (status);
CREATE SEQUENCE itsm.execucao_ad_seq START 1;

-- ---------- Menus e permissões ----------
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, updater, deleted) VALUES
(51080, 'Formulários',          '', 2, 6, 51020, 'formularios', 'ep:document', 'itsm/formulario/index', 'ItsmFormulario', 0, true, true, true, '1', '1', 0),
(51081, 'Ver formulários',      'itsm:formulario:query',  3, 1, 51080, '', '', '', NULL, 0, true, true, true, '1', '1', 0),
(51082, 'Configurar formulários', 'itsm:formulario:update', 3, 2, 51080, '', '', '', NULL, 0, true, true, true, '1', '1', 0),
(51090, 'Regras',               '', 2, 7, 51020, 'regras', 'ep:operation', 'itsm/regra/index', 'ItsmRegra', 0, true, true, true, '1', '1', 0),
(51091, 'Ver regras',           'itsm:regra:query',  3, 1, 51090, '', '', '', NULL, 0, true, true, true, '1', '1', 0),
(51092, 'Configurar regras',    'itsm:regra:update', 3, 2, 51090, '', '', '', NULL, 0, true, true, true, '1', '1', 0),
(51095, 'Execuções no AD',      '', 2, 2, 51000, 'execucoes-ad', 'ep:cpu', 'itsm/execucao/index', 'ItsmExecucaoAd', 0, true, true, true, '1', '1', 0),
(51096, 'Ver execuções no AD',  'itsm:execucao:query', 3, 1, 51095, '', '', '', NULL, 0, true, true, true, '1', '1', 0),
(51097, 'Repetir ou cancelar execução', 'itsm:execucao:update', 3, 2, 51095, '', '', '', NULL, 0, true, true, true, '1', '1', 0),
(51098, 'Pedir aprovação',      'itsm:chamado:pedir-aprovacao', 3, 10, 51010, '', '', '', NULL, 0, true, true, true, '1', '1', 0),
(51110, 'Catálogo de serviços', '', 2, 0, 0, '/catalogo', 'ep:grid', 'itsm/catalogo/index', 'ItsmCatalogo', 0, true, true, false, '1', '1', 0),
(51120, 'Minhas aprovações',    '', 2, 2, 0, '/aprovacoes', 'ep:select', 'itsm/aprovacao/index', 'ItsmAprovacao', 0, true, true, false, '1', '1', 0);

-- catálogo e aprovações para todos; consulta de configuração e execuções para NOC/Service Desk
INSERT INTO system_role_menu (id, role_id, menu_id, creator, updater, deleted, tenant_id)
SELECT nextval('system_role_menu_seq'), r, m, '1', '1', 0, 1
FROM (VALUES (2), (50001), (50002), (50003), (50004), (50005)) AS papeis(r)
CROSS JOIN (VALUES (51110), (51120)) AS menus(m)
UNION ALL
SELECT nextval('system_role_menu_seq'), r, m, '1', '1', 0, 1
FROM (VALUES (50001), (50002)) AS papeis(r)
CROSS JOIN (VALUES (51080), (51081), (51090), (51091), (51095), (51096), (51097), (51098)) AS menus(m);
