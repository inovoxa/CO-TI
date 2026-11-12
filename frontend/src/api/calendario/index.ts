import request from '@/config/axios'

export interface Nascimento {
  /** yyyy-MM-dd */
  data: string | null
  publico: boolean
}

export interface Aniversariante {
  usuarioId: number
  nome: string
  unidade: string | null
  avatar: string | null
  /** Dia do aniversário no ano consultado (yyyy-MM-dd) */
  dia: string
  publico: boolean
  /** É o aniversário de quem está vendo */
  proprio: boolean
}

export interface Evento {
  id?: number
  titulo: string
  descricao?: string | null
  local?: string | null
  /** epoch ms */
  inicio: number
  fim?: number | null
  diaInteiro: boolean
  cor?: string | null
}

export const getMeuNascimento = (): Promise<Nascimento> => request.get({ url: '/calendario/meu-nascimento' })
export const salvarMeuNascimento = (data: Nascimento) => request.put({ url: '/calendario/meu-nascimento', data })

export const getAniversariantes = (params: { ano: number; mes?: number; deptId?: number }): Promise<Aniversariante[]> =>
  request.get({ url: '/calendario/aniversariantes', params })
export const exportarAniversariantes = (params: { ano: number; mes?: number; deptId?: number }) =>
  request.download({ url: '/calendario/aniversariantes/exportar', params })

export const getEventos = (de: string, ate: string): Promise<Evento[]> =>
  request.get({ url: '/calendario/eventos', params: { de, ate } })
export const criarEvento = (data: Evento): Promise<number> => request.post({ url: '/calendario/eventos', data })
export const alterarEvento = (id: number, data: Evento) => request.put({ url: `/calendario/eventos/${id}`, data })
export const excluirEvento = (id: number) => request.delete({ url: `/calendario/eventos/${id}` })
