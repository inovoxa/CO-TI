package cn.iocoder.yudao.module.acesso.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

public interface ErrorCodeConstants {

    ErrorCode LINK_NAO_EXISTE = new ErrorCode(1_109_000_000, "Link não encontrado");
    ErrorCode LINK_ENDERECO_INVALIDO = new ErrorCode(1_109_000_001, "Endereço inválido: use um endereço http ou https, sem usuário e senha");
    ErrorCode LINK_LIMITE = new ErrorCode(1_109_000_002, "Você já tem {} links pessoais. Remova um para adicionar outro");

}
