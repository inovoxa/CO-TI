-- Calendário: aniversariantes e agenda individual.
-- A data de nascimento é informada pela própria pessoa em Meu perfil; só aparece para os outros se ela marcar como pública,
-- e mesmo assim só dia e mês (o ano, que revela a idade, fica só para ela).
CREATE SCHEMA IF NOT EXISTS calendario;
GRANT USAGE ON SCHEMA calendario TO coti_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA calendario GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO coti_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA calendario GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO coti_app;

CREATE TABLE calendario.nascimento (
    usuario_id   int8      NOT NULL,
    data         date      NOT NULL,
    publico      boolean   NOT NULL DEFAULT false,
    update_time  timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_calendario_nascimento PRIMARY KEY (usuario_id)
);
CREATE INDEX idx_calendario_nascimento_mes ON calendario.nascimento ((extract(month FROM data))) WHERE publico;

-- Compromissos de cada pessoa: só o dono vê
CREATE TABLE calendario.evento (
    id           int8         NOT NULL,
    dono_id      int8         NOT NULL,
    titulo       varchar(120) NOT NULL,
    descricao    varchar(2000) NULL,
    local        varchar(200) NULL,
    inicio       timestamp    NOT NULL,
    fim          timestamp    NULL,
    dia_inteiro  boolean      NOT NULL DEFAULT false,
    cor          varchar(20)  NULL,
    create_time  timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_calendario_evento PRIMARY KEY (id),
    CONSTRAINT ck_calendario_evento_fim CHECK (fim IS NULL OR fim >= inicio)
);
CREATE INDEX idx_calendario_evento_dono ON calendario.evento (dono_id, inicio);
CREATE SEQUENCE calendario.evento_seq START 1;

INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, updater, deleted) VALUES
(59500, 'Calendário', '', 2, 2, 0, '/calendario', 'ep:calendar', 'calendario/index', 'Calendario', 0, true, true, false, '1', '1', 0),
(59501, 'Usar o calendário', 'calendario:usar', 3, 1, 59500, '', '', '', NULL, 0, true, true, true, '1', '1', 0);

-- Todo servidor com login usa; o telão é uma tela fixa, não uma pessoa
INSERT INTO system_role_menu (id, role_id, menu_id, creator, updater, deleted, tenant_id)
SELECT nextval('system_role_menu_seq'), r.id, m.id, '1', '1', 0, 1
FROM system_role r
CROSS JOIN (VALUES (59500), (59501)) AS m(id)
WHERE r.deleted = 0 AND r.code <> 'telao';
