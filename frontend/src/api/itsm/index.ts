import request from '@/config/axios'

// ========== tipos ==========

export interface Ator {
  id: number
  tipo: number // 1 solicitante, 2 técnico, 3 observador
  userId?: number
  equipeId?: number
  email?: string
  nome: string
}

export interface Chamado {
  id: number
  titulo: string
  descricao?: string
  tipo: number
  tipoRotulo: string
  status: number
  statusRotulo: string
  urgencia: number
  impacto: number
  prioridade: number
  prioridadeRotulo: string
  categoriaId?: number
  categoria?: string
  origemId?: number
  origem?: string
  deptId?: number
  dept?: string
  abertoEm: number
  assumidoEm?: number
  solucionadoEm?: number
  fechadoEm?: number
  prazoAtendimento?: number
  prazoSolucao?: number
  slaVencido: boolean
  esperaSeg?: number
  tempoAssumirSeg?: number
  tempoSolucaoSeg?: number
  tempoGastoSeg?: number
  origemAutomatica?: string
  slaAtendimentoId?: number
  slaAtendimento?: string
  slaSolucaoId?: number
  slaSolucao?: string
  atendimentoVencido?: boolean
  satisfacaoPendente?: boolean
  satisfacaoNota?: number
  satisfacaoComentario?: string
  solicitantes: Ator[]
  tecnicos: Ator[]
  observadores: Ator[]
  acoes: string[]
}

export interface Interacao {
  id: number
  tipo: 'acompanhamento' | 'tarefa' | 'solucao'
  userId?: number
  autor?: string
  conteudo: string
  privado: boolean
  criadoEm: number
  estadoTarefa?: number
  tecnicoId?: number
  tecnico?: string
  inicio?: number
  fim?: number
  tempoGastoSeg?: number
  statusSolucao?: number // 2 aguardando, 3 aprovada, 4 recusada
  avaliadoPorNome?: string
  avaliadoEm?: number
}

export interface Historico {
  id: number
  momento: number
  usuario?: string
  campo: string
  valorAntigo?: string
  valorNovo?: string
}

export interface Categoria {
  id: number
  parentId: number
  nome: string
  nomeCompleto: string
  nivel: number
  codigo?: string
  descricao?: string
  tecnicoId?: number
  equipeId?: number
  slaAtendimentoId?: number
  slaSolucaoId?: number
  visivelPortal: boolean
  paraIncidente: boolean
  paraRequisicao: boolean
  ativo: boolean
  ordem: number
}

export interface Membro {
  userId: number
  supervisor: boolean
  nome?: string
}

export interface Equipe {
  id?: number
  nome: string
  descricao?: string
  email?: string
  grupoAd?: string
  ativo: boolean
  membros: Membro[]
}

export interface Origem {
  id?: number
  nome: string
  padraoPortal: boolean
  padraoEmail: boolean
  ativo: boolean
}

// ========== rótulos ==========

export const STATUS = [
  { valor: 1, rotulo: 'Novo', cor: 'primary' },
  { valor: 2, rotulo: 'Em atendimento', cor: 'success' },
  { valor: 3, rotulo: 'Em atendimento (planejado)', cor: 'success' },
  { valor: 4, rotulo: 'Pendente', cor: 'warning' },
  { valor: 5, rotulo: 'Solucionado', cor: 'info' },
  { valor: 6, rotulo: 'Fechado', cor: 'info' }
] as const

export const ESCALA = [
  { valor: 1, rotulo: 'Muito baixa' },
  { valor: 2, rotulo: 'Baixa' },
  { valor: 3, rotulo: 'Média' },
  { valor: 4, rotulo: 'Alta' },
  { valor: 5, rotulo: 'Muito alta' }
]

export const PRIORIDADES = [...ESCALA, { valor: 6, rotulo: 'Crítica' }]

export const corStatus = (s: number) => STATUS.find((x) => x.valor === s)?.cor || 'info'
export const corPrioridade = (p: number) =>
  p >= 6 ? 'danger' : p >= 5 ? 'danger' : p >= 4 ? 'warning' : p >= 3 ? 'primary' : 'info'

/** 3h 25min, 2 dias e 4h... */
export const duracao = (seg?: number | null) => {
  if (seg === undefined || seg === null) return '—'
  if (seg < 60) return `${seg} s`
  const min = Math.floor(seg / 60)
  if (min < 60) return `${min} min`
  const h = Math.floor(min / 60)
  if (h < 24) return `${h} h ${min % 60} min`
  const d = Math.floor(h / 24)
  return `${d} ${d === 1 ? 'dia' : 'dias'} e ${h % 24} h`
}

// ========== equipe técnica ==========

export const getChamadoPage = (params: any) => request.get({ url: '/itsm/chamado/page', params })
export const getContagens = (): Promise<Record<string, number>> => request.get({ url: '/itsm/chamado/contagens' })
export const getChamado = (id: number): Promise<Chamado> => request.get({ url: '/itsm/chamado/get', params: { id } })
export const getLinhaDoTempo = (id: number): Promise<Interacao[]> =>
  request.get({ url: '/itsm/chamado/linha-do-tempo', params: { id } })
export const getHistorico = (id: number): Promise<Historico[]> =>
  request.get({ url: '/itsm/chamado/historico', params: { id } })
export const criarChamado = (data: any): Promise<number> => request.post({ url: '/itsm/chamado/create', data })
export const atualizarChamado = (data: any) => request.put({ url: '/itsm/chamado/update', data })
export const excluirChamado = (id: number) => request.delete({ url: '/itsm/chamado/delete', params: { id } })
export const adicionarAtor = (data: { chamadoId: number; tipo: number; userId?: number; equipeId?: number }) =>
  request.post({ url: '/itsm/chamado/ator', data })
export const removerAtor = (id: number) => request.delete({ url: '/itsm/chamado/ator', params: { id } })
export const assumir = (id: number) => request.post({ url: '/itsm/chamado/assumir', params: { id } })
export const adicionarInteracao = (data: any) => request.post({ url: '/itsm/chamado/interacao', data })
export const colocarPendente = (chamadoId: number, motivo?: string) =>
  request.post({ url: '/itsm/chamado/pendente', data: { chamadoId, motivo } })
export const retomar = (id: number) => request.post({ url: '/itsm/chamado/retomar', params: { id } })
export const fechar = (id: number) => request.post({ url: '/itsm/chamado/fechar', params: { id } })
export const reabrir = (id: number) => request.post({ url: '/itsm/chamado/reabrir', params: { id } })
export const avaliarSolucaoTecnico = (data: { chamadoId: number; aprovar: boolean; comentario?: string }) =>
  request.post({ url: '/itsm/chamado/avaliar-solucao', data })

// ========== autoatendimento ==========

export const getCategoriasPortal = (): Promise<Categoria[]> => request.get({ url: '/itsm/portal/categorias' })
export const abrirPortal = (data: any): Promise<number> => request.post({ url: '/itsm/portal/abrir', data })
export const getMeus = (params: any) => request.get({ url: '/itsm/portal/meus', params })
export const getMeu = (id: number): Promise<Chamado> => request.get({ url: '/itsm/portal/get', params: { id } })
export const getMinhaLinhaDoTempo = (id: number): Promise<Interacao[]> =>
  request.get({ url: '/itsm/portal/linha-do-tempo', params: { id } })
export const responderPortal = (chamadoId: number, conteudo: string) =>
  request.post({ url: '/itsm/portal/acompanhamento', data: { chamadoId, tipo: 'acompanhamento', conteudo } })
export const avaliarSolucaoPortal = (data: { chamadoId: number; aprovar: boolean; comentario?: string }) =>
  request.post({ url: '/itsm/portal/avaliar-solucao', data })

// ========== cadastros ==========

export const getCategorias = (somenteAtivas = false): Promise<Categoria[]> =>
  request.get({ url: '/itsm/categoria/list', params: { somenteAtivas } })
export const salvarCategoria = (data: any): Promise<number> => request.post({ url: '/itsm/categoria/save', data })
export const excluirCategoria = (id: number) => request.delete({ url: '/itsm/categoria/delete', params: { id } })
export const getEquipes = (somenteAtivas = false): Promise<Equipe[]> =>
  request.get({ url: '/itsm/equipe/list', params: { somenteAtivas } })
export const salvarEquipe = (data: Equipe): Promise<number> => request.post({ url: '/itsm/equipe/save', data })
export const excluirEquipe = (id: number) => request.delete({ url: '/itsm/equipe/delete', params: { id } })
export const getOrigens = (somenteAtivas = false): Promise<Origem[]> =>
  request.get({ url: '/itsm/origem/list', params: { somenteAtivas } })
export const salvarOrigem = (data: Origem): Promise<number> => request.post({ url: '/itsm/origem/save', data })
export const excluirOrigem = (id: number) => request.delete({ url: '/itsm/origem/delete', params: { id } })

// ========== SLA, calendários e feriados ==========

export interface Periodo {
  diaSemana: number // 1 segunda ... 7 domingo
  inicio: string // HH:mm
  fim: string // HH:mm ou 24:00
}

export interface Calendario {
  id?: number
  nome: string
  descricao?: string
  periodos: Periodo[]
  feriadoIds: number[]
}

export interface Feriado {
  id?: number
  nome: string
  dataInicio: string
  dataFim?: string
  perpetuo: boolean
}

export interface NivelSla {
  id?: number
  nome: string
  minutos: number // em relação ao prazo; negativo = antes de vencer
  atribuirEquipeId?: number
  atribuirTecnicoId?: number
  novaPrioridade?: number
  ativo: boolean
}

export interface Sla {
  id?: number
  nome: string
  tipo: number // 1 atendimento, 0 solução
  tempo: number
  unidade: 'minuto' | 'hora' | 'dia' | 'mes'
  calendarioId?: number
  fimExpediente: boolean
  descricao?: string
  ativo: boolean
  niveis: NivelSla[]
}

export interface SlaPorPrioridade {
  prioridade: number
  slaAtendimentoId?: number
  slaSolucaoId?: number
}

export const UNIDADES = [
  { valor: 'minuto', rotulo: 'minutos' },
  { valor: 'hora', rotulo: 'horas' },
  { valor: 'dia', rotulo: 'dias' },
  { valor: 'mes', rotulo: 'meses' }
]
export const DIAS = ['', 'Segunda', 'Terça', 'Quarta', 'Quinta', 'Sexta', 'Sábado', 'Domingo']

export const getSlas = (): Promise<Sla[]> => request.get({ url: '/itsm/sla/list' })
export const salvarSla = (data: Sla): Promise<number> => request.post({ url: '/itsm/sla/save', data })
export const excluirSla = (id: number) => request.delete({ url: '/itsm/sla/delete', params: { id } })
export const getSlaPorPrioridade = (): Promise<SlaPorPrioridade[]> => request.get({ url: '/itsm/sla/prioridade' })
export const salvarSlaPorPrioridade = (data: SlaPorPrioridade[]) => request.put({ url: '/itsm/sla/prioridade', data })
export const executarAutomacao = (): Promise<{ escalonamentos: number; fechados: number }> =>
  request.post({ url: '/itsm/sla/executar-automacao' })
export const getCalendarios = (): Promise<Calendario[]> => request.get({ url: '/itsm/calendario/list' })
export const salvarCalendario = (data: Calendario): Promise<number> => request.post({ url: '/itsm/calendario/save', data })
export const excluirCalendario = (id: number) => request.delete({ url: '/itsm/calendario/delete', params: { id } })
export const getFeriados = (): Promise<Feriado[]> => request.get({ url: '/itsm/feriado/list' })
export const salvarFeriado = (data: Feriado): Promise<number> => request.post({ url: '/itsm/feriado/save', data })
export const excluirFeriado = (id: number) => request.delete({ url: '/itsm/feriado/delete', params: { id } })
export const responderSatisfacao = (data: { chamadoId: number; nota: number; comentario?: string }) =>
  request.post({ url: '/itsm/portal/satisfacao', data })
