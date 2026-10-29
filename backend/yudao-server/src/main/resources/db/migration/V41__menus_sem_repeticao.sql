-- CO-TI: menus sem repetição e sem telas de dados simulados.

-- ========== Centro de Operações: as visões por unidade ganham nomes próprios ==========
-- Elas resumem o Service Desk, o Monitoramento e o Inventário filtrando pela unidade escolhida no mapa.
UPDATE system_menu SET name = 'Chamados por unidade' WHERE id = 50030;
UPDATE system_menu SET name = 'Links por unidade' WHERE id = 50040;
UPDATE system_menu SET name = 'Equipamentos por unidade' WHERE id = 50050;

-- Quem já tem o módulo completo deixa de ver a visão por unidade (e os botões dela), para o menu não repetir.
DELETE FROM system_role_menu AS rm
USING (VALUES (50030, 51010), (50040, 55010), (50050, 53010)) AS par(visao, modulo)
WHERE (rm.menu_id = par.visao OR rm.menu_id IN (SELECT id FROM system_menu WHERE parent_id = par.visao))
  AND EXISTS (SELECT 1 FROM system_role_menu o WHERE o.role_id = rm.role_id AND o.menu_id = par.modulo AND o.deleted = 0);

-- ========== Telas sem integração real: desativadas até ligar a ferramenta ==========
-- Câmeras e Logs mostravam dados simulados; Mensagens (Element) e Plataforma (Grafana) dependem de configuração.
UPDATE system_menu SET status = 1 WHERE id IN (50020, 50060, 50070, 50080);

-- ========== Auditoria de identidade vai para Sistema › Auditoria do sistema ==========
UPDATE system_menu SET parent_id = 12, sort = 3, name = 'Trilha de auditoria' WHERE id = 50090;
-- quem tinha a tela precisa dos diretórios acima dela para o menu aparecer
INSERT INTO system_role_menu (id, role_id, menu_id, creator, updater, deleted, tenant_id)
SELECT nextval('system_role_menu_seq'), f.role_id, f.menu_id, '1', '1', 0, f.tenant_id
FROM (
    SELECT DISTINCT rm.role_id, pai.id AS menu_id, rm.tenant_id
    FROM system_role_menu rm
    CROSS JOIN (VALUES (1), (12)) AS pai(id)
    WHERE rm.menu_id = 50090 AND rm.deleted = 0
      AND NOT EXISTS (SELECT 1 FROM system_role_menu o WHERE o.role_id = rm.role_id AND o.menu_id = pai.id AND o.deleted = 0)
) f;

-- ========== Monitoramento: "Unidades" é o cadastro de filiais (endereço e ponto no mapa) ==========
UPDATE system_menu SET name = 'Unidades e endereços' WHERE id = 55040;

-- ========== Sistema e Infraestrutura: itens do RuoYi desativados e sem uso no CO-TI ==========
-- (multiempresa, SMS, OAuth2, login social, regiões da China, exemplos do gerador, fonte de dados, construtor de
-- formulários, Swagger, Druid e SkyWalking)
CREATE TEMP TABLE menus_removidos ON COMMIT DROP AS
WITH RECURSIVE arvore AS (
    SELECT id FROM system_menu WHERE id IN (181, 98, 201, 529, 271, 83, 195, 18, 20, 15, 85) AND status = 1
    UNION ALL
    SELECT m.id FROM system_menu m JOIN arvore a ON m.parent_id = a.id
)
SELECT id FROM arvore;

DELETE FROM system_role_menu WHERE menu_id IN (SELECT id FROM menus_removidos);
DELETE FROM system_menu WHERE id IN (SELECT id FROM menus_removidos);
