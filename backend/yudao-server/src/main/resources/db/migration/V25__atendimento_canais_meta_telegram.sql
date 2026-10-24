-- ========== F2b — canais: WhatsApp Cloud API (com coexistência), Instagram e Telegram ==========

ALTER TABLE atendimento.caixa DROP CONSTRAINT ck_atendimento_caixa_canal;
ALTER TABLE atendimento.caixa ADD CONSTRAINT ck_atendimento_caixa_canal
    CHECK (canal IN ('api', 'whatsapp_cloud', 'whatsapp_evolution', 'instagram', 'telegram', 'widget'));

-- Dados próprios do tipo de mensagem: modelo usado (nome, idioma, parâmetros), localização, resposta de botão,
-- origem "pelo celular" (coexistência) ou "histórico" (importado na conexão)
ALTER TABLE atendimento.mensagem ADD COLUMN dados text NOT NULL DEFAULT '{}';

-- Modelos de mensagem aprovados na Meta (WhatsApp fora da janela de 24 h), copiados da conta para escolher na tela
CREATE TABLE atendimento.modelo (
    id           int8          NOT NULL,
    caixa_id     int8          NOT NULL,
    nome         varchar(512)  NOT NULL,
    idioma       varchar(20)   NOT NULL,
    categoria    varchar(30)   NULL,
    status       varchar(30)   NOT NULL,
    corpo        text          NOT NULL DEFAULT '',   -- texto do corpo, com {{1}}, {{2}}...
    variaveis    int4          NOT NULL DEFAULT 0,
    componentes  text          NOT NULL DEFAULT '[]', -- como a Meta devolveu
    creator      varchar(64)   NULL DEFAULT '',
    create_time  timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater      varchar(64)   NULL DEFAULT '',
    update_time  timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted      int2          NOT NULL DEFAULT 0,
    CONSTRAINT pk_atendimento_modelo PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_atendimento_modelo ON atendimento.modelo (caixa_id, nome, idioma) WHERE deleted = 0;
CREATE SEQUENCE atendimento.modelo_seq START 1;

-- Webhooks dos provedores: gravados assim que a assinatura confere (o provedor recebe 200 na hora) e processados
-- em seguida; o que falhar por motivo passageiro é tentado de novo. Os processados são apagados depois de 7 dias.
CREATE TABLE atendimento.webhook_entrada (
    id                 int8          NOT NULL,
    canal              varchar(30)   NOT NULL,
    caixa_id           int8          NULL,
    corpo              text          NOT NULL,
    status             varchar(20)   NOT NULL DEFAULT 'pendente',   -- pendente | processando | ok | erro
    tentativas         int4          NOT NULL DEFAULT 0,
    erro               varchar(1000) NULL,
    proxima_tentativa  timestamp     NULL,
    recebido_em        timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processado_em      timestamp     NULL,
    creator            varchar(64)   NULL DEFAULT '',
    create_time        timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater            varchar(64)   NULL DEFAULT '',
    update_time        timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted            int2          NOT NULL DEFAULT 0,
    CONSTRAINT pk_atendimento_webhook_entrada PRIMARY KEY (id),
    CONSTRAINT ck_atendimento_webhook_status CHECK (status IN ('pendente', 'processando', 'ok', 'erro'))
);
CREATE INDEX idx_atendimento_webhook_pendente ON atendimento.webhook_entrada (proxima_tentativa) WHERE status = 'pendente' AND deleted = 0;
CREATE SEQUENCE atendimento.webhook_entrada_seq START 1;
