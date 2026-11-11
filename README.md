# CO-TI — Centro de Operações de TI

Plataforma única para a TI de uma empresa ou órgão público, com filiais: chamados e governança (base GLPI), atendimento omnichannel com IA (base Chatwoot), inventário (compatível com os agentes OCS), monitoramento de links e serviços (base Uptime Kuma), logs (funções do Graylog), cofre de senhas e integração total com o Active Directory.

Cada empresa tem **uma instalação própria** (single-tenant). As filiais e unidades ficam dentro dela, na árvore de departamentos.

| Pasta | O que é |
|---|---|
| `backend/` | Spring Boot (RuoYi-Vue-Pro/yudao, Java 21). Módulos do CO-TI: `yudao-module-identidade` (AD); o telão do MVP está em `yudao-module-system/.../monitor` |
| `frontend/` | Vue 3 + Element Plus (yudao-ui-admin-vue3). Páginas do Centro de Operações em `src/views/monitor/` |
| `backend/yudao-server/src/main/resources/db/migration/` | Migrations Flyway do banco único PostgreSQL |
| `simulador/` | Gerador de dados e de carga (unidades, chamados, logs GELF, métricas) |
| `integracoes/matrix/` | Sincronização da estrutura com o Matrix Synapse (opcional) |
| `preview/` | Prévia navegável de todos os módulos planejados |
| `docs/` | Documentação; `docs/mvp-LEIA-ME.md` é o guia original do MVP |

## Subir com Docker

```bash
cp .env.example .env        # troque todas as senhas (openssl rand -base64 32)
# defina CO_TI_ADMIN_SENHA_INICIAL (mínimo 12): o backend não sobe enquanto o admin tiver a senha pública admin123
docker compose up -d --build
```

Depois abra `https://<servidor>:8443` (a porta de `CO_TI_HTTPS_PORT`) e entre como `admin`, com a senha inicial definida. Em seguida, remova `CO_TI_ADMIN_SENHA_INICIAL` do `.env`.

Serviços: `postgres` (PostgreSQL 16, TimescaleDB e pgvector), `redis`, `backend`, `frontend` (nginx, que publica o portal, a API `/admin-api` e o WebSocket `/infra/ws` na mesma origem) e `backup`.

### Instalar no Coolify (só com imagens)

O workflow **Imagens** publica `ghcr.io/inovoxa/co-ti-backend`, `co-ti-frontend` e `co-ti-postgres` a cada push no
`main` com o CI verde (`latest`) e a cada tag `vX.Y.Z` (`X.Y.Z`). Para um cliente novo não é preciso clonar nem
compilar nada no servidor dele:

1. Coolify → projeto → **+ New** → **Docker Compose Empty** → cole o [`docker-compose.coolify.yml`](docker-compose.coolify.yml).
2. No serviço **frontend**, defina o domínio (ex.: `https://ti.cliente.com.br`), com o DNS já apontando para o servidor.
3. **Deploy.** As senhas do banco e do Redis, a chave-mestra e a senha inicial do `admin` são geradas pelo Coolify
   (variáveis `SERVICE_*`, em *Environment Variables*). Entre como `admin` com a `SERVICE_PASSWORD_ADMIN`.
4. **Guarde a `SERVICE_REALBASE64_32_CHAVEMESTRA` fora do servidor**: sem ela os segredos cifrados (MFA, cofre)
   de um backup não abrem.

Nesse modo o frontend roda com `CO_TI_ATRAS_DE_PROXY=true`: o Traefik do Coolify faz o HTTPS (Let's Encrypt) e o nginx
serve tudo em HTTP na 80, guardando o IP real do cliente. Para fixar a versão num cliente, defina `CO_TI_VERSAO`
(ex.: `1.2.0`). Se os pacotes do GHCR forem privados, faça `docker login ghcr.io` no servidor com um token
`read:packages`. Os backups ficam em `/data/coolify/services/<uuid>/backups`.

### Instalar no servidor do órgão, com imagens prontas

Cada órgão tem a própria instalação. Com `docker-compose.imagens.yml`, o servidor recebe só as imagens: nem o
repositório nem acesso ao GitHub.

```bash
# na sua máquina, com o commit da versão feito
deploy/imagens/gerar-imagens.sh --versao 1.0.0 --publicar       # registro privado (docker login antes), ou
deploy/imagens/gerar-imagens.sh --versao 1.0.0 --exportar saida # arquivo para levar ao servidor

# no servidor, com docker-compose.imagens.yml e o .env na mesma pasta
docker load -i co-ti-1.0.0.tar.gz                               # só se veio por arquivo
docker compose -f docker-compose.imagens.yml --env-file .env up -d
```

No `.env`, além das variáveis de sempre, defina `CO_TI_VERSAO` e, se usar outro registro, `CO_TI_REGISTRO`. Para
atualizar, gere uma versão nova, troque `CO_TI_VERSAO` e rode o `up -d` de novo.

- **Backups e certificados:** ficam nos volumes `backups` e `tls`, não em pastas do servidor.
- **Credencial do registro:** use uma conta só de leitura, porque quem administra o servidor consegue vê-la.
- **Código-fonte:** entregar as imagens ao órgão é distribuir o software. Pela GPLv3, o código correspondente tem de
  ser oferecido a ele. O commit de cada versão está no rótulo `org.opencontainers.image.revision` das imagens.

### HTTPS

O portal só abre por HTTPS (TLS 1.2 e 1.3); o HTTP (`CO_TI_HTTP_PORT`) redireciona, menos os caminhos dos agentes de
inventário (`/ocsinventory`, `/front/inventory.php`, `/download/`) e do push do monitoramento (`/api/push/`), que
seguem nos dois. Ponha o certificado da empresa em `segredos/tls/cert.pem` (com a cadeia) e `segredos/tls/key.pem`
e reinicie o `frontend`. Sem ele, a primeira subida gera um **autoassinado** (2 anos) para os nomes de
`CO_TI_TLS_NOMES`: funciona, mas o navegador avisa. Para trocar, apague os dois arquivos ou substitua-os.
O microfone do assistente só funciona em HTTPS.

### Backup

O serviço `backup` faz, todo dia às `CO_TI_BACKUP_HORA` (02:30), um `pg_dump` do banco e um `.tar.gz` dos anexos em
`./backups`, guardando `CO_TI_BACKUP_DIAS` (14) dias. O último fica descrito em `backups/ultimo.json`. **Copie a
pasta para fora do servidor** (outro servidor, fita, nuvem) e guarde o `.env` à parte: sem a mesma
`CO_TI_CHAVE_MESTRA`, os segredos cifrados (MFA, cofre) do backup não abrem.

```bash
docker compose run --rm backup agora                       # um backup já
docker compose stop backend                                # restaurar (num banco vazio: volume pgdata novo)
docker compose run --rm backup restaurar coti-AAAAMMDD-HHMM.dump arquivos-AAAAMMDD-HHMM.tar.gz
docker compose start backend
```

Testado: restauração completa em ~1,5 min (150 mil chamados, 595 mil logs, anexos), conferindo tabela a tabela.

### Dados de demonstração

Num banco recém-criado, `python ferramentas/semente/semente.py` carrega uma prefeitura fictícia (Araraquara): secretarias,
escolas, UBS e CRAS como unidades no mapa, ~540 servidores e perfis, equipes, SLAs, categorias e o catálogo de serviços,
base de conhecimento, governança, ~500 equipamentos com softwares, monitores e conversas de WhatsApp, além de 3 anos de
chamados (~150 mil, com atores, acompanhamentos, soluções, histórico e pesquisa de satisfação), problemas ligados aos
chamados que os revelaram, uma mudança por semana (com janela, aprovação e plano de retorno) e projetos da TI com
tarefas. Leva cerca de 1 minuto.

- `--anos` e `--chamados` mudam o volume; `--seed` muda os dados (a mesma semente gera os mesmos dados).
- `--somente-chamados` e `--somente-gestao` refazem só uma etapa num banco já carregado (com a mesma `--seed`).
- Contas de demonstração: `tecnico.demo`, `coordenador.demo`, `noc.demo`, `gestor.demo` e `servidor.demo`, com a senha
  `CO_TI_SENHA_DEMO` do `.env` (o script cria uma na primeira vez). As demais pessoas não têm senha local.
- Os links das unidades entram pausados (o IP do roteador é fictício); os monitores do próprio CO-TI e do portal da
  Prefeitura verificam de verdade.
- Para recomeçar do zero: `docker compose down` e `docker volume rm coti_pgdata coti_redisdata coti_arquivos`.

### Volume e retenção

- **Rotinas em paralelo:** verificações, SLA, e-mail, WhatsApp e AD rodam num pool de `CO_TI_AGENDADOR_THREADS` (8);
  uma rotina demorada (ex.: sincronizar um AD grande) não para as outras.
- **Limpeza diária (03h37):** tokens de sessão expirados e logs de operação, acesso, API e erros mais antigos que
  `CO_TI_RETENCAO_LOG_*_DIAS`. Chamados, conversas, inventário e auditoria ficam sempre. As verificações do
  monitoramento têm retenção própria (`CO_TI_MONITORAMENTO_RETENCAO_DIAS`).
- **Buscas:** título de chamado e nome/login de pessoa usam índice trigram (`pg_trgm`). Os campos de pessoa buscam
  enquanto se digita (no máximo 30 resultados), em vez de baixar todos os usuários.
- **Referência medida** (150 mil chamados, 540 pessoas, banco de 400 MB): fila de chamados e busca por título em ~70 ms,
  painel de 12 meses em ~0,45 s.
- **Mais de uma instância do backend:** as rotinas se coordenam pelo Redis. Filas (e-mail, WhatsApp, webhooks, AD,
  SLA, monitores) reivindicam cada item, então todas as instâncias trabalham juntas; as rotinas de varredura e limpeza
  (sincronização do AD, SNMP, alertas do inventário, retenções) rodam em uma instância por vez. Para escalar, use
  `CO_TI_WS_SENDER=redis` (avisos ao vivo chegam a quem está em qualquer instância) e o mesmo volume de anexos para
  todas.

## Ligar o Active Directory

O caminho mais simples é a tela **Infraestrutura → Diretório LDAP (AD)**. Ela tem os mesmos campos do "Diretório LDAP" do GLPI: servidor, porta, BaseDN, RootDN, senha e filtro da conexão. A tela também tem:

- **Testar conexão:** testa a conta de serviço, conta quem o filtro deixa entrar e, se você informar um usuário, confere o login dele.
- **Buscar certificado do servidor:** mostra o certificado da CA do DC, para você conferir a impressão digital e confiar.
- **Sincronizar agora.**

O que for salvo na tela vale na hora, sem reiniciar, e passa a valer no lugar das variáveis abaixo. A senha da conta de serviço fica cifrada com a chave-mestra. A conexão precisa ser LDAPS (636) ou StartTLS (389).

Sem nada salvo na tela, valem as variáveis do `.env`:

```
CO_TI_AD_HABILITADO=true
CO_TI_AD_URL=ldaps://dc01.empresa.local:636,ldaps://dc02.empresa.local:636
CO_TI_AD_BIND_DN=CN=svc-coti,OU=Servicos,DC=empresa,DC=local      # conta só de leitura
CO_TI_AD_BIND_SENHA=...
CO_TI_AD_BASE_USUARIOS=OU=Empresa,DC=empresa,DC=local
```

- **Login:** a senha é conferida no AD (bind). Na primeira entrada, o usuário é criado no portal. Contas com vínculo ao AD **só** entram com a senha do AD. O `admin` local continua entrando mesmo com o AD fora do ar. O usuário pode ser digitado como no Windows: `joao.silva`, `EMPRESA\joao.silva` ou `joao.silva@empresa.local`; o domínio é descartado e vale a conta (`sAMAccountName`).
- **Estrutura:** as OUs abaixo de `CO_TI_AD_OU_RAIZ` (padrão: a base) viram departamentos debaixo do departamento `CO_TI_AD_DEPT_RAIZ_ID` (100, "Empresa").
- **Perfis:** em `coti.ad.grupos-perfis` (`application-coti.yaml`), cada grupo do AD é mapeado para um perfil do portal (`noc`, `service_desk`, `soc`, `portaria`, `gestor_unidade`, `telao`). Os grupos aninhados também valem.
- **Quem aprova pedidos:** só quem tem o papel **Aprovador** (dado à mão em Sistema → Usuários → Atribuir papel) ou está no grupo do AD de `CO_TI_AD_GRUPO_APROVADORES` (papel **Aprovador (grupo do AD)**, que entra e sai com a sincronização). Vale para o gestor do AD, o e-mail do superior digitado no formulário, o aprovador fixo, as regras e o pedido do técnico; e-mail de quem não é aprovador recusa o envio.
- **Sincronização completa:** a cada 30 min (`CO_TI_AD_CRON`). Quem sumiu do AD ou foi desativado lá é desativado no portal. Se mais de 20% das contas sumirem de uma vez, a sincronização não desativa ninguém e registra um erro, porque o mais provável é um filtro mal configurado.
- O certificado do DC precisa ser confiável. Use `CO_TI_AD_TRUSTSTORE` com a CA do AD; `ldap://` sem StartTLS é recusado.

## Catálogo de serviços e automação no AD

- **Catálogo:** em *Service Desk → Formulários → Importar*, carregue `exemplos/catalogo-servicos.json` (23 formulários:
  usuários e acessos, informática, saúde e telefonia). O usuário pede em *Catálogo de serviços*; o pedido vira chamado
  e passa pelas *Regras* (atribuição, categoria, urgência, pedido de aprovação).
- **Sistemas administrativos:** `exemplos/formulario-sistemas-administrativos.json` tem o formulário 5.1, de Araraquara:
  importe só nesse órgão. Ele fica em destaque no catálogo, abre um único chamado com todos os sistemas escolhidos e não
  aparece no chat, porque usa perguntas condicionais e múltipla escolha. Para converter outra exportação do GLPI 11, veja
  `ferramentas/formularios/converter_glpi.js`.
- **Manutenção predial:** os formulários 6.1 a 6.4 (iluminação e elétrica, ar-condicionado, extintores e segurança,
  outros serviços) entram sozinhos na atualização e abrem o chamado para a equipe *Manutenção predial*. Inclua as pessoas
  nessa equipe em *Service Desk → Equipes*; até lá, ninguém é avisado.
- **Estoque GTI** (menu próprio, para Service Desk e Gestão de TI): celulares, chips, HDs, monitores e outros, cada peça
  com identificação, ou memórias e cabos só pela quantidade. Guarda compra, nota, valor e garantia do item novo e quem
  usava o usado. A situação e o saldo só mudam por retirada, devolução, manutenção, retorno, entrada ou baixa, e tudo
  fica no histórico do item. Item não se apaga: sai por baixa. O movimento pode ser ligado a um chamado, gera o termo de
  responsabilidade para imprimir ou salvar em PDF e a lista sai em Excel e PDF. Retirada do 4.2 pede aprovação do gestor
  (regra *Retirada de celular corporativo*); sem gestor no AD, o chamado abre com um aviso para pedir à mão.
- **Técnico por área:** o perfil *Técnico* só vê os chamados atribuídos a ele, às equipes de que faz parte ou registrados
  por ele, em todas as telas, no painel, no Excel e no assistente. Cada área do catálogo tem uma equipe; inclua os técnicos
  em *Service Desk → Equipes*. Quem precisa ver tudo, como a triagem, usa um perfil com a permissão *Ver chamados de todas as
  equipes* (os perfis que já existiam a receberam). Chamado sem equipe, como o aberto sem categoria, fica com esses perfis.
- **Aprovação:** cada formulário define quem aprova (gestor do AD, e-mail informado no formulário ou uma pessoa fixa).
  O aprovador responde em *Minhas aprovações*; recusado, o chamado é fechado.
- **Automação no AD:** formulários com operação (criar usuário, resetar senha, reativar, desativar, acesso a pasta,
  transferência, criar e-mail) rodam o `deploy/ad/Executar_AD_Automatico.ps1` por SSH **depois** da aprovação.
  Configuração e instalação em [deploy/ad/README.md](deploy/ad/README.md). `CO_TI_AD_EXECUCAO=simulado` serve para
  demonstração. A senha provisória fica cifrada e o solicitante a vê uma única vez.
- A fila fica em *Service Desk → Execuções no AD*: erro de negócio não se repete sozinho; falha de rede tenta de novo
  em 1, 5 e 15 minutos.

## Base de conhecimento, e-mail e painel

- **Base de conhecimento** (menu próprio, todos os perfis): artigos com revisões, FAQ para todos e artigos internos para
  a equipe técnica, busca em português sem acento. Na abertura de chamado o portal sugere artigos; no chamado o técnico
  usa um artigo como solução ou transforma a solução em rascunho de artigo.
- **E-mail** (*Service Desk → Configuração → E-mail*):
  - *Envio:* conta SMTP (senha cifrada com a chave-mestra) e o endereço do portal para os links.
  - *Notificações:* evento por evento, quem recebe e o modelo. Quem fez a ação não recebe o aviso dela, e
    acompanhamento privado só vai para os técnicos.
  - *Caixas de entrada:* coleta por IMAP. E-mail novo vira chamado; resposta com `[CO-TI #123]` no assunto vira
    acompanhamento. Respostas automáticas e listas são ignoradas. Opcionalmente, só aceita remetente com DMARC/DKIM
    aprovado.
  - Para testar sem servidor de e-mail: `docker compose -f docker-compose.yml -f docker-compose.teste.yml up -d greenmail`
    (SMTP `greenmail:3025`, IMAP `greenmail:3143`, segurança "Nenhuma").
- **Painel** (*Service Desk → Painel*): abertos, vencidos, cumprimento de SLA, tempos médios, satisfação, série diária,
  categorias, prioridades, canais, técnicos e equipes. Respeita o escopo de unidade do perfil.
- **Arquivos** (anexos e imagens) ficam no volume `arquivos` do backend (`/app/arquivos`). O endereço de cada arquivo
  tem uma parte aleatória de 128 bits: o download não pede login (as imagens do editor precisam abrir), então o
  endereço é o que protege o arquivo.

## Problemas e mudanças

- **Problemas** (*Service Desk → Problemas*): a causa comum de vários chamados. Sintomas, causas e impactos; tarefas e
  solução. A solução pode solucionar de uma vez os chamados vinculados que ainda estão abertos.
- **Mudanças** (*Service Desk → Mudanças*): tipo padrão (pré-aprovada), normal (precisa de aprovação antes de ser
  aceita, testada ou aplicada) ou emergencial (aplicada já, aprovação depois). Janela de execução com aviso de
  conflito com outras mudanças, análise de impacto e planos de implantação, retorno e controle.
  - Aprovação: todos os aprovadores precisam aprovar; uma recusa recusa. O aprovador responde em *Minhas aprovações*
    sem precisar acessar mudanças. Reabrir uma mudança recusada pede uma nova rodada.
- No chamado, o técnico cria um problema ou uma mudança a partir dele, ou vincula a um existente.
- Os dois seguem o escopo de unidade do perfil, como os chamados.

## Governança

Menu próprio, com o novo perfil **Gestão de TI**. NOC e Service Desk consultam fornecedores, contratos, licenças e
projetos e atualizam as tarefas pelas quais respondem.

- **Fornecedores:** CNPJ (inclusive o alfanumérico, em vigor desde julho de 2026) ou CPF validados.
- **Contratos:** vigência, aviso prévio e renovação (não renova, tácita ou expressa). A situação é calculada:
  vigente, perto do prazo, vencido, encerrado. O responsável recebe e-mail antes do prazo, uma vez por prazo; a
  tarefa diária roda às 7h (`coti.itsm.governanca.alerta-cron`).
- **Custos e orçamentos:** custos lançados em contratos, projetos e licenças; o orçamento mostra quanto já foi
  usado e o saldo.
- **Licenças:** quantidade comprada, quem usa (pessoa ou equipamento), aviso de expiração e bloqueio de uso além
  do comprado (a não ser que seja permitido). A chave fica cifrada com a chave-mestra; só quem tem *Ver a chave da
  licença* a vê, e isso vai para a trilha de auditoria.
- **Projetos:** tarefas e subtarefas com cronograma (Gantt), marcos, horas, equipe, custos e vínculos com chamados,
  problemas e mudanças. O andamento é calculado pelas tarefas, ponderado pelas horas previstas.
- Contratos, licenças, orçamentos e projetos seguem o escopo de unidade do perfil.
- O pedido de aprovação de mudança agora também vai por e-mail para quem precisa aprovar.

## Atendimento (F2a)

Menu *Atendimento* (perfil novo **Atendente**; Service Desk e NOC também atendem).

- **Conversas** em três colunas e em tempo real: fila (minhas, sem atendente, todas; abertas, pendentes, adiadas,
  resolvidas), a conversa com notas internas, respostas prontas (digite `/` no campo) e anexos, e o painel do
  contato com os chamados dele e as conversas anteriores.
- **Atribuição automática** para quem está disponível, com limite de conversas por pessoa
  (`docs/specs/atendimento-atribuicao.md`). Quem responde uma conversa sem atendente fica com ela.
- **Abrir chamado** a partir da conversa: o solicitante é a pessoa do portal ligada ao contato (pelo e-mail ou
  pelo celular, com ou sem o nono dígito) ou, sem conta, o e-mail do contato.
- **Canal API** para integrar qualquer sistema: o sistema manda as mensagens para
  `POST /admin-api/atendimento/canal/api/mensagem` (cabeçalho `X-CO-TI-Token`) e recebe as respostas no webhook da
  caixa, assinadas (`X-CO-TI-Assinatura: sha256=<HMAC do corpo>`). Token e segredo aparecem uma vez só, ao gerar.
  Defina `CO_TI_URL_PUBLICA` para os links dos anexos saírem completos.

### Horário, macros e automações (F2c)

Regras completas em `docs/specs/atendimento-automacoes.md`.

- **Horário de atendimento:** a caixa usa um calendário do Service Desk (o mesmo do SLA, com expediente e
  feriados). Fora do expediente, a conversa que abre ou reabre recebe a *mensagem fora do horário*.
- **Macros** (*Atendimento → Macros*): sequência de ações executada numa conversa pelo botão de varinha.
  - As ações possíveis: atribuir, distribuir, etiquetas, mensagem, nota, situação e prioridade.
  - A macro pode ser pessoal ou para todos; a "para todos" exige *Macros para todos*.
- **Automações** (*Atendimento → Configuração → Automações*): quando um evento acontece (conversa criada, mensagem
  recebida, conversa reaberta, conversa resolvida) e todas as condições valem, as ações rodam.
  - Condições: caixa, canal, texto da mensagem, situação, prioridade, etiqueta, atendente, contato e horário.
  - O que uma automação faz não dispara outras, e cada execução fica registrada na conversa e na contagem da regra.
  - Os textos aceitam `{{contato.nome}}`, `{{conversa.id}}` e `{{atendente.nome}}`.
- **Pesquisa de satisfação:** com a opção ligada na caixa, ao resolver o CO-TI pergunta ao contato uma nota de 1 a 5
  pelo próprio canal.
  - Vale como nota uma resposta como "5", "nota 4", "3 estrelas" ou ⭐⭐⭐⭐, por até 3 dias. Ela fica na conversa
    resolvida, sem reabrir, e o contato recebe um agradecimento.
  - Qualquer outro texto é uma mensagem comum e reabre a conversa.
- **Relatórios** (*Atendimento → Relatórios*; perfis Gestão de TI, Service Desk e NOC):
  - abertas agora, conversas novas e resolvidas;
  - mediana e média da primeira resposta e da resolução, no total ou só em horário de atendimento;
  - mensagens trocadas e um mapa de quando os contatos escrevem (dia da semana × hora);
  - satisfação (média, % de notas 4 e 5, distribuição e últimas avaliações);
  - linhas por atendente e por caixa.

  Os tempos são gravados quando acontecem, então valem a partir desta versão.

### Fluxo do catálogo pelo WhatsApp (F2e) — substitui o agente do n8n

Análise do n8n e regras em `docs/specs/atendimento-fluxo-whatsapp.md`. Ligue *Fluxo do catálogo* na caixa.

- **Menu:**
  1. abrir um chamado (áreas e serviços do catálogo, com os códigos 1.1–4.2 e 6.1–6.4);
  2. consultar meus chamados;
  3. tirar uma dúvida (FAQ);
  4. falar com um atendente.
- **Formulários do catálogo:** são os mesmos do portal. As perguntas vêm uma por vez, com a validação do formulário
  (CPF, datas, e-mail, opções). Antes do envio, o contato confere um resumo e pode corrigir campos. Aprovação do
  superior e operação no AD funcionam exatamente como no portal.
- **Comandos** a qualquer momento: `menu`, `voltar`, `cancelar` e `atendente`.
- **Ajudas no preenchimento:** o texto livre sugere o serviço (pela IA, se estiver ligada; senão, pela busca no
  catálogo), e o CEP completa o endereço (ViaCEP; `CO_TI_VIACEP_URL` vazio desliga).
- **Segurança:**
  - só abre ou consulta pedidos quem tem o celular no cadastro (portal ou AD); os outros têm a FAQ e o atendente;
  - a consulta mostra apenas os chamados da própria pessoa;
  - os dados pessoais ficam cifrados durante o preenchimento e aparecem mascarados no resumo; depois do envio,
    também na conversa.
- **Senha provisória pelo canal:** quem esqueceu a senha não entra no portal. Depois do reset (ou da criação da conta)
  no AD, o solicitante responde **SENHA** e recebe a senha ali mesmo:
  - só ele recebe (o celular verificado é o de quem abriu o chamado), uma única vez, com registro na auditoria;
  - no histórico do CO-TI a mensagem aparece oculta; o texto real fica cifrado só até a entrega;
  - pedida de novo, o robô explica que já foi entregue e oferece um novo reset. Continua valendo também
    "Ver senha provisória" em "Meus chamados".
- **"Esqueci minha senha" na tela de login:** abre um assistente (caixa "Assistente da tela de login", canal
  `widget`) que faz as perguntas do **1.2 Reset de senha**, uma a uma, com a mesma validação do portal:
  - o visitante não está autenticado: o chamado sai em nome do dono do login informado, só se ele existir e estiver
    ativo; a conversa não consulta chamados, não usa outros serviços e não entrega senha;
  - o aprovador vê no pedido que ele veio da tela de login e para qual e-mail pessoal a senha vai;
  - aprovado e executado no AD, a senha provisória vai **por e-mail ao e-mail pessoal** do formulário (fica cifrada
    na fila só até o envio e é apagada do banco). Sem o envio de e-mails ligado (Service Desk → Configuração → E-mail), o chamado
    registra que a senha precisa ser entregue por outro meio;
  - para tirar o link da tela de login, desative essa caixa em Atendimento → Caixas de entrada.
- **Avisos de volta** (no lugar dos workflows 13, 14 e 20 do n8n), ligados por caixa:
  - o contato recebe o andamento (acompanhamentos públicos), a solução (inclusive a da operação no AD, sem senha
    no texto) e a decisão da aprovação do chamado que abriu;
  - o aprovador recebe o pedido de aprovação pelo celular do cadastro, com o link do portal;
  - no WhatsApp oficial, depois de 24 h sem mensagem do contato, o aviso vai pelo modelo aprovado escolhido na
    caixa (com uma variável). Sem modelo, o aviso não sai e fica registrado na conversa.
- **Áudio:** com um provedor de IA que tenha *modelo de áudio* (ex.: `whisper-1` na OpenAI ou na Groq), os áudios
  recebidos viram texto. O fluxo e o assistente entendem a mensagem falada, e quem atende lê a transcrição.

### Inteligência artificial (F2d)

Regras completas em `docs/specs/atendimento-ia.md` (reimplementação própria; nada da pasta `enterprise/` do Chatwoot).

- **Vários provedores** (*Infraestrutura → Inteligência artificial*):
  - OpenRouter, OpenAI, Anthropic, Google Gemini, Groq, DeepSeek, Mistral, Ollama local ou outro compatível com a
    API da OpenAI.
  - Cada um tem modelo, chave (cifrada), temperatura, limite e prioridade, e um botão de teste.
  - Cada uso tem o seu provedor principal e uma reserva opcional: o **Assistente IA** do portal, o **robô** do atendimento
    e o **copiloto**. O Assistente IA e o Atendimento ligam e desligam à parte e, com o principal escolhido, um nunca usa
    a credencial do outro. Sem principal, os provedores ativos respondem pela ordem de prioridade.
  - **IA local com Ollama no próprio servidor:** tipo *Ollama*, endereço `http://host.docker.internal:11434/v1`, que o
    docker-compose já resolve, e chave em branco. A chave só é necessária se houver um proxy na frente que exija uma.
    Em outra máquina, use o IP dela e suba o Ollama com `OLLAMA_HOST=0.0.0.0`. O modelo precisa aceitar ferramentas
    (*tools*), por exemplo `qwen2.5:7b` ou `llama3.1:8b`.
  - A tela mostra o uso (chamadas, erros e tokens) dos últimos 30 dias por provedor.
- **Assistente na caixa:**
  - A conversa nova fica com ele (pendente) e as respostas usam só os artigos FAQ da base de conhecimento.
  - Ele pode consultar e abrir chamados do próprio contato.
  - Passa para uma pessoa quando o contato pede, quando não sabe, ao atingir o limite de respostas ou quando os
    provedores falham.
  - Quando um atendente responde ou assume a conversa, o assistente sai dela.
- **Copiloto** na conversa: *Sugerir* (rascunho no campo), *Reescrever* (corrigir, mais formal, mais cordial, mais
  curto) e *Resumir* (vira nota interna). Nada é enviado sozinho.

### WhatsApp oficial, Instagram e Telegram (F2b)

Crie a caixa com o canal e clique na engrenagem para configurar as credenciais. "Testar conexão" confere o token e
liga o recebimento. Defina `CO_TI_URL_PUBLICA` (endereço público, com HTTPS): é para lá que os provedores mandam os
webhooks. Tokens e chaves ficam cifrados com a chave-mestra e nunca voltam para a tela. Os webhooks são gravados assim
que a assinatura confere e processados em seguida, numa fila. Falha passageira (como baixar uma mídia) é tentada de
novo, e mensagem repetida não duplica. O corpo bruto é apagado depois de 7 dias.

- **WhatsApp oficial (Cloud API):**
  - No app da Meta, informe o *Phone number ID*, o *WABA ID*, um token permanente de usuário do sistema
    (`whatsapp_business_messaging` e `whatsapp_business_management`) e a chave secreta do app.
  - Cadastre o webhook `https://<CO_TI_URL_PUBLICA>/admin-api/atendimento/canal/meta/webhook`, com o token de
    verificação mostrado na tela, e assine o campo `messages`.
  - Recebe texto, imagem, áudio, vídeo, documento, figurinha, localização, contatos, respostas de botão e reações.
    Manda texto e anexos (enviados antes à Meta) e mostra *enviada / entregue / lida*.
  - Abrir a conversa avisa a leitura ao contato.
  - **Janela de 24 h:** depois de 24 h sem mensagem do contato, a tela avisa e o envio passa a ser por **modelo
    aprovado**. Para isso, sincronize os modelos na configuração da caixa e use o botão *Modelo* na conversa.
    Mandar um modelo numa conversa resolvida a reabre.
- **Coexistência:** o mesmo número continua no aplicativo WhatsApp Business do celular. Ligue a opção e assine também
  os campos `smb_message_echoes`, `history` e `smb_app_state_sync`. Com isso:
  - o que o atendente responde pelo celular entra na conversa como *Pelo celular*;
  - o histórico compartilhado na conexão vira conversas resolvidas, com as datas originais;
  - os contatos da agenda do aplicativo viram contatos.
- **Instagram (Direct):**
  - Informe o token da conta profissional (API com login do Instagram) e a chave secreta do app. O teste preenche a
    conta.
  - Use o mesmo webhook da Meta, com os campos `messages` e `messaging_seen`.
  - O contato novo vem com nome e @usuário. O que a empresa manda pelo app do Instagram também entra como
    *Pelo celular*.
  - Anexos vão como link do CO-TI, por isso exigem `CO_TI_URL_PUBLICA`.
- **Telegram:** cole o token do @BotFather. "Testar e conectar" cadastra o webhook do robô, com um segredo que o CO-TI
  confere em cada chamada. Só conversas privadas com o robô.

## Inventário (F3a) — agentes OCS e GLPI Agent

Os agentes já instalados continuam como estão: basta apontá-los para o CO-TI.

- **Endereços:**
  - Agente OCS: `https://<portal>/ocsinventory`. No Windows: `/SERVER=https://<portal>/ocsinventory`.
  - GLPI Agent: `https://<portal>/front/inventory.php`, o mesmo caminho do GLPI.
  - Se o nome do servidor antigo passar a apontar para o CO-TI, nada muda nas máquinas.
- **Proteção** (no `.env`):
  - `CO_TI_INVENTARIO_REDES`: redes de onde os agentes podem mandar (CIDR). O padrão são as redes privadas.
  - `CO_TI_INVENTARIO_USUARIO` e `CO_TI_INVENTARIO_SENHA`: senha HTTP Basic opcional (`/USER` e `/PWD` no agente).
- **O que aparece em Inventário → Ativos:**
  - hardware, sistema (com alerta de sistema **sem suporte**), antivírus, softwares e peças;
  - o histórico do que mudou;
  - o usuário logado, ligado à pessoa do AD; a unidade do ativo segue a da pessoa.
- **Máquina repetida:** quando o agente é reinstalado, a máquina é reconhecida pelo UUID, pela série ou pelo
  MAC + nome.
- **Integração:**
  - o chamado ganha o bloco "Equipamentos";
  - o telão e a tela de inventário do Centro de Operações passam a usar os dados reais.

Detalhes do protocolo e das regras: `docs/specs/inventario-agentes.md`.

### Alertas e licenças (F3b)

- **Alertas:** Inventário → Alertas reúne sistema sem suporte, antivírus, disco quase cheio, computador sem
  comunicação e licença com mais instalações que o comprado.
  - Os alertas se encerram sozinhos quando a condição normaliza.
  - Cada regra pode abrir chamado; o chamado já vem ligado à máquina.
  - A reavaliação é a cada 15 minutos (`CO_TI_INVENTARIO_ALERTAS_MS`).
- **Licenças:** em Governança → Licenças, a aba "Instalações encontradas" diz quais softwares do inventário contam
  para a licença. A lista passa a mostrar quantas máquinas têm o software.
- Detalhes: `docs/specs/inventario-alertas-licencas.md`.

### Descoberta de rede (F3c)

- **Como funciona:** para cada sub-rede onde há agentes OCS, o CO-TI escolhe um agente. Esse agente varre a
  sub-rede e manda os IPs e MACs que respondem.
- **Onde ver:** Inventário → Rede mostra, por sub-rede, o que está cadastrado e o que é **desconhecido**. Um
  equipamento desconhecido pode virar ativo (impressora, switch, telefone IP...) ou ser ignorado com um motivo.
- **Alerta:** a regra "Equipamento desconhecido na rede" vem desligada.
- **Variáveis:**
  - `CO_TI_INVENTARIO_IPDISCOVER` liga ou desliga a descoberta;
  - `CO_TI_INVENTARIO_IPDISCOVER_REDES` limita as sub-redes varridas.
- Detalhes: `docs/specs/inventario-rede.md`.

### SNMP: impressoras, switches e o que não tem agente

- **Configurar:** em Inventário → SNMP, cadastre as credenciais (v2c ou v3).
- **Varredura:** a cada 6 horas o CO-TI consulta por SNMP os IPs descobertos na rede e os ativos cadastrados com IP.
  O botão "Consultar IP" faz isso na hora para um endereço.
- **O que entra no inventário:** fabricante, modelo, série, interfaces e, nas impressoras, o contador de páginas e o
  nível de cada toner (com alerta de toner acabando).
- **Ativo que já existe:** se a equipe já tinha cadastrado o equipamento, o SNMP atualiza esse cadastro e mantém o
  nome dado.
- Detalhes: `docs/specs/inventario-snmp.md`.

### Implantação de pacotes (teledeploy do agente OCS)

- **Onde:** Inventário → Pacotes.
- **Como funciona:** crie o pacote (executar um comando, abrir um arquivo ou só copiar), envie o zip ou tar.gz e
  escolha as máquinas ou uma unidade. O agente OCS baixa e executa no próximo contato, e a tela mostra o resultado de
  cada máquina.
- **Permissão:** só o administrador implanta, até alguém receber a permissão "Criar e implantar pacotes". Tudo vai para
  a auditoria.
- **HTTPS:** os agentes exigem o portal em HTTPS, com um certificado em que confiem.
- Detalhes: `docs/specs/inventario-pacotes.md`.

## Monitoramento (F4) — no lugar do Uptime Kuma

- **Onde:** Monitoramento → Monitores, Manutenções, Canais de aviso e Unidades.
- **Tipos:** HTTP(S), HTTP com palavra-chave, HTTP com consulta JSON, porta TCP, ping, DNS, push e manual. Cada
  monitor tem intervalo, tentativas antes de cair, tempo limite, "invertido" e limite de lentidão; o HTTP avisa também
  quando o certificado está perto de vencer.
- **Quando cai:** avisa pelos canais (e-mail, Matrix, Telegram ou webhook), abre chamado se o monitor pedir e, ao
  voltar, registra no chamado e o soluciona. Janelas de manutenção (uma vez, toda semana ou manual) suspendem
  verificação, aviso e chamado.
- **Telão:** o monitor marcado como **link principal** de uma unidade define a cor dela no mapa; a tela "Links e
  serviços" do Centro de Operações e o assistente passam a mostrar os monitores reais.
- **Unidades no mapa:** o CEP preenche o endereço (ViaCEP), o número completa e as coordenadas vêm do OpenStreetMap
  (Nominatim); dá para ajustar o ponto no mapa. Em rede sem internet, deixe `CO_TI_VIACEP_URL` e
  `CO_TI_GEOCODIFICACAO_URL` vazios e marque o ponto à mão.
- **Importar do Uptime Kuma:** em Monitores → "Importar do Uptime Kuma", envie o `kuma.db` (pasta `data` do Kuma,
  com o Kuma parado). Entram os monitores dos tipos acima, os canais de aviso, as manutenções e, se quiser, até 30 dias
  de histórico. Os monitores push mantêm o token: os scripts que chamam `/api/push/<token>` continuam funcionando
  apontando para o CO-TI. Depois, em cada monitor, escolha a unidade, o papel no telão e se abre chamado. Só o
  administrador importa, até alguém receber a permissão "Importar do Uptime Kuma".
- **Dados de demonstração:** `python exemplos/kuma/gerar_kuma_ficticio.py kuma-ficticio.db --pausar-ficticios` gera um
  `kuma.db` fictício (links de 8 unidades, sistemas, canais, manutenções e 30 dias de histórico com quedas) para testar
  a importação. Com `--pausar-ficticios`, os monitores de alvo inventado entram pausados, com o histórico; os
  "CO-TI ·" verificam os serviços do próprio docker-compose.
- **Importar da planilha de faixas de IP:** `python ferramentas/unidades/importar_planilha.py ip_unidades.csv >
  importar.sql` gera o SQL que troca **todos** os monitores e unidades pelos da planilha (colunas `id, faixa_ip,
  nome_unidade, logradouro, localizacao_geografica`): uma unidade por linha, com departamento na secretaria deduzida do
  nome, e um monitor de ping no `.1` da faixa. Faça backup antes; o modo de aplicar está no topo do script.
- **Faixas do Docker × rede monitorada:** o Docker usa por padrão `172.17.0.0/16` (ponte `docker0`) e `172.18+` nas
  redes do compose. Se a rede da Prefeitura usa as mesmas faixas, o ping de dentro do container não sai da máquina e
  a unidade aparece fora do ar. A rede do CO-TI fica em `CO_TI_REDE_DOCKER` (padrão `192.168.240.0/24`); a ponte
  padrão se muda no Docker (Docker Desktop → Settings → Docker Engine, ou `/etc/docker/daemon.json` no Linux):
  `"bip": "192.168.241.1/24", "default-address-pools": [{"base": "192.168.242.0/23", "size": 26}]`, e reinicie o Docker.
- **Retenção:** cada verificação fica `CO_TI_MONITORAMENTO_RETENCAO_DIAS` dias (padrão 31) no TimescaleDB; os totais
  por hora (disponibilidade de 30 dias e 1 ano) ficam 400 dias.
- Detalhes: `docs/specs/monitoramento.md`.

## Logs centralizados — no lugar do Graylog

O CO-TI recebe Syslog (UDP e TCP, porta `CO_TI_SYSLOG_PORTA`, 514) e GELF (UDP, `CO_TI_GELF_PORTA`, 12201), separa os
campos do FortiGate e dos eventos do Windows e monta painéis no estilo do Graylog em **Logs → Painéis**: abas
(Contas, Máquinas, Arquivos, Firewall), filtro para todos os quadros, contadores com variação, rankings e mensagens.
Clique num valor para ver as mensagens dele, somá-lo ao filtro ou excluí-lo. **Logs → Busca** tem a linguagem do
Graylog (`event_id:4740`, `hostname:*youtube* AND -action:blocked`) e **Logs → Fontes** mostra as entradas e como
configurar o FortiGate, o NXLog e o rsyslog.

- Guarda `CO_TI_LOGS_RETENCAO_DIAS` (30) dias; comprime depois de 2 dias (cerca de 10 vezes menos espaço).
- Para volume alto (firewall), mande por **TCP**: se a gravação atrasar, o CO-TI segura a conexão em vez de descartar.
- Libere as portas no firewall do servidor (514 TCP/UDP e 12201 UDP).
- Para testar: `python ferramentas/logs/simular.py --historico 7 --volume 500000` ou `--ao-vivo --taxa 50`.

Detalhes em `docs/specs/logs.md`.

## Assistente interno de IA

O menu **Assistente IA** é um chat para perguntar em português sobre a TI da organização. Exemplos: "quantos
computadores ainda usam Windows 10 na Saúde?", "qual o status do link da UBS Centro?", "o que está pendente no
chamado #123?".

- **O que consulta:** inventário, alertas, licenças, chamados, problemas, mudanças, conversas, base de conhecimento,
  pessoas, lista de ramais e telefones, monitores, links e logs.
- **Com quais permissões:** as de quem pergunta, incluindo o escopo de unidade.
- **Só leitura:** ele não altera nada.
- **Configuração:** usa o provedor de IA escolhido para o Assistente em Infraestrutura → Inteligência artificial (OpenRouter, OpenAI,
  Ollama local etc.).
- Detalhes: `docs/specs/assistente-interno.md`.

## Importar do GLPI 10/11

*Service Desk → Configuração → Importar do GLPI* (só o administrador). Lê o banco MySQL/MariaDB do GLPI e traz:
pessoas, grupos técnicos, categorias, origens, chamados (com acompanhamentos, tarefas, soluções, aprovações,
satisfação e anexos), base de conhecimento, problemas, mudanças, fornecedores, orçamentos, contratos, licenças e
projetos.

1. No servidor do GLPI, crie um usuário só de leitura:
   `CREATE USER 'coti'@'%' IDENTIFIED BY '...'; GRANT SELECT ON glpi.* TO 'coti'@'%';`
2. Para trazer os anexos, copie a pasta `files/` do GLPI para `importacao/glpi-files` ao lado do
   `docker-compose.yml` (montada só leitura no contêiner).
3. *Verificar* mostra o que há lá, quem já existe aqui e como as entidades viram unidades (pelo nome). Nada é gravado.
4. *Importar* roda em segundo plano. Dá para rodar de novo: o que já veio é pulado.

Como fica:
- Chamados, problemas e mudanças mantêm o número do GLPI (se o número estiver livre aqui).
- Pessoas que não existem aqui (pelo login ou pelo e-mail) entram **desativadas**; ativam quando entrarem pelo AD ou
  quando o administrador ativar. Solicitante sem conta fica pelo e-mail.
- Nenhuma notificação é enviada.
- A senha do banco do GLPI não é gravada. As chaves das licenças entram cifradas.
- Não vêm: políticas de SLA (os prazos de cada chamado vêm), regras, modelos, respostas de formulários do GLPI 11,
  inventário (computadores entram na F3), plugins.
- As datas do GLPI são lidas no fuso do servidor do CO-TI.

## Segurança

- **Verificação em duas etapas (TOTP):** cada usuário ativa em *Meu perfil → Verificação em duas etapas*.
  - Funciona com qualquer aplicativo autenticador. Recebe 10 códigos de recuperação de uso único.
  - O mesmo código não serve duas vezes. O desafio do login vale 5 minutos e 5 tentativas.
  - Quem perde o celular e os códigos: o administrador usa *Usuários → Mais → Redefinir verificação em duas etapas*.
- **Chave-mestra (`CO_TI_CHAVE_MESTRA`):** cifra os segredos do MFA e o cofre com AES-256-GCM. **Guarde uma cópia fora do servidor.** Sem ela, quem tem MFA precisa recadastrar o celular e as senhas do cofre não abrem mais.
- **Trilha de auditoria** (*Sistema → Auditoria do sistema → Trilha de auditoria*):
  - registra login, falhas, MFA, mudanças vindas do AD e a senha inicial;
  - é encadeada por SHA-256: o botão *Verificar integridade* aponta a primeira linha alterada ou removida;
  - o banco recusa `UPDATE`/`DELETE`/`TRUNCATE` nela.
- **Dois papéis no PostgreSQL:**
  - `coti` é o dono das tabelas e só roda as migrations;
  - a aplicação conecta como `coti_app`, que só lê e grava dados e não consegue alterar a estrutura nem a auditoria.
- **Logins alternativos desligados:** auto-cadastro, SMS e login social do RuoYi ficam desligados (`coti.seguranca.login-alternativo`). O `grant_type=password` do OAuth2 é recusado para quem tem MFA.
- **Dados que não ficam guardados:**
  - o navegador não guarda a senha ("lembrar" guarda só o usuário);
  - o log não grava senha nem token, e o SQL fica fora do log (para investigar: `LOGGING_LEVEL_CN_IOCODER_YUDAO=debug`);
  - senhas locais com bcrypt de custo `CO_TI_BCRYPT_CUSTO` (12); hashes antigos continuam valendo;
  - o painel `/druid` fica desligado.
- **IP real:** o nginx sobrescreve `X-Forwarded-For` com o IP da conexão. Atrás de outro proxy, configure o módulo `realip`.

## Relatórios em planilha e PDF

- **Excel:** botão nas telas *Painel* (uma aba por indicador), *Chamados* e *Inventário → Ativos*. Sai com os filtros da
  tela e o escopo de unidade do perfil, até 50 mil linhas.
- **PDF:** botão *PDF* no *Painel*, pela impressão do navegador ("Salvar como PDF"), com os gráficos.
- **Quem pode:** permissões `itsm:chamado:exportar` e `inventario:ativo:exportar`. Quem já consultava recebeu a permissão
  na atualização; retire do perfil se não quiser. Cada exportação de Excel fica na trilha de auditoria.

## Cofre de senhas

Menu *Cofre de senhas*, para as senhas de equipamentos e sistemas da equipe de TI. Dê o papel **Cofre de senhas** a quem
vai usar (Sistema → Usuários → Atribuir papel).

- **Pastas:** cada uma tem membros, escolhidos pessoa a pessoa, com um de três papéis: vê, edita ou gerencia. Sem ser
  membro, ninguém vê a pasta. O administrador do sistema decide os membros de qualquer pasta, mas só lê uma senha se
  incluir a si mesmo, e essa inclusão fica na auditoria.
- **Proteção:** senha e notas ficam cifradas com a chave-mestra (AES-256-GCM), ligadas à linha do segredo. A lista nunca
  traz o valor. Sem a mesma `CO_TI_CHAVE_MESTRA`, um backup restaurado não abre as senhas.
- **Ver uma senha:** exige a verificação em duas etapas ativa na conta (`CO_TI_COFRE_EXIGIR_MFA=false` desliga) e fica na
  trilha de auditoria (`cofre.senha_vista`). A janela fecha sozinha em 60 s.
- **Saída de alguém:** tirar a pessoa da pasta não muda as senhas que ela já conhece. Troque essas senhas; a auditoria
  mostra quais ela viu.
- **Ainda não tem:** histórico de versões da senha, compartilhamento por equipe ou grupo do AD e aviso de troca periódica.

## Ramais e telefones

Menu *Ramais e telefones*: lista única para todas as unidades, com busca por nome, setor, unidade ou número (os números são
comparados pelos dígitos, então `33011234` acha `(16) 3301-1234`). No celular, tocar no número abre a ligação.

- **Ligada às unidades:** o contato aponta para uma unidade do monitoramento. Os contatos também aparecem no painel da
  unidade, no mapa, e no detalhe do chamado (pela unidade a que o setor do chamado pertence).
- **Link fora:** sem rede na unidade, o ramal VoIP para junto. A lista avisa, risca ramal e fixo e põe o celular e o
  WhatsApp primeiro. O estado vem do link principal da unidade no monitoramento.
- **Contato de emergência:** quem procurar primeiro quando a rede cai. Exige unidade e celular. Sem energia ou internet na
  unidade, o celular só funciona pelo plano de dados, então cadastre o número antes de precisar.
- **Quem pode:** todos os papéis que veem a base de conhecimento consultam (`itsm:telefone:query`). Service Desk e Gestão
  de TI mantêm a lista (`itsm:telefone:update`).
- **Dados pessoais:** a lista é mantida à mão e não puxa o celular do cadastro de usuários. Ramal e fixo aparecem para todos;
  celular e WhatsApp só para NOC, Service Desk, SOC, Gestão de TI e Atendente (`itsm:telefone:celular`), inclusive na busca
  por número. O celular marcado como corporativo aparece para todos. Só inclua número pessoal com autorização.
- **Planilha:** *Baixar modelo* e *Importar planilha* (.xlsx). As colunas são achadas pelo título, em qualquer ordem, e a
  unidade precisa ter o nome igual ao da aba *Unidades* do modelo. O mesmo nome na mesma unidade atualiza o contato; a coluna
  que não vier mantém o valor cadastrado; a linha com erro é recusada e aparece no resultado, sem parar as outras.
- **Ainda não tem:** exportação.

## Instalar no celular (PWA)

O portal pode ser instalado na tela inicial do celular ou do computador, com atalhos para *Minhas aprovações* e *Meus
chamados*: no Chrome/Edge, *Instalar app*; no Safari do iPhone, *Compartilhar → Adicionar à Tela de Início*.

- **Precisa de HTTPS com certificado confiável:** com o autoassinado, o portal funciona, mas o navegador não instala.
- **Nada de dados no aparelho:** só os arquivos estáticos do portal ficam em cache. Sem rede, aparece uma página avisando;
  chamados e aprovações precisam de conexão.
- **Atualização:** a versão nova chega ao abrir o portal. Se mudar `public/sw.js`, troque `VERSAO` nele.
- **Ainda não tem:** notificação no celular. O aviso de aprovação continua por e-mail e WhatsApp.

## Idioma

A interface e as mensagens estão em português (padrão) e inglês.

Para o texto herdado do RuoYi há uma ferramenta em `ferramentas/traducao/`. Ela troca só strings e textos de tela, nunca comentários. Os dicionários ficam em `ferramentas/traducao/dicionarios/*.json`. Depois de trazer uma atualização do RuoYi:

```bash
cd ferramentas/traducao
python traduzir.py aplicar     # reaplica as traduções
python traduzir.py extrair     # lista em pendentes.json o que chegou de novo
```

Menus e dicionários do banco são traduzidos pela migration `V11__traducao_ptbr.sql`. Os módulos do RuoYi que o CO-TI não usa (loja, ERP, CRM, BPM, multi-empresa, SMS etc.) ficam escondidos.

## Desenvolvimento

Requisitos: JDK 21, Maven 3.9, Node 22+ com pnpm 10, Docker.

```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d postgres redis   # expõe 55432 e 6379 em 127.0.0.1
cd backend && mvn install -DskipTests -pl yudao-server -am
set -a; . ../.env; set +a
CO_TI_DB_URL=jdbc:postgresql://127.0.0.1:55432/coti java -jar yudao-server/target/yudao-server.jar --spring.profiles.active=coti

cd frontend && pnpm install && pnpm dev        # .env.local aponta para http://localhost:48080
```

Testes do módulo de identidade (servidor LDAP em memória com a forma de um AD):

```bash
cd backend && mvn test -pl yudao-module-identidade
```

## Sino

Avisa no portal, sem depender de e-mail: chamado novo, atribuído, andamento, solução, fechado, reaberto, pendente, escalonado,
aprovação pendente, aprovado e recusado. Andamento privado só vai para os técnicos. Os técnicos e as equipes do chamado
também recebem um aviso quando o prazo de SLA entra na última hora (`coti.itsm.sla-aviso-minutos`, padrão 60), uma vez por
prazo; chamado já vencido na atualização não gera aviso.

## Chat interno

Menu *Chat* e botão no cabeçalho, com o contador de não lidas. Todo servidor com login pode conversar com outro e criar
grupos. Quem cria é o administrador do grupo, e quem entra depois só vê as mensagens a partir daí.

- **Recursos:** conversas diretas e grupos, não lidas, quem está online, respostas, @menções em grupos, edição, busca nas
  conversas da pessoa, anexos de até 25 MB e aviso no navegador.
- **Entrega:** cada mensagem é gravada antes de ser avisada e tem um número sequencial. Ao reconectar, a tela busca o que
  faltou, e reenviar não duplica.
- **Privacidade:** não há criptografia de ponta a ponta, e quem administra o servidor consegue ler o banco. Mensagem apagada ou
  editada some da tela, mas o texto anterior fica guardado para apuração. Nada é apagado automaticamente; defina a retenção
  com o jurídico e o encarregado da LGPD do órgão.
- **Anexos:** só imagens, PDF, Office, texto e ZIP; o conteúdo precisa combinar com a extensão, e só quem participa da conversa
  baixa. Ficam em `arquivos/chat`, no mesmo volume dos anexos, e entram no backup.
- **Mais de um backend:** use `CO_TI_WS_SENDER=redis`.

## Acesso rápido

Menu *Acesso rápido* e opção no menu da foto de perfil. Links do órgão, que todos veem e o administrador cadastra, e links
pessoais, até 10 por pessoa, que só o dono vê. Para delegar os links públicos, inclua *Gerenciar links públicos* em outro perfil.
Só endereços http e https são aceitos, inclusive de sistemas internos; os links abrem em outra aba.

## Infraestrutura

Menu só do administrador.

- **Visão geral:** quanto cada área ocupa, quanto cresce por dia e a previsão até o espaço acabar, com aviso no sino em 80% e 90%.
  Os prazos de retenção e os limites ficam na mesma tela e valem sobre as variáveis de ambiente.
  - Quando o servidor informa um disco maior que o real, como no Docker Desktop, defina o *Espaço reservado ao CO-TI*
    (`CO_TI_DISCO_LIMITE_GB`).
  - Os logs centralizados têm teto próprio (`CO_TI_LOGS_LIMITE_GB`, padrão 50): acima dele, os dias mais antigos saem antes do
    prazo, sempre preservando 7.
  - Os logs de arquivo do backend têm teto de 1 GB.
  - As verificações do monitoramento são comprimidas depois de 2 dias.
- **Rotinas:** última execução, duração e último erro de cada rotina agendada, inclusive das que só escrevem o erro no log.
- **Formulários:** a mesma tela de antes, agora aqui.
- **Integrações:** entradas por webhook, chaves de API e webhooks de saída.

O conteúdo das requisições não vai mais para o log: só o endereço. Para depurar, suba com `LOGGING_LEVEL_CN_IOCODER_YUDAO=debug`;
mesmo assim, o conteúdo do cofre nunca aparece.

### Desligamento vindo do RH

O sistema do RH chama `POST /admin-api/integracao/entrada/<código>`, e o CO-TI:

1. identifica a pessoa no AD;
2. abre o chamado do formulário **1.4 Desativação de usuário**, sem aprovação;
3. bloqueia a conta;
4. desativa a conta no portal e derruba as sessões;
5. fecha o chamado.

**Autenticação:** cabeçalhos `X-COTI-Timestamp` (segundos desde 1970) e `X-COTI-Signature`, que é o HMAC-SHA256 em hexadecimal de
`timestamp.corpo`, com o segredo mostrado ao criar a entrada. Pedidos com mais de 5 minutos são recusados. Para sistemas que não
assinam, há o token fixo (`Authorization: Bearer`), que exige a lista de IPs.

**Travas:**
- A entrada nasce em **simulação**, que só registra o que faria. Depois, passa para **com confirmação**, em que um técnico confirma
  em *Execuções no AD*, e por fim para **automático**.
- Sem uma conta única para o identificador, com conta protegida (`admin`, grupos e logins da lista) ou com nome divergente, o chamado
  vai para a equipe e o AD não é tocado.
- Acima do limite diário de bloqueios automáticos (padrão 5), os próximos esperam confirmação, e o administrador é avisado.
- Data de efetivação futura agenda o bloqueio. Um aviso com a situação de cancelamento, como uma readmissão, cancela o que ainda
  não rodou.
- Evento repetido não abre outro chamado.

**Dados pessoais:** o conteúdo recebido fica cifrado com a chave-mestra e é apagado depois de 90 dias. CPF e data de nascimento não
entram no chamado. Use *Testar* na tela, com um JSON de exemplo, antes de ligar.

**Script do AD:** atualize o `Executar_AD_Automatico.ps1` no servidor Windows. O pedido automático usa `somente_login=true`, que
busca só o login exato (sem `Name -eq`), e a resposta traz os grupos removidos e as pendências.

### API e webhooks de saída

- **Chaves de API:** dão acesso a `POST /admin-api/integracao/v1/chamados` (formulário + respostas, com `Idempotency-Key`) e a
  `GET /admin-api/integracao/v1/chamados/<id>`, com `Authorization: Bearer coti_...`.
  - Cada chave só vê os chamados que abriu e faz até 120 pedidos por minuto.
  - Valem a validação e a aprovação normais, e formulários com operação no AD ficam de fora.
  - A chave aparece uma vez; só o hash fica guardado.
- **Webhooks de saída:** avisam quando um chamado é aberto, atribuído, solucionado, fechado ou reaberto, com a mesma assinatura das
  entradas.
  - O aviso traz só o número, a situação e a categoria.
  - Falha tenta de novo em 1, 5, 15 e 60 minutos.
  - O próprio servidor e os endereços locais de enlace, como o serviço de metadados das nuvens, são recusados.

## Licença

O núcleo é GPLv3 (`LICENSE`). O código de terceiros e as partes portadas estão listados em `NOTICE`. Para as atualizações do RuoYi e do frontend, veja `UPSTREAM.md`.
