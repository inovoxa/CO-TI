import request from '@/config/axios'

export interface Area {
  chave: string
  rotulo: string
  bytes: number
  linhas: number
  crescimentoPorDia: number
  guarda: string
}

export interface Capacidade {
  totalBytes: number
  usadoBytes: number
  livreBytes: number
  percentual: number
  pelaReserva: boolean
  dadosBytes: number
  crescimentoPorDia: number
  diasAteEncher: number | null
  diasDeHistorico: number
  diasMinimos: number
  areas: Area[]
}

export interface ItemRetencao {
  chave: string
  rotulo: string
  ajuda: string
  unidade: string
  padrao: number
  minimo: number
  semLimite: boolean
  valor: number
}

export interface Rotina {
  nome: string
  rotulo: string
  ultimaExecucao: string | null
  duracaoMs: number
  status: 'ok' | 'erro'
  ultimoErro: string | null
  execucoes: number
  falhas: number
  ultimaFalha: string | null
}

export const getCapacidade = (): Promise<Capacidade> => request.get({ url: '/infraestrutura/capacidade' })
export const getRetencao = (): Promise<ItemRetencao[]> => request.get({ url: '/infraestrutura/retencao' })
export const salvarRetencao = (chave: string, valor: number) =>
  request.put({ url: '/infraestrutura/retencao', data: { chave, valor } })
export const getRotinas = (): Promise<Rotina[]> => request.get({ url: '/infraestrutura/rotinas' })
