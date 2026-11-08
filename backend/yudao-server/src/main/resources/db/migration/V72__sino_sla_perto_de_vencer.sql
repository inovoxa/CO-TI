-- Aviso de SLA perto de vencer, no sino. Cada prazo avisa uma vez; se o prazo mudar (ex.: ficou pendente), avisa de novo.
INSERT INTO system_notify_template (id, name, code, nickname, content, type, params, status, remark, creator, updater, deleted) VALUES
(9012, 'Prazo para assumir perto de vencer', 'coti_chamado_sla_atendimento', 'SLA', 'Chamado #{chamado} precisa ser assumido até {hora}: {titulo}', 2, '["chamado","titulo","papel","hora"]', 0, 'Para os técnicos e a equipe do chamado', '1', '1', 0),
(9013, 'Prazo para solucionar perto de vencer', 'coti_chamado_sla_solucao', 'SLA', 'Chamado #{chamado} precisa ser solucionado até {hora}: {titulo}', 2, '["chamado","titulo","papel","hora"]', 0, 'Para os técnicos e a equipe do chamado', '1', '1', 0);

CREATE TABLE itsm.sla_aviso (
    chamado_id int8        NOT NULL,
    tipo       varchar(12) NOT NULL,   -- atendimento | solucao
    prazo      timestamp   NOT NULL,
    criado_em  timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_itsm_sla_aviso PRIMARY KEY (chamado_id, tipo, prazo)
);
