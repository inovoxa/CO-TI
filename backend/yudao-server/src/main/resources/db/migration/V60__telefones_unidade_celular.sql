-- Lista de ramais ligada às unidades do monitoramento. Quando o link da unidade cai, o ramal VoIP para junto: por isso
-- entram o celular, o WhatsApp e o contato de emergência de cada unidade.

ALTER TABLE itsm.telefone
    ADD COLUMN unidade_id      bigint      NULL,      -- monitor_unit.id
    ADD COLUMN celular         varchar(30) NULL,
    ADD COLUMN whatsapp        boolean     NOT NULL DEFAULT FALSE,
    ADD COLUMN celular_publico boolean     NOT NULL DEFAULT FALSE,  -- celular corporativo, que todos podem ver
    ADD COLUMN emergencia      boolean     NOT NULL DEFAULT FALSE;  -- procurar primeiro quando a rede da unidade cai

-- O texto antigo vira vínculo quando o nome bate com uma única unidade; o que não bate fica na observação, para não se perder.
UPDATE itsm.telefone t
SET unidade_id = u.id
FROM (SELECT min(id) AS id, lower(trim(name)) AS nome FROM monitor_unit WHERE deleted = 0
      GROUP BY lower(trim(name)) HAVING count(*) = 1) u
WHERE lower(trim(t.unidade)) = u.nome;

UPDATE itsm.telefone
SET observacao = left(concat_ws(' · ', observacao, 'Unidade: ' || unidade), 255)
WHERE unidade IS NOT NULL AND unidade_id IS NULL;

ALTER TABLE itsm.telefone DROP COLUMN unidade;

ALTER TABLE itsm.telefone DROP CONSTRAINT ck_itsm_telefone_numero;
ALTER TABLE itsm.telefone ADD CONSTRAINT ck_itsm_telefone_numero
    CHECK (ramal IS NOT NULL OR telefone IS NOT NULL OR celular IS NOT NULL);
ALTER TABLE itsm.telefone ADD CONSTRAINT ck_itsm_telefone_emergencia
    CHECK (NOT emergencia OR (celular IS NOT NULL AND unidade_id IS NOT NULL));

CREATE INDEX idx_itsm_telefone_unidade ON itsm.telefone (unidade_id) WHERE deleted = 0;

-- Celular é dado pessoal: só a equipe de TI vê, salvo os marcados como corporativos (celular_publico).
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, updater, deleted) VALUES
(59003, 'Ver celulares', 'itsm:telefone:celular', 3, 3, 59000, '', '', '', NULL, 0, true, true, true, '1', '1', 0);

INSERT INTO system_role_menu (id, role_id, menu_id, creator, updater, deleted, tenant_id)
SELECT nextval('system_role_menu_seq'), r, 59003, '1', '1', 0, 1
FROM (VALUES (50001), (50002), (50003), (50007), (50008)) AS papeis(r);
