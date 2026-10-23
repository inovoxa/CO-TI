package cn.iocoder.yudao.module.identidade.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

/**
 * Erros do módulo identidade (faixa 1-100-000-000).
 */
public interface ErrorCodeConstants {

    ErrorCode MFA_JA_ATIVO = new ErrorCode(1_100_000_000, "A verificação em duas etapas já está ativa. Desative antes de cadastrar outro aparelho");
    ErrorCode MFA_NAO_INICIADO = new ErrorCode(1_100_000_001, "Comece o cadastro da verificação em duas etapas antes de confirmar");
    ErrorCode MFA_CODIGO_INVALIDO = new ErrorCode(1_100_000_002, "Código incorreto. Confira o horário do celular e tente o código atual");
    ErrorCode MFA_NAO_ATIVO = new ErrorCode(1_100_000_003, "A verificação em duas etapas não está ativa");
    ErrorCode MFA_CHAVE_MESTRA_AUSENTE = new ErrorCode(1_100_000_004, "O servidor não tem a chave-mestra configurada (CO_TI_CHAVE_MESTRA). Avise o administrador");

}
