-- ========== ITSM F1e (parte 3) — importação de um GLPI 10/11 existente ==========

-- Uma execução do importador. A senha do banco de origem não é gravada: fica só na memória durante a execução.
CREATE TABLE itsm.importacao (
    id             int8          NOT NULL,
    origem         varchar(20)   NOT NULL DEFAULT 'glpi',
    status         varchar(12)   NOT NULL DEFAULT 'pendente',   -- pendente | executando | concluida | erro | cancelada
    host           varchar(255)  NOT NULL,
    porta          int4          NOT NULL DEFAULT 3306,
    banco          varchar(64)   NOT NULL,
    usuario        varchar(64)   NOT NULL,
    opcoes         text          NOT NULL DEFAULT '{}',          -- o que importar e como
    etapa          varchar(40)   NULL,
    progresso      text          NOT NULL DEFAULT '{}',          -- {etapa: {lidos, importados, existentes, erros}}
    log            text          NOT NULL DEFAULT '',
    versao_origem  varchar(20)   NULL,
    cancelar       bool          NOT NULL DEFAULT false,
    iniciado_em    timestamp     NULL,
    terminado_em   timestamp     NULL,
    creator        varchar(64)   NULL DEFAULT '',
    create_time    timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater        varchar(64)   NULL DEFAULT '',
    update_time    timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted        int2          NOT NULL DEFAULT 0,
    CONSTRAINT pk_itsm_importacao PRIMARY KEY (id),
    CONSTRAINT ck_itsm_importacao_status CHECK (status IN ('pendente', 'executando', 'concluida', 'erro', 'cancelada'))
);
-- uma importação por vez
CREATE UNIQUE INDEX uk_itsm_importacao_executando ON itsm.importacao (origem) WHERE status = 'executando' AND deleted = 0;
CREATE SEQUENCE itsm.importacao_seq START 1;

-- O que já veio de cada origem: rodar de novo não duplica (o que já está no mapa é pulado)
CREATE TABLE itsm.importacao_mapa (
    origem      varchar(20)  NOT NULL,
    tipo        varchar(30)  NOT NULL,
    id_origem   varchar(64)  NOT NULL,
    id_destino  int8         NOT NULL,
    criado_em   timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_itsm_importacao_mapa PRIMARY KEY (origem, tipo, id_origem)
);

INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, updater, deleted) VALUES
(51026, 'Importar do GLPI', '', 2, 20, 51020, 'importar-glpi', 'ep:upload', 'itsm/importacao/index', 'ItsmImportacao', 0, true, true, true, '1', '1', 0),
(51027, 'Importar dados de outro sistema', 'itsm:importacao:update', 3, 1, 51026, '', '', '', NULL, 0, true, true, true, '1', '1', 0);
-- sem perfil padrão: só o administrador (ou quem ele autorizar)
