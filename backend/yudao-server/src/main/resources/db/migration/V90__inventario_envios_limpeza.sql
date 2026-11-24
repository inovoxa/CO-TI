-- Inventário: registro de cada envio dos agentes e limpeza de máquinas repetidas ou paradas.
-- Ver docs/specs/inventario-agentes.md ("Envios recebidos" e "Limpeza").

-- Cada pedido de agente (PROLOG, INVENTORY, contact...), aceito ou recusado. Guardado por
-- CO_TI_INVENTARIO_ENVIOS_DIAS (padrão 30 dias).
CREATE TABLE cmdb.agente_envio (
    id          int8          GENERATED ALWAYS AS IDENTITY,
    quando      timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ip          varchar(64)   NULL,
    device_id   varchar(255)  NULL,
    formato     varchar(10)   NULL,       -- ocs | glpi
    consulta    varchar(20)   NULL,       -- PROLOG | INVENTORY | DOWNLOAD | CONTACT...
    resultado   varchar(20)   NOT NULL,   -- ver EnvioAgenteService
    detalhe     varchar(500)  NULL,
    agente      varchar(255)  NULL,
    bytes       int4          NULL,
    ms          int4          NULL,
    ativo_id    int8          NULL,
    CONSTRAINT pk_cmdb_agente_envio PRIMARY KEY (id)
);
CREATE INDEX idx_cmdb_agente_envio_quando ON cmdb.agente_envio (quando DESC);
CREATE INDEX idx_cmdb_agente_envio_device ON cmdb.agente_envio (device_id, quando DESC);
CREATE INDEX idx_cmdb_agente_envio_ativo ON cmdb.agente_envio (ativo_id, quando DESC);
CREATE INDEX idx_cmdb_agente_envio_ip ON cmdb.agente_envio (ip, quando DESC);

-- Ativo mesclado em outro: o registro sai das listas (deleted = 1) e o histórico dele aparece no que ficou.
ALTER TABLE cmdb.ativo ADD COLUMN mesclado_em int8 NULL;
CREATE INDEX idx_cmdb_ativo_mesclado ON cmdb.ativo (mesclado_em) WHERE mesclado_em IS NOT NULL;

-- "São máquinas diferentes": o par deixa de aparecer como repetido (menor id primeiro).
CREATE TABLE cmdb.ativo_distinto (
    ativo_a     int8          NOT NULL,
    ativo_b     int8          NOT NULL,
    user_id     int8          NULL,
    quando      timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_cmdb_ativo_distinto PRIMARY KEY (ativo_a, ativo_b),
    CONSTRAINT ck_cmdb_ativo_distinto CHECK (ativo_a < ativo_b)
);
