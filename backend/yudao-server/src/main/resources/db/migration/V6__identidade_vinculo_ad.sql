-- Vínculo entre o usuário do portal e a conta do Active Directory.
CREATE TABLE identidade.vinculo_ad (
    id                   int8         NOT NULL,
    user_id              int8         NOT NULL,
    guid                 varchar(64)  NOT NULL,
    login                varchar(64)  NOT NULL,
    dn                   varchar(512) NOT NULL,
    gestor_dn            varchar(512) NULL,
    ativo_no_ad          bool         NOT NULL DEFAULT true,
    ultima_sincronizacao timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    creator              varchar(64)  NULL DEFAULT '',
    create_time          timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater              varchar(64)  NULL DEFAULT '',
    update_time          timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted              int2         NOT NULL DEFAULT 0,
    CONSTRAINT pk_vinculo_ad PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_vinculo_ad_guid ON identidade.vinculo_ad (guid) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_vinculo_ad_user ON identidade.vinculo_ad (user_id) WHERE deleted = 0;

COMMENT ON TABLE identidade.vinculo_ad IS 'Usuário do portal ↔ conta do AD (quem tem vínculo só entra com a senha do AD)';

CREATE SEQUENCE identidade.vinculo_ad_seq START 1;
