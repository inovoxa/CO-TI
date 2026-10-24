# Atendimento — horário, macros e automações

Comportamento de referência: Chatwoot 4.19, parte MIT (`app/models/automation_rule.rb`, `macro.rb`, `working_hour.rb`).
Nada é copiado; esta especificação descreve o que o CO-TI faz.

## Horário de atendimento

- A caixa pode usar um **calendário do Service Desk** (o mesmo do SLA, com expediente e feriados).
- Se a caixa tiver uma **mensagem fora do horário**, ela é mandada quando uma conversa é aberta ou reaberta fora do
  expediente. Vai depois da saudação e uma vez por abertura.
- A condição `horario` (dentro/fora) das automações usa o mesmo calendário. Caixa sem calendário conta como sempre
  dentro do horário.

## Automações

Uma regra tem **evento**, **condições** (todas precisam valer) e **ações**, e vale para todas as caixas, a não ser que
uma condição `caixa` restrinja.

| Evento | Quando |
|---|---|
| `conversa_criada` | conversa nova pelo canal, depois da saudação e da distribuição |
| `mensagem_recebida` | cada mensagem do contato (não conta eco do celular nem histórico importado) |
| `conversa_reaberta` | o contato escreveu numa conversa resolvida/adiada, ou alguém reabriu |
| `conversa_resolvida` | a conversa foi resolvida (por pessoa, macro ou automação de outro evento) |

Condições: `caixa`, `canal`, `conteudo` (texto da mensagem; sem diferença de maiúsculas e acentos; vários termos
separados por vírgula: basta um), `status`, `prioridade`, `etiqueta`, `atendente`, `contato_email`,
`contato_telefone`, `contato_portal` (o contato é pessoa do portal) e `horario`.

Ações (as mesmas das macros):

| Ação | O que faz |
|---|---|
| `atribuir_agente` | atribui a um atendente; só se ele atender a caixa |
| `atribuir_automatico` | distribui como uma conversa nova |
| `remover_agente` | tira o atendente |
| `adicionar_etiquetas`, `remover_etiquetas` | mexe nas etiquetas |
| `enviar_mensagem` | manda ao contato |
| `nota` | escreve nota interna |
| `status` | aberta, pendente ou resolvida |
| `prioridade` | de 0 (nenhuma) a 4 (urgente) |

Os textos aceitam `{{contato.nome}}`, `{{conversa.id}}` e `{{atendente.nome}}`.

### Execução

- Roda na mesma transação do evento, na ordem definida (campo `ordem`, depois id).
- **Sem laços:** o que uma automação faz não dispara outras automações.
- Uma ação que não se aplica é pulada, sem derrubar as outras. Exemplo: atribuir a quem não atende a caixa.
- Cada execução deixa um registro na conversa ("Automação «nome»: ...") e conta em `execucoes` / `ultima_execucao`.
- Só quem tem `atendimento:automacao:update` cria ou altera regras, e isso vai para a auditoria.

## Macros

- Lista de ações executada pelo atendente numa conversa, com um clique.
- **Pessoal:** só o dono vê e usa. **Todos:** qualquer atendente usa; criar ou alterar exige
  `atendimento:macro:todos`.
- A execução respeita o acesso à conversa e registra "Ana executou a macro «nome»".
- Ao contrário das automações, as ações de uma macro **disparam** as automações. Exemplo: resolver pela macro dispara
  `conversa_resolvida`.
