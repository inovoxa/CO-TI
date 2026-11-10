-- Tipos de monitor de serviço: bancos SQL, SNMP, container Docker (API remota), Redis, MongoDB, SMTP, RADIUS, MQTT e
-- RabbitMQ. Senhas, comunidades e segredos ficam em "segredos", cifrados com a chave-mestra.
ALTER TABLE monitor.monitor
    ADD COLUMN usuario        varchar(200) NULL,
    ADD COLUMN banco          varchar(200) NULL,          -- banco (SQL, MongoDB) ou serviço (Oracle)
    ADD COLUMN consulta       text         NULL,          -- SQL, OID do SNMP, nome do container ou tópico MQTT
    ADD COLUMN opcoes         text         NULL,          -- objeto JSON com as opções do tipo (TLS, versão do SNMP...)
    ADD COLUMN segredos       text         NULL,          -- objeto JSON cifrado: senha e segredo
    ADD COLUMN docker_host_id int8         NULL;

ALTER TABLE monitor.monitor DROP CONSTRAINT ck_monitor_tipo;
ALTER TABLE monitor.monitor ADD CONSTRAINT ck_monitor_tipo CHECK (tipo IN ('http', 'palavra', 'json', 'tcp', 'ping', 'dns', 'push',
    'manual', 'postgres', 'mysql', 'sqlserver', 'oracle', 'snmp', 'docker', 'redis', 'mongodb', 'smtp', 'radius', 'mqtt', 'rabbitmq'));

-- Servidores Docker acessados pela API remota (https com certificado de cliente, ou http na rede interna)
CREATE TABLE monitor.docker_host (
    id            int8          NOT NULL,
    nome          varchar(100)  NOT NULL,
    url           varchar(500)  NOT NULL,
    ca            text          NULL,          -- PEM da autoridade que assinou o certificado do servidor
    certificado   text          NULL,          -- PEM do certificado de cliente
    chave         text          NULL,          -- PEM da chave do cliente, cifrado com a chave-mestra
    creator       varchar(64)   NULL DEFAULT '',
    create_time   timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater       varchar(64)   NULL DEFAULT '',
    update_time   timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted       int2          NOT NULL DEFAULT 0,
    CONSTRAINT pk_monitor_docker_host PRIMARY KEY (id)
);
CREATE SEQUENCE monitor.docker_host_seq START 1;
