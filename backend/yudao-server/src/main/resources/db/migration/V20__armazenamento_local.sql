-- ========== Armazenamento de arquivos (anexos de chamado, imagens do editor, base de conhecimento) ==========
-- O RuoYi vem com configurações de exemplo que apontam para buckets públicos do autor (Qiniu, Aliyun, Tencent...),
-- com o Qiniu marcado como padrão: um anexo de chamado iria para um servidor de terceiros. Desliga todas e usa
-- disco local no volume do contêiner (/app/arquivos). Endereço relativo: funciona atrás do nginx do frontend.
UPDATE infra_file_config SET master = false, deleted = 1;
INSERT INTO infra_file_config (id, name, storage, remark, master, config, creator, updater, deleted) VALUES
(50000, 'Disco local (CO-TI)', 10, 'Volume /app/arquivos do contêiner do backend', true,
 '{"@class":"cn.iocoder.yudao.module.infra.framework.file.core.client.local.LocalFileClientConfig","basePath":"/app/arquivos","domain":""}',
 '1', '1', 0);
