import request from '@/config/axios'

export interface Link {
  id?: number
  titulo: string
  url: string
  descricao?: string
  categoria?: string
  ordem?: number
}

export interface Painel {
  publicos: Link[]
  meus: Link[]
  limiteMeus: number
}

export const getPainel = (): Promise<Painel> => request.get({ url: '/acesso/painel' })
export const salvarPublico = (data: Link): Promise<number> => request.post({ url: '/acesso/publico', data })
export const excluirPublico = (id: number) => request.delete({ url: '/acesso/publico', params: { id } })
export const salvarMeu = (data: Link): Promise<number> => request.post({ url: '/acesso/meu', data })
export const excluirMeu = (id: number) => request.delete({ url: '/acesso/meu', params: { id } })
