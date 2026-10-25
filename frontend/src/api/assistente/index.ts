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

export const perguntar = (texto: string, sessaoId?: number): Promise<Resposta> =>
  request.post({ url: '/assistente/perguntar', data: { texto, sessaoId }, timeout: 300000 })
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
  status_links: 'Links',
  buscar_logs: 'Logs',
  buscar_problemas: 'Problemas',
  buscar_mudancas: 'Mudanças',
  buscar_conversas: 'Conversas do atendimento',
  rede_descoberta: 'Rede descoberta',
  pacotes: 'Pacotes'
}
