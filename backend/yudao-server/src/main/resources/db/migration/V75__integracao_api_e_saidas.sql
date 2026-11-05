-- API para outros sistemas abrirem e consultarem chamados, e webhooks que avisam outros sistemas do andamento.
CREATE TABLE integracao.chave_api (
    id             int8         NOT NULL,
    nome           varchar(100) NOT NULL,
    prefixo        varchar(16)  NOT NULL,   -- só para reconhecer a chave na tela
    hash           varchar(64)  NOT NULL,   -- a chave em si não fica guardada
    escopos        varchar(200) NOT NULL,   -- chamado:criar, chamado:consultar
    solicitante_id int8         NOT NULL,   -- pessoa em nome de quem os chamados são abertos
    ips_permitidos varchar(500) NULL,
    ativa          bool         NOT NULL DEFAULT true,
    expira_em      timestamp    NULL,
    ultimo_uso     timestamp    NULL,
    criado_em      timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_integracao_chave_api PRIMARY KEY (id),
    CONSTRAINT uk_integracao_chave_api_hash UNIQUE (hash)
);
CREATE SEQUENCE integracao.chave_api_seq START 1;

-- Chamados abertos por cada chave: ela só consulta os próprios, e repetir o pedido não abre outro.
CREATE TABLE integracao.chave_chamado (
    chave_id     int8         NOT NULL,
    chamado_id   int8         NOT NULL,
    idempotencia varchar(128) NOT NULL,
    criado_em    timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_integracao_chave_chamado PRIMARY KEY (chave_id, chamado_id),
    CONSTRAINT uk_integracao_chave_idempotencia UNIQUE (chave_id, idempotencia)
);

CREATE TABLE integracao.saida (
    id              int8          NOT NULL,
    nome            varchar(100)  NOT NULL,
    url             varchar(1000) NOT NULL,
    segredo_cifrado text          NOT NULL,
    eventos         varchar(300)  NOT NULL,   -- chamado.criado, chamado.atribuido, chamado.solucionado, chamado.fechado, chamado.reaberto
    ativa           bool          NOT NULL DEFAULT true,
    criado_em       timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_integracao_saida PRIMARY KEY (id)
);
CREATE SEQUENCE integracao.saida_seq START 1;

CREATE TABLE integracao.entrega (
    id                int8         NOT NULL,
    saida_id          int8         NOT NULL,
    evento            varchar(30)  NOT NULL,
    chamado_id        int8         NOT NULL,
    payload           text         NOT NULL,   -- só identificadores e situação, sem conteúdo do chamado
    status            varchar(12)  NOT NULL DEFAULT 'pendente',   -- pendente | enviando | enviado | erro
    tentativas        int4         NOT NULL DEFAULT 0,
    proxima_tentativa timestamp    NULL,
    resposta_codigo   int4         NULL,
    erro              varchar(300) NULL,
    criado_em         timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    enviado_em        timestamp    NULL,
    CONSTRAINT pk_integracao_entrega PRIMARY KEY (id)
);
CREATE SEQUENCE integracao.entrega_seq START 1;
CREATE INDEX idx_integracao_entrega_fila ON integracao.entrega (id) WHERE status = 'pendente';
CREATE INDEX idx_integracao_entrega_limpeza ON integracao.entrega (criado_em);
CREATE INDEX idx_integracao_chave_chamado_limpeza ON integracao.chave_chamado (criado_em);
