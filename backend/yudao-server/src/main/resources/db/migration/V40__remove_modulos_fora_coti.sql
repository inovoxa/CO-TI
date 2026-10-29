-- CO-TI: remove do banco o que sobrou dos módulos do RuoYi que não fazem parte do CO-TI
-- (o código deles foi apagado do repositório). Nenhum desses módulos tem tabelas próprias aqui:
-- só menus (já escondidos), tarefas agendadas e dicionários.

-- ========== Menus: a árvore inteira de cada módulo ==========
CREATE TEMP TABLE menus_removidos ON COMMIT DROP AS
WITH RECURSIVE arvore AS (
    SELECT id FROM system_menu WHERE id IN (
        194, 347, 348,   -- links da documentação do RuoYi
        114,  -- pagamentos
        207,  -- relatórios
        148,  -- fluxos (BPM)
        373,  -- membros
        449,  -- loja
        480,  -- CRM
        597,  -- ERP
        1348, -- WMS
        959,  -- MES
        8200, -- OA
        1476, -- RH
        1894, -- financeiro
        8000, -- projetos
        791,  -- IA
        860,  -- IoT
        1418, -- chat
        272,  -- WeChat
        1637  -- CMS
    )
    UNION ALL
    SELECT m.id FROM system_menu m JOIN arvore a ON m.parent_id = a.id
)
SELECT id FROM arvore;

DELETE FROM system_role_menu WHERE menu_id IN (SELECT id FROM menus_removidos);
DELETE FROM system_menu WHERE id IN (SELECT id FROM menus_removidos);

-- ========== Tarefas agendadas cujo handler não existe mais ==========
DELETE FROM infra_job_log WHERE job_id IN (SELECT id FROM infra_job
    WHERE handler_name NOT IN ('accessLogCleanJob', 'errorLogCleanJob', 'jobLogCleanJob', 'demoJob'));
DELETE FROM infra_job
    WHERE handler_name NOT IN ('accessLogCleanJob', 'errorLogCleanJob', 'jobLogCleanJob', 'demoJob');

-- ========== Dicionários dos módulos removidos ==========
CREATE TEMP TABLE dicts_removidos ON COMMIT DROP AS
SELECT type FROM system_dict_type
WHERE split_part(type, '_', 1) IN ('ai', 'ai1', 'bpm', 'brokerage', 'cms', 'crm', 'erp', 'fms', 'hrm', 'im', 'iot',
                                   'member', 'merchant', 'mes', 'mp', 'oa', 'pay', 'pms', 'product', 'promotion',
                                   'trade', 'wms');

DELETE FROM system_dict_data WHERE dict_type IN (SELECT type FROM dicts_removidos);
DELETE FROM system_dict_type WHERE type IN (SELECT type FROM dicts_removidos);
