-- Sino: avisos de chamado dentro do portal, para quem está envolvido. Funciona mesmo sem e-mail configurado.
INSERT INTO system_notify_template (id, name, code, nickname, content, type, params, status, remark, creator, updater, deleted) VALUES
(9001, 'Chamado novo', 'coti_chamado_criado', 'Chamados', 'Novo chamado #{chamado}: {titulo}', 2, '["chamado","titulo","papel"]', 0, 'Para os técnicos e a equipe do chamado', '1', '1', 0),
(9002, 'Chamado atribuído', 'coti_chamado_atribuido', 'Chamados', 'Chamado #{chamado} atribuído a você ou à sua equipe: {titulo}', 2, '["chamado","titulo","papel"]', 0, 'Para quem acabou de receber o chamado', '1', '1', 0),
(9003, 'Novo andamento', 'coti_chamado_acompanhamento', 'Chamados', 'Novo andamento no chamado #{chamado}: {titulo}', 2, '["chamado","titulo","papel"]', 0, 'Andamento privado só vai para os técnicos', '1', '1', 0),
(9004, 'Solução registrada', 'coti_chamado_solucao', 'Chamados', 'Chamado #{chamado} solucionado: {titulo}', 2, '["chamado","titulo","papel"]', 0, NULL, '1', '1', 0),
(9005, 'Chamado fechado', 'coti_chamado_fechado', 'Chamados', 'Chamado #{chamado} fechado: {titulo}', 2, '["chamado","titulo","papel"]', 0, NULL, '1', '1', 0),
(9006, 'Chamado reaberto', 'coti_chamado_reaberto', 'Chamados', 'Chamado #{chamado} reaberto: {titulo}', 2, '["chamado","titulo","papel"]', 0, NULL, '1', '1', 0),
(9007, 'Chamado pendente', 'coti_chamado_pendente', 'Chamados', 'Chamado #{chamado} ficou pendente: {titulo}', 2, '["chamado","titulo","papel"]', 0, NULL, '1', '1', 0),
(9008, 'Aprovação pendente', 'coti_chamado_aprovacao', 'Aprovações', 'Sua aprovação foi pedida no chamado #{chamado}: {titulo}', 2, '["chamado","titulo","papel"]', 0, NULL, '1', '1', 0),
(9009, 'Pedido aprovado', 'coti_chamado_aprovado', 'Aprovações', 'Chamado #{chamado} aprovado: {titulo}', 2, '["chamado","titulo","papel"]', 0, NULL, '1', '1', 0),
(9010, 'Pedido recusado', 'coti_chamado_recusado', 'Aprovações', 'Chamado #{chamado} recusado: {titulo}', 2, '["chamado","titulo","papel"]', 0, NULL, '1', '1', 0),
(9011, 'Chamado escalonado', 'coti_chamado_escalonado', 'Chamados', 'Chamado #{chamado} escalonado: {titulo}', 2, '["chamado","titulo","papel"]', 0, NULL, '1', '1', 0);

-- Chat: resposta automática de quem está de férias ou ausente
ALTER TABLE chat.mensagem DROP CONSTRAINT ck_chat_mensagem_tipo;
ALTER TABLE chat.mensagem ADD CONSTRAINT ck_chat_mensagem_tipo CHECK (tipo IN ('texto', 'sistema', 'auto'));

CREATE TABLE chat.ausencia (
    id          int8         NOT NULL,
    user_id     int8         NOT NULL,
    ativa       bool         NOT NULL DEFAULT false,
    mensagem    varchar(500) NOT NULL,
    inicio      date         NULL,   -- vazio = já vale
    fim         date         NULL,   -- vazio = até desligar
    creator     varchar(64)  NULL DEFAULT '',
    create_time timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater     varchar(64)  NULL DEFAULT '',
    update_time timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     int2         NOT NULL DEFAULT 0,
    CONSTRAINT pk_chat_ausencia PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_chat_ausencia_user ON chat.ausencia (user_id) WHERE deleted = 0;
CREATE SEQUENCE chat.ausencia_seq START 1;
