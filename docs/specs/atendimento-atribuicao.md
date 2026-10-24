# Atribuição automática de conversas (com limite por atendente)

Especificação própria do CO-TI. Escrita a partir do comportamento esperado de uma central de atendimento, sem
copiar nem traduzir código da pasta `enterprise/` do Chatwoot (ver NOTICE e a decisão registrada no plano).

## Objetivo

Quando chega uma conversa nova numa caixa de entrada com atribuição automática ligada, ela vai para um atendente
disponível, sem ninguém precisar distribuir à mão. O limite por atendente evita que uma pessoa fique com tudo.

## Regras

1. Candidatos: os membros da caixa que estão **disponíveis** — marcaram "disponível" e o navegador deu sinal de
   vida nos últimos 3 minutos.
2. Fora deles, ninguém recebe: a conversa fica sem atendente, na fila "Não atribuídas".
3. Limite (`limite_por_agente` na caixa; 0 = sem limite): não recebe quem já tem esse número de conversas
   **abertas** na caixa.
4. Entre os candidatos que sobram, recebe quem tem menos conversas abertas (em todas as caixas); empate, quem
   recebeu a última há mais tempo.
5. Conversa reaberta (o contato escreveu de novo) volta para o mesmo atendente, se ele continua membro da caixa;
   senão, entra na distribuição como nova.
6. Atribuir à mão sempre pode, mesmo acima do limite (o limite vale só para a distribuição automática).
7. Quando um atendente fica indisponível, as conversas dele continuam com ele (não são redistribuídas).

## Fora do escopo desta versão

- Pesos diferentes por atendente, prioridade por habilidade, redistribuição por tempo sem resposta.
