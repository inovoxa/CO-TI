-- ========== ITSM F1d — e-mail: notificações (GLPI: Notification/QueuedNotification) e coletor (GLPI: MailCollector) ==========

-- Conta de envio (uma por instalação). A senha fica cifrada com a chave-mestra.
CREATE TABLE itsm.email_config (
    id                 int8          NOT NULL,
    ativo              bool          NOT NULL DEFAULT false,
    smtp_host          varchar(255)  NULL,
    smtp_porta         int4          NOT NULL DEFAULT 587,
    smtp_seguranca     varchar(8)    NOT NULL DEFAULT 'starttls', -- nenhuma | starttls | ssl
    smtp_usuario       varchar(255)  NULL,
    smtp_senha_cifrada varchar(1024) NULL,
    remetente_email    varchar(320)  NULL,
    remetente_nome     varchar(100)  NOT NULL DEFAULT 'Central de Serviços',
    responder_para     varchar(320)  NULL,                       -- normalmente a caixa coletada
    url_portal         varchar(255)  NULL,                       -- links nos e-mails, ex.: https://ti.empresa.gov.br
    assinatura         text          NULL,
    update_time        timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_itsm_email_config PRIMARY KEY (id),
    CONSTRAINT ck_itsm_email_seguranca CHECK (smtp_seguranca IN ('nenhuma', 'starttls', 'ssl'))
);
INSERT INTO itsm.email_config (id) VALUES (1);

-- O que avisar e para quem (GLPI: glpi_notifications + targets), com o modelo do e-mail
CREATE TABLE itsm.email_evento (
    evento           varchar(32)   NOT NULL,
    nome             varchar(100)  NOT NULL,
    ativo            bool          NOT NULL DEFAULT true,
    para_solicitante bool          NOT NULL DEFAULT false,
    para_observador  bool          NOT NULL DEFAULT false,
    para_tecnico     bool          NOT NULL DEFAULT false,   -- técnicos e membros das equipes atribuídas
    para_aprovador   bool          NOT NULL DEFAULT false,   -- quem tem aprovação pendente
    assunto          varchar(255)  NOT NULL,
    corpo            text          NOT NULL,
    ordem            int4          NOT NULL DEFAULT 0,
    CONSTRAINT pk_itsm_email_evento PRIMARY KEY (evento)
);

-- Fila de saída (outbox): gravada na mesma transação da mudança no chamado
CREATE TABLE itsm.email_saida (
    id                int8          NOT NULL,
    chamado_id        int8          NULL,
    evento            varchar(32)   NULL,
    para              varchar(320)  NOT NULL,
    assunto           varchar(400)  NOT NULL,
    corpo             text          NOT NULL,
    message_id        varchar(255)  NOT NULL,
    status            varchar(10)   NOT NULL DEFAULT 'pendente', -- pendente | enviando | enviado | erro
    tentativas        int4          NOT NULL DEFAULT 0,
    proxima_tentativa timestamp     NULL,
    erro              varchar(1000) NULL,
    criado_em         timestamp     NOT NULL,
    enviado_em        timestamp     NULL,
    CONSTRAINT pk_itsm_email_saida PRIMARY KEY (id),
    CONSTRAINT ck_itsm_email_saida_status CHECK (status IN ('pendente', 'enviando', 'enviado', 'erro'))
);
CREATE INDEX idx_itsm_email_saida_fila ON itsm.email_saida (status, proxima_tentativa);
CREATE INDEX idx_itsm_email_saida_chamado ON itsm.email_saida (chamado_id);
CREATE SEQUENCE itsm.email_saida_seq START 1;

-- Caixas de entrada coletadas por IMAP
CREATE TABLE itsm.email_caixa (
    id                    int8          NOT NULL,
    nome                  varchar(100)  NOT NULL,
    ativo                 bool          NOT NULL DEFAULT true,
    host                  varchar(255)  NOT NULL,
    porta                 int4          NOT NULL DEFAULT 993,
    seguranca             varchar(8)    NOT NULL DEFAULT 'ssl',   -- nenhuma | starttls | ssl
    usuario               varchar(255)  NOT NULL,
    senha_cifrada         varchar(1024) NULL,
    pasta                 varchar(255)  NOT NULL DEFAULT 'INBOX',
    pasta_processados     varchar(255)  NULL,                     -- vazio = só marca como lida
    pasta_recusados       varchar(255)  NULL,
    aceitar_desconhecidos bool          NOT NULL DEFAULT false,   -- remetente sem conta no portal abre chamado
    exigir_autenticacao   bool          NOT NULL DEFAULT false,   -- só aceita com DMARC/DKIM aprovado
    categoria_id          int8          NULL,
    anexo_max_mb          int4          NOT NULL DEFAULT 10,
    ultima_coleta         timestamp     NULL,
    ultimo_erro           varchar(1000) NULL,
    creator               varchar(64)   NULL DEFAULT '',
    create_time           timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater               varchar(64)   NULL DEFAULT '',
    update_time           timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted               int2          NOT NULL DEFAULT 0,
    CONSTRAINT pk_itsm_email_caixa PRIMARY KEY (id),
    CONSTRAINT ck_itsm_email_caixa_seguranca CHECK (seguranca IN ('nenhuma', 'starttls', 'ssl'))
);
CREATE SEQUENCE itsm.email_caixa_seq START 1;

-- Cada e-mail lido (não processa duas vezes; mostra o que foi recusado e por quê)
CREATE TABLE itsm.email_recebido (
    id          int8          NOT NULL,
    caixa_id    int8          NOT NULL,
    message_id  varchar(998)  NOT NULL,
    remetente   varchar(320)  NULL,
    assunto     varchar(400)  NULL,
    chamado_id  int8          NULL,
    resultado   varchar(16)   NOT NULL,   -- chamado | acompanhamento | recusado | ignorado
    motivo      varchar(500)  NULL,
    recebido_em timestamp     NOT NULL,
    CONSTRAINT pk_itsm_email_recebido PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_itsm_email_recebido ON itsm.email_recebido (caixa_id, message_id);
CREATE SEQUENCE itsm.email_recebido_seq START 1;

-- ---------- Modelos padrão ----------
-- {{x}} é escapado; {{{x}}} entra como HTML (só para textos já saneados: descrição e conteúdo)
INSERT INTO itsm.email_evento (evento, nome, ordem, para_solicitante, para_observador, para_tecnico, para_aprovador, assunto, corpo) VALUES
('criado', 'Chamado aberto', 1, true, true, true, false,
 '[CO-TI #{{chamado.id}}] {{chamado.titulo}}',
 '<p>O chamado <b>#{{chamado.id}}</b> foi aberto.</p><p><b>{{chamado.titulo}}</b><br>Categoria: {{chamado.categoria}}<br>Prioridade: {{chamado.prioridade}}</p>{{{chamado.descricao}}}<p><a href="{{chamado.link}}">Acompanhar o chamado</a></p>'),
('atribuido', 'Técnico atribuído', 2, false, false, true, false,
 '[CO-TI #{{chamado.id}}] Atribuído: {{chamado.titulo}}',
 '<p>O chamado <b>#{{chamado.id}}</b> foi atribuído a você ou à sua equipe.</p><p><b>{{chamado.titulo}}</b><br>Solicitante: {{chamado.solicitante}}<br>Prioridade: {{chamado.prioridade}}<br>Solucionar até: {{chamado.prazo}}</p>{{{chamado.descricao}}}<p><a href="{{chamado.link}}">Abrir o chamado</a></p>'),
('acompanhamento', 'Novo acompanhamento', 3, true, true, true, false,
 '[CO-TI #{{chamado.id}}] {{chamado.titulo}}',
 '<p><b>{{autor}}</b> escreveu no chamado <b>#{{chamado.id}}</b>:</p>{{{conteudo}}}<p><a href="{{chamado.link}}">Ver o chamado</a></p>'),
('solucao', 'Solução registrada', 4, true, true, false, false,
 '[CO-TI #{{chamado.id}}] Solucionado: {{chamado.titulo}}',
 '<p>O chamado <b>#{{chamado.id}}</b> recebeu uma solução:</p>{{{conteudo}}}<p>Se resolveu, aprove em <a href="{{chamado.link}}">Meus chamados</a>. Se não, responda este e-mail contando o que falta: o chamado volta para a equipe.</p>'),
('fechado', 'Chamado fechado', 5, true, false, false, false,
 '[CO-TI #{{chamado.id}}] Fechado: {{chamado.titulo}}',
 '<p>O chamado <b>#{{chamado.id}}</b> foi fechado.</p><p>Conte como foi o atendimento: <a href="{{chamado.link}}">avaliar</a>.</p>'),
('reaberto', 'Chamado reaberto', 6, false, false, true, false,
 '[CO-TI #{{chamado.id}}] Reaberto: {{chamado.titulo}}',
 '<p>O chamado <b>#{{chamado.id}}</b> foi reaberto.</p><p><a href="{{chamado.link}}">Abrir o chamado</a></p>'),
('pendente', 'Chamado pendente', 7, true, false, false, false,
 '[CO-TI #{{chamado.id}}] Pendente: {{chamado.titulo}}',
 '<p>O chamado <b>#{{chamado.id}}</b> está pendente, aguardando uma informação ou um terceiro.</p><p><a href="{{chamado.link}}">Ver o chamado</a></p>'),
('aprovacao', 'Aprovação solicitada', 8, false, false, false, true,
 '[CO-TI #{{chamado.id}}] Aprovação: {{chamado.titulo}}',
 '<p>{{chamado.solicitante}} fez um pedido que precisa da sua aprovação.</p><p><b>{{chamado.titulo}}</b></p>{{{chamado.descricao}}}<p><a href="{{portal}}/aprovacoes">Aprovar ou recusar</a></p>'),
('aprovado', 'Pedido aprovado', 9, true, false, true, false,
 '[CO-TI #{{chamado.id}}] Aprovado: {{chamado.titulo}}',
 '<p>O pedido <b>#{{chamado.id}}</b> foi aprovado.</p><p><a href="{{chamado.link}}">Ver o chamado</a></p>'),
('recusado', 'Pedido recusado', 10, true, false, true, false,
 '[CO-TI #{{chamado.id}}] Recusado: {{chamado.titulo}}',
 '<p>O pedido <b>#{{chamado.id}}</b> foi recusado na aprovação.</p><p><a href="{{chamado.link}}">Ver o motivo</a></p>'),
('escalonado', 'Escalonamento de SLA', 11, false, false, true, false,
 '[CO-TI #{{chamado.id}}] Prazo: {{chamado.titulo}}',
 '<p>O chamado <b>#{{chamado.id}}</b> foi escalonado pelo SLA. Solucionar até: {{chamado.prazo}}.</p><p><a href="{{chamado.link}}">Abrir o chamado</a></p>');

-- ---------- Menu ----------
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, updater, deleted) VALUES
(51150, 'E-mail',               '', 2, 8, 51020, 'email', 'ep:message', 'itsm/email/index', 'ItsmEmail', 0, true, true, true, '1', '1', 0),
(51151, 'Ver configuração de e-mail', 'itsm:email:query',  3, 1, 51150, '', '', '', NULL, 0, true, true, true, '1', '1', 0),
(51152, 'Configurar e-mail',    'itsm:email:update', 3, 2, 51150, '', '', '', NULL, 0, true, true, true, '1', '1', 0);

INSERT INTO system_role_menu (id, role_id, menu_id, creator, updater, deleted, tenant_id)
SELECT nextval('system_role_menu_seq'), r, m, '1', '1', 0, 1
FROM (VALUES (50001), (50002)) AS papeis(r) CROSS JOIN (VALUES (51150), (51151)) AS menus(m);
