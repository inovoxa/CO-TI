# Inventário: descoberta de rede (F3c-1)

Usa o IPDiscover que já existe no agente OCS (Windows e Unix): o próprio agente varre a sub-rede dele (ARP/ICMP), sem
instalar nada novo. Referência: `Apache/Ocsinventory/Server/Capacities/Ipdiscover.pm` do OCS Inventory NG 2.12.6
(GPL-2.0-or-later; ver NOTICE).

## Protocolo

1. **Escolha do varredor:** no PROLOG, se o agente vai mandar inventário (`SEND`), o CO-TI olha as sub-redes das
   placas físicas do último inventário (`IPSUBNET`/`IPMASK` de NETWORKS, ignorando placas virtuais, 127.x e
   169.254.x).
   - Se a sub-rede tem menos varredores que `CO_TI_INVENTARIO_IPDISCOVER_POR_REDE` (padrão 1), o agente é escolhido
     (`cmdb.rede_varredor`).
   - Um varredor sem contato há 7 dias, baixado ou fora de uso perde a vaga para o próximo agente que aparecer.
2. **Pedido de varredura:** para o varredor, a resposta ao PROLOG leva:

   ```xml
   <OPTION><NAME>IPDISCOVER</NAME>
     <PARAM IPDISC_LAT="100" SCAN_TYPE_IPDISCOVER="ICMP" SCAN_ARP_BANDWIDTH="256">10.20.5.0</PARAM>
   </OPTION>
   ```

3. **Resultado:** no inventário seguinte, o agente manda `<IPDISCOVER><H><I>ip</I><M>mac</M><N>nome</N></H>…`. O
   CO-TI grava cada host em `cmdb.rede_dispositivo` (um registro por MAC). Regras:
   - IPv4 e MAC são validados. MACs zerados ou de broadcast são descartados. Hífens viram dois-pontos. O nome "-" é
     ignorado.
   - A varredura de quem não é varredor é ignorada.
   - O MAC é comparado com os ativos (MAC principal e placas de rede): se bater, o equipamento fica "conhecido".
   - O equipamento herda a unidade do varredor, para o escopo de dados.
4. **Agentes sem suporte:** o GLPI Agent não usa este mecanismo. A descoberta dele (NetDiscovery/SNMP) fica para a
   próxima etapa.

## Tela (Inventário → Rede)

- **Sub-redes:** quantidade de equipamentos e de desconhecidos, quem varre e a última varredura.
- **Lista de equipamentos:** desconhecidos (padrão), já cadastrados, ignorados ou todos; busca por IP, MAC ou nome.
- **Cadastrar:** cria um ativo manual (tipo, nome, fabricante, modelo, patrimônio, unidade) com o IP e o MAC
  descobertos.
- **Ignorar:** marca como conhecido sem cadastro (ex.: celular de visitante), com um motivo; dá para desfazer.
- **Permissões:** consulta com `inventario:ativo:query`; cadastrar e ignorar com `inventario:ativo:update`.

## Alerta

A regra `rede_desconhecido` alerta para equipamentos desconhecidos e não ignorados vistos nos últimos N dias (limite
padrão 1). Ela vem **desligada**, porque a primeira varredura costuma achar muita coisa: a ideia é cadastrar ou
ignorar o que já existe e só então ligar. Ignorar ou cadastrar o equipamento encerra o alerta.

## Configuração

| Variável | Padrão | O que faz |
|---|---|---|
| `CO_TI_INVENTARIO_IPDISCOVER` | `true` | liga ou desliga a descoberta |
| `CO_TI_INVENTARIO_IPDISCOVER_POR_REDE` | `1` | quantos varredores por sub-rede |
| `CO_TI_INVENTARIO_IPDISCOVER_REDES` | vazio (todas) | lista CIDR das sub-redes que podem ser varridas |
