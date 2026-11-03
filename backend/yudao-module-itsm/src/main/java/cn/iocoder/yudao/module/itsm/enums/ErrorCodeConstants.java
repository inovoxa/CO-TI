package cn.iocoder.yudao.module.itsm.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

/**
 * Erros do ITSM (faixa 1-101-000-000).
 */
public interface ErrorCodeConstants {

    // chamado 1-101-000-xxx
    ErrorCode CHAMADO_NAO_EXISTE = new ErrorCode(1_101_000_000, "Chamado não encontrado");
    ErrorCode CHAMADO_TRANSICAO_INVALIDA = new ErrorCode(1_101_000_001, "{}");
    ErrorCode CHAMADO_FECHADO = new ErrorCode(1_101_000_002, "O chamado está fechado e não pode ser alterado");
    ErrorCode CHAMADO_SEM_ACESSO = new ErrorCode(1_101_000_003, "Você não tem acesso a este chamado");
    ErrorCode CHAMADO_SEM_SOLUCAO_PENDENTE = new ErrorCode(1_101_000_004, "Não há solução aguardando avaliação");
    ErrorCode CHAMADO_ATOR_INVALIDO = new ErrorCode(1_101_000_005, "Informe uma pessoa ou uma equipe");
    ErrorCode CHAMADO_ATOR_DUPLICADO = new ErrorCode(1_101_000_006, "Já está no chamado com esse papel");
    ErrorCode CHAMADO_ULTIMO_SOLICITANTE = new ErrorCode(1_101_000_007, "O chamado precisa de pelo menos um solicitante");
    ErrorCode CHAMADO_ATOR_NAO_EXISTE = new ErrorCode(1_101_000_008, "Participante não encontrado");
    ErrorCode CHAMADO_TAREFA_DATAS = new ErrorCode(1_101_000_009, "O fim da tarefa precisa ser depois do início");
    ErrorCode CHAMADO_SO_TECNICO = new ErrorCode(1_101_000_010, "Somente a equipe técnica pode registrar tarefas e soluções");

    // cadastros 1-101-001-xxx
    ErrorCode CATEGORIA_NAO_EXISTE = new ErrorCode(1_101_001_000, "Categoria não encontrada");
    ErrorCode CATEGORIA_PAI_INVALIDA = new ErrorCode(1_101_001_001, "A categoria superior não pode ser ela mesma nem uma descendente");
    ErrorCode CATEGORIA_NOME_DUPLICADO = new ErrorCode(1_101_001_002, "Já existe uma categoria com esse nome no mesmo nível");
    ErrorCode CATEGORIA_TEM_FILHAS = new ErrorCode(1_101_001_003, "Exclua ou mova as subcategorias antes");
    ErrorCode CATEGORIA_EM_USO = new ErrorCode(1_101_001_004, "A categoria tem chamados; desative em vez de excluir");
    ErrorCode CATEGORIA_CODIGO_DUPLICADO = new ErrorCode(1_101_001_005, "Já existe uma categoria com esse código");
    ErrorCode EQUIPE_NAO_EXISTE = new ErrorCode(1_101_001_010, "Equipe não encontrada");
    ErrorCode EQUIPE_NOME_DUPLICADO = new ErrorCode(1_101_001_011, "Já existe uma equipe com esse nome");
    ErrorCode EQUIPE_EM_USO = new ErrorCode(1_101_001_012, "A equipe está em chamados abertos; desative em vez de excluir");
    ErrorCode ORIGEM_NAO_EXISTE = new ErrorCode(1_101_001_020, "Origem não encontrada");
    ErrorCode ORIGEM_EM_USO = new ErrorCode(1_101_001_021, "A origem está em uso; desative em vez de excluir");

    // SLA 1-101-002-xxx
    ErrorCode SLA_NAO_EXISTE = new ErrorCode(1_101_002_000, "SLA não encontrado");
    ErrorCode SLA_INVALIDO = new ErrorCode(1_101_002_001, "{}");
    ErrorCode SLA_EM_USO = new ErrorCode(1_101_002_002, "O SLA está em uso (categoria, prioridade ou chamado aberto); desative em vez de excluir");
    ErrorCode SLA_TIPO_ERRADO = new ErrorCode(1_101_002_003, "Use um SLA de atendimento no campo de atendimento e um de solução no de solução");
    ErrorCode CALENDARIO_NAO_EXISTE = new ErrorCode(1_101_002_010, "Calendário não encontrado");
    ErrorCode CALENDARIO_EM_USO = new ErrorCode(1_101_002_011, "O calendário é usado por um SLA");
    ErrorCode CALENDARIO_PERIODO_INVALIDO = new ErrorCode(1_101_002_012, "Período inválido: o fim precisa ser depois do início e os períodos do mesmo dia não podem se sobrepor");
    ErrorCode FERIADO_NAO_EXISTE = new ErrorCode(1_101_002_020, "Feriado não encontrado");
    ErrorCode FERIADO_DATAS_INVALIDAS = new ErrorCode(1_101_002_021, "A data final do feriado é anterior à inicial");
    ErrorCode SATISFACAO_NAO_DISPONIVEL = new ErrorCode(1_101_002_030, "Não há pesquisa de satisfação pendente para este chamado");

    // aprovações, regras, formulários e execução no AD 1-101-003-xxx
    ErrorCode APROVACAO_SEM_APROVADOR = new ErrorCode(1_101_003_000, "Não foi possível definir quem aprova: o solicitante não tem gestor no AD ou o aprovador não foi encontrado");
    ErrorCode APROVACAO_APROVADOR_INVALIDO = new ErrorCode(1_101_003_001, "O aprovador não existe ou está desativado");
    ErrorCode APROVACAO_NAO_EXISTE = new ErrorCode(1_101_003_002, "Aprovação não encontrada");
    ErrorCode APROVACAO_JA_RESPONDIDA = new ErrorCode(1_101_003_003, "Esta aprovação já foi respondida");
    ErrorCode APROVACAO_PROPRIO_SOLICITANTE = new ErrorCode(1_101_003_004, "O aprovador não pode ser o próprio solicitante");
    ErrorCode APROVACAO_NAO_AUTORIZADO = new ErrorCode(1_101_003_005, "{} não está autorizado(a) a aprovar pedidos. Só aprova quem tem o papel Aprovador");
    ErrorCode APROVACAO_EMAIL_NAO_AUTORIZADO = new ErrorCode(1_101_003_006, "O e-mail do superior informado ({}) não é de um aprovador autorizado. Confira o e-mail do seu superior ou procure a TI");
    ErrorCode APROVACAO_GESTOR_NAO_AUTORIZADO = new ErrorCode(1_101_003_007, "Seu gestor no AD ({}) não está autorizado a aprovar pedidos. Procure a TI");
    ErrorCode APROVACAO_SEM_PERMISSAO = new ErrorCode(1_101_003_008, "Você não está mais autorizado(a) a aprovar pedidos. Peça à TI para encaminhar esta aprovação a outra pessoa");
    ErrorCode REGRA_NAO_EXISTE = new ErrorCode(1_101_003_010, "Regra não encontrada");
    ErrorCode REGRA_INVALIDA = new ErrorCode(1_101_003_011, "{}");
    ErrorCode FORMULARIO_NAO_EXISTE = new ErrorCode(1_101_003_020, "Formulário não encontrado");
    ErrorCode FORMULARIO_INVALIDO = new ErrorCode(1_101_003_021, "{}");
    ErrorCode FORMULARIO_RESPOSTAS_INVALIDAS = new ErrorCode(1_101_003_022, "Confira os campos destacados: {}");
    ErrorCode FORMULARIO_CODIGO_DUPLICADO = new ErrorCode(1_101_003_023, "Já existe um formulário com esse código");
    ErrorCode FORMULARIO_EM_USO = new ErrorCode(1_101_003_024, "O formulário já tem pedidos; desative em vez de excluir");
    ErrorCode EXECUCAO_NAO_EXISTE = new ErrorCode(1_101_003_030, "Execução não encontrada");
    ErrorCode EXECUCAO_ESTADO_INVALIDO = new ErrorCode(1_101_003_031, "Esta execução não pode ser alterada na situação atual");
    ErrorCode EXECUCAO_SENHA_INDISPONIVEL = new ErrorCode(1_101_003_032, "Não há senha provisória para ver (ela é mostrada uma única vez)");


    // base de conhecimento 1-101-004-xxx
    ErrorCode KB_CATEGORIA_NAO_EXISTE = new ErrorCode(1_101_004_000, "Categoria da base não encontrada");
    ErrorCode KB_CATEGORIA_PAI_INVALIDA = new ErrorCode(1_101_004_001, "A categoria superior não pode ser ela mesma nem uma descendente");
    ErrorCode KB_CATEGORIA_EM_USO = new ErrorCode(1_101_004_002, "A categoria tem subcategorias ou artigos");
    ErrorCode KB_ARTIGO_NAO_EXISTE = new ErrorCode(1_101_004_010, "Artigo não encontrado");
    ErrorCode KB_ARTIGO_DATAS = new ErrorCode(1_101_004_011, "O fim da publicação precisa ser depois do início");
    ErrorCode KB_REVISAO_NAO_EXISTE = new ErrorCode(1_101_004_012, "Revisão não encontrada");
    ErrorCode KB_CHAMADO_SEM_SOLUCAO = new ErrorCode(1_101_004_013, "O chamado ainda não tem solução para virar artigo");


    // e-mail 1-101-005-xxx
    ErrorCode EMAIL_CONFIG_INCOMPLETA = new ErrorCode(1_101_005_000, "Para ligar o envio, informe o servidor SMTP e o remetente");
    ErrorCode EMAIL_EVENTO_NAO_EXISTE = new ErrorCode(1_101_005_001, "Evento de notificação não encontrado");
    ErrorCode EMAIL_CAIXA_NAO_EXISTE = new ErrorCode(1_101_005_002, "Caixa de entrada não encontrada");
    ErrorCode EMAIL_SEM_CHAVE_MESTRA = new ErrorCode(1_101_005_003, "Defina CO_TI_CHAVE_MESTRA no servidor: sem ela a senha não pode ser guardada");

    ErrorCode PAINEL_PERIODO_INVALIDO = new ErrorCode(1_101_006_000, "Período inválido (até 366 dias, com o fim depois do início)");

    // problemas e mudanças 1-101-007-xxx
    ErrorCode ITIL_TRANSICAO_INVALIDA = new ErrorCode(1_101_007_000, "Não foi possível: {}");
    ErrorCode PROBLEMA_NAO_EXISTE = new ErrorCode(1_101_007_001, "Problema não encontrado");
    ErrorCode MUDANCA_NAO_EXISTE = new ErrorCode(1_101_007_002, "Mudança não encontrada");
    ErrorCode ITIL_ATOR_INVALIDO = new ErrorCode(1_101_007_003, "Informe uma pessoa ou uma equipe (equipe só como técnico)");
    ErrorCode ITIL_ATOR_DUPLICADO = new ErrorCode(1_101_007_004, "Já participa com esse papel");
    ErrorCode ITIL_ATOR_NAO_EXISTE = new ErrorCode(1_101_007_005, "Participante não encontrado");
    ErrorCode ITIL_ENCERRADO = new ErrorCode(1_101_007_006, "Está encerrado; reabra antes de alterar");
    ErrorCode ITIL_VINCULO_INVALIDO = new ErrorCode(1_101_007_007, "Não é possível vincular esses itens");
    ErrorCode ITIL_VINCULO_DUPLICADO = new ErrorCode(1_101_007_008, "Já estão vinculados");
    ErrorCode ITIL_VINCULO_NAO_EXISTE = new ErrorCode(1_101_007_009, "Vínculo não encontrado");
    ErrorCode ITIL_TAREFA_DATAS = new ErrorCode(1_101_007_010, "O fim da tarefa precisa ser depois do início");
    ErrorCode MUDANCA_JANELA_INVALIDA = new ErrorCode(1_101_007_011, "O fim da janela precisa ser depois do início");
    ErrorCode MUDANCA_APROVACAO_NAO_EXISTE = new ErrorCode(1_101_007_012, "Aprovação não encontrada");
    ErrorCode MUDANCA_APROVACAO_JA_RESPONDIDA = new ErrorCode(1_101_007_013, "Esta aprovação já foi respondida");
    ErrorCode MUDANCA_APROVACAO_DE_OUTRO = new ErrorCode(1_101_007_014, "Só a pessoa indicada pode responder esta aprovação");
    ErrorCode ITIL_SEM_SOLUCAO_CHAMADOS = new ErrorCode(1_101_007_015, "Só a solução pode solucionar os chamados vinculados");

    // governança 1-101-008-xxx
    ErrorCode FORNECEDOR_NAO_EXISTE = new ErrorCode(1_101_008_000, "Fornecedor não encontrado");
    ErrorCode FORNECEDOR_DOCUMENTO_INVALIDO = new ErrorCode(1_101_008_001, "CNPJ ou CPF inválido");
    ErrorCode FORNECEDOR_DOCUMENTO_DUPLICADO = new ErrorCode(1_101_008_002, "Já existe um fornecedor com esse CNPJ/CPF");
    ErrorCode FORNECEDOR_EM_USO = new ErrorCode(1_101_008_003, "O fornecedor tem contratos ou licenças; desative em vez de excluir");
    ErrorCode CONTRATO_NAO_EXISTE = new ErrorCode(1_101_008_010, "Contrato não encontrado");
    ErrorCode CONTRATO_DADOS_INVALIDOS = new ErrorCode(1_101_008_011, "{}");
    ErrorCode CONTRATO_RENOVACAO_INVALIDA = new ErrorCode(1_101_008_012, "Só contrato com renovação expressa e não encerrado pode ser renovado");
    ErrorCode CUSTO_NAO_EXISTE = new ErrorCode(1_101_008_020, "Custo não encontrado");
    ErrorCode CUSTO_SEM_PERMISSAO = new ErrorCode(1_101_008_021, "Sem permissão para lançar custos aqui");
    ErrorCode CUSTO_PERIODO = new ErrorCode(1_101_008_022, "O fim do custo precisa ser depois do início");
    ErrorCode LICENCA_NAO_EXISTE = new ErrorCode(1_101_008_030, "Licença não encontrada");
    ErrorCode LICENCA_ESGOTADA = new ErrorCode(1_101_008_031, "Todas as {} licenças estão em uso; aumente a quantidade ou permita excesso");
    ErrorCode LICENCA_USO_DUPLICADO = new ErrorCode(1_101_008_032, "Já usa esta licença");
    ErrorCode LICENCA_USO_INVALIDO = new ErrorCode(1_101_008_033, "Informe uma pessoa ou um equipamento");
    ErrorCode LICENCA_SEM_CHAVE = new ErrorCode(1_101_008_034, "Esta licença não tem chave guardada");
    ErrorCode GOVERNANCA_SEM_CHAVE_MESTRA = new ErrorCode(1_101_008_035, "Defina CO_TI_CHAVE_MESTRA no servidor: sem ela a chave não pode ser guardada");
    ErrorCode LICENCA_QUANTIDADE_MENOR = new ErrorCode(1_101_008_036, "Há {} em uso: libere antes de diminuir a quantidade (ou permita excesso)");
    ErrorCode LICENCA_USO_NAO_EXISTE = new ErrorCode(1_101_008_037, "Uso de licença não encontrado");
    ErrorCode ORCAMENTO_NAO_EXISTE = new ErrorCode(1_101_008_040, "Orçamento não encontrado");
    ErrorCode ORCAMENTO_EM_USO = new ErrorCode(1_101_008_041, "O orçamento tem custos lançados ou projetos ligados");
    ErrorCode ORCAMENTO_PERIODO = new ErrorCode(1_101_008_042, "O fim do orçamento precisa ser depois do início");
    ErrorCode PROJETO_NAO_EXISTE = new ErrorCode(1_101_008_050, "Projeto não encontrado");
    ErrorCode PROJETO_CODIGO_DUPLICADO = new ErrorCode(1_101_008_051, "Já existe um projeto com esse código");
    ErrorCode PROJETO_DATAS = new ErrorCode(1_101_008_052, "O fim previsto precisa ser depois do início");
    ErrorCode PROJETO_TAREFA_NAO_EXISTE = new ErrorCode(1_101_008_053, "Tarefa não encontrada");
    ErrorCode PROJETO_TAREFA_PAI_INVALIDA = new ErrorCode(1_101_008_054, "A tarefa superior precisa ser do mesmo projeto e não pode ser ela mesma nem uma subtarefa dela");
    ErrorCode PROJETO_TAREFA_SEM_PERMISSAO = new ErrorCode(1_101_008_055, "Só o responsável pela tarefa ou quem gerencia o projeto pode alterá-la");
    ErrorCode PROJETO_VINCULO_DUPLICADO = new ErrorCode(1_101_008_056, "Já está ligado ao projeto");
    ErrorCode PROJETO_VINCULO_NAO_EXISTE = new ErrorCode(1_101_008_057, "Vínculo não encontrado");
    ErrorCode PROJETO_ENCERRADO = new ErrorCode(1_101_008_058, "O projeto está concluído ou cancelado; reabra mudando a situação");

    // importação 1-101-009-xxx
    ErrorCode IMPORTACAO_CONEXAO = new ErrorCode(1_101_009_000, "Não foi possível ler o banco do GLPI: {}");
    ErrorCode IMPORTACAO_EM_ANDAMENTO = new ErrorCode(1_101_009_001, "Já há uma importação em andamento; espere terminar ou cancele");
    ErrorCode IMPORTACAO_NAO_EXECUTANDO = new ErrorCode(1_101_009_002, "Esta importação não está em andamento");
    ErrorCode IMPORTACAO_NAO_EXISTE = new ErrorCode(1_101_009_003, "Importação não encontrada");

    // relatórios 1-101-010-xxx
    ErrorCode RELATORIO_GRANDE_DEMAIS = new ErrorCode(1_101_010_000, "O relatório teria {} linhas e o limite é {}. Use mais filtros e tente de novo");

    // ramais e telefones 1-101-011-xxx
    ErrorCode TELEFONE_NAO_EXISTE = new ErrorCode(1_101_011_000, "Contato não encontrado na lista");
    ErrorCode TELEFONE_SEM_NUMERO = new ErrorCode(1_101_011_001, "Informe o ramal ou o telefone");

}
