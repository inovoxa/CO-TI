-- Convites dos compromissos: o dono chama pessoas, o compromisso aparece na agenda delas e cada uma responde.
CREATE TABLE calendario.convite (
    evento_id     int8        NOT NULL,
    usuario_id    int8        NOT NULL,
    resposta      varchar(10) NOT NULL DEFAULT 'pendente',
    respondido_em timestamp   NULL,
    create_time   timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_calendario_convite PRIMARY KEY (evento_id, usuario_id),
    CONSTRAINT fk_calendario_convite_evento FOREIGN KEY (evento_id) REFERENCES calendario.evento (id) ON DELETE CASCADE,
    CONSTRAINT ck_calendario_convite_resposta CHECK (resposta IN ('pendente', 'sim', 'nao', 'talvez'))
);
CREATE INDEX idx_calendario_convite_usuario ON calendario.convite (usuario_id);

-- Avisos no sino (o link abre o compromisso no Calendário)
INSERT INTO system_notify_template (id, name, code, nickname, content, type, params, status, remark, creator, updater, deleted) VALUES
(9017, 'Convite para compromisso', 'coti_agenda_convite', 'Agenda', '{pessoa} convidou você: {titulo}, {quando}', 2, '["pessoa","titulo","quando","link"]', 0, 'Para quem foi convidado', '1', '1', 0),
(9018, 'Compromisso alterado', 'coti_agenda_alterado', 'Agenda', '{pessoa} alterou o compromisso: {titulo}, agora {quando}', 2, '["pessoa","titulo","quando","link"]', 0, 'Para os convidados', '1', '1', 0),
(9019, 'Compromisso cancelado', 'coti_agenda_cancelado', 'Agenda', '{pessoa} cancelou o compromisso: {titulo}, {quando}', 2, '["pessoa","titulo","quando"]', 0, 'Para os convidados', '1', '1', 0),
(9020, 'Retirado do compromisso', 'coti_agenda_retirado', 'Agenda', '{pessoa} tirou você do compromisso: {titulo}, {quando}', 2, '["pessoa","titulo","quando"]', 0, 'Para quem deixou de ser convidado', '1', '1', 0),
(9021, 'Resposta a convite', 'coti_agenda_resposta', 'Agenda', '{pessoa} {acao} {titulo}, {quando}', 2, '["pessoa","acao","titulo","quando","link"]', 0, 'Para o dono do compromisso', '1', '1', 0);
