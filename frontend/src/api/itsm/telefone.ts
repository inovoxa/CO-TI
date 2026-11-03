import request from '@/config/axios'

export interface Telefone {
  id?: number
  nome: string
  setor?: string
  unidadeId?: number
  unidade?: string
  ramal?: string
  telefone?: string
  celular?: string
  /** existe um celular, mas ele é restrito à equipe de TI */
  celularOculto?: boolean
  whatsapp?: boolean
  celularPublico?: boolean
  emergencia?: boolean
  email?: string
  observacao?: string
  linkFora?: boolean
}

export interface UnidadeTelefone {
  id: number
  nome: string
  linkFora: boolean
}

export interface ContatosUnidade {
  unidadeId: number
  unidade: string
  linkFora: boolean
  contatos: Telefone[]
}

export interface ImportacaoTelefones {
  criados: number
  atualizados: number
  recusados: number
  erros: string[]
}

export const getTelefones = (params: {
  pageNo: number
  pageSize: number
  busca?: string
  setor?: string
  unidadeId?: number
  emergencia?: boolean
}): Promise<{ list: Telefone[]; total: number }> =>
  request.get({ url: '/itsm/telefone/page', params })

export const getSetoresTelefone = (): Promise<string[]> =>
  request.get({ url: '/itsm/telefone/setores' })

export const getUnidadesTelefone = (): Promise<UnidadeTelefone[]> =>
  request.get({ url: '/itsm/telefone/unidades' })

export const getContatosUnidade = (params: {
  unidadeId?: number
  deptId?: number
}): Promise<ContatosUnidade | null> => request.get({ url: '/itsm/telefone/da-unidade', params })

export const salvarTelefone = (data: Telefone): Promise<number> =>
  request.post({ url: '/itsm/telefone/salvar', data })

export const excluirTelefone = (id: number) =>
  request.delete({ url: '/itsm/telefone/excluir', params: { id } })

export const baixarModeloTelefones = (): Promise<Blob> =>
  request.download({ url: '/itsm/telefone/modelo' })

export const importarTelefones = (arquivo: File): Promise<ImportacaoTelefones> =>
  request.upload({ url: '/itsm/telefone/importar', data: { arquivo } })
