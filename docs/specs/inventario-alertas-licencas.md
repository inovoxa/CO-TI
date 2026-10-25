# Inventário: alertas e software × licenças (F3b)

## Alertas

Cinco regras fixas (`cmdb.alerta_regra`). A equipe liga e desliga cada uma, ajusta o limite e decide se ela abre
chamado (permissão `inventario:alerta:update`).

| Regra | Condição | Limite padrão |
|---|---|---|
| `sem_suporte` | sistema operacional sem suporte do fabricante na data (regra da F3a) | — |
| `antivirus` | desktop ou notebook com agente e antivírus ausente ou desatualizado | — |
| `disco_cheio` | espaço livre dos discos locais abaixo do limite | 10 % livre |
| `sem_contato` | computador com agente sem contato há mais que o limite | 30 dias |
| `licenca_excedida` | licença ativa do ITSM com mais máquinas que o comprado (ver abaixo) | — |

- São avaliados só os computadores (desktop, notebook, servidor, virtual) **em uso**.
- **Avaliação:** a cada `CO_TI_INVENTARIO_ALERTAS_MS` (padrão 15 min; 0 = só manual) e pelo botão "Avaliar agora".
  Cada avaliação compara o que está errado agora com os alertas abertos (`cmdb.alerta_ativo`):
  - condição nova → alerta aberto;
  - condição que sumiu (ou regra desligada) → alerta encerrado (`fim`); o histórico fica.
  - Um índice único impede dois alertas abertos da mesma regra para o mesmo objeto.
- **Chamado:** com "abre chamado", cada alerta sem chamado abre um, no máximo 20 por avaliação, para não inundar o
  Service Desk ao ligar a opção numa regra com muitas máquinas. O resto abre nas avaliações seguintes.
  - Origem "monitoramento", `origem_automatica = inventario:<regra>`, solicitante "sistema".
  - A categoria e a urgência vêm da regra; a unidade é a da máquina.
  - O chamado já nasce ligado à máquina (`cmdb.chamado_ativo`).
  - Quando a condição normaliza, o chamado recebe um acompanhamento. O técnico decide se soluciona.
- **Escopo:** o alerta guarda a unidade da máquina (ou da licença). Quem só vê a própria unidade só vê os alertas
  dela. A avaliação em si roda sem escopo (é rotina do sistema).

## Software × licenças

- A licença continua no ITSM (Governança → Licenças). O inventário não adivinha qual software é de qual licença: a
  equipe escolhe, na aba "Instalações encontradas", os itens do catálogo de software que contam
  (`cmdb.licenca_software`). Exemplo: a licença "Microsoft 365 E3" conta "Microsoft 365 Apps para Grandes Empresas -
  pt-br" e "Microsoft 365 Apps for enterprise - en-us".
- **Instalações encontradas** = máquinas não baixadas com pelo menos um desses softwares (uma máquina conta uma vez).
  - O total é da licença inteira, sem escopo.
  - A lista de máquinas respeita o escopo de quem consulta.
- **Excedida** = licença limitada com mais instalações encontradas que a quantidade comprada. Isso é independente dos
  usos registrados à mão, que continuam valendo como antes.
- Telas:
  - **Licenças:** coluna "Encontradas", em vermelho quando passa do comprado;
  - **Softwares:** coluna "Licenças".

## Filtros da lista de ativos

| Filtro | Como funciona |
|---|---|
| Busca | nome, patrimônio, série, IP, usuário ou modelo |
| Tipo, situação e unidade | a unidade inclui as subunidades |
| Sistema operacional, fabricante e modelo | valores existentes, com a quantidade de cada um (`/inventario/ativo/opcoes`) |
| Antivírus e origem | — |
| Faixa de memória | em GB |
| Software instalado | — |
| Alertas | sistema sem suporte, antivírus com problema, sem comunicação |
