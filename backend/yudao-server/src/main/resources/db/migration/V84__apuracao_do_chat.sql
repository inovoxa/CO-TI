-- Apuração das conversas do chat, em Sistema → Auditoria do sistema. A permissão não vai para nenhum perfil: quem administra
-- precisa dá-la de propósito (e o backend exige que ela esteja num perfil da pessoa, mesmo para o super administrador).
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, updater, deleted) VALUES
(59210, 'Apuração do chat', '', 2, 4, 12, 'apuracao-chat', 'ep:chat-line-square', 'chat/apuracao/index', 'ChatApuracao', 0, true, true, false, '1', '1', 0),
(59211, 'Apurar conversas do chat', 'chat:apurar', 3, 1, 59210, '', '', '', NULL, 0, true, true, true, '1', '1', 0);
