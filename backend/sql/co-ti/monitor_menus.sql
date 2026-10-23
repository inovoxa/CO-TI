-- ============================================================================================
-- Menus e permissões do Centro de Operações (tabela system_menu do RuoYi-Vue-Pro / yudao)
-- type: 1 = diretório, 2 = página (menu), 3 = botão/permissão
-- Cada página é uma ROTA própria: /operacoes/<path>. O "component" aponta para src/views/<component>.vue
-- e o "component_name" precisa ser igual ao defineOptions({ name }) da página (usado pelo cache de abas).
-- IDs a partir de 50000 para não colidir com os do sistema. Rode uma vez; para refazer, apague 50000–50099.
-- Depois: Gestão do sistema → Perfis → marque os menus para cada perfil (veja a sugestão no fim).
-- ============================================================================================

-- Se a tela antiga (system/monitorScreen) foi cadastrada antes, desative-a:
-- UPDATE system_menu SET status = 1 WHERE component = 'system/monitorScreen/index';

INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, updater, deleted) VALUES
-- diretório
(50000, 'Centro de Operações', '', 1, 5, 0, '/operacoes', 'ep:monitor', NULL, NULL, 0, b'1', b'1', b'1', '1', '1', b'0'),

-- páginas (uma rota cada)
(50010, 'Visão geral',        '', 2, 1, 50000, 'visao-geral', 'ep:data-board',     'monitor/visaoGeral/index', 'MonitorVisaoGeral', 0, b'1', b'1', b'1', '1', '1', b'0'),
(50020, 'Câmeras e acesso',   '', 2, 2, 50000, 'cameras',     'ep:video-camera',   'monitor/cameras/index',    'MonitorCameras',    0, b'1', b'1', b'1', '1', '1', b'0'),
(50030, 'Chamados',           '', 2, 3, 50000, 'chamados',    'ep:tickets',        'monitor/chamados/index',   'MonitorChamados',   0, b'1', b'1', b'1', '1', '1', b'0'),
(50040, 'Links e serviços',   '', 2, 4, 50000, 'links',       'ep:connection',     'monitor/links/index',      'MonitorLinks',      0, b'1', b'1', b'1', '1', '1', b'0'),
(50050, 'Inventário',         '', 2, 5, 50000, 'inventario',  'ep:monitor',        'monitor/inventario/index', 'MonitorInventario', 0, b'1', b'1', b'1', '1', '1', b'0'),
(50060, 'Logs',               '', 2, 6, 50000, 'logs',        'ep:document',       'monitor/logs/index',       'MonitorLogs',       0, b'1', b'1', b'1', '1', '1', b'0'),
(50070, 'Mensagens',          '', 2, 7, 50000, 'mensagens',   'ep:chat-dot-round', 'monitor/mensagens/index',  'MonitorMensagens',  0, b'1', b'1', b'1', '1', '1', b'0'),
(50080, 'Plataforma e escala','', 2, 8, 50000, 'plataforma',  'ep:coin',           'monitor/plataforma/index', 'MonitorPlataforma', 0, b'1', b'1', b'1', '1', '1', b'0'),

-- permissões (botões) — são elas que liberam as APIs do backend
(50011, 'Ver visão geral, telão e mensagens', 'system:monitor-screen:query',     3, 1, 50010, '', '', '', NULL, 0, b'1', b'1', b'1', '1', '1', b'0'),
(50021, 'Ver câmeras',                        'system:monitor-cameras:query',    3, 1, 50020, '', '', '', NULL, 0, b'1', b'1', b'1', '1', '1', b'0'),
(50031, 'Ver chamados',                       'system:monitor-chamados:query',   3, 1, 50030, '', '', '', NULL, 0, b'1', b'1', b'1', '1', '1', b'0'),
(50032, 'Abrir chamado',                      'system:monitor-chamados:create',  3, 2, 50030, '', '', '', NULL, 0, b'1', b'1', b'1', '1', '1', b'0'),
(50041, 'Ver links',                          'system:monitor-links:query',      3, 1, 50040, '', '', '', NULL, 0, b'1', b'1', b'1', '1', '1', b'0'),
(50051, 'Ver inventário',                     'system:monitor-inventario:query', 3, 1, 50050, '', '', '', NULL, 0, b'1', b'1', b'1', '1', '1', b'0'),
(50061, 'Ver logs',                           'system:monitor-logs:query',       3, 1, 50060, '', '', '', NULL, 0, b'1', b'1', b'1', '1', '1', b'0');

-- ============================================================================================
-- Sugestão de perfis (marque em Gestão do sistema → Perfis → Permissões de menu):
--   Operador do NOC    : todos os menus e permissões acima
--   Service Desk       : Visão geral, Chamados (ver + abrir), Inventário, Links
--   SOC (segurança)    : Visão geral, Logs, Câmeras, Links, Mensagens
--   Portaria           : Câmeras
--   Gestor da unidade  : Visão geral, Chamados (ver), Inventário — combine com "permissão de dados"
--                        por departamento para ele ver só a própria unidade
--   Telão (usuário da TV): system:monitor-screen:query, :links:query, :cameras:query
-- ============================================================================================
