-- Texto abaixo do "Bem-vindo" no login (uma frase por linha), alterável em Sistema > Identidade visual (vazio: o padrão)
INSERT INTO infra_config (id, category, type, name, config_key, value, visible, remark, creator, updater, deleted)
SELECT nextval('infra_config_seq'), 'coti', 1, 'Texto da tela de login', 'coti.marca.frase', '', true, 'Alterado em Sistema > Identidade visual', '1', '1', 0
WHERE NOT EXISTS (SELECT 1 FROM infra_config WHERE config_key = 'coti.marca.frase' AND deleted = 0);

UPDATE system_menu SET name = 'Alterar nome, logo, foto e texto' WHERE id = 57001;
