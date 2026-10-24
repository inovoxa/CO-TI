# Automação no AD (servidor Windows)

O CO-TI executa as operações pedidas pelo catálogo de serviços (criar usuário, resetar senha, reativar,
desativar, acesso a pasta, transferência de setor e criação de e-mail) chamando o
`Executar_AD_Automatico.ps1` por SSH num servidor Windows que tenha o módulo ActiveDirectory
(e, para e-mail, acesso ao Exchange). A operação só roda **depois da aprovação** pedida pelo formulário.

## Instalação no Windows

1. Instale o OpenSSH Server (Configurações → Aplicativos → Recursos opcionais) e deixe o serviço `sshd`
   automático.
2. Crie uma conta de serviço no AD, por exemplo `svc-coti-ad`, com delegação **apenas** nas OUs que o
   CO-TI pode alterar (criar/alterar usuários, mover para a OU de desativados, redefinir senha).
3. Copie para `C:\Scripts\`:
   - `Executar_AD_Automatico.ps1`
   - `coti-ad.config.exemplo.json`, renomeado para `coti-ad.config.json` e ajustado à empresa
     (domínio, sufixo de e-mail, OU de desativados, Exchange e pastas que podem ser liberadas).
4. Gere um par de chaves no servidor do CO-TI e autorize a pública na conta de serviço:
   ```bash
   mkdir -p segredos && ssh-keygen -t ed25519 -N '' -f segredos/ad_ssh_key
   # conteúdo de segredos/ad_ssh_key.pub → C:\Users\svc-coti-ad\.ssh\authorized_keys
   ```
5. Pegue a impressão digital da chave do servidor Windows (o CO-TI recusa conectar sem ela):
   ```bash
   ssh-keyscan -t ed25519 dc-automacao.empresa.local | ssh-keygen -lf -
   ```

## Configuração no CO-TI (`.env`)

```
CO_TI_AD_EXECUCAO=ssh
CO_TI_AD_SSH_HOST=dc-automacao.empresa.local
CO_TI_AD_SSH_USUARIO=svc-coti-ad
CO_TI_AD_SSH_IMPRESSAO=SHA256:...
```

A chave privada fica em `./segredos/ad_ssh_key` (montada só para leitura no contêiner; a pasta está no
`.gitignore`). Para demonstração sem AD, use `CO_TI_AD_EXECUCAO=simulado`.

## Contrato

```
powershell.exe -File C:\Scripts\Executar_AD_Automatico.ps1 -Acao <Criar|Reset|Reativar|Desativar|AcessoPasta|Transferir|CriarEmail> -DadosB64 <JSON em Base64>
```

- Os dados são as respostas do formulário (nomes das perguntas), mais `chamado_id` e `login_solicitante`.
  Vão em Base64: nada digitado no formulário vira parte do comando.
- O script responde uma linha JSON: `{"sucesso":true,"acao_real":"...","login":"...","senha":"...","mensagem":"..."}`
  ou `{"sucesso":false,"erro":"..."}`.
- A senha provisória é cifrada com a chave-mestra assim que chega, nunca aparece na linha do tempo nem no
  log, e o solicitante a vê **uma única vez** em "Meus chamados".
- Erro de negócio (usuário inexistente, pasta fora da lista) não é repetido; falha de rede/SSH é repetida
  em 1, 5 e 15 minutos.
