-- CO-TI: últimos rótulos em chinês nos dicionários.
-- Tipo de terminal (aparece nos logs de acesso e nas sessões do OAuth2)
UPDATE system_dict_type SET name = 'Terminal' WHERE type = 'terminal';
UPDATE system_dict_data SET label = CASE value
        WHEN '10' THEN 'Miniapp do WeChat'
        WHEN '11' THEN 'Conta oficial do WeChat'
        WHEN '20' THEN 'Navegador (H5)'
        WHEN '31' THEN 'App iOS'
        WHEN '32' THEN 'App Android'
        ELSE label END
 WHERE dict_type = 'terminal';

-- status de requisição do módulo de manufatura (MES), removido na V40 sem levar este dicionário
DELETE FROM system_dict_data WHERE dict_type = 'mes_wm_issue_status';
DELETE FROM system_dict_type WHERE type = 'mes_wm_issue_status';
