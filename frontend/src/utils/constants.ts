/**
 * Created by 芋道源码
 *
 * 枚举类
 */

// ========== COMMON 模块 ==========
// 全局通用状态枚举
export const CommonStatusEnum = {
  ENABLE: 0, // 开启
  DISABLE: 1 // 禁用
}

// 全局用户类型枚举
export const UserTypeEnum = {
  MEMBER: 1, // 会员
  ADMIN: 2 // 管理员
}

// ========== SYSTEM 模块 ==========
/**
 * 菜单的类型枚举
 */
export const SystemMenuTypeEnum = {
  DIR: 1, // 目录
  MENU: 2, // 菜单
  BUTTON: 3 // 按钮
}

/**
 * 角色的类型枚举
 */
export const SystemRoleTypeEnum = {
  SYSTEM: 1, // 内置角色
  CUSTOM: 2 // 自定义角色
}

/**
 * 数据权限的范围枚举
 */
export const SystemDataScopeEnum = {
  ALL: 1, // 全部数据权限
  DEPT_CUSTOM: 2, // 指定部门数据权限
  DEPT_ONLY: 3, // 部门数据权限
  DEPT_AND_CHILD: 4, // 部门及以下数据权限
  DEPT_SELF: 5 // 仅本人数据权限
}

/**
 * 用户的社交平台的类型枚举
 */
export const SystemUserSocialTypeEnum = {
  DINGTALK: {
    title: 'DingTalk',
    type: 20,
    source: 'dingtalk',
    img: 'https://s1.ax1x.com/2022/05/22/OzMDRs.png'
  },
  WECHAT_ENTERPRISE: {
    title: 'WeCom',
    type: 30,
    source: 'wechat_enterprise',
    img: 'https://s1.ax1x.com/2022/05/22/OzMrzn.png'
  }
}

/**
 * 用户性别枚举（对齐后端 system_user_sex 字典）
 */
export const SystemUserSexEnum = {
  UNKNOWN: 0, // 未知
  MALE: 1, // 男
  FEMALE: 2 // 女
}

// ========== INFRA 模块 ==========
/**
 * 代码生成模板类型
 */
export const InfraCodegenTemplateTypeEnum = {
  CRUD: 1, // 基础 CRUD
  TREE: 2, // 树形 CRUD
  SUB: 15 // 主子表 CRUD
}

/**
 * 任务状态的枚举
 */
export const InfraJobStatusEnum = {
  INIT: 0, // 初始化中
  NORMAL: 1, // 运行中
  STOP: 2 // 暂停运行
}

/**
 * API 异常数据的处理状态
 */
export const InfraApiErrorLogProcessStatusEnum = {
  INIT: 0, // 未处理
  DONE: 1, // 已处理
  IGNORE: 2 // 已忽略
}

// ========== PAY 模块 ==========
/**
 * 支付渠道枚举
 */
export const PayChannelEnum = {
  WX_PUB: {
    code: 'wx_pub',
    name: 'WeChat JSAPI'
  },
  WX_LITE: {
    code: 'wx_lite',
    name: 'WeChat Mini Program'
  },
  WX_APP: {
    code: 'wx_app',
    name: 'WeChat App'
  },
  WX_NATIVE: {
    code: 'wx_native',
    name: 'WeChat Native'
  },
  WX_WAP: {
    code: 'wx_wap',
    name: 'WeChat WAP'
  },
  WX_BAR: {
    code: 'wx_bar',
    name: 'WeChat código de barras'
  },
  ALIPAY_PC: {
    code: 'alipay_pc',
    name: 'Alipay PC'
  },
  ALIPAY_WAP: {
    code: 'alipay_wap',
    name: 'Alipay WAP'
  },
  ALIPAY_APP: {
    code: 'alipay_app',
    name: 'Alipay App'
  },
  ALIPAY_QR: {
    code: 'alipay_qr',
    name: 'Alipay QR code'
  },
  ALIPAY_BAR: {
    code: 'alipay_bar',
    name: 'Alipay código de barras'
  },
  WALLET: {
    code: 'wallet',
    name: 'Carteira'
  },
  MOCK: {
    code: 'mock',
    name: 'Pagamento simulado'
  }
}

/**
 * 支付的展示模式每局
 */
export const PayDisplayModeEnum = {
  URL: {
    mode: 'url'
  },
  IFRAME: {
    mode: 'iframe'
  },
  FORM: {
    mode: 'form'
  },
  QR_CODE: {
    mode: 'qr_code'
  },
  APP: {
    mode: 'app'
  }
}

/**
 * 支付类型枚举
 */
export const PayType = {
  WECHAT: 'WECHAT',
  ALIPAY: 'ALIPAY',
  MOCK: 'MOCK'
}

/**
 * 支付订单状态枚举
 */
export const PayOrderStatusEnum = {
  WAITING: {
    status: 0,
    name: 'Não pago'
  },
  SUCCESS: {
    status: 10,
    name: 'Pago'
  },
  CLOSED: {
    status: 20,
    name: 'Não pago'
  }
}

// ========== MALL - 商品模块 ==========
/**
 * 商品 SPU 状态
 */
export const ProductSpuStatusEnum = {
  RECYCLE: {
    status: -1,
    name: 'Lixeira'
  },
  DISABLE: {
    status: 0,
    name: 'Indisponível'
  },
  ENABLE: {
    status: 1,
    name: 'Disponível'
  }
}

// ========== MALL - 营销模块 ==========
/**
 * 优惠劵模板的有限期类型的枚举
 */
export const CouponTemplateValidityTypeEnum = {
  DATE: {
    type: 1,
    name: 'Válido em datas fixas'
  },
  TERM: {
    type: 2,
    name: 'Válido após resgate'
  }
}

/**
 * 优惠劵模板的领取方式的枚举
 */
export const CouponTemplateTakeTypeEnum = {
  USER: {
    type: 1,
    name: 'Resgate direto'
  },
  ADMIN: {
    type: 2,
    name: 'Distribuição dirigida'
  },
  REGISTER: {
    type: 3,
    name: 'Cupom de boas-vindas'
  }
}

/**
 * 营销的商品范围枚举
 */
export const PromotionProductScopeEnum = {
  ALL: {
    scope: 1,
    name: 'Cupom geral'
  },
  SPU: {
    scope: 2,
    name: 'Cupom de produto'
  },
  CATEGORY: {
    scope: 3,
    name: 'Cupom de categoria'
  }
}

/**
 * 营销的条件类型枚举
 */
export const PromotionConditionTypeEnum = {
  PRICE: {
    type: 10,
    name: 'Acima de N reais'
  },
  COUNT: {
    type: 20,
    name: 'Acima de N itens'
  }
}

/**
 * 优惠类型枚举
 */
export const PromotionDiscountTypeEnum = {
  PRICE: {
    type: 1,
    name: 'Desconto por valor'
  },
  PERCENT: {
    type: 2,
    name: 'Desconto'
  }
}

// ========== MALL - 交易模块 ==========
/**
 * 分销关系绑定模式枚举
 */
export const BrokerageBindModeEnum = {
  ANYTIME: {
    mode: 1,
    name: 'Primeiro vínculo'
  },
  REGISTER: {
    mode: 2,
    name: 'Vínculo no cadastro'
  },
  OVERRIDE: {
    mode: 3,
    name: 'Substituir vínculo'
  }
}
/**
 * 分佣模式枚举
 */
export const BrokerageEnabledConditionEnum = {
  ALL: {
    condition: 1,
    name: 'Todos revendem'
  },
  ADMIN: {
    condition: 2,
    name: 'Revenda autorizada'
  }
}
/**
 * 佣金记录业务类型枚举
 */
export const BrokerageRecordBizTypeEnum = {
  ORDER: {
    type: 1,
    name: 'Comissão recebida'
  },
  WITHDRAW: {
    type: 2,
    name: 'Pedido de saque'
  }
}
/**
 * 佣金提现状态枚举
 */
export const BrokerageWithdrawStatusEnum = {
  AUDITING: {
    status: 0,
    name: 'Em análise'
  },
  AUDIT_SUCCESS: {
    status: 10,
    name: 'Aprovado'
  },
  AUDIT_FAIL: {
    status: 20,
    name: 'Reprovado'
  },
  WITHDRAW_SUCCESS: {
    status: 11,
    name: 'Saque concluído'
  },
  WITHDRAW_FAIL: {
    status: 21,
    name: 'Saque falhou'
  }
}
/**
 * 佣金提现类型枚举
 */
export const BrokerageWithdrawTypeEnum = {
  WALLET: {
    type: 1,
    name: 'Carteira'
  },
  BANK: {
    type: 2,
    name: 'Cartão bancário'
  },
  WECHAT: {
    type: 3,
    name: 'WeChat'
  },
  ALIPAY: {
    type: 4,
    name: 'Alipay'
  }
}

/**
 * 配送方式枚举
 */
export const DeliveryTypeEnum = {
  EXPRESS: {
    type: 1,
    name: 'Entrega'
  },
  PICK_UP: {
    type: 2,
    name: 'Retirada na loja'
  }
}
/**
 * 交易订单 - 状态
 */
export const TradeOrderStatusEnum = {
  UNPAID: {
    status: 0,
    name: 'Aguardando pagamento'
  },
  UNDELIVERED: {
    status: 10,
    name: 'Aguardando envio'
  },
  DELIVERED: {
    status: 20,
    name: 'Enviado'
  },
  COMPLETED: {
    status: 30,
    name: 'Concluído'
  },
  CANCELED: {
    status: 40,
    name: 'Cancelado'
  }
}

// ========== ERP - 企业资源计划 ==========

export const ErpBizType = {
  PURCHASE_ORDER: 10,
  PURCHASE_IN: 11,
  PURCHASE_RETURN: 12,
  SALE_ORDER: 20,
  SALE_OUT: 21,
  SALE_RETURN: 22
}

// ========== BPM 模块 ==========

export const BpmModelType = {
  BPMN: 10, // BPMN 设计器
  SIMPLE: 20 // 简易设计器
}

export const BpmModelFormType = {
  NORMAL: 10, // 流程表单
  CUSTOM: 20 // 业务表单
}

export const BpmProcessInstanceStatus = {
  NOT_START: -1, // 未开始
  RUNNING: 1, // 审批中
  APPROVE: 2, // 审批通过
  REJECT: 3, // 审批不通过
  CANCEL: 4 // 已取消
}

export const BpmAutoApproveType = {
  NONE: 0, // 不自动通过
  APPROVE_ALL: 1, // 仅审批一次，后续重复的审批节点均自动通过
  APPROVE_SEQUENT: 2 // 仅针对连续审批的节点自动通过
}

// ========== SYSTEM - 地区模块 ==========
/**
 * 地区选择器层级枚举
 */
export const AreaLevelEnum = {
  PROVINCE: 1, // 省
  CITY: 2, // 市
  DISTRICT: 3 // 区
}

/** 执行监听器事件选项 */
export const BPM_PROCESS_LISTENER_EXECUTION_EVENT_OPTIONS = [
  { label: 'Início', value: 'start' },
  { label: 'Fim', value: 'end' }
]

/** 任务监听器事件选项 */
export const BPM_PROCESS_LISTENER_TASK_EVENT_OPTIONS = [
  { label: 'Criar', value: 'create' },
  { label: 'Atribuir', value: 'assignment' },
  { label: 'Concluir', value: 'complete' },
  { label: 'Excluir', value: 'delete' },
  { label: 'Atualizar', value: 'update' },
  { label: 'Tempo esgotado', value: 'timeout' }
]
