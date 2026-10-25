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
    ErrorCode DISPOSITIVO_NAO_EXISTE = new ErrorCode(1_103_000_007, "Equipamento da rede não encontrado");
    ErrorCode SNMP_CREDENCIAL_NAO_EXISTE = new ErrorCode(1_103_000_008, "Credencial SNMP não encontrada");
    ErrorCode SNMP_CREDENCIAL_INCOMPLETA = new ErrorCode(1_103_000_009, "Credencial SNMP incompleta: {}");
    ErrorCode SNMP_SEM_CHAVE_MESTRA = new ErrorCode(1_103_000_010, "Defina CO_TI_CHAVE_MESTRA no servidor: sem ela as credenciais SNMP não podem ser guardadas");
    ErrorCode SNMP_IP_INVALIDO = new ErrorCode(1_103_000_011, "IP inválido (use IPv4, ex.: 10.20.5.50)");
    ErrorCode SNMP_SEM_CREDENCIAL = new ErrorCode(1_103_000_012, "Cadastre e ative pelo menos uma credencial SNMP");
    ErrorCode SNMP_FALHA = new ErrorCode(1_103_000_013, "Falha no SNMP: {}");
    ErrorCode SNMP_VARRENDO = new ErrorCode(1_103_000_014, "Já há uma varredura SNMP em andamento");
    ErrorCode ALERTA_LIMITE_INVALIDO = new ErrorCode(1_103_000_006, "Limite inválido: use {}");

}
