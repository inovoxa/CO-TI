-- Acesso rápido: links públicos, que o administrador cadastra e todos veem, e pessoais, só de quem os criou.
CREATE SCHEMA IF NOT EXISTS portal;
GRANT USAGE ON SCHEMA portal TO coti_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA portal GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO coti_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA portal GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO coti_app;

CREATE TABLE portal.link (
    id          int8          NOT NULL,
    titulo      varchar(100)  NOT NULL,
    url         varchar(1000) NOT NULL,
    descricao   varchar(200)  NULL,
    categoria   varchar(60)   NULL,      -- só nos públicos, para agrupar
    dono_id     int8          NULL,      -- vazio = público
    ordem       int4          NOT NULL DEFAULT 0,
    creator     varchar(64)   NULL DEFAULT '',
    create_time timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater     varchar(64)   NULL DEFAULT '',
    update_time timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     int2          NOT NULL DEFAULT 0,
    CONSTRAINT pk_portal_link PRIMARY KEY (id)
);
CREATE INDEX idx_portal_link_dono ON portal.link (dono_id) WHERE deleted = 0;
CREATE SEQUENCE portal.link_seq START 1;

INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, updater, deleted) VALUES
(59300, 'Acesso rápido', '', 2, 3, 0, '/acesso-rapido', 'ep:link', 'acesso/index', 'AcessoRapido', 0, true, true, false, '1', '1', 0),
(59301, 'Usar o acesso rápido', 'acesso:link:usar', 3, 1, 59300, '', '', '', NULL, 0, true, true, true, '1', '1', 0),
(59302, 'Gerenciar links públicos', 'acesso:link:gerenciar', 3, 2, 59300, '', '', '', NULL, 0, true, true, true, '1', '1', 0);

-- Todo servidor com login usa; o telão é uma tela fixa. Links públicos: só o administrador, que já tem todas as permissões.
-- Para delegar, inclua "Gerenciar links públicos" em outro perfil.
INSERT INTO system_role_menu (id, role_id, menu_id, creator, updater, deleted, tenant_id)
SELECT nextval('system_role_menu_seq'), r.id, m.id, '1', '1', 0, 1
FROM system_role r
CROSS JOIN (VALUES (59300), (59301)) AS m(id)
WHERE r.deleted = 0 AND r.code <> 'telao';
