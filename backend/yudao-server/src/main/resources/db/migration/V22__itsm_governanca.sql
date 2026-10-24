-- ========== ITSM F1e (parte 2) — governança: fornecedores, contratos, licenças, orçamentos e projetos ==========
-- Referência funcional: GLPI 12 (Supplier, Contract, ContractCost, SoftwareLicense, Budget, Project, ProjectTask).
-- Valores em reais, numeric(15,2). Contratos, licenças, orçamentos e projetos têm unidade (dept_id) e seguem o
-- escopo de dados do perfil, como os chamados; fornecedor é cadastro único da empresa.

CREATE TABLE itsm.fornecedor (
    id           int8          NOT NULL,
    nome         varchar(150)  NOT NULL,
    tipo         varchar(60)   NULL,          -- ex.: fabricante, revenda, prestador de serviço, operadora
    documento    varchar(20)   NULL,          -- CNPJ ou CPF, só dígitos
    email        varchar(255)  NULL,
    telefone     varchar(40)   NULL,
    site         varchar(255)  NULL,
    contato      varchar(150)  NULL,          -- pessoa de contato
    endereco     varchar(255)  NULL,
    cidade       varchar(100)  NULL,
    uf           varchar(2)    NULL,
    cep          varchar(9)    NULL,
    observacao   text          NULL,
    ativo        bool          NOT NULL DEFAULT true,
    id_externo   varchar(64)   NULL,
    creator      varchar(64)   NULL DEFAULT '',
    create_time  timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater      varchar(64)   NULL DEFAULT '',
    update_time  timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted      int2          NOT NULL DEFAULT 0,
    CONSTRAINT pk_itsm_fornecedor PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_itsm_fornecedor_documento ON itsm.fornecedor (documento) WHERE deleted = 0 AND documento IS NOT NULL;
CREATE SEQUENCE itsm.fornecedor_seq START 1;

CREATE TABLE itsm.orcamento (
    id           int8          NOT NULL,
    nome         varchar(150)  NOT NULL,
    tipo         varchar(60)   NULL,          -- ex.: custeio, investimento
    dept_id      int8          NULL,
    inicio       date          NOT NULL,
    fim          date          NOT NULL,
    valor        numeric(15,2) NOT NULL,
    observacao   text          NULL,
    creator      varchar(64)   NULL DEFAULT '',
    create_time  timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater      varchar(64)   NULL DEFAULT '',
    update_time  timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted      int2          NOT NULL DEFAULT 0,
    CONSTRAINT pk_itsm_orcamento PRIMARY KEY (id),
    CONSTRAINT ck_itsm_orcamento_periodo CHECK (fim >= inicio),
    CONSTRAINT ck_itsm_orcamento_valor CHECK (valor >= 0)
);
CREATE INDEX idx_itsm_orcamento_dept ON itsm.orcamento (dept_id) WHERE deleted = 0;
CREATE SEQUENCE itsm.orcamento_seq START 1;

-- Contrato. renovacao: 0 não renova, 1 tácita (renova sozinho a cada período), 2 expressa (precisa renovar)
CREATE TABLE itsm.contrato (
    id                  int8          NOT NULL,
    nome                varchar(150)  NOT NULL,
    numero              varchar(60)   NULL,
    tipo                varchar(60)   NULL,   -- ex.: manutenção, locação, suporte, link de dados
    fornecedor_id       int8          NULL,
    dept_id             int8          NULL,
    responsavel_id      int8          NULL,   -- recebe os avisos de vencimento
    inicio              date          NOT NULL,
    duracao_meses       int4          NOT NULL,
    aviso_previo_meses  int4          NOT NULL DEFAULT 0,   -- antecedência exigida para cancelar ou negociar
    renovacao           int2          NOT NULL DEFAULT 0,
    alerta_dias         int4          NOT NULL DEFAULT 30,  -- quantos dias antes do prazo avisar
    numero_contabil     varchar(60)   NULL,
    observacao          text          NULL,
    encerrado           bool          NOT NULL DEFAULT false,
    alerta_referencia   date          NULL,   -- prazo para o qual o aviso já foi enviado
    id_externo          varchar(64)   NULL,
    creator             varchar(64)   NULL DEFAULT '',
    create_time         timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater             varchar(64)   NULL DEFAULT '',
    update_time         timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             int2          NOT NULL DEFAULT 0,
    CONSTRAINT pk_itsm_contrato PRIMARY KEY (id),
    CONSTRAINT ck_itsm_contrato_duracao CHECK (duracao_meses > 0 AND aviso_previo_meses >= 0 AND aviso_previo_meses < duracao_meses),
    CONSTRAINT ck_itsm_contrato_renovacao CHECK (renovacao IN (0, 1, 2)),
    CONSTRAINT ck_itsm_contrato_alerta CHECK (alerta_dias BETWEEN 0 AND 365)
);
CREATE INDEX idx_itsm_contrato_dept ON itsm.contrato (dept_id) WHERE deleted = 0;
CREATE INDEX idx_itsm_contrato_fornecedor ON itsm.contrato (fornecedor_id) WHERE deleted = 0;
CREATE SEQUENCE itsm.contrato_seq START 1;

-- Renovações expressas registradas (o período é somado à duração)
CREATE TABLE itsm.contrato_renovacao (
    id           int8          NOT NULL,
    contrato_id  int8          NOT NULL,
    meses        int4          NOT NULL,
    fim_antes    date          NOT NULL,
    fim_depois   date          NOT NULL,
    observacao   varchar(1000) NULL,
    creator      varchar(64)   NULL DEFAULT '',
    create_time  timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater      varchar(64)   NULL DEFAULT '',
    update_time  timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted      int2          NOT NULL DEFAULT 0,
    CONSTRAINT pk_itsm_contrato_renovacao PRIMARY KEY (id),
    CONSTRAINT ck_itsm_contrato_renovacao_meses CHECK (meses > 0)
);
CREATE INDEX idx_itsm_contrato_renovacao ON itsm.contrato_renovacao (contrato_id) WHERE deleted = 0;
CREATE SEQUENCE itsm.contrato_renovacao_seq START 1;

-- Custos lançados (GLPI: glpi_contractcosts e glpi_projectcosts). objeto: contrato | projeto
CREATE TABLE itsm.custo (
    id           int8          NOT NULL,
    objeto       varchar(10)   NOT NULL,
    objeto_id    int8          NOT NULL,
    nome         varchar(150)  NOT NULL,
    inicio       date          NOT NULL,
    fim          date          NULL,
    valor        numeric(15,2) NOT NULL,
    orcamento_id int8          NULL,
    observacao   varchar(1000) NULL,
    creator      varchar(64)   NULL DEFAULT '',
    create_time  timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater      varchar(64)   NULL DEFAULT '',
    update_time  timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted      int2          NOT NULL DEFAULT 0,
    CONSTRAINT pk_itsm_custo PRIMARY KEY (id),
    CONSTRAINT ck_itsm_custo_objeto CHECK (objeto IN ('contrato', 'projeto', 'licenca')),
    CONSTRAINT ck_itsm_custo_periodo CHECK (fim IS NULL OR fim >= inicio)
);
CREATE INDEX idx_itsm_custo_objeto ON itsm.custo (objeto, objeto_id) WHERE deleted = 0;
CREATE INDEX idx_itsm_custo_orcamento ON itsm.custo (orcamento_id) WHERE deleted = 0;
CREATE SEQUENCE itsm.custo_seq START 1;

-- Licença de software. quantidade -1 = ilimitada. A chave fica cifrada (chave-mestra) e é revelada com registro.
CREATE TABLE itsm.licenca (
    id                 int8          NOT NULL,
    software           varchar(150)  NOT NULL,
    fabricante         varchar(100)  NULL,
    nome               varchar(150)  NULL,   -- edição ou versão, ex.: "Microsoft 365 E3"
    tipo               varchar(60)   NULL,   -- assinatura, perpétua, OEM, volume
    quantidade         int4          NOT NULL DEFAULT 1,
    permite_excesso    bool          NOT NULL DEFAULT false,
    chave_cifrada      text          NULL,
    expira             date          NULL,
    alerta_dias        int4          NOT NULL DEFAULT 30,
    alerta_referencia  date          NULL,
    fornecedor_id      int8          NULL,
    contrato_id        int8          NULL,
    dept_id            int8          NULL,
    responsavel_id     int8          NULL,
    observacao         text          NULL,
    ativo              bool          NOT NULL DEFAULT true,
    id_externo         varchar(64)   NULL,
    creator            varchar(64)   NULL DEFAULT '',
    create_time        timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater            varchar(64)   NULL DEFAULT '',
    update_time        timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted            int2          NOT NULL DEFAULT 0,
    CONSTRAINT pk_itsm_licenca PRIMARY KEY (id),
    CONSTRAINT ck_itsm_licenca_quantidade CHECK (quantidade = -1 OR quantidade >= 0),
    CONSTRAINT ck_itsm_licenca_alerta CHECK (alerta_dias BETWEEN 0 AND 365)
);
CREATE INDEX idx_itsm_licenca_dept ON itsm.licenca (dept_id) WHERE deleted = 0;
CREATE SEQUENCE itsm.licenca_seq START 1;

-- Quem usa a licença: uma pessoa ou um equipamento (texto até o inventário da F3)
CREATE TABLE itsm.licenca_uso (
    id           int8          NOT NULL,
    licenca_id   int8          NOT NULL,
    user_id      int8          NULL,
    equipamento  varchar(150)  NULL,
    desde        date          NOT NULL,
    observacao   varchar(500)  NULL,
    creator      varchar(64)   NULL DEFAULT '',
    create_time  timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater      varchar(64)   NULL DEFAULT '',
    update_time  timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted      int2          NOT NULL DEFAULT 0,
    CONSTRAINT pk_itsm_licenca_uso PRIMARY KEY (id),
    CONSTRAINT ck_itsm_licenca_uso_um CHECK (num_nonnulls(user_id, equipamento) = 1)
);
CREATE INDEX idx_itsm_licenca_uso ON itsm.licenca_uso (licenca_id) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_itsm_licenca_uso_user ON itsm.licenca_uso (licenca_id, user_id) WHERE deleted = 0 AND user_id IS NOT NULL;
CREATE UNIQUE INDEX uk_itsm_licenca_uso_equip ON itsm.licenca_uso (licenca_id, lower(equipamento)) WHERE deleted = 0 AND equipamento IS NOT NULL;
CREATE SEQUENCE itsm.licenca_uso_seq START 1;

-- Projeto. situacao: 1 planejado, 2 em andamento, 3 pausado, 4 concluído, 5 cancelado
CREATE TABLE itsm.projeto (
    id                int8          NOT NULL,
    nome              varchar(150)  NOT NULL,
    codigo            varchar(40)   NULL,
    descricao         text          NOT NULL DEFAULT '',
    situacao          int2          NOT NULL DEFAULT 1,
    prioridade        int2          NOT NULL DEFAULT 3,
    dept_id           int8          NULL,
    responsavel_id    int8          NULL,
    inicio_previsto   date          NULL,
    fim_previsto      date          NULL,
    inicio_real       date          NULL,
    fim_real          date          NULL,
    percentual        int2          NOT NULL DEFAULT 0,
    percentual_auto   bool          NOT NULL DEFAULT true,   -- média das tarefas
    orcamento_id      int8          NULL,
    id_externo        varchar(64)   NULL,
    creator           varchar(64)   NULL DEFAULT '',
    create_time       timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater           varchar(64)   NULL DEFAULT '',
    update_time       timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           int2          NOT NULL DEFAULT 0,
    CONSTRAINT pk_itsm_projeto PRIMARY KEY (id),
    CONSTRAINT ck_itsm_projeto_situacao CHECK (situacao BETWEEN 1 AND 5),
    CONSTRAINT ck_itsm_projeto_percentual CHECK (percentual BETWEEN 0 AND 100),
    CONSTRAINT ck_itsm_projeto_prioridade CHECK (prioridade BETWEEN 1 AND 6),
    CONSTRAINT ck_itsm_projeto_datas CHECK (fim_previsto IS NULL OR inicio_previsto IS NULL OR fim_previsto >= inicio_previsto)
);
CREATE UNIQUE INDEX uk_itsm_projeto_codigo ON itsm.projeto (lower(codigo)) WHERE deleted = 0 AND codigo IS NOT NULL;
CREATE INDEX idx_itsm_projeto_dept ON itsm.projeto (dept_id) WHERE deleted = 0;
CREATE SEQUENCE itsm.projeto_seq START 1;

CREATE TABLE itsm.projeto_membro (
    id           int8          NOT NULL,
    projeto_id   int8          NOT NULL,
    user_id      int8          NOT NULL,
    papel        varchar(60)   NULL,
    creator      varchar(64)   NULL DEFAULT '',
    create_time  timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater      varchar(64)   NULL DEFAULT '',
    update_time  timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted      int2          NOT NULL DEFAULT 0,
    CONSTRAINT pk_itsm_projeto_membro PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_itsm_projeto_membro ON itsm.projeto_membro (projeto_id, user_id) WHERE deleted = 0;
CREATE INDEX idx_itsm_projeto_membro_user ON itsm.projeto_membro (user_id) WHERE deleted = 0;
CREATE SEQUENCE itsm.projeto_membro_seq START 1;

-- Tarefa de projeto. situacao: 1 a fazer, 2 em andamento, 3 concluída, 4 cancelada
CREATE TABLE itsm.projeto_tarefa (
    id                int8          NOT NULL,
    projeto_id        int8          NOT NULL,
    tarefa_pai_id     int8          NULL,
    nome              varchar(200)  NOT NULL,
    descricao         text          NULL,
    responsavel_id    int8          NULL,
    situacao          int2          NOT NULL DEFAULT 1,
    inicio_previsto   date          NULL,
    fim_previsto      date          NULL,
    inicio_real       date          NULL,
    fim_real          date          NULL,
    horas_previstas   numeric(8,2)  NOT NULL DEFAULT 0,
    horas_gastas      numeric(8,2)  NOT NULL DEFAULT 0,
    percentual        int2          NOT NULL DEFAULT 0,
    marco             bool          NOT NULL DEFAULT false,
    ordem             int4          NOT NULL DEFAULT 0,
    creator           varchar(64)   NULL DEFAULT '',
    create_time       timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater           varchar(64)   NULL DEFAULT '',
    update_time       timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           int2          NOT NULL DEFAULT 0,
    CONSTRAINT pk_itsm_projeto_tarefa PRIMARY KEY (id),
    CONSTRAINT ck_itsm_projeto_tarefa_situacao CHECK (situacao BETWEEN 1 AND 4),
    CONSTRAINT ck_itsm_projeto_tarefa_percentual CHECK (percentual BETWEEN 0 AND 100),
    CONSTRAINT ck_itsm_projeto_tarefa_horas CHECK (horas_previstas >= 0 AND horas_gastas >= 0),
    CONSTRAINT ck_itsm_projeto_tarefa_datas CHECK (fim_previsto IS NULL OR inicio_previsto IS NULL OR fim_previsto >= inicio_previsto)
);
CREATE INDEX idx_itsm_projeto_tarefa ON itsm.projeto_tarefa (projeto_id, ordem) WHERE deleted = 0;
CREATE INDEX idx_itsm_projeto_tarefa_resp ON itsm.projeto_tarefa (responsavel_id) WHERE deleted = 0;
CREATE SEQUENCE itsm.projeto_tarefa_seq START 1;

-- Chamados e mudanças ligados ao projeto (GLPI: Itil_Project)
CREATE TABLE itsm.projeto_vinculo (
    id           int8          NOT NULL,
    projeto_id   int8          NOT NULL,
    objeto       varchar(10)   NOT NULL,
    objeto_id    int8          NOT NULL,
    creator      varchar(64)   NULL DEFAULT '',
    create_time  timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater      varchar(64)   NULL DEFAULT '',
    update_time  timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted      int2          NOT NULL DEFAULT 0,
    CONSTRAINT pk_itsm_projeto_vinculo PRIMARY KEY (id),
    CONSTRAINT ck_itsm_projeto_vinculo_objeto CHECK (objeto IN ('chamado', 'problema', 'mudanca'))
);
CREATE UNIQUE INDEX uk_itsm_projeto_vinculo ON itsm.projeto_vinculo (projeto_id, objeto, objeto_id) WHERE deleted = 0;
CREATE SEQUENCE itsm.projeto_vinculo_seq START 1;

-- ---------- avisos por e-mail (destinatários definidos pelo sistema, não pelos papéis do chamado) ----------
ALTER TABLE itsm.email_evento ADD COLUMN destinatarios_fixos varchar(200) NULL;
ALTER TABLE itsm.email_evento ADD COLUMN variaveis varchar(500) NULL;
INSERT INTO itsm.email_evento (evento, nome, ordem, assunto, corpo, destinatarios_fixos, variaveis) VALUES
('mudanca-aprovacao', 'Mudança aguardando aprovação', 20,
 '[CO-TI] Aprovação pedida: mudança #{{mudanca.id}} {{mudanca.titulo}}',
 '<p>{{autor}} pediu a sua aprovação para a mudança <b>#{{mudanca.id}} {{mudanca.titulo}}</b> ({{mudanca.tipo}}).</p><p>Janela: {{mudanca.janela}}</p><p>{{comentario}}</p><p><a href="{{link}}">Ver e responder</a></p>',
 'Quem precisa aprovar', '{{mudanca.id}} {{mudanca.titulo}} {{mudanca.tipo}} {{mudanca.janela}} {{comentario}} {{autor}} {{link}}'),
('contrato-vencendo', 'Contrato perto do prazo', 21,
 '[CO-TI] Contrato {{contrato.nome}}: prazo em {{contrato.prazo}}',
 '<p>O contrato <b>{{contrato.nome}}</b> ({{contrato.fornecedor}}) tem prazo em <b>{{contrato.prazo}}</b>: {{contrato.oque}}.</p><p><a href="{{link}}">Abrir o contrato</a></p>',
 'Responsável pelo contrato', '{{contrato.nome}} {{contrato.numero}} {{contrato.fornecedor}} {{contrato.prazo}} {{contrato.oque}} {{link}}'),
('licenca-vencendo', 'Licença perto de expirar', 22,
 '[CO-TI] Licença {{licenca.nome}} expira em {{licenca.expira}}',
 '<p>A licença <b>{{licenca.nome}}</b> expira em <b>{{licenca.expira}}</b> ({{licenca.uso}} em uso).</p><p><a href="{{link}}">Abrir a licença</a></p>',
 'Responsável pela licença', '{{licenca.nome}} {{licenca.expira}} {{licenca.uso}} {{link}}');

-- ========== Menus e permissões ==========
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, updater, deleted) VALUES
(51200, 'Governança',              '', 1, 3, 0, '/governanca', 'ep:briefcase', NULL, NULL, 0, true, true, true, '1', '1', 0),
(51210, 'Fornecedores',            '', 2, 1, 51200, 'fornecedores', 'ep:shop', 'itsm/fornecedor/index', 'ItsmFornecedor', 0, true, true, true, '1', '1', 0),
(51211, 'Ver fornecedores',        'itsm:fornecedor:query',  3, 1, 51210, '', '', '', NULL, 0, true, true, true, '1', '1', 0),
(51212, 'Cadastrar fornecedores',  'itsm:fornecedor:update', 3, 2, 51210, '', '', '', NULL, 0, true, true, true, '1', '1', 0),
(51220, 'Contratos',               '', 2, 2, 51200, 'contratos', 'ep:document-checked', 'itsm/contrato/index', 'ItsmContrato', 0, true, true, true, '1', '1', 0),
(51221, 'Ver contratos',           'itsm:contrato:query',  3, 1, 51220, '', '', '', NULL, 0, true, true, true, '1', '1', 0),
(51222, 'Cadastrar contratos e custos', 'itsm:contrato:update', 3, 2, 51220, '', '', '', NULL, 0, true, true, true, '1', '1', 0),
(51230, 'Licenças',                '', 2, 3, 51200, 'licencas', 'ep:key', 'itsm/licenca/index', 'ItsmLicenca', 0, true, true, true, '1', '1', 0),
(51231, 'Ver licenças',            'itsm:licenca:query',  3, 1, 51230, '', '', '', NULL, 0, true, true, true, '1', '1', 0),
(51232, 'Cadastrar e distribuir licenças', 'itsm:licenca:update', 3, 2, 51230, '', '', '', NULL, 0, true, true, true, '1', '1', 0),
(51233, 'Ver a chave da licença',  'itsm:licenca:ver-chave', 3, 3, 51230, '', '', '', NULL, 0, true, true, true, '1', '1', 0),
(51240, 'Orçamentos',              '', 2, 4, 51200, 'orcamentos', 'ep:wallet', 'itsm/orcamento/index', 'ItsmOrcamento', 0, true, true, true, '1', '1', 0),
(51241, 'Ver orçamentos',          'itsm:orcamento:query',  3, 1, 51240, '', '', '', NULL, 0, true, true, true, '1', '1', 0),
(51242, 'Cadastrar orçamentos',    'itsm:orcamento:update', 3, 2, 51240, '', '', '', NULL, 0, true, true, true, '1', '1', 0),
(51250, 'Projetos',                '', 2, 5, 51200, 'projetos', 'ep:data-board', 'itsm/projeto/index', 'ItsmProjeto', 0, true, true, true, '1', '1', 0),
(51251, 'Ver projetos',            'itsm:projeto:query',  3, 1, 51250, '', '', '', NULL, 0, true, true, true, '1', '1', 0),
(51252, 'Gerenciar projetos',      'itsm:projeto:update', 3, 2, 51250, '', '', '', NULL, 0, true, true, true, '1', '1', 0);

-- Novo perfil: gestão de TI (governança completa e consulta ao Service Desk)
INSERT INTO system_role (id, name, code, sort, data_scope, data_scope_dept_ids, status, type, remark, creator, updater, deleted, tenant_id) VALUES
(50007, 'Gestão de TI', 'gestao_ti', 16, 1, '', 0, 2, 'Contratos, licenças, orçamentos, projetos e indicadores', '1', '1', 0, 1);

INSERT INTO system_role_menu (id, role_id, menu_id, creator, updater, deleted, tenant_id)
SELECT nextval('system_role_menu_seq'), 50007, m, '1', '1', 0, 1
FROM (VALUES (51200), (51210), (51211), (51212), (51220), (51221), (51222), (51230), (51231), (51232), (51233),
             (51240), (51241), (51242), (51250), (51251), (51252),
             (51000), (51140), (51010), (51011), (51160), (51161), (51170), (51171), (51100), (51120), (51130)) AS t(m)
UNION ALL
-- NOC e Service Desk consultam fornecedores, contratos, licenças e projetos (e atualizam as próprias tarefas)
SELECT nextval('system_role_menu_seq'), r, m, '1', '1', 0, 1
FROM (VALUES (50001), (50002)) AS papeis(r)
CROSS JOIN (VALUES (51200), (51210), (51211), (51220), (51221), (51230), (51231), (51250), (51251)) AS menus(m);
