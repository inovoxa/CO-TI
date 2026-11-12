import request from '@/config/axios'

export interface FiltroApuracao {
  motivo: string
  pessoaId?: number
  somenteEnviadas?: boolean
  conversaId?: number
  inicio?: string
  fim?: string
  texto?: string
  pagina?: number
}

export interface PessoaApuracao {
  id: number
  nome: string
  usuario: string
}

export interface ConversaApuracao {
  id: number
  tipo: 'direta' | 'grupo'
  nome: string
  participantes: string[]
  mensagens: number
  ultima: string | null
}

export interface VersaoMensagem {
  acao: 'editada' | 'apagada'
  conteudo: string
  quando: string
  por: string | null
}

export interface AnexoApuracao {
  id: number
  nome: string
  tamanho: number
  expirado: boolean
}

export interface MensagemApuracao {
  id: number
  conversaId: number
  conversa: string
  remetente: string | null
  usuario: string | null
  tipo: string
  conteudo: string
  enviadaEm: string
  editadaEm: string | null
  apagadaEm: string | null
  versoes: VersaoMensagem[]
  anexos: AnexoApuracao[]
}

export interface PaginaApuracao {
  mensagens: MensagemApuracao[]
  total: number
  pagina: number
  porPagina: number
}

export const podeApurar = (): Promise<boolean> => request.get({ url: '/chat/apuracao/acesso' })
export const pessoas = (busca: string): Promise<PessoaApuracao[]> =>
  request.get({ url: '/chat/apuracao/pessoas', params: { busca } })
export const conversas = (f: FiltroApuracao): Promise<ConversaApuracao[]> =>
  request.post({ url: '/chat/apuracao/conversas', data: f })
export const mensagens = (f: FiltroApuracao): Promise<PaginaApuracao> =>
  request.post({ url: '/chat/apuracao/mensagens', data: f })
export const exportar = (f: FiltroApuracao): Promise<Blob> =>
  request.download({ method: 'POST', url: '/chat/apuracao/exportar', data: f })
export const anexo = (id: number, motivo: string): Promise<Blob> =>
  request.download({ method: 'POST', url: '/chat/apuracao/anexo', data: { id, motivo } })
