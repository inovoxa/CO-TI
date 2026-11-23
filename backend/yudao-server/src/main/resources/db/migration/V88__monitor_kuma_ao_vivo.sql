-- Uptime Kuma ao vivo: o Kuma continua verificando e o CO-TI lê o estado de cada monitor pelo /metrics dele
-- (Prometheus, com chave de API). O monitor do tipo "kuma" espelha o estado que o Kuma informa.
CREATE TABLE monitor.kuma_servidor (
    id            int8          NOT NULL,
    nome          varchar(100)  NOT NULL,
    url           varchar(500)  NOT NULL,          -- endereço do Kuma, ex.: https://kuma.orgao.gov.br
    usuario       varchar(100)  NULL,              -- só quando o Kuma não tem chave de API (login comum)
    chave         text          NULL,              -- chave de API (uk1_...) ou senha, cifrada com a chave-mestra
    ignorar_tls   bool          NOT NULL DEFAULT false,
    creator       varchar(64)   NULL DEFAULT '',
    create_time   timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater       varchar(64)   NULL DEFAULT '',
    update_time   timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted       int2          NOT NULL DEFAULT 0,
    CONSTRAINT pk_monitor_kuma_servidor PRIMARY KEY (id)
);
CREATE SEQUENCE monitor.kuma_servidor_seq START 1;

-- monitor do tipo "kuma": servidor de origem; o id (ou o nome) do monitor no Kuma vai em "consulta"
ALTER TABLE monitor.monitor ADD COLUMN kuma_servidor_id int8 NULL;

ALTER TABLE monitor.monitor DROP CONSTRAINT ck_monitor_tipo;
ALTER TABLE monitor.monitor ADD CONSTRAINT ck_monitor_tipo CHECK (tipo IN ('http', 'palavra', 'json', 'tcp', 'ping', 'dns', 'push',
    'manual', 'postgres', 'mysql', 'sqlserver', 'oracle', 'snmp', 'docker', 'redis', 'mongodb', 'smtp', 'radius', 'mqtt', 'rabbitmq',
    'kuma'));
