-- Controle de estoque da GTI: o que está guardado ou entregue. Equipamentos com agente continuam no inventário.
-- Item não se apaga: sai por baixa. Movimento só se acrescenta, porque é o histórico de por quem cada item passou.
CREATE TABLE itsm.estoque_tipo (
    id          int8         NOT NULL,
    nome        varchar(80)  NOT NULL,
    controle    varchar(12)  NOT NULL,           -- unidade: cada peça tem identificação | quantidade: só o saldo
    ativo       bool         NOT NULL DEFAULT true,
    creator     varchar(64)  NULL DEFAULT '',
    create_time timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater     varchar(64)  NULL DEFAULT '',
    update_time timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     int2         NOT NULL DEFAULT 0,
    CONSTRAINT pk_itsm_estoque_tipo PRIMARY KEY (id),
    CONSTRAINT ck_itsm_estoque_tipo_controle CHECK (controle IN ('unidade', 'quantidade'))
);
CREATE UNIQUE INDEX uk_itsm_estoque_tipo_nome ON itsm.estoque_tipo (lower(nome)) WHERE deleted = 0;
CREATE SEQUENCE itsm.estoque_tipo_seq START 100;

INSERT INTO itsm.estoque_tipo (id, nome, controle, creator, updater) VALUES
(1, 'Celular', 'unidade', '1', '1'),
(2, 'Chip', 'unidade', '1', '1'),
(3, 'Monitor', 'unidade', '1', '1'),
(4, 'HD/SSD', 'unidade', '1', '1'),
(5, 'Computador', 'unidade', '1', '1'),
(6, 'Memória', 'quantidade', '1', '1'),
(7, 'Periféricos e cabos', 'quantidade', '1', '1'),
(8, 'Outros', 'quantidade', '1', '1');

CREATE TABLE itsm.estoque_item (
    id             int8          NOT NULL,
    tipo_id        int8          NOT NULL,
    descricao      varchar(200)  NOT NULL,       -- ex.: Samsung A15 128 GB, DDR4 8 GB
    identificador  varchar(80)   NULL,           -- IMEI, ICCID ou número de série (controle por unidade)
    linha          varchar(20)   NULL,           -- número da linha do chip ou celular
    patrimonio     varchar(50)   NULL,
    condicao       varchar(8)    NOT NULL,       -- novo | usado
    situacao       varchar(12)   NOT NULL DEFAULT 'disponivel', -- disponivel | em_uso | manutencao | baixado
    quantidade     int4          NOT NULL DEFAULT 1,
    minimo         int4          NOT NULL DEFAULT 0, -- aviso de estoque baixo (controle por quantidade)
    data_compra    date          NULL,
    fornecedor     varchar(150)  NULL,
    nota_fiscal    varchar(60)   NULL,
    valor          numeric(12,2) NULL,
    garantia_ate   date          NULL,
    usado_por      varchar(150)  NULL,           -- item usado: quem usava antes de entrar no estoque
    responsavel    varchar(150)  NULL,           -- quem está com o item agora
    destino        varchar(150)  NULL,           -- setor ou unidade de quem está com o item
    observacao     varchar(1000) NULL,
    creator        varchar(64)   NULL DEFAULT '',
    create_time    timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater        varchar(64)   NULL DEFAULT '',
    update_time    timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted        int2          NOT NULL DEFAULT 0,
    CONSTRAINT pk_itsm_estoque_item PRIMARY KEY (id),
    CONSTRAINT ck_itsm_estoque_condicao CHECK (condicao IN ('novo', 'usado')),
    CONSTRAINT ck_itsm_estoque_situacao CHECK (situacao IN ('disponivel', 'em_uso', 'manutencao', 'baixado')),
    CONSTRAINT ck_itsm_estoque_quantidade CHECK (quantidade >= 0 AND minimo >= 0)
);
CREATE UNIQUE INDEX uk_itsm_estoque_identificador ON itsm.estoque_item (tipo_id, lower(identificador))
    WHERE deleted = 0 AND identificador IS NOT NULL;
CREATE INDEX idx_itsm_estoque_item_tipo ON itsm.estoque_item (tipo_id, situacao);
CREATE SEQUENCE itsm.estoque_item_seq START 1;

CREATE TABLE itsm.estoque_movimento (
    id          int8          NOT NULL,
    item_id     int8          NOT NULL,
    tipo        varchar(12)   NOT NULL,          -- entrada | retirada | devolucao | manutencao | retorno | baixa
    quantidade  int4          NOT NULL DEFAULT 1,
    pessoa      varchar(150)  NULL,              -- quem retirou ou devolveu
    destino     varchar(150)  NULL,
    motivo      varchar(500)  NULL,
    user_id     int8          NULL,              -- quem registrou
    quando      timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    creator     varchar(64)   NULL DEFAULT '',
    create_time timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater     varchar(64)   NULL DEFAULT '',
    update_time timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     int2          NOT NULL DEFAULT 0,
    CONSTRAINT pk_itsm_estoque_movimento PRIMARY KEY (id),
    CONSTRAINT ck_itsm_estoque_mov_tipo CHECK (tipo IN ('entrada', 'retirada', 'devolucao', 'manutencao', 'retorno', 'baixa')),
    CONSTRAINT ck_itsm_estoque_mov_qtd CHECK (quantidade > 0)
);
CREATE INDEX idx_itsm_estoque_mov_item ON itsm.estoque_movimento (item_id, quando DESC);
CREATE SEQUENCE itsm.estoque_movimento_seq START 1;

INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, updater, deleted) VALUES
(59100, 'Estoque GTI', '', 2, 4, 0, '/estoque', 'ep:box', 'itsm/estoque/index', 'ItsmEstoque', 0, true, true, false, '1', '1', 0),
(59101, 'Ver o estoque', 'itsm:estoque:query', 3, 1, 59100, '', '', '', NULL, 0, true, true, true, '1', '1', 0),
(59102, 'Movimentar o estoque', 'itsm:estoque:update', 3, 2, 59100, '', '', '', NULL, 0, true, true, true, '1', '1', 0);

-- Service Desk e Gestão de TI: quem entrega e controla os equipamentos
INSERT INTO system_role_menu (id, role_id, menu_id, creator, updater, deleted, tenant_id)
SELECT nextval('system_role_menu_seq'), r, m, '1', '1', 0, 1
FROM (VALUES (50002), (50007)) AS papeis(r)
CROSS JOIN (VALUES (59100), (59101), (59102)) AS menus(m);
