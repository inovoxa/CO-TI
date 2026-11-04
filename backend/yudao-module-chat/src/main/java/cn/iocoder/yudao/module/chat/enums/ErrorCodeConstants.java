package cn.iocoder.yudao.module.chat.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

public interface ErrorCodeConstants {

    ErrorCode CONVERSA_NAO_EXISTE = new ErrorCode(1_108_000_000, "Conversa não encontrada");
    ErrorCode SO_ADMIN_DO_GRUPO = new ErrorCode(1_108_000_001, "Só o administrador do grupo pode fazer isso");
    ErrorCode MENSAGEM_NAO_EXISTE = new ErrorCode(1_108_000_002, "Mensagem não encontrada");
    ErrorCode MENSAGEM_INVALIDA = new ErrorCode(1_108_000_003, "{}");
    ErrorCode ANEXO_INVALIDO = new ErrorCode(1_108_000_004, "{}");
    ErrorCode ANEXO_NAO_EXISTE = new ErrorCode(1_108_000_005, "Anexo não encontrado");
    ErrorCode GRUPO_INVALIDO = new ErrorCode(1_108_000_006, "{}");

}
