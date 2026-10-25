-- ========== F3c-3 — implantação de pacotes pelo agente OCS (teledeploy) ==========
-- Ver docs/specs/inventario-pacotes.md. Protocolo conferido em Apache/Ocsinventory/Server/Capacities/Download.pm e
-- ocsreports/require/teledeploy/PackageBuilder.php (OCS Inventory NG 2.12.6, GPL-2.0-or-later).

CREATE TABLE cmdb.pacote (
    id                 int8          NOT NULL,
    nome               varchar(150)  NOT NULL,
    descricao          text          NULL,
    plataforma         varchar(10)   NOT NULL DEFAULT 'windows',
    acao               varchar(10)   NOT NULL,      -- EXECUTE | STORE | LAUNCH
    comando            varchar(1000) NULL,          -- EXECUTE: linha de comando; LAUNCH: arquivo a abrir
    caminho            varchar(500)  NULL,          -- STORE: pasta de destino
    prioridade         int2          NOT NULL DEFAULT 5,
    notificar          bool          NOT NULL DEFAULT false,
    notificar_texto    varchar(500)  NULL,
    fileid             varchar(20)   NOT NULL,      -- identificador do pacote no protocolo (como o OCS: segundos)
    arquivo_nome       varchar(255)  NULL,
    tamanho            int8          NULL,
    md5                varchar(32)   NULL,          -- DIGEST do protocolo (o agente confere)
    sha256             varchar(64)   NULL,
    fragmentos         int4          NOT NULL DEFAULT 0,
    ativo              bool          NOT NULL DEFAULT true,
    user_id            int8          NULL,
    creator            varchar(64)   NULL DEFAULT '',
    create_time        timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater            varchar(64)   NULL DEFAULT '',
    update_time        timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted            int2          NOT NULL DEFAULT 0,
    CONSTRAINT pk_cmdb_pacote PRIMARY KEY (id),
    CONSTRAINT ck_cmdb_pacote_acao CHECK (acao IN ('EXECUTE', 'STORE', 'LAUNCH')),
    CONSTRAINT ck_cmdb_pacote_plataforma CHECK (plataforma IN ('windows', 'linux', 'macos')),
    CONSTRAINT ck_cmdb_pacote_prioridade CHECK (prioridade BETWEEN 0 AND 10)
);
CREATE UNIQUE INDEX uk_cmdb_pacote_fileid ON cmdb.pacote (fileid);
CREATE SEQUENCE cmdb.pacote_seq START 1;

-- Pacote para uma máquina: aguardando → notificado (o agente recebeu) → sucesso | erro; ou cancelado
CREATE TABLE cmdb.pacote_implantacao (
    id             int8          NOT NULL,
    pacote_id      int8          NOT NULL,
    ativo_id       int8          NOT NULL,
    situacao       varchar(12)   NOT NULL DEFAULT 'aguardando',
    codigo         varchar(60)   NULL,          -- o que o agente respondeu (SUCCESS, ERR_BAD_DIGEST...)
    dept_id        int8          NULL,          -- unidade da máquina (escopo)
    pedido_por     int8          NULL,
    pedido_em      timestamp     NOT NULL,
    notificado_em  timestamp     NULL,
    concluido_em   timestamp     NULL,
    creator        varchar(64)   NULL DEFAULT '',
    create_time    timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater        varchar(64)   NULL DEFAULT '',
    update_time    timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted        int2          NOT NULL DEFAULT 0,
    CONSTRAINT pk_cmdb_pacote_implantacao PRIMARY KEY (id),
    CONSTRAINT ck_cmdb_pacote_implantacao_situacao CHECK (situacao IN ('aguardando', 'notificado', 'sucesso', 'erro', 'cancelado'))
);
-- uma implantação em andamento por pacote e máquina
CREATE UNIQUE INDEX uk_cmdb_pacote_implantacao_aberta ON cmdb.pacote_implantacao (pacote_id, ativo_id)
    WHERE deleted = 0 AND situacao IN ('aguardando', 'notificado');
CREATE INDEX idx_cmdb_pacote_implantacao_ativo ON cmdb.pacote_implantacao (ativo_id) WHERE deleted = 0;
CREATE INDEX idx_cmdb_pacote_implantacao_pacote ON cmdb.pacote_implantacao (pacote_id) WHERE deleted = 0;
CREATE SEQUENCE cmdb.pacote_implantacao_seq START 1;

-- ========== Menu ==========
-- Implantar executa comandos nas máquinas: a permissão de edição fica só com o administrador até alguém conceder
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, updater, deleted) VALUES
(53070, 'Pacotes',                  '', 2, 7, 53000, 'pacotes', 'ep:box', 'inventario/pacote/index', 'InventarioPacote', 0, true, true, true, '1', '1', 0),
(53071, 'Ver pacotes',              'inventario:pacote:query',  3, 1, 53070, '', '', '', NULL, 0, true, true, true, '1', '1', 0),
(53072, 'Criar e implantar pacotes', 'inventario:pacote:update', 3, 2, 53070, '', '', '', NULL, 0, true, true, true, '1', '1', 0);

INSERT INTO system_role_menu (id, role_id, menu_id, creator, updater, deleted, tenant_id)
SELECT nextval('system_role_menu_seq'), r, m, '1', '1', 0, 1
FROM (VALUES (50001), (50002), (50007)) AS papeis(r)
CROSS JOIN (VALUES (53070), (53071)) AS menus(m);
