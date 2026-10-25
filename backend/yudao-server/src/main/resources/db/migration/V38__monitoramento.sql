-- ========== F4a — Monitoramento (porta do Uptime Kuma 2.5.7, MIT) ==========
-- Ver docs/specs/monitoramento.md. Regras de estado conferidas em server/model/monitor.js e server/uptime-calculator.js
-- do Uptime Kuma (MIT, ver NOTICE); o código é escrito em Java.

CREATE TABLE monitor.monitor (
    id                      int8          NOT NULL,
    nome                    varchar(150)  NOT NULL,
    descricao               text          NULL,
    tipo                    varchar(10)   NOT NULL,
    alvo                    varchar(1000) NULL,          -- URL (http/palavra/json), host (tcp/ping/dns)
    porta                   int4          NULL,          -- tcp
    metodo                  varchar(10)   NOT NULL DEFAULT 'GET',
    corpo                   text          NULL,
    cabecalhos              text          NULL,          -- objeto JSON
    codigos_aceitos         varchar(100)  NOT NULL DEFAULT '200-299',
    palavra                 varchar(500)  NULL,
    palavra_invertida       bool          NOT NULL DEFAULT false,
    json_caminho            varchar(300)  NULL,
    json_operador           varchar(10)   NULL,
    json_esperado           varchar(500)  NULL,
    dns_tipo                varchar(10)   NOT NULL DEFAULT 'A',
    dns_servidor            varchar(255)  NULL,
    dns_esperado            varchar(255)  NULL,
    intervalo_s             int4          NOT NULL DEFAULT 60,
    tentativas              int2          NOT NULL DEFAULT 0,   -- falhas seguidas (pendente) antes de "fora do ar"
    intervalo_tentativa_s   int4          NOT NULL DEFAULT 60,
    tempo_limite_s          int4          NOT NULL DEFAULT 30,
    reenviar_a_cada         int4          NOT NULL DEFAULT 0,   -- repete o aviso a cada N verificações fora (0 = não)
    invertido               bool          NOT NULL DEFAULT false,
    ignorar_tls             bool          NOT NULL DEFAULT false,
    max_redirecionamentos   int2          NOT NULL DEFAULT 10,
    lento_ms                int4          NULL,          -- acima disto o monitor aparece como lento
    cert_aviso_dias         int4          NOT NULL DEFAULT 14,  -- 0 = não avisa
    push_token              varchar(64)   NULL,
    unidade_id              int8          NULL,          -- monitor_unit: o principal de cada unidade alimenta o telão
    papel                   varchar(15)   NOT NULL DEFAULT 'servico',
    dept_id                 int8          NULL,          -- escopo de dados (da unidade, se não informado)
    abrir_chamado           bool          NOT NULL DEFAULT false,
    chamado_categoria_id    int8          NULL,
    chamado_urgencia        int2          NOT NULL DEFAULT 4,
    chamado_solucionar      bool          NOT NULL DEFAULT true,
    ativo                   bool          NOT NULL DEFAULT true,
    -- estado atual (o histórico fica em monitor.batida)
    estado                  int2          NULL,          -- 0 fora, 1 no ar, 2 pendente, 3 manutenção
    estado_desde            timestamp     NULL,
    ultima_verificacao      timestamp     NULL,
    ultima_latencia_ms      int4          NULL,
    ultima_msg              varchar(500)  NULL,
    tentativa_atual         int2          NOT NULL DEFAULT 0,
    quedas_seguidas         int4          NOT NULL DEFAULT 0,
    cert_dias               int4          NULL,
    cert_validade           timestamp     NULL,
    cert_avisado_validade   timestamp     NULL,
    chamado_id              int8          NULL,          -- aberto pela queda atual
    kuma_id                 int8          NULL,          -- monitor de origem no banco do Uptime Kuma importado
    ordem                   int2          GENERATED ALWAYS AS (CASE WHEN ativo AND estado = 0 THEN 0 WHEN ativo AND estado = 2 THEN 1 ELSE 2 END) STORED,
    creator                 varchar(64)   NULL DEFAULT '',
    create_time             timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater                 varchar(64)   NULL DEFAULT '',
    update_time             timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted                 int2          NOT NULL DEFAULT 0,
    CONSTRAINT pk_monitor_monitor PRIMARY KEY (id),
    CONSTRAINT ck_monitor_tipo CHECK (tipo IN ('http', 'palavra', 'json', 'tcp', 'ping', 'dns', 'push', 'manual')),
    CONSTRAINT ck_monitor_papel CHECK (papel IN ('principal', 'redundante', 'servico')),
    CONSTRAINT ck_monitor_estado CHECK (estado IS NULL OR estado BETWEEN 0 AND 3),
    CONSTRAINT ck_monitor_intervalo CHECK (intervalo_s >= 5 AND intervalo_tentativa_s >= 5 AND tempo_limite_s >= 1)
);
CREATE UNIQUE INDEX uk_monitor_push ON monitor.monitor (push_token) WHERE deleted = 0 AND push_token IS NOT NULL;
CREATE INDEX idx_monitor_unidade ON monitor.monitor (unidade_id) WHERE deleted = 0;
CREATE SEQUENCE monitor.monitor_seq START 1;

-- Cada verificação (heartbeat). Hypertable do TimescaleDB: um pedaço por dia, apagados depois da retenção.
CREATE TABLE monitor.batida (
    monitor_id   int8          NOT NULL,
    momento      timestamp     NOT NULL,
    estado       int2          NOT NULL,
    latencia_ms  int4          NULL,
    msg          varchar(500)  NULL,
    importante   bool          NOT NULL DEFAULT false   -- mudou de estado (o que vira evento e aviso)
);
SELECT create_hypertable('monitor.batida', 'momento', chunk_time_interval => interval '1 day');
CREATE INDEX idx_monitor_batida ON monitor.batida (monitor_id, momento DESC);
CREATE INDEX idx_monitor_batida_importante ON monitor.batida (monitor_id, momento DESC) WHERE importante;

-- Totais por hora, para disponibilidade de 24 h, 30 dias e 1 ano sem varrer as batidas.
-- Como no Kuma: pendente conta como fora; manutenção fica fora da conta.
CREATE TABLE monitor.batida_hora (
    monitor_id     int8        NOT NULL,
    hora           timestamp   NOT NULL,
    no_ar          int4        NOT NULL DEFAULT 0,
    fora           int4        NOT NULL DEFAULT 0,
    manutencao     int4        NOT NULL DEFAULT 0,
    soma_latencia  int8        NOT NULL DEFAULT 0,
    n_latencia     int4        NOT NULL DEFAULT 0,
    CONSTRAINT pk_monitor_batida_hora PRIMARY KEY (monitor_id, hora)
);

-- Janelas de manutenção: o monitor não é verificado, não avisa e não abre chamado.
CREATE TABLE monitor.manutencao (
    id            int8          NOT NULL,
    titulo        varchar(150)  NOT NULL,
    descricao     text          NULL,
    tipo          varchar(10)   NOT NULL,      -- manual | unica | semanal
    ativa         bool          NOT NULL DEFAULT true,
    inicio        timestamp     NULL,          -- unica
    fim           timestamp     NULL,
    dias_semana   varchar(20)   NULL,          -- semanal: 1 (segunda) a 7 (domingo), separados por vírgula
    hora_inicio   time          NULL,
    hora_fim      time          NULL,          -- menor que o início = atravessa a meia-noite
    unidade_id    int8          NULL,          -- todos os monitores da unidade, além dos escolhidos
    creator       varchar(64)   NULL DEFAULT '',
    create_time   timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater       varchar(64)   NULL DEFAULT '',
    update_time   timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted       int2          NOT NULL DEFAULT 0,
    CONSTRAINT pk_monitor_manutencao PRIMARY KEY (id),
    CONSTRAINT ck_monitor_manutencao_tipo CHECK (tipo IN ('manual', 'unica', 'semanal'))
);
CREATE SEQUENCE monitor.manutencao_seq START 1;

CREATE TABLE monitor.manutencao_monitor (
    id             int8         NOT NULL,
    manutencao_id  int8         NOT NULL,
    monitor_id     int8         NOT NULL,
    creator        varchar(64)  NULL DEFAULT '',
    create_time    timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater        varchar(64)  NULL DEFAULT '',
    update_time    timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted        int2         NOT NULL DEFAULT 0,
    CONSTRAINT pk_monitor_manutencao_monitor PRIMARY KEY (id)
);
CREATE INDEX idx_monitor_manutencao_monitor ON monitor.manutencao_monitor (manutencao_id) WHERE deleted = 0;
CREATE SEQUENCE monitor.manutencao_monitor_seq START 1;

-- Canais de aviso (e-mail, Matrix, Telegram, webhook). Segredos cifrados com a chave-mestra.
CREATE TABLE monitor.canal (
    id            int8          NOT NULL,
    nome          varchar(100)  NOT NULL,
    tipo          varchar(10)   NOT NULL,
    config        text          NOT NULL DEFAULT '{}',
    padrao        bool          NOT NULL DEFAULT false,   -- já vem marcado nos monitores novos
    ativo         bool          NOT NULL DEFAULT true,
    creator       varchar(64)   NULL DEFAULT '',
    create_time   timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater       varchar(64)   NULL DEFAULT '',
    update_time   timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted       int2          NOT NULL DEFAULT 0,
    CONSTRAINT pk_monitor_canal PRIMARY KEY (id),
    CONSTRAINT ck_monitor_canal_tipo CHECK (tipo IN ('email', 'matrix', 'telegram', 'webhook'))
);
CREATE SEQUENCE monitor.canal_seq START 1;

CREATE TABLE monitor.monitor_canal (
    id            int8         NOT NULL,
    monitor_id    int8         NOT NULL,
    canal_id      int8         NOT NULL,
    creator       varchar(64)  NULL DEFAULT '',
    create_time   timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater       varchar(64)  NULL DEFAULT '',
    update_time   timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted       int2         NOT NULL DEFAULT 0,
    CONSTRAINT pk_monitor_monitor_canal PRIMARY KEY (id)
);
CREATE INDEX idx_monitor_monitor_canal ON monitor.monitor_canal (monitor_id) WHERE deleted = 0;
CREATE SEQUENCE monitor.monitor_canal_seq START 1;

-- ========== Endereço da unidade (o mapa do telão usa latitude/longitude) ==========
-- Preenchido à mão no cadastro da unidade: o CEP traz o endereço, o número completa e as coordenadas são calculadas
-- (ou marcadas no mapa).
ALTER TABLE monitor_unit ADD COLUMN cep varchar(9) NULL;
ALTER TABLE monitor_unit ADD COLUMN logradouro varchar(200) NULL;
ALTER TABLE monitor_unit ADD COLUMN numero varchar(20) NULL;
ALTER TABLE monitor_unit ADD COLUMN complemento varchar(100) NULL;
ALTER TABLE monitor_unit ADD COLUMN bairro varchar(100) NULL;
ALTER TABLE monitor_unit ADD COLUMN uf varchar(2) NULL;

-- ========== Modelos de e-mail dos avisos ==========
INSERT INTO itsm.email_evento (evento, nome, ordem, assunto, corpo, destinatarios_fixos, variaveis) VALUES
('monitor-caiu', 'Monitor fora do ar', 30,
 '[CO-TI] Fora do ar: {{monitor.nome}}',
 '<p>O monitor <b>{{monitor.nome}}</b> ({{monitor.alvo}}) está <b>fora do ar</b> desde {{monitor.desde}}.</p><p>{{monitor.msg}}</p><p><a href="{{link}}">Ver o monitor</a></p>',
 'Pessoas do canal de aviso', '{{monitor.nome}} {{monitor.alvo}} {{monitor.unidade}} {{monitor.desde}} {{monitor.msg}} {{link}}'),
('monitor-voltou', 'Monitor normalizado', 31,
 '[CO-TI] Normalizado: {{monitor.nome}}',
 '<p>O monitor <b>{{monitor.nome}}</b> ({{monitor.alvo}}) voltou ao ar em {{monitor.desde}}, depois de {{monitor.duracao}} fora.</p><p><a href="{{link}}">Ver o monitor</a></p>',
 'Pessoas do canal de aviso', '{{monitor.nome}} {{monitor.alvo}} {{monitor.unidade}} {{monitor.desde}} {{monitor.duracao}} {{link}}'),
('monitor-certificado', 'Certificado perto de vencer', 32,
 '[CO-TI] Certificado de {{monitor.nome}} vence em {{monitor.dias}} dias',
 '<p>O certificado TLS de <b>{{monitor.nome}}</b> ({{monitor.alvo}}) vence em <b>{{monitor.dias}} dias</b> ({{monitor.validade}}).</p><p><a href="{{link}}">Ver o monitor</a></p>',
 'Pessoas do canal de aviso', '{{monitor.nome}} {{monitor.alvo}} {{monitor.dias}} {{monitor.validade}} {{link}}');

-- ========== Menus e permissões ==========
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, updater, deleted) VALUES
(55000, 'Monitoramento',            '', 1, 4, 0, '/monitoramento', 'ep:odometer', NULL, NULL, 0, true, true, true, '1', '1', 0),
(55010, 'Monitores',                '', 2, 1, 55000, 'monitores', 'ep:data-line', 'monitoramento/monitor/index', 'MonitoramentoMonitor', 0, true, true, true, '1', '1', 0),
(55011, 'Ver monitores',            'monitoramento:monitor:query',  3, 1, 55010, '', '', '', NULL, 0, true, true, true, '1', '1', 0),
(55012, 'Criar e alterar monitores', 'monitoramento:monitor:update', 3, 2, 55010, '', '', '', NULL, 0, true, true, true, '1', '1', 0),
(55020, 'Manutenções',              '', 2, 2, 55000, 'manutencoes', 'ep:tools', 'monitoramento/manutencao/index', 'MonitoramentoManutencao', 0, true, true, true, '1', '1', 0),
(55021, 'Criar e alterar manutenções', 'monitoramento:manutencao:update', 3, 1, 55020, '', '', '', NULL, 0, true, true, true, '1', '1', 0),
(55030, 'Canais de aviso',          '', 2, 3, 55000, 'canais', 'ep:bell', 'monitoramento/canal/index', 'MonitoramentoCanal', 0, true, true, true, '1', '1', 0),
(55031, 'Configurar canais',        'monitoramento:canal:update', 3, 1, 55030, '', '', '', NULL, 0, true, true, true, '1', '1', 0),
(55040, 'Unidades',                 '', 2, 4, 55000, 'unidades', 'ep:location', 'monitoramento/unidade/index', 'MonitoramentoUnidade', 0, true, true, true, '1', '1', 0),
(55041, 'Cadastrar unidades',       'monitoramento:unidade:update', 3, 1, 55040, '', '', '', NULL, 0, true, true, true, '1', '1', 0),
(55050, 'Importar do Uptime Kuma',  'monitoramento:importar', 3, 3, 55010, '', '', '', NULL, 0, true, true, true, '1', '1', 0);

-- NOC: tudo; Service Desk, SOC, gestor da unidade e gestão de TI: consulta
INSERT INTO system_role_menu (id, role_id, menu_id, creator, updater, deleted, tenant_id)
SELECT nextval('system_role_menu_seq'), 50001, m, '1', '1', 0, 1
FROM (VALUES (55000), (55010), (55011), (55012), (55020), (55021), (55030), (55031), (55040), (55041)) AS menus(m)
UNION ALL
SELECT nextval('system_role_menu_seq'), r, m, '1', '1', 0, 1
FROM (VALUES (50002), (50003), (50005), (50007)) AS papeis(r)
CROSS JOIN (VALUES (55000), (55010), (55011), (55020), (55040)) AS menus(m);
-- importar do Kuma: só o administrador, até alguém receber a permissão
