-- CO-TI: textos do banco que a V11 não cobriu e ainda aparecem em chinês nas telas
-- (status do BPM, tarefas agendadas e configurações). Atenção: infra_job.name tem no máximo 32 caracteres.

-- ========== Dicionários do BPM ==========
UPDATE system_dict_type AS d SET name = t.nome FROM (VALUES
('bpm_process_instance_status', 'Situação do processo'), ('bpm_task_status', 'Situação da tarefa de aprovação')
) AS t(tipo, nome) WHERE d.type = t.tipo;

UPDATE system_dict_data AS d SET label = t.rotulo FROM (VALUES
('bpm_process_instance_status', '1', 'Em aprovação'), ('bpm_process_instance_status', '2', 'Aprovado'),
('bpm_process_instance_status', '3', 'Recusado'), ('bpm_process_instance_status', '4', 'Cancelado'),
('bpm_task_status', '0', 'Aguardando aprovação'), ('bpm_task_status', '1', 'Em aprovação'),
('bpm_task_status', '2', 'Aprovado'), ('bpm_task_status', '3', 'Recusado'), ('bpm_task_status', '4', 'Cancelado'),
('bpm_task_status', '5', 'Devolvido'), ('bpm_task_status', '6', 'Delegado'), ('bpm_task_status', '7', 'Aprovando')
) AS t(tipo, valor, rotulo) WHERE d.dict_type = t.tipo AND d.value = t.valor;

-- ========== Tarefas agendadas (pelo handler, que não muda) ==========
UPDATE infra_job AS j SET name = t.nome FROM (VALUES
('payNotifyJob', 'Pagamentos: notificações'), ('payOrderSyncJob', 'Pagamentos: sincronizar pedidos'),
('payOrderExpireJob', 'Pagamentos: expirar pedidos'), ('payRefundSyncJob', 'Pagamentos: sincronizar estornos'),
('payTransferSyncJob', 'Pagamentos: sincronizar repasses'),
('tradeOrderAutoCancelJob', 'Loja: expirar pedidos'), ('tradeOrderAutoReceiveJob', 'Loja: confirmar recebimento'),
('tradeOrderAutoCommentJob', 'Loja: avaliação automática'), ('brokerageRecordUnfreezeJob', 'Loja: liberar comissões'),
('combinationRecordExpireJob', 'Loja: expirar compras em grupo'), ('couponExpireJob', 'Loja: expirar cupons'),
('productStatisticsJob', 'Loja: estatísticas de produtos'),
('accessLogCleanJob', 'Limpeza do log de acesso'), ('errorLogCleanJob', 'Limpeza do log de erros'),
('jobLogCleanJob', 'Limpeza do log de tarefas'),
('iotDeviceOfflineCheckJob', 'IoT: dispositivos offline'), ('iotOtaUpgradeJob', 'IoT: enviar atualizações OTA'),
('cmsArticleLifecycleJob', 'CMS: ciclo de vida dos artigos'), ('cmsVisitLogCleanupJob', 'CMS: limpeza do log de visitas'),
('cmsVisitAggregationJob', 'CMS: consolidar visitas'), ('cmsStaticTaskJob', 'CMS: geração estática pendente'),
('cmsStaticTaskFullGenerateJob', 'CMS: geração estática diária'), ('cmsCollectionDispatchJob', 'CMS: coletas pendentes'),
('hrmSalaryChangeJob', 'RH: aplicar reajustes salariais'), ('hrmPerformanceAppealTimeoutJob', 'RH: recursos vencidos'),
('hrmEmployeeChangeJob', 'RH: mudanças de funcionários'),
('pmsKnowledgeRecycleCleanJob', 'Projetos: esvaziar lixeira'), ('oaScheduleReminderJob', 'Agenda: lembretes')
) AS t(handler, nome) WHERE j.handler_name = t.handler;

-- ========== Configurações ==========
UPDATE infra_config AS c SET name = t.nome FROM (VALUES
('system.user.init-password', 'Usuários: senha inicial'), ('system.user.register-enabled', 'Usuários: permitir cadastro'),
('url.druid', 'Endereço do monitor do banco (Druid)'), ('url.skywalking', 'Endereço do SkyWalking'),
('url.spring-boot-admin', 'Endereço do Spring Boot Admin'), ('url.swagger', 'Endereço da documentação da API (Swagger)')
) AS t(chave, nome) WHERE c.config_key = t.chave;
