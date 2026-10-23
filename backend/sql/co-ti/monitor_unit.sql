-- Tabela de unidades/filiais com localização no mapa.
-- Cada unidade aponta para um departamento (system_dept), que forma a árvore de setores.
CREATE TABLE IF NOT EXISTS `monitor_unit` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'ID da unidade',
  `dept_id`     BIGINT       NOT NULL COMMENT 'Departamento raiz da unidade (system_dept.id)',
  `name`        VARCHAR(100) NOT NULL COMMENT 'Nome da unidade/filial',
  `city`        VARCHAR(100) DEFAULT NULL COMMENT 'Cidade/UF',
  `latitude`    DECIMAL(10,7) NOT NULL COMMENT 'Latitude',
  `longitude`   DECIMAL(10,7) NOT NULL COMMENT 'Longitude',
  `status`      TINYINT      NOT NULL DEFAULT 0 COMMENT '0 = ativa, 1 = inativa',
  `creator`     VARCHAR(64)  DEFAULT '' COMMENT 'Criador',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Criado em',
  `updater`     VARCHAR(64)  DEFAULT '' COMMENT 'Atualizador',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Atualizado em',
  `deleted`     BIT(1)       NOT NULL DEFAULT b'0' COMMENT 'Excluído',
  PRIMARY KEY (`id`),
  KEY `idx_dept_id` (`dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Unidades/filiais monitoradas no telão';

-- Exemplo (ajuste dept_id para os IDs reais da sua tabela system_dept):
-- INSERT INTO monitor_unit (dept_id, name, city, latitude, longitude) VALUES
-- (100, 'Sede · Datacenter principal', 'Brasília/DF',       -15.7939, -47.8828),
-- (101, 'Datacenter de contingência',  'São Paulo/SP',      -23.5505, -46.6333),
-- (102, 'Regional Osasco',             'Osasco/SP',         -23.5329, -46.7917),
-- (103, 'Regional Rio de Janeiro',     'Rio de Janeiro/RJ', -22.9068, -43.1729),
-- (104, 'Regional Belo Horizonte',     'Belo Horizonte/MG', -19.9167, -43.9345),
-- (105, 'Regional Curitiba',           'Curitiba/PR',       -25.4284, -49.2733);

-- Menus e permissões: execute backend/sql/monitor_menus.sql
