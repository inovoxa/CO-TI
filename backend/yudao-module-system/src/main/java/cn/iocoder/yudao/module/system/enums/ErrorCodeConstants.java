package cn.iocoder.yudao.module.system.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

/**
 * System 错误码枚举类
 *
 * system 系统，使用 1-002-000-000 段
 */
public interface ErrorCodeConstants {

    // ========== AUTH 模块 1-002-000-000 ==========
    ErrorCode AUTH_LOGIN_BAD_CREDENTIALS = new ErrorCode(1_002_000_000, "Usuário ou senha incorretos");
    ErrorCode AUTH_LOGIN_USER_DISABLED = new ErrorCode(1_002_000_001, "Esta conta está desativada");
    ErrorCode AUTH_LOGIN_CAPTCHA_CODE_ERROR = new ErrorCode(1_002_000_004, "Verificação de segurança incorreta: {}");
    ErrorCode AUTH_THIRD_LOGIN_NOT_BIND = new ErrorCode(1_002_000_005, "Conta externa não vinculada");
    ErrorCode AUTH_MOBILE_NOT_EXISTS = new ErrorCode(1_002_000_007, "Celular não cadastrado");
    ErrorCode AUTH_REGISTER_CAPTCHA_CODE_ERROR = new ErrorCode(1_002_000_008, "Verificação de segurança incorreta: {}");
    // CO-TI
    ErrorCode AUTH_MFA_DESAFIO_INVALIDO = new ErrorCode(1_002_000_050, "A verificação expirou. Entre com usuário e senha de novo");
    ErrorCode AUTH_MFA_CODIGO_INVALIDO = new ErrorCode(1_002_000_051, "Código de verificação incorreto");
    ErrorCode AUTH_LOGIN_ALTERNATIVO_DESLIGADO = new ErrorCode(1_002_000_052, "Esta forma de acesso está desativada");

    // ========== 菜单模块 1-002-001-000 ==========
    ErrorCode MENU_NAME_DUPLICATE = new ErrorCode(1_002_001_000, "Já existe um menu com esse nome");
    ErrorCode MENU_PARENT_NOT_EXISTS = new ErrorCode(1_002_001_001, "O menu superior não existe");
    ErrorCode MENU_PARENT_ERROR = new ErrorCode(1_002_001_002, "Um menu não pode ser pai dele mesmo");
    ErrorCode MENU_NOT_EXISTS = new ErrorCode(1_002_001_003, "Menu não encontrado");
    ErrorCode MENU_EXISTS_CHILDREN = new ErrorCode(1_002_001_004, "Exclua os submenus antes");
    ErrorCode MENU_PARENT_NOT_DIR_OR_MENU = new ErrorCode(1_002_001_005, "O menu superior precisa ser diretório ou menu");
    ErrorCode MENU_COMPONENT_NAME_DUPLICATE = new ErrorCode(1_002_001_006, "Já existe um menu com esse componente");

    // ========== 角色模块 1-002-002-000 ==========
    ErrorCode ROLE_NOT_EXISTS = new ErrorCode(1_002_002_000, "Perfil não encontrado");
    ErrorCode ROLE_NAME_DUPLICATE = new ErrorCode(1_002_002_001, "Já existe o perfil “{}”");
    ErrorCode ROLE_CODE_DUPLICATE = new ErrorCode(1_002_002_002, "Já existe um perfil com o código “{}”");
    ErrorCode ROLE_CAN_NOT_UPDATE_SYSTEM_TYPE_ROLE = new ErrorCode(1_002_002_003, "Perfis do sistema não podem ser alterados");
    ErrorCode ROLE_IS_DISABLE = new ErrorCode(1_002_002_004, "O perfil “{}” está desativado");
    ErrorCode ROLE_ADMIN_CODE_ERROR = new ErrorCode(1_002_002_005, "O código “{}” não pode ser usado");

    // ========== 用户模块 1-002-003-000 ==========
    ErrorCode USER_USERNAME_EXISTS = new ErrorCode(1_002_003_000, "Este usuário já existe");
    ErrorCode USER_MOBILE_EXISTS = new ErrorCode(1_002_003_001, "Este celular já está cadastrado");
    ErrorCode USER_EMAIL_EXISTS = new ErrorCode(1_002_003_002, "Este e-mail já está cadastrado");
    ErrorCode USER_NOT_EXISTS = new ErrorCode(1_002_003_003, "Usuário não encontrado");
    ErrorCode USER_IMPORT_LIST_IS_EMPTY = new ErrorCode(1_002_003_004, "A planilha está vazia!");
    ErrorCode USER_PASSWORD_FAILED = new ErrorCode(1_002_003_005, "Senha atual incorreta");
    ErrorCode USER_IS_DISABLE = new ErrorCode(1_002_003_006, "O usuário “{}” está desativado");
    ErrorCode USER_COUNT_MAX = new ErrorCode(1_002_003_008, "Limite de usuários da licença atingido ({})!");
    ErrorCode USER_IMPORT_INIT_PASSWORD = new ErrorCode(1_002_003_009, "Informe a senha inicial");
    ErrorCode USER_MOBILE_NOT_EXISTS = new ErrorCode(1_002_003_010, "Celular não cadastrado");
    ErrorCode USER_REGISTER_DISABLED = new ErrorCode(1_002_003_011, "Cadastro desativado");
    ErrorCode USER_ADMIN_NOT_ALLOW_DISABLE = new ErrorCode(1_002_003_012, "A conta de administrador não pode ser desativada");
    ErrorCode USER_ADMIN_NOT_ALLOW_DELETE = new ErrorCode(1_002_003_013, "A conta de administrador não pode ser excluída");

    // ========== 部门模块 1-002-004-000 ==========
    ErrorCode DEPT_NAME_DUPLICATE = new ErrorCode(1_002_004_000, "Já existe um departamento com esse nome");
    ErrorCode DEPT_PARENT_NOT_EXITS = new ErrorCode(1_002_004_001,"O departamento superior não existe");
    ErrorCode DEPT_NOT_FOUND = new ErrorCode(1_002_004_002, "Departamento não encontrado");
    ErrorCode DEPT_EXITS_CHILDREN = new ErrorCode(1_002_004_003, "Exclua os subdepartamentos antes");
    ErrorCode DEPT_PARENT_ERROR = new ErrorCode(1_002_004_004, "Um departamento não pode ser pai dele mesmo");
    ErrorCode DEPT_NOT_ENABLE = new ErrorCode(1_002_004_006, "O departamento ({}) está desativado");
    ErrorCode DEPT_PARENT_IS_CHILD = new ErrorCode(1_002_004_007, "Um subdepartamento não pode virar pai do próprio superior");
    ErrorCode DEPT_NOT_IN_SCOPE = new ErrorCode(1_002_004_008, "O departamento ({}) está fora do seu alcance de dados");

    // ========== 岗位模块 1-002-005-000 ==========
    ErrorCode POST_NOT_FOUND = new ErrorCode(1_002_005_000, "Cargo não encontrado");
    ErrorCode POST_NOT_ENABLE = new ErrorCode(1_002_005_001, "O cargo ({}) está desativado");
    ErrorCode POST_NAME_DUPLICATE = new ErrorCode(1_002_005_002, "Já existe um cargo com esse nome");
    ErrorCode POST_CODE_DUPLICATE = new ErrorCode(1_002_005_003, "Já existe um cargo com esse código");

    // ========== 字典类型 1-002-006-000 ==========
    ErrorCode DICT_TYPE_NOT_EXISTS = new ErrorCode(1_002_006_001, "Dicionário não encontrado");
    ErrorCode DICT_TYPE_NOT_ENABLE = new ErrorCode(1_002_006_002, "O dicionário está desativado");
    ErrorCode DICT_TYPE_NAME_DUPLICATE = new ErrorCode(1_002_006_003, "Já existe um dicionário com esse nome");
    ErrorCode DICT_TYPE_TYPE_DUPLICATE = new ErrorCode(1_002_006_004, "Já existe um dicionário com esse tipo");
    ErrorCode DICT_TYPE_HAS_CHILDREN = new ErrorCode(1_002_006_005, "Exclua os itens do dicionário antes");

    // ========== 字典数据 1-002-007-000 ==========
    ErrorCode DICT_DATA_NOT_EXISTS = new ErrorCode(1_002_007_001, "Item do dicionário não encontrado");
    ErrorCode DICT_DATA_NOT_ENABLE = new ErrorCode(1_002_007_002, "O item ({}) está desativado");
    ErrorCode DICT_DATA_VALUE_DUPLICATE = new ErrorCode(1_002_007_003, "Já existe um item com esse valor");

    // ========== 通知公告 1-002-008-000 ==========
    ErrorCode NOTICE_NOT_FOUND = new ErrorCode(1_002_008_001, "Aviso não encontrado");

    // ========== 短信渠道 1-002-011-000 ==========
    ErrorCode SMS_CHANNEL_NOT_EXISTS = new ErrorCode(1_002_011_000, "Canal de SMS não encontrado");
    ErrorCode SMS_CHANNEL_DISABLE = new ErrorCode(1_002_011_001, "O canal de SMS está desativado");
    ErrorCode SMS_CHANNEL_HAS_CHILDREN = new ErrorCode(1_002_011_002, "Exclua os modelos de SMS do canal antes");

    // ========== 短信模板 1-002-012-000 ==========
    ErrorCode SMS_TEMPLATE_NOT_EXISTS = new ErrorCode(1_002_012_000, "Modelo de SMS não encontrado");
    ErrorCode SMS_TEMPLATE_CODE_DUPLICATE = new ErrorCode(1_002_012_001, "Já existe o modelo de SMS “{}”");
    ErrorCode SMS_TEMPLATE_API_ERROR = new ErrorCode(1_002_012_002, "Falha no provedor de SMS: {}");
    ErrorCode SMS_TEMPLATE_API_AUDIT_CHECKING = new ErrorCode(1_002_012_003, "Modelo de SMS em aprovação no provedor");
    ErrorCode SMS_TEMPLATE_API_AUDIT_FAIL = new ErrorCode(1_002_012_004, "Modelo de SMS reprovado pelo provedor: {}");
    ErrorCode SMS_TEMPLATE_API_NOT_FOUND = new ErrorCode(1_002_012_005, "Modelo de SMS não existe no provedor");

    // ========== 短信发送 1-002-013-000 ==========
    ErrorCode SMS_SEND_MOBILE_NOT_EXISTS = new ErrorCode(1_002_013_000, "Celular não cadastrado");
    ErrorCode SMS_SEND_MOBILE_TEMPLATE_PARAM_MISS = new ErrorCode(1_002_013_001, "Falta o parâmetro ({}) do modelo");
    ErrorCode SMS_SEND_TEMPLATE_NOT_EXISTS = new ErrorCode(1_002_013_002, "Modelo de SMS não encontrado");

    // ========== 短信验证码 1-002-014-000 ==========
    ErrorCode SMS_CODE_NOT_FOUND = new ErrorCode(1_002_014_000, "Código inválido");
    ErrorCode SMS_CODE_EXPIRED = new ErrorCode(1_002_014_001, "Código expirado");
    ErrorCode SMS_CODE_USED = new ErrorCode(1_002_014_002, "Código já usado");
    ErrorCode SMS_CODE_EXCEED_SEND_MAXIMUM_QUANTITY_PER_DAY = new ErrorCode(1_002_014_004, "Limite diário de SMS atingido");
    ErrorCode SMS_CODE_SEND_TOO_FAST = new ErrorCode(1_002_014_005, "Aguarde antes de pedir outro SMS");

    // ========== 租户信息 1-002-015-000 ==========
    ErrorCode TENANT_NOT_EXISTS = new ErrorCode(1_002_015_000, "Empresa não encontrada");
    ErrorCode TENANT_DISABLE = new ErrorCode(1_002_015_001, "A empresa “{}” está desativada");
    ErrorCode TENANT_EXPIRE = new ErrorCode(1_002_015_002, "A licença da empresa “{}” expirou");
    ErrorCode TENANT_CAN_NOT_UPDATE_SYSTEM = new ErrorCode(1_002_015_003, "A empresa do sistema não pode ser alterada nem excluída!");
    ErrorCode TENANT_NAME_DUPLICATE = new ErrorCode(1_002_015_004, "Já existe a empresa “{}”");
    ErrorCode TENANT_WEBSITE_DUPLICATE = new ErrorCode(1_002_015_005, "Já existe empresa com o domínio “{}”");

    // ========== 租户套餐 1-002-016-000 ==========
    ErrorCode TENANT_PACKAGE_NOT_EXISTS = new ErrorCode(1_002_016_000, "Plano não encontrado");
    ErrorCode TENANT_PACKAGE_USED = new ErrorCode(1_002_016_001, "O plano está em uso; troque o plano da empresa antes");
    ErrorCode TENANT_PACKAGE_DISABLE = new ErrorCode(1_002_016_002, "O plano “{}” está desativado");
    ErrorCode TENANT_PACKAGE_NAME_DUPLICATE = new ErrorCode(1_002_016_003, "Já existe um plano com esse nome");

    // ========== 社交用户 1-002-018-000 ==========
    ErrorCode SOCIAL_USER_AUTH_FAILURE = new ErrorCode(1_002_018_000, "Falha no login externo: {}");
    ErrorCode SOCIAL_USER_NOT_FOUND = new ErrorCode(1_002_018_001, "Login externo sem usuário vinculado");

    ErrorCode SOCIAL_CLIENT_WEIXIN_MINI_APP_PHONE_CODE_ERROR = new ErrorCode(1_002_018_200, "Não foi possível obter o celular");
    ErrorCode SOCIAL_CLIENT_WEIXIN_MINI_APP_QRCODE_ERROR = new ErrorCode(1_002_018_201, "Falha ao gerar o código do mini app");
    ErrorCode SOCIAL_CLIENT_WEIXIN_MINI_APP_SUBSCRIBE_TEMPLATE_ERROR = new ErrorCode(1_002_018_202, "Falha ao obter os modelos do mini app");
    ErrorCode SOCIAL_CLIENT_WEIXIN_MINI_APP_SUBSCRIBE_MESSAGE_ERROR = new ErrorCode(1_002_018_203, "Falha ao enviar mensagem do mini app");
    ErrorCode SOCIAL_CLIENT_WEIXIN_MINI_APP_ORDER_UPLOAD_SHIPPING_INFO_ERROR = new ErrorCode(1_002_018_204, "Falha ao enviar dados de entrega");
    ErrorCode SOCIAL_CLIENT_WEIXIN_MINI_APP_ORDER_NOTIFY_CONFIRM_RECEIVE_ERROR = new ErrorCode(1_002_018_205, "Falha ao enviar dados de recebimento");
    ErrorCode SOCIAL_CLIENT_NOT_EXISTS = new ErrorCode(1_002_018_210, "Cliente de login externo não encontrado");
    ErrorCode SOCIAL_CLIENT_UNIQUE = new ErrorCode(1_002_018_211, "Cliente de login externo já configurado");

    // ========== OAuth2 客户端 1-002-020-000 =========
    ErrorCode OAUTH2_CLIENT_NOT_EXISTS = new ErrorCode(1_002_020_000, "Cliente OAuth2 não encontrado");
    ErrorCode OAUTH2_CLIENT_EXISTS = new ErrorCode(1_002_020_001, "Já existe um cliente OAuth2 com esse código");
    ErrorCode OAUTH2_CLIENT_DISABLE = new ErrorCode(1_002_020_002, "Cliente OAuth2 desativado");
    ErrorCode OAUTH2_CLIENT_AUTHORIZED_GRANT_TYPE_NOT_EXISTS = new ErrorCode(1_002_020_003, "Tipo de concessão não suportado");
    ErrorCode OAUTH2_CLIENT_SCOPE_OVER = new ErrorCode(1_002_020_004, "Escopo maior que o permitido");
    ErrorCode OAUTH2_CLIENT_REDIRECT_URI_NOT_MATCH = new ErrorCode(1_002_020_005, "redirect_uri inválido: {}");
    ErrorCode OAUTH2_CLIENT_CLIENT_SECRET_ERROR = new ErrorCode(1_002_020_006, "client_secret inválido: {}");

    // ========== OAuth2 授权 1-002-021-000 =========
    ErrorCode OAUTH2_GRANT_CLIENT_ID_MISMATCH = new ErrorCode(1_002_021_000, "client_id não confere");
    ErrorCode OAUTH2_GRANT_REDIRECT_URI_MISMATCH = new ErrorCode(1_002_021_001, "redirect_uri não confere");
    ErrorCode OAUTH2_GRANT_STATE_MISMATCH = new ErrorCode(1_002_021_002, "state não confere");

    // ========== OAuth2 授权 1-002-022-000 =========
    ErrorCode OAUTH2_CODE_NOT_EXISTS = new ErrorCode(1_002_022_000, "code inválido");
    ErrorCode OAUTH2_CODE_EXPIRE = new ErrorCode(1_002_022_001, "code expirado");

    // ========== 邮箱账号 1-002-023-000 ==========
    ErrorCode MAIL_ACCOUNT_NOT_EXISTS = new ErrorCode(1_002_023_000, "Conta de e-mail não encontrada");
    ErrorCode MAIL_ACCOUNT_RELATE_TEMPLATE_EXISTS = new ErrorCode(1_002_023_001, "Exclua os modelos dessa conta de e-mail antes");

    // ========== 邮件模版 1-002-024-000 ==========
    ErrorCode MAIL_TEMPLATE_NOT_EXISTS = new ErrorCode(1_002_024_000, "Modelo de e-mail não encontrado");
    ErrorCode MAIL_TEMPLATE_CODE_EXISTS = new ErrorCode(1_002_024_001, "Já existe o modelo de e-mail ({})");

    // ========== 邮件发送 1-002-025-000 ==========
    ErrorCode MAIL_SEND_TEMPLATE_PARAM_MISS = new ErrorCode(1_002_025_000, "Falta o parâmetro ({}) do modelo");
    ErrorCode MAIL_SEND_MAIL_NOT_EXISTS = new ErrorCode(1_002_025_001, "E-mail não encontrado");

    // ========== 站内信模版 1-002-026-000 ==========
    ErrorCode NOTIFY_TEMPLATE_NOT_EXISTS = new ErrorCode(1_002_026_000, "Modelo de mensagem não encontrado");
    ErrorCode NOTIFY_TEMPLATE_CODE_DUPLICATE = new ErrorCode(1_002_026_001, "Já existe o modelo de mensagem “{}”");

    // ========== 站内信模版 1-002-027-000 ==========

    // ========== 站内信发送 1-002-028-000 ==========
    ErrorCode NOTIFY_SEND_TEMPLATE_PARAM_MISS = new ErrorCode(1_002_028_000, "Falta o parâmetro ({}) do modelo");

}
