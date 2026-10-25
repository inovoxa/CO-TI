-- ========== F3c-2 — inventário por SNMP (impressoras, switches e o que não tem agente) ==========
-- Ver docs/specs/inventario-snmp.md. MIBs padrão: MIB-II (RFC 1213), IF-MIB, ENTITY-MIB, HOST-RESOURCES e
-- Printer-MIB (RFC 3805).

-- Credenciais tentadas em ordem; segredos cifrados com a chave-mestra (CO_TI_CHAVE_MESTRA)
CREATE TABLE cmdb.snmp_credencial (
    id                  int8          NOT NULL,
    nome                varchar(100)  NOT NULL,
    versao              varchar(3)    NOT NULL,      -- v2c | v3
    comunidade_cifrada  text          NULL,          -- v2c
    usuario             varchar(100)  NULL,          -- v3
    nivel               varchar(20)   NULL,          -- noAuthNoPriv | authNoPriv | authPriv
    auth_protocolo      varchar(10)   NULL,          -- MD5 | SHA | SHA256 | SHA512
    auth_senha_cifrada  text          NULL,
    priv_protocolo      varchar(10)   NULL,          -- DES | AES | AES256
    priv_senha_cifrada  text          NULL,
    porta               int4          NOT NULL DEFAULT 161,
    ordem               int4          NOT NULL DEFAULT 0,
    ativo               bool          NOT NULL DEFAULT true,
    creator             varchar(64)   NULL DEFAULT '',
    create_time         timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater             varchar(64)   NULL DEFAULT '',
    update_time         timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             int2          NOT NULL DEFAULT 0,
    CONSTRAINT pk_cmdb_snmp_credencial PRIMARY KEY (id),
    CONSTRAINT ck_cmdb_snmp_credencial_versao CHECK (versao IN ('v2c', 'v3')),
    CONSTRAINT ck_cmdb_snmp_credencial_porta CHECK (porta BETWEEN 1 AND 65535)
);
CREATE SEQUENCE cmdb.snmp_credencial_seq START 1;

-- Qual credencial respondeu em cada IP e quando (para tentar ela primeiro e mostrar o resultado da última varredura)
CREATE TABLE cmdb.snmp_alvo (
    id              int8          NOT NULL,
    ip              varchar(45)   NOT NULL,
    credencial_id   int8          NULL,
    ativo_id        int8          NULL,
    ultima_tentativa timestamp    NULL,
    ultima_resposta timestamp     NULL,
    erro            varchar(255)  NULL,
    creator         varchar(64)   NULL DEFAULT '',
    create_time     timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater         varchar(64)   NULL DEFAULT '',
    update_time     timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         int2          NOT NULL DEFAULT 0,
    CONSTRAINT pk_cmdb_snmp_alvo PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_cmdb_snmp_alvo_ip ON cmdb.snmp_alvo (ip) WHERE deleted = 0;
CREATE SEQUENCE cmdb.snmp_alvo_seq START 1;

-- Ativo vindo do SNMP; contador de páginas das impressoras
ALTER TABLE cmdb.ativo DROP CONSTRAINT ck_cmdb_ativo_origem;
ALTER TABLE cmdb.ativo ADD CONSTRAINT ck_cmdb_ativo_origem CHECK (origem IN ('ocs', 'glpi_agent', 'snmp', 'manual'));
ALTER TABLE cmdb.ativo ADD COLUMN paginas int8 NULL;
ALTER TABLE cmdb.ativo ADD COLUMN localizacao varchar(255) NULL;

-- Toner/suprimento baixo
INSERT INTO cmdb.alerta_regra (id, codigo, nome, ativo, abrir_chamado, limite, urgencia) VALUES
(7, 'suprimento_baixo', 'Toner ou suprimento de impressora acabando', true, false, 10, 3);

-- ========== Menu ==========
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, updater, deleted) VALUES
(53060, 'SNMP',                     '', 2, 6, 53000, 'snmp', 'ep:connection', 'inventario/snmp/index', 'InventarioSnmp', 0, true, true, true, '1', '1', 0),
(53061, 'Configurar SNMP',          'inventario:snmp:update', 3, 1, 53060, '', '', '', NULL, 0, true, true, true, '1', '1', 0);

INSERT INTO system_role_menu (id, role_id, menu_id, creator, updater, deleted, tenant_id)
SELECT nextval('system_role_menu_seq'), r, m, '1', '1', 0, 1
FROM (VALUES (50001), (50002)) AS papeis(r)
CROSS JOIN (VALUES (53060), (53061)) AS menus(m);
