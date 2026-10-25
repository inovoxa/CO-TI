# Assistente interno de IA

Chat do portal (menu "Assistente IA") para a equipe e para os servidores perguntarem, em texto livre, sobre a TI da
organização. Implementado em `yudao-module-assistente`; usa os provedores de IA configurados em Atendimento →
Inteligência artificial (finalidade `interno` no registro de uso).

## Princípio: sabe tudo o que quem pergunta pode ver

- **Leitura:** o modelo não lê o banco. Ele chama **consultas** (ferramentas) que rodam na requisição da pessoa, com
  os mesmos serviços das telas. Por isso as permissões (RBAC) e o escopo de unidade do perfil valem igual.
- **Permissões:** cada consulta declara a permissão que exige.
  - Só as consultas permitidas são oferecidas ao modelo.
  - A permissão é conferida de novo na execução, caso o modelo chame uma consulta que não foi oferecida.
- **Só leitura:** o assistente não abre, altera nem apaga nada. Quando a pessoa pede uma ação, ele indica onde fazer
  no portal.
- **Conteúdo das consultas é dado, não instrução:** títulos de chamados, artigos, logs e mensagens podem conter texto
  malicioso. O prompt de sistema manda ignorar ordens escritas nesse conteúdo. Como as consultas são só de leitura e
  presas às permissões da pessoa, uma injeção não amplia o acesso.

## Consultas

| Consulta | Permissão | O que traz |
|---|---|---|
| `buscar_ativos` | `inventario:ativo:query` | máquinas por nome, tipo, sistema (nome parcial), fabricante, modelo, antivírus, alerta, situação, unidade, pessoa, software e memória |
| `detalhar_ativo` | `inventario:ativo:query` | hardware, peças, softwares, alertas e chamados de uma máquina |
| `resumo_inventario` | `inventario:ativo:query` | contagem por sistema, fabricante, modelo, tipo, antivírus, situação, unidade ou origem |
| `buscar_softwares` | `inventario:ativo:query` | catálogo com instalações e licenças |
| `alertas_inventario` | `inventario:ativo:query` | alertas abertos ou encerrados |
| `licencas` | `itsm:licenca:query` | compradas, usos registrados, instalações encontradas |
| `buscar_chamados` | `itsm:chamado:query` | fila do Service Desk |
| `painel_service_desk` | `itsm:chamado:query` | quantidades por fila |
| `detalhar_chamado` | técnico pelo escopo; os demais só se participam | chamado e andamento |
| `meus_chamados` | — (os da própria pessoa) | chamados em que a pessoa é solicitante ou observadora |
| `buscar_base_conhecimento` | visibilidade do artigo | texto dos artigos mais relevantes |
| `buscar_problemas` / `buscar_mudancas` | `itsm:problema:query` / `itsm:mudanca:query` | problemas e mudanças |
| `buscar_conversas` | `atendimento:conversa:query` | conversas do atendimento |
| `buscar_pessoas` | `system:user:query` | nome, login, e-mail, celular, unidade e máquinas |
| `status_links` / `buscar_logs` | `system:monitor-links:query` / `system:monitor-logs:query` | Centro de Operações |

Para incluir uma consulta nova, registre-a em `FerramentasInternas` com a permissão do módulo dono dos dados.

## Limites

- Até 6 rodadas de consultas por pergunta.
- O resultado de cada consulta é cortado em 14 mil caracteres.
- Vão ao modelo as 12 últimas mensagens da conversa.
- Até 20 perguntas por minuto por pessoa.

## Conversas

- **Onde ficam:** `ia.assistente_sessao` e `ia.assistente_mensagem`.
- **Acesso:** cada pessoa só lista, lê e apaga as próprias conversas.
- **Registro das consultas:** a resposta guarda as consultas feitas (nome e argumentos), que a tela mostra embaixo
  da resposta. Os resultados não são gravados.
