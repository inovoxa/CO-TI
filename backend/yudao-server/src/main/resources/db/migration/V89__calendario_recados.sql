-- Recados no calendário: aparecem num aviso ao entrar no sistema, do primeiro ao último dia do período.
-- Sem unidade é recado geral (todo mundo vê); com unidades, vê quem está nelas ou nas que ficam abaixo delas.
CREATE TABLE calendario.recado (
    id           int8          NOT NULL,
    autor_id     int8          NOT NULL,
    titulo       varchar(120)  NOT NULL,
    texto        varchar(4000) NOT NULL,
    inicio       date          NOT NULL,
    fim          date          NOT NULL,
    cor          varchar(20)   NULL,
    create_time  timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_calendario_recado PRIMARY KEY (id),
    CONSTRAINT ck_calendario_recado_fim CHECK (fim >= inicio)
);
CREATE INDEX idx_calendario_recado_periodo ON calendario.recado (inicio, fim);
CREATE SEQUENCE calendario.recado_seq START 1;

-- Para quais unidades (system_dept); nenhuma linha = recado geral
CREATE TABLE calendario.recado_unidade (
    recado_id  int8 NOT NULL,
    dept_id    int8 NOT NULL,
    CONSTRAINT pk_calendario_recado_unidade PRIMARY KEY (recado_id, dept_id),
    CONSTRAINT fk_calendario_recado_unidade FOREIGN KEY (recado_id) REFERENCES calendario.recado (id) ON DELETE CASCADE
);

-- Quem já deu "Ciente": o aviso de entrada não mostra de novo para ela
CREATE TABLE calendario.recado_ciente (
    recado_id  int8      NOT NULL,
    usuario_id int8      NOT NULL,
    ciente_em  timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_calendario_recado_ciente PRIMARY KEY (recado_id, usuario_id),
    CONSTRAINT fk_calendario_recado_ciente FOREIGN KEY (recado_id) REFERENCES calendario.recado (id) ON DELETE CASCADE
);

INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, updater, deleted) VALUES
(59502, 'Publicar recados', 'calendario:recado', 3, 2, 59500, '', '', '', NULL, 0, true, true, true, '1', '1', 0);

-- Publica: administrador e gestão de TI (dá para dar a outros perfis em Sistema → Perfis)
INSERT INTO system_role_menu (id, role_id, menu_id, creator, updater, deleted, tenant_id)
SELECT nextval('system_role_menu_seq'), r.id, 59502, '1', '1', 0, 1
FROM system_role r
WHERE r.deleted = 0 AND r.code IN ('super_admin', 'gestao_ti');
