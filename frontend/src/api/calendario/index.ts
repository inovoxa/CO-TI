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

export type Resposta = 'pendente' | 'sim' | 'nao' | 'talvez'

export interface Participante {
  id: number
  nome: string
  resposta: Resposta
}

export interface Pessoa {
  id: number
  nome: string
  unidade: string | null
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
  /** Ids das pessoas convidadas (ao salvar) */
  participantes?: number[]
}

/** Como vem do servidor: com quem é o dono e quem foi convidado */
export interface EventoVO extends Omit<Evento, 'participantes'> {
  id: number
  meu: boolean
  donoId: number
  dono: string
  minhaResposta: Resposta | null
  participantes: Participante[]
}

export const getMeuNascimento = (): Promise<Nascimento> => request.get({ url: '/calendario/meu-nascimento' })
export const salvarMeuNascimento = (data: Nascimento) => request.put({ url: '/calendario/meu-nascimento', data })

export const getAniversariantes = (params: { ano: number; mes?: number; deptId?: number }): Promise<Aniversariante[]> =>
  request.get({ url: '/calendario/aniversariantes', params })
export const exportarAniversariantes = (params: { ano: number; mes?: number; deptId?: number }) =>
  request.download({ url: '/calendario/aniversariantes/exportar', params })

export const getEventos = (de: string, ate: string): Promise<EventoVO[]> =>
  request.get({ url: '/calendario/eventos', params: { de, ate } })
export const criarEvento = (data: Evento): Promise<number> => request.post({ url: '/calendario/eventos', data })
export const alterarEvento = (id: number, data: Evento) => request.put({ url: `/calendario/eventos/${id}`, data })
export const excluirEvento = (id: number) => request.delete({ url: `/calendario/eventos/${id}` })
export const getEvento = (id: number): Promise<EventoVO> => request.get({ url: `/calendario/eventos/${id}` })
export const getPessoas = (busca: string): Promise<Pessoa[]> => request.get({ url: '/calendario/pessoas', params: { busca } })
export const responder = (id: number, resposta: Resposta) =>
  request.post({ url: `/calendario/eventos/${id}/resposta`, data: { resposta } })
export const sair = (id: number) => request.post({ url: `/calendario/eventos/${id}/sair` })
