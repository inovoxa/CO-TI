import request from '@/config/axios'

// ========== tipos ==========

export interface Resumo {
  id: number
  nome: string
}

export interface Fornecedor {
  id?: number
  nome: string
  tipo?: string
  documento?: string
  email?: string
  telefone?: string
  site?: string
  contato?: string
  endereco?: string
  cidade?: string
  uf?: string
  cep?: string
  observacao?: string
  ativo: boolean
  totalContratos?: number
  totalLicencas?: number
}

export interface Contrato {
  id?: number
  nome: string
  numero?: string
  tipo?: string
  fornecedorId?: number
  deptId?: number
  responsavelId?: number
  inicio: string
  duracaoMeses: number
  avisoPrevioMeses: number
  renovacao: number // 0 não renova, 1 tácita, 2 expressa
  alertaDias: number
  numeroContabil?: string
  observacao?: string
  encerrado: boolean
  fornecedor?: string
  dept?: string
  responsavel?: string
  renovacaoRotulo?: string
  fim?: string
  prazoAviso?: string
  prazo?: string
  dataAlerta?: string
  situacao?: string
  situacaoRotulo?: string
  periodo?: number
  custoTotal?: number
  totalLicencas?: number
  renovacoes?: Renovacao[]
}

export interface Renovacao {
  id: number
  meses: number
  fimAntes: string
  fimDepois: string
  observacao?: string
  quem?: string
  quando: number
}

export interface Custo {
  id?: number
  objeto: 'contrato' | 'projeto' | 'licenca'
  objetoId: number
  nome: string
  inicio: string
  fim?: string
  valor: number
  orcamentoId?: number
  observacao?: string
  orcamento?: string
  origem?: string
}

export interface Uso {
  id?: number
  licencaId: number
  userId?: number
  equipamento?: string
  desde?: string
  observacao?: string
  nome?: string
}

export interface Licenca {
  id?: number
  software: string
  fabricante?: string
  nome?: string
  tipo?: string
  quantidade: number
  permiteExcesso: boolean
  chave?: string
  removerChave?: boolean
  expira?: string
  alertaDias: number
  fornecedorId?: number
  contratoId?: number
  deptId?: number
  responsavelId?: number
  observacao?: string
  ativo: boolean
  chaveDefinida?: boolean
  fornecedor?: string
  contrato?: string
  dept?: string
  responsavel?: string
  emUso?: number
  disponivel?: number
  excedida?: boolean
  situacao?: string
  situacaoRotulo?: string
  usos?: Uso[]
}

export interface Orcamento {
  id?: number
  nome: string
  tipo?: string
  deptId?: number
  inicio: string
  fim: string
  valor: number
  observacao?: string
  dept?: string
  gasto?: number
  saldo?: number
  percentualGasto?: number
  custos?: Custo[]
}

export interface Tarefa {
  id?: number
  projetoId: number
  tarefaPaiId?: number
  nome: string
  descricao?: string
  responsavelId?: number
  situacao: number // 1 a fazer, 2 em andamento, 3 concluída, 4 cancelada
  inicioPrevisto?: string
  fimPrevisto?: string
  inicioReal?: string
  fimReal?: string
  horasPrevistas: number
  horasGastas: number
  percentual: number
  marco: boolean
  ordem: number
  responsavel?: string
  situacaoRotulo?: string
  atrasada?: boolean
  podeEditar?: boolean
}

export interface VinculoProjeto {
  id: number
  projetoId: number
  objeto: 'chamado' | 'problema' | 'mudanca'
  objetoId: number
  titulo?: string
  statusRotulo?: string
}

export interface Projeto {
  id?: number
  nome: string
  codigo?: string
  descricao?: string
  situacao: number // 1 planejado, 2 em andamento, 3 pausado, 4 concluído, 5 cancelado
  prioridade: number
  deptId?: number
  responsavelId?: number
  inicioPrevisto?: string
  fimPrevisto?: string
  inicioReal?: string
  fimReal?: string
  percentual: number
  percentualAuto: boolean
  orcamentoId?: number
  membroIds: number[]
  situacaoRotulo?: string
  responsavel?: string
  dept?: string
  orcamento?: string
  atrasado?: boolean
  totalTarefas?: number
  tarefasConcluidas?: number
  horasPrevistas?: number
  horasGastas?: number
  custoTotal?: number
  membros?: { userId: number; nome: string; papel?: string }[]
  tarefas?: Tarefa[]
  vinculos?: VinculoProjeto[]
  acoes?: string[]
}

// ========== rótulos ==========

export const SITUACOES_CONTRATO = [
  { valor: 'a_vencer', rotulo: 'Perto do prazo', cor: 'warning' },
  { valor: 'vencido', rotulo: 'Vencido', cor: 'danger' },
  { valor: 'vigente', rotulo: 'Vigente', cor: 'success' },
  { valor: 'futuro', rotulo: 'Ainda não começou', cor: 'info' },
  { valor: 'encerrado', rotulo: 'Encerrado', cor: 'info' }
]
export const SITUACOES_LICENCA = [
  { valor: 'vigente', rotulo: 'Vigente', cor: 'success' },
  { valor: 'a_vencer', rotulo: 'Perto de expirar', cor: 'warning' },
  { valor: 'vencida', rotulo: 'Expirada', cor: 'danger' },
  { valor: 'inativa', rotulo: 'Inativa', cor: 'info' },
  { valor: 'excedida', rotulo: 'Em uso além do comprado', cor: 'danger' }
]
export const RENOVACOES = [
  { valor: 0, rotulo: 'Não renova' },
  { valor: 1, rotulo: 'Tácita (renova sozinho)' },
  { valor: 2, rotulo: 'Expressa (precisa renovar)' }
]
export const SITUACOES_PROJETO = [
  { valor: 1, rotulo: 'Planejado', cor: 'info' },
  { valor: 2, rotulo: 'Em andamento', cor: 'primary' },
  { valor: 3, rotulo: 'Pausado', cor: 'warning' },
  { valor: 4, rotulo: 'Concluído', cor: 'success' },
  { valor: 5, rotulo: 'Cancelado', cor: 'info' }
]
export const SITUACOES_TAREFA = [
  { valor: 1, rotulo: 'A fazer', cor: 'info' },
  { valor: 2, rotulo: 'Em andamento', cor: 'primary' },
  { valor: 3, rotulo: 'Concluída', cor: 'success' },
  { valor: 4, rotulo: 'Cancelada', cor: 'info' }
]
export const cor = (lista: { valor: any; cor: string }[], v: any) =>
  (lista.find((x) => x.valor === v)?.cor || 'info') as any

const MOEDA = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })
export const reais = (v?: number | null) => (v === undefined || v === null ? '—' : MOEDA.format(v))
/** 2026-10-24 → 24/10/2026 */
export const data = (d?: string | null) => (d ? d.split('-').reverse().join('/') : '—')

// ========== chamadas ==========

export const getFornecedores = (params: any) =>
  request.get({ url: '/itsm/fornecedor/page', params })
export const getFornecedoresSimples = (): Promise<Resumo[]> =>
  request.get({ url: '/itsm/fornecedor/simples' })
export const salvarFornecedor = (data: Fornecedor): Promise<number> =>
  request.post({ url: '/itsm/fornecedor/save', data })
export const excluirFornecedor = (id: number) =>
  request.delete({ url: '/itsm/fornecedor/delete', params: { id } })

export const getContratos = (params: any) => request.get({ url: '/itsm/contrato/page', params })
export const getContratosSimples = (): Promise<Resumo[]> =>
  request.get({ url: '/itsm/contrato/simples' })
export const getContrato = (id: number): Promise<Contrato> =>
  request.get({ url: '/itsm/contrato/get', params: { id } })
export const salvarContrato = (data: Contrato): Promise<number> =>
  request.post({ url: '/itsm/contrato/save', data })
export const renovarContrato = (data: { contratoId: number; meses: number; observacao?: string }) =>
  request.post({ url: '/itsm/contrato/renovar', data })
export const excluirContrato = (id: number) =>
  request.delete({ url: '/itsm/contrato/delete', params: { id } })

export const getCustos = (objeto: string, objetoId: number): Promise<Custo[]> =>
  request.get({ url: '/itsm/custo/list', params: { objeto, objetoId } })
export const salvarCusto = (data: Custo): Promise<number> =>
  request.post({ url: '/itsm/custo/save', data })
export const excluirCusto = (id: number) =>
  request.delete({ url: '/itsm/custo/delete', params: { id } })

export const getLicencas = (params: any) => request.get({ url: '/itsm/licenca/page', params })
export const getLicenca = (id: number): Promise<Licenca> =>
  request.get({ url: '/itsm/licenca/get', params: { id } })
export const salvarLicenca = (data: Licenca): Promise<number> =>
  request.post({ url: '/itsm/licenca/save', data })
export const excluirLicenca = (id: number) =>
  request.delete({ url: '/itsm/licenca/delete', params: { id } })
export const adicionarUso = (data: Uso): Promise<number> =>
  request.post({ url: '/itsm/licenca/uso', data })
export const removerUso = (id: number) =>
  request.delete({ url: '/itsm/licenca/uso', params: { id } })
export const revelarChave = (id: number): Promise<string> =>
  request.post({ url: '/itsm/licenca/revelar-chave', params: { id } })

export const getOrcamentos = (params: any) => request.get({ url: '/itsm/orcamento/page', params })
export const getOrcamentosSimples = (): Promise<Resumo[]> =>
  request.get({ url: '/itsm/orcamento/simples' })
export const getOrcamento = (id: number): Promise<Orcamento> =>
  request.get({ url: '/itsm/orcamento/get', params: { id } })
export const salvarOrcamento = (data: Orcamento): Promise<number> =>
  request.post({ url: '/itsm/orcamento/save', data })
export const excluirOrcamento = (id: number) =>
  request.delete({ url: '/itsm/orcamento/delete', params: { id } })

export const getProjetos = (params: any) => request.get({ url: '/itsm/projeto/page', params })
export const getProjeto = (id: number): Promise<Projeto> =>
  request.get({ url: '/itsm/projeto/get', params: { id } })
export const salvarProjeto = (data: Projeto): Promise<number> =>
  request.post({ url: '/itsm/projeto/save', data })
export const excluirProjeto = (id: number) =>
  request.delete({ url: '/itsm/projeto/delete', params: { id } })
export const salvarTarefa = (data: Tarefa): Promise<number> =>
  request.post({ url: '/itsm/projeto/tarefa', data })
export const excluirTarefa = (id: number) =>
  request.delete({ url: '/itsm/projeto/tarefa', params: { id } })
export const progressoTarefa = (data: {
  tarefaId: number
  situacao: number
  percentual: number
  horasGastas?: number
}) => request.post({ url: '/itsm/projeto/tarefa/progresso', data })
export const vincularProjeto = (data: { projetoId: number; objeto: string; objetoId: number }) =>
  request.post({ url: '/itsm/projeto/vinculo', data })
export const desvincularProjeto = (id: number) =>
  request.delete({ url: '/itsm/projeto/vinculo', params: { id } })
