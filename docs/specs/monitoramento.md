# Monitoramento (F4)

Monitores de disponibilidade no lugar do Uptime Kuma. O Kuma 2.5.7 (MIT) é referência de comportamento:
`server/model/monitor.js` (estados e avisos), `server/uptime-calculator.js` (disponibilidade) e
`server/monitor-types` (tipos). O código é escrito em Java, em `backend/yudao-module-monitoramento` (ver NOTICE).

## Tipos

| Tipo | Verificação | No ar quando |
|---|---|---|
| `http` | pedido HTTP(S) com método, corpo, cabeçalhos e até N redirecionamentos | o código está entre os aceitos (padrão 200-299) |
| `palavra` | como `http` e lê até 1 MB da resposta | a palavra aparece (ou, invertida, não aparece) |
| `json` | como `http` e lê a resposta como JSON | o valor no caminho confere com o esperado (==, !=, <, <=, >, >=, contem) |
| `tcp` | conexão na porta | a conexão abre |
| `ping` | `ping -c 1 -W <limite>` (ICMP sem root) | responde |
| `dns` | consulta A, AAAA, CNAME, MX, NS, TXT, SOA, SRV, PTR ou CAA, no servidor informado ou no do sistema | há resposta (e contém o valor esperado, se informado) |
| `push` | o serviço chama `/api/push/<token>?status=up&msg=&ping=` | chegou sinal no intervalo |
| `manual` | ninguém verifica | a equipe marcou "no ar" |

**Consulta JSON:** aceita caminhos simples (`status`, `$.dados.servicos[0].estado`, `itens["nome com espaço"]`).
É um subconjunto das expressões JSONata do Kuma. Números são comparados como números.

**HTTPS:** cada verificação guarda os dias até o certificado vencer. "Ignorar erros de TLS" aceita certificado
vencido, autoassinado ou de outro nome, como no Kuma.

## Estados e avisos

Estados: `0` fora do ar, `1` no ar, `2` pendente, `3` em manutenção (os mesmos códigos do Kuma).

- **Tentativas:** com `tentativas = N`, as N primeiras falhas seguidas ficam pendentes e são verificadas no intervalo
  de nova tentativa. A falha seguinte derruba o monitor.
- **Invertido:** no ar quando a verificação falha (ex.: porta que deve estar fechada).
- **Batida importante** (vira evento): primeira verificação, no ar ↔ fora, pendente → fora, entrada e saída da
  manutenção.
- **Aviso:**
  - quando cai (no ar → fora, pendente → fora, manutenção → fora; e a primeira verificação se já estiver fora);
  - quando volta (fora → no ar);
  - de novo a cada N verificações fora, se "repetir aviso" estiver ligado;
  - uma vez por certificado, quando faltam `cert_aviso_dias` dias ou menos (renovou = novo aviso quando chegar perto).
- **Lento:** acima de `lento_ms`, o monitor continua no ar, mas aparece como lento (e a unidade fica "degradada" no
  telão).

## Chamado automático

Com "abrir chamado quando cair":

- na queda, abre um chamado com origem Monitoramento, a categoria e a urgência do monitor, impacto alto para link
  principal, na unidade do monitor (escopo de dados);
- enquanto o chamado estiver aberto, novas quedas não abrem outro;
- quando volta, registra no chamado quanto tempo ficou fora e o soluciona (ou só registra, se "solucionar sozinho"
  estiver desligado).

## Canais de aviso

| Canal | Configuração | Envio |
|---|---|---|
| E-mail | pessoas do CO-TI e/ou endereços | fila de e-mail do Service Desk, modelos `monitor-caiu`, `monitor-voltou` e `monitor-certificado` (editáveis) |
| Matrix | sala; servidor e token opcionais (padrão: os do bot do CO-TI) | `PUT /_matrix/client/v3/rooms/<sala>/send/m.room.message/<txn>` |
| Telegram | token do bot e chat | `POST /bot<token>/sendMessage` |
| Webhook | URL e segredo opcional | `POST` JSON `{evento, monitor, estado, msg, latenciaMs, duracaoForaS, chamadoId, momento, texto}`; com segredo, `X-COTI-Assinatura: sha256=<HMAC do corpo>` |

Tokens e segredos ficam cifrados com a chave-mestra (`CO_TI_CHAVE_MESTRA`) e voltam mascarados para a tela. Um canal
que falha não impede os outros; a falha vai para o log. "Padrão" marca o canal nos monitores novos.

O sino do portal independe dos canais: avisa a queda (depois das tentativas, sem reaviso) e o certificado perto de vencer
(um aviso por certificado), modelos `coti_monitor_caiu` e `coti_monitor_certificado`. Recebe quem enxerga o monitor na tela:
permissão `monitoramento:monitor:query`, ou administrador, respeitando o escopo de departamento. A volta ao ar não vai para o sino.

## Manutenção

| Tipo | Vale |
|---|---|
| Uma vez | do início ao fim |
| Toda semana | nos dias marcados (1 = segunda … 7 = domingo), da hora de início à de fim; fim antes do início atravessa a meia-noite |
| Manual | enquanto estiver ligada |

Aplica-se aos monitores escolhidos e/ou a todos os de uma unidade. Durante a manutenção o monitor não é verificado,
fica no estado 3, não avisa e não abre chamado; a disponibilidade não conta esse tempo.

## Agendador

- **Ciclo:** a cada segundo, os monitores vencidos (última verificação + intervalo, ou o intervalo de nova tentativa
  quando pendente) vão para threads virtuais.
- **Várias instâncias:** uma trava no Redis por monitor (`coti:monitoramento:monitor:<id>`) impede verificação em
  dobro. Depois de pegar a trava, o monitor é relido do banco.
- **Push:** vence quando não chegou sinal no intervalo; o sinal grava a batida na hora.
- **Manual:** só muda pela tela (ou ao entrar e sair da manutenção).
- **Desligar:** `CO_TI_MONITORAMENTO_ATIVO=false` desliga o agendador.

## Dados

- **Batidas:** `monitor.batida` é uma hypertable do TimescaleDB, com um pedaço por dia. Os pedaços mais antigos que
  `CO_TI_MONITORAMENTO_RETENCAO_DIAS` (padrão 31) são apagados inteiros (`drop_chunks`) às 3h17.
- **Totais por hora:** `monitor.batida_hora` soma no ar, fora (pendente conta como fora), manutenção e latência a
  cada batida. Dá a disponibilidade de 24 h, 30 dias e 1 ano sem varrer as batidas e fica 400 dias.
- **Estado atual:** fica no próprio monitor (estado, desde, última verificação, latência, mensagem, tentativas,
  certificado, chamado aberto).
- **Escopo:** `dept_id` (da unidade, se não informado) aplica o escopo de dados por unidade.

## Telão e Centro de Operações

- **Links e serviços:** `MonitoramentoLinksFonte` é a fonte real (`@Primary`) de `MonitorFontes.Links`, usada pela
  tela "Links e serviços".
- **Link de cada unidade no telão:** vem de `MonitorFontes.IndicadoresLink`.
  - Vale o pior estado entre os monitores com papel **principal** da unidade: fora → `offline`; pendente ou lento →
    `degradado`; senão `online`.
  - A disponibilidade é a média de 24 h desses monitores.
  - Unidade sem monitor principal aparece no ar.
- **Ocorrência:** a mudança vira ocorrência no telão na rodada seguinte, em até 5 s.
- **Simulação do MVP:** só envia pelo WebSocket os tipos que ainda não têm fonte real.
- **Batida ao vivo:** cada batida vai pelo WebSocket como `monitoramento-batida` (`{monitorId, estado, latenciaMs,
  msg, momento, importante}`).

## Unidades e mapa

O Kuma não tem localização. Em Monitoramento → Unidades, cada unidade (filial, a `monitor_unit` do telão) tem
departamento raiz, endereço e coordenadas:

1. **CEP:** traz logradouro, bairro, cidade e UF (`CO_TI_VIACEP_URL`, padrão ViaCEP).
2. **Número:** com o número, "Localizar no mapa" consulta o Nominatim/OpenStreetMap (`CO_TI_GEOCODIFICACAO_URL`), no
   máximo uma consulta por segundo, com o contato `CO_TI_GEOCODIFICACAO_CONTATO` no User-Agent (pedido da política
   de uso).
3. **Precisão:** tenta endereço com número, depois a rua, o CEP e a cidade. A tela diz quando a precisão ficou menor.
4. **Ajuste:** o ponto pode ser arrastado ou clicado no mapa.

Sem internet, deixe as duas URLs vazias e marque o ponto no mapa (ou digite as coordenadas).

## Importar do Uptime Kuma

Monitoramento → Monitores → "Importar do Uptime Kuma" (permissão `monitoramento:importar`, só do administrador até
ser concedida).

1. **Analisar:**
   - envie o `kuma.db` (SQLite da pasta `data` do Kuma, com o Kuma parado), em fluxo de até `CO_TI_KUMA_MAX_MB`
     (padrão 2048);
   - o arquivo é aberto só para leitura (sem extensões) e nada é gravado;
   - a tela mostra o que entra, o que não entra e o que precisa de atenção.
2. **Confirmar:** escolha os monitores, quantos dias de histórico (0, 1, 7 ou 30), se vêm os canais e as manutenções
   e, se quiser, uma unidade para todos. A análise vale 1 hora; o arquivo é apagado ao confirmar ou ao expirar.

| No Kuma | No CO-TI |
|---|---|
| `http`, `keyword`, `json-query`, `port`, `ping`, `dns`, `push`, `manual` | o tipo equivalente; intervalo, tentativas, intervalo de nova tentativa, reaviso, tempo limite, invertido, ignorar TLS, redirecionamentos, códigos aceitos, método, corpo, cabeçalhos, aviso de certificado e ativo/pausado |
| autenticação Basic | cabeçalho `Authorization` (aviso na análise) |
| consulta JSONata que não é caminho simples | monitor pausado, com a expressão na descrição para revisar |
| `push_token` | o mesmo token (os scripts continuam funcionando apontando para o CO-TI) |
| `group` | não vira monitor; o nome do grupo vai na descrição dos monitores dele |
| outros tipos (docker, mqtt, bancos, gRPC, navegador real…) | não entram (listados na análise) |
| notificação `telegram`, `matrix`, `webhook`, `smtp` | canal Telegram, Matrix, webhook ou e-mail (destinatários do SMTP; sai pelo servidor de e-mail do CO-TI); "padrão" e ligações com monitores |
| outras notificações | não entram |
| manutenção `manual`, `single`, `recurring-weekday` | manual, uma vez, toda semana (0 = domingo vira 7) |
| outras estratégias (cron, por intervalo, dia do mês) | não entram |
| `heartbeat` dos últimos dias | batidas e totais por hora; o monitor continua do estado em que o Kuma parou, sem aviso |

- **Duplicados:** monitor já importado (mesmo id no Kuma e mesmo nome) é ignorado. Só o id não basta: cada Kuma numera
  os monitores a partir de 1, e assim dá para importar mais de um Kuma.
- **Auditoria:** a importação vai para a auditoria.
- **Depois de importar:** em cada monitor, escolha a unidade, o papel no telão e se abre chamado.

## Permissões

| Permissão | Quem tem |
|---|---|
| `monitoramento:monitor:query` | NOC, Service Desk, SOC, gestor da unidade, gestão de TI |
| `monitoramento:monitor:update`, `monitoramento:manutencao:update`, `monitoramento:canal:update`, `monitoramento:unidade:update` | NOC |
| `monitoramento:importar` | ninguém (só o administrador) |

A configuração dos canais (destinatários) só aparece para quem pode configurar.

## Fora desta etapa

- Páginas de status públicas.
- Tipos SNMP, banco de dados, SMTP e grupo.
- Aviso por WhatsApp.
- Importar de um Kuma com MariaDB (só o `kuma.db` SQLite).
