package cn.iocoder.yudao.module.assistente.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

/**
 * Assistente interno: faixa 1-104-000-000.
 */
public interface ErrorCodeConstants {

    ErrorCode SESSAO_NAO_EXISTE = new ErrorCode(1_104_000_000, "Conversa não encontrada");
    ErrorCode IA_INDISPONIVEL = new ErrorCode(1_104_000_001, "O assistente não está disponível: {}");

}
