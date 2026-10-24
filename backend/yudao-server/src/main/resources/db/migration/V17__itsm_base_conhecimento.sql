-- ========== ITSM F1d — base de conhecimento (GLPI: glpi_knowbaseitems, _revisions, _items, categorias) ==========

-- Busca sem acento: unaccent não é IMMUTABLE, então a função de busca fixa o dicionário (padrão do PostgreSQL)
CREATE EXTENSION IF NOT EXISTS unaccent;
CREATE OR REPLACE FUNCTION itsm.sem_acento(texto text) RETURNS text
    LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT
    AS $$ SELECT public.unaccent('public.unaccent'::regdictionary, texto) $$;

CREATE TABLE itsm.kb_categoria (
    id            int8          NOT NULL,
    parent_id     int8          NOT NULL DEFAULT 0,
    nome          varchar(100)  NOT NULL,
    nome_completo varchar(1000) NOT NULL,
    descricao     varchar(1000) NULL,
    ordem         int4          NOT NULL DEFAULT 0,
    creator       varchar(64)   NULL DEFAULT '',
    create_time   timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater       varchar(64)   NULL DEFAULT '',
    update_time   timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted       int2          NOT NULL DEFAULT 0,
    CONSTRAINT pk_itsm_kb_categoria PRIMARY KEY (id)
);
CREATE SEQUENCE itsm.kb_categoria_seq START 1;

CREATE TABLE itsm.kb_artigo (
    id                int8          NOT NULL,
    titulo            varchar(255)  NOT NULL,
    resumo            varchar(1000) NULL,
    conteudo          text          NOT NULL,        -- HTML saneado
    texto             text          NOT NULL,        -- texto puro do conteúdo (busca e trechos)
    categoria_id      int8          NULL,
    faq               bool          NOT NULL DEFAULT false, -- visível para todos no portal (GLPI: is_faq)
    publicado         bool          NOT NULL DEFAULT true,  -- rascunho = false
    fixado            bool          NOT NULL DEFAULT false, -- aparece primeiro (GLPI: is_pinned)
    inicio_publicacao timestamp     NULL,
    fim_publicacao    timestamp     NULL,
    autor_id          int8          NOT NULL,
    revisao           int4          NOT NULL DEFAULT 1,
    visualizacoes     int4          NOT NULL DEFAULT 0,
    util              int4          NOT NULL DEFAULT 0,
    nao_util          int4          NOT NULL DEFAULT 0,
    creator           varchar(64)   NULL DEFAULT '',
    create_time       timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater           varchar(64)   NULL DEFAULT '',
    update_time       timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           int2          NOT NULL DEFAULT 0,
    busca             tsvector GENERATED ALWAYS AS (
        setweight(to_tsvector('portuguese', itsm.sem_acento(coalesce(titulo, ''))), 'A') ||
        setweight(to_tsvector('portuguese', itsm.sem_acento(coalesce(resumo, ''))), 'B') ||
        setweight(to_tsvector('portuguese', itsm.sem_acento(coalesce(texto, ''))), 'C')) STORED,
    CONSTRAINT pk_itsm_kb_artigo PRIMARY KEY (id)
);
CREATE INDEX idx_itsm_kb_artigo_busca ON itsm.kb_artigo USING gin (busca);
CREATE INDEX idx_itsm_kb_artigo_categoria ON itsm.kb_artigo (categoria_id) WHERE deleted = 0;
CREATE SEQUENCE itsm.kb_artigo_seq START 1;

-- Versões anteriores (a cada edição de título ou conteúdo)
CREATE TABLE itsm.kb_revisao (
    id        int8         NOT NULL,
    artigo_id int8         NOT NULL,
    revisao   int4         NOT NULL,
    titulo    varchar(255) NOT NULL,
    conteudo  text         NOT NULL,
    autor_id  int8         NULL,
    criado_em timestamp    NOT NULL,
    CONSTRAINT pk_itsm_kb_revisao PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_itsm_kb_revisao ON itsm.kb_revisao (artigo_id, revisao);
CREATE SEQUENCE itsm.kb_revisao_seq START 1;

-- Artigo usado num chamado (GLPI: glpi_knowbaseitems_items)
CREATE TABLE itsm.kb_artigo_chamado (
    id         int8      NOT NULL,
    artigo_id  int8      NOT NULL,
    chamado_id int8      NOT NULL,
    user_id    int8      NULL,
    criado_em  timestamp NOT NULL,
    CONSTRAINT pk_itsm_kb_artigo_chamado PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_itsm_kb_artigo_chamado ON itsm.kb_artigo_chamado (artigo_id, chamado_id);
CREATE INDEX idx_itsm_kb_artigo_chamado_ch ON itsm.kb_artigo_chamado (chamado_id);
CREATE SEQUENCE itsm.kb_artigo_chamado_seq START 1;

-- "Este artigo ajudou?" — um voto por pessoa (pode mudar)
CREATE TABLE itsm.kb_voto (
    artigo_id int8      NOT NULL,
    user_id   int8      NOT NULL,
    util      bool      NOT NULL,
    votado_em timestamp NOT NULL,
    CONSTRAINT pk_itsm_kb_voto PRIMARY KEY (artigo_id, user_id)
);

-- ---------- Menus ----------
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, updater, deleted) VALUES
(51130, 'Base de conhecimento', '', 2, 3, 0, '/base-conhecimento', 'ep:reading', 'itsm/kb/index', 'ItsmKb', 0, true, true, false, '1', '1', 0),
(51131, 'Ver artigos internos', 'itsm:kb:query',  3, 1, 51130, '', '', '', NULL, 0, true, true, true, '1', '1', 0),
(51132, 'Escrever artigos',     'itsm:kb:update', 3, 2, 51130, '', '', '', NULL, 0, true, true, true, '1', '1', 0);

-- todos leem a FAQ; NOC, Service Desk e SOC leem os internos; NOC e Service Desk escrevem
INSERT INTO system_role_menu (id, role_id, menu_id, creator, updater, deleted, tenant_id)
SELECT nextval('system_role_menu_seq'), r, 51130, '1', '1', 0, 1
FROM (VALUES (2), (50001), (50002), (50003), (50004), (50005)) AS papeis(r)
UNION ALL
SELECT nextval('system_role_menu_seq'), r, 51131, '1', '1', 0, 1 FROM (VALUES (50001), (50002), (50003)) AS papeis(r)
UNION ALL
SELECT nextval('system_role_menu_seq'), r, 51132, '1', '1', 0, 1 FROM (VALUES (50001), (50002)) AS papeis(r);
