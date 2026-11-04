import request from '@/config/axios'

export interface Tipo {
  id?: number
  nome: string
  controle?: 'unidade' | 'quantidade'
  ativo?: boolean
}

export interface Item {
  id?: number
  tipoId?: number
  tipo?: string
  controle?: 'unidade' | 'quantidade'
  descricao: string
  identificador?: string
  linha?: string
  patrimonio?: string
  condicao: 'novo' | 'usado'
  situacao?: 'disponivel' | 'em_uso' | 'manutencao' | 'baixado'
  quantidade?: number
  minimo?: number
  baixo?: boolean
  dataCompra?: string
  fornecedor?: string
  notaFiscal?: string
  valor?: number
  garantiaAte?: string
  usadoPor?: string
  responsavel?: string
  destino?: string
  observacao?: string
}

export interface Movimento {
  id: number
  tipo: string
  quantidade: number
  pessoa?: string
  destino?: string
  motivo?: string
  registradoPor?: string
  quando: string
}

export interface Resumo {
  tipoId: number
  tipo: string
  disponivel: number
  emUso: number
  manutencao: number
  baixos: number
}

export const getTipos = (): Promise<Tipo[]> => request.get({ url: '/itsm/estoque/tipos' })
export const salvarTipo = (data: Tipo): Promise<number> =>
  request.post({ url: '/itsm/estoque/tipo/salvar', data })
export const getResumo = (): Promise<Resumo[]> => request.get({ url: '/itsm/estoque/resumo' })
export const getPagina = (params: any): Promise<{ list: Item[]; total: number }> =>
  request.get({ url: '/itsm/estoque/page', params })
export const salvar = (data: Item): Promise<number> =>
  request.post({ url: '/itsm/estoque/salvar', data })
export const movimentar = (data: {
  itemId: number
  tipo: string
  quantidade: number
  pessoa?: string
  destino?: string
  motivo?: string
}) => request.post({ url: '/itsm/estoque/movimentar', data })
export const getHistorico = (itemId: number): Promise<Movimento[]> =>
  request.get({ url: '/itsm/estoque/historico', params: { itemId } })
