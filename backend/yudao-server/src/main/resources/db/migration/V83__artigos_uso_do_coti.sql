-- Artigos básicos para a equipe interna sobre o uso do CO-TI. São internos (faq = false): quem tem "Ver artigos internos"
-- lê; o portal público não vê. Ficam editáveis na Base de conhecimento como qualquer outro artigo.
DO $$
DECLARE
    cat int8 := nextval('itsm.kb_categoria_seq');
    a   record;
BEGIN
    INSERT INTO itsm.kb_categoria (id, parent_id, nome, nome_completo, descricao, ordem, creator, updater)
    VALUES (cat, 0, 'Uso do CO-TI', 'Uso do CO-TI', 'Como usar o sistema no dia a dia (equipe interna)', 0, '1', '1');

    FOR a IN SELECT * FROM (VALUES
    (1, true, 'Primeiros passos no CO-TI',
     'Como entrar, onde fica cada coisa e onde aparecem os avisos.',
     $h$<h3>Entrar</h3>
<p>Use o mesmo usuário e senha da rede. Pode digitar só o usuário (<b>joao</b>), com o domínio (<b>PMA\joao</b>) ou o e-mail (<b>joao@pma.local</b>). A conta é criada sozinha no primeiro acesso. Se a senha da rede não entra, ela pode estar vencida ou bloqueada no AD: troque ou desbloqueie como de costume.</p>
<h3>O menu</h3>
<ul>
<li><b>Service Desk</b>: chamados, mudanças, problemas e o painel da equipe.</li>
<li><b>Atendimento</b>: conversas que chegam pelos canais (WhatsApp, Telegram, Instagram, e-mail e o chat do site).</li>
<li><b>Chat</b>: conversa interna entre a equipe.</li>
<li><b>Base de conhecimento</b>: artigos como este e a FAQ para os usuários.</li>
<li><b>Monitoramento</b> e <b>Centro de Operações</b>: links, servidores e equipamentos de cada unidade.</li>
<li><b>Inventário</b>: computadores e equipamentos enviados pelos agentes.</li>
<li><b>Cofre de senhas</b>: senhas da equipe, guardadas cifradas.</li>
</ul>
<p>O que aparece no menu depende do seu perfil. Se falta alguma tela de que você precisa, peça a quem administra o sistema.</p>
<h3>Avisos</h3>
<p>O sino no alto da tela mostra o que é com você: chamados atribuídos, prazos de SLA perto de vencer, aprovações pendentes e alertas do monitoramento. Clique no aviso para abrir o item.</p>
<h3>Dicas</h3>
<ul>
<li>A lupa no alto procura telas pelo nome.</li>
<li>As abas abaixo do menu guardam as telas abertas. Feche as que não está usando.</li>
<li>Na dúvida, pergunte ao <b>Assistente IA</b>: ele responde com base nos artigos da base.</li>
</ul>$h$),

    (2, true, 'Como atender um chamado',
     'Do chamado novo ao fechado: assumir, conversar com o usuário, deixar pendente e solucionar.',
     $h$<h3>Onde estão</h3>
<p>Em <b>Service Desk → Chamados</b>. As filas do alto separam <b>Novos</b>, <b>Comigo</b>, <b>Minhas equipes</b>, <b>Pendentes</b> e <b>Prazo vencido</b>. O <b>Painel</b> mostra o volume e os prazos.</p>
<h3>Situações</h3>
<ul>
<li><b>Novo</b>: ninguém pegou ainda.</li>
<li><b>Em atendimento</b>: tem técnico responsável. "Planejado" quando há tarefa agendada.</li>
<li><b>Pendente</b>: esperando algo de fora (usuário, fornecedor, peça). O prazo do SLA para enquanto estiver pendente.</li>
<li><b>Solucionado</b>: a solução foi enviada e espera o usuário aceitar ou recusar.</li>
<li><b>Fechado</b>: encerrado. Só pode ser reaberto.</li>
</ul>
<h3>Passo a passo</h3>
<ol>
<li><b>Assuma</b> o chamado. Ele passa a ser seu e sai da fila.</li>
<li>Escreva um <b>acompanhamento</b> para falar com o usuário. Marque <b>privado</b> para anotações só da equipe: o usuário não vê.</li>
<li>Use <b>tarefa</b> para trabalho que precisa ser feito (com técnico e situação: a fazer, feita).</li>
<li>Se depende de terceiros, coloque em <b>pendente</b> com o motivo e <b>retome</b> quando a resposta chegar.</li>
<li>Ao terminar, registre a <b>solução</b> explicando o que foi feito. O usuário recebe e pode aceitar ou recusar. Se recusar, o chamado volta para atendimento.</li>
<li>Sem resposta do usuário, o chamado solucionado <b>fecha sozinho</b> depois de alguns dias (7, se ninguém mudou).</li>
</ol>
<h3>Boas práticas</h3>
<ul>
<li>Escreva a solução para quem vai ler depois, não só para o usuário. Problema que se repete vira artigo na base.</li>
<li>Ligue ao chamado o artigo que resolveu: ajuda a medir o que a base resolve.</li>
<li>Nunca cole senha em acompanhamento. Use o <b>Cofre de senhas</b>.</li>
<li>Se o chamado é de outra equipe, mude a equipe em vez de deixá-lo parado.</li>
</ul>$h$),

    (3, true, 'Catálogo de serviços, Meus chamados e aprovações',
     'Como o usuário pede um serviço, como acompanhar e como aprovar pedidos.',
     $h$<h3>Catálogo de serviços</h3>
<p>Em <b>Catálogo de serviços</b>, qualquer pessoa escolhe o formulário do que precisa (acesso a sistema, conta de rede, equipamento, ramal) e preenche. O pedido vira um chamado e passa pelas regras do Service Desk: categoria, equipe responsável, urgência e, quando o serviço exige, aprovação.</p>
<p>Quando o usuário liga ou manda mensagem pedindo um serviço do catálogo, prefira abrir pelo formulário: as perguntas certas já vêm prontas e o pedido chega completo.</p>
<h3>Meus chamados</h3>
<p>Em <b>Meus chamados</b>, cada pessoa vê os chamados que abriu: a situação, as respostas da equipe e a solução para aceitar ou recusar. Também dá para responder por ali.</p>
<h3>Aprovações</h3>
<p>Alguns pedidos precisam do "de acordo" de um gestor antes de serem atendidos. Quem aprova recebe o aviso e decide em <b>Minhas aprovações</b>. Aprovado, o chamado segue para a equipe. Recusado, ele é encerrado com o motivo.</p>
<p>Só aprova quem tem o papel de aprovador ou está no grupo de aprovadores do AD. Se um pedido parou esperando aprovação de alguém errado, avise quem administra o sistema.</p>$h$),

    (4, true, 'Usando o chat interno',
     'Conversas, grupos, menções, anexos e resposta automática de ausência.',
     $h$<h3>Conversar</h3>
<ul>
<li>Em <b>Chat</b>, comece uma conversa direta com qualquer pessoa ou crie um <b>grupo</b>. Quem cria o grupo administra: renomeia e adiciona pessoas.</li>
<li>Digite <b>@</b> e o nome para <b>mencionar</b> alguém: a pessoa recebe o aviso mesmo com a conversa fechada.</li>
<li>Dá para <b>responder</b> uma mensagem específica, <b>editar</b> e <b>apagar</b> as suas, e <b>buscar</b> nas conversas.</li>
<li>Férias ou ausência? Ligue a <b>resposta automática</b> (o botão ao lado dos emojis) com a data de volta.</li>
</ul>
<h3>Anexos</h3>
<ul>
<li>Use o clipe para enviar um arquivo. O limite padrão é <b>5 MB</b> por arquivo.</li>
<li>São aceitos imagens (png, jpg, gif, webp), PDF, texto, CSV, documentos do Office e do LibreOffice e ZIP. Programas e scripts não entram.</li>
<li>Para não lotar o disco, <b>os anexos são apagados depois de um tempo</b> (padrão: 48 horas). A mensagem continua, com o aviso "anexo expirado". O chat não é lugar de guardar documento: o que precisa ficar vai no chamado ou na pasta do setor.</li>
<li>O limite e o prazo ficam em Infraestrutura → Visão geral. Lá também dá para desligar os anexos do chat.</li>
</ul>
<h3>Cuidados</h3>
<p>O chat é ferramenta de trabalho. Mensagens apagadas ainda podem ser consultadas por quem administra o sistema em uma apuração. Nunca envie senhas pelo chat: use o Cofre de senhas.</p>$h$),

    (5, true, 'Base de conhecimento: ler e escrever artigos',
     'Diferença entre FAQ e artigo interno, como escrever um bom artigo e ligar ao chamado.',
     $h$<h3>FAQ ou interno</h3>
<ul>
<li><b>FAQ</b>: aparece para todos no portal. É para o usuário resolver sozinho (trocar senha, instalar impressora).</li>
<li><b>Interno</b>: só a equipe de TI vê. É para procedimentos técnicos (restaurar backup, configurar equipamento), como este artigo.</li>
</ul>
<h3>Escrever um bom artigo</h3>
<ol>
<li>Título com o problema, do jeito que o usuário fala: "Impressora não imprime", não "Spooler".</li>
<li>Um resumo de uma linha: aparece na busca e nas sugestões.</li>
<li>Passos numerados e curtos, com o caminho exato das telas.</li>
<li>Escolha a categoria. Use <b>fixado</b> só para o que é muito procurado.</li>
<li>Pode salvar como rascunho e publicar depois. Cada edição guarda a versão anterior, e dá para voltar a ela.</li>
</ol>
<h3>No dia a dia</h3>
<ul>
<li>Ao abrir um chamado, o sistema sugere artigos parecidos com o título. Vale olhar antes de atender.</li>
<li>Ligue ao chamado o artigo que resolveu.</li>
<li>O "Este artigo ajudou?" mostra o que precisa ser melhorado.</li>
<li>O Assistente IA usa estes artigos para responder: artigo bom é resposta boa.</li>
</ul>$h$),

    (6, true, 'Monitoramento e Centro de Operações',
     'Como ver se um link ou serviço caiu, avisos e janelas de manutenção.',
     $h$<h3>Onde olhar</h3>
<ul>
<li><b>Centro de Operações → Visão geral</b>: o resumo de tudo que está fora do ar ou lento, por unidade. É a tela do telão.</li>
<li><b>Links, Equipamentos e Chamados por unidade</b>: o mesmo recorte por escola, posto de saúde ou secretaria.</li>
<li><b>Monitoramento → Monitores</b>: cada verificação (ping, site, porta, SNMP, banco de dados), com o histórico e a disponibilidade.</li>
<li><b>Monitoramento → Unidades e endereços</b>: o cadastro das unidades, com endereço e localização no mapa. Dá para exportar e importar em planilha.</li>
</ul>
<h3>Quando algo cai</h3>
<ol>
<li>O aviso chega pelo sino e pelos <b>canais de aviso</b> configurados (e-mail, Telegram ou webhook).</li>
<li>Confira se não é a unidade inteira. Se vários monitores da mesma unidade caíram juntos, o problema costuma ser o link ou a energia, não cada equipamento.</li>
<li>Abra ou atualize o chamado da ocorrência, para o histórico ficar registrado.</li>
</ol>
<h3>Manutenção programada</h3>
<p>Antes de desligar um equipamento ou link de propósito, crie uma janela em <b>Monitoramento → Manutenções</b>. Durante a janela, os monitores escolhidos não disparam aviso e o período fica registrado como manutenção, não como queda.</p>$h$),

    (7, true, 'Espaço em disco: o que ocupa e como controlar',
     'Onde ver o espaço do servidor do CO-TI e ajustar por quanto tempo cada dado fica guardado.',
     $h$<h3>Onde ver</h3>
<p>Em <b>Infraestrutura → Visão geral</b> estão o espaço livre, quanto o CO-TI cresce por dia e a previsão de quando o disco enche. Logo abaixo aparece o que mais ocupa: logs, verificações do monitoramento, anexos.</p>
<h3>Retenção e limites</h3>
<p>Na mesma tela, a tabela "Retenção e limites" define por quanto tempo cada tipo de dado fica guardado. A limpeza roda sozinha. <b>Reduzir um prazo apaga os dados mais antigos na próxima execução, sem como recuperar.</b></p>
<ul>
<li><b>Anexos do chat</b>: apagados depois de 48 horas (mínimo 24). 0 guarda para sempre: evite.</li>
<li><b>Tamanho máximo do anexo no chat</b>: 5 MB. 0 desliga os anexos no chat.</li>
<li><b>Logs centralizados</b> e <b>Tamanho máximo dos logs</b>: costumam ser o que mais cresce.</li>
<li><b>Verificações do monitoramento</b>: cada teste de cada monitor. Os totais por hora ficam mais tempo e garantem a disponibilidade do mês e do ano.</li>
</ul>
<p>Chamados, conversas de atendimento, inventário e a trilha de auditoria não são apagados pela limpeza.</p>
<h3>Quando o aviso chegar</h3>
<p>Com 80% do disco usado, os administradores recebem aviso. Veja o que mais cresce e reduza o prazo daquele item, ou peça mais disco para o servidor.</p>$h$)
    ) AS t(ordem, publicado, titulo, resumo, conteudo)
    LOOP
        INSERT INTO itsm.kb_artigo (id, titulo, resumo, conteudo, texto, categoria_id, faq, publicado, fixado, autor_id, creator, updater)
        VALUES (nextval('itsm.kb_artigo_seq'), a.titulo, a.resumo, a.conteudo,
                trim(regexp_replace(regexp_replace(a.conteudo, '<[^>]+>', ' ', 'g'), '\s+', ' ', 'g')),
                cat, false, a.publicado, a.ordem = 1, 1, '1', '1');
    END LOOP;
END $$;
