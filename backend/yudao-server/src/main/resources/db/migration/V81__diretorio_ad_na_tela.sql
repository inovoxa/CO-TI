-- Diretório LDAP (AD) configurado na tela da Infraestrutura, como no GLPI. Sem linha aqui, valem as variáveis CO_TI_AD_*.
CREATE TABLE identidade.ad_config (
    id                 int2          NOT NULL DEFAULT 1,
    habilitado         boolean       NOT NULL DEFAULT false,
    servidores         varchar(500)  NULL,               -- nome ou IP; vários separados por vírgula (o próximo assume se um cair)
    porta              int4          NULL,
    seguranca          varchar(10)   NOT NULL DEFAULT 'ldaps',   -- ldaps | starttls
    certificado_ca     text          NULL,               -- PEM da CA do AD
    bind_dn            varchar(500)  NULL,               -- conta de serviço (RootDN), DN ou usuario@dominio
    bind_senha         text          NULL,               -- cifrada com a chave-mestra (AES-GCM)
    base_usuarios      varchar(500)  NULL,
    filtro             varchar(2000) NULL,               -- condições a mais, somadas a "é uma pessoa"
    ou_raiz            varchar(500)  NULL,
    espelhar_ous       boolean       NOT NULL DEFAULT true,
    dept_raiz_id       int8          NULL,
    perfil_padrao      varchar(64)   NULL,
    grupo_aprovadores  varchar(2000) NULL,
    grupos_perfis      text          NULL,               -- JSON {"grupo do AD": "código do perfil"}
    desativar_ausentes boolean       NOT NULL DEFAULT true,
    updater            varchar(64)   NULL,
    update_time        timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_identidade_ad_config PRIMARY KEY (id),
    CONSTRAINT ck_identidade_ad_config_uma_linha CHECK (id = 1),
    CONSTRAINT ck_identidade_ad_config_seguranca CHECK (seguranca IN ('ldaps', 'starttls'))
);

INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, updater, deleted) VALUES
(59430, 'Diretório LDAP (AD)', '', 2, 6, 2, 'diretorio-ad', 'ep:user', 'infra/diretorioAd/index', 'InfraDiretorioAd', 0, true, true, false, '1', '1', 0),
(59431, 'Ver diretório', 'infra:ad:query', 3, 1, 59430, '', '', '', NULL, 0, true, true, true, '1', '1', 0),
(59432, 'Configurar diretório', 'infra:ad:update', 3, 2, 59430, '', '', '', NULL, 0, true, true, true, '1', '1', 0);
