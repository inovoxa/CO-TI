import request from '@/config/axios'

export interface Telefone {
  id?: number
  nome: string
  setor?: string
  unidade?: string
  ramal?: string
  telefone?: string
  email?: string
  observacao?: string
}

export const getTelefones = (params: {
  pageNo: number
  pageSize: number
  busca?: string
  setor?: string
}): Promise<{ list: Telefone[]; total: number }> =>
  request.get({ url: '/itsm/telefone/page', params })

export const getSetoresTelefone = (): Promise<string[]> =>
  request.get({ url: '/itsm/telefone/setores' })

export const salvarTelefone = (data: Telefone): Promise<number> =>
  request.post({ url: '/itsm/telefone/salvar', data })

export const excluirTelefone = (id: number) =>
  request.delete({ url: '/itsm/telefone/excluir', params: { id } })
