-- Sino: monitor fora do ar e certificado perto de vencer, para quem enxerga o monitor. Funciona mesmo sem canal de aviso.
INSERT INTO system_notify_template (id, name, code, nickname, content, type, params, status, remark, creator, updater, deleted) VALUES
(9015, 'Monitor fora do ar', 'coti_monitor_caiu', 'Monitoramento', 'Fora do ar: {monitor} ({motivo})', 2, '["monitor","motivo","link"]', 0, 'Para quem enxerga o monitor; só na queda, sem repetir', '1', '1', 0),
(9016, 'Certificado perto de vencer', 'coti_monitor_certificado', 'Monitoramento', 'Certificado de {monitor} {prazo}', 2, '["monitor","prazo","link"]', 0, 'Para quem enxerga o monitor; um aviso por certificado', '1', '1', 0);
