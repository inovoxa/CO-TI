-- Foto do lado esquerdo da tela de login, alterável em Sistema > Identidade visual (vazio: a padrão do CO-TI)
INSERT INTO infra_config (id, category, type, name, config_key, value, visible, remark, creator, updater, deleted)
SELECT nextval('infra_config_seq'), 'coti', 1, 'Foto da tela de login', 'coti.marca.fundo', '', true, 'Alterado em Sistema > Identidade visual', '1', '1', 0
WHERE NOT EXISTS (SELECT 1 FROM infra_config WHERE config_key = 'coti.marca.fundo' AND deleted = 0);

UPDATE system_menu SET name = 'Alterar nome, logo e foto' WHERE id = 57001;
