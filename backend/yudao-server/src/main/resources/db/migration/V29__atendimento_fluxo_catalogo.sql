-- ========== F2e (parte 1) — fluxo do catálogo pelo WhatsApp (substitui o agente do n8n) ==========
-- Ver docs/specs/atendimento-fluxo-whatsapp.md

ALTER TABLE atendimento.caixa ADD COLUMN fluxo_ativo bool NOT NULL DEFAULT false;
ALTER TABLE atendimento.caixa ADD COLUMN fluxo_boas_vindas varchar(1000) NULL;

-- Estado do fluxo de cada conversa. As respostas ainda não enviadas ficam cifradas (podem ter CPF, nascimento...)
CREATE TABLE atendimento.fluxo (
    conversa_id      int8          NOT NULL,
    etapa            varchar(30)   NOT NULL,
    formulario_id    int8          NULL,
    indice           int4          NOT NULL DEFAULT 0,
    respostas        text          NULL,           -- JSON cifrado com a chave-mestra
    dados            text          NOT NULL DEFAULT '{}',   -- grupo escolhido, sugestão, correção em andamento
    sensiveis        text          NOT NULL DEFAULT '[]',   -- ids das mensagens com dado pessoal (mascaradas no envio)
    ultima_mensagem  int8          NOT NULL DEFAULT 0,      -- última mensagem do contato já tratada
    atualizado_em    timestamp     NOT NULL,
    creator          varchar(64)   NULL DEFAULT '',
    create_time      timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater          varchar(64)   NULL DEFAULT '',
    update_time      timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted          int2          NOT NULL DEFAULT 0,
    CONSTRAINT pk_atendimento_fluxo PRIMARY KEY (conversa_id)
);
