import { Layout } from '@/utils/routerHelper'
import { telaoRoute } from './monitor-telao'

const { t } = useI18n()
/**
 * redirect: noredirect        当设置 noredirect 的时候该路由在面包屑导航中不可被点击
 * name:'router-name'          设定路由的名字，一定要填写不然使用<keep-alive>时会出现各种问题
 * meta : {
 hidden: true              当设置 true 的时候该路由不会再侧边栏出现 如404，login等页面(默认 false)

 alwaysShow: true          当你一个路由下面的 children 声明的路由大于1个时，自动会变成嵌套的模式，
 只有一个时，会将那个子路由当做根路由显示在侧边栏，
 若你想不管路由下面的 children 声明的个数都显示你的根路由，
 你可以设置 alwaysShow: true，这样它就会忽略之前定义的规则，
 一直显示根路由(默认 false)

 title: 'title'            设置该路由在侧边栏和面包屑中展示的名字

 icon: 'svg-name'          设置该路由的图标

 noCache: true             如果设置为true，则不会被 <keep-alive> 缓存(默认 false)

 breadcrumb: false         如果设置为false，则不会在breadcrumb面包屑中显示(默认 true)

 affix: true               如果设置为true，则会一直固定在tag项中(默认 false)

 noTagsView: true          如果设置为true，则不会出现在tag中(默认 false)

 activeMenu: '/dashboard'  显示高亮的路由路径

 followAuth: '/dashboard'  跟随哪个路由进行权限过滤

 canTo: true               设置为true即使hidden为true，也依然可以进行路由跳转(默认 false)
 }
 **/
const remainingRouter: AppRouteRecordRaw[] = [
  {
    path: '/redirect',
    component: Layout,
    name: 'RedirectRoot',
    children: [
      {
        path: '/redirect/:path(.*)',
        name: 'Redirect',
        component: () => import('@/views/Redirect/Redirect.vue'),
        meta: {}
      }
    ],
    meta: {
      hidden: true,
      noTagsView: true
    }
  },
  {
    path: '/',
    component: Layout,
    redirect: '/index',
    name: 'Home',
    meta: {},
    children: [
      {
        path: 'index',
        component: () => import('@/views/Home/Index.vue'),
        name: 'Index',
        meta: {
          title: t('router.home'),
          icon: 'ep:home-filled',
          noCache: false,
          affix: true
        }
      }
    ]
  },
  {
    path: '/user',
    component: Layout,
    name: 'UserInfo',
    meta: {
      hidden: true
    },
    children: [
      {
        path: 'profile',
        component: () => import('@/views/Profile/Index.vue'),
        name: 'Profile',
        meta: {
          canTo: true,
          hidden: true,
          noTagsView: false,
          icon: 'ep:user',
          title: t('common.profile')
        }
      },
      {
        path: 'notify-message',
        component: () => import('@/views/system/notify/my/index.vue'),
        name: 'MyNotifyMessage',
        meta: {
          canTo: true,
          hidden: true,
          noTagsView: false,
          icon: 'ep:message',
          title: 'Minhas mensagens'
        }
      }
    ]
  },
  {
    path: '/dict',
    component: Layout,
    name: 'dict',
    meta: {
      hidden: true
    },
    children: [
      {
        path: 'type/data/:dictType',
        component: () => import('@/views/system/dict/data/index.vue'),
        name: 'SystemDictData',
        meta: {
          title: 'Dados do dicionário',
          noCache: true,
          hidden: true,
          canTo: true,
          icon: '',
          activeMenu: '/system/dict'
        }
      }
    ]
  },
  {
    path: '/fms/auxiliary',
    component: Layout,
    name: 'FmsAuxiliaryRoot',
    meta: {
      hidden: true
    },
    children: [
      {
        path: 'type/item/:auxiliaryTypeId',
        redirect: (to) => ({
          path: '/fms/config/auxiliary',
          query: { auxiliaryTypeId: to.params.auxiliaryTypeId }
        }),
        name: 'FmsAuxiliaryItem',
        meta: {
          title: 'Itens de apuração auxiliar',
          noCache: true,
          hidden: true,
          canTo: true,
          icon: '',
          activeMenu: '/fms/config/auxiliary'
        }
      }
    ]
  },
  {
    path: '/codegen',
    component: Layout,
    name: 'CodegenEdit',
    meta: {
      hidden: true
    },
    children: [
      {
        path: 'edit',
        component: () => import('@/views/infra/codegen/EditTable.vue'),
        name: 'InfraCodegenEditTable',
        meta: {
          noCache: true,
          hidden: true,
          canTo: true,
          icon: 'ep:edit',
          title: 'Editar configuração de geração',
          activeMenu: 'infra/codegen/index'
        }
      }
    ]
  },
  {
    path: '/job',
    component: Layout,
    name: 'JobL',
    meta: {
      hidden: true
    },
    children: [
      {
        path: 'job-log',
        component: () => import('@/views/infra/job/logger/index.vue'),
        name: 'InfraJobLog',
        meta: {
          noCache: true,
          hidden: true,
          canTo: true,
          icon: 'ep:edit',
          title: 'Log de execuções',
          activeMenu: 'infra/job/index'
        }
      }
    ]
  },
  {
    path: '/login',
    component: () => import('@/views/Login/Login.vue'),
    name: 'Login',
    meta: {
      hidden: true,
      title: t('router.login'),
      noTagsView: true
    }
  },
  {
    path: '/pms/kb/document/share/:token',
    component: () => import('@/views/pms/kb/document/share/index.vue'),
    name: 'PmsKnowledgeDocumentShare',
    meta: {
      hidden: true,
      title: 'Compartilhar documento',
      noTagsView: true
    }
  },
  {
    path: '/sso',
    component: () => import('@/views/Login/Login.vue'),
    name: 'SSOLogin',
    meta: {
      hidden: true,
      title: t('router.login'),
      noTagsView: true
    }
  },
  {
    path: '/social-login',
    component: () => import('@/views/Login/SocialLogin.vue'),
    name: 'SocialLogin',
    meta: {
      hidden: true,
      title: t('router.socialLogin'),
      noTagsView: true
    }
  },
  {
    path: '/403',
    component: () => import('@/views/Error/403.vue'),
    name: 'NoAccess',
    meta: {
      hidden: true,
      title: '403',
      noTagsView: true
    }
  },
  {
    path: '/404',
    component: () => import('@/views/Error/404.vue'),
    name: 'NoFound',
    meta: {
      hidden: true,
      title: '404',
      noTagsView: true
    }
  },
  {
    path: '/500',
    component: () => import('@/views/Error/500.vue'),
    name: 'Error',
    meta: {
      hidden: true,
      title: '500',
      noTagsView: true
    }
  },
  {
    path: '/:pathMatch(.*)*',
    component: () => import('@/views/Error/404.vue'),
    name: '',
    meta: {
      title: '404',
      hidden: true,
      breadcrumb: false
    }
  },
  {
    path: '/bpm',
    component: Layout,
    name: 'bpm',
    meta: {
      hidden: true
    },
    children: [
      {
        path: 'manager/form/edit',
        component: () => import('@/views/bpm/form/editor/index.vue'),
        name: 'BpmFormEditor',
        meta: {
          noCache: true,
          hidden: true,
          canTo: true,
          title: 'Desenhar formulário do fluxo',
          activeMenu: '/bpm/manager/form'
        }
      },
      {
        path: 'manager/definition',
        component: () => import('@/views/bpm/model/definition/index.vue'),
        name: 'BpmProcessDefinition',
        meta: {
          noCache: true,
          hidden: true,
          canTo: true,
          title: 'Definição do fluxo',
          activeMenu: '/bpm/manager/model'
        }
      },
      {
        path: 'process-instance/detail',
        component: () => import('@/views/bpm/processInstance/detail/index.vue'),
        name: 'BpmProcessInstanceDetail',
        meta: {
          noCache: true,
          hidden: true,
          canTo: true,
          title: 'Detalhes do fluxo',
          activeMenu: '/bpm/task/my'
        },
        props: (route) => ({
          id: route.query.id,
          taskId: route.query.taskId,
          activityId: route.query.activityId
        })
      },
      {
        path: 'process-instance/report',
        component: () => import('@/views/bpm/processInstance/report/index.vue'),
        name: 'BpmProcessInstanceReport',
        meta: {
          noCache: true,
          hidden: true,
          canTo: true,
          title: 'Relatório',
          activeMenu: '/bpm/manager/model'
        }
      },
      {
        path: 'oa/leave/create',
        component: () => import('@/views/bpm/oa/leave/create.vue'),
        name: 'OALeaveCreate',
        meta: {
          noCache: true,
          hidden: true,
          canTo: true,
          title: 'Pedir licença',
          activeMenu: '/bpm/oa/leave'
        }
      },
      {
        path: 'oa/leave/detail',
        component: () => import('@/views/bpm/oa/leave/detail.vue'),
        name: 'OALeaveDetail',
        meta: {
          noCache: true,
          hidden: true,
          canTo: true,
          title: 'Ver licença',
          activeMenu: '/bpm/oa/leave'
        }
      },
      {
        path: 'manager/model/create',
        component: () => import('@/views/bpm/model/form/index.vue'),
        name: 'BpmModelCreate',
        meta: {
          noCache: true,
          hidden: true,
          canTo: true,
          title: 'Criar fluxo',
          activeMenu: '/bpm/manager/model'
        }
      },
      {
        path: 'manager/model/:type/:id',
        component: () => import('@/views/bpm/model/form/index.vue'),
        name: 'BpmModelUpdate',
        meta: {
          noCache: true,
          hidden: true,
          canTo: true,
          title: 'Editar fluxo',
          activeMenu: '/bpm/manager/model'
        }
      }
    ]
  },
  {
    path: '/mall/product', // 商品中心
    component: Layout,
    name: 'ProductCenter',
    meta: {
      hidden: true
    },
    children: [
      {
        path: 'spu/add',
        component: () => import('@/views/mall/product/spu/form/index.vue'),
        name: 'ProductSpuAdd',
        meta: {
          noCache: false, // 需要缓存
          hidden: true,
          canTo: true,
          icon: 'ep:edit',
          title: 'Novo produto',
          activeMenu: '/mall/product/spu'
        }
      },
      {
        path: 'spu/edit/:id(\\d+)',
        component: () => import('@/views/mall/product/spu/form/index.vue'),
        name: 'ProductSpuEdit',
        meta: {
          noCache: true,
          hidden: true,
          canTo: true,
          icon: 'ep:edit',
          title: 'Editar produto',
          activeMenu: '/mall/product/spu'
        }
      },
      {
        path: 'spu/detail/:id(\\d+)',
        component: () => import('@/views/mall/product/spu/form/index.vue'),
        name: 'ProductSpuDetail',
        meta: {
          noCache: true,
          hidden: true,
          canTo: true,
          icon: 'ep:view',
          title: 'Detalhes do produto',
          activeMenu: '/mall/product/spu'
        }
      },
      {
        path: 'property/value/:propertyId(\\d+)',
        component: () => import('@/views/mall/product/property/value/index.vue'),
        name: 'ProductPropertyValue',
        meta: {
          noCache: true,
          hidden: true,
          canTo: true,
          icon: 'ep:view',
          title: 'Valores do atributo',
          activeMenu: '/product/property'
        }
      }
    ]
  },
  {
    path: '/mall/trade', // 交易中心
    component: Layout,
    name: 'TradeCenter',
    meta: {
      hidden: true
    },
    children: [
      {
        path: 'order/detail/:id(\\d+)',
        component: () => import('@/views/mall/trade/order/detail/index.vue'),
        name: 'TradeOrderDetail',
        meta: { title: 'Detalhes do pedido', icon: 'ep:view', activeMenu: '/mall/trade/order' }
      },
      {
        path: 'after-sale/detail/:id(\\d+)',
        component: () => import('@/views/mall/trade/afterSale/detail/index.vue'),
        name: 'TradeAfterSaleDetail',
        meta: { title: 'Detalhes do reembolso', icon: 'ep:view', activeMenu: '/mall/trade/after-sale' }
      }
    ]
  },
  {
    path: '/member',
    component: Layout,
    name: 'MemberCenter',
    meta: { hidden: true },
    children: [
      {
        path: 'user/detail/:id',
        name: 'MemberUserDetail',
        meta: {
          title: 'Detalhes do membro',
          noCache: true,
          hidden: true
        },
        component: () => import('@/views/member/user/detail/index.vue')
      }
    ]
  },
  {
    path: '/pay',
    component: Layout,
    name: 'pay',
    meta: { hidden: true },
    children: [
      {
        path: 'cashier',
        name: 'PayCashier',
        meta: {
          title: 'Caixa',
          noCache: true,
          hidden: true
        },
        component: () => import('@/views/pay/cashier/index.vue')
      }
    ]
  },
  {
    path: '/diy',
    name: 'DiyCenter',
    meta: { hidden: true },
    component: Layout,
    children: [
      {
        path: 'template/decorate/:id',
        name: 'DiyTemplateDecorate',
        meta: {
          title: 'Editar modelo',
          noCache: false,
          hidden: true,
          activeMenu: '/mall/promotion/diy-template/diy-template'
        },
        component: () => import('@/views/mall/promotion/diy/template/decorate.vue')
      },
      {
        path: 'page/decorate/:id',
        name: 'DiyPageDecorate',
        meta: {
          title: 'Editar página',
          noCache: false,
          hidden: true,
          activeMenu: '/mall/promotion/diy-template/diy-page'
        },
        component: () => import('@/views/mall/promotion/diy/page/decorate.vue')
      }
    ]
  },
  {
    path: '/oa',
    component: Layout,
    name: 'OaDetail',
    meta: { hidden: true },
    children: [
      {
        path: 'reimbursement/detail/:id',
        name: 'OaReimbursementDetail',
        component: () => import('@/views/oa/reimbursement/detail/index.vue'),
        meta: {
          title: 'Reembolso de despesas',
          hidden: true,
          noCache: true,
          activeMenu: '/oa/process/reimbursement'
        }
      },
      {
        path: 'overtime/detail/:id',
        name: 'OaOvertimeApplyDetail',
        component: () => import('@/views/oa/overtime/detail/index.vue'),
        meta: { title: 'Pedido de hora extra', hidden: true, noCache: true, activeMenu: '/oa/process/overtime' }
      },
      {
        path: 'leave/detail/:id',
        name: 'OaLeaveApplyDetail',
        component: () => import('@/views/oa/leave/detail/index.vue'),
        meta: { title: 'Pedido de licença', hidden: true, noCache: true, activeMenu: '/oa/process/leave' }
      },
      {
        path: 'regular/detail/:id',
        name: 'OaRegularApplyDetail',
        component: () => import('@/views/oa/regular/detail/index.vue'),
        meta: { title: 'Pedido de efetivação', hidden: true, noCache: true, activeMenu: '/oa/process/regular' }
      },
      {
        path: 'resign/detail/:id',
        name: 'OaResignApplyDetail',
        component: () => import('@/views/oa/resign/detail/index.vue'),
        meta: { title: 'Pedido de desligamento', hidden: true, noCache: true, activeMenu: '/oa/process/resign' }
      },
      {
        path: 'discussion/detail/:id',
        name: 'OaDiscussionDetail',
        component: () => import('@/views/oa/discussion/detail/index.vue'),
        meta: {
          title: 'Detalhes da discussão',
          hidden: true,
          noCache: true,
          activeMenu: '/oa/discussion/list'
        }
      }
    ]
  },
  {
    path: '/pms',
    component: Layout,
    name: 'PmsCenter',
    meta: { hidden: true },
    children: [
      {
        path: 'pm/iteration/detail/:id(\\d+)',
        name: 'PmsIterationDetail',
        meta: {
          title: 'Detalhes da iteração',
          noCache: true,
          hidden: true,
          activeMenu: '/pms/pm/project/list'
        },
        component: () => import('@/views/pms/pm/iteration/detail/index.vue')
      },
      {
        path: 'pm/project/detail/:id(\\d+)',
        name: 'PmsProjectDetail',
        meta: {
          title: 'Detalhes do projeto',
          noCache: true,
          hidden: true,
          viewKey: 'PmsProjectDetail',
          activeMenu: '/pms/pm/project/list'
        },
        component: () => import('@/views/pms/pm/project/detail/index.vue')
      },
      {
        path: 'pm/project/config/:id(\\d+)',
        name: 'PmsProjectConfig',
        meta: {
          title: 'Configurações do projeto',
          noCache: true,
          hidden: true,
          activeMenu: '/pms/pm/project/list'
        },
        component: () => import('@/views/pms/pm/project/config/index.vue')
      },
      {
        path: 'kb/library/:libraryId(\\d+)',
        name: 'PmsKnowledgeLibraryDetail',
        meta: {
          title: 'Detalhes da base de conhecimento',
          noCache: true,
          hidden: true,
          canTo: true,
          viewKey: 'PmsKnowledgeDocument',
          activeMenu: '/pms/kb/library'
        },
        component: () => import('@/views/pms/kb/document/index.vue')
      },
      {
        path: 'kb/library/:libraryId(\\d+)/folder/:folderId(\\d+)',
        name: 'PmsKnowledgeFolderDetail',
        meta: {
          title: 'Detalhes da pasta',
          noCache: true,
          hidden: true,
          canTo: true,
          viewKey: 'PmsKnowledgeDocument',
          activeMenu: '/pms/kb/library'
        },
        component: () => import('@/views/pms/kb/document/index.vue')
      },
      {
        path: 'kb/library/:libraryId(\\d+)/document/:documentId(\\d+)',
        name: 'PmsKnowledgeDocumentDetail',
        meta: {
          title: 'Detalhes do documento',
          noCache: true,
          hidden: true,
          canTo: true,
          viewKey: 'PmsKnowledgeDocument',
          activeMenu: '/pms/kb/library'
        },
        component: () => import('@/views/pms/kb/document/index.vue')
      }
    ]
  },
  {
    path: '/crm',
    component: Layout,
    name: 'CrmCenter',
    meta: { hidden: true },
    children: [
      {
        path: 'clue/detail/:id',
        name: 'CrmClueDetail',
        meta: {
          title: 'Detalhes do lead',
          noCache: true,
          hidden: true,
          activeMenu: '/crm/clue'
        },
        component: () => import('@/views/crm/clue/detail/index.vue')
      },
      {
        path: 'customer/detail/:id',
        name: 'CrmCustomerDetail',
        meta: {
          title: 'Detalhes do cliente',
          noCache: true,
          hidden: true,
          activeMenu: '/crm/customer'
        },
        component: () => import('@/views/crm/customer/detail/index.vue')
      },
      {
        path: 'business/detail/:id',
        name: 'CrmBusinessDetail',
        meta: {
          title: 'Detalhes da oportunidade',
          noCache: true,
          hidden: true,
          activeMenu: '/crm/business'
        },
        component: () => import('@/views/crm/business/detail/index.vue')
      },
      {
        path: 'contract/detail/:id',
        name: 'CrmContractDetail',
        meta: {
          title: 'Detalhes do contrato',
          noCache: true,
          hidden: true,
          activeMenu: '/crm/contract'
        },
        component: () => import('@/views/crm/contract/detail/index.vue')
      },
      {
        path: 'receivable-plan/detail/:id',
        name: 'CrmReceivablePlanDetail',
        meta: {
          title: 'Detalhes do plano de recebimento',
          noCache: true,
          hidden: true,
          activeMenu: '/crm/receivable-plan'
        },
        component: () => import('@/views/crm/receivable/plan/detail/index.vue')
      },
      {
        path: 'receivable/detail/:id',
        name: 'CrmReceivableDetail',
        meta: {
          title: 'Detalhes do recebimento',
          noCache: true,
          hidden: true,
          activeMenu: '/crm/receivable'
        },
        component: () => import('@/views/crm/receivable/detail/index.vue')
      },
      {
        path: 'contact/detail/:id',
        name: 'CrmContactDetail',
        meta: {
          title: 'Detalhes do contato',
          noCache: true,
          hidden: true,
          activeMenu: '/crm/contact'
        },
        component: () => import('@/views/crm/contact/detail/index.vue')
      },
      {
        path: 'product/detail/:id',
        name: 'CrmProductDetail',
        meta: {
          title: 'Detalhes do produto',
          noCache: true,
          hidden: true,
          activeMenu: '/crm/product'
        },
        component: () => import('@/views/crm/product/detail/index.vue')
      }
    ]
  },
  {
    path: '/hrm',
    component: Layout,
    name: 'HrmCenter',
    meta: { hidden: true },
    children: [
      {
        path: 'portal/opening-guide',
        name: 'HrmPortalOpeningGuide',
        meta: {
          title: 'Guia de ativação do colaborador',
          noCache: true,
          hidden: true,
          activeMenu: '/hrm/portal/home'
        },
        component: () => import('@/views/hrm/portal/opening-guide/index.vue')
      },
      {
        path: 'recruit/post/detail/:id',
        name: 'HrmRecruitPostDetail',
        meta: {
          title: 'Detalhes da vaga',
          noCache: true,
          hidden: true,
          activeMenu: '/hrm/recruit/post'
        },
        component: () => import('@/views/hrm/recruit/post/detail/index.vue')
      },
      {
        path: 'recruit/candidate/detail/:id',
        name: 'HrmRecruitCandidateDetail',
        meta: {
          title: 'Detalhes do candidato',
          noCache: true,
          hidden: true,
          activeMenu: '/hrm/recruit/candidate'
        },
        component: () => import('@/views/hrm/recruit/candidate/detail/index.vue')
      },
      {
        path: 'employee/detail/:id',
        name: 'HrmEmployeeDetail',
        meta: {
          title: 'Ficha do colaborador',
          noCache: true,
          hidden: true,
          activeMenu: '/hrm/employee/list'
        },
        component: () => import('@/views/hrm/employee/detail/index.vue')
      },
      {
        path: 'dept/detail/:id',
        name: 'HrmDeptDetail',
        meta: {
          title: 'Detalhes da organização',
          noCache: true,
          hidden: true,
          activeMenu: '/hrm/dept'
        },
        component: () => import('@/views/hrm/dept/detail/index.vue')
      },
      {
        path: 'attendance/month/detail/:employeeId',
        name: 'HrmAttendanceMonthDetail',
        meta: {
          title: 'Ponto do mês',
          noCache: true,
          hidden: true,
          activeMenu: '/hrm/attendance/month'
        },
        component: () => import('@/views/hrm/attendance/month/detail/index.vue')
      },
      {
        path: 'performance/plan/detail/:id',
        name: 'HrmPerformancePlanDetail',
        meta: {
          title: 'Plano de desempenho',
          noCache: true,
          hidden: true,
          activeMenu: '/hrm/performance/plan'
        },
        component: () => import('@/views/hrm/performance/plan/detail/index.vue')
      },
      {
        path: 'performance/plan/form',
        name: 'HrmPerformancePlanForm',
        meta: {
          title: 'Configuração de KPI',
          noCache: true,
          hidden: true,
          canTo: true,
          activeMenu: '/hrm/performance/plan'
        },
        component: () => import('@/views/hrm/performance/plan/form/index.vue')
      },
      {
        path: 'performance/assessment/employee/:employeeId',
        name: 'HrmPerformanceAssessmentEmployee',
        meta: {
          title: 'Histórico de desempenho',
          noCache: true,
          hidden: true,
          activeMenu: '/hrm/performance/assessment'
        },
        component: () => import('@/views/hrm/performance/assessment/employee/index.vue')
      },
      {
        path: 'performance/assessment/detail/:id',
        name: 'HrmPerformanceAssessmentDetail',
        meta: {
          title: 'Detalhes da avaliação',
          noCache: true,
          hidden: true,
          activeMenu: '/hrm/performance/assessment'
        },
        component: () => import('@/views/hrm/performance/assessment/detail/index.vue')
      },
      {
        path: 'insurance/month-record/detail/:id',
        name: 'HrmInsuranceMonthRecordDetail',
        meta: {
          title: 'Encargos do mês',
          noCache: true,
          hidden: true,
          activeMenu: '/hrm/insurance/month-record'
        },
        component: () => import('@/views/hrm/insurance/month-record/detail/index.vue')
      },
      {
        path: 'salary/employee-info/detail/:id',
        name: 'HrmSalaryEmployeeInfoDetail',
        meta: {
          title: 'Ficha salarial',
          noCache: true,
          hidden: true,
          activeMenu: '/hrm/salary/employee-info'
        },
        component: () => import('@/views/hrm/salary/employee-info/detail/index.vue')
      },
      {
        path: 'salary/history/detail/:id',
        name: 'HrmSalaryHistoryDetail',
        meta: {
          title: 'Histórico salarial',
          noCache: true,
          hidden: true,
          activeMenu: '/hrm/salary/history'
        },
        component: () => import('@/views/hrm/salary/month-record/detail/index.vue')
      },
      {
        path: 'salary/slip/detail/:id',
        name: 'HrmSalarySlipSendRecordDetail',
        meta: {
          title: 'Envio de holerites',
          noCache: true,
          hidden: true,
          activeMenu: '/hrm/salary/slip'
        },
        component: () => import('@/views/hrm/salary/slip/send-record/detail/index.vue')
      }
    ]
  },
  {
    path: '/ai',
    component: Layout,
    name: 'Ai',
    meta: {
      hidden: true
    },
    children: [
      {
        path: 'image/square',
        component: () => import('@/views/ai/image/square/index.vue'),
        name: 'AiImageSquare',
        meta: {
          title: 'Imagens geradas',
          icon: 'ep:home-filled',
          noCache: false
        }
      },
      {
        path: 'knowledge/document',
        component: () => import('@/views/ai/knowledge/document/index.vue'),
        name: 'AiKnowledgeDocument',
        meta: {
          title: 'Documentos da base de conhecimento',
          icon: 'ep:document',
          noCache: false,
          activeMenu: '/ai/knowledge'
        }
      },
      {
        path: 'knowledge/document/create',
        component: () => import('@/views/ai/knowledge/document/form/index.vue'),
        name: 'AiKnowledgeDocumentCreate',
        meta: {
          title: 'Criar documento',
          icon: 'ep:plus',
          noCache: true,
          hidden: true,
          activeMenu: '/ai/knowledge'
        }
      },
      {
        path: 'knowledge/document/update',
        component: () => import('@/views/ai/knowledge/document/form/index.vue'),
        name: 'AiKnowledgeDocumentUpdate',
        meta: {
          title: 'Editar documento',
          icon: 'ep:edit',
          noCache: true,
          hidden: true,
          activeMenu: '/ai/knowledge'
        }
      },
      {
        path: 'knowledge/retrieval',
        component: () => import('@/views/ai/knowledge/knowledge/retrieval/index.vue'),
        name: 'AiKnowledgeRetrieval',
        meta: {
          title: 'Teste de busca nos documentos',
          icon: 'ep:search',
          noCache: true,
          hidden: true,
          activeMenu: '/ai/knowledge'
        }
      },
      {
        path: 'knowledge/segment',
        component: () => import('@/views/ai/knowledge/segment/index.vue'),
        name: 'AiKnowledgeSegment',
        meta: {
          title: 'Trechos da base de conhecimento',
          icon: 'ep:tickets',
          noCache: true,
          hidden: true,
          activeMenu: '/ai/knowledge'
        }
      },
      {
        path: 'console/workflow/create',
        component: () => import('@/views/ai/workflow/form/index.vue'),
        name: 'AiWorkflowCreate',
        meta: {
          noCache: true,
          hidden: true,
          canTo: true,
          title: 'Desenhar fluxo de IA',
          activeMenu: '/ai/console/workflow'
        }
      },
      {
        path: 'console/workflow/:type/:id',
        component: () => import('@/views/ai/workflow/form/index.vue'),
        name: 'AiWorkflowUpdate',
        meta: {
          noCache: true,
          hidden: true,
          canTo: true,
          title: 'Desenhar fluxo de IA',
          activeMenu: '/ai/console/workflow'
        }
      }
    ]
  },
  {
    path: '/ai1',
    component: Layout,
    name: 'Ai1Center',
    meta: {
      hidden: true
    },
    children: [
      {
        path: 'provider/model/:providerId',
        component: () => import('@/views/ai1/model/index.vue'),
        name: 'Ai1ProviderModel',
        meta: {
          title: 'Modelos',
          noCache: true,
          hidden: true,
          activeMenu: '/ai1/provider'
        }
      },
      {
        path: 'knowledge/document/:knowledgeBaseId',
        component: () => import('@/views/ai1/knowledge/document/index.vue'),
        name: 'Ai1KnowledgeDocument',
        meta: {
          title: 'Documentos da base de conhecimento',
          noCache: true,
          hidden: true,
          activeMenu: '/ai1/knowledge'
        }
      },
      {
        path: 'skill/file/:skillId',
        component: () => import('@/views/ai1/skill/file/index.vue'),
        name: 'Ai1SkillFile',
        meta: {
          title: 'Conteúdo da skill',
          noCache: true,
          hidden: true,
          activeMenu: '/ai1/skill'
        }
      },
      {
        path: 'agent/session/:agentId',
        component: () => import('@/views/ai1/agent/session/index.vue'),
        name: 'Ai1AgentSession',
        meta: {
          title: 'Histórico de conversas',
          noCache: true,
          hidden: true,
          activeMenu: '/ai1/agent'
        }
      }
    ]
  },
  {
    path: '/iot',
    component: Layout,
    name: 'IOT',
    meta: {
      hidden: true
    },
    children: [
      {
        path: 'product/product/detail/:id',
        name: 'IoTProductDetail',
        meta: {
          title: 'Detalhes do produto',
          noCache: true,
          hidden: true,
          activeMenu: '/iot/device/product'
        },
        component: () => import('@/views/iot/product/product/detail/index.vue')
      },
      {
        path: 'device/detail/:id',
        name: 'IoTDeviceDetail',
        meta: {
          title: 'Detalhes do dispositivo',
          noCache: true,
          hidden: true,
          activeMenu: '/iot/device/device'
        },
        component: () => import('@/views/iot/device/device/detail/index.vue')
      },
      {
        path: 'ota/operation/firmware/detail/:id',
        name: 'IoTOtaFirmwareDetail',
        meta: {
          title: 'Detalhes do firmware',
          noCache: true,
          hidden: true,
          activeMenu: '/iot/operation/ota/firmware'
        },
        component: () => import('@/views/iot/ota/firmware/detail/index.vue')
      }
    ]
  },
  {
    path: '/mes',
    component: Layout,
    name: 'MesWmRouter',
    meta: {
      hidden: true
    },
    children: [
      {
        path: 'wm/warehouse/location',
        component: () => import('@/views/mes/wm/warehouse/location/index.vue'),
        name: 'MesWmLocation',
        meta: {
          noCache: true,
          hidden: true,
          canTo: true,
          icon: '',
          title: 'Configurar área do estoque',
          activeMenu: '/mes/wm/warehouse'
        }
      },
      {
        path: 'wm/warehouse/area',
        component: () => import('@/views/mes/wm/warehouse/area/index.vue'),
        name: 'MesWmArea',
        meta: {
          noCache: true,
          hidden: true,
          canTo: true,
          icon: '',
          title: 'Configurar posição do estoque',
          activeMenu: '/mes/wm/warehouse'
        }
      },
      {
        path: 'pro/task/gantt-edit',
        component: () => import('@/views/mes/pro/task/edit/index.vue'),
        name: 'MesProTaskGanttEdit',
        meta: {
          noCache: true,
          hidden: true,
          canTo: true,
          icon: '',
          title: 'Editar gráfico de Gantt',
          activeMenu: '/mes/pro/task'
        }
      }
    ]
  },
  {
    path: '/im',
    name: 'Im',
    redirect: '/im/home/conversation',
    meta: { hidden: true, title: 'Chat' },
    children: [
      {
        path: 'home',
        component: () => import('@/views/im/home/index.vue'),
        name: 'ImHome',
        redirect: '/im/home/conversation',
        meta: { hidden: true, title: 'Chat' },
        children: [
          {
            path: 'conversation',
            component: () => import('@/views/im/home/pages/conversation/index.vue'),
            name: 'ImHomeConversation',
            meta: { hidden: true, title: 'Mensagens' }
          },
          {
            path: 'contact',
            component: () => import('@/views/im/home/pages/contact/index.vue'),
            name: 'ImHomeContact',
            meta: { hidden: true, title: 'Contatos' }
          }
        ]
      }
    ]
  },
  telaoRoute
]

export default remainingRouter
