import request from '@/config/axios'

/** Assistente da tela de login ("Esqueci minha senha"): público, a visita se identifica pelo token de /iniciar. */
export interface MensagemWidget {
  id: number
  /** voce | assistente | atendente */
  de: string
  texto: string
  em: string | number
}

const BASE = '/atendimento/canal/widget'
const visita = (token: string) => ({ 'X-CO-TI-Visita': token, isToken: false })

export const assistenteDisponivel = (): Promise<boolean> =>
  request.get({ url: BASE + '/disponivel', headers: { isToken: false } })

export const iniciarVisita = (): Promise<string> =>
  request.post({ url: BASE + '/iniciar', headers: { isToken: false } })

export const enviarMensagem = (token: string, texto: string): Promise<boolean> =>
  request.post({ url: BASE + '/mensagem', data: { texto }, headers: visita(token) })

export const listarMensagens = (token: string, depois: number): Promise<MensagemWidget[]> =>
  request.get({ url: BASE + '/mensagens', params: { depois }, headers: visita(token) })
