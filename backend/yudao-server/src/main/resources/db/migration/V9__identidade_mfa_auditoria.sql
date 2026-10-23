-- ========== Segundo fator (TOTP) ==========
CREATE TABLE identidade.mfa_totp (
    id                  int8         NOT NULL,
    user_id             int8         NOT NULL,
    segredo_cifrado     varchar(255) NOT NULL,   -- AES-256-GCM com a chave-mestra (CO_TI_CHAVE_MESTRA)
    ativo               bool         NOT NULL DEFAULT false,
    ultimo_passo        int8         NOT NULL DEFAULT 0,   -- impede reutilizar o mesmo código
    codigos_recuperacao text         NOT NULL DEFAULT '[]', -- hashes BCrypt, cada um vale uma vez
    ativado_em          timestamp    NULL,
    creator             varchar(64)  NULL DEFAULT '',
    create_time         timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater             varchar(64)  NULL DEFAULT '',
    update_time         timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             int2         NOT NULL DEFAULT 0,
    CONSTRAINT pk_mfa_totp PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_mfa_totp_user ON identidade.mfa_totp (user_id) WHERE deleted = 0;
CREATE SEQUENCE identidade.mfa_totp_seq START 1;

-- ========== Trilha de auditoria encadeada ==========
-- Cada linha guarda o SHA-256 da anterior: apagar ou alterar uma linha quebra a cadeia dali em diante.
-- detalhes é text (não jsonb) para o hash ser calculado sobre exatamente os bytes gravados.
CREATE TABLE identidade.auditoria (
    id            int8         NOT NULL,
    momento       timestamp(3) NOT NULL,
    acao          varchar(64)  NOT NULL,
    user_id       int8         NULL,
    usuario       varchar(64)  NULL,
    ip            varchar(64)  NULL,
    detalhes      text         NOT NULL DEFAULT '{}',
    hash_anterior char(64)     NOT NULL,
    hash          char(64)     NOT NULL,
    CONSTRAINT pk_auditoria PRIMARY KEY (id)
);
CREATE INDEX idx_auditoria_momento ON identidade.auditoria (momento);
CREATE INDEX idx_auditoria_user ON identidade.auditoria (user_id);
CREATE INDEX idx_auditoria_acao ON identidade.auditoria (acao);
CREATE SEQUENCE identidade.auditoria_seq START 1;

COMMENT ON TABLE identidade.auditoria IS 'Trilha de auditoria somente-inserção, com hash encadeado';

-- Somente inserção: nem a aplicação consegue alterar ou apagar o que já foi registrado.
CREATE FUNCTION identidade.auditoria_somente_insercao() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'identidade.auditoria é somente-inserção (% bloqueado)', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_auditoria_imutavel
    BEFORE UPDATE OR DELETE ON identidade.auditoria
    FOR EACH ROW EXECUTE FUNCTION identidade.auditoria_somente_insercao();
CREATE TRIGGER trg_auditoria_sem_truncate
    BEFORE TRUNCATE ON identidade.auditoria
    FOR EACH STATEMENT EXECUTE FUNCTION identidade.auditoria_somente_insercao();

-- ========== Menus e permissões ==========
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, updater, deleted) VALUES
(50090, 'Auditoria',              '',                              2, 9, 50000, 'auditoria', 'ep:lock', 'identidade/auditoria/index', 'IdentidadeAuditoria', 0, true, true, true, '1', '1', 0),
(50091, 'Ver trilha de auditoria', 'identidade:auditoria:query',   3, 1, 50090, '', '', '', NULL, 0, true, true, true, '1', '1', 0),
(50092, 'Verificar integridade',   'identidade:auditoria:verificar', 3, 2, 50090, '', '', '', NULL, 0, true, true, true, '1', '1', 0),
(50093, 'Redefinir MFA de usuário', 'identidade:mfa:redefinir',     3, 3, 50090, '', '', '', NULL, 0, true, true, true, '1', '1', 0);

-- SOC enxerga a trilha
INSERT INTO system_role_menu (id, role_id, menu_id, creator, updater, deleted, tenant_id)
SELECT nextval('system_role_menu_seq'), 50003, m, '1', '1', 0, 1 FROM (VALUES (50090), (50091), (50092)) AS t(m);
