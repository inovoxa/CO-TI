-- ========== Assistente virtual: textos e opções do robô num lugar só ==========
-- Menu, respostas da consulta, avisos do chamado e orientações da IA editáveis pela tela
-- Atendimento → Configuração → Assistente virtual. Só o que foi alterado fica aqui: sem linha, vale o padrão do código
-- (cn.iocoder.yudao.module.atendimento.dominio.TextosAssistente).
CREATE TABLE atendimento.assistente_texto (
    chave          varchar(80)  NOT NULL,
    valor          text         NOT NULL,
    atualizado_por int8         NULL,
    atualizado_em  timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_atendimento_assistente_texto PRIMARY KEY (chave)
);

-- ========== Menus ==========
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, updater, deleted) VALUES
(52042, 'Assistente virtual',          '', 2, 0, 52030, 'assistente', 'ep:service', 'atendimento/assistente/index', 'AtendimentoAssistente', 0, true, true, true, '1', '1', 0),
(52043, 'Configurar o assistente',     'atendimento:assistente:config', 3, 1, 52042, '', '', '', NULL, 0, true, true, true, '1', '1', 0);
