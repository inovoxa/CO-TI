-- CO-TI: tira os dados de exemplo em chinês que vieram com o RuoYi-Vue-Pro (notificações, avisos, modelos, clientes de
-- login de demonstração) e apaga de vez os registros de exemplo que só estavam marcados como excluídos.

-- notificações e avisos de exemplo ("我是 芋艿，我开始 写代码 了")
DELETE FROM system_notify_message WHERE template_content ~ '[一-龥]' OR template_nickname ~ '[一-龥]';
DELETE FROM system_notice WHERE title ~ '[一-龥]' OR content ~ '[一-龥]';

-- modelos de e-mail e SMS de exemplo; o CO-TI manda e-mail pelo Service Desk e não usa SMS
DELETE FROM system_mail_template WHERE name ~ '[一-龥]' OR title ~ '[一-龥]' OR deleted = 1;
DELETE FROM system_sms_template;
DELETE FROM system_sms_channel;
DELETE FROM system_social_client;

-- cliente OAuth2 do próprio portal: nome e sem o logotipo que vinha do servidor de testes do RuoYi
UPDATE system_oauth2_client
   SET name = 'CO-TI', description = 'Portal do CO-TI (login de usuários)', logo = ''
 WHERE client_id = 'default';
DELETE FROM system_oauth2_client WHERE client_id <> 'default';

-- descrições de configuração e de dicionário
UPDATE infra_config SET remark = 'Senha inicial dos usuários criados pelo administrador'
 WHERE config_key = 'system.user.init-password';
UPDATE infra_config SET category = 'biz', remark = 'Permite que visitantes criem conta pela tela de login'
 WHERE config_key = 'system.user.register-enabled';
UPDATE system_dict_type SET remark = NULL WHERE remark ~ '[一-龥]';
UPDATE system_dict_data SET remark = NULL WHERE remark ~ '[一-龥]';

-- registros de exemplo já excluídos (usuários, setores, perfis, cargos, empresas, armazenamentos)
DELETE FROM system_user_role WHERE user_id IN (SELECT id FROM system_users WHERE deleted = 1 AND (nickname ~ '[一-龥]' OR id < 1000));
DELETE FROM system_user_post WHERE user_id IN (SELECT id FROM system_users WHERE deleted = 1 AND (nickname ~ '[一-龥]' OR id < 1000));
DELETE FROM system_users WHERE deleted = 1 AND (nickname ~ '[一-龥]' OR remark ~ '[一-龥]' OR id < 1000);
DELETE FROM system_dept WHERE deleted = 1 AND name ~ '[一-龥]';
DELETE FROM system_role_menu WHERE role_id IN (SELECT id FROM system_role WHERE deleted = 1 AND (name ~ '[一-龥]' OR remark ~ '[一-龥]'));
DELETE FROM system_role WHERE deleted = 1 AND (name ~ '[一-龥]' OR remark ~ '[一-龥]');
DELETE FROM system_user_post WHERE post_id IN (SELECT id FROM system_post WHERE name ~ '[一-龥]');
DELETE FROM system_post WHERE name ~ '[一-龥]';
DELETE FROM system_tenant WHERE deleted = 1;
UPDATE system_tenant SET contact_name = 'Administrador', contact_mobile = NULL WHERE contact_name ~ '[一-龥]';
DELETE FROM system_tenant_package WHERE name ~ '[一-龥]' OR remark ~ '[一-龥]';
DELETE FROM infra_file_config WHERE deleted = 1;

-- tabelas das telas de exemplo do gerador de código (telas e código removidos)
DROP TABLE IF EXISTS yudao_demo01_contact;
DROP TABLE IF EXISTS yudao_demo02_category;
DROP TABLE IF EXISTS yudao_demo03_course;
DROP TABLE IF EXISTS yudao_demo03_grade;
DROP TABLE IF EXISTS yudao_demo03_student;
