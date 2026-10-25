package cn.iocoder.yudao.module.inventario.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

/**
 * Inventário: faixa 1-103-000-000.
 */
public interface ErrorCodeConstants {

    ErrorCode ATIVO_NAO_EXISTE = new ErrorCode(1_103_000_000, "Ativo não encontrado");
    ErrorCode ATIVO_SITUACAO_INVALIDA = new ErrorCode(1_103_000_001, "Situação inválida: use em_uso, estoque, manutencao ou baixado");
    ErrorCode ATIVO_TIPO_INVALIDO = new ErrorCode(1_103_000_002, "Tipo de ativo inválido");
    ErrorCode ATIVO_DO_AGENTE = new ErrorCode(1_103_000_003, "Este campo vem do agente de inventário e é atualizado por ele");
    ErrorCode SOFTWARE_NAO_EXISTE = new ErrorCode(1_103_000_004, "Software não encontrado");
    ErrorCode ALERTA_REGRA_NAO_EXISTE = new ErrorCode(1_103_000_005, "Regra de alerta não encontrada");
    ErrorCode ALERTA_LIMITE_INVALIDO = new ErrorCode(1_103_000_006, "Limite inválido: use {}");

}
