-- Entradas por webhook: um sistema externo avisa e o CO-TI abre o chamado. O primeiro caso é o desligamento vindo do RH, que abre
-- o formulário de desativação de usuário e bloqueia a conta no AD.
CREATE SCHEMA IF NOT EXISTS integracao;
GRANT USAGE ON SCHEMA integracao TO coti_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA integracao GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO coti_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA integracao GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO coti_app;

CREATE TABLE integracao.entrada (
    id                 int8          NOT NULL,
    codigo             varchar(40)   NOT NULL,   -- vai no endereço do webhook
    nome               varchar(100)  NOT NULL,
    ativa              bool          NOT NULL DEFAULT true,
    modo               varchar(12)   NOT NULL DEFAULT 'simulacao',   -- simulacao | confirmacao | automatico
    autenticacao       varchar(10)   NOT NULL DEFAULT 'hmac',        -- hmac | bearer (bearer exige lista de IPs)
    segredo_cifrado    text          NOT NULL,
    ips_permitidos     varchar(500)  NULL,       -- IPs ou redes CIDR separados por vírgula
    formulario_id      int8          NOT NULL,   -- formulário com ação Desativar
    solicitante_id     int8          NOT NULL,   -- pessoa em nome de quem o chamado é aberto
    atributo_ad        varchar(30)   NOT NULL DEFAULT 'employeeID',
    caminho_id         varchar(150)  NOT NULL,   -- onde está, no JSON, o valor procurado no AD
    caminho_nome       varchar(150)  NULL,
    conferir_nome      bool          NOT NULL DEFAULT false,
    caminho_situacao   varchar(150)  NOT NULL,
    valores_desligar   varchar(300)  NOT NULL,   -- ex.: DESLIGADO,BLOQUEADO
    valores_cancelar   varchar(300)  NULL,       -- ex.: READMITIDO,CANCELADO
    caminho_data       varchar(150)  NULL,       -- data de efetivação; futura = agenda
    caminho_chave      varchar(150)  NULL,       -- id do evento na origem; vazio = hash do corpo
    mapa               text          NOT NULL DEFAULT '{}',   -- campo do formulário -> caminho no JSON
    grupos_protegidos  varchar(1000) NULL,       -- CN ou DN, separados por ponto e vírgula
    logins_protegidos  varchar(1000) NULL,       -- separados por vírgula
    limite_dia         int4          NOT NULL DEFAULT 5,
    criado_em          timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em      timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_integracao_entrada PRIMARY KEY (id),
    CONSTRAINT uk_integracao_entrada_codigo UNIQUE (codigo)
);
CREATE SEQUENCE integracao.entrada_seq START 1;

CREATE TABLE integracao.evento (
    id                int8          NOT NULL,
    entrada_id        int8          NOT NULL,
    chave             varchar(128)  NOT NULL,   -- evento repetido não processa de novo
    ip                varchar(45)   NULL,
    payload_cifrado   text          NOT NULL,   -- pode ter dados pessoais: nunca em texto puro
    id_hash           varchar(64)   NULL,       -- hash do identificador, para achar eventos da mesma pessoa sem guardar o valor
    status            varchar(12)   NOT NULL DEFAULT 'recebido',
    -- recebido | processando | simulado | ignorado | chamado | executando | agendado | confirmacao | cancelado | erro
    resultado         varchar(1000) NULL,
    login             varchar(100)  NULL,
    chamado_id        int8          NULL,
    tentativas        int4          NOT NULL DEFAULT 0,
    proxima_tentativa timestamp     NULL,
    recebido_em       timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processado_em     timestamp     NULL,
    CONSTRAINT pk_integracao_evento PRIMARY KEY (id),
    CONSTRAINT uk_integracao_evento_chave UNIQUE (entrada_id, chave)
);
CREATE SEQUENCE integracao.evento_seq START 1;
CREATE INDEX idx_integracao_evento_fila ON integracao.evento (id) WHERE status = 'recebido';
CREATE INDEX idx_integracao_evento_pessoa ON integracao.evento (entrada_id, id_hash, recebido_em DESC);
CREATE INDEX idx_integracao_evento_limpeza ON integracao.evento (recebido_em);

-- Fila do AD: pedido da integração fecha o chamado sozinho e pode esperar a confirmação de um técnico.
ALTER TABLE itsm.execucao_ad ADD COLUMN fechar_ao_concluir bool NOT NULL DEFAULT false;
ALTER TABLE itsm.execucao_ad DROP CONSTRAINT ck_itsm_execucao_status;
ALTER TABLE itsm.execucao_ad ADD CONSTRAINT ck_itsm_execucao_status CHECK (status IN ('aguardando_aprovacao', 'aguardando_confirmacao',
    'pronta', 'executando', 'sucesso', 'erro', 'recusada', 'cancelada'));

INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, updater, deleted) VALUES
(59420, 'Integrações', '', 2, 5, 2, 'integracoes', 'ep:connection', 'infra/integracoes/index', 'InfraIntegracoes', 0, true, true, false, '1', '1', 0),
(59421, 'Ver integrações', 'infra:integracao:query', 3, 1, 59420, '', '', '', NULL, 0, true, true, true, '1', '1', 0),
(59422, 'Configurar integrações', 'infra:integracao:update', 3, 2, 59420, '', '', '', NULL, 0, true, true, true, '1', '1', 0);
