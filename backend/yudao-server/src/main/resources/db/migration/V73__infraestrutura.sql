-- Infraestrutura refeita: compressão das batidas, retenção editável, capacidade, rotinas e o novo menu.

-- ---------------------------------------------------------------------- escala
-- Batidas: ~113 mil linhas por dia. Comprimidas por monitor, as consultas por monitor seguem rápidas.
ALTER TABLE monitor.batida SET (timescaledb.compress, timescaledb.compress_segmentby = 'monitor_id',
                                timescaledb.compress_orderby = 'momento DESC');
SELECT add_compression_policy('monitor.batida', interval '2 days');

-- Sem estes índices, cada lote da limpeza varreria a tabela inteira.
CREATE INDEX idx_system_notify_message_limpeza ON system_notify_message (create_time);
CREATE INDEX idx_itsm_email_saida_limpeza ON itsm.email_saida (criado_em) WHERE status = 'enviado';
CREATE INDEX idx_itsm_sla_aviso_limpeza ON itsm.sla_aviso (criado_em);

-- ---------------------------------------------------------------------- tabelas
-- Só guarda o que foi alterado na tela; sem linha, vale a variável de ambiente.
CREATE TABLE infra_retencao (
    chave       varchar(40) NOT NULL,
    valor       int4        NOT NULL,
    updater     varchar(64) NULL,
    update_time timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_infra_retencao PRIMARY KEY (chave)
);

-- Uma linha por área por dia: é a base do crescimento e da previsão.
CREATE TABLE infra_capacidade_dia (
    dia    date        NOT NULL,
    area   varchar(30) NOT NULL,
    bytes  int8        NOT NULL,
    linhas int8        NOT NULL DEFAULT 0,
    CONSTRAINT pk_infra_capacidade_dia PRIMARY KEY (dia, area)
);

-- Uma linha por rotina, atualizada no lugar: não vira mais um log que cresce.
CREATE TABLE infra_rotina (
    nome            varchar(150) NOT NULL,
    ultima_execucao timestamp    NULL,
    duracao_ms      int8         NOT NULL DEFAULT 0,
    status          varchar(10)  NOT NULL DEFAULT 'ok',   -- ok | erro
    ultimo_erro     varchar(500) NULL,
    execucoes       int8         NOT NULL DEFAULT 0,
    falhas          int8         NOT NULL DEFAULT 0,
    ultima_falha    timestamp    NULL,
    CONSTRAINT pk_infra_rotina PRIMARY KEY (nome)
);

INSERT INTO system_notify_template (id, name, code, nickname, content, type, params, status, remark, creator, updater, deleted) VALUES
(9014, 'Aviso de infraestrutura', 'coti_infra_aviso', 'Infraestrutura', '{texto}', 2, '["texto","link"]', 0, 'Disco e limite de logs; vai para os administradores', '1', '1', 0);

-- ---------------------------------------------------------------------- menu
-- Saem as ferramentas de desenvolvimento e as telas que não funcionam no CO-TI (a da JVM abre em quadro uma página que exige
-- login e responde 401). O código continua no projeto.
WITH RECURSIVE sai AS (
    SELECT id FROM system_menu WHERE id IN (14, 16, 19, 565, 86)
    UNION ALL
    SELECT m.id FROM system_menu m JOIN sai ON m.parent_id = sai.id
), marcados AS (
    UPDATE system_menu SET deleted = 1, updater = '1', update_time = now() WHERE id IN (SELECT id FROM sai) RETURNING id
)
UPDATE system_role_menu SET deleted = 1, updater = '1', update_time = now() WHERE menu_id IN (SELECT id FROM marcados);

-- As tarefas do Quartz nunca rodam aqui (o Quartz não é ligado); as limpezas reais são do agendador do Spring.
UPDATE infra_job SET deleted = 1, updater = '1', update_time = now() WHERE deleted = 0;

-- Log de erros e Redis sobem um nível e as pastas vazias somem.
UPDATE system_menu SET parent_id = 2, name = 'Erros do sistema', sort = 3 WHERE id = 89;
UPDATE system_menu SET parent_id = 2, sort = 8 WHERE id = 17;

UPDATE system_menu SET deleted = 1, updater = '1', update_time = now() WHERE id IN (88, 773);
UPDATE system_role_menu SET deleted = 1, updater = '1', update_time = now() WHERE menu_id IN (88, 773);
UPDATE system_menu SET sort = 6 WHERE id = 193;
UPDATE system_menu SET sort = 7 WHERE id = 10;

-- Formulários vão para a Infraestrutura. Técnico, NOC e Service Desk só viam a tela, sem editar: perdem o item de menu,
-- mas mantêm a permissão de consulta usada por outras telas.
UPDATE system_menu SET parent_id = 2, sort = 4 WHERE id = 51080;
UPDATE system_role_menu SET deleted = 1, updater = '1', update_time = now()
WHERE menu_id = 51080 AND role_id IN (SELECT id FROM system_role WHERE code IN ('tecnico', 'noc', 'service_desk'));

INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, updater, deleted) VALUES
(59400, 'Visão geral', '', 2, 1, 2, 'geral', 'ep:data-analysis', 'infra/geral/index', 'InfraGeral', 0, true, true, false, '1', '1', 0),
(59401, 'Ver capacidade', 'infra:capacidade:query', 3, 1, 59400, '', '', '', NULL, 0, true, true, true, '1', '1', 0),
(59402, 'Alterar retenção', 'infra:retencao:update', 3, 2, 59400, '', '', '', NULL, 0, true, true, true, '1', '1', 0),
(59410, 'Rotinas', '', 2, 2, 2, 'rotinas', 'ep:timer', 'infra/rotinas/index', 'InfraRotinas', 0, true, true, false, '1', '1', 0),
(59411, 'Ver rotinas', 'infra:rotina:query', 3, 1, 59410, '', '', '', NULL, 0, true, true, true, '1', '1', 0);
