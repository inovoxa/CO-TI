# Inventário: implantação de pacotes (F3c-3)

Instala programas, roda scripts ou copia arquivos nas máquinas que têm o **agente OCS**, usando o teledeploy que o
agente já tem. Referências:
- servidor: `Apache/Ocsinventory/Server/Capacities/Download.pm` e `ocsreports/require/teledeploy/PackageBuilder.php`;
- agente: `Ocsinventory-Unix-Agent/lib/Ocsinventory/Agent/Modules/Download.pm`.

Todos são do OCS Inventory NG 2.12.6 (GPL-2.0-or-later; ver NOTICE).

## Pacote

| Campo | Uso |
|---|---|
| Ação | `EXECUTE` roda uma linha de comando no conteúdo extraído; `LAUNCH` abre um arquivo do pacote; `STORE` só copia para uma pasta |
| Plataforma | windows, linux ou macos: o pacote só é pedido para máquinas do mesmo sistema |
| Arquivo | zip (Windows) ou tar.gz (Linux) |
| Prioridade | 0 a 10 (0 = antes); padrão 5 |
| Aviso ao usuário | mensagem antes de instalar, com 60 s e opção de adiar |

**Envio do arquivo:**
- vai em fluxo (corpo bruto, `PUT /inventario/pacote/arquivo`), sem o limite de 16 MB do upload comum;
- limite próprio de `CO_TI_INVENTARIO_PACOTE_MAX_MB` (padrão 2048);
- o nginx aceita até 2 GB nessa rota, sem buffer.

**Guarda:**
- o CO-TI calcula MD5 (o digest que o agente confere) e SHA-256 (para conferência humana);
- grava os fragmentos de 1 MB em `CO_TI_INVENTARIO_PACOTES_PASTA` (padrão `/app/arquivos/pacotes`, no volume
  `arquivos`).

**Depois da primeira implantação**, a ação, o comando, a pasta e o arquivo não mudam: para outra versão, crie outro
pacote. Isso garante que o histórico diz exatamente o que rodou em cada máquina.

## Protocolo

1. **Pedido:** quem tem `inventario:pacote:update` escolhe as máquinas, ou uma unidade com as subunidades.
   - Entram só máquinas em uso, com agente OCS, da plataforma certa e no escopo de quem pede.
   - Cada máquina vira uma implantação `aguardando`. Não há duas abertas para o mesmo pacote e máquina.
2. **PROLOG:** em todo PROLOG (com ou sem inventário), a resposta leva:

   ```xml
   <OPTION><NAME>DOWNLOAD</NAME>
     <PARAM TYPE="CONF" ON="1" CYCLE_LATENCY="60" FRAG_LATENCY="10" PERIOD_LATENCY="1" PERIOD_LENGTH="10"
            TIMEOUT="30" EXECUTION_TIMEOUT="120"/>
     <PARAM TYPE="PACK" ID="1761400000" INFO_LOC="portal/download" PACK_LOC="portal/download"
            CERT_PATH="INSTALL_PATH" CERT_FILE="INSTALL_PATH" SCHEDULE="" POSTCMD="" FORCE="0"/>
   </OPTION>
   ```

   A implantação passa a `notificado` e o pacote continua indo até o agente responder.
3. **Download:** o agente baixa:
   - `https://<INFO_LOC>/<ID>/info`: `<DOWNLOAD ID PRI ACT DIGEST PROTO FRAGS DIGEST_ALGO="MD5" DIGEST_ENCODE="Hexa" COMMAND|NAME|PATH NOTIFY_*…/>`;
   - `<PROTO>://<PACK_LOC>/<ID>/<ID>-<n>`: os fragmentos.

   `INFO_LOC`/`PACK_LOC` = `CO_TI_INVENTARIO_PACOTES_ENDERECO`, ou o endereço público (`CO_TI_URL_PUBLICA`) +
   `/download`, sem o esquema.
4. **Resultado:** o agente manda `QUERY=DOWNLOAD` com `ID` e `ERR`. O agente Unix manda os campos como atributos da
   raiz (XML::Simple); o Windows, como elementos. O CO-TI lê os dois.
   - `SUCCESS*` e `EXIT_CODE_0` → `sucesso`;
   - qualquer outro código (`ERR_BAD_DIGEST`, `ERR_EXECUTE`, `ERR_TIMEOUT`, `EXIT_CODE_n`…) → `erro`, com o código
     guardado.
   - Depois de erro, dá para pedir de novo.
5. **Cancelar:** a implantação em andamento (`aguardando`/`notificado`) para de ser oferecida ao agente.

## Segurança

- **Permissões:**
  - criar, enviar arquivo, implantar e cancelar exigem `inventario:pacote:update`, que **não vem em nenhum perfil**
    (só o administrador): conceda conscientemente;
  - ver pacotes e situação exige `inventario:pacote:query`.
- **Auditoria:** cada criação, alteração, envio de arquivo (com SHA-256), implantação (com as máquinas) e cancelamento
  vai para a auditoria encadeada.
- **Download sem login (como no OCS):**
  - só das redes dos agentes (`CO_TI_INVENTARIO_REDES`);
  - só pacotes ativos;
  - identificadores só com dígitos e fragmentos no formato `<ID>-<n>`, sem caminho;
  - **não coloque senhas dentro de pacotes.**
- **HTTPS obrigatório no agente:** o agente baixa o `info` sempre por HTTPS e confere o certificado (no Windows, o
  `cacert.pem` da instalação do agente). O portal precisa estar em HTTPS com um certificado que os agentes confiem. O
  MD5 do `info` garante a integridade dos fragmentos.

## Fora desta etapa

- Teledeploy do GLPI Agent (tarefa `deploy`, outro protocolo).
- Agendamento por horário (`SCHEDULE`), comando pós-execução (`POSTCMD`) e implantação forçada (`FORCE`): o campo vai
  vazio ou zero.
