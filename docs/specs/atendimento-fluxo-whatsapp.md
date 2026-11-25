# Atendimento — fluxo do catálogo pelo WhatsApp (substitui o n8n)

Referência analisada: `GLPI_V2_API_Workflows` (n8n da LGTI, workflows 01–26).

## O que o n8n faz hoje

Fluxo: WhatsApp → Chatwoot → `01_GLPI_Agente_Principal`. O WF01 é um agente de IA (OpenRouter, DeepSeek) com um
prompt de cerca de 22 mil caracteres:

- **Menu:** 1 abrir, 2 consultar, 3 FAQ. A opção 1 mostra o catálogo 1.1–4.2 e 6.1–6.4.
- **Coleta:** o modelo pergunta os campos um por um e depois chama uma ferramenta por serviço (WF04–WF16), que
  monta o HTML e cria o chamado na API v2 do GLPI.
- **Consulta:** WF11 busca por número **ou por e-mail digitado**.
- **Outros serviços:** WF12 (acompanhamento), WF18 (FAQ), CEP, "escalar humano", transcrição de áudio (Whisper) e
  voz (ElevenLabs).
- **Pós-pedido:**
  - WF13 avisa o contato quando o chamado anda;
  - WF14 executa no AD depois da aprovação;
  - WF20 avisa o superior por WhatsApp.

Problemas desse desenho:

1. **A validação depende do modelo.** O prompt manda "nunca recusar coletar CPF", e os formatos são conferidos
   pelo texto do prompt, não por código.
2. **Ninguém é identificado.** Qualquer número pede reset de senha ou desativação em nome de qualquer pessoa. A
   única barreira é a aprovação do superior, que é informada pelo próprio solicitante. A consulta por e-mail
   digitado mostra chamados de terceiros.
3. **Dados pessoais ficam na conversa** (CPF, nascimento, e-mail pessoal) e na memória do agente.
4. **Três cópias do catálogo** (SQL, JSON dos formulários, MAPA do WF22) e o prompt repetindo os campos.

## O que o CO-TI faz (F2e)

O fluxo é **determinístico** e roda sobre o **catálogo de formulários** que já existe (F1c: os 25 formulários de
`formularios_definicoes.json`). A mesma definição serve ao portal e ao WhatsApp. Validação, aprovação e operação
no AD são exatamente as do portal (`FormularioService.enviar`).

| n8n | CO-TI |
|---|---|
| prompt com o menu e os campos | menu e perguntas gerados do catálogo (grupos e formulários ativos, na ordem) |
| validação pelo modelo | `Formulario.validar` por resposta (regex, data, e-mail, opções), com a mensagem de erro e a mesma pergunta de novo |
| WF04–WF16 + HTML montado à mão | `FormularioService.enviar`: chamado, respostas, aprovação (gestor, campo ou fixo) e execução no AD |
| WF11 consulta por e-mail digitado | só os chamados **do próprio contato** (pessoa do portal ligada ao celular) |
| WF12 atualizar chamado | no detalhe (`#123`), o contato escreve no chamado e aprova ou recusa a solução, com as regras do portal |
| WF18 FAQ | artigos FAQ publicados, com a mesma ordem por relevância do assistente (F2d) |
| Consultar_CEP | consulta de CEP (ViaCEP, configurável) que preenche logradouro, bairro e cidade |
| "escalar humano" | `atendente`, a qualquer momento: transbordo com distribuição |
| interpretação de texto livre pelo modelo | sugestão do serviço pelo texto: a IA (F2d) quando ligada, senão a busca no catálogo; sempre com confirmação |

### Identificação

- O catálogo só abre pedidos para quem é **identificado**: o contato ligado a uma pessoa do portal pelo celular
  (com ou sem o nono dígito, F2a) ou pelo e-mail.
- Quem não é identificado pode consultar a FAQ ou falar com um atendente. A mensagem explica que o celular
  precisa estar no cadastro (portal ou AD).
- O solicitante do chamado é a própria pessoa. Aprovação e operação no AD seguem as regras do formulário.

### Dados pessoais

- As respostas ficam guardadas na conversa **cifradas** (estado do fluxo, `atendimento.fluxo`) até o envio. Depois,
  só no chamado (respostas do formulário).
- Depois do envio, as mensagens do contato que responderam a campos sensíveis (`cpf`, `data_nascimento`,
  `email_pessoal`, `matricula`, `celular`) aparecem **mascaradas** na conversa.
- Os resumos de confirmação mostram esses campos parcialmente (`***.***.789-01`).

### Comandos (a qualquer momento)

| Comando | Efeito |
|---|---|
| `menu` ou `0` | menu principal (descarta o pedido em andamento, depois de confirmar se já havia respostas) |
| `voltar` | pergunta anterior |
| `cancelar` | descarta o pedido |
| `atendente` | passa para uma pessoa |

Depois de 30 minutos sem resposta, o pedido em andamento expira.

### Relação com o assistente de IA (F2d)

- Numa caixa com **fluxo do catálogo**, o fluxo atende. O assistente conversacional da F2d vale só para as caixas
  sem fluxo.
- Com a IA ligada, o texto livre do menu ("a impressora não imprime") vai ao provedor só para **sugerir o código do
  serviço**. A sugestão sempre pede confirmação ao contato. Sem IA, a sugestão vem da busca no catálogo.
- A IA **nunca coleta campos de formulário**: isso é sempre do fluxo, com validação em código.

## Segunda parte (F2e-2, feita)

Avisos de volta pelo canal, no lugar de WF13, WF14 e WF20:
- andamento, solução e resultado da operação no AD, para o contato;
- pedido de aprovação, para o superior.

Inclui o modelo aprovado do WhatsApp quando a janela de 24 h já fechou, e a transcrição de áudio pelos
provedores de IA que oferecem `/audio/transcriptions`.

## Terceira parte: Assistente virtual (feita)

Tela **Atendimento → Configuração → Assistente virtual** (permissão `atendimento:assistente:config`), com tudo do
robô num lugar só:

- **Caixas:** quem atende primeiro (fluxo do catálogo, assistente de IA ou direto para as pessoas), boas-vindas,
  nome, instruções e limite do assistente de IA, avisos do chamado e o modelo de aviso do WhatsApp. O diálogo de
  Caixas de entrada só mostra um resumo e o link.
- **Textos** (menu, consulta e atualização, avisos e orientações gerais da IA): cada um tem o padrão do código
  (`TextosAssistente`), variáveis clicáveis, prévia como sai no WhatsApp e "restaurar o padrão". O banco
  (`atendimento.assistente_texto`) guarda só o que foi alterado. Uma linha cuja variável ficou vazia some.
- **Opções:** quantos chamados listar, mostrar a última atualização da equipe, deixar escrever no chamado e
  deixar aprovar ou recusar a solução.
- **IA:** as regras de segurança do prompt são fixas e aparecem só para leitura; a organização muda o tom
  (orientações gerais) e as instruções de cada caixa.
- **Exportar e importar** em JSON, com prévia do que muda (sem segredos).

### Consulta e atualização pelo canal (WF11 e WF12)

- `2` no menu lista os chamados do contato; `#123` em qualquer ponto (inclusive respondendo a um aviso) abre o detalhe.
- O detalhe mostra a situação, as datas e a última atualização pública da equipe.
- Em andamento: `1` pede um texto, que vira acompanhamento do solicitante (`ChamadoService.adicionarInteracao`, o
  mesmo do portal).
- Solucionado: `1` aprova a solução e encerra; `2` pede o motivo e recusa a solução, reabrindo
  (`ChamadoService.avaliarSolucao`).
- Só chamados em que o contato é solicitante; o número digitado nunca alcança o chamado de outra pessoa. A etapa
  que espera o texto expira em 30 minutos, como o formulário.
- No assistente de IA, a ferramenta `adicionar_informacao(numero, texto)` faz o mesmo, com as mesmas restrições.
