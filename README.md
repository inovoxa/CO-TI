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

Depois abra `http://<servidor>:8080` e entre como `admin`, com a senha inicial definida. Em seguida, remova `CO_TI_ADMIN_SENHA_INICIAL` do `.env`.

Serviços: `postgres` (PostgreSQL 16, TimescaleDB e pgvector), `redis`, `backend` e `frontend` (nginx, que publica o portal, a API `/admin-api` e o WebSocket `/infra/ws` na mesma origem).

## Ligar o Active Directory

No `.env`:

```
CO_TI_AD_HABILITADO=true
CO_TI_AD_URL=ldaps://dc01.empresa.local:636,ldaps://dc02.empresa.local:636
CO_TI_AD_BIND_DN=CN=svc-coti,OU=Servicos,DC=empresa,DC=local      # conta só de leitura
CO_TI_AD_BIND_SENHA=...
CO_TI_AD_BASE_USUARIOS=OU=Empresa,DC=empresa,DC=local
```

- **Login:** a senha é conferida no AD (bind). Na primeira entrada, o usuário é criado no portal. Contas com vínculo ao AD **só** entram com a senha do AD. O `admin` local continua entrando mesmo com o AD fora do ar.
- **Estrutura:** as OUs abaixo de `CO_TI_AD_OU_RAIZ` (padrão: a base) viram departamentos debaixo do departamento `CO_TI_AD_DEPT_RAIZ_ID` (100, "Empresa").
- **Perfis:** em `coti.ad.grupos-perfis` (`application-coti.yaml`), cada grupo do AD é mapeado para um perfil do portal (`noc`, `service_desk`, `soc`, `portaria`, `gestor_unidade`, `telao`). Os grupos aninhados também valem.
- **Sincronização completa:** a cada 30 min (`CO_TI_AD_CRON`). Quem sumiu do AD ou foi desativado lá é desativado no portal. Se mais de 20% das contas sumirem de uma vez, a sincronização não desativa ninguém e registra um erro, porque o mais provável é um filtro mal configurado.
- O certificado do DC precisa ser confiável. Use `CO_TI_AD_TRUSTSTORE` com a CA do AD; `ldap://` sem StartTLS é recusado.

## Catálogo de serviços e automação no AD

- **Catálogo:** em *Service Desk → Formulários → Importar*, carregue `exemplos/catalogo-servicos.json` (25 formulários:
  usuários e acessos, informática, saúde e telefonia). O usuário pede em *Catálogo de serviços*; o pedido vira chamado
  e passa pelas *Regras* (atribuição, categoria, urgência, pedido de aprovação).
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
- **Chave-mestra (`CO_TI_CHAVE_MESTRA`):** cifra os segredos do MFA (e, depois, o cofre) com AES-256-GCM. **Guarde uma cópia fora do servidor.** Sem ela, quem tem MFA precisa recadastrar o celular.
- **Trilha de auditoria** (*Centro de Operações → Auditoria*):
  - registra login, falhas, MFA, mudanças vindas do AD e a senha inicial;
  - é encadeada por SHA-256: o botão *Verificar integridade* aponta a primeira linha alterada ou removida;
  - o banco recusa `UPDATE`/`DELETE`/`TRUNCATE` nela.
- **Dois papéis no PostgreSQL:**
  - `coti` é o dono das tabelas e só roda as migrations;
  - a aplicação conecta como `coti_app`, que só lê e grava dados e não consegue alterar a estrutura nem a auditoria.
- **Logins alternativos desligados:** auto-cadastro, SMS e login social do RuoYi ficam desligados (`coti.seguranca.login-alternativo`). O `grant_type=password` do OAuth2 é recusado para quem tem MFA.
- **Dados que não ficam guardados:**
  - o navegador não guarda a senha ("lembrar" guarda só o usuário);
  - o log não grava senha nem token;
  - o painel `/druid` fica desligado.
- **IP real:** o nginx sobrescreve `X-Forwarded-For` com o IP da conexão. Atrás de outro proxy, configure o módulo `realip`.

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

## Licença

O núcleo é GPLv3 (`LICENSE`). O código de terceiros e as partes portadas estão listados em `NOTICE`. Para as atualizações do RuoYi e do frontend, veja `UPSTREAM.md`.
