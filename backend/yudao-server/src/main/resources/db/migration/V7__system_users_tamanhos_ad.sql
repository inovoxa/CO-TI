-- O yudao nasceu com tamanhos pensados para contas chinesas (celular de 11 dígitos, apelido de 30).
-- Nomes, e-mails e telefones vindos do AD são maiores: "Maria Aparecida dos Santos Oliveira", "+55 16 99999-9999".
ALTER TABLE system_users ALTER COLUMN nickname TYPE varchar(100);
ALTER TABLE system_users ALTER COLUMN email    TYPE varchar(128);
ALTER TABLE system_users ALTER COLUMN mobile   TYPE varchar(32);
