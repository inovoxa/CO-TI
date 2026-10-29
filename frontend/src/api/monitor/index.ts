import request from '@/config/axios'

// =================== tipos (espelham os VOs do backend) ===================
export type Status = 'ok' | 'atencao' | 'critico'

export interface UnitKpi {
  unitId: number
  link: 'online' | 'degradado' | 'offline'
  latenciaMs: number
  disponibilidade: number
  servicosNoAr: number
  servicosTotal: number
  chamadosAbertos: number
  slaVencido: number
  status: Status
  atualizadoEm: number
}

export interface Ocorrencia {
  unitId: number
  nivel: 'info' | 'atencao' | 'critico'
  categoria: 'rede' | 'ativos' | 'atendimento'
  texto: string
  momento: number
}

export interface DeptNode {
  id: number
  parentId: number
  name: string
  sort: number
}

export interface MonitorUnit {
  id: number
  deptId: number
  name: string
  city: string
  latitude: number
  longitude: number
  kpi?: UnitKpi
}

export interface MonitorOverview {
  depts: DeptNode[]
  units: MonitorUnit[]
  ocorrencias: Ocorrencia[]
}

/** Lista com contagens por filtro (chips) e total antes do corte de 200 linhas */
export interface Lista<T> {
  contagens: Record<string, number>
  lista: T[]
  total: number
  resumo: Record<string, any>
}

export interface Chamado {
  id: number
  titulo: string
  categoria: string
  prioridade: 'Crítica' | 'Alta' | 'Média' | 'Baixa'
  status: 'Novo' | 'Em atendimento' | 'Pendente' | 'Solucionado'
  unidadeId: number
  unidade: string
  solicitante: string
  tecnico?: string
  abertoEm: number
  prazo: number
  solucionadoEm?: number
  origem: string
}

export interface ChamadoNovo {
  titulo: string
  categoria: string
  prioridade: string
  unidadeId?: number
}

export interface Link {
  id: number
  nome: string
  tipo: string
  alvo: string
  unidadeId: number
  unidade: string
  papel: 'principal' | 'redundante' | 'servico'
  estado: 'up' | 'lento' | 'down'
  latenciaMs?: number | null
  historico: Array<'u' | 'p' | 'd'>
  uptime24h: number
  uptime30d: number
  certificadoDias?: number | null
}

export interface Equipamento {
  hostname: string
  tipo: 'Desktop' | 'Notebook' | 'Servidor'
  unidadeId: number
  unidade: string
  usuario: string
  so: string
  cpu: string
  ramGb: number
  discoGb: number
  discoLivrePct: number
  antivirus: 'Atualizado' | 'Desatualizado' | 'Ausente'
  ultimaComunicacao: number
  ip: string
}

export interface Camera {
  id: string
  nome: string
  unidadeId: number
  unidade: string
  snapshotUrl: string
  online: boolean
}

export interface CameraEvento {
  ts: number
  camera: string
  texto: string
  alerta: boolean
  unidadeId: number
}

export interface CameraResumo {
  veiculosEntraram: number
  veiculosSairam: number
  noEstacionamento: number
  vagas: number
  funcionarios: number
  visitantes: number
  alertas: number
  cameras: Camera[]
  eventos: CameraEvento[]
}

// =================== chamadas ===================
type P = Record<string, string | number | undefined>
const limpo = (p: P) => Object.fromEntries(Object.entries(p).filter(([, v]) => v !== undefined && v !== ''))

/** Visão geral: setores, unidades, indicadores atuais e ocorrências recentes */
export const getOverview = (): Promise<MonitorOverview> =>
  request.get({ url: '/system/monitor-screen/overview' })

export const listarChamados = (p: { unidadeId?: number; status?: string; busca?: string }): Promise<Lista<Chamado>> =>
  request.get({ url: '/system/monitor/chamados', params: limpo(p) })

export const abrirChamado = (data: ChamadoNovo): Promise<Chamado> =>
  request.post({ url: '/system/monitor/chamados', data })

export const listarLinks = (p: { unidadeId?: number; filtro?: string }): Promise<Lista<Link>> =>
  request.get({ url: '/system/monitor/links', params: limpo(p) })

export const listarEquipamentos = (p: { unidadeId?: number; filtro?: string; busca?: string }): Promise<Lista<Equipamento>> =>
  request.get({ url: '/system/monitor/inventario', params: limpo(p) })

export const resumoCameras = (p: { unidadeId?: number }): Promise<CameraResumo> =>
  request.get({ url: '/system/monitor/cameras', params: limpo(p) })

export const getConfig = (): Promise<{ element: string; grafana: string }> =>
  request.get({ url: '/system/monitor/config' })
