package cn.iocoder.yudao.module.logs.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

/**
 * Logs: faixa 1-106-000-000.
 */
public interface ErrorCodeConstants {

    ErrorCode CONSULTA_INVALIDA = new ErrorCode(1_106_000_000, "Consulta inválida: {}");
    ErrorCode PAINEL_NAO_EXISTE = new ErrorCode(1_106_000_001, "Painel não encontrado");
    ErrorCode PAINEL_INVALIDO = new ErrorCode(1_106_000_002, "Painel inválido: {}");
    ErrorCode CAMPO_INVALIDO = new ErrorCode(1_106_000_003, "Campo inválido: {}");

}
