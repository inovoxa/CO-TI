# Centro de Operações de TI sobre o RuoYi-Vue-Pro (yudao)

Este pacote adiciona ao RuoYi-Vue-Pro um telão para a área de **tecnologia e inteligência** de um órgão público ou de uma grande empresa, com:

- **mapa** das unidades e datacenters (Leaflet + OpenStreetMap), cada um colorido pelo status (verde / amarelo / vermelho);
- **estrutura organizacional** (diretoria → unidades → setores), usando os departamentos que você já cadastra no sistema;
- **indicadores ao vivo por unidade**, chegando pelo WebSocket do próprio projeto a cada 5 segundos:
  - **Rede:** link principal (online / degradado / fora do ar), latência e disponibilidade 24 h;
  - **Servidores:** quantos respondem, uso de CPU;
  - **Datacenter / sala técnica:** temperatura, energia (concessionária ou nobreak, com autonomia), ocupação do storage e resultado do backup;
  - **Suporte:** chamados abertos e com SLA vencido;
  - **Segurança da informação:** incidentes de nível alto em aberto;
- **feed de ocorrências** (queda de link, nobreak, temperatura, falha de backup, novo incidente, SLA estourado).

Os números começam **simulados**, para você ver tudo funcionando de ponta a ponta. Depois é só ligar nas ferramentas que a TI já usa (veja a parte 4).

> A pasta `preview/` tem a **prévia completa da plataforma** (abre direto no navegador), com todos os módulos planejados: visão geral, câmeras com IA, chamados, links, inventário, logs e estrutura. O código desta versão implementa o núcleo (telão + estrutura + tempo real); os módulos abaixo entram nas próximas etapas.

---

## Arquitetura da plataforma completa

O RuoYi-Vue-Pro é o **portal único**: login, perfis, árvore de unidades/departamentos/usuários, menus, auditoria e o WebSocket de tempo real. Cada módulo é um **conector** que lê uma ferramenta open source especializada, em vez de reescrevê-la. Assim a equipe ganha uma tela só, sem perder o que essas ferramentas já fazem bem.

```
                  ┌──────────────── Portal (RuoYi-Vue-Pro) ────────────────┐
 Active Directory → Estrutura: órgão → unidades → departamentos → usuários │
                  │ Perfis e permissões por unidade · Logs de auditoria    │
                  │ WebSocket /infra/ws → telão e telas dos módulos         │
                  └───────┬────────┬────────┬────────┬────────┬────────────┘
                          │        │        │        │        │
                       GLPI   Uptime Kuma  OCS    Graylog   Frigate (NVR + IA)
                     chamados   links   inventário  logs    câmeras e acesso
```

| Módulo | Ferramenta | Como o portal lê | Observações |
|---|---|---|---|
| **1. Chamados** | GLPI (ou o módulo BPM do próprio RuoYi) | API REST do GLPI | Monitoramento e logs abrem chamados automaticamente (queda de link, força bruta). |
| **2. Links e serviços** | Uptime Kuma | endpoint `/metrics` (Prometheus) e páginas de status em `/api/status-page/<slug>` | O Uptime Kuma **não tem API REST oficial**; a API interna (Socket.io) pode mudar entre versões. Para muitos links, o Zabbix é mais robusto. |
| **3. Inventário** | OCS Inventory NG | API REST do OCS (precisa ser habilitada no servidor) | Liga cada equipamento ao usuário da árvore; alerta sistema sem suporte e antivírus. |
| **4. Logs** | Graylog | API REST (busca, *streams* e alertas) | Recebe logs do AD, firewall, VPN, proxy e servidores via Syslog/GELF. Alertas do Graylog chamam um webhook do portal. |
| **5. Estrutura** | RuoYi-Vue-Pro + Active Directory/LDAP | nativo; sincronização com o AD | A ficha da pessoa junta equipamentos (OCS), chamados (GLPI) e acessos (Graylog). |
| **6. Câmeras com IA** | Frigate (NVR com detecção local) + controle de acesso | eventos via MQTT e API HTTP do Frigate | Conta veículos e pessoas que cruzam linhas virtuais; desde a versão 0.16 também lê placas. Funcionário/visitante vem do crachá e do cadastro da recepção. |

### Câmeras e LGPD (leia antes de ativar)

- **Contagem é anônima** e não identifica ninguém: pode ser usada com tranquilidade.
- **Placa de veículo** é dado pessoal quando ligada a um servidor: defina finalidade (controle de acesso/segurança) e prazo de retenção.
- **Reconhecimento facial** usa dado **biométrico**, que a LGPD trata como **dado sensível**. Só ative com base legal definida pela área jurídica, relatório de impacto (RIPD) e aprovação do encarregado de dados. Na prévia ele aparece **desligado** de propósito.
- Processe o vídeo **localmente** (Frigate roda on-premise, com acelerador como Coral/Hailo/OpenVINO) e não envie imagens para serviços externos.

### Ordem sugerida de implantação

1. Núcleo: portal, estrutura (com AD) e telão — **este pacote**.
2. Chamados (GLPI) — é o módulo que a equipe usa todo dia.
3. Links e serviços (Uptime Kuma ou Zabbix) — com abertura automática de chamado.
4. Inventário (OCS) — ligado à ficha de cada pessoa.
5. Logs (Graylog) — com alertas de força bruta e varredura.
6. Câmeras (Frigate) — depende de hardware, rede das câmeras e da análise LGPD.

---

## Mensagens em árvore: Matrix Synapse (pasta `integracoes/matrix/`)

O chat interno usa o **Matrix**, com o servidor **Synapse** e o aplicativo **Element** (web, desktop e celular). O Synapse é open source sob licença AGPLv3, mantido pela Element, e roda dentro da rede do órgão. Os **Spaces** do Matrix são agrupamentos de salas que podem conter outros spaces. Por isso, a estrutura do órgão vira uma árvore navegável:

```
▸ Diretoria de Tecnologia e Inteligência        space do órgão
   # Avisos gerais                              todos entram; só o gabinete publica
   ▸ Centro de Operações                        space
      ! NOC · alertas                           o portal posta as ocorrências aqui (bot)
      # SOC · segurança  # Plantão de TI  # Janela de mudanças
   ▸ Sede · Datacenter principal                 um space por unidade
      # Geral · Sede                            todos da unidade
      # Segurança da Informação (SOC) …         uma sala por departamento
   ▸ Regional Curitiba
      # Geral · Curitiba  # Atendimento ao Público  # Administrativo  # Suporte Local de TI
```

- Cada pessoa entra automaticamente no space do órgão, no space da sua unidade, na sala geral e na sala do seu departamento. Quem é de TI da sede também entra nas salas do Centro de Operações.
- As salas de departamento têm **criptografia de ponta a ponta** e entrada **restrita**: só entra quem é membro do space da unidade.
- **Federação desligada:** as salas existem só no servidor do órgão.
- Quando alguém muda de departamento na estrutura, a próxima sincronização coloca a pessoa na sala nova. Com `--remover`, ela também sai da antiga.

### Sincronizar a estrutura com o Synapse

```bash
cd integracoes/matrix
# 1) Ver o plano (não altera nada)
python3 sincronizar_synapse.py --dados ../../simulador/dados --dominio orgao.gov.br

# 2) Aplicar (token de um ADMINISTRADOR do Synapse)
export SYNAPSE_ADMIN_TOKEN=...
python3 sincronizar_synapse.py --dados ../../simulador/dados --dominio orgao.gov.br \
    --servidor https://matrix.orgao.gov.br --executar
```

- O script pode rodar quantas vezes você quiser: ele reconhece spaces e salas pelo endereço (`#bsb-geral:orgao.gov.br`) e não duplica nada.
- Na escala nacional, são 110 spaces, 441 salas e cerca de 150 mil entradas em salas, o que dá cerca de 185 mil chamadas à API. Antes de rodar, libere o limite de taxa do administrador: `POST /_synapse/admin/v1/users/@admin:orgao.gov.br/override_ratelimit` com `{"messages_per_second":0,"burst_count":0}`. Mesmo assim, o script espera e repete se o servidor pedir.
- **Login:** em produção, as pessoas entram com a conta de rede, via SSO (OIDC com Keycloak ligado ao Active Directory) ou com o módulo LDAP do Synapse. O script cria as contas sem senha, só para já colocá-las nas salas. Use `--senha-teste` **apenas** em ambiente de teste.
- No sistema real, troque os CSVs do simulador por uma exportação das tabelas `system_dept` e `system_users` com as mesmas colunas, ou agende o script para rodar todo dia.

### Alertas do portal no Matrix

O `MatrixNotifier.java` posta as ocorrências do telão na sala `#noc-alertas`, e as de segurança também em `#soc`. Ele é chamado pelo `MonitorPushJob` fora da thread do telão, então uma falha no chat nunca atrasa as telas. Configuração no `application.yaml`:

```yaml
monitor:
  matrix:
    enabled: true
    homeserver: https://matrix.orgao.gov.br
    token: ${MATRIX_BOT_TOKEN}            # token do usuário @centro-operacoes, NÃO do administrador
    sala-alertas: "#noc-alertas:orgao.gov.br"
    sala-seguranca: "#soc:orgao.gov.br"
    nivel-minimo: critico                  # "atencao" gera muito mais mensagens
```

A sala `#noc-alertas` é criada **sem** criptografia para que esse cliente simples possa postar. Para um bot em salas cifradas, use uma biblioteca com suporte a E2EE (por exemplo, `matrix-nio`) ou o proxy Pantalaimon.

Na prévia, o módulo **Mensagens** mostra essa árvore funcionando. O bot do NOC aceita os comandos `!status <unidade>`, `!chamado <descrição>` (abre chamado de verdade no módulo Chamados) e `!quem <nome>`. Na ficha de cada pessoa, em *Estrutura*, o botão "Conversar no Matrix" abre uma conversa direta.

---

## Dados simulados (pasta `simulador/`)

### Na prévia

No topo da prévia há o seletor **Escala**:

| Escala | Unidades | Pessoas | Equipamentos | Câmeras | Eventos/s no barramento |
|---|---|---|---|---|---|
| Piloto | 12 | ~150 | ~230 | ~40 | ~270 |
| Regional | 40 | ~2.600 | ~2.800 | ~240 | ~1.800 |
| Nacional | 108 | ~30.000 | ~30.500 | ~860 | ~16.000 |

O módulo **Plataforma e escala** mostra o volume por fluxo, o atraso da fila, as bordas guardando dados quando o link cai e o dimensionamento sugerido para cada escala.

### No sistema real: `gerar_dados.py`

Precisa só de Python 3.8+ (sem bibliotecas extras).

**Modo carga** — gera arquivos para popular o sistema e testar as telas:

```bash
cd simulador
python3 gerar_dados.py --escala nacional --saida dados --gz
```

| Arquivo | Para quê |
|---|---|
| `sql/01_estrutura.sql` | árvore de departamentos (`system_dept`) e unidades no mapa (`monitor_unit`). IDs a partir de 10000 para não colidir. |
| `usuarios.csv` | pessoas; importe pela tela *Usuários → Importar* (converta para .xlsx no modelo do sistema) |
| `equipamentos.csv` | inventário no formato do OCS (hostname, SO, hardware, antivírus, última comunicação) |
| `monitores.csv` | links e serviços para cadastrar no Uptime Kuma ou Zabbix |
| `chamados.csv` | chamados no formato do GLPI (prioridade, status, SLA) |
| `metricas.csv` | estado e latência de cada monitor a cada minuto, 24 h |
| `logs.jsonl` | logs no formato **GELF** do Graylog, 24 h com perfil de horário comercial |
| `eventos_cameras.jsonl` | veículos (placa e classificação), catracas, visitantes |
| `resumo.json` | contagens e taxas da escala gerada |

A escala nacional gera ~1 milhão de linhas de log e ~1,3 milhão de eventos de câmera em pouco mais de 1 minuto. Use `--logs N` para mais ou menos volume e `--seed` para repetir exatamente os mesmos dados.

**Modo fluxo** — eventos contínuos para teste de carga:

```bash
# Kafka (precisa do kcat): 5.000 eventos/s
python3 gerar_dados.py --escala nacional --fluxo --taxa 5000 | kcat -P -b localhost:9092 -t eventos

# Graylog: crie uma entrada "GELF UDP" na porta 12201 e envie os logs direto
python3 gerar_dados.py --escala regional --fluxo --gelf-udp 127.0.0.1:12201

# Teste de 60 s gravando em arquivo
python3 gerar_dados.py --escala nacional --fluxo --duracao 60 > teste.jsonl
```

Sem `--taxa`, o fluxo usa a taxa realista da escala (a mesma da prévia). Com `--taxa` maior, você mede até onde a instalação aguenta.

Todos os nomes, placas, IPs e hostnames são fictícios.

---

## Parte 1 — Subir o RuoYi-Vue-Pro

### O que instalar

| Ferramenta | Versão | Observação |
|---|---|---|
| JDK | 17 ou 21 | use o branch `master-jdk17` (recomendado). O branch `master` é JDK 8. |
| Maven | 3.8+ | ou o que vem no IntelliJ |
| MySQL | 5.7 ou 8 | porta 3306, usuário `root`, senha `123456` por padrão |
| Redis | 6+ | porta 6379, sem senha por padrão |
| Node.js | 18+ (LTS) | com `pnpm` (`npm i -g pnpm`) |
| IntelliJ IDEA | Community serve | o projeto não funciona bem no Eclipse |

> Dica Windows: dá para subir MySQL e Redis com Docker Desktop:
> ```bash
> docker run -d --name mysql -p 3306:3306 -e MYSQL_ROOT_PASSWORD=123456 mysql:8
> docker run -d --name redis -p 6379:6379 redis:7
> ```

### Backend

```bash
git clone https://github.com/YunaiV/ruoyi-vue-pro.git
cd ruoyi-vue-pro
git checkout master-jdk17
```

1. Crie o banco `ruoyi-vue-pro` e execute **somente** o arquivo `sql/mysql/ruoyi-vue-pro.sql`.
2. Se sua senha do MySQL/Redis for diferente, ajuste `yudao-server/src/main/resources/application-local.yaml`.
3. Compile uma vez na raiz:
   ```bash
   mvn clean install package -Dmaven.test.skip=true
   ```
4. Rode a classe `YudaoServerApplication` (módulo `yudao-server`).
5. Abra `http://127.0.0.1:48080` — se aparecer `{"code":401,...,"msg":"账号未登录"}`, está funcionando.

### Frontend (Vue 3 + Element Plus)

```bash
git clone https://github.com/yudaocode/yudao-ui-admin-vue3.git
cd yudao-ui-admin-vue3
pnpm install
pnpm dev
```

Acesse o endereço que aparecer no terminal e entre com o usuário administrador padrão indicado na tela de login / documentação do projeto.

> O sistema vem em chinês. Para trocar o idioma, use o seletor no canto superior (há inglês). Os nomes dos seus setores e unidades você mesmo cadastra em português.

---

## Parte 2 — Instalar o telão no backend

Copie a pasta `backend/yudao-module-system/src` deste pacote **por cima** da pasta `yudao-module-system/src` do projeto (só arquivos novos, nada é sobrescrito):

```
dal/dataobject/monitor/MonitorUnitDO.java
dal/mysql/monitor/MonitorUnitMapper.java
controller/admin/monitor/MonitorScreenController.java
controller/admin/monitor/vo/MonitorOverviewRespVO.java
controller/admin/monitor/vo/UnitKpiVO.java
controller/admin/monitor/MonitorModulosController.java      ← uma rota de API por módulo, cada uma com sua permissão
controller/admin/monitor/vo/MonitorOverviewRespVO.java
controller/admin/monitor/vo/UnitKpiVO.java
controller/admin/monitor/vo/OcorrenciaVO.java
controller/admin/monitor/vo/MonitorModuloVOs.java           ← chamados, links, equipamentos, logs, câmeras
service/monitor/MonitorKpiService.java
service/monitor/MatrixNotifier.java
service/monitor/fonte/MonitorFontes.java                    ← contrato de cada módulo (a "fonte" de dados)
service/monitor/fonte/FontesSimuladas.java                  ← implementação simulada de todas as fontes
job/monitor/MonitorPushJob.java
```

**Fontes trocáveis.** As telas e o controller só conhecem as interfaces de `MonitorFontes`. Para ligar uma ferramenta real, crie uma classe que implemente só a interface daquele módulo e marque-a com `@Primary`. Por exemplo, `GlpiChamados implements MonitorFontes.Chamados` usando a API do GLPI. O resto continua simulado até chegar a vez dele.

> Estes arquivos foram compilados com Java 21 contra Lombok, Spring 6, MyBatis-Plus e stubs das classes do yudao, e a simulação foi executada (100 rodadas consultando todas as rotas). Uma hora simulada com 108 unidades fica estável em cerca de 70% verde, 25% amarelo e 5–10% vermelho. Ainda falta rodar dentro do projeto completo.

Depois:

1. **Banco** — execute `backend/sql/monitor_unit.sql`. Descomente os `INSERT` de exemplo e troque os `dept_id` pelos IDs reais dos departamentos de cada unidade ou datacenter (tela *Gestão do sistema → Departamentos*, ou `SELECT id, name FROM system_dept`).

2. **Dependência do WebSocket** — no `yudao-module-system/pom.xml`, adicione (se ainda não existir):
   ```xml
   <dependency>
       <groupId>cn.iocoder.boot</groupId>
       <artifactId>yudao-spring-boot-starter-websocket</artifactId>
   </dependency>
   ```

3. **Ignorar multi-empresa nesta tabela** — a tarefa que empurra os dados roda fora de uma requisição, então a tabela não tem "empresa atual". Em `yudao-server/src/main/resources/application.yaml`, inclua `monitor_unit` na lista:
   ```yaml
   yudao:
     tenant:
       ignore-tables:
         # ...as que já existem...
         - monitor_unit
   ```

4. **JDK 8?** Se estiver no branch `master`, troque `jakarta.annotation.Resource` por `javax.annotation.Resource` nos três arquivos que usam (há um comentário indicando).

5. **Endereços das ferramentas embutidas** (opcional; sem isso as páginas Mensagens e Plataforma mostram instruções):
   ```yaml
   monitor:
     urls:
       element: https://portal.orgao.gov.br/element/                     # Element, no MESMO domínio do portal
       grafana: https://portal.orgao.gov.br/grafana/d/plataforma?kiosk   # painel da plataforma
   ```

6. Reinicie o backend. No log deve aparecer `fontes simuladas prontas: N unidades...` e a tarefa rodando a cada 5 s, sem erro.

---

## Parte 3 — Instalar as páginas no frontend

Cada item do menu é uma **página própria, com rota própria**. Elas não recarregam o navegador e compartilham:

- o **filtro de unidade**, guardado no estado global (Pinia) e no endereço (`?unidade=7`). Ao trocar de módulo, a unidade escolhida continua, e o link copiado já abre filtrado;
- **uma única conexão WebSocket**, em que cada página assina só o tipo de mensagem que lhe interessa;
- a **faixa de ocorrências críticas** no topo de todas as páginas.

```
/operacoes/visao-geral   mapa, indicadores e ocorrências de todos os módulos
/operacoes/cameras       contagens, imagens das câmeras (Frigate) e eventos de acesso
/operacoes/chamados      fila com SLA, filtros, busca e "Novo chamado"
/operacoes/links         últimas 40 verificações de cada link e serviço
/operacoes/inventario    estações e servidores, sistema sem suporte, antivírus
/operacoes/logs          busca com fluxo ao vivo
/operacoes/mensagens     Element (Matrix) dentro do portal
/operacoes/plataforma    painel do Grafana com a saúde da plataforma
/telao                   tela cheia para a TV, sem menu, girando entre 4 quadros a cada 30 s
```

1. Instale o Leaflet:
   ```bash
   pnpm add leaflet
   pnpm add -D @types/leaflet
   ```
2. Copie a pasta `frontend/src` deste pacote por cima da `src` do `yudao-ui-admin-vue3`. Só entram arquivos novos:
   ```
   src/api/monitor/index.ts                 chamadas de API e tipos
   src/store/modules/monitor.ts             estado compartilhado (unidade, indicadores, ocorrências)
   src/hooks/web/useMonitorSocket.ts        conexão WebSocket única
   src/views/monitor/components/            cabeçalho com filtro, indicador, mapa, utilitários
   src/views/monitor/<módulo>/index.vue     uma pasta por página
   src/router/modules/monitor-telao.ts      rota do telão (ver passo 4)
   ```
   Se você instalou a versão anterior, apague `src/views/system/monitorScreen` e `src/api/system/monitorScreen`.
3. **Menus e permissões:** execute `backend/sql/monitor_menus.sql`. Ele cria o diretório "Centro de Operações", as 8 páginas e as permissões de cada uma. Depois, em *Gestão do sistema → Perfis*, marque os menus de cada perfil. O fim do arquivo sugere perfis como NOC, Service Desk, SOC, Portaria e Telão.
4. **Telão:** abra `src/router/modules/remaining.ts` e acrescente a rota exportada em `monitor-telao.ts` ao array de rotas fixas. É uma rota fora do layout (sem menu) que continua exigindo login. Para a TV, crie um usuário com o perfil "Telão" e abra `/telao`. Parâmetros opcionais: `?unidade=7` e `?intervalo=20` (segundos por quadro).
5. Saia e entre de novo para carregar os menus.

> Todos os arquivos `.vue` e `.ts` foram compilados (script, template e SCSS) com o compilador do Vue 3.5. A checagem de tipos completa (`pnpm ts:check`) só é possível dentro do projeto.

### Se o "Ao vivo" não ficar verde

- Confira se `yudao.websocket.enable: true` no `application.yaml`.
- Compare com a tela de teste que já vem no projeto (*Infraestrutura → WebSocket*). Se ela conecta e o telão não, copie de `src/views/infra/webSocket/index.vue` a forma como a URL e o token são montados.
- Se rodar o backend em mais de uma instância, troque `yudao.websocket.sender-type` de `local` para `redis`.

---

## Parte 4 — Trocar a simulação por dados reais

Tudo o que é simulado está em **um único método**: `MonitorKpiService.lerIndicadores(...)`. A regra que pinta cada unidade de verde, amarelo ou vermelho fica em `calcularStatus(...)` e vale igual para dados reais; ajuste os limites (temperatura, latência, ocupação do storage...) ao acordo de nível de serviço da sua organização.

O telão não substitui as ferramentas de monitoramento: ele **consolida** o que elas já medem. As fontes abaixo são todas open source e comuns em órgãos públicos e grandes empresas:

| Indicador | Ferramenta | Como buscar |
|---|---|---|
| Link, latência, disponibilidade | **Zabbix** | API JSON-RPC (`item.get`, `problem.get`), filtrando por um *host group* por unidade |
| Servidores respondendo, CPU, storage | **Zabbix** ou **Prometheus** | mesmo *host group* da unidade |
| Temperatura, energia, autonomia do nobreak | **Zabbix** via SNMP dos sensores, PDUs e nobreaks | itens SNMP por sala técnica |
| Resultado do backup | **Bacula/Bareos**, Veeam etc. | API ou consulta ao catálogo da última janela |
| Chamados e SLA | **GLPI** (ou OTRS/Znuny) | API REST, filtrando por entidade/localização |
| Incidentes de segurança | **Wazuh** (ou outro SIEM) | API REST ou OpenSearch, alertas de nível alto por grupo de agentes |

Para não sobrecarregar essas ferramentas, consulte cada uma no seu próprio ritmo (por exemplo, Zabbix a cada 30 s, GLPI a cada 1 min) e guarde o último valor; a tarefa de 5 s só lê esse valor guardado e envia ao telão.

Para eventos que precisam aparecer **na hora** (incidente crítico, queda de link), use o webhook/alerta da própria ferramenta para chamar uma rota do sistema que dispara `webSocketMessageSender.sendObject(UserTypeEnum.ADMIN.getValue(), "monitor-ocorrencia", List.of(ocorrencia))`.

### Cuidados para órgão público / ambiente sensível

- Rode tudo **dentro da rede interna** (on-premise); o telão não precisa de acesso à internet, exceto pelo mapa. Para ambiente sem internet, use um servidor de mapas próprio (por exemplo, tiles do OpenStreetMap servidos localmente) e troque a URL em `iniciarMapa()`.
- Use o **controle de perfis** do RuoYi-Vue-Pro: quem vê só a sua unidade, quem vê tudo (permissões de dados por departamento).
- O sistema já registra **log de acesso e de operações**, útil para auditoria e LGPD.
- Os indicadores de segurança mostram **quantidade e nível**, não o conteúdo do incidente; os detalhes ficam na ferramenta de segurança, com acesso restrito.

---

## Próximos passos sugeridos

1. Tela de cadastro das unidades (dá para gerar com o **gerador de código** do próprio projeto a partir da tabela `monitor_unit`).
2. Integração real com Zabbix (o ponto de partida mais útil, porque cobre rede, servidores e datacenter).
3. Modo tela cheia para a TV da sala de operações, com rotação automática entre unidades.
4. Histórico de 24 h em gráfico (ECharts já vem no projeto) e relatório mensal de disponibilidade.
5. Escalonamento automático: notificar o plantão por e-mail, Telegram ou Teams quando uma unidade ficar vermelha.
