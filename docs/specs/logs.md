# Logs centralizados (no lugar do Graylog)

O CO-TI recebe os logs da rede, separa os campos e monta painéis no estilo do Graylog: abas, filtro para todos os
quadros, contadores com a variação do período anterior, rankings, mensagens e histograma. Módulo
`backend/yudao-module-logs`; telas em `frontend/src/views/logs`.

## Entradas

| Entrada | Porta no servidor | Uso |
|---|---|---|
| Syslog UDP | `CO_TI_SYSLOG_PORTA` (514) | FortiGate, switches, servidores Linux |
| Syslog TCP | `CO_TI_SYSLOG_PORTA` (514) | o mesmo, sem perda: com a fila cheia o CO-TI segura a conexão e quem manda espera |
| GELF UDP | `CO_TI_GELF_PORTA` (12201) | Windows pelo NXLog ou Winlogbeat (aceita pedaços, gzip e zlib) |

Dentro do contêiner as portas são 1514 e 12201 (o processo não roda como root). `CO_TI_LOGS_PERMITIDOS` limita quem
pode mandar (redes separadas por vírgula).

## Interpretação

- **Syslog** RFC 3164 (`<34>Oct 27 11:05:01 host programa[pid]: texto`) e RFC 5424 (com dados estruturados).
- **FortiGate**: o corpo chave=valor vira campos (`hostname`, `srcip`, `action`, `catdesc`...); o horário vem de
  `eventtime` (segundos a nanossegundos, conforme a versão do FortiOS).
- **Windows** (GELF ou JSON dentro do Syslog): normaliza `event_id`, `usuario` (TargetUserName, senão
  SubjectUserName), `computador`, `ip_origem`, `objeto`, `compartilhamento` e `acao` (nome do evento em português;
  4663 com permissão DELETE vira "Arquivo apagado"). Aceita os nomes do NXLog e do Winlogbeat.
- Data no futuro (relógio errado) ou ausente: fica o horário de chegada.

Cada mensagem cai em um **fluxo**: `firewall` (FortiGate), `contas` (logon e contas: 4624, 4625, 4740, 4720...),
`arquivos` (4656, 4660, 4663, 4670, 5140-5145) ou `maquinas` (o resto do Windows e o Syslog dos servidores).

## Armazenamento

`logs.mensagem`, hypertable do TimescaleDB com um pedaço por dia: `momento, recebido, fluxo, origem, ip, nivel,
aplicacao, mensagem, campos (jsonb)`. Índices: (fluxo, momento), (origem, momento) e GIN `jsonb_path_ops` nos campos.

- Gravação em lote pelo `COPY`, com 3 threads (`coti.logs.gravadores`); medido: cerca de 7.500 mensagens/s no
  Docker Desktop de uma estação.
- Compressão automática depois de 2 dias (cerca de 10 vezes menos espaço).
- Retenção: `CO_TI_LOGS_RETENCAO_DIAS` (30), apagando os pedaços diários às 03h47 em uma instância só. O
  `drop_chunks` roda pela função `coti_apagar_pedacos` (V45), dona da tabela, porque a aplicação conecta como
  `coti_app`. A mesma função corrigiu a retenção das batidas do monitoramento, que falhava por falta de permissão.

## Linguagem de busca

Como no Graylog: texto livre, `campo:valor`, `campo:"com espaço"`, `campo:abc*`, `campo:<=3`, `_exists_:campo`,
`AND`, `OR`, `NOT`, `-campo:valor` e parênteses (espaço entre termos = AND). Colunas próprias: `origem` (`source`),
`fluxo`, `aplicacao`, `ip`, `nivel` (`level`), `mensagem` (`message`). Tudo vira SQL parametrizado
(`dominio/Consulta.java`); igualdade em campo usa o índice GIN.

## Painéis

`logs.painel` guarda as abas; os quadros ficam num JSON por painel (`contador`, `mensagens`, `ranking`, `histograma`,
com consulta, fluxo, período, largura de 1 a 4 colunas, campo do ranking, campo distinto do contador e colunas das
mensagens). A V45 cria os painéis Contas, Máquinas, Arquivos e Firewall. Quem tem `logs:painel:editar` cria, edita,
duplica, move e exclui quadros e abas pela tela.

No valor de um ranking, de uma coluna ou de um campo da mensagem: mostrar as mensagens deste valor, adicionar ao
filtro, excluir dos resultados, copiar. Clicar numa barra do histograma mostra as mensagens daquele intervalo.

## Permissões

`logs:consultar` (painéis e busca), `logs:painel:editar`, `logs:fontes` (situação das entradas). SOC e Gestão de TI
têm tudo; NOC consulta e vê as fontes.

## Assistente de IA

Com `logs:consultar`, o assistente usa as mesmas consultas dos painéis, com a mesma linguagem e os mesmos períodos
(inclusive `hoje` e `ontem`, que contam do calendário e comparam com o dia anterior até a mesma hora):

- `contar_logs`: quantas mensagens (ou valores distintos, ex.: pessoas) e o período anterior para comparar;
- `ranking_logs`: valores mais frequentes de um campo (sites, contas, IPs, computadores, quem apagou);
- `buscar_logs`: exemplos de mensagens, com os campos separados;
- `paineis_logs`: os quadros dos painéis e a consulta exata de cada um.

Os logs não têm a unidade. Para "na UBS Vila Xavier", o assistente busca as máquinas da unidade no inventário,
descobre a rede (cada UBS tem a sua, ex.: 10.0.15.x) e filtra `srcip:10.0.15.*`; a troca de IP pelo DHCP não atrapalha
porque o IP continua na faixa. No ranking por `srcip`, `dstip`, `ip` ou `ip_origem`, cada IP já vem com a máquina, a
pessoa e a unidade do inventário (só para quem tem `inventario:ativo:query`) — é o dono **atual** do IP, não
necessariamente quem o usava no dia do log.

Conferido com o OpenRouter (DeepSeek): as 6 perguntas de teste (bloqueios do dia, sites mais acessados hoje, falhas de
logon da semana sem contas de computador, arquivos apagados na Saúde, quadro "Pornografia" e falhas de ontem)
bateram com o banco; depois, "IPs que mais acessam o YouTube na UBS Vila Xavier em 7 dias" e "usuários que mais
acessaram o YouTube na UBS Selmi Dei hoje" também, com máquina e pessoa certas.

Quando a resposta usou logs, aparece o botão **Ver detalhes da pesquisa** (janela flutuante e página do assistente).
Ele abre `/logs-pesquisa/detalhe` (`views/logs/analise`), no estilo do monitor do Redis: a pergunta, a consulta, o fluxo,
o período, o total e a variação; pizza dos 10 maiores, histograma, ranking (com máquina, pessoa e unidade quando o
campo é um IP; `GET /inventario/ativo/dos-ips`) e as mensagens mais recentes. Dá para trocar o campo agrupado e abrir na
busca.

## Consumo de banda

Contador, ranking e histograma aceitam `soma` (no quadro, no `POST /logs/contagem` e `/logs/ranking`, e como
`?soma=` no `/logs/histograma`): em vez de contar mensagens, somam um campo numérico. `bytes` = `sentbyte` +
`rcvdbyte` do FortiGate; também dá para somar só `rcvdbyte` (download) ou `sentbyte` (upload). Valor que não é número
inteiro conta 0. O ranking traz, por item, a soma e quantas mensagens (sessões) a formaram.

A banda certa vem do **log de tráfego** (`type:traffic`), gravado no fim da sessão com os bytes dela inteira; o log do
filtro web registra o acesso e somá-lo junto contaria o mesmo acesso duas vezes. Internet = `type:traffic AND
dstintfrole:wan`. No FortiGate, as regras de saída precisam de *Log Allowed Traffic: All Sessions* (configuração do
firewall, feita pela equipe de rede).

- A V46 põe no painel Firewall: Banda de internet (contador), Quem mais consumiu banda (por IP), Sites que mais consomem
  banda e Banda de internet ao longo do dia.
- O assistente usa `medir=bytes` em `contar_logs` e `ranking_logs`; se a consulta não diz o tipo, a ferramenta põe
  `type:traffic` sozinha, e o ranking por IP traz máquina, pessoa e unidade como nos acessos.
- A tela de detalhes tem "Medir: Mensagens | Banda": pizza, histograma e ranking passam a mostrar MB/GB e sessões.

Conferido: "quem mais consumiu banda na UBS Vila Xavier nos últimos 7 dias" (10.0.15.188, 2,5 GB = banco), "sites que
mais consumiram banda hoje" e "quanto a Netflix consumiu em 7 dias e quem mais usou" (213,8 GB = banco).

## Volume medido (simulador, 500 mil mensagens em 7 dias)

- Contadores, rankings e histogramas do dia: 40 a 260 ms cada; os 38 quadros dos 4 painéis respondem juntos.
- Busca por campo em 7 dias: cerca de 0,2 s. Texto livre varre as mensagens: cerca de 1,7 s em 7 dias. Prefira
  campos em períodos longos.
- 491 mil mensagens: 795 MB sem compressão; 294 MB com 5 dos 8 dias comprimidos.

## Simulador

`python ferramentas/logs/simular.py --historico 7 --volume 500000` preenche dias para trás (Syslog TCP);
`--ao-vivo --taxa 50` manda mensagens agora (Syslog UDP + GELF UDP, metade comprimida). Com `--inventario maquinas.csv` (nome;ip;login, consulta no
início do script) usa as máquinas, os IPs e os usuários do inventário, para os logs baterem com as unidades.
