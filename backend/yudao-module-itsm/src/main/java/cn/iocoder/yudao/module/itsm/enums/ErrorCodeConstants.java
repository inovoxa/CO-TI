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

}
