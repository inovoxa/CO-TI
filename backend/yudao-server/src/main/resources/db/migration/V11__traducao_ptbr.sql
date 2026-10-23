-- CO-TI: menus e dicionários do núcleo (Sistema e Infraestrutura) em português.
-- Os textos das telas e mensagens ficam no código (ferramentas/traducao); aqui só o que está no banco.

-- ========== Menus ==========
UPDATE system_menu AS m SET name = t.nome FROM (VALUES
(1, 'Sistema'), (2, 'Infraestrutura'),
(4, 'Usuários'), (5, 'Perfis'), (6, 'Menus'), (7, 'Departamentos'), (8, 'Cargos'), (9, 'Dicionários'),
(10, 'Configurações'), (11, 'Avisos'), (12, 'Auditoria do sistema'), (14, 'Tarefas agendadas'),
(16, 'Monitor da JVM'), (17, 'Monitor do Redis'), (19, 'Gerador de código'),
(21, 'Log de operações'), (22, 'Log de acesso'),
(23, 'Consultar usuário'), (24, 'Criar usuário'), (25, 'Editar usuário'), (26, 'Excluir usuário'),
(27, 'Exportar usuários'), (28, 'Importar usuários'), (29, 'Redefinir senha'),
(30, 'Consultar perfil'), (31, 'Criar perfil'), (32, 'Editar perfil'), (33, 'Excluir perfil'), (34, 'Exportar perfis'),
(35, 'Consultar menu'), (36, 'Criar menu'), (37, 'Editar menu'), (38, 'Excluir menu'),
(39, 'Consultar departamento'), (40, 'Criar departamento'), (41, 'Editar departamento'), (42, 'Excluir departamento'),
(43, 'Consultar cargo'), (44, 'Criar cargo'), (45, 'Editar cargo'), (46, 'Excluir cargo'), (47, 'Exportar cargos'),
(48, 'Consultar dicionário'), (49, 'Criar dicionário'), (50, 'Editar dicionário'), (51, 'Excluir dicionário'),
(52, 'Exportar dicionários'),
(53, 'Consultar configuração'), (54, 'Criar configuração'), (55, 'Editar configuração'), (56, 'Excluir configuração'),
(57, 'Exportar configurações'),
(58, 'Consultar aviso'), (59, 'Criar aviso'), (60, 'Editar aviso'), (61, 'Excluir aviso'),
(62, 'Consultar operações'), (63, 'Exportar log'), (64, 'Consultar acessos'), (65, 'Exportar log'),
(68, 'Criar tarefa'), (69, 'Editar tarefa'), (70, 'Excluir tarefa'), (71, 'Ativar/pausar tarefa'),
(72, 'Exportar tarefas'), (84, 'Executar tarefa'), (92, 'Consultar tarefa'),
(73, 'Editar geração'), (74, 'Excluir geração'), (75, 'Importar tabela'), (76, 'Prévia do código'), (77, 'Gerar código'),
(78, 'Definir menus do perfil'), (79, 'Definir dados do perfil'), (80, 'Definir perfis do usuário'),
(81, 'Ver informações do Redis'), (82, 'Ver chaves do Redis'),
(86, 'Log de requisições'), (87, 'Exportar log'), (88, 'Logs de API'), (89, 'Log de erros'),
(90, 'Tratar erro'), (91, 'Exportar log'), (93, 'Consultar log'), (94, 'Consultar log'),
(95, 'Arquivos'), (96, 'Consultar arquivo'), (97, 'Excluir arquivo'),
(187, 'Armazenamento'), (188, 'Consultar armazenamento'), (189, 'Criar armazenamento'),
(190, 'Editar armazenamento'), (191, 'Excluir armazenamento'), (192, 'Exportar armazenamento'),
(193, 'Arquivos'),
(318, 'E-mail'), (319, 'Contas de e-mail'), (320, 'Consultar conta'), (321, 'Criar conta'), (322, 'Editar conta'),
(323, 'Excluir conta'), (324, 'Modelos de e-mail'), (325, 'Consultar modelo'), (326, 'Criar modelo'),
(327, 'Editar modelo'), (328, 'Excluir modelo'), (329, 'E-mails enviados'), (330, 'Consultar envios'),
(331, 'Enviar e-mail de teste'),
(332, 'Mensagens internas'), (333, 'Modelos de mensagem'), (334, 'Consultar modelo'), (335, 'Criar modelo'),
(336, 'Editar modelo'), (337, 'Excluir modelo'), (338, 'Enviar mensagem de teste'), (339, 'Mensagens enviadas'),
(340, 'Consultar mensagens'),
(565, 'Teste de WebSocket'), (772, 'Comunicação'), (773, 'Monitoramento da plataforma')
) AS t(id, nome) WHERE m.id = t.id;

-- Esconde (status = 1, reversível) o que não faz parte do CO-TI ou não deve ficar exposto em produção
UPDATE system_menu SET status = 1 WHERE id IN (
    13,   -- tokens OAuth2
    15,   -- monitor do Druid (desligado no perfil coti)
    18,   -- construtor de formulários
    20,   -- Swagger
    83,   -- demos de geração de código
    85,   -- SkyWalking
    98,   -- SMS (provedores chineses)
    181,  -- multi-empresa (o CO-TI é uma instalação por empresa)
    195,  -- fontes de dados extras
    201,  -- servidor OAuth2
    271,  -- regiões da China
    529   -- login social
);

-- ========== Dicionários (tipos) ==========
UPDATE system_dict_type AS d SET name = t.nome FROM (VALUES
('common_status', 'Situação'), ('date_interval', 'Intervalo de datas'),
('infra_api_error_log_process_status', 'Tratamento de erro de API'), ('infra_boolean_string', 'Sim/Não'),
('infra_codegen_front_type', 'Frontend do gerador'), ('infra_codegen_scene', 'Destino do gerador'),
('infra_codegen_template_type', 'Modelo do gerador'), ('infra_config_type', 'Tipo de configuração'),
('infra_file_storage', 'Armazenamento de arquivos'), ('infra_job_log_status', 'Resultado da tarefa'),
('infra_job_status', 'Situação da tarefa'), ('infra_operate_type', 'Tipo de operação'),
('system_data_scope', 'Alcance de dados'), ('system_login_result', 'Resultado do login'),
('system_login_type', 'Tipo de acesso'), ('system_mail_send_status', 'Situação do e-mail'),
('system_menu_type', 'Tipo de menu'), ('system_notice_type', 'Tipo de aviso'),
('system_notify_template_type', 'Tipo de mensagem'), ('system_oauth2_grant_type', 'Concessão OAuth2'),
('system_role_type', 'Tipo de perfil'), ('system_sms_channel_code', 'Canal de SMS'),
('system_sms_receive_status', 'Recebimento do SMS'), ('system_sms_send_status', 'Envio do SMS'),
('system_sms_template_type', 'Tipo de SMS'), ('system_social_type', 'Rede social'),
('system_user_sex', 'Sexo'), ('user_type', 'Tipo de usuário')
) AS t(tipo, nome) WHERE d.type = t.tipo;

-- ========== Dicionários (itens): por id, para não depender do texto original ==========
UPDATE system_dict_data AS d SET label = t.rotulo, remark = '' FROM (VALUES
(16, 'Ativo'), (17, 'Inativo'),
(353, 'Dia'), (354, 'Semana'), (355, 'Mês'), (356, 'Trimestre'), (357, 'Ano'),
(44, 'Pendente'), (45, 'Resolvido'), (46, 'Ignorado'),
(35, 'Não'), (34, 'Sim'),
(184, 'Vue2 Element UI'), (185, 'Vue3 Element Plus'), (186, 'Vben2 Ant Design (schema)'),
(596, 'Vben5 Ant Design (schema)'), (622, 'Vben5 Ant Design'), (628, 'Vben5 Antdv Next (schema)'),
(629, 'Vben5 Antdv Next'), (623, 'Vben5 Element Plus (schema)'), (624, 'Vben5 Element Plus'),
(627, 'Uniapp (celular)'),
(103, 'Portal'), (104, 'App do usuário'),
(36, 'Tabela simples'), (37, 'Árvore'), (283, 'Mestre (padrão)'), (284, 'Mestre (estilo ERP)'),
(285, 'Mestre (detalhe embutido)'), (282, 'Detalhe'),
(5, 'Do sistema'), (6, 'Personalizada'),
(105, 'Banco de dados'), (106, 'Disco local'), (107, 'Servidor FTP'), (108, 'Servidor SFTP'), (109, 'S3 / MinIO'),
(39, 'Executando'), (40, 'Sucesso'), (41, 'Falha'),
(38, 'Iniciando'), (3, 'Ativa'), (4, 'Pausada'),
(9, 'Outra'), (10, 'Consulta'), (11, 'Criação'), (12, 'Edição'), (13, 'Exclusão'), (14, 'Exportação'),
(15, 'Importação'),
(23, 'Todos os dados'), (24, 'Departamentos escolhidos'), (25, 'Só o próprio departamento'),
(26, 'Próprio departamento e abaixo'), (27, 'Só os próprios dados'),
(28, 'Sucesso'), (29, 'Usuário ou senha incorretos'), (30, 'Usuário desativado'),
(31, 'Verificação ausente'), (32, 'Verificação incorreta'), (33, 'Erro inesperado'),
(59, 'Usuário e senha'), (60, 'Login externo'), (110, 'SMS'), (61, 'Saída'), (62, 'Saída forçada'),
(177, 'Na fila'), (178, 'Enviado'), (179, 'Falhou'), (180, 'Não enviado'),
(18, 'Diretório'), (19, 'Menu'), (20, 'Botão'),
(7, 'Comunicado'), (8, 'Aviso'),
(181, 'Comunicado'), (182, 'Mensagem do sistema'),
(21, 'Do sistema'), (22, 'Personalizado'),
(58, 'Teste (DingTalk)'), (47, 'Aliyun'), (407, 'Tencent Cloud'), (408, 'Huawei Cloud'), (412, 'Qiniu'),
(55, 'Aguardando'), (56, 'Recebido'), (57, 'Não recebido'),
(51, 'Na fila'), (52, 'Enviado'), (53, 'Falhou'), (54, 'Não enviado'),
(50, 'Marketing'), (48, 'Código de verificação'), (49, 'Notificação'),
(274, 'Gitee'), (275, 'DingTalk'), (276, 'WeCom'), (277, 'WeChat (conta oficial)'), (278, 'WeChat (plataforma)'),
(279, 'WeChat (mini app)'), (626, 'Alipay (mini app)'),
(1, 'Masculino'), (2, 'Feminino'),
(42, 'Usuário do app'), (43, 'Usuário do portal')
) AS t(id, rotulo) WHERE d.id = t.id;
