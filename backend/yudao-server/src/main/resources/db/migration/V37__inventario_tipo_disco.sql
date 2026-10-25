-- ========== Inventário: tipo de disco (SSD/HD) por máquina ==========
-- Ver docs/specs/inventario-agentes.md ("Tipo de disco"). Classificado no recebimento do inventário pelo modelo e pela
-- interface de cada disco; os ativos que já existiam são classificados na subida do backend a partir dos componentes.

ALTER TABLE cmdb.ativo ADD COLUMN disco_tipo varchar(15) NULL;
ALTER TABLE cmdb.ativo ADD CONSTRAINT ck_cmdb_ativo_disco_tipo CHECK (disco_tipo IN ('ssd', 'hdd', 'misto', 'desconhecido'));
CREATE INDEX idx_cmdb_ativo_disco_tipo ON cmdb.ativo (disco_tipo) WHERE deleted = 0;
