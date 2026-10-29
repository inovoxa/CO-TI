-- ========== Logs centralizados (no lugar do Graylog) ==========
-- O CO-TI recebe Syslog (UDP/TCP) e GELF (UDP), separa os campos (FortiGate, eventos do Windows) e guarda aqui.
-- Ver docs/specs/logs.md.

-- Cada mensagem recebida. Hypertable do TimescaleDB: um pedaço por dia, comprimido depois de 2 dias e apagado
-- depois da retenção (coti.logs.retencao-dias).
CREATE TABLE logs.mensagem (
    momento    timestamp     NOT NULL,              -- quando aconteceu (o horário da própria mensagem)
    recebido   timestamp     NOT NULL,              -- quando chegou ao CO-TI
    fluxo      varchar(20)   NOT NULL,              -- firewall | contas | maquinas | arquivos | geral
    origem     varchar(255)  NOT NULL,              -- quem gerou (devname do FortiGate, computador do Windows, host)
    ip         varchar(45)   NULL,                  -- endereço de onde a mensagem chegou
    nivel      int2          NOT NULL DEFAULT 6,    -- severidade do Syslog: 0 emergência ... 7 depuração
    aplicacao  varchar(100)  NULL,                  -- programa (tag do Syslog, canal do Windows)
    mensagem   text          NOT NULL,
    campos     jsonb         NOT NULL DEFAULT '{}'  -- campos separados (chave=valor do FortiGate, dados do evento)
);
SELECT create_hypertable('logs.mensagem', 'momento', chunk_time_interval => interval '1 day');
CREATE INDEX idx_logs_mensagem_fluxo ON logs.mensagem (fluxo, momento DESC);
CREATE INDEX idx_logs_mensagem_origem ON logs.mensagem (origem, momento DESC);
CREATE INDEX idx_logs_mensagem_campos ON logs.mensagem USING gin (campos jsonb_path_ops);

-- compressão: o firewall gera milhões de linhas por dia; comprimidas ocupam cerca de um décimo
ALTER TABLE logs.mensagem SET (timescaledb.compress, timescaledb.compress_segmentby = 'fluxo',
                               timescaledb.compress_orderby = 'momento DESC');
SELECT add_compression_policy('logs.mensagem', interval '2 days');

-- Retenção das hypertables pela aplicação: drop_chunks exige ser dono da tabela, e a aplicação conecta como
-- coti_app (só DML). Esta função roda como dono e só aceita as tabelas da lista.
CREATE OR REPLACE FUNCTION public.coti_apagar_pedacos(tabela text, limite timestamp) RETURNS int
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp AS $$
DECLARE n int;
BEGIN
    IF tabela NOT IN ('logs.mensagem', 'monitor.batida') THEN
        RAISE EXCEPTION 'tabela não permitida: %', tabela;
    END IF;
    SELECT count(*) INTO n FROM drop_chunks(tabela::regclass, older_than => limite);
    RETURN n;
END $$;
REVOKE ALL ON FUNCTION public.coti_apagar_pedacos(text, timestamp) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.coti_apagar_pedacos(text, timestamp) TO coti_app;

-- Painéis (abas) no estilo do Graylog: cada um guarda os quadros como JSON
-- (contador, mensagens, ranking, histograma), editáveis pela tela.
CREATE TABLE logs.painel (
    id           int8          NOT NULL,
    nome         varchar(60)   NOT NULL,
    ordem        int4          NOT NULL DEFAULT 0,
    widgets      jsonb         NOT NULL DEFAULT '[]',
    creator      varchar(64)   NULL DEFAULT '',
    create_time  timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater      varchar(64)   NULL DEFAULT '',
    update_time  timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted      int2          NOT NULL DEFAULT 0,
    CONSTRAINT pk_logs_painel PRIMARY KEY (id)
);
CREATE SEQUENCE logs.painel_seq START 100;

INSERT INTO logs.painel (id, nome, ordem, widgets) VALUES
(1, 'Contas', 1, '[
  {"id":"c1","tipo":"contador","titulo":"Contas logadas","fluxo":"contas","consulta":"event_id:4624","distinto":"usuario","periodo":"1d","largura":1},
  {"id":"c2","tipo":"contador","titulo":"Logon anônimo","fluxo":"contas","consulta":"event_id:4624 AND usuario:\"ANONYMOUS LOGON\"","periodo":"1d","largura":1},
  {"id":"c3","tipo":"contador","titulo":"Contas bloqueadas","fluxo":"contas","consulta":"event_id:4740","periodo":"1d","largura":1},
  {"id":"c4","tipo":"contador","titulo":"Falhas de logon","fluxo":"contas","consulta":"event_id:4625","periodo":"1d","largura":1},
  {"id":"c5","tipo":"contador","titulo":"Contas criadas","fluxo":"contas","consulta":"event_id:4720","periodo":"1d","largura":1},
  {"id":"c6","tipo":"contador","titulo":"Contas modificadas","fluxo":"contas","consulta":"event_id:4738","periodo":"1d","largura":1},
  {"id":"c7","tipo":"contador","titulo":"Contas excluídas","fluxo":"contas","consulta":"event_id:4726","periodo":"1d","largura":1},
  {"id":"c8","tipo":"contador","titulo":"Senhas alteradas","fluxo":"contas","consulta":"event_id:4723 OR event_id:4724","periodo":"1d","largura":1},
  {"id":"c9","tipo":"contador","titulo":"Grupos alterados","fluxo":"contas","consulta":"event_id:4728 OR event_id:4729 OR event_id:4732 OR event_id:4733 OR event_id:4756 OR event_id:4757","periodo":"1d","largura":1},
  {"id":"c10","tipo":"ranking","titulo":"Falhas de logon por conta","fluxo":"contas","consulta":"event_id:4625","campo":"usuario","periodo":"1d","limite":10,"largura":2},
  {"id":"c11","tipo":"ranking","titulo":"Falhas de logon por computador","fluxo":"contas","consulta":"event_id:4625","campo":"computador","periodo":"1d","limite":10,"largura":1},
  {"id":"c12","tipo":"histograma","titulo":"Logons e falhas ao longo do dia","fluxo":"contas","consulta":"event_id:4624 OR event_id:4625","periodo":"1d","largura":4},
  {"id":"c13","tipo":"mensagens","titulo":"Bloqueios e alterações de conta","fluxo":"contas","consulta":"event_id:4740 OR event_id:4720 OR event_id:4726 OR event_id:4738","colunas":["acao","usuario","computador"],"periodo":"7d","largura":4}
]'),
(2, 'Máquinas', 2, '[
  {"id":"m1","tipo":"contador","titulo":"Erros","fluxo":"maquinas","consulta":"nivel:<=3","periodo":"1d","largura":1},
  {"id":"m2","tipo":"contador","titulo":"Avisos","fluxo":"maquinas","consulta":"nivel:4","periodo":"1d","largura":1},
  {"id":"m3","tipo":"contador","titulo":"Reinicializações","fluxo":"maquinas","consulta":"event_id:1074 OR event_id:6008","periodo":"7d","largura":1},
  {"id":"m4","tipo":"contador","titulo":"Máquinas enviando","fluxo":"maquinas","consulta":"","distinto":"origem","periodo":"1d","largura":1},
  {"id":"m5","tipo":"ranking","titulo":"Máquinas com mais erros","fluxo":"maquinas","consulta":"nivel:<=3","campo":"origem","periodo":"1d","limite":10,"largura":2},
  {"id":"m6","tipo":"ranking","titulo":"Programas com mais erros","fluxo":"maquinas","consulta":"nivel:<=3","campo":"aplicacao","periodo":"1d","limite":10,"largura":2},
  {"id":"m7","tipo":"histograma","titulo":"Erros ao longo do dia","fluxo":"maquinas","consulta":"nivel:<=4","periodo":"1d","largura":4},
  {"id":"m8","tipo":"mensagens","titulo":"Erros recentes","fluxo":"maquinas","consulta":"nivel:<=3","colunas":["aplicacao","event_id"],"periodo":"1d","largura":4}
]'),
(3, 'Arquivos', 3, '[
  {"id":"a1","tipo":"contador","titulo":"Acessos a arquivos","fluxo":"arquivos","consulta":"","periodo":"1d","largura":1},
  {"id":"a2","tipo":"contador","titulo":"Arquivos apagados","fluxo":"arquivos","consulta":"acao:\"Arquivo apagado\"","periodo":"1d","largura":1},
  {"id":"a3","tipo":"contador","titulo":"Permissões alteradas","fluxo":"arquivos","consulta":"event_id:4670","periodo":"7d","largura":1},
  {"id":"a4","tipo":"contador","titulo":"Pessoas acessando","fluxo":"arquivos","consulta":"","distinto":"usuario","periodo":"1d","largura":1},
  {"id":"a5","tipo":"ranking","titulo":"Quem mais apagou","fluxo":"arquivos","consulta":"acao:\"Arquivo apagado\"","campo":"usuario","periodo":"7d","limite":10,"largura":2},
  {"id":"a6","tipo":"ranking","titulo":"Pastas compartilhadas mais usadas","fluxo":"arquivos","consulta":"","campo":"compartilhamento","periodo":"1d","limite":10,"largura":2},
  {"id":"a7","tipo":"mensagens","titulo":"Arquivos apagados","fluxo":"arquivos","consulta":"acao:\"Arquivo apagado\"","colunas":["usuario","objeto"],"periodo":"7d","largura":4}
]'),
(4, 'Firewall', 4, '[
  {"id":"f1","tipo":"contador","titulo":"Acessos bloqueados","fluxo":"firewall","consulta":"action:blocked","periodo":"1d","largura":1},
  {"id":"f2","tipo":"contador","titulo":"Pornografia","fluxo":"firewall","consulta":"catdesc:Pornography","periodo":"1d","largura":1},
  {"id":"f3","tipo":"contador","titulo":"Vírus e ataques","fluxo":"firewall","consulta":"subtype:virus OR subtype:ips","periodo":"1d","largura":1},
  {"id":"f4","tipo":"contador","titulo":"IPs internos","fluxo":"firewall","consulta":"","distinto":"srcip","periodo":"1d","largura":1},
  {"id":"f5","tipo":"mensagens","titulo":"Mensagens do filtro web","fluxo":"firewall","consulta":"subtype:webfilter","colunas":["hostname","srcip","action"],"periodo":"1d","largura":4},
  {"id":"f6","tipo":"ranking","titulo":"Ranking de sites","fluxo":"firewall","consulta":"subtype:webfilter","campo":"hostname","periodo":"1d","limite":15,"largura":2},
  {"id":"f7","tipo":"ranking","titulo":"Categorias bloqueadas","fluxo":"firewall","consulta":"action:blocked","campo":"catdesc","periodo":"1d","limite":10,"largura":1},
  {"id":"f8","tipo":"ranking","titulo":"Pornografia - sites","fluxo":"firewall","consulta":"catdesc:Pornography","campo":"hostname","periodo":"1d","limite":10,"largura":1},
  {"id":"f9","tipo":"ranking","titulo":"Quem mais navegou","fluxo":"firewall","consulta":"subtype:webfilter","campo":"srcip","periodo":"1d","limite":10,"largura":2},
  {"id":"f10","tipo":"histograma","titulo":"Tráfego ao longo do dia","fluxo":"firewall","consulta":"","periodo":"1d","largura":2}
]');

-- ========== Menus e permissões ==========
-- a tela de logs simulada do Centro de Operações (escondida desde a V41) dá lugar a esta
DELETE FROM system_role_menu WHERE menu_id IN (50060, 50061);
DELETE FROM system_menu WHERE id IN (50060, 50061);

INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, updater, deleted) VALUES
(56000, 'Logs',                  '', 1, 4, 0, '/logs', 'ep:document', NULL, NULL, 0, true, true, true, '1', '1', 0),
(56010, 'Painéis',               '', 2, 1, 56000, 'paineis', 'ep:data-board', 'logs/paineis/index', 'LogsPaineis', 0, true, true, true, '1', '1', 0),
(56011, 'Consultar logs',        'logs:consultar', 3, 1, 56010, '', '', '', NULL, 0, true, true, true, '1', '1', 0),
(56012, 'Editar painéis',        'logs:painel:editar', 3, 2, 56010, '', '', '', NULL, 0, true, true, true, '1', '1', 0),
(56020, 'Busca',                 '', 2, 2, 56000, 'busca', 'ep:search', 'logs/busca/index', 'LogsBusca', 0, true, true, true, '1', '1', 0),
(56030, 'Fontes',                '', 2, 3, 56000, 'fontes', 'ep:connection', 'logs/fontes/index', 'LogsFontes', 0, true, true, true, '1', '1', 0),
(56031, 'Ver fontes e entradas', 'logs:fontes', 3, 1, 56030, '', '', '', NULL, 0, true, true, true, '1', '1', 0);

-- SOC e gestão de TI: tudo; NOC: consulta e fontes
INSERT INTO system_role_menu (id, role_id, menu_id, creator, updater, deleted, tenant_id)
SELECT nextval('system_role_menu_seq'), r, m, '1', '1', 0, 1
FROM (VALUES (50003), (50007)) AS papeis(r)
CROSS JOIN (VALUES (56000), (56010), (56011), (56012), (56020), (56030), (56031)) AS menus(m);
INSERT INTO system_role_menu (id, role_id, menu_id, creator, updater, deleted, tenant_id)
SELECT nextval('system_role_menu_seq'), 50001, m, '1', '1', 0, 1
FROM (VALUES (56000), (56010), (56011), (56020), (56030), (56031)) AS menus(m);
