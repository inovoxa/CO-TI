package cn.iocoder.yudao.module.cofre.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

/**
 * Erros do módulo cofre (faixa 1-107-000-000).
 */
public interface ErrorCodeConstants {

    ErrorCode PASTA_NAO_EXISTE = new ErrorCode(1_107_000_000, "Pasta não encontrada");
    ErrorCode PASTA_SEM_ACESSO = new ErrorCode(1_107_000_001, "Você não tem esse acesso nesta pasta");
    ErrorCode PASTA_NOME_REPETIDO = new ErrorCode(1_107_000_002, "Já existe uma pasta com esse nome");
    ErrorCode PASTA_COM_SEGREDOS = new ErrorCode(1_107_000_003, "Exclua ou mova as senhas da pasta antes de excluí-la");
    ErrorCode PASTA_ULTIMO_GESTOR = new ErrorCode(1_107_000_004, "A pasta precisa de pelo menos uma pessoa que gerencie");
    ErrorCode PAPEL_INVALIDO = new ErrorCode(1_107_000_005, "Papel inválido");
    ErrorCode SEGREDO_NAO_EXISTE = new ErrorCode(1_107_000_006, "Senha não encontrada");
    ErrorCode SEGREDO_SENHA_OBRIGATORIA = new ErrorCode(1_107_000_007, "Informe a senha");
    ErrorCode COFRE_SEM_CHAVE_MESTRA = new ErrorCode(1_107_000_008, "O servidor não tem a chave-mestra configurada (CO_TI_CHAVE_MESTRA). Avise o administrador");
    ErrorCode COFRE_EXIGE_MFA = new ErrorCode(1_107_000_009, "Para ver senhas do cofre, ative a verificação em duas etapas em Meu perfil");

}
