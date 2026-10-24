-- ========== F2d — assistente de IA nas caixas e copiloto do atendente ==========
-- Ver docs/specs/atendimento-ia.md (reimplementação própria; nada da pasta enterprise/ do Chatwoot)

-- Provedores de IA (vários): OpenRouter, OpenAI, Anthropic, Gemini, Groq, DeepSeek, Mistral, Ollama local ou outro
-- compatível com a API da OpenAI. A ordem (prioridade) define a reserva quando o principal falha.
CREATE TABLE atendimento.ia_provedor (
    id           int8           NOT NULL,
    nome         varchar(80)    NOT NULL,
    tipo         varchar(20)    NOT NULL,
    base_url     varchar(300)   NOT NULL,
    chave        text           NULL,           -- cifrada com a chave-mestra (Ollama local pode não ter)
    modelo       varchar(150)   NOT NULL,
    temperatura  numeric(3, 2)  NOT NULL DEFAULT 0.3,
    max_tokens   int4           NOT NULL DEFAULT 800,
    ativo        bool           NOT NULL DEFAULT true,
    prioridade   int4           NOT NULL DEFAULT 0,
    testado_em   timestamp      NULL,
    erro_teste   varchar(500)   NULL,
    creator      varchar(64)    NULL DEFAULT '',
    create_time  timestamp      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater      varchar(64)    NULL DEFAULT '',
    update_time  timestamp      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted      int2           NOT NULL DEFAULT 0,
    CONSTRAINT pk_atendimento_ia_provedor PRIMARY KEY (id),
    CONSTRAINT ck_atendimento_ia_provedor_tipo CHECK (tipo IN ('openrouter', 'openai', 'anthropic', 'gemini', 'groq', 'deepseek', 'mistral', 'ollama', 'compativel')),
    CONSTRAINT ck_atendimento_ia_provedor_valores CHECK (temperatura BETWEEN 0 AND 2 AND max_tokens BETWEEN 50 AND 8000)
);
CREATE SEQUENCE atendimento.ia_provedor_seq START 1;

-- Chave geral e qual provedor é o principal de cada uso (vazio = o primeiro ativo pela prioridade)
CREATE TABLE atendimento.ia_config (
    id                      int8      NOT NULL,
    ativo                   bool      NOT NULL DEFAULT false,
    provedor_assistente_id  int8      NULL,
    provedor_copiloto_id    int8      NULL,
    creator                 varchar(64) NULL DEFAULT '',
    create_time             timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater                 varchar(64) NULL DEFAULT '',
    update_time             timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted                 int2      NOT NULL DEFAULT 0,
    CONSTRAINT pk_atendimento_ia_config PRIMARY KEY (id),
    CONSTRAINT ck_atendimento_ia_config_unica CHECK (id = 1)
);
INSERT INTO atendimento.ia_config (id) VALUES (1);

-- Assistente por caixa
ALTER TABLE atendimento.caixa ADD COLUMN robo_ativo bool NOT NULL DEFAULT false;
ALTER TABLE atendimento.caixa ADD COLUMN robo_nome varchar(60) NULL;
ALTER TABLE atendimento.caixa ADD COLUMN robo_instrucoes text NULL;
ALTER TABLE atendimento.caixa ADD COLUMN robo_max_respostas int4 NOT NULL DEFAULT 10;

-- Conversa com o assistente (fica pendente até o transbordo)
ALTER TABLE atendimento.conversa ADD COLUMN robo bool NOT NULL DEFAULT false;
ALTER TABLE atendimento.conversa ADD COLUMN robo_respostas int4 NOT NULL DEFAULT 0;
ALTER TABLE atendimento.conversa ADD COLUMN robo_falhas int4 NOT NULL DEFAULT 0;

-- Uso da IA (custo e auditoria; o texto das mensagens não é copiado para cá). Uma linha por tentativa.
CREATE TABLE atendimento.ia_uso (
    id              int8          NOT NULL,
    finalidade      varchar(20)   NOT NULL,   -- assistente | sugerir | resumir | reescrever | teste
    provedor_id     int8          NULL,
    conversa_id     int8          NULL,
    user_id         int8          NULL,
    modelo          varchar(150)  NULL,
    tokens_entrada  int4          NULL,
    tokens_saida    int4          NULL,
    duracao_ms      int4          NOT NULL DEFAULT 0,
    erro            varchar(500)  NULL,
    criado_em       timestamp     NOT NULL,
    creator         varchar(64)   NULL DEFAULT '',
    create_time     timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater         varchar(64)   NULL DEFAULT '',
    update_time     timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         int2          NOT NULL DEFAULT 0,
    CONSTRAINT pk_atendimento_ia_uso PRIMARY KEY (id)
);
CREATE INDEX idx_atendimento_ia_uso_criado ON atendimento.ia_uso (criado_em);
CREATE SEQUENCE atendimento.ia_uso_seq START 1;

-- ========== Menus ==========
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, updater, deleted) VALUES
(52037, 'Inteligência artificial',  '', 2, 4, 52030, 'ia', 'ep:cpu', 'atendimento/ia/index', 'AtendimentoIa', 0, true, true, true, '1', '1', 0),
(52038, 'Configurar a IA',          'atendimento:ia:config', 3, 1, 52037, '', '', '', NULL, 0, true, true, true, '1', '1', 0);
