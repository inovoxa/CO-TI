"""
Carga de demonstração do CO-TI: Prefeitura de Araraquara (fictícia), com histórico de chamados para testar volume.

    python ferramentas/semente/semente.py                 # 3 anos, ~150 mil chamados
    python ferramentas/semente/semente.py --anos 1 --chamados 20000

Pré-requisitos: o CO-TI rodando pelo docker compose (banco recém-criado pelas migrations) e o arquivo .env na raiz.
Só biblioteca padrão do Python 3.10+. Tudo é fictício: pessoas, e-mails (@araraquara.example), telefones, patrimônios e
IPs. Endereços usam bairros reais da cidade só para o mapa ficar plausível.

O que faz:
  1. estrutura (SQL): secretarias e setores, unidades no mapa, servidores e perfis, equipes, SLAs, categorias, base de
     conhecimento, governança, inventário, monitores e atendimento por WhatsApp;
  2. catálogo de serviços: importa exemplos/catalogo-servicos.json pela API do próprio CO-TI (com uma sessão
     temporária do administrador, encerrada no fim);
  3. histórico de chamados (SQL gerado dentro do PostgreSQL): atores, acompanhamentos, soluções, histórico e pesquisa
     de satisfação;
  4. problemas (ligados aos chamados que os revelaram), mudanças com janela e aprovação, e projetos da TI com tarefas.

Contas de demonstração: as listadas em CONTAS_DEMO entram com a senha CO_TI_SENHA_DEMO do .env (criada na primeira
execução se não existir). As demais pessoas não têm senha local, como as contas que viriam do AD.
"""
import argparse
import json
import os
import random
import secrets
import subprocess
import sys
import time
import unicodedata
import urllib.request
import uuid
from datetime import date, datetime, timedelta

RAIZ = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
AGORA = datetime.now().replace(microsecond=0)

# ======================================================================================== dados de referência

NOMES_F = ('Ana Beatriz Camila Carla Daniela Eduarda Fernanda Gabriela Helena Isabela Juliana Karina Larissa Letícia '
           'Luana Mariana Natália Patrícia Priscila Rafaela Renata Sabrina Simone Tatiane Valéria Vanessa Yasmin Aline '
           'Bianca Cláudia Débora Elaine Fabiana Glória Heloísa Ingrid Jaqueline Lúcia Márcia Rosana Sílvia').split()
NOMES_M = ('André Bruno Caio Carlos Daniel Diego Eduardo Fábio Felipe Fernando Gustavo Henrique Igor João Leonardo '
           'Lucas Marcelo Márcio Mateus Nicolas Otávio Paulo Rafael Raul Renato Ricardo Rodrigo Sérgio Thiago Túlio '
           'Vinícius Wagner Alexandre Antônio César Douglas Emerson Fabrício Gilberto Hugo Jorge').split()
SOBRENOMES = ('Silva Santos Oliveira Souza Rodrigues Ferreira Alves Pereira Lima Gomes Costa Ribeiro Martins Carvalho '
              'Almeida Lopes Soares Fernandes Vieira Barbosa Rocha Dias Nascimento Andrade Moreira Nunes Marques Machado '
              'Mendes Freitas Cardoso Teixeira Araújo Monteiro Correia Pinto Batista Campos Moura Prado Bueno Toledo '
              'Camargo Arruda Paiva Rezende').split()

# (secretaria, [setores da sede], [(prefixo da unidade, [bairros])])  — nomes de departamento têm no máximo 30 letras
SECRETARIAS = [
    ('Gabinete do Prefeito', ['Chefia de Gabinete', 'Ouvidoria'], []),
    ('Administração', ['Recursos Humanos', 'Compras e Licitações', 'Patrimônio', 'Protocolo Geral'], []),
    ('Fazenda', ['Tributação', 'Contabilidade', 'Tesouraria', 'Fiscalização Tributária'], []),
    ('Saúde', ['Gestão da Saúde', 'Vigilância em Saúde', 'Regulação'],
     [('UBS', ['Vila Xavier', 'Selmi Dei', 'Jardim Iguatemi', 'Vale do Sol', 'Parque São Paulo', 'Yolanda Ópice',
               'Santa Angelina', 'Jardim América'])]),
    ('Educação', ['Gestão Pedagógica', 'Merenda Escolar', 'Transporte Escolar'],
     [('EMEF', ['Jardim Brasil', 'Hortênsias', 'Cruzeiro do Sul', 'Vila Harmonia', 'Jardim Indaiá', 'Pinheiros']),
      ('CER', ['Jardim Paulistano', 'Vila Melhado', 'Parque Residencial'])]),
    ('Assistência Social', ['Proteção Social Básica', 'Cadastro Único'],
     [('CRAS', ['Selmi Dei', 'Jardim Adalberto', 'Vale Verde'])]),
    ('Obras e Serviços Públicos', ['Engenharia', 'Manutenção Urbana', 'Garagem Municipal'], []),
    ('Meio Ambiente', ['Licenciamento Ambiental', 'Parques e Jardins'], []),
    ('Cultura', ['Teatro Municipal', 'Bibliotecas'], []),
    ('Esportes e Lazer', ['Ginásios', 'Eventos Esportivos'], []),
    ('Desenvolvimento Urbano', ['Aprovação de Projetos', 'Cadastro Imobiliário'], []),
    ('Mobilidade Urbana', ['Trânsito', 'Transporte Coletivo'], []),
    ('Segurança Pública', ['Guarda Civil Municipal', 'Defesa Civil'], []),
    ('Comunicação', ['Imprensa', 'Mídias Digitais'], []),
    ('Tecnologia da Informação', ['Service Desk', 'Infraestrutura e Datacenter', 'Redes e Telefonia', 'Sistemas',
                                  'Segurança da Informação'], []),
]
# coordenadas aproximadas dos bairros (só para o mapa); o centro é a Prefeitura
CENTRO = (-21.7946, -48.1756)
BAIRROS = {
    'Centro': (-21.7946, -48.1756), 'Vila Xavier': (-21.7745, -48.1680), 'Selmi Dei': (-21.7565, -48.2010),
    'Jardim Iguatemi': (-21.8160, -48.1980), 'Vale do Sol': (-21.8235, -48.1655), 'Parque São Paulo': (-21.7720, -48.2070),
    'Yolanda Ópice': (-21.8060, -48.2160), 'Santa Angelina': (-21.8110, -48.1500), 'Jardim América': (-21.7860, -48.1590),
    'Jardim Brasil': (-21.7830, -48.1700), 'Hortênsias': (-21.8040, -48.1450), 'Cruzeiro do Sul': (-21.8150, -48.1720),
    'Vila Harmonia': (-21.7880, -48.1930), 'Jardim Indaiá': (-21.8000, -48.2050), 'Pinheiros': (-21.7680, -48.1830),
    'Jardim Paulistano': (-21.7920, -48.2060), 'Vila Melhado': (-21.8000, -48.1810), 'Parque Residencial': (-21.7760, -48.1500),
    'Jardim Adalberto': (-21.7640, -48.1870), 'Vale Verde': (-21.8300, -48.1900), 'Vila Ferroviária': (-21.7850, -48.1820),
    'Jardim Primavera': (-21.8070, -48.1620), 'Fonte Luminosa': (-21.7900, -48.1660),
}
SEDES = {  # bairro da sede de cada secretaria (a do Gabinete é o Paço Municipal)
    'Gabinete do Prefeito': 'Centro', 'Administração': 'Centro', 'Fazenda': 'Centro', 'Saúde': 'Vila Ferroviária',
    'Educação': 'Fonte Luminosa', 'Assistência Social': 'Jardim Primavera', 'Obras e Serviços Públicos': 'Vila Xavier',
    'Meio Ambiente': 'Jardim Primavera', 'Cultura': 'Centro', 'Esportes e Lazer': 'Fonte Luminosa',
    'Desenvolvimento Urbano': 'Centro', 'Mobilidade Urbana': 'Vila Ferroviária', 'Segurança Pública': 'Vila Harmonia',
    'Comunicação': 'Centro', 'Tecnologia da Informação': 'Centro',
}

# perfis criados pelas migrations
PERFIL = {'admin': 1, 'usuario': 2, 'noc': 50001, 'service_desk': 50002, 'soc': 50003, 'gestor_unidade': 50005,
          'gestao_ti': 50007, 'atendente': 50008}

EQUIPES = [  # (chave, nome, descrição, setor da TI)
    ('n1', 'N1 - Service Desk', 'Primeiro atendimento: telefone, portal, e-mail e WhatsApp', 'Service Desk'),
    ('campo', 'N2 - Suporte técnico', 'Atendimento presencial nas unidades: computadores e impressoras', 'Service Desk'),
    ('infra', 'N2 - Infraestrutura', 'Servidores, datacenter, backup e serviços de rede', 'Infraestrutura e Datacenter'),
    ('redes', 'Redes e Telefonia', 'Links, Wi-Fi, VPN, ramais e celulares', 'Redes e Telefonia'),
    ('sistemas', 'Sistemas', 'Sistemas corporativos, saúde, educação e e-mail', 'Sistemas'),
    ('seg', 'Segurança da Informação', 'Incidentes de segurança, phishing e malware', 'Segurança da Informação'),
]

# SLA por prioridade (1 muito baixa ... 6 crítica): (atendimento, solução) em (tempo, unidade), dentro do expediente
SLA_PRIORIDADE = {1: ((8, 'hora'), (5, 'dia')), 2: ((8, 'hora'), (3, 'dia')), 3: ((4, 'hora'), (2, 'dia')),
                  4: ((2, 'hora'), (8, 'hora')), 5: ((1, 'hora'), (4, 'hora')), 6: ((30, 'minuto'), (2, 'hora'))}

# Categorias: os grupos e nomes do catálogo (exemplos/catalogo-servicos.json) são os mesmos, para a importação achar.
# (grupo, nome, equipe, tipo 1 incidente/2 requisição, urgência, impacto, peso no volume, títulos, soluções)
CATEGORIAS = [
    ('Usuários e acessos', 'Criação de usuário', 'n1', 2, 2, 2, 30,
     ['Criação de usuário para novo servidor', 'Novo funcionário precisa de acesso à rede', 'Cadastro de estagiário na rede'],
     ['Usuário criado no AD com o perfil do setor; senha provisória entregue ao solicitante.']),
    ('Usuários e acessos', 'Reset de senha', 'n1', 2, 3, 2, 90,
     ['Esqueci a senha do computador', 'Senha expirada, não consigo entrar', 'Conta bloqueada por tentativas erradas',
      'Reset de senha do e-mail'],
     ['Senha redefinida e conta desbloqueada; usuário trocou a senha no primeiro acesso.',
      'Conta desbloqueada; orientado sobre a política de senhas.']),
    ('Usuários e acessos', 'Reativação de usuário', 'n1', 2, 2, 2, 8,
     ['Reativar usuário que voltou de licença', 'Usuário desativado por engano'],
     ['Conta reativada no AD e grupos conferidos.']),
    ('Usuários e acessos', 'Desativação de usuário', 'n1', 2, 2, 2, 10,
     ['Desligamento de servidor: desativar acessos', 'Fim de contrato de estagiário'],
     ['Conta desativada no AD e movida para a OU de desligados; caixa de e-mail preservada por 90 dias.']),
    ('Usuários e acessos', 'Acesso a pasta de rede', 'n1', 2, 2, 2, 25,
     ['Acesso à pasta do setor no servidor de arquivos', 'Liberar pasta compartilhada da secretaria'],
     ['Permissão concedida no grupo da pasta; usuário precisa sair e entrar no Windows.']),
    ('Usuários e acessos', 'Criação de e-mail institucional', 'n1', 2, 2, 2, 12,
     ['Criar e-mail institucional para o setor', 'Novo e-mail para servidor recém-contratado'],
     ['Caixa criada e configurada no Outlook e no celular do servidor.']),
    ('Usuários e acessos', 'Transferência de setor ou local', 'n1', 2, 2, 2, 10,
     ['Servidor transferido de secretaria: ajustar acessos', 'Mudança de lotação'],
     ['Usuário movido de OU, grupos do setor antigo retirados e os do novo incluídos.']),
    ('Suporte de informática', 'Manutenção de impressoras', 'campo', 1, 2, 2, 85,
     ['Impressora não imprime', 'Impressora atolando papel', 'Impressão saindo manchada', 'Impressora offline na rede',
      'Erro de spooler ao imprimir'],
     ['Fila de impressão limpa e driver reinstalado.', 'Rolete de tração limpo; troca agendada com a locadora.',
      'Impressora estava com IP alterado; fixado por reserva no DHCP.']),
    ('Suporte de informática', 'Manutenção de computador', 'campo', 1, 3, 2, 110,
     ['Computador não liga', 'Computador muito lento', 'Tela azul ao iniciar o Windows', 'Computador reiniciando sozinho',
      'Teclado e mouse não funcionam', 'Monitor sem imagem'],
     ['Fonte substituída; equipamento testado com o usuário.', 'Limpeza de arquivos temporários e troca do HD por SSD.',
      'Memória com defeito substituída.', 'Cabo de vídeo trocado.']),
    ('Suporte de informática', 'Instalação de software', 'campo', 2, 2, 1, 45,
     ['Instalar leitor de PDF', 'Instalação do certificado digital', 'Instalar programa de assinatura de documentos',
      'Instalar LibreOffice'],
     ['Software instalado pela implantação de pacotes do inventário.', 'Instalado e testado com o usuário.']),
    ('Suporte de informática', 'Atualização de software', 'campo', 2, 2, 1, 20,
     ['Atualizar navegador', 'Atualizar Java para o sistema da receita'], ['Atualização aplicada.']),
    ('Suporte de informática', 'Reparação de software', 'campo', 1, 2, 2, 30,
     ['Office pedindo ativação', 'Outlook não abre', 'Excel travando ao abrir planilhas'],
     ['Reparo do Office executado e perfil do Outlook recriado.', 'Licença reativada.']),
    ('Suporte de informática', 'Internet lenta ou instável', 'redes', 1, 3, 3, 55,
     ['Internet muito lenta no setor', 'Internet caindo toda hora', 'Sistemas web demorando para abrir'],
     ['Link da unidade com perda de pacotes; chamado aberto na operadora e normalizado.',
      'Switch do setor reiniciado e porta com erro trocada.']),
    ('Suporte de informática', 'Sem conexão com a internet', 'redes', 1, 4, 3, 40,
     ['Sem internet na unidade', 'Computador sem rede', 'Cabo de rede danificado'],
     ['Rompimento de fibra na rua; operadora reparou.', 'Patch cord substituído.', 'Nobreak do rack desligado; religado.']),
    ('Suporte de informática', 'Acesso Wi-Fi', 'redes', 2, 2, 1, 25,
     ['Liberar Wi-Fi para notebook', 'Wi-Fi da escola sem sinal na sala', 'Senha do Wi-Fi de visitantes'],
     ['Notebook cadastrado na rede corporativa.', 'Access point reposicionado.']),
    ('Suporte de informática', 'VPN / acesso remoto', 'redes', 2, 3, 2, 15,
     ['Acesso VPN para teletrabalho', 'VPN não conecta'], ['Usuário incluído no grupo da VPN e cliente configurado.']),
    ('Suporte de informática', 'Outros assuntos de rede', 'redes', 1, 2, 2, 10,
     ['Ponto de rede novo na sala', 'Mudança de layout: remanejar pontos de rede'], ['Ponto ativado no switch.']),
    ('Suporte de informática', 'Cartucho ou toner', 'campo', 2, 2, 1, 60,
     ['Troca de toner', 'Toner acabando na impressora do setor', 'Cartucho da impressora colorida'],
     ['Toner substituído; suprimento solicitado à locadora.']),
    ('Sistemas da saúde', 'Saúde: problemas de acesso', 'sistemas', 1, 4, 3, 45,
     ['Não consigo entrar no prontuário eletrônico', 'Sistema da UBS fora do ar', 'Erro ao gerar receita no sistema'],
     ['Perfil do profissional reconfigurado no prontuário.', 'Serviço do sistema reiniciado no servidor.']),
    ('Sistemas da saúde', 'Saúde: novo acesso', 'sistemas', 2, 3, 2, 25,
     ['Acesso ao prontuário para novo médico', 'Cadastro de enfermeira no sistema de vacinação'],
     ['Acesso criado com o perfil do cargo.']),
    ('Sistemas da saúde', 'Saúde: solicitações gerais', 'sistemas', 2, 2, 2, 15,
     ['Relatório de atendimentos da UBS', 'Ajuste de agenda no sistema'], ['Relatório gerado e enviado por e-mail.']),
    ('Telefonia', 'Telefonia: problemas com ramal', 'redes', 1, 3, 2, 30,
     ['Ramal mudo', 'Ramal não recebe ligações externas', 'Transferência de ligação não funciona'],
     ['Ramal reconfigurado na central.', 'Cabo do aparelho trocado.']),
    ('Telefonia', 'Telefonia: aparelho telefônico', 'redes', 2, 2, 1, 15,
     ['Aparelho telefônico com defeito', 'Novo ramal para sala'], ['Aparelho substituído.']),
    ('Telefonia', 'Telefonia: celular e chip', 'redes', 2, 2, 1, 15,
     ['Chip corporativo sem sinal', 'Celular corporativo novo para a coordenação'], ['Chip trocado pela operadora.']),
    ('Telefonia', 'Telefonia: outros', 'redes', 1, 2, 1, 5, ['Dúvida sobre lista de ramais'], ['Lista atualizada no portal.']),
    ('Sistemas corporativos', 'E-mail institucional', 'sistemas', 1, 3, 2, 45,
     ['Caixa de e-mail cheia', 'Não recebo e-mails externos', 'E-mail não sincroniza no celular'],
     ['Arquivamento configurado.', 'Regra de transporte corrigida.', 'Conta reconfigurada no celular.']),
    ('Sistemas corporativos', 'Sistema tributário', 'sistemas', 1, 4, 3, 30,
     ['Erro ao emitir guia de IPTU', 'Sistema tributário lento', 'Boleto do ISS com valor errado'],
     ['Ajuste de parâmetro do exercício feito pelo fornecedor.', 'Índice recriado no banco do sistema.']),
    ('Sistemas corporativos', 'Protocolo eletrônico', 'sistemas', 1, 3, 2, 30,
     ['Não consigo tramitar processo', 'Assinatura eletrônica falhando', 'Processo sumiu da caixa do setor'],
     ['Unidade do usuário corrigida no protocolo.', 'Certificado renovado.']),
    ('Sistemas corporativos', 'Folha de pagamento', 'sistemas', 1, 3, 3, 15,
     ['Holerite não aparece no portal do servidor', 'Erro no fechamento da folha'], ['Rotina reprocessada.']),
    ('Sistemas corporativos', 'Gestão escolar', 'sistemas', 1, 3, 2, 30,
     ['Diário de classe não abre', 'Erro ao lançar notas', 'Matrícula não aparece na turma'],
     ['Turma reprocessada no sistema escolar.', 'Permissão do professor ajustada.']),
    ('Infraestrutura', 'Servidor ou serviço fora do ar', 'infra', 1, 5, 5, 8,
     ['Servidor de arquivos indisponível', 'Sistema fora do ar para todos', 'Lentidão geral nos sistemas'],
     ['Serviço reiniciado e causa registrada no problema vinculado.', 'Disco do volume expandido.']),
    ('Infraestrutura', 'Backup e restauração', 'infra', 2, 3, 2, 10,
     ['Restaurar arquivo apagado da pasta do setor', 'Recuperar versão anterior de planilha'],
     ['Arquivo restaurado do backup da noite anterior.']),
    ('Segurança da informação', 'E-mail suspeito', 'seg', 1, 4, 3, 15,
     ['Recebi e-mail pedindo minha senha', 'Cliquei em link suspeito'],
     ['Mensagem bloqueada no filtro; senha do usuário trocada por precaução.']),
    ('Segurança da informação', 'Vírus ou malware', 'seg', 1, 5, 4, 6,
     ['Antivírus detectou ameaça', 'Arquivos com extensão estranha no computador'],
     ['Equipamento isolado, varredura completa e reinstalação.']),
]

ACOMPANHAMENTOS_TEC = [
    'Verificando o problema; retorno em breve.', 'Acesso remoto realizado para análise.',
    'Técnico a caminho da unidade.', 'Aguardando peça para substituição.', 'Encaminhado para o fornecedor do sistema.',
    'Pode testar novamente, por favor?', 'Precisamos do número de patrimônio do equipamento.',
]
ACOMPANHAMENTOS_USR = [
    'Obrigado, aguardo.', 'O problema voltou a acontecer hoje.', 'Segue o print do erro.',
    'Estarei no setor à tarde.', 'Funcionou, obrigado!',
]

KB = [  # (categoria, título, resumo, conteúdo em parágrafos, faq)
    ('Contas e senhas', 'Como trocar a senha da rede', 'Passo a passo para trocar a senha do Windows e do e-mail.',
     ['Pressione Ctrl+Alt+Del e escolha "Alterar uma senha".',
      'A nova senha precisa de ao menos 12 caracteres, com letras maiúsculas, minúsculas e números.',
      'A mesma senha vale para o e-mail institucional e para os sistemas integrados ao AD.'], True),
    ('Contas e senhas', 'Conta bloqueada: o que fazer', 'A conta bloqueia após 5 tentativas erradas.',
     ['Aguarde 15 minutos: o desbloqueio é automático.',
      'Se precisar antes, abra um chamado de "Reset de senha" pelo catálogo ou pelo WhatsApp da TI.'], True),
    ('Contas e senhas', 'Verificação em duas etapas no portal', 'Como cadastrar o aplicativo autenticador.',
     ['Instale um aplicativo autenticador no celular.', 'No portal, abra seu perfil e leia o QR Code.',
      'Guarde os códigos de recuperação em local seguro.'], True),
    ('Impressoras', 'Impressora não imprime', 'Verificações antes de abrir chamado.',
     ['Confira se a impressora está ligada e sem papel atolado.', 'No Windows, abra "Impressoras" e cancele os documentos parados na fila.',
      'Se o painel mostrar erro de toner, abra um chamado de "Cartucho ou toner".'], True),
    ('Impressoras', 'Como instalar a impressora do setor', 'Instalação pela lista de impressoras da rede.',
     ['Abra o Explorador de Arquivos e digite \\\\impressao na barra de endereços.',
      'Clique duas vezes na impressora do seu setor; o driver é instalado sozinho.'], True),
    ('Rede e internet', 'Internet lenta: o que verificar', 'Antes de abrir chamado.',
     ['Teste em outro computador do setor: se todos estão lentos, o problema é no link da unidade.',
      'Feche abas de vídeo e downloads grandes.', 'Abra o chamado informando a unidade e o horário.'], True),
    ('Rede e internet', 'Configurar a VPN para teletrabalho', 'Acesso remoto aos sistemas internos.',
     ['A VPN é liberada mediante pedido do gestor pelo catálogo de serviços.',
      'Instale o cliente indicado pela TI e entre com o usuário e a senha da rede.'], True),
    ('Rede e internet', 'Wi-Fi corporativo x visitantes', 'Qual rede usar.',
     ['PMA-CORP: equipamentos da Prefeitura cadastrados pela TI.',
      'PMA-VISITANTES: celulares e equipamentos pessoais, com acesso só à internet.'], True),
    ('E-mail', 'Configurar o e-mail institucional no celular', 'Android e iPhone.',
     ['Instale o aplicativo Outlook.', 'Entre com o endereço completo e a senha da rede.',
      'Aceite a política de segurança do dispositivo.'], True),
    ('E-mail', 'Caixa de e-mail cheia', 'Como liberar espaço.',
     ['Esvazie a pasta Itens Excluídos.', 'Mova anexos grandes para a pasta de rede do setor.',
      'Peça o arquivamento automático pelo catálogo, se necessário.'], True),
    ('Segurança', 'Como reconhecer um e-mail falso (phishing)', 'Sinais de alerta.',
     ['A TI nunca pede sua senha por e-mail.', 'Desconfie de urgência, erros de português e links encurtados.',
      'Na dúvida, encaminhe para seguranca@araraquara.example e não clique.'], True),
    ('Segurança', 'Cliquei em um link suspeito', 'O que fazer imediatamente.',
     ['Desconecte o cabo de rede ou desligue o Wi-Fi.', 'Ligue para o ramal da TI e informe o horário.',
      'Troque sua senha a partir de outro computador.'], True),
    ('Sistemas', 'Prontuário eletrônico: erro de acesso', 'Para profissionais da saúde.',
     ['Confira se o seu cadastro está vinculado à unidade em que você está.',
      'Limpe o cache do navegador e tente de novo.', 'Persistindo, abra "Saúde: problemas de acesso".'], True),
    ('Sistemas', 'Sistema escolar: lançamento de notas', 'Dúvidas frequentes dos professores.',
     ['O lançamento fica aberto até o último dia do bimestre.',
      'Se a turma não aparece, peça à secretaria da escola para conferir a atribuição de aulas.'], True),
    ('Procedimentos internos', 'Checklist de desligamento de servidor', 'Uso interno da TI.',
     ['Desativar a conta no AD e mover para a OU de desligados.', 'Recolher equipamentos e crachá.',
      'Converter a caixa de e-mail em compartilhada por 90 dias.'], False),
    ('Procedimentos internos', 'Restauração de arquivos do backup', 'Uso interno da TI.',
     ['O backup diário fica disponível por 30 dias.', 'Restaure em uma pasta à parte e avise o solicitante.'], False),
    ('Procedimentos internos', 'Troca de HD por SSD', 'Uso interno do suporte de campo.',
     ['Clone o disco com a ferramenta padrão.', 'Atualize o inventário: o agente informa o novo disco na próxima coleta.'], False),
]

SOFTWARES = [  # (nome, editora, versões, fração dos computadores que têm)
    ('Microsoft Windows 10 Pro', 'Microsoft Corporation', ['22H2'], 0.0), ('Google Chrome', 'Google LLC', ['129.0', '130.0'], 0.95),
    ('Mozilla Firefox', 'Mozilla', ['131.0', '128.3 ESR'], 0.55), ('LibreOffice', 'The Document Foundation', ['24.2', '24.8'], 0.75),
    ('Microsoft Office Standard 2019', 'Microsoft Corporation', ['16.0'], 0.35), ('Adobe Acrobat Reader', 'Adobe', ['24.003'], 0.85),
    ('7-Zip', 'Igor Pavlov', ['24.08'], 0.8), ('OCS Inventory NG Agent', 'OCS Inventory NG Team', ['2.10'], 1.0),
    ('Kaspersky Endpoint Security', 'AO Kaspersky Lab', ['12.6'], 0.9), ('AnyDesk', 'AnyDesk Software GmbH', ['8.1'], 0.6),
    ('Java 8 Update 431', 'Oracle Corporation', ['8.0.431'], 0.4), ('Assinador de documentos', 'Fornecedor do protocolo', ['3.2'], 0.3),
    ('PJe Office', 'CNJ', ['2.5'], 0.08), ('Zoom Workplace', 'Zoom Video Communications', ['6.2'], 0.25),
    ('Microsoft Teams', 'Microsoft Corporation', ['24.1'], 0.3), ('VLC media player', 'VideoLAN', ['3.0.21'], 0.3),
    ('Notepad++', 'Notepad++ Team', ['8.7'], 0.12), ('QGIS', 'QGIS.org', ['3.34'], 0.02), ('AutoCAD LT 2023', 'Autodesk', ['23.0'], 0.02),
]
CONTAS_DEMO = {'service_desk': 'tecnico', 'gestao_ti': 'coordenador', 'noc': 'noc', 'gestor_unidade': 'gestor',
               'usuario': 'servidor'}

# ======================================================================================== utilidades


def lit(v):
    """Literal SQL."""
    if v is None:
        return 'NULL'
    if isinstance(v, bool):
        return 'true' if v else 'false'
    if isinstance(v, (int, float)):
        return repr(v)
    if isinstance(v, (datetime, date)):
        return "'" + v.isoformat(sep=' ') + "'" if isinstance(v, datetime) else "'" + v.isoformat() + "'"
    return "'" + str(v).replace("'", "''") + "'"


def linhas(tabela, colunas, valores):
    if not valores:
        return ''
    out = []
    for i in range(0, len(valores), 1000):
        bloco = valores[i:i + 1000]
        out.append('INSERT INTO %s (%s) VALUES\n%s;\n' % (
            tabela, ', '.join(colunas), ',\n'.join('(' + ', '.join(lit(x) for x in v) + ')' for v in bloco)))
    return ''.join(out)


def sem_acento(s):
    return ''.join(c for c in unicodedata.normalize('NFD', s) if unicodedata.category(c) != 'Mn')


def ler_env():
    env = {}
    caminho = os.path.join(RAIZ, '.env')
    if os.path.exists(caminho):
        for linha in open(caminho, encoding='utf-8'):
            linha = linha.strip()
            if linha and not linha.startswith('#') and '=' in linha:
                k, v = linha.split('=', 1)
                env[k.strip()] = v.strip().strip('"').strip("'")
    return env


def senha_demo():
    env = ler_env()
    if env.get('CO_TI_SENHA_DEMO'):
        return env['CO_TI_SENHA_DEMO']
    senha = 'Demo-' + secrets.token_urlsafe(12)
    with open(os.path.join(RAIZ, '.env'), 'a', encoding='utf-8') as f:
        f.write('\n# senha das contas de demonstração (ferramentas/semente)\nCO_TI_SENHA_DEMO=%s\n' % senha)
    print('  senha das contas de demonstração gravada no .env (CO_TI_SENHA_DEMO)')
    return senha


class Banco:
    def __init__(self, container):
        self.cmd = ['docker', 'exec', '-i', container, 'psql', '-U', 'coti', '-d', 'coti', '-v', 'ON_ERROR_STOP=1',
                    '-q', '-X', '-At']

    def executar(self, sql, rotulo):
        t = time.time()
        r = subprocess.run(self.cmd, input=sql.encode('utf-8'), capture_output=True)
        if r.returncode != 0:
            sys.exit('falhou em "%s":\n%s' % (rotulo, r.stderr.decode('utf-8', 'replace')[-3000:]))
        print('  %-38s %6.1fs' % (rotulo, time.time() - t))
        return r.stdout.decode('utf-8').strip()


# ======================================================================================== estrutura


class Ids:
    def __init__(self):
        self.prox = {}

    def __call__(self, tabela, inicio=1000):
        n = self.prox.get(tabela, inicio)
        self.prox[tabela] = n + 1
        return n


def gerar_estrutura(rnd, senha):
    ids = Ids()
    sql = ['BEGIN;\nCREATE EXTENSION IF NOT EXISTS pgcrypto;\n',
           "CREATE TEMP TABLE senha_demo AS SELECT crypt(%s, gen_salt('bf', 12)) AS h;\n" % lit(senha)]
    criado = AGORA - timedelta(days=3 * 365 + 30)

    # ---------------- departamentos e unidades
    sql.append("UPDATE system_dept SET name = 'Prefeitura de Araraquara' WHERE id = 100;\n")
    depts, unidades = [], []
    info = {}  # dept_id -> {secretaria, unidade, tipo}
    sec_ids = {}
    subunidades = []  # unidades das escolas/UBS antes das sedes (o vínculo dept → unidade usa a primeira que achar)
    sedes = []
    for ordem, (sec, setores, grupos) in enumerate(SECRETARIAS, 1):
        sid = ids('dept')
        sec_ids[sec] = sid
        depts.append((sid, sec, 100, ordem, 0, 1, '1', '1', 0))
        info[sid] = dict(sec=sec, tipo='sede')
        for i, s in enumerate(setores, 1):
            did = ids('dept')
            depts.append((did, s, sid, i, 0, 1, '1', '1', 0))
            info[did] = dict(sec=sec, tipo='setor', setor=s)
        for prefixo, bairros in grupos:
            for i, b in enumerate(bairros, 1):
                did = ids('dept')
                nome = ('%s %s' % (prefixo, b))[:30]
                depts.append((did, nome, sid, 10 + i, 0, 1, '1', '1', 0))
                info[did] = dict(sec=sec, tipo=prefixo, setor=nome)
                subunidades.append((did, nome, b))
        sedes.append((sid, 'Paço Municipal' if sec == 'Gabinete do Prefeito' else 'Sede - ' + sec, SEDES[sec]))
    sql.append(linhas('system_dept', ['id', 'name', 'parent_id', 'sort', 'status', 'tenant_id', 'creator', 'updater', 'deleted'], depts))

    unidade_por_dept = {}
    for did, nome, bairro in subunidades + sedes:
        uid = ids('unidade')
        lat, lon = BAIRROS[bairro]
        lat += rnd.uniform(-0.0025, 0.0025)
        lon += rnd.uniform(-0.0025, 0.0025)
        if nome == 'Paço Municipal':
            lat, lon = CENTRO
            end = ('14801-300', 'Rua São Bento', '840')
        else:
            end = ('148%02d-%03d' % (rnd.randint(0, 20), rnd.randint(0, 999)), rnd.choice(
                ['Rua', 'Avenida']) + ' ' + rnd.choice(['das Flores', 'Brasil', 'José Bonifácio', 'Sete de Setembro',
                                                       'Padre Duarte', 'Bento de Abreu', 'Maria Antônia', 'XV de Novembro']),
                   str(rnd.randint(10, 2500)))
        unidades.append((uid, did, nome, 'Araraquara', round(lat, 6), round(lon, 6), 0, end[0], end[1], end[2], bairro,
                         'SP', '1', '1', 0))
        unidade_por_dept[did] = uid
    sql.append(linhas('monitor_unit', ['id', 'dept_id', 'name', 'city', 'latitude', 'longitude', 'status', 'cep',
                                       'logradouro', 'numero', 'bairro', 'uf', 'creator', 'updater', 'deleted'], unidades))

    # ---------------- pessoas
    usuarios, papeis, demo = [], [], {}
    usados = set()
    pessoas = []  # (id, nome, dept, papel)

    def pessoa(dept, papel, cargo):
        while True:
            fem = rnd.random() < 0.55
            nome = rnd.choice(NOMES_F if fem else NOMES_M)
            sob = rnd.sample(SOBRENOMES, 2)
            login = sem_acento('%s.%s' % (nome, sob[1])).lower()
            if login not in usados:
                break
        usados.add(login)
        uid = ids('users')
        completo = '%s %s %s' % (nome, sob[0], sob[1])
        senha_local = None
        if papel in CONTAS_DEMO and CONTAS_DEMO[papel] not in demo.values():
            login = CONTAS_DEMO[papel] + '.demo'
            demo[uid] = CONTAS_DEMO[papel]
            senha_local = True
        usuarios.append((uid, login, senha_local, completo, cargo, dept, login.replace('.demo', '') + '@araraquara.example'
                         if senha_local else login + '@araraquara.example',
                         '(16) 9%04d-%04d' % (rnd.randint(1000, 9999), rnd.randint(0, 9999)), 2 if fem else 1))
        papeis.append((uid, PERFIL['usuario']))
        if papel != 'usuario':
            papeis.append((uid, PERFIL[papel]))
        pessoas.append((uid, completo, dept, papel))
        return uid

    ti = {s: d for d, x in info.items() if x['sec'] == 'Tecnologia da Informação' and x['tipo'] == 'setor'
          for s in [x['setor']]}
    tecnicos = {k: [] for k, *_ in EQUIPES}
    coord_ti = pessoa(sec_ids['Tecnologia da Informação'], 'gestao_ti', 'Coordenador de TI')
    for _ in range(9):
        tecnicos['n1'].append(pessoa(ti['Service Desk'], 'service_desk', 'Técnico de suporte (N1)'))
    for _ in range(7):
        tecnicos['campo'].append(pessoa(ti['Service Desk'], 'service_desk', 'Técnico de campo'))
    for _ in range(5):
        tecnicos['infra'].append(pessoa(ti['Infraestrutura e Datacenter'], 'noc', 'Analista de infraestrutura'))
    for _ in range(4):
        tecnicos['redes'].append(pessoa(ti['Redes e Telefonia'], 'noc', 'Analista de redes'))
    for _ in range(5):
        tecnicos['sistemas'].append(pessoa(ti['Sistemas'], 'service_desk', 'Analista de sistemas'))
    for _ in range(3):
        tecnicos['seg'].append(pessoa(ti['Segurança da Informação'], 'soc', 'Analista de segurança'))
    gestores = {}
    for did, x in info.items():
        if x['sec'] == 'Tecnologia da Informação':
            continue
        if x['tipo'] == 'sede':
            gestores[did] = pessoa(did, 'gestor_unidade', 'Secretário(a) municipal')
            n = rnd.randint(2, 4)
        elif x['tipo'] == 'setor':
            n = rnd.randint(5, 10)
        else:
            gestores[did] = pessoa(did, 'gestor_unidade', 'Diretor(a) / coordenador(a) da unidade')
            n = rnd.randint(6, 10)
        for _ in range(n):
            pessoa(did, 'usuario', 'Servidor(a)')
    solicitantes = [p for p in pessoas if p[3] in ('usuario', 'gestor_unidade')]

    sql.append(linhas('system_users', ['id', 'username', 'nickname', 'remark', 'dept_id', 'email', 'mobile', 'sex', 'status',
                                       'tenant_id', 'creator', 'updater', 'deleted', 'create_time'],
                      [(u[0], u[1], u[3], u[4], u[5], u[6], u[7], u[8], 0, 1, '1', '1', 0, criado) for u in usuarios]))
    sql.append('UPDATE system_users SET password = (SELECT h FROM senha_demo) WHERE id IN (%s);\n'
               % ','.join(str(u[0]) for u in usuarios if u[2]))
    sql.append(linhas('system_user_role', ['id', 'user_id', 'role_id', 'tenant_id', 'creator', 'updater', 'deleted'],
                      [(ids('user_role'), u, r, 1, '1', '1', 0) for u, r in papeis]))

    # ---------------- Service Desk: equipes, SLAs, categorias
    equipe_id = {}
    eq_rows, membros = [], []
    for chave, nome, desc, setor in EQUIPES:
        eid = ids('equipe')
        equipe_id[chave] = eid
        eq_rows.append((eid, nome, desc, True, '1', '1', 0))
        for i, u in enumerate(tecnicos[chave]):
            membros.append((ids('membro'), eid, u, i == 0, '1', '1', 0))
    membros.append((ids('membro'), equipe_id['n1'], coord_ti, True, '1', '1', 0))
    sql.append(linhas('itsm.equipe', ['id', 'nome', 'descricao', 'ativo', 'creator', 'updater', 'deleted'], eq_rows))
    sql.append(linhas('itsm.equipe_membro', ['id', 'equipe_id', 'user_id', 'supervisor', 'creator', 'updater', 'deleted'], membros))

    slas, sla_prio = [], []
    for prio, ((ta, ua), (ts, us)) in SLA_PRIORIDADE.items():
        rot = ['', 'muito baixa', 'baixa', 'média', 'alta', 'muito alta', 'crítica'][prio]
        a, s = ids('sla'), ids('sla')
        slas.append((a, 'Atendimento - prioridade ' + rot, 1, ta, ua, 1, False, True, '1', '1', 0))
        slas.append((s, 'Solução - prioridade ' + rot, 0, ts, us, 1, False, True, '1', '1', 0))
        sla_prio.append((prio, a, s))
    sql.append(linhas('itsm.sla', ['id', 'nome', 'tipo', 'tempo', 'unidade', 'calendario_id', 'fim_expediente', 'ativo',
                                   'creator', 'updater', 'deleted'], slas))
    sql.append('DELETE FROM itsm.sla_prioridade;\n' + linhas('itsm.sla_prioridade', ['prioridade', 'sla_atendimento_id',
                                                                                   'sla_solucao_id'], sla_prio))

    cat_rows, modelos, grupo_id = [], [], {}
    ordem = 0
    for grupo, nome, eq, tipo, urg, imp, peso, titulos, solucoes in CATEGORIAS:
        if grupo not in grupo_id:
            gid = ids('categoria')
            grupo_id[grupo] = gid
            cat_rows.append((gid, 0, grupo, grupo, 1, True, True, True, True, len(grupo_id), None, '1', '1', 0))
        ordem += 1
        cid = ids('categoria')
        cat_rows.append((cid, grupo_id[grupo], nome, '%s > %s' % (grupo, nome), 2, True, tipo == 1, tipo == 2, True,
                         ordem, equipe_id[eq], '1', '1', 0))
        modelos.append((cid, equipe_id[eq], tipo, urg, imp, peso, titulos, solucoes, grupo == 'Usuários e acessos'))
    sql.append(linhas('itsm.categoria', ['id', 'parent_id', 'nome', 'nome_completo', 'nivel', 'visivel_portal',
                                         'para_incidente', 'para_requisicao', 'ativo', 'ordem', 'equipe_id', 'creator',
                                         'updater', 'deleted'], cat_rows))

    # ---------------- base de conhecimento
    kb_cat, kb_art = {}, []
    for categoria, titulo, resumo, paragrafos, faq in KB:
        if categoria not in kb_cat:
            kb_cat[categoria] = ids('kb_cat')
        html = ''.join('<p>%s</p>' % p for p in paragrafos)
        quando = AGORA - timedelta(days=rnd.randint(30, 900))
        kb_art.append((ids('kb'), titulo, resumo, html, ' '.join(paragrafos), kb_cat[categoria], faq, True, False,
                       rnd.choice(tecnicos['n1'] + tecnicos['sistemas']), 1, rnd.randint(20, 2500), rnd.randint(0, 80),
                       rnd.randint(0, 6), '1', '1', 0, quando, quando))
    sql.append(linhas('itsm.kb_categoria', ['id', 'nome', 'nome_completo', 'ordem', 'creator', 'updater', 'deleted'],
                      [(i, n, n, k, '1', '1', 0) for k, (n, i) in enumerate(kb_cat.items(), 1)]))
    sql.append(linhas('itsm.kb_artigo', ['id', 'titulo', 'resumo', 'conteudo', 'texto', 'categoria_id', 'faq', 'publicado',
                                         'fixado', 'autor_id', 'revisao', 'visualizacoes', 'util', 'nao_util', 'creator',
                                         'updater', 'deleted', 'create_time', 'update_time'], kb_art))

    # ---------------- governança
    forn = [('Telecom Interior Ltda.', 'Operadora', 'Links de internet e MPLS das unidades'),
            ('Print Service Locação', 'Locação', 'Impressoras multifuncionais e suprimentos'),
            ('Sistemas Públicos S.A.', 'Software', 'Sistema tributário e folha de pagamento'),
            ('SaúdeTec Informática', 'Software', 'Prontuário eletrônico das UBS'),
            ('EducaNet Sistemas', 'Software', 'Gestão escolar'),
            ('Distribuidora de Licenças Ltda.', 'Revenda', 'Licenças Microsoft e antivírus'),
            ('Datacenter Manutenção Ltda.', 'Serviços', 'Manutenção de nobreaks e ar-condicionado')]
    f_rows, fid = [], {}
    for nome, tipo, obs in forn:
        fid[nome] = ids('fornecedor')
        f_rows.append((fid[nome], nome, tipo, 'contato@' + sem_acento(nome.split()[0]).lower() + '.example', 'Araraquara',
                       'SP', obs, True, '1', '1', 0))
    sql.append(linhas('itsm.fornecedor', ['id', 'nome', 'tipo', 'email', 'cidade', 'uf', 'observacao', 'ativo', 'creator',
                                          'updater', 'deleted'], f_rows))
    contratos, cid_ = [], {}
    for nome, f, tipo, inicio, meses in [
            ('Links de dados das unidades', 'Telecom Interior Ltda.', 'Serviço', date(AGORA.year - 2, 3, 1), 60),
            ('Outsourcing de impressão', 'Print Service Locação', 'Locação', date(AGORA.year - 1, 7, 1), 36),
            ('Sistema tributário e folha', 'Sistemas Públicos S.A.', 'Licença de uso', date(AGORA.year - 3, 1, 1), 48),
            ('Prontuário eletrônico', 'SaúdeTec Informática', 'Licença de uso', date(AGORA.year - 2, 5, 1), 36),
            ('Gestão escolar', 'EducaNet Sistemas', 'Licença de uso', date(AGORA.year - 1, 2, 1), 24),
            ('Manutenção do datacenter', 'Datacenter Manutenção Ltda.', 'Manutenção', date(AGORA.year - 1, 11, 1), 12)]:
        cid_[nome] = ids('contrato')
        contratos.append((cid_[nome], nome, '%03d/%d' % (len(contratos) + 11, inicio.year), tipo, fid[f],
                          sec_ids['Tecnologia da Informação'], coord_ti, inicio, meses, 3, 1, 60, False, '1', '1', 0))
    sql.append(linhas('itsm.contrato', ['id', 'nome', 'numero', 'tipo', 'fornecedor_id', 'dept_id', 'responsavel_id',
                                        'inicio', 'duracao_meses', 'aviso_previo_meses', 'renovacao', 'alerta_dias',
                                        'encerrado', 'creator', 'updater', 'deleted'], contratos))
    sql.append(linhas('itsm.licenca', ['id', 'software', 'fabricante', 'nome', 'tipo', 'quantidade', 'permite_excesso',
                                       'expira', 'alerta_dias', 'fornecedor_id', 'dept_id', 'responsavel_id', 'ativo',
                                       'creator', 'updater', 'deleted'],
                      [(ids('licenca'), 'Microsoft Office Standard 2019', 'Microsoft', 'Office 2019 - volume', 'Perpétua',
                        450, False, None, 60, fid['Distribuidora de Licenças Ltda.'], sec_ids['Tecnologia da Informação'],
                        coord_ti, True, '1', '1', 0),
                       (ids('licenca'), 'Kaspersky Endpoint Security', 'Kaspersky', 'Antivírus corporativo', 'Assinatura',
                        1300, False, date(AGORA.year + 1, 3, 31), 60, fid['Distribuidora de Licenças Ltda.'],
                        sec_ids['Tecnologia da Informação'], coord_ti, True, '1', '1', 0),
                       (ids('licenca'), 'AutoCAD LT 2023', 'Autodesk', 'AutoCAD LT - Obras', 'Assinatura', 15, False,
                        date(AGORA.year, 12, 31), 45, fid['Distribuidora de Licenças Ltda.'], sec_ids['Obras e Serviços Públicos'],
                        coord_ti, True, '1', '1', 0)]))

    # ---------------- inventário
    sw_rows, sw_id = [], {}
    for nome, editora, _v, _f in SOFTWARES:
        sw_id[nome] = ids('software')
        chave = (sem_acento(nome).lower().strip() + '|' + sem_acento(editora).lower().strip())[:520]
        sw_rows.append((sw_id[nome], nome, editora, chave, '1', '1', 0))
    sql.append(linhas('cmdb.software', ['id', 'nome', 'editora', 'chave', 'creator', 'updater', 'deleted'], sw_rows))

    ativos, comps, inst = [], [], []
    patrimonio = 30000
    modelos_pc = [('Dell Inc.', 'OptiPlex 3080'), ('Dell Inc.', 'OptiPlex 7010'), ('Lenovo', 'ThinkCentre M70q'),
                  ('Positivo Tecnologia', 'Master D580'), ('HP', 'ProDesk 400 G7')]
    modelos_nb = [('Dell Inc.', 'Latitude 3420'), ('Lenovo', 'ThinkPad E14'), ('Positivo Tecnologia', 'Master N2140')]
    so_lista = [('Microsoft Windows 10 Pro', '10.0.19045', False, 0.55), ('Microsoft Windows 11 Pro', '10.0.22631', False, 0.33),
                ('Microsoft Windows 8.1 Pro', '6.3.9600', True, 0.07), ('Microsoft Windows 7 Professional', '6.1.7601', True, 0.05)]
    sub_ip = {}
    for p in pessoas:
        uid, nome, dept, papel = p
        und = unidade_por_dept.get(dept) or unidade_por_dept.get(next(d for d, x in info.items()
                                                                       if x['sec'] == info[dept]['sec'] and x['tipo'] == 'sede'))
        sub_ip.setdefault(und, len(sub_ip) + 10)
        if rnd.random() < 0.12:
            continue  # nem todo mundo tem computador próprio
        notebook = papel != 'usuario' or rnd.random() < 0.15
        fab, mod = rnd.choice(modelos_nb if notebook else modelos_pc)
        r = rnd.random()
        acum = 0
        for so in so_lista:
            acum += so[3]
            if r <= acum:
                break
        ssd = rnd.random() < 0.6
        disco_gb = rnd.choice([240, 480, 512]) if ssd else rnd.choice([500, 1000])
        livre = disco_gb * (rnd.uniform(0.03, 0.09) if rnd.random() < 0.04 else rnd.uniform(0.2, 0.7))
        # contato: a maioria recente; alguns sumidos (desligados, quebrados, sem rede)
        contato = AGORA - (timedelta(days=rnd.randint(31, 200)) if rnd.random() < 0.04
                           else timedelta(minutes=rnd.randint(5, 60 * 30)))
        av = 'ausente' if so[2] and rnd.random() < 0.5 else ('desatualizado' if rnd.random() < 0.06 else 'ativo')
        patrimonio += 1
        aid = ids('ativo')
        host = 'PMA-%s%05d' % ('NB' if notebook else 'PC', patrimonio)
        primeiro = AGORA - timedelta(days=rnd.randint(60, 1000))
        ativos.append((aid, 'notebook' if notebook else 'desktop', host, 'em_uso', str(patrimonio),
                       'BR' + uuid.UUID(int=rnd.getrandbits(128)).hex[:10].upper(), fab, mod,
                       str(uuid.UUID(int=rnd.getrandbits(128))), so[0], so[1], 'x64', so[2],
                       rnd.choice(['Intel Core i5-10400', 'Intel Core i5-12500', 'Intel Core i3-10100', 'AMD Ryzen 5 5600G']),
                       rnd.choice([4, 6, 8]), rnd.choice([4096, 8192, 8192, 16384]), disco_gb * 1024, int(livre * 1024),
                       '10.%d.%d.%d' % (sub_ip[und] // 250, sub_ip[und] % 250, rnd.randint(20, 240)),
                       ':'.join('%02x' % rnd.randint(0, 255) for _ in range(6)), 'PMA', 'PMA\\' + usuarios[uid - 1000][1],
                       uid, dept, av, 'ocs', host + '-' + primeiro.strftime('%Y-%m-%d-%H-%M-%S'), 'OCS-NG_WINDOWS_AGENT_v2.10',
                       primeiro, contato, contato, 'ssd' if ssd else 'hdd', '1', '1', 0, primeiro))
        comps.append((ids('comp'), aid, 'disco', 'disco0', '%s %d GB' % ('SSD' if ssd else 'HD', disco_gb), '1', '1', 0))
        comps.append((ids('comp'), aid, 'memoria', 'slot0', 'DDR4 %d MB' % ativos[-1][15], '1', '1', 0))
        for nome_sw, _e, versoes, frac in SOFTWARES:
            if rnd.random() < frac:
                inst.append((ids('ativo_sw'), aid, sw_id[nome_sw], rnd.choice(versoes), primeiro.date(), 'x64', '1', '1', 0))
    # servidores e impressoras
    for nome, tipo, so in [('SRV-AD01', 'virtual', 'Microsoft Windows Server 2022 Standard'),
                           ('SRV-AD02', 'virtual', 'Microsoft Windows Server 2022 Standard'),
                           ('SRV-ARQUIVOS', 'virtual', 'Microsoft Windows Server 2019 Standard'),
                           ('SRV-IMPRESSAO', 'virtual', 'Microsoft Windows Server 2016 Standard'),
                           ('SRV-SISTEMAS01', 'virtual', 'Ubuntu 22.04 LTS'), ('SRV-BANCO01', 'virtual', 'Ubuntu 22.04 LTS'),
                           ('SRV-BACKUP', 'servidor', 'Ubuntu 22.04 LTS'), ('HV-HOST01', 'servidor', 'Proxmox VE 8'),
                           ('HV-HOST02', 'servidor', 'Proxmox VE 8')]:
        aid = ids('ativo')
        primeiro = AGORA - timedelta(days=700)
        ativos.append((aid, tipo, nome, 'em_uso', None, None, 'Dell Inc.' if tipo == 'servidor' else 'QEMU',
                       'PowerEdge R650' if tipo == 'servidor' else 'Standard PC', str(uuid.UUID(int=rnd.getrandbits(128))),
                       so, None, 'x64', False, 'Intel Xeon Silver 4314', 16, 65536, 2048 * 1024, 900 * 1024,
                       '10.0.0.%d' % (10 + len(ativos) % 200), None, 'PMA', None, None, sec_ids['Tecnologia da Informação'],
                       'ativo' if 'Windows' in so else 'desconhecido', 'ocs', nome + '-agente', 'OCS-NG_UNIX_AGENT_v2.10',
                       primeiro, AGORA - timedelta(minutes=rnd.randint(5, 120)), AGORA - timedelta(minutes=rnd.randint(5, 120)),
                       'ssd', '1', '1', 0, primeiro))
    for d, x in info.items():
        if x['tipo'] in ('sede', 'UBS', 'EMEF', 'CER', 'CRAS') and rnd.random() < 0.9:
            aid = ids('ativo')
            patrimonio += 1
            ativos.append((aid, 'impressora', 'IMP-%05d' % patrimonio, 'em_uso', str(patrimonio), None, 'Brother',
                           'MFC-L6902DW', None, None, None, None, False, None, None, None, None, None,
                           '10.%d.%d.%d' % (sub_ip.get(unidade_por_dept.get(d), 9) // 250, sub_ip.get(unidade_por_dept.get(d), 9) % 250, 5),
                           None, None, None, None, d, 'desconhecido', 'snmp', None, None, AGORA - timedelta(days=500),
                           AGORA - timedelta(hours=rnd.randint(1, 6)), AGORA - timedelta(hours=rnd.randint(1, 6)), None,
                           '1', '1', 0, AGORA - timedelta(days=500)))
    sql.append(linhas('cmdb.ativo', ['id', 'tipo', 'nome', 'situacao', 'patrimonio', 'numero_serie', 'fabricante', 'modelo',
                                     'uuid', 'so_nome', 'so_versao', 'so_arquitetura', 'so_sem_suporte', 'cpu', 'cpu_nucleos',
                                     'memoria_mb', 'disco_total_mb', 'disco_livre_mb', 'ip', 'mac', 'dominio',
                                     'usuario_login', 'usuario_id', 'dept_id', 'antivirus', 'origem', 'device_id', 'agente',
                                     'primeiro_inventario', 'ultimo_inventario', 'ultimo_contato', 'disco_tipo', 'creator',
                                     'updater', 'deleted', 'create_time'], ativos))
    sql.append(linhas('cmdb.ativo_componente', ['id', 'ativo_id', 'tipo', 'chave', 'descricao', 'creator', 'updater',
                                                'deleted'], comps))
    sql.append(linhas('cmdb.ativo_software', ['id', 'ativo_id', 'software_id', 'versao', 'instalado_em', 'arquitetura',
                                              'creator', 'updater', 'deleted'], inst))

    # ---------------- monitoramento: serviços reais da instalação + links das unidades (pausados até ter o IP)
    paco = unidade_por_dept[sec_ids['Gabinete do Prefeito']]
    sede_ti = unidade_por_dept[sec_ids['Tecnologia da Informação']]
    mon = []

    def monitor(nome, tipo, alvo, porta, unidade, papel, ativo, intervalo=60, **extra):
        m = dict(id=ids('monitor'), nome=nome, tipo=tipo, alvo=alvo, porta=porta, unidade_id=unidade, papel=papel,
                 ativo=ativo, intervalo_s=intervalo, tentativas=1, intervalo_tentativa_s=30, tempo_limite_s=10,
                 reenviar_a_cada=0, invertido=False, ignorar_tls=False, max_redirecionamentos=10, lento_ms=1500,
                 cert_aviso_dias=21, codigos_aceitos='200-299', metodo='GET', abrir_chamado=False,
                 dns_tipo='A', palavra_invertida=False, quedas_seguidas=0, tentativa_atual=0, chamado_solucionar=True,
                 chamado_urgencia=4, descricao=None, dns_servidor=None,
                 creator='1', updater='1', deleted=0)
        m.update(extra)
        mon.append(m)

    monitor('CO-TI · Portal', 'http', 'http://frontend/', None, sede_ti, 'servico', True)
    monitor('CO-TI · Banco de dados', 'tcp', 'postgres', 5432, sede_ti, 'servico', True)
    monitor('CO-TI · Cache (Redis)', 'tcp', 'redis', 6379, sede_ti, 'servico', True)
    monitor('Portal da Prefeitura', 'http', 'https://www.araraquara.sp.gov.br/', None, paco, 'servico', True, 300)
    monitor('DNS público (resolução externa)', 'dns', 'www.araraquara.sp.gov.br', None, paco, 'servico', True, 300,
            dns_tipo='A', dns_servidor='1.1.1.1')
    for u in unidades:
        monitor('Link · %s' % u[2], 'ping', '10.%d.%d.1' % (sub_ip.get(u[0], 9) // 250, sub_ip.get(u[0], 9) % 250), None,
                u[0], 'principal', False,
                descricao='Roteador da unidade. Pausado: confira o IP do roteador e ative o monitor.')
    cols = list(mon[0].keys() | set().union(*[m.keys() for m in mon]))
    sql.append(linhas('monitor.monitor', cols, [tuple(m.get(c) for c in cols) for m in mon]))

    # ---------------- atendimento por WhatsApp
    caixa = ids('caixa')
    sql.append(linhas('atendimento.caixa', ['id', 'nome', 'canal', 'ativo', 'config', 'saudacao', 'equipe_id',
                                            'atribuicao_automatica', 'limite_por_agente', 'reabrir_horas', 'calendario_id',
                                            'csat_ativo', 'robo_ativo', 'fluxo_ativo', 'avisos_ativo', 'creator', 'updater',
                                            'deleted'],
                      [(caixa, 'WhatsApp da TI', 'whatsapp_cloud', True, '{}',
                        'Olá! Você está falando com a TI da Prefeitura. Como podemos ajudar?', equipe_id['n1'], True, 5, 48,
                        1, False, False, False, False, '1', '1', 0)]))
    sql.append(linhas('atendimento.caixa_agente', ['id', 'caixa_id', 'user_id', 'creator', 'updater', 'deleted'],
                      [(ids('caixa_agente'), caixa, u, '1', '1', 0) for u in tecnicos['n1']]))
    contatos, canais, conversas, msgs = [], [], [], []
    for p in rnd.sample(solicitantes, min(260, len(solicitantes))):
        cid = ids('contato')
        fone = '5516' + '9%08d' % rnd.randint(0, 99999999)
        contatos.append((cid, p[1], '+' + fone, None, p[0], AGORA - timedelta(days=rnd.randint(0, 180)), '1', '1', 0))
        ccid = ids('contato_canal')
        canais.append((ccid, cid, caixa, fone, '1', '1', 0))
        for _ in range(rnd.randint(1, 8)):
            inicio = AGORA - timedelta(days=rnd.uniform(0, 180))
            if inicio.weekday() >= 5:
                inicio -= timedelta(days=2)
            inicio = inicio.replace(hour=rnd.randint(8, 17), minute=rnd.randint(0, 59))
            agente = rnd.choice(tecnicos['n1'])
            aberta = inicio > AGORA - timedelta(hours=20) and rnd.random() < 0.6
            resp = inicio + timedelta(minutes=rnd.randint(1, 25))
            fim = resp + timedelta(minutes=rnd.randint(3, 90))
            vid = ids('conversa')
            conversas.append((vid, caixa, cid, ccid, 0 if aberta else 1, agente, 2, resp, None if aberta else fim, inicio,
                              fim if not aberta else resp, '1', '1', 0, inicio))
            assunto = rnd.choice(CATEGORIAS)
            texto = rnd.choice(assunto[7])
            for tipo, rem, rid, conteudo, quando in [
                    (0, 'contato', None, 'Bom dia! ' + texto + '.', inicio),
                    (1, 'agente', agente, 'Olá, %s! Vou verificar para você.' % p[1].split()[0], resp),
                    (0, 'contato', None, rnd.choice(['Obrigado!', 'Certo, aguardo.', 'É no meu setor mesmo.']),
                     resp + timedelta(minutes=2)),
                    (1, 'agente', agente, rnd.choice(assunto[8]), fim)][:2 if aberta else 4]:
                msgs.append((ids('mensagem'), vid, tipo, False, rem, rid, conteudo, 'texto', 'entregue', '1', '1', 0, quando))
    sql.append(linhas('atendimento.contato', ['id', 'nome', 'telefone', 'email', 'user_id', 'ultima_atividade_em',
                                              'creator', 'updater', 'deleted'], contatos))
    sql.append(linhas('atendimento.contato_canal', ['id', 'contato_id', 'caixa_id', 'origem_id', 'creator', 'updater',
                                                    'deleted'], canais))
    sql.append(linhas('atendimento.conversa', ['id', 'caixa_id', 'contato_id', 'contato_canal_id', 'status', 'agente_id',
                                               'prioridade', 'primeira_resposta_em', 'resolvida_em', 'ultima_entrada_em',
                                               'ultima_atividade_em', 'creator', 'updater', 'deleted', 'create_time'], conversas))
    sql.append(linhas('atendimento.mensagem', ['id', 'conversa_id', 'tipo', 'privada', 'remetente_tipo', 'remetente_id',
                                               'conteudo', 'formato', 'status', 'creator', 'updater', 'deleted',
                                               'create_time'], msgs))

    # ---------------- sequências e pools para a geração dos chamados
    for tabela, seq, coluna in [('system_dept', 'system_dept_seq', 'id'), ('monitor_unit', 'monitor_unit_seq', 'id'),
                                ('system_users', 'system_users_seq', 'id'), ('system_user_role', 'system_user_role_seq', 'id'),
                                ('itsm.equipe', 'itsm.equipe_seq', 'id'), ('itsm.equipe_membro', 'itsm.equipe_membro_seq', 'id'),
                                ('itsm.sla', 'itsm.sla_seq', 'id'), ('itsm.categoria', 'itsm.categoria_seq', 'id'),
                                ('itsm.kb_categoria', 'itsm.kb_categoria_seq', 'id'), ('itsm.kb_artigo', 'itsm.kb_artigo_seq', 'id'),
                                ('itsm.fornecedor', 'itsm.fornecedor_seq', 'id'), ('itsm.contrato', 'itsm.contrato_seq', 'id'),
                                ('itsm.licenca', 'itsm.licenca_seq', 'id'), ('cmdb.software', 'cmdb.software_seq', 'id'),
                                ('cmdb.ativo', 'cmdb.ativo_seq', 'id'), ('cmdb.ativo_componente', 'cmdb.ativo_componente_seq', 'id'),
                                ('cmdb.ativo_software', 'cmdb.ativo_software_seq', 'id'), ('monitor.monitor', 'monitor.monitor_seq', 'id'),
                                ('atendimento.caixa', 'atendimento.caixa_seq', 'id'),
                                ('atendimento.caixa_agente', 'atendimento.caixa_agente_seq', 'id'),
                                ('atendimento.contato', 'atendimento.contato_seq', 'id'),
                                ('atendimento.contato_canal', 'atendimento.contato_canal_seq', 'id'),
                                ('atendimento.conversa', 'atendimento.conversa_seq', 'id'),
                                ('atendimento.mensagem', 'atendimento.mensagem_seq', 'id')]:
        sql.append("SELECT setval('%s', (SELECT max(%s) FROM %s));\n" % (seq, coluna, tabela))
    sql.append('COMMIT;\n')

    pools = dict(
        solicitantes=[(p[0], p[2]) for p in solicitantes],
        tecnicos=[(equipe_id[k], u) for k, us in tecnicos.items() for u in us],
        modelos=modelos,
        demo={papel: uid for uid, papel in demo.items()},
        coord_ti=coord_ti,
        dept_ti=sec_ids['Tecnologia da Informação'],
        equipes=equipe_id,
        tecnicos_por_equipe=tecnicos,
        gestores=sorted(gestores.values()),
        resumo=dict(departamentos=len(depts), unidades=len(unidades), pessoas=len(usuarios), equipes=len(eq_rows),
                    categorias=len(cat_rows), artigos=len(kb_art), ativos=len(ativos), softwares_instalados=len(inst),
                    monitores=len(mon), conversas=len(conversas), mensagens=len(msgs)),
        usuarios={u[0]: u[1] for u in usuarios},
    )
    return ''.join(sql), pools


# ======================================================================================== catálogo pela API


def importar_catalogo(banco, url):
    token = secrets.token_hex(16)
    banco.executar("""INSERT INTO system_oauth2_access_token (id, user_id, user_type, user_info, access_token, refresh_token,
        client_id, expires_time, creator, updater, deleted, tenant_id)
        VALUES (nextval('system_oauth2_access_token_seq'), 1, 2, '{"nickname":"Administrador","deptId":"100"}', %s, %s,
        'default', now() + interval '10 minutes', '1', '1', 0, 1);""" % (lit(token), lit(secrets.token_hex(16))),
                   'sessão temporária do admin')
    try:
        corpo_arq = open(os.path.join(RAIZ, 'exemplos', 'catalogo-servicos.json'), 'rb').read()
        limite = 'coti' + secrets.token_hex(8)
        corpo = (('--%s\r\nContent-Disposition: form-data; name="arquivo"; filename="catalogo.json"\r\n'
                  'Content-Type: application/json\r\n\r\n' % limite).encode() + corpo_arq + ('\r\n--%s--\r\n' % limite).encode())
        req = urllib.request.Request(url + '/admin-api/itsm/formulario/importar?criarCategorias=true', data=corpo,
                                     method='POST', headers={'Authorization': 'Bearer ' + token, 'tenant-id': '1',
                                                             'Content-Type': 'multipart/form-data; boundary=' + limite})
        t = time.time()
        resp = json.loads(urllib.request.urlopen(req, timeout=120).read())
        if resp.get('code') != 0:
            sys.exit('importação do catálogo falhou: %s' % resp)
        print('  %-38s %6.1fs  %s' % ('catálogo de serviços (API)', time.time() - t,
                                     {k: v for k, v in resp['data'].items() if not isinstance(v, list) or v}))
        try:
            urllib.request.urlopen(urllib.request.Request(url + '/admin-api/system/auth/logout', method='POST', data=b'',
                                                          headers={'Authorization': 'Bearer ' + token, 'tenant-id': '1'}),
                                   timeout=30)
        except Exception:
            pass
    finally:
        banco.executar('DELETE FROM system_oauth2_access_token WHERE access_token = %s;' % lit(token),
                       'sessão temporária encerrada')


# ======================================================================================== chamados (volume)


def gerar_chamados(rnd, pools, anos, total):
    """Gera o SQL que cria os chamados dentro do PostgreSQL (generate_series), sem trafegar milhões de linhas."""
    inicio = (AGORA - timedelta(days=int(anos * 365))).date()
    dias = []
    d = inicio
    pesos = []
    while d <= AGORA.date():
        dow = d.weekday()
        fator = [1.15, 1.05, 1.0, 0.98, 0.85, 0.07, 0.03][dow]
        sazonal = {1: 0.75, 2: 1.15, 3: 1.12, 7: 0.85, 12: 0.8}.get(d.month, 1.0)
        crescimento = 0.8 + 0.4 * ((d - inicio).days / max(1, (AGORA.date() - inicio).days))
        pesos.append((d, fator * sazonal * crescimento * rnd.uniform(0.85, 1.15)))
        d += timedelta(days=1)
    soma = sum(p for _, p in pesos)
    for d, p in pesos:
        dias.append((d, max(0, round(total * p / soma))))

    modelos = []
    acum = 0.0
    peso_total = sum(m[5] for m in pools['modelos'])
    for n, (cid, eid, tipo, urg, imp, peso, titulos, solucoes, aprov) in enumerate(pools['modelos'], 1):
        ini = acum
        acum += peso / peso_total
        modelos.append((n, cid, eid, tipo, urg, imp, ini, acum if n < len(pools['modelos']) else 1.0001,
                        '{' + ','.join('"%s"' % t.replace('"', '\\"') for t in titulos) + '}',
                        '{' + ','.join('"%s"' % s.replace('"', '\\"') for s in solucoes) + '}', aprov))

    sol = pools['solicitantes']
    tec = pools['tecnicos']
    # recomeça as sequências se uma tentativa anterior falhou (a sequência não volta com o ROLLBACK)
    sql = [''.join("SELECT setval('%s_seq', coalesce((SELECT max(id) FROM %s), 0) + 1, false);\n" % (t, t)
                   for t in ('itsm.chamado', 'itsm.chamado_ator', 'itsm.interacao', 'itsm.historico', 'itsm.satisfacao')),
           'BEGIN;\nSET LOCAL synchronous_commit = off;\n',
           'CREATE TEMP TABLE dias (dia date, qtd int);\n', linhas('dias', ['dia', 'qtd'], dias),
           'CREATE TEMP TABLE modelo (n int, categoria_id bigint, equipe_id bigint, tipo smallint, urgencia int, '
           'impacto int, ini float8, fim float8, titulos text[], solucoes text[], aprovacao boolean);\n',
           linhas('modelo', ['n', 'categoria_id', 'equipe_id', 'tipo', 'urgencia', 'impacto', 'ini', 'fim', 'titulos',
                             'solucoes', 'aprovacao'], modelos),
           'CREATE TEMP TABLE solic (n int, user_id bigint, dept_id bigint);\n',
           linhas('solic', ['n', 'user_id', 'dept_id'], [(i, u, d) for i, (u, d) in enumerate(sol, 1)]),
           'CREATE TEMP TABLE tec (equipe_id bigint, n int, user_id bigint);\n',
           linhas('tec', ['equipe_id', 'n', 'user_id'],
                  [(e, i, u) for e in {x[0] for x in tec} for i, u in enumerate([u for ee, u in tec if ee == e], 1)]),
           'CREATE TEMP TABLE tec_qtd AS SELECT equipe_id, count(*) AS qtd FROM tec GROUP BY 1;\n',
           'CREATE TEMP TABLE nomes AS SELECT id, nickname FROM system_users;\n',
           'CREATE TEMP TABLE slap AS SELECT p.prioridade, p.sla_atendimento_id, p.sla_solucao_id, '
           "a.tempo * CASE a.unidade WHEN 'minuto' THEN 60 WHEN 'hora' THEN 3600 ELSE 32400 END AS seg_at, "
           "s.tempo * CASE s.unidade WHEN 'minuto' THEN 60 WHEN 'hora' THEN 3600 ELSE 32400 END AS seg_sol "
           'FROM itsm.sla_prioridade p JOIN itsm.sla a ON a.id = p.sla_atendimento_id JOIN itsm.sla s ON s.id = p.sla_solucao_id;\n',
           # catálogo: a categoria do formulário importado (mesmo nome) — o modelo já aponta para ela
           ]
    nsol = len(sol)
    sql.append("""
-- 1) linhas-base: um chamado por linha, com sorteios fixos
CREATE TEMP TABLE b AS
SELECT nextval('itsm.chamado_seq') AS id,
       dia + make_interval(secs => 8 * 3600 + ((random() + random() + random()) / 3) * 10 * 3600) AS aberto_em,
       random() AS r_modelo, random() AS r_status, random() AS r_tempo, random() AS r_sol, random() AS r_origem,
       random() AS r_extra, (1 + floor(random() * %d))::int AS n_solic
FROM dias, generate_series(1, qtd);
DELETE FROM b WHERE aberto_em > now() - interval '5 minutes';

-- 2) chamado completo
CREATE TEMP TABLE c AS
SELECT b.id, b.aberto_em, m.categoria_id, m.equipe_id, m.tipo, m.aprovacao,
       m.titulos[1 + floor(b.r_modelo * 7919)::int %% array_length(m.titulos, 1)] AS titulo,
       m.solucoes[1 + floor(b.r_sol * 7919)::int %% array_length(m.solucoes, 1)] AS solucao,
       greatest(1, least(5, m.urgencia + (floor(b.r_extra * 3) - 1)::int)) AS urgencia,
       greatest(1, least(5, m.impacto + (floor(b.r_tempo * 2.2) - 1)::int)) AS impacto,
       s.user_id AS solicitante, s.dept_id,
       CASE WHEN b.r_origem < 0.42 THEN 1 WHEN b.r_origem < 0.60 THEN 3 WHEN b.r_origem < 0.74 THEN 2
            WHEN b.r_origem < 0.94 THEN 4 ELSE 5 END AS origem_id,
       b.r_status, b.r_tempo, b.r_sol, b.r_extra
FROM b JOIN modelo m ON b.r_modelo >= m.ini AND b.r_modelo < m.fim
JOIN solic s ON s.n = b.n_solic;

ALTER TABLE c ADD prioridade int, ADD tecnico bigint, ADD seg_at bigint, ADD seg_sol bigint;
UPDATE c SET prioridade = (ARRAY[[1,1,2,2,2],[1,2,2,3,3],[2,2,3,4,4],[2,3,4,4,5],[2,3,4,5,5]])[urgencia][impacto];
UPDATE c SET prioridade = 6 WHERE prioridade = 5 AND r_extra > 0.93;
UPDATE c SET seg_at = slap.seg_at, seg_sol = slap.seg_sol FROM slap WHERE slap.prioridade = c.prioridade;
UPDATE c SET tecnico = t.user_id FROM tec_qtd q, tec t
 WHERE q.equipe_id = c.equipe_id AND t.equipe_id = c.equipe_id AND t.n = 1 + floor(c.r_sol * 1000)::int %% q.qtd;

-- prazos em horas corridas (o expediente tem 9 h/dia: SLA de 1 dia = 9 h úteis ≈ 24 h corridas)
ALTER TABLE c ADD prazo_at timestamp, ADD prazo_sol timestamp, ADD t_assumir bigint, ADD t_sol bigint;
UPDATE c SET prazo_at = aberto_em + make_interval(secs => CASE WHEN seg_at >= 32400 THEN seg_at * 2.6 ELSE seg_at END),
             prazo_sol = aberto_em + make_interval(secs => CASE WHEN seg_sol >= 32400 THEN seg_sol * 2.6 ELSE seg_sol END),
             t_assumir = 120 + (power(r_tempo, 2.2) * least(seg_at, 14400) * 1.3)::bigint;
UPDATE c SET t_sol = t_assumir + (power(r_sol, 1.6) * (extract(epoch FROM prazo_sol - aberto_em)) * 1.15)::bigint;

-- situação: antigos fechados; os recentes ainda em andamento
ALTER TABLE c ADD status smallint, ADD assumido_em timestamp, ADD solucionado_em timestamp, ADD fechado_em timestamp;
UPDATE c SET assumido_em = CASE WHEN aberto_em + make_interval(secs => t_assumir) < now() THEN aberto_em + make_interval(secs => t_assumir) END,
             solucionado_em = CASE WHEN aberto_em + make_interval(secs => t_sol) < now() - interval '30 minutes'
                                   THEN aberto_em + make_interval(secs => t_sol) END;
UPDATE c SET status = CASE
    WHEN assumido_em IS NULL THEN 1
    WHEN solucionado_em IS NULL THEN CASE WHEN r_extra < 0.18 THEN 4 ELSE 2 END
    WHEN solucionado_em < now() - interval '3 days' THEN 6
    ELSE 5 END;
UPDATE c SET fechado_em = solucionado_em + interval '3 days' WHERE status = 6;
-- uns poucos esquecidos (pendentes há semanas), como em toda fila real
UPDATE c SET status = 4, solucionado_em = NULL, fechado_em = NULL
 WHERE aberto_em BETWEEN now() - interval '60 days' AND now() - interval '10 days' AND r_status < 0.004;

INSERT INTO itsm.chamado (id, titulo, descricao, tipo, status, urgencia, impacto, prioridade, categoria_id, origem_id,
    dept_id, aberto_em, assumido_em, solucionado_em, fechado_em, sla_atendimento_id, sla_solucao_id, prazo_atendimento,
    prazo_solucao, espera_seg, espera_sla_seg, tempo_assumir_seg, tempo_solucao_seg, tempo_fechamento_seg, tempo_gasto_seg,
    registrado_por, validacao, creator, create_time, updater, update_time, deleted)
SELECT c.id, c.titulo, '<p>' || c.titulo || '.</p>', c.tipo, c.status, c.urgencia, c.impacto, c.prioridade, c.categoria_id,
       c.origem_id, c.dept_id, c.aberto_em, c.assumido_em, c.solucionado_em, c.fechado_em, slap.sla_atendimento_id,
       slap.sla_solucao_id, c.prazo_at, c.prazo_sol, 0, 0,
       CASE WHEN c.assumido_em IS NOT NULL THEN extract(epoch FROM c.assumido_em - c.aberto_em)::bigint END,
       CASE WHEN c.solucionado_em IS NOT NULL THEN extract(epoch FROM c.solucionado_em - c.aberto_em)::bigint END,
       CASE WHEN c.fechado_em IS NOT NULL THEN extract(epoch FROM c.fechado_em - c.aberto_em)::bigint END,
       CASE WHEN c.assumido_em IS NOT NULL THEN 300 + (c.r_tempo * 5400)::bigint ELSE 0 END,
       CASE WHEN c.origem_id = 1 THEN c.solicitante ELSE coalesce(c.tecnico, c.solicitante) END,
       CASE WHEN c.aprovacao THEN 3 ELSE 1 END,
       c.solicitante::text, c.aberto_em, coalesce(c.tecnico, c.solicitante)::text,
       coalesce(c.fechado_em, c.solucionado_em, c.assumido_em, c.aberto_em), 0
FROM c JOIN slap ON slap.prioridade = c.prioridade;
SELECT setval('itsm.chamado_seq', (SELECT max(id) FROM itsm.chamado));

-- 3) atores: solicitante, equipe e técnico
INSERT INTO itsm.chamado_ator (id, chamado_id, tipo, user_id, equipe_id, notificar, creator, create_time, deleted)
SELECT nextval('itsm.chamado_ator_seq'), id, 1, solicitante, NULL::bigint, true, solicitante::text, aberto_em, 0 FROM c
UNION ALL
SELECT nextval('itsm.chamado_ator_seq'), id, 2, NULL::bigint, equipe_id, true, '1', aberto_em, 0 FROM c
UNION ALL
SELECT nextval('itsm.chamado_ator_seq'), id, 2, tecnico, NULL, true, tecnico::text, assumido_em, 0 FROM c
 WHERE assumido_em IS NOT NULL AND tecnico IS NOT NULL;

-- 4) interações: acompanhamento do técnico, resposta do solicitante e a solução
INSERT INTO itsm.interacao (id, chamado_id, tipo, user_id, conteudo, privado, origem_id, status_solucao, avaliado_por,
    avaliado_em, creator, create_time, updater, update_time, deleted)
SELECT nextval('itsm.interacao_seq'), id, 'acompanhamento', tecnico,
       '<p>' || (ARRAY[%s])[1 + floor(r_extra * 7)::int] || '</p>', false, NULL::bigint, NULL::smallint, NULL::bigint,
       NULL::timestamp, tecnico::text,
       assumido_em + make_interval(secs => 60 + r_tempo * 1800), tecnico::text,
       assumido_em + make_interval(secs => 60 + r_tempo * 1800), 0
FROM c WHERE assumido_em IS NOT NULL AND tecnico IS NOT NULL AND r_status < 0.72
UNION ALL
SELECT nextval('itsm.interacao_seq'), id, 'acompanhamento', solicitante,
       '<p>' || (ARRAY[%s])[1 + floor(r_sol * 5)::int] || '</p>', false, 4, NULL::smallint, NULL::bigint,
       NULL::timestamp, solicitante::text,
       assumido_em + make_interval(secs => 3600 + r_tempo * 7200), solicitante::text,
       assumido_em + make_interval(secs => 3600 + r_tempo * 7200), 0
FROM c WHERE assumido_em IS NOT NULL AND r_status > 0.80
UNION ALL
SELECT nextval('itsm.interacao_seq'), id, 'solucao', tecnico, '<p>' || solucao || '</p>', false, NULL,
       CASE WHEN status = 6 THEN 3 ELSE 2 END, CASE WHEN status = 6 THEN solicitante END,
       CASE WHEN status = 6 THEN fechado_em END, tecnico::text, solucionado_em, tecnico::text, solucionado_em, 0
FROM c WHERE solucionado_em IS NOT NULL AND tecnico IS NOT NULL;

-- 5) histórico de status
INSERT INTO itsm.historico (id, chamado_id, momento, user_id, usuario, campo, valor_antigo, valor_novo)
SELECT nextval('itsm.historico_seq'), c.id, c.assumido_em, c.tecnico, n.nickname, 'status', 'Novo', 'Em atendimento'
FROM c JOIN nomes n ON n.id = c.tecnico WHERE c.assumido_em IS NOT NULL
UNION ALL
SELECT nextval('itsm.historico_seq'), c.id, c.solucionado_em, c.tecnico, n.nickname, 'status', 'Em atendimento', 'Solucionado'
FROM c JOIN nomes n ON n.id = c.tecnico WHERE c.solucionado_em IS NOT NULL
UNION ALL
SELECT nextval('itsm.historico_seq'), c.id, c.fechado_em, NULL, 'Fechamento automático', 'status', 'Solucionado', 'Fechado'
FROM c WHERE c.fechado_em IS NOT NULL
UNION ALL
SELECT nextval('itsm.historico_seq'), c.id, c.assumido_em + interval '1 hour', c.tecnico, n.nickname, 'status',
       'Em atendimento', 'Pendente'
FROM c JOIN nomes n ON n.id = c.tecnico WHERE c.status = 4;

-- 6) pesquisa de satisfação: enviada em metade dos fechados, respondida em ~45%% delas
INSERT INTO itsm.satisfacao (id, chamado_id, enviada_em, respondida_em, nota, comentario)
SELECT nextval('itsm.satisfacao_seq'), id, fechado_em,
       CASE WHEN r_tempo < 0.45 THEN fechado_em + make_interval(secs => 600 + r_sol * 86400) END,
       CASE WHEN r_tempo < 0.45 THEN (ARRAY[1,2,3,4,4,5,5,5,5,5])[1 + floor(r_extra * 10)::int] END,
       CASE WHEN r_tempo < 0.08 THEN (ARRAY['Atendimento rápido, obrigado!', 'Resolveram no mesmo dia.',
            'Demorou um pouco, mas resolveu.', 'Técnico muito atencioso.', 'Precisei ligar duas vezes.'])[1 + floor(r_sol * 5)::int] END
FROM c WHERE status = 6 AND r_status < 0.5;

COMMIT;
ANALYZE itsm.chamado; ANALYZE itsm.chamado_ator; ANALYZE itsm.interacao; ANALYZE itsm.historico; ANALYZE itsm.satisfacao;
SELECT count(*) || ' chamados, ' || count(*) FILTER (WHERE status < 5) || ' em aberto' FROM itsm.chamado;
""" % (nsol, ','.join(lit(x) for x in ACOMPANHAMENTOS_TEC), ','.join(lit(x) for x in ACOMPANHAMENTOS_USR)))
    return ''.join(sql)


# ======================================================================================== problemas, mudanças, projetos

# (título, títulos de chamado que o revelam, equipe, sintomas, causa raiz, solução definitiva, mudança que resolveu)
PROBLEMAS = [
    ('Sistema da Saúde indisponível nas UBS no início do expediente',
     ['Sistema da UBS fora do ar', 'Não consigo entrar no prontuário eletrônico'], 'sistemas',
     'As UBS não acessam o prontuário entre 7h e 7h30; depois volta sozinho.',
     'Rotina de backup do banco da Saúde terminava depois das 7h e travava as tabelas de agendamento.',
     'Backup passou para as 2h, com cópia incremental; alerta no monitoramento se passar das 6h.',
     'Mudar o horário do backup do banco da Saúde'),
    ('Quedas de internet nas escolas da zona norte',
     ['Internet caindo toda hora', 'Sem internet na unidade'], 'redes',
     'Escolas ficam sem internet várias vezes por dia, por poucos minutos.',
     'Roteadores das escolas com firmware antigo reiniciando por falta de memória.',
     'Firmware atualizado e reinício programado semanal fora do horário de aula.',
     'Atualizar o firmware dos roteadores das escolas'),
    ('Tela azul recorrente nos computadores do lote 2019',
     ['Tela azul ao iniciar o Windows', 'Computador reiniciando sozinho'], 'campo',
     'Computadores do mesmo modelo travam com tela azul ao ligar.',
     'Driver de armazenamento incompatível com a atualização cumulativa do Windows.',
     'Driver do fabricante distribuído pelo inventário e atualização segura reaprovada.',
     'Distribuir o driver de armazenamento do lote 2019'),
    ('Fila de impressão travando nas secretarias',
     ['Erro de spooler ao imprimir', 'Impressora offline na rede'], 'campo',
     'Impressoras de rede ficam offline e a fila trava até reiniciar o spooler.',
     'Servidor de impressão sem atualização do driver universal e porta SNMP ativada.',
     'Driver universal atualizado e status SNMP desligado nas portas de impressão.',
     'Atualizar o servidor de impressão'),
    ('Contas bloqueadas em massa toda segunda-feira',
     ['Conta bloqueada por tentativas erradas', 'Senha expirada, não consigo entrar'], 'n1',
     'Dezenas de contas bloqueadas pela manhã depois do fim de semana.',
     'Celulares com a senha antiga do e-mail tentando sincronizar e estourando o limite de tentativas.',
     'Limite de bloqueio ajustado e campanha de orientação para atualizar a senha no celular.',
     'Ajustar a política de bloqueio de contas'),
    ('Lentidão no sistema tributário em fim de mês',
     ['Sistema tributário lento', 'Erro ao emitir guia de IPTU'], 'sistemas',
     'Emissão de guias leva minutos nos últimos dias do mês.',
     'Consulta de débitos sem índice no banco do fornecedor; servidor com pouca memória.',
     'Índice criado pelo fornecedor e memória do servidor dobrada.',
     'Ampliar a memória do servidor do sistema tributário'),
    ('E-mails externos chegando com atraso',
     ['Não recebo e-mails externos', 'E-mail não sincroniza no celular'], 'infra',
     'Mensagens de fora chegam horas depois.',
     'Lista de bloqueio do antispam marcando o retransmissor do provedor.',
     'Retransmissor liberado e regras de reputação revisadas.',
     None),
    ('Tentativas de phishing com o nome da Prefeitura',
     ['Recebi e-mail pedindo minha senha'], 'seg',
     'Servidores recebem e-mails falsos pedindo a senha do portal.',
     'Domínio parecido registrado por terceiros e ausência de DMARC restritivo.',
     'DMARC em rejeição, alerta de domínios parecidos e treinamento dos servidores.',
     'Publicar DMARC em modo rejeição'),
    ('VPN do teletrabalho não conecta depois da atualização',
     ['VPN não conecta'], 'redes',
     'Cliente VPN falha na autenticação nos notebooks atualizados.',
     'Certificado do concentrador VPN com algoritmo que a nova versão do cliente recusa.',
     'Certificado reemitido com algoritmo atual.',
     'Renovar o certificado do concentrador VPN'),
    ('Office pedindo ativação em vários computadores',
     ['Office pedindo ativação'], 'campo',
     'Office volta a pedir ativação semanas depois de instalado.',
     'Imagem de instalação antiga com chave de volume vencida.',
     'Imagem refeita e ativação pelo servidor de licenças.',
     None),
]

# (título, tipo 1 padrão/2 normal/3 emergencial, equipe, impacto, plano de implantação, plano de retorno)
MUDANCAS_ROTINA = [
    ('Atualização mensal de segurança dos servidores Windows', 1, 'infra', 2,
     'Aplicar as atualizações do mês em grupos: homologação, secundários e principais.',
     'Desinstalar a atualização pelo número KB e reiniciar.'),
    ('Atualização do antivírus nos computadores', 1, 'seg', 2,
     'Liberar a nova versão no console para 10% dos computadores; depois para todos.',
     'Voltar a política para a versão anterior no console.'),
    ('Troca de switch de acesso na unidade', 2, 'redes', 3,
     'Configurar o switch novo em bancada, trocar no fim do expediente e testar cada ponto.',
     'Recolocar o switch antigo, que fica no rack até o dia seguinte.'),
    ('Atualização de versão do sistema da Saúde', 2, 'sistemas', 4,
     'Fornecedor aplica a versão no sábado; equipe valida agenda, prontuário e farmácia.',
     'Fornecedor restaura o backup feito antes da atualização.'),
    ('Ampliação de espaço no servidor de arquivos', 1, 'infra', 2,
     'Aumentar o disco virtual e estender o volume sem desligar.',
     'Sem retorno necessário: a ampliação não altera os dados.'),
    ('Novo SSID para a rede de visitantes', 2, 'redes', 2,
     'Criar o SSID isolado, com portal de aceite, nas unidades piloto.',
     'Desativar o SSID novo e manter o anterior.'),
    ('Renovação do certificado do portal', 1, 'infra', 3,
     'Instalar o certificado novo no balanceador e conferir a cadeia.',
     'Recolocar o certificado anterior enquanto válido.'),
    ('Correção emergencial de vulnerabilidade no firewall', 3, 'seg', 4,
     'Aplicar a correção do fabricante fora do horário, com acesso ao console garantido.',
     'Restaurar a configuração e a imagem anterior salvas antes.'),
]

PROJETOS = [  # (nome, código, equipe, situação, meses atrás do início, duração em meses, tarefas)
    ('Fibra própria nas escolas municipais', 'PRJ-REDE-01', 'redes', 2, 8, 14,
     ['Levantamento das escolas', 'Projeto de rotas', 'Licitação', 'Lançamento - lote 1', 'Lançamento - lote 2',
      'Migração dos links', 'Entrega']),
    ('Prontuário eletrônico em todas as UBS', 'PRJ-SAU-01', 'sistemas', 2, 10, 12,
     ['Mapeamento dos fluxos', 'Treinamento das equipes', 'Implantação - UBS piloto', 'Implantação - demais UBS',
      'Desligar o papel', 'Entrega']),
    ('Renovação do parque de computadores', 'PRJ-INV-01', 'campo', 2, 5, 10,
     ['Inventário dos equipamentos antigos', 'Especificação', 'Compra', 'Imagem padrão', 'Troca por secretaria',
      'Descarte responsável', 'Entrega']),
    ('Wi-Fi nas salas de aula', 'PRJ-REDE-02', 'redes', 1, -1, 8,
     ['Site survey', 'Compra dos pontos de acesso', 'Instalação', 'Entrega']),
    ('Migração do e-mail para a nuvem', 'PRJ-SIS-02', 'infra', 4, 22, 6,
     ['Piloto com a TI', 'Migração das secretarias', 'Desligar o servidor antigo', 'Entrega']),
    ('Adequação à LGPD na TI', 'PRJ-SEG-01', 'seg', 2, 12, 18,
     ['Inventário de dados pessoais', 'Política de segurança', 'Treinamento', 'Gestão de acessos', 'Entrega']),
    ('Central telefônica em nuvem', 'PRJ-TEL-01', 'redes', 3, 6, 9,
     ['Levantamento dos ramais', 'Contratação', 'Portabilidade', 'Entrega']),
    ('Backup externo do datacenter', 'PRJ-INF-02', 'infra', 4, 16, 5,
     ['Contratação do armazenamento', 'Configuração das cópias', 'Teste de restauração', 'Entrega']),
]


def gerar_gestao(rnd, pools):
    """Problemas, mudanças e projetos (centenas de linhas, montadas aqui e inseridas de uma vez)."""
    tec = pools['tecnicos_por_equipe']
    eq = pools['equipes']
    coord = pools['coord_ti']
    dept = pools['dept_ti']
    hoje = AGORA.date()
    contador = {}

    def nid(t):
        contador[t] = contador.get(t, 1000) + 1
        return contador[t]

    def prio(u, i):
        return [[1, 1, 2, 2, 2], [1, 2, 2, 3, 3], [2, 2, 3, 4, 4], [2, 3, 4, 4, 5], [2, 3, 4, 5, 5]][u - 1][i - 1]

    prob, mud, ator, inter, hist, vinc, aprov = [], [], [], [], [], [], []
    vinc_chamado = []  # (objeto, id, títulos de chamado, de, até, quantos)

    def atores(objeto, oid, solicitante, chave, tecnico):
        ator.append((nid('ator'), objeto, oid, 1, solicitante, None, str(solicitante), 0))
        ator.append((nid('ator'), objeto, oid, 2, None, eq[chave], '1', 0))
        if tecnico:
            ator.append((nid('ator'), objeto, oid, 2, tecnico, None, str(tecnico), 0))

    def historico(objeto, oid, momento, uid, campo, antes, depois):
        hist.append((nid('hist'), objeto, oid, momento, uid, campo, antes, depois))

    # ---- mudanças de rotina: uma por semana ao longo de três anos, mais as que vêm por aí
    inicio = AGORA - timedelta(days=3 * 365)
    semana = inicio
    while semana < AGORA + timedelta(days=21):
        titulo, tipo, chave, imp, plano, retorno = rnd.choice(MUDANCAS_ROTINA)
        janela = (semana + timedelta(days=rnd.randint(0, 4))).replace(hour=19 if tipo != 3 else 22, minute=0, second=0)
        aberto = janela - timedelta(days=rnd.randint(3, 10) if tipo != 3 else 0, hours=rnd.randint(1, 6))
        responsavel = rnd.choice(tec[chave])
        urg = 4 if tipo == 3 else rnd.randint(2, 3)
        futuro = janela > AGORA
        if futuro:
            status, validacao = (10, 2) if rnd.random() < 0.5 and tipo == 2 else (7, 3 if tipo == 2 else 1)
            solucionado = fechado = None
        else:
            falhou = rnd.random() < 0.04
            status, validacao = (6, 3 if tipo == 2 else 1)
            solucionado = janela + timedelta(hours=rnd.randint(1, 3))
            fechado = solucionado + timedelta(days=rnd.randint(1, 5))
            if falhou:
                plano = plano + ' Executada com retorno: o plano de retorno foi aplicado.'
        mid = nid('mudanca')
        mud.append((mid, titulo, '<p>%s</p>' % titulo, tipo, status, urg, imp, prio(urg, imp), dept, aberto,
                    janela + timedelta(hours=4), janela, janela + timedelta(hours=3), solucionado, fechado,
                    'Afeta os usuários do serviço durante a janela.', 'Acompanhamento pelo monitoramento durante a janela.',
                    plano, retorno, validacao, 3600 * rnd.randint(1, 4) if solucionado else 0, responsavel,
                    str(responsavel), aberto, str(responsavel), fechado or aberto, 0))
        atores('mudanca', mid, responsavel, chave, responsavel)
        if tipo == 2:
            aprov.append((nid('aprov'), mid, coord, responsavel, 1, 2 if status == 10 else 3, 'Janela: ' + janela.strftime('%d/%m %Hh'),
                          None if status == 10 else 'De acordo.', aberto + timedelta(hours=1),
                          None if status == 10 else aberto + timedelta(hours=rnd.randint(2, 30)), str(responsavel), 0))
        if solucionado:
            inter.append((nid('inter'), 'mudanca', mid, 'solucao', responsavel, '<p>Aplicada na janela, sem incidentes.</p>',
                          str(responsavel), solucionado, 0))
            historico('mudanca', mid, solucionado, responsavel, 'status', 'Aceita', 'Aplicada')
            historico('mudanca', mid, fechado, coord, 'status', 'Aplicada', 'Fechada')
        semana += timedelta(days=7)

    # ---- problemas: cada um aparece num momento do histórico; os mais recentes ainda estão em análise
    momentos = sorted(rnd.sample(range(30, 3 * 365 - 30), len(PROBLEMAS)), reverse=True)
    for n, ((titulo, chamados, chave, sintomas, causa, solucao, mudanca), dias) in enumerate(zip(PROBLEMAS, momentos)):
        aberto = (AGORA - timedelta(days=dias)).replace(hour=rnd.randint(9, 16), minute=rnd.choice([0, 15, 30, 45]), second=0)
        responsavel = tec[chave][0]
        urg, imp = rnd.randint(3, 4), rnd.randint(3, 5)
        aberto_ainda = dias < 75 or n >= len(PROBLEMAS) - 2
        if aberto_ainda:
            status = rnd.choice([2, 3, 7])
            solucionado = fechado = None
            causa_txt, solucao_txt = (causa if status != 7 else None), None
        else:
            status = 6
            solucionado = aberto + timedelta(days=rnd.randint(10, 40))
            fechado = solucionado + timedelta(days=30)
            causa_txt, solucao_txt = causa, solucao
        pid = nid('problema')
        prob.append((pid, titulo, '<p>%s</p>' % sintomas, status, urg, imp, prio(urg, imp), dept, aberto,
                     aberto + timedelta(days=30), solucionado, fechado, 'Vários setores; chamados repetidos no Service Desk.',
                     causa_txt, sintomas, 3600 * rnd.randint(4, 30), responsavel, str(responsavel), aberto, str(responsavel),
                     fechado or solucionado or aberto, 0))
        atores('problema', pid, coord, chave, responsavel)
        inter.append((nid('inter'), 'problema', pid, 'acompanhamento', responsavel,
                      '<p>Chamados semelhantes agrupados para análise. Coletando registros dos equipamentos.</p>',
                      str(responsavel), aberto + timedelta(hours=2), 0))
        historico('problema', pid, aberto + timedelta(hours=1), responsavel, 'status', 'Novo', 'Em análise')
        if solucao_txt:
            inter.append((nid('inter'), 'problema', pid, 'solucao', responsavel, '<p>%s</p>' % solucao_txt,
                          str(responsavel), solucionado, 0))
            historico('problema', pid, solucionado, responsavel, 'status', 'Em análise', 'Solucionado')
            historico('problema', pid, fechado, coord, 'status', 'Solucionado', 'Fechado')
        vinc_chamado.append(('problema', pid, chamados, aberto - timedelta(days=20), aberto + timedelta(days=5), 6))
        if mudanca and solucionado:
            janela = (solucionado - timedelta(days=2)).replace(hour=19, minute=0, second=0)
            mid = nid('mudanca')
            mud.append((mid, mudanca, '<p>Correção definitiva do problema "%s".</p>' % titulo, 2, 6, urg, imp, prio(urg, imp),
                        dept, janela - timedelta(days=5), janela + timedelta(hours=4), janela, janela + timedelta(hours=3),
                        janela + timedelta(hours=2), janela + timedelta(days=3), 'Corrige a causa de chamados recorrentes.',
                        'Acompanhar os chamados do tema por 30 dias.', solucao, 'Reverter a alteração e reabrir o problema.',
                        3, 3600 * 3, responsavel, str(responsavel), janela - timedelta(days=5), str(responsavel),
                        janela + timedelta(days=3), 0))
            atores('mudanca', mid, responsavel, chave, responsavel)
            aprov.append((nid('aprov'), mid, coord, responsavel, 1, 3, 'Correção do problema.', 'Aprovado.',
                          janela - timedelta(days=4), janela - timedelta(days=3), str(responsavel), 0))
            vinc.append((nid('vinc'), 'mudanca', mid, 'problema', pid, str(responsavel), 0))

    # ---- projetos com tarefas
    proj, membros, tarefas = [], [], []
    for nome, codigo, chave, situacao, atras, meses, etapas in PROJETOS:
        ini = hoje - timedelta(days=int(atras * 30.4))
        fim = ini + timedelta(days=int(meses * 30.4))
        responsavel = tec[chave][0]
        prj = nid('projeto')
        concluidas = len(etapas) if situacao == 4 else (0 if situacao == 1 else
                     max(1, min(len(etapas) - 1, round(len(etapas) * (hoje - ini).days / max(1, (fim - ini).days)))))
        proj.append((prj, nome, codigo, '<p>%s.</p>' % nome, situacao, rnd.randint(3, 5), dept, responsavel, ini, fim,
                     ini if situacao != 1 else None, fim if situacao == 4 else None,
                     100 if situacao == 4 else round(100 * concluidas / len(etapas)), True, str(responsavel), 0))
        for u in {responsavel, coord, *rnd.sample(tec[chave], min(2, len(tec[chave])))}:
            membros.append((nid('membro'), prj, u, 'Responsável' if u == responsavel else
                            ('Patrocinador' if u == coord else 'Equipe'), str(responsavel), 0))
        passo = (fim - ini) / len(etapas)
        for i, etapa in enumerate(etapas):
            t_ini, t_fim = ini + passo * i, ini + passo * (i + 1)
            feita = i < concluidas
            andamento = not feita and i == concluidas and situacao in (2, 3)
            horas = rnd.choice([16, 24, 40, 80])
            tarefas.append((nid('tarefa'), prj, etapa, rnd.choice(tec[chave]),
                            3 if feita else (2 if andamento else 1), t_ini, t_fim,
                            t_ini if feita or andamento else None, t_fim if feita else None, horas,
                            horas if feita else (round(horas * 0.4) if andamento else 0),
                            100 if feita else (40 if andamento else 0), etapa == 'Entrega', i, str(responsavel), 0))

    sql = ['BEGIN;\n', 'CREATE TEMP TABLE nomes AS SELECT id, nickname FROM system_users;\n',
           linhas('itsm.problema', ['id', 'titulo', 'descricao', 'status', 'urgencia', 'impacto', 'prioridade', 'dept_id',
                                    'aberto_em', 'prazo', 'solucionado_em', 'fechado_em', 'impactos', 'causas', 'sintomas',
                                    'tempo_gasto_seg', 'registrado_por', 'creator', 'create_time', 'updater', 'update_time',
                                    'deleted'], prob),
           linhas('itsm.mudanca', ['id', 'titulo', 'descricao', 'tipo', 'status', 'urgencia', 'impacto', 'prioridade',
                                   'dept_id', 'aberto_em', 'prazo', 'inicio_previsto', 'fim_previsto', 'solucionado_em',
                                   'fechado_em', 'analise_impacto', 'plano_controle', 'plano_implantacao', 'plano_retorno',
                                   'validacao', 'tempo_gasto_seg', 'registrado_por', 'creator', 'create_time', 'updater',
                                   'update_time', 'deleted'], mud),
           linhas('itsm.mudanca_aprovacao', ['id', 'mudanca_id', 'aprovador_id', 'solicitado_por', 'rodada', 'status',
                                             'comentario_pedido', 'comentario_resposta', 'pedido_em', 'respondido_em',
                                             'creator', 'deleted'], aprov),
           linhas('itsm.itil_ator', ['id', 'objeto', 'objeto_id', 'tipo', 'user_id', 'equipe_id', 'creator', 'deleted'], ator),
           linhas('itsm.itil_interacao', ['id', 'objeto', 'objeto_id', 'tipo', 'user_id', 'conteudo', 'creator',
                                          'create_time', 'deleted'], inter),
           linhas('itsm.itil_vinculo', ['id', 'objeto', 'objeto_id', 'alvo', 'alvo_id', 'creator', 'deleted'], vinc),
           linhas('itsm.projeto', ['id', 'nome', 'codigo', 'descricao', 'situacao', 'prioridade', 'dept_id',
                                   'responsavel_id', 'inicio_previsto', 'fim_previsto', 'inicio_real', 'fim_real',
                                   'percentual', 'percentual_auto', 'creator', 'deleted'], proj),
           linhas('itsm.projeto_membro', ['id', 'projeto_id', 'user_id', 'papel', 'creator', 'deleted'], membros),
           linhas('itsm.projeto_tarefa', ['id', 'projeto_id', 'nome', 'responsavel_id', 'situacao', 'inicio_previsto',
                                          'fim_previsto', 'inicio_real', 'fim_real', 'horas_previstas', 'horas_gastas',
                                          'percentual', 'marco', 'ordem', 'creator', 'deleted'], tarefas)]
    # histórico com o nome de quem mexeu
    sql.append('CREATE TEMP TABLE h (id bigint, objeto varchar, objeto_id bigint, momento timestamp, user_id bigint, '
               'campo varchar, antes varchar, depois varchar);\n')
    sql.append(linhas('h', ['id', 'objeto', 'objeto_id', 'momento', 'user_id', 'campo', 'antes', 'depois'], hist))
    sql.append('INSERT INTO itsm.itil_historico (id, objeto, objeto_id, momento, user_id, usuario, campo, valor_antigo, '
               'valor_novo) SELECT h.id, h.objeto, h.objeto_id, h.momento, h.user_id, n.nickname, h.campo, h.antes, '
               'h.depois FROM h LEFT JOIN nomes n ON n.id = h.user_id;\n')
    # chamados que revelaram cada problema: os do tema, abertos perto da data do problema
    for objeto, oid, titulos, de, ate, qtd in vinc_chamado:
        sql.append("INSERT INTO itsm.itil_vinculo (id, objeto, objeto_id, alvo, alvo_id, creator, deleted) "
                   "SELECT nextval('itsm.itil_vinculo_seq'), %s, %d, 'chamado', id, '1', 0 FROM ("
                   "SELECT id FROM itsm.chamado WHERE titulo IN (%s) AND aberto_em BETWEEN %s AND %s "
                   "ORDER BY aberto_em LIMIT %d) x;\n"
                   % (lit(objeto), oid, ','.join(lit(t) for t in titulos), lit(de), lit(ate), qtd))
    for seq, tabela in [('problema', 'itsm.problema'), ('mudanca', 'itsm.mudanca'), ('mudanca_aprovacao', 'itsm.mudanca_aprovacao'),
                        ('itil_ator', 'itsm.itil_ator'), ('itil_interacao', 'itsm.itil_interacao'),
                        ('itil_historico', 'itsm.itil_historico'), ('itil_vinculo', 'itsm.itil_vinculo'),
                        ('projeto', 'itsm.projeto'), ('projeto_membro', 'itsm.projeto_membro'),
                        ('projeto_tarefa', 'itsm.projeto_tarefa')]:
        sql.append("SELECT setval('itsm.%s_seq', greatest(1, coalesce((SELECT max(id) FROM %s), 1)));\n" % (seq, tabela))
    sql.append('COMMIT;\n')
    sql.append("SELECT (SELECT count(*) FROM itsm.problema) || ' problemas, ' || (SELECT count(*) FROM itsm.mudanca) "
               "|| ' mudanças, ' || (SELECT count(*) FROM itsm.projeto) || ' projetos com ' || "
               "(SELECT count(*) FROM itsm.projeto_tarefa) || ' tarefas, ' || "
               "(SELECT count(*) FROM itsm.itil_vinculo WHERE alvo = 'chamado') || ' chamados ligados a problemas';\n")
    return ''.join(sql)


# ======================================================================================== principal


def main():
    ap = argparse.ArgumentParser(description='Carga de demonstração do CO-TI (Prefeitura de Araraquara, fictícia).')
    ap.add_argument('--anos', type=float, default=3, help='anos de histórico de chamados (padrão 3)')
    ap.add_argument('--chamados', type=int, default=150_000, help='total aproximado de chamados (padrão 150 mil)')
    ap.add_argument('--seed', type=int, default=2026, help='semente: a mesma semente gera os mesmos dados')
    ap.add_argument('--container', default='coti-postgres-1', help='container do PostgreSQL')
    ap.add_argument('--url', default='http://localhost:8080', help='endereço do portal (para importar o catálogo)')
    ap.add_argument('--forcar', action='store_true', help='rodar mesmo que o banco já tenha chamados')
    ap.add_argument('--somente-chamados', action='store_true',
                    help='a estrutura já foi carregada (mesma --seed): gera só o histórico de chamados')
    ap.add_argument('--somente-gestao', action='store_true',
                    help='estrutura e chamados já carregados (mesma --seed): gera só problemas, mudanças e projetos')
    a = ap.parse_args()

    rnd = random.Random(a.seed)
    banco = Banco(a.container)
    if a.somente_gestao:
        ja = banco.executar('SELECT count(*) FROM itsm.problema; ', 'verificando o banco')
        if int(ja or 0) > 0:
            sys.exit('O banco já tem %s problemas: a etapa de gestão já foi carregada.' % ja)
    else:
        ja = banco.executar('SELECT count(*) FROM itsm.chamado; ', 'verificando o banco')
        if int(ja or 0) > 0 and not a.forcar:
            sys.exit('O banco já tem %s chamados. Esta carga é para um banco recém-criado (use --forcar para insistir).' % ja)

    senha = senha_demo()
    sql, pools = gerar_estrutura(rnd, senha)
    if not a.somente_chamados and not a.somente_gestao:
        print('1/4 estrutura')
        banco.executar(sql, 'estrutura, inventário e atendimento')
        print('    ' + ', '.join('%s: %s' % (k, f'{v:,}'.replace(',', '.')) for k, v in pools['resumo'].items()))
        print('2/4 catálogo de serviços')
        importar_catalogo(banco, a.url)

    if not a.somente_gestao:
        print('3/4 histórico de chamados')
        print('    ' + banco.executar(gerar_chamados(rnd, pools, a.anos, a.chamados), 'chamados, atores, interações')
              .splitlines()[-1])

    print('4/4 problemas, mudanças e projetos')
    print('    ' + banco.executar(gerar_gestao(rnd, pools), 'problemas, mudanças e projetos').splitlines()[-1])

    print('\nContas de demonstração (senha: CO_TI_SENHA_DEMO no .env):')
    for papel, uid in pools['demo'].items():
        print('  %-14s %s' % (pools['usuarios'][uid], papel))


if __name__ == '__main__':
    main()
