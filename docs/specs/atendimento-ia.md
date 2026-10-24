# Atendimento — assistente de IA e copiloto

**Origem:** o Chatwoot tem um assistente (Captain) na pasta `enterprise/`, cuja licença proíbe copiar ou
adaptar o código. O CO-TI reimplementa do zero a *ideia* de um assistente que responde pelos canais e de um copiloto
para o atendente. Nenhum código, prompt ou estrutura de dados daquela pasta foi consultado ou copiado. Esta
especificação é a referência da implementação.

## Provedores (vários)

- A instalação cadastra **quantos provedores quiser**. Todos falam a API de chat da OpenAI
  (`POST {base}/chat/completions`, com *tools*). Os tipos prontos preenchem o endereço padrão:

  | Tipo | Observação |
  |---|---|
  | **OpenRouter** | manda também `HTTP-Referer` (endereço público do CO-TI) e `X-Title: CO-TI` |
  | OpenAI, Anthropic (endpoint compatível), Google Gemini (endpoint compatível), Groq, DeepSeek, Mistral | — |
  | **Ollama (local)** | sem chave; para quem não pode mandar dados para fora |
  | Outro compatível | vLLM, LM Studio, proxy próprio |

- Cada provedor tem: nome, tipo, endereço, chave (cifrada com a chave-mestra, nunca devolvida à tela), modelo,
  temperatura, limite de tokens, ativo e prioridade. Há um teste de conexão por provedor.
- A configuração geral tem a chave liga/desliga e o **provedor principal** de cada uso: o assistente e o copiloto
  (sugerir, resumir, reescrever). Sem principal escolhido, vale o primeiro ativo pela prioridade.
- **Reserva:** se o principal falhar (rede, limite, 5xx, chave inválida...), os outros provedores ativos são
  tentados na ordem da prioridade. Só quando todos falham a chamada conta como falha (e o assistente transborda
  depois de 2 falhas seguidas).
- Cada tentativa fica registrada em `atendimento.ia_uso`: provedor, finalidade, conversa, modelo, tokens, duração
  e erro. Isso serve para custo e auditoria; o texto das mensagens não é copiado para lá.

## Assistente nas caixas

Configurado por caixa: ativo, nome (ex.: "Assistente da TI"), instruções da organização e limite de respostas por
conversa (padrão 10).

### Ciclo

1. Numa caixa com assistente, a conversa nova começa **pendente** (situação "com o assistente"). Ela não é
   distribuída e não entra em "minhas" de ninguém.
2. Cada mensagem do contato numa conversa pendente e sem atendente é respondida pelo assistente, fora da
   requisição (fila própria, uma conversa por vez, na ordem de chegada). Mensagens seguidas são respondidas de uma vez.
3. **Transbordo para humano**, com a conversa passando a aberta e sendo distribuída como nova:
   - o modelo chama `transferir_para_humano`, quando o contato pede uma pessoa, quando não sabe a resposta ou quando
     o assunto exige alguém (senha, acesso, dados pessoais);
   - o limite de respostas por conversa é atingido;
   - o provedor falha 2 vezes seguidas.

   O motivo fica registrado na conversa.
4. Um atendente que responde, assume ou muda a situação da conversa tira o assistente dela.

### O que o assistente pode fazer (ferramentas)

| Ferramenta | Efeito |
|---|---|
| `buscar_base_conhecimento(consulta)` | busca nos artigos **publicados e marcados como FAQ** (os mesmos que qualquer pessoa vê no portal) |
| `consultar_chamados()` | lista os últimos chamados **do próprio contato**, pela pessoa do portal ou pelo e-mail |
| `abrir_chamado(titulo, descricao)` | abre um chamado em nome do contato, com origem no canal |
| `transferir_para_humano(motivo)` | transbordo (acima) |

Regras de segurança:
- As ferramentas só agem sobre o contato da conversa. Nenhum parâmetro do modelo escolhe outra pessoa, outro
  chamado ou outro registro.
- O assistente não pede nem recebe senhas. Redefinição de senha e acessos vão sempre para um humano.
- O texto vindo do contato e dos artigos é dado, não instrução. O prompt de sistema diz isso, e as ferramentas não
  dão acesso a nada além do descrito acima.
- `abrir_chamado` exige contato com e-mail ou pessoa do portal, e no máximo 1 chamado por conversa.
- No máximo 4 rodadas de ferramentas por resposta.
- A resposta é texto puro, enviado como as outras mensagens. O remetente é "robo", com o nome do assistente.

## Copiloto do atendente

Dentro da conversa, para quem atende:

| Ação | O que faz |
|---|---|
| **Sugerir resposta** | rascunho com base nas últimas mensagens e nos artigos publicados (não só FAQ); vai para o campo de texto, para revisar antes de enviar |
| **Resumir** | resumo curto da conversa, para quem assume ou para o chamado |
| **Reescrever** | o texto digitado, em tom mais formal, mais cordial ou só com a ortografia corrigida |

O copiloto nunca envia nada sozinho. Usa o mesmo provedor, com registro em `ia_uso`.

## Permissões

- `atendimento:ia:config`: configurar o provedor (perfil administrador).
- Assistente da caixa: quem tem `atendimento:caixa:update`.
- Copiloto: quem atende a conversa (`atendimento:conversa:query` e acesso à caixa).
