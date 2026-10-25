-- ========== F3c-1 — descoberta de rede (IPDiscover do agente OCS) ==========
-- Ver docs/specs/inventario-rede.md. Protocolo conferido em Apache/Ocsinventory/Server/Capacities/Ipdiscover.pm
-- (OCS Inventory NG 2.12.6, GPL-2.0-or-later).

-- Agente escolhido para varrer cada sub-rede
CREATE TABLE cmdb.rede_varredor (
    id            int8          NOT NULL,
    subrede       varchar(45)   NOT NULL,
    mascara       varchar(45)   NULL,
    ativo_id      int8          NOT NULL,
    eleito_em     timestamp     NOT NULL,
    ultima_varredura timestamp  NULL,
    creator       varchar(64)   NULL DEFAULT '',
    create_time   timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater       varchar(64)   NULL DEFAULT '',
    update_time   timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted       int2          NOT NULL DEFAULT 0,
    CONSTRAINT pk_cmdb_rede_varredor PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_cmdb_rede_varredor ON cmdb.rede_varredor (subrede, ativo_id) WHERE deleted = 0;
CREATE SEQUENCE cmdb.rede_varredor_seq START 1;

-- O que apareceu na rede (um registro por MAC)
CREATE TABLE cmdb.rede_dispositivo (
    id            int8          NOT NULL,
    mac           varchar(17)   NOT NULL,
    ip            varchar(45)   NOT NULL,
    nome          varchar(255)  NULL,
    subrede       varchar(45)   NULL,
    mascara       varchar(45)   NULL,
    ativo_id      int8          NULL,       -- já inventariado/cadastrado
    ignorado      bool          NOT NULL DEFAULT false,
    observacao    varchar(500)  NULL,
    visto_por     int8          NULL,       -- ativo que varreu
    dept_id       int8          NULL,       -- unidade do varredor (escopo)
    primeira_vez  timestamp     NOT NULL,
    ultima_vez    timestamp     NOT NULL,
    creator       varchar(64)   NULL DEFAULT '',
    create_time   timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater       varchar(64)   NULL DEFAULT '',
    update_time   timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted       int2          NOT NULL DEFAULT 0,
    CONSTRAINT pk_cmdb_rede_dispositivo PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_cmdb_rede_dispositivo_mac ON cmdb.rede_dispositivo (mac) WHERE deleted = 0;
CREATE INDEX idx_cmdb_rede_dispositivo_subrede ON cmdb.rede_dispositivo (subrede) WHERE deleted = 0;
CREATE INDEX idx_cmdb_rede_dispositivo_dept ON cmdb.rede_dispositivo (dept_id) WHERE deleted = 0;
CREATE SEQUENCE cmdb.rede_dispositivo_seq START 1;

-- Alerta de equipamento desconhecido na rede (desligado no começo: a primeira varredura acha muita coisa)
ALTER TABLE cmdb.alerta_ativo DROP CONSTRAINT ck_cmdb_alerta_ativo_objeto;
ALTER TABLE cmdb.alerta_ativo ADD CONSTRAINT ck_cmdb_alerta_ativo_objeto CHECK (objeto IN ('ativo', 'licenca', 'rede'));
INSERT INTO cmdb.alerta_regra (id, codigo, nome, ativo, abrir_chamado, limite, urgencia) VALUES
(6, 'rede_desconhecido', 'Equipamento desconhecido na rede', false, false, 1, 3);

-- ========== Menu ==========
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, updater, deleted) VALUES
(53050, 'Rede',                     '', 2, 5, 53000, 'rede', 'ep:share', 'inventario/rede/index', 'InventarioRede', 0, true, true, true, '1', '1', 0);

INSERT INTO system_role_menu (id, role_id, menu_id, creator, updater, deleted, tenant_id)
SELECT nextval('system_role_menu_seq'), r, 53050, '1', '1', 0, 1
FROM (VALUES (50001), (50002), (50007)) AS papeis(r);
