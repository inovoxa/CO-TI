-- Unidades/filiais com localização no mapa (MVP do telão). Cada unidade aponta para a raiz da sua árvore em system_dept.
CREATE TABLE monitor_unit (
    id          int8          NOT NULL,
    dept_id     int8          NOT NULL,
    name        varchar(100)  NOT NULL,
    city        varchar(100)  NULL DEFAULT NULL,
    latitude    numeric(10,7) NOT NULL,
    longitude   numeric(10,7) NOT NULL,
    status      int2          NOT NULL DEFAULT 0,
    creator     varchar(64)   NULL DEFAULT '',
    create_time timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater     varchar(64)   NULL DEFAULT '',
    update_time timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     int2          NOT NULL DEFAULT 0,
    CONSTRAINT pk_monitor_unit PRIMARY KEY (id)
);
CREATE INDEX idx_monitor_unit_dept_id ON monitor_unit (dept_id);

COMMENT ON TABLE monitor_unit IS 'Unidades/filiais monitoradas no telão';
COMMENT ON COLUMN monitor_unit.dept_id IS 'Departamento raiz da unidade (system_dept.id)';
COMMENT ON COLUMN monitor_unit.status IS '0 = ativa, 1 = inativa';

CREATE SEQUENCE monitor_unit_seq START 1;
