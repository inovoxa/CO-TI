package cn.iocoder.yudao.module.atendimento.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

/**
 * Erros do atendimento: 1-102-xxx-xxx.
 */
public interface ErrorCodeConstants {

    ErrorCode CAIXA_NAO_EXISTE = new ErrorCode(1_102_000_000, "Caixa de entrada não encontrada");
    ErrorCode CAIXA_URL_PROIBIDA = new ErrorCode(1_102_000_001, "Endereço do webhook não permitido: {}");
    ErrorCode CAIXA_SEM_CHAVE_MESTRA = new ErrorCode(1_102_000_002, "Defina CO_TI_CHAVE_MESTRA no servidor: sem ela os segredos do canal não podem ser guardados");
    ErrorCode CAIXA_TOKEN_INVALIDO = new ErrorCode(1_102_000_003, "Token da caixa inválido ou caixa desativada");
    ErrorCode CAIXA_EM_USO = new ErrorCode(1_102_000_004, "A caixa tem conversas; desative em vez de excluir");

    ErrorCode CONTATO_NAO_EXISTE = new ErrorCode(1_102_001_000, "Contato não encontrado");
    ErrorCode CONTATO_TELEFONE_INVALIDO = new ErrorCode(1_102_001_001, "Telefone inválido");
    ErrorCode CONTATO_DUPLICADO = new ErrorCode(1_102_001_002, "Já existe um contato com esse {}");
    ErrorCode CONTATO_ORIGEM_OBRIGATORIA = new ErrorCode(1_102_001_003, "Informe origemId (o id do contato no seu sistema)");

    ErrorCode CONVERSA_NAO_EXISTE = new ErrorCode(1_102_002_000, "Conversa não encontrada");
    ErrorCode CONVERSA_SEM_ACESSO = new ErrorCode(1_102_002_001, "Você não participa desta caixa de entrada");
    ErrorCode CONVERSA_RESOLVIDA = new ErrorCode(1_102_002_002, "A conversa está resolvida; reabra para responder");
    ErrorCode CONVERSA_ATRIBUIR_OUTRO = new ErrorCode(1_102_002_003, "Sem permissão para atribuir a outra pessoa");
    ErrorCode CONVERSA_AGENTE_NAO_MEMBRO = new ErrorCode(1_102_002_004, "Essa pessoa não atende esta caixa");
    ErrorCode CONVERSA_VAZIA = new ErrorCode(1_102_002_005, "Escreva a mensagem ou anexe um arquivo");
    ErrorCode CONVERSA_ADIAR_DATA = new ErrorCode(1_102_002_006, "Escolha uma data futura para adiar");
    ErrorCode CONVERSA_JA_TEM_CHAMADO = new ErrorCode(1_102_002_007, "Esta conversa já tem o chamado #{}");
    ErrorCode CONVERSA_ANEXO_INVALIDO = new ErrorCode(1_102_002_008, "{}");

    ErrorCode ETIQUETA_NAO_EXISTE = new ErrorCode(1_102_003_000, "Etiqueta não encontrada");
    ErrorCode ETIQUETA_DUPLICADA = new ErrorCode(1_102_003_001, "Já existe uma etiqueta com esse nome");
    ErrorCode RESPOSTA_NAO_EXISTE = new ErrorCode(1_102_003_010, "Resposta pronta não encontrada");
    ErrorCode RESPOSTA_DUPLICADA = new ErrorCode(1_102_003_011, "Já existe uma resposta com esse atalho");

    ErrorCode CANAL_TIPO_ERRADO = new ErrorCode(1_102_004_000, "Esta caixa não é do canal {}");
    ErrorCode CANAL_CAMPO_OBRIGATORIO = new ErrorCode(1_102_004_001, "Informe {}");
    ErrorCode CANAL_DUPLICADO = new ErrorCode(1_102_004_002, "{} já está na caixa \"{}\"");
    ErrorCode CANAL_SEM_URL_PUBLICA = new ErrorCode(1_102_004_003, "Defina CO_TI_URL_PUBLICA (endereço público do CO-TI): o provedor precisa dele para mandar as mensagens");
    ErrorCode CANAL_TESTE_FALHOU = new ErrorCode(1_102_004_004, "{}");
    ErrorCode MODELO_NAO_EXISTE = new ErrorCode(1_102_004_010, "Modelo não encontrado ou não aprovado");
    ErrorCode ACAO_INVALIDA = new ErrorCode(1_102_005_000, "{}");
    ErrorCode AUTOMACAO_NAO_EXISTE = new ErrorCode(1_102_005_001, "Automação não encontrada");
    ErrorCode MACRO_NAO_EXISTE = new ErrorCode(1_102_005_010, "Macro não encontrada");
    ErrorCode MACRO_SEM_PERMISSAO = new ErrorCode(1_102_005_011, "Sem permissão para macros de todos");
    ErrorCode CAIXA_CALENDARIO_INVALIDO = new ErrorCode(1_102_000_005, "Calendário não encontrado");
    ErrorCode MODELO_PARAMETROS = new ErrorCode(1_102_004_011, "O modelo pede {} variáveis; preencha todas");

}
