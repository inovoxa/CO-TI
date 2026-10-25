# Inventário: agentes OCS e GLPI Agent (F3a)

O CO-TI recebe o inventário dos agentes que já estão instalados nas máquinas, sem trocar nada nelas. O destino
é o CMDB (`cmdb.ativo`), o mesmo cadastro de ativos que o Service Desk usa.

Referências: servidor OCS Inventory NG 2.12.6 (GPL-2.0-or-later; ver NOTICE) e formato JSON do GLPI Agent.

## Endereços

| Agente | URL configurada no agente | Chega em |
|---|---|---|
| OCS (Windows 2.x, Unix 2.x) | `https://<portal>/ocsinventory` | `POST /admin-api/inventario/agente` |
| GLPI Agent 1.x | `https://<portal>/front/inventory.php` (o mesmo caminho do GLPI) | idem |

O nginx do frontend faz o encaminhamento e grava o IP real da conexão em `X-Real-IP` e `X-Forwarded-For`.
O backend não fica exposto fora da rede do Docker.

## Segurança

- O endpoint não tem login do portal (`@PermitAll`). As proteções são:
  1. **redes permitidas** (`CO_TI_INVENTARIO_REDES`, CIDR IPv4/IPv6). O padrão são as redes privadas; com a lista
     vazia, qualquer origem é aceita;
  2. **HTTP Basic opcional** (`CO_TI_INVENTARIO_USUARIO` e `CO_TI_INVENTARIO_SENHA`), equivalente a `/USER` e
     `/PWD` no agente Windows. A comparação é em tempo constante;
  3. limite de 600 pedidos por minuto por IP.
- O corpo aceito tem no máximo 50 MB depois de descomprimido (proteção contra bomba de compressão). O XML é lido
  sem DTD nem entidades externas (XXE).
- Os textos vindos do agente perdem os caracteres de controle e são cortados no tamanho da coluna.

## Protocolo OCS

1. O agente manda `<REQUEST><DEVICEID>…</DEVICEID><QUERY>PROLOG</QUERY></REQUEST>`, comprimido com zlib.
2. O servidor responde `<REPLY><RESPONSE>SEND|STOP</RESPONSE><PROLOG_FREQ>h</PROLOG_FREQ></REPLY>`, também
   comprimido, com `Content-Type: application/x-compressed`.
   - `SEND` quando a máquina é nova ou `CO_TI_INVENTARIO_DIAS` já passou desde o último inventário (0 = sempre).
   - O PROLOG atualiza o "último contato".
3. Com `SEND`, o agente manda `QUERY=INVENTORY` com `CONTENT` (HARDWARE, BIOS, CPUS, MEMORIES, STORAGES,
   DRIVES, NETWORKS, SOFTWARES…). A resposta é `NO_ACCOUNT_UPDATE`.
4. Outras consultas (pacotes, IPDiscover, SNMP) recebem `OTHER_ERROR` até a F3c.

A descompressão aceita zlib, gzip, deflate puro e texto sem compressão.

## Protocolo GLPI Agent

- `{"action":"contact","deviceid":…}` → `{"status":"ok","expiration":h,"tasks":{"inventory":{…}}}`. A tarefa
  `inventory` só vai quando é hora de inventariar.
- `{"action":"inventory","deviceid":…,"content":{…}}` → `{"status":"ok","expiration":h}`.
- O conteúdo é traduzido para as mesmas seções do OCS (operatingsystem → OSNAME/OSVERSION/ARCH, cpus, softwares
  com install_date…). Daí em diante, o tratamento é um só.

## Máquina repetida

O mesmo equipamento é reconhecido, nesta ordem, por:

1. `DEVICEID` igual;
2. UUID da placa (se não for genérico);
3. número de série + fabricante (se a série não for genérica: "To Be Filled By O.E.M.", "System Serial Number",
   zeros…);
4. MAC de uma placa de rede + mesmo nome.

Quando a máquina é achada pelos critérios 2–4 (agente reinstalado), o `DEVICEID` novo substitui o antigo e a troca
fica no histórico. Pedidos do mesmo `DEVICEID` são gravados um de cada vez.

## O que é gravado

- **Ativo** (resumo):
  - tipo: virtual, notebook, servidor ou desktop, conforme o chassi, o fabricante e o sistema;
  - nome, fabricante, modelo, série, patrimônio e UUID;
  - sistema, versão e arquitetura, e se o sistema está **sem suporte** do fabricante na data;
  - processador, núcleos, memória, discos locais (total e livre), IP, MAC da placa física e domínio;
  - último usuário logado, sem o domínio;
  - antivírus: ativo, desatualizado, ausente ou desconhecido.
- **Componentes** (`cmdb.ativo_componente`): cada peça com uma chave que a identifica (série, slot, MAC…).
- **Softwares**: um catálogo único por nome + editora normalizados (`cmdb.software`) e as instalações por máquina,
  com versão, data e arquitetura.
- **Pessoa e unidade:**
  - o login informado pelo agente é procurado no vínculo com o AD e, senão, entre os usuários locais;
  - achada a pessoa, o ativo fica ligado a ela e passa para a unidade dela;
  - a exceção é quando alguém escolheu a unidade à mão (`dept_manual`).
- **Patrimônio:** o agente só preenche quando o campo está vazio. A etiqueta cadastrada pela equipe vale mais.

## Histórico (`cmdb.historico`, somente inclusão)

- **Primeiro inventário:** uma linha de resumo.
- **Inventários seguintes:**
  - campos que mudaram (nome, sistema, memória, usuário, antivírus…). IP e espaço livre ficam de fora, porque
    mudam o tempo todo;
  - peças incluídas e removidas;
  - softwares instalados e removidos.
  - Acima de 100 itens do mesmo tipo num inventário, o resto vira uma linha "… e mais N".
- **Alterações da equipe:** situação, patrimônio, unidade e vínculo com chamado, com o usuário que fez.

## Tela e permissões

- **Inventário → Ativos** (`inventario:ativo:query`, edição com `inventario:ativo:update`):
  - filtros por tipo, situação, unidade (com as subunidades), busca e alertas;
  - alertas: sistema sem suporte, antivírus com problema, sem comunicação há `CO_TI_INVENTARIO_SEM_CONTATO_DIAS`.
- **Detalhe do ativo:** componentes, softwares, chamados ligados e histórico.
  - A equipe muda situação, patrimônio, unidade e observação.
  - Os outros campos só mudam em ativo cadastrado à mão, porque o agente os sobrescreveria.
- **Softwares:** o catálogo com o número de instalações. O clique abre os ativos que têm aquele software.
- **Agentes:** endereços, redes, versões em uso e últimos inventários.
- **Chamado:** o bloco "Equipamentos" liga ativos ao chamado.
  - Sugere primeiro os ativos de quem pediu.
  - Exige `itsm:chamado:update` e respeita o escopo do chamado.
- **Escopo de dados:** `cmdb.ativo` segue a unidade (`dept_id`) e, no perfil "só os meus", o usuário (`usuario_id`).
- **Telão e Centro de Operações → Inventário:** passam a ler o CMDB (`InventarioEquipamentosFonte`).

## Fora da F3a

- **F3b:** alertas automáticos (chamado/ocorrência para sistema sem suporte, antivírus, disco cheio) e software ×
  licenças.
- **F3c:** implantação de pacotes, IPDiscover e SNMP.
