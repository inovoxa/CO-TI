import request from '@/config/axios'

export interface Consulta {
  ferramenta: string
  argumentos: Record<string, any>
}

export interface Resposta {
  sessaoId: number
  resposta: string
  consultas: Consulta[]
  erro: boolean
}

export interface Sessao {
  id: number
  titulo: string
  atualizadaEm: string
}

export interface Mensagem {
  id: number
  papel: 'user' | 'assistant'
  conteudo: string
  erro?: boolean
  criadaEm: string
  consultas?: Consulta[]
}

export interface Voz {
  /** o servidor transcreve a fala (provedor de IA com modelo de áudio) */
  transcricao: boolean
  /** a IA está ligada */
  ia: boolean
}

/** falada = a resposta vai ser lida em voz alta: o assistente responde curto e sem tabelas */
export const perguntar = (texto: string, sessaoId?: number, falada = false): Promise<Resposta> =>
  request.post({
    url: '/assistente/perguntar',
    data: { texto, sessaoId, falada },
    timeout: 300000
  })
export const getVoz = (): Promise<Voz> => request.get({ url: '/assistente/voz' })
export const transcrever = (audio: Blob): Promise<string> =>
  request.upload({
    url: '/assistente/transcrever',
    data: { arquivo: new File([audio], 'fala.' + (audio.type.includes('ogg') ? 'ogg' : 'webm'), { type: audio.type }) },
    timeout: 120000
  })
export const getSessoes = (): Promise<Sessao[]> => request.get({ url: '/assistente/sessoes' })
export const getMensagens = (sessaoId: number): Promise<Mensagem[]> =>
  request.get({ url: '/assistente/mensagens', params: { sessaoId } })
export const excluirSessao = (sessaoId: number) =>
  request.delete({ url: '/assistente/sessao', params: { sessaoId } })

/** Nome legível de cada consulta, para mostrar o que o assistente consultou */
export const CONSULTAS: Record<string, string> = {
  buscar_ativos: 'Inventário',
  detalhar_ativo: 'Detalhe do ativo',
  resumo_inventario: 'Resumo do inventário',
  buscar_softwares: 'Softwares',
  alertas_inventario: 'Alertas',
  licencas: 'Licenças',
  buscar_chamados: 'Chamados',
  painel_service_desk: 'Fila do Service Desk',
  detalhar_chamado: 'Detalhe do chamado',
  meus_chamados: 'Meus chamados',
  buscar_base_conhecimento: 'Base de conhecimento',
  buscar_pessoas: 'Pessoas',
  buscar_contatos: 'Ramais e telefones',
  buscar_estoque: 'Estoque GTI',
  status_links: 'Links',
  buscar_logs: 'Logs',
  contar_logs: 'Contagem de logs',
  ranking_logs: 'Ranking de logs',
  paineis_logs: 'Painéis de logs',
  buscar_problemas: 'Problemas',
  buscar_mudancas: 'Mudanças',
  buscar_conversas: 'Conversas do atendimento',
  rede_descoberta: 'Rede descoberta',
  pacotes: 'Pacotes'
}
