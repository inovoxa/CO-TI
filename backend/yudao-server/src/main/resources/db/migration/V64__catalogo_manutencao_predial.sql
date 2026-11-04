-- Catálogo de manutenção predial (6.1 a 6.4), com equipe própria: esses chamados não são da TI e não devem cair na fila
-- do Service Desk. Só acrescenta; um código 6.x que já exista fica como está.
INSERT INTO itsm.equipe (id, nome, descricao, creator, updater)
SELECT nextval('itsm.equipe_seq'), 'Manutenção predial', 'Recebe os pedidos do catálogo de manutenção predial', '1', '1'
WHERE NOT EXISTS (SELECT 1 FROM itsm.equipe e WHERE e.deleted = 0 AND e.nome = 'Manutenção predial');

INSERT INTO itsm.categoria (id, parent_id, nome, nome_completo, nivel, equipe_id, ordem, creator, updater)
SELECT nextval('itsm.categoria_seq'), 0, 'Manutenção predial', 'Manutenção predial', 1, e.id, 600, '1', '1'
FROM itsm.equipe e
WHERE e.deleted = 0 AND e.nome = 'Manutenção predial'
  AND NOT EXISTS (SELECT 1 FROM itsm.categoria c WHERE c.deleted = 0 AND c.parent_id = 0 AND c.nome = 'Manutenção predial');

INSERT INTO itsm.categoria (id, parent_id, nome, nome_completo, nivel, equipe_id, ordem, creator, updater)
SELECT nextval('itsm.categoria_seq'), pai.id, 'Iluminação e elétrica', 'Manutenção predial > Iluminação e elétrica', 2, pai.equipe_id, 600, '1', '1'
FROM itsm.categoria pai
WHERE pai.deleted = 0 AND pai.parent_id = 0 AND pai.nome = 'Manutenção predial'
  AND NOT EXISTS (SELECT 1 FROM itsm.categoria c WHERE c.deleted = 0 AND c.nome_completo = 'Manutenção predial > Iluminação e elétrica');

INSERT INTO itsm.formulario (id, codigo, nome, descricao, grupo, icone, categoria_id, tipo, urgencia, impacto, titulo_modelo, definicao, aprovacao, ativo, ordem, creator, updater)
SELECT nextval('itsm.formulario_seq'), '6.1', 'Iluminação e elétrica', 'Lâmpada, tomada, disjuntor ou falta de energia. Risco imediato (fogo, choque, gás ou desabamento): saia do local e acione os Bombeiros (193) antes de abrir o pedido.', 'Manutenção predial', 'ep:sunny', c.id, 2, 3, 3, 'Iluminação e elétrica: {problema} - {local}',
       '{"secoes":[{"nome":"Onde é o problema","perguntas":[{"name":"nome_solicitante","label":"Seu nome","type":"text","required":true},{"name":"local","label":"Nome do local/unidade","type":"text","required":true},{"name":"local_exato","label":"Sala, andar ou ponto de referência","type":"text","required":true},{"name":"celular_contato","label":"Celular para contato (com DDD)","type":"regex","required":true,"regex":"/^[0-9]{10,11}$/"}]},{"nome":"O que aconteceu","perguntas":[{"name":"problema","label":"O que você precisa?","type":"select","required":true,"values":["Lâmpada queimada","Tomada ou interruptor com defeito","Disjuntor desarmando","Falta de energia no local","Fio exposto ou cheiro de queimado","Outros"]},{"name":"risco","label":"Há risco para as pessoas ou o local está sem poder funcionar?","type":"radios","required":true,"values":["Não","Sim"]},{"name":"descricao_solicitacao","label":"Descreva o pedido","type":"textarea","required":true}]}]}', 'nenhuma', true, 600, '1', '1'
FROM itsm.categoria c
WHERE c.deleted = 0 AND c.nome_completo = 'Manutenção predial > Iluminação e elétrica'
  AND NOT EXISTS (SELECT 1 FROM itsm.formulario o WHERE o.deleted = 0 AND o.codigo = '6.1');

INSERT INTO itsm.categoria (id, parent_id, nome, nome_completo, nivel, equipe_id, ordem, creator, updater)
SELECT nextval('itsm.categoria_seq'), pai.id, 'Ar-condicionado', 'Manutenção predial > Ar-condicionado', 2, pai.equipe_id, 601, '1', '1'
FROM itsm.categoria pai
WHERE pai.deleted = 0 AND pai.parent_id = 0 AND pai.nome = 'Manutenção predial'
  AND NOT EXISTS (SELECT 1 FROM itsm.categoria c WHERE c.deleted = 0 AND c.nome_completo = 'Manutenção predial > Ar-condicionado');

INSERT INTO itsm.formulario (id, codigo, nome, descricao, grupo, icone, categoria_id, tipo, urgencia, impacto, titulo_modelo, definicao, aprovacao, ativo, ordem, creator, updater)
SELECT nextval('itsm.formulario_seq'), '6.2', 'Ar-condicionado', 'Defeito, limpeza ou instalação de ar-condicionado. Risco imediato (fogo, choque, gás ou desabamento): saia do local e acione os Bombeiros (193) antes de abrir o pedido.', 'Manutenção predial', 'ep:wind-power', c.id, 2, 3, 3, 'Ar-condicionado: {problema} - {local}',
       '{"secoes":[{"nome":"Onde é o problema","perguntas":[{"name":"nome_solicitante","label":"Seu nome","type":"text","required":true},{"name":"local","label":"Nome do local/unidade","type":"text","required":true},{"name":"local_exato","label":"Sala, andar ou ponto de referência","type":"text","required":true},{"name":"celular_contato","label":"Celular para contato (com DDD)","type":"regex","required":true,"regex":"/^[0-9]{10,11}$/"}]},{"nome":"O que aconteceu","perguntas":[{"name":"problema","label":"O que você precisa?","type":"select","required":true,"values":["Não liga","Não gela","Vazando água","Barulho anormal","Limpeza ou manutenção preventiva","Instalação ou remoção","Outros"]},{"name":"risco","label":"Há risco para as pessoas ou o local está sem poder funcionar?","type":"radios","required":true,"values":["Não","Sim"]},{"name":"descricao_solicitacao","label":"Descreva o pedido","type":"textarea","required":true}]}]}', 'nenhuma', true, 601, '1', '1'
FROM itsm.categoria c
WHERE c.deleted = 0 AND c.nome_completo = 'Manutenção predial > Ar-condicionado'
  AND NOT EXISTS (SELECT 1 FROM itsm.formulario o WHERE o.deleted = 0 AND o.codigo = '6.2');

INSERT INTO itsm.categoria (id, parent_id, nome, nome_completo, nivel, equipe_id, ordem, creator, updater)
SELECT nextval('itsm.categoria_seq'), pai.id, 'Extintores e segurança', 'Manutenção predial > Extintores e segurança', 2, pai.equipe_id, 602, '1', '1'
FROM itsm.categoria pai
WHERE pai.deleted = 0 AND pai.parent_id = 0 AND pai.nome = 'Manutenção predial'
  AND NOT EXISTS (SELECT 1 FROM itsm.categoria c WHERE c.deleted = 0 AND c.nome_completo = 'Manutenção predial > Extintores e segurança');

INSERT INTO itsm.formulario (id, codigo, nome, descricao, grupo, icone, categoria_id, tipo, urgencia, impacto, titulo_modelo, definicao, aprovacao, ativo, ordem, creator, updater)
SELECT nextval('itsm.formulario_seq'), '6.3', 'Extintores e segurança', 'Extintor, sinalização, alarme, hidrante e saídas de emergência. Risco imediato (fogo, choque, gás ou desabamento): saia do local e acione os Bombeiros (193) antes de abrir o pedido.', 'Manutenção predial', 'ep:warning', c.id, 2, 3, 3, 'Extintores e segurança: {problema} - {local}',
       '{"secoes":[{"nome":"Onde é o problema","perguntas":[{"name":"nome_solicitante","label":"Seu nome","type":"text","required":true},{"name":"local","label":"Nome do local/unidade","type":"text","required":true},{"name":"local_exato","label":"Sala, andar ou ponto de referência","type":"text","required":true},{"name":"celular_contato","label":"Celular para contato (com DDD)","type":"regex","required":true,"regex":"/^[0-9]{10,11}$/"}]},{"nome":"O que aconteceu","perguntas":[{"name":"problema","label":"O que você precisa?","type":"select","required":true,"values":["Extintor vencido ou faltando","Extintor usado ou descarregado","Sinalização de emergência","Alarme de incêndio","Hidrante ou mangueira","Saída de emergência obstruída","Outros"]},{"name":"risco","label":"Há risco para as pessoas ou o local está sem poder funcionar?","type":"radios","required":true,"values":["Não","Sim"]},{"name":"descricao_solicitacao","label":"Descreva o pedido","type":"textarea","required":true}]}]}', 'nenhuma', true, 602, '1', '1'
FROM itsm.categoria c
WHERE c.deleted = 0 AND c.nome_completo = 'Manutenção predial > Extintores e segurança'
  AND NOT EXISTS (SELECT 1 FROM itsm.formulario o WHERE o.deleted = 0 AND o.codigo = '6.3');

INSERT INTO itsm.categoria (id, parent_id, nome, nome_completo, nivel, equipe_id, ordem, creator, updater)
SELECT nextval('itsm.categoria_seq'), pai.id, 'Outros serviços prediais', 'Manutenção predial > Outros serviços prediais', 2, pai.equipe_id, 603, '1', '1'
FROM itsm.categoria pai
WHERE pai.deleted = 0 AND pai.parent_id = 0 AND pai.nome = 'Manutenção predial'
  AND NOT EXISTS (SELECT 1 FROM itsm.categoria c WHERE c.deleted = 0 AND c.nome_completo = 'Manutenção predial > Outros serviços prediais');

INSERT INTO itsm.formulario (id, codigo, nome, descricao, grupo, icone, categoria_id, tipo, urgencia, impacto, titulo_modelo, definicao, aprovacao, ativo, ordem, creator, updater)
SELECT nextval('itsm.formulario_seq'), '6.4', 'Outros serviços prediais', 'Hidráulica, porta, janela, telhado, pintura, limpeza, poda e o que não se encaixar nos outros. Risco imediato (fogo, choque, gás ou desabamento): saia do local e acione os Bombeiros (193) antes de abrir o pedido.', 'Manutenção predial', 'ep:office-building', c.id, 2, 3, 3, 'Outros serviços prediais: {problema} - {local}',
       '{"secoes":[{"nome":"Onde é o problema","perguntas":[{"name":"nome_solicitante","label":"Seu nome","type":"text","required":true},{"name":"local","label":"Nome do local/unidade","type":"text","required":true},{"name":"local_exato","label":"Sala, andar ou ponto de referência","type":"text","required":true},{"name":"celular_contato","label":"Celular para contato (com DDD)","type":"regex","required":true,"regex":"/^[0-9]{10,11}$/"}]},{"nome":"O que aconteceu","perguntas":[{"name":"problema","label":"O que você precisa?","type":"select","required":true,"values":["Vazamento ou entupimento","Falta de água","Porta, fechadura ou janela","Telhado, infiltração ou mofo","Piso, parede ou pintura","Limpeza ou retirada de entulho","Poda ou jardinagem","Dedetização ou controle de pragas","Outros"]},{"name":"risco","label":"Há risco para as pessoas ou o local está sem poder funcionar?","type":"radios","required":true,"values":["Não","Sim"]},{"name":"descricao_solicitacao","label":"Descreva o pedido","type":"textarea","required":true}]}]}', 'nenhuma', true, 603, '1', '1'
FROM itsm.categoria c
WHERE c.deleted = 0 AND c.nome_completo = 'Manutenção predial > Outros serviços prediais'
  AND NOT EXISTS (SELECT 1 FROM itsm.formulario o WHERE o.deleted = 0 AND o.codigo = '6.4');
