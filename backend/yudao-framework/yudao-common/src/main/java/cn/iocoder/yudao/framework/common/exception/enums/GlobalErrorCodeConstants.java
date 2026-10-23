package cn.iocoder.yudao.framework.common.exception.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

/**
 * 全局错误码枚举
 * 0-999 系统异常编码保留
 *
 * 一般情况下，使用 HTTP 响应状态码 https://developer.mozilla.org/zh-CN/docs/Web/HTTP/Status
 * 虽然说，HTTP 响应状态码作为业务使用表达能力偏弱，但是使用在系统层面还是非常不错的
 * 比较特殊的是，因为之前一直使用 0 作为成功，就不使用 200 啦。
 *
 * @author 芋道源码
 */
public interface GlobalErrorCodeConstants {

    ErrorCode SUCCESS = new ErrorCode(0, "Sucesso");

    // ========== 客户端错误段 ==========

    ErrorCode BAD_REQUEST = new ErrorCode(400, "Parâmetros inválidos");
    ErrorCode UNAUTHORIZED = new ErrorCode(401, "Sessão expirada: entre novamente");
    ErrorCode FORBIDDEN = new ErrorCode(403, "Você não tem permissão para esta operação");
    ErrorCode NOT_FOUND = new ErrorCode(404, "Endereço não encontrado");
    ErrorCode METHOD_NOT_ALLOWED = new ErrorCode(405, "Método não permitido");
    ErrorCode LOCKED = new ErrorCode(423, "A requisição falhou; tente de novo em instantes"); // 并发请求，不允许
    ErrorCode TOO_MANY_REQUESTS = new ErrorCode(429, "Muitas requisições; aguarde um pouco e tente de novo");

    // ========== 服务端错误段 ==========

    ErrorCode INTERNAL_SERVER_ERROR = new ErrorCode(500, "Erro inesperado no servidor");
    ErrorCode NOT_IMPLEMENTED = new ErrorCode(501, "Funcionalidade indisponível");
    ErrorCode ERROR_CONFIGURATION = new ErrorCode(502, "Configuração inválida");

    // ========== 自定义错误段 ==========
    ErrorCode REPEATED_REQUESTS = new ErrorCode(900, "Requisição repetida; aguarde e tente de novo"); // 重复请求
    ErrorCode DEMO_DENY = new ErrorCode(901, "Modo demonstração: alterações bloqueadas");

    ErrorCode UNKNOWN = new ErrorCode(999, "Erro desconhecido");

}
