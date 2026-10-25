package cn.iocoder.yudao.module.monitoramento.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

/**
 * Monitoramento: faixa 1-105-000-000.
 */
public interface ErrorCodeConstants {

    ErrorCode MONITOR_NAO_EXISTE = new ErrorCode(1_105_000_000, "Monitor não encontrado");
    ErrorCode MONITOR_INVALIDO = new ErrorCode(1_105_000_001, "Monitor inválido: {}");
    ErrorCode MANUTENCAO_NAO_EXISTE = new ErrorCode(1_105_000_002, "Manutenção não encontrada");
    ErrorCode MANUTENCAO_INVALIDA = new ErrorCode(1_105_000_003, "Manutenção inválida: {}");
    ErrorCode CANAL_NAO_EXISTE = new ErrorCode(1_105_000_004, "Canal de aviso não encontrado");
    ErrorCode CANAL_INVALIDO = new ErrorCode(1_105_000_005, "Canal de aviso inválido: {}");
    ErrorCode CANAL_SEM_CHAVE_MESTRA = new ErrorCode(1_105_000_006, "Defina CO_TI_CHAVE_MESTRA no servidor: sem ela os tokens dos canais não podem ser guardados");
    ErrorCode UNIDADE_NAO_EXISTE = new ErrorCode(1_105_000_007, "Unidade não encontrada");
    ErrorCode UNIDADE_INVALIDA = new ErrorCode(1_105_000_008, "Unidade inválida: {}");
    ErrorCode CEP_NAO_ENCONTRADO = new ErrorCode(1_105_000_009, "CEP não encontrado");
    ErrorCode ENDERECO_INDISPONIVEL = new ErrorCode(1_105_000_010, "Busca de endereço indisponível: {}");
    ErrorCode KUMA_INVALIDO = new ErrorCode(1_105_000_011, "Arquivo do Uptime Kuma inválido: {}");
    ErrorCode KUMA_ANALISE_EXPIRADA = new ErrorCode(1_105_000_012, "A análise do arquivo expirou; envie o arquivo de novo");

}
