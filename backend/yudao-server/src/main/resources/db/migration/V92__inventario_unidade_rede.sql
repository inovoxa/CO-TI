-- Inventário: unidade pela faixa de IP. A máquina fica na unidade da faixa onde o IP dela cai (a mais específica);
-- fora de qualquer faixa, vale a unidade da pessoa logada. Unidade escolhida à mão no ativo continua valendo mais.
CREATE TABLE cmdb.unidade_rede (
    id            int8          GENERATED ALWAYS AS IDENTITY,
    cidr          varchar(50)   NOT NULL,
    dept_id       int8          NOT NULL,
    observacao    varchar(255)  NULL,
    criado_em     timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_cmdb_unidade_rede PRIMARY KEY (id),
    CONSTRAINT uk_cmdb_unidade_rede_cidr UNIQUE (cidr)
);
