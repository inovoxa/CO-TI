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
