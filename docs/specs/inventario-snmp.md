# Inventário por SNMP (F3c-2)

Impressoras, switches, roteadores, nobreaks e outros equipamentos sem agente entram no CMDB por SNMP. Quem consulta é o
próprio servidor do CO-TI, com a biblioteca SNMP4J 3.9 (Apache 2.0).

## Credenciais (Inventário → SNMP)

- **Versões:** v2c (comunidade) e v3, com os níveis noAuthNoPriv, authNoPriv e authPriv.
  - Autenticação: SHA, SHA-256, SHA-512 ou MD5.
  - Criptografia: AES, AES-256 ou DES.
  - MD5 e DES são fracos: só use se o equipamento não aceitar outra coisa.
  - Senhas v3 com pelo menos 8 caracteres (exigência do USM).
- **Guarda dos segredos:** cifrados com a chave-mestra (`CO_TI_CHAVE_MESTRA`, contexto `inventario-snmp`).
  - A tela só diz se estão definidos; segredo em branco na edição mantém o guardado.
- **Ordem das tentativas:** as credenciais são tentadas em ordem em cada IP. A que respondeu fica lembrada
  (`cmdb.snmp_alvo`) e é tentada primeiro na próxima vez.
- **Permissões:**
  - configurar, consultar e varrer: `inventario:snmp:update`;
  - ver os IPs consultados: `inventario:ativo:query`.

## Alvos

- IPs que a descoberta de rede achou e não estão ignorados, menos as máquinas com agente OCS ou GLPI Agent.
- Ativos manuais ou de SNMP que têm IP.
- Botão "Consultar IP" para um endereço avulso.
- **Varredura:** a cada `CO_TI_INVENTARIO_SNMP_MS` (padrão 6 h; 0 = só manual).
  - 16 consultas em paralelo; espera de `CO_TI_INVENTARIO_SNMP_TEMPO_MS` (padrão 1,5 s) por pacote, com uma
    repetição.
  - Uma varredura por vez.

## O que é lido

| MIB | OIDs | Uso |
|---|---|---|
| MIB-II system | sysDescr, sysObjectID, sysName, sysLocation, sysContact, ifNumber | nome, descrição, localização, fabricante (pelo número IANA do sysObjectID) |
| ENTITY-MIB | entPhysicalSerialNum/MfgName/ModelName (índice 1) | série, fabricante e modelo do chassi |
| HOST-RESOURCES | hrDeviceDescr.1 | modelo da impressora |
| Printer-MIB (RFC 3805) | prtGeneralSerialNumber, prtMarkerLifeCount, prtMarkerSupplies (descrição, máximo, nível) | série, páginas impressas, suprimentos |
| IF-MIB | ifDescr, ifPhysAddress, ifOperStatus, ifSpeed | interfaces (componentes "rede") e MAC |

**Valores especiais e texto:**
- Nos suprimentos, a Printer-MIB usa valores negativos (-1 outro, -2 desconhecido, -3 "tem algum"). Nesses casos não
  há percentual.
- Os textos são lidos como UTF-8, com Latin-1 de reserva; acentos funcionam.

**Classificação do tipo:**
- **impressora:** tem Printer-MIB;
- **rede:** quatro ou mais interfaces, ou "switch/router/firewall" na descrição;
- **telefone:** a descrição fala de SIP ou VoIP;
- **servidor:** Linux ou Windows sem cara de equipamento de rede;
- **outro:** o resto.

## Gravação

O resultado passa pelo mesmo caminho dos agentes (`InventarioAgenteService.gravarResultado`): reconhecimento de
máquina repetida, componentes (suprimentos e interfaces) e histórico. Origem `snmp`.

- **Identificador:** `snmp:<série>`, ou `snmp:<MAC>`, ou `snmp:<IP>`.
- **Ativo que já existe:** um ativo para o mesmo IP ou MAC (cadastrado à mão, pela descoberta ou por uma varredura
  anterior) é **assumido**. O nome e o tipo escolhidos pela equipe ficam; o resto é atualizado.
- **Máquinas com agente:** nunca são sobrescritas pelo SNMP.
- **Campos extras:** `paginas` (contador) e `localizacao` (sysLocation) no ativo.
- **Unidade:** um ativo novo herda a unidade de quem varreu a sub-rede (descoberta de rede).

## Alerta

A regra `suprimento_baixo` alerta impressoras em uso com algum suprimento abaixo do limite (padrão 10% restante). O
alerta lista os suprimentos.
