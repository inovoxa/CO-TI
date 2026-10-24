import request from '@/config/axios'

// ========== tipos ==========

export interface Etiqueta {
  id?: number
  nome: string
  cor: string
  descricao?: string
}

export interface ConversaResumo {
  id: number
  caixaId: number
  caixa?: string
  canal?: string
  contatoId: number
  contato: string
  telefone?: string
  status: number // 0 aberta, 1 resolvida, 2 pendente, 3 adiada
  statusRotulo: string
  agenteId?: number
  agente?: string
  prioridade?: number
  chamadoId?: number
  adiadaAte?: number
  ultimaAtividadeEm: number
  ultimaEntradaEm?: number
  ultimaMensagem?: string
  ultimaMensagemTipo?: number
  naoLidas: number
  etiquetas: Etiqueta[]
  /** canal com janela de 24 h (WhatsApp oficial, Instagram) */
  janela?: boolean
  /** até quando dá para mandar texto livre */
  janelaAte?: number
}

export interface Anexo {
  url: string
  tipo?: string
  nome?: string
  tamanho?: number
}

export interface Mensagem {
  id: number
  conversaId: number
  tipo: number // 0 entrada, 1 saída, 2 atividade, 3 modelo
  privada: boolean
  remetenteTipo: 'contato' | 'agente' | 'robo' | 'sistema'
  remetenteId?: number
  remetente: string
  conteudo: string
  formato: string
  anexos: Anexo[]
  status: 'pendente' | 'enviada' | 'entregue' | 'lida' | 'falhou'
  erro?: string
  criadaEm: number
  /** modelo usado, localização, resposta de botão, reação, origem ("celular") e histórico */
  dados?: Record<string, any>
}

export interface Contato {
  id?: number
  nome: string
  telefone?: string
  email?: string
  identificador?: string
  userId?: number
  usuario?: string
  atributos?: Record<string, any>
  observacao?: string
  bloqueado: boolean
  ultimaAtividadeEm?: number
  conversas?: number
}

export interface Lateral {
  contato: Contato
  chamados: { id: number; titulo: string; status: string; abertoEm: number }[]
  anteriores: ConversaResumo[]
}

export interface Agente {
  userId: number
  nome: string
  disponibilidade: string
  disponivel: boolean
}

export interface Caixa {
  id?: number
  nome: string
  canal: string
  ativo: boolean
  webhookUrl?: string
  saudacao?: string
  equipeId?: number
  atribuicaoAutomatica: boolean
  limitePorAgente: number
  reabrirHoras: number
  agenteIds: number[]
  tokenDefinido?: boolean
  segredoDefinido?: boolean
  agentes?: Agente[]
  abertas?: number
}

export interface Credenciais {
  token: string
  segredoWebhook: string
  enderecoEntrada: string
}

export interface CanalConfig {
  caixaId: number
  canal?: string
  phoneNumberId?: string
  wabaId?: string
  coexistencia?: boolean
  igUserId?: string
  // segredos: só na gravação (em branco = mantém)
  token?: string
  appSecret?: string
  botToken?: string
  // resposta
  tokenDefinido?: boolean
  appSecretDefinido?: boolean
  botTokenDefinido?: boolean
  verifyToken?: string
  enderecoWebhook?: string
  info?: Record<string, any>
  testadoEm?: number
  erroTeste?: string
}

export interface Modelo {
  id: number
  nome: string
  idioma: string
  categoria?: string
  status: string
  corpo: string
  variaveis: number
  nomesVariaveis: string[]
}

export interface RespostaPronta {
  id?: number
  atalho: string
  conteudo: string
}

// ========== rótulos ==========

export const CANAIS: Record<string, { rotulo: string; icone: string }> = {
  api: { rotulo: 'API', icone: 'ep:connection' },
  whatsapp_cloud: { rotulo: 'WhatsApp (oficial)', icone: 'ep:chat-dot-round' },
  instagram: { rotulo: 'Instagram', icone: 'ep:camera' },
  telegram: { rotulo: 'Telegram', icone: 'ep:promotion' }
}
export const STATUS_CONVERSA = [
  { valor: 0, rotulo: 'Abertas' },
  { valor: 2, rotulo: 'Pendentes' },
  { valor: 3, rotulo: 'Adiadas' },
  { valor: 1, rotulo: 'Resolvidas' }
]
export const PRIORIDADES = [
  { valor: 1, rotulo: 'Baixa', cor: 'info' },
  { valor: 2, rotulo: 'Média', cor: 'primary' },
  { valor: 3, rotulo: 'Alta', cor: 'warning' },
  { valor: 4, rotulo: 'Urgente', cor: 'danger' }
]

/**
 * Texto do contato ou do atendente para mostrar como HTML: escapa tudo e só então aplica a formatação do WhatsApp
 * (*negrito*, _itálico_, ~riscado~, ```mono```) e transforma endereços http(s) em links. Nenhuma marcação vinda de
 * fora passa.
 */
export function formatarTexto(texto: string): string {
  const esc = (texto || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
  return esc
    .replace(/```([^`]+)```/g, '<code>$1</code>')
    .replace(/(^|[\s(])\*([^*\n]+)\*(?=[\s).,!?:;]|$)/g, '$1<b>$2</b>')
    .replace(/(^|[\s(])_([^_\n]+)_(?=[\s).,!?:;]|$)/g, '$1<i>$2</i>')
    .replace(/(^|[\s(])~([^~\n]+)~(?=[\s).,!?:;]|$)/g, '$1<s>$2</s>')
    .replace(
      /\bhttps?:\/\/[^\s<]+[^\s<.,;:!?)]/g,
      (u) => `<a href="${u}" rel="noopener noreferrer" target="_blank">${u}</a>`
    )
    .replace(/\n/g, '<br>')
}

// ========== chamadas ==========

export const getConversas = (params: any) =>
  request.get({ url: '/atendimento/conversa/page', params })
export const getContagens = (caixaId?: number): Promise<Record<string, number>> =>
  request.get({ url: '/atendimento/conversa/contagens', params: { caixaId } })
export const getConversa = (id: number): Promise<ConversaResumo> =>
  request.get({ url: '/atendimento/conversa/get', params: { id } })
export const getMensagens = (id: number, antesDe?: number): Promise<Mensagem[]> =>
  request.get({ url: '/atendimento/conversa/mensagens', params: { id, antesDe } })
export const getLateral = (id: number): Promise<Lateral> =>
  request.get({ url: '/atendimento/conversa/lateral', params: { id } })
export const responder = (data: {
  conversaId: number
  conteudo?: string
  privada: boolean
  anexos?: Anexo[]
}): Promise<number> => request.post({ url: '/atendimento/conversa/responder', data })
export const atribuir = (conversaId: number, agenteId?: number) =>
  request.post({ url: '/atendimento/conversa/atribuir', data: { conversaId, agenteId } })
export const mudarStatus = (conversaId: number, status: number, adiadaAte?: string) =>
  request.post({ url: '/atendimento/conversa/status', data: { conversaId, status, adiadaAte } })
export const definirEtiquetas = (conversaId: number, etiquetaIds: number[]) =>
  request.post({ url: '/atendimento/conversa/etiquetas', data: { conversaId, etiquetaIds } })
export const definirPrioridade = (conversaId: number, prioridade?: number) =>
  request.post({ url: '/atendimento/conversa/prioridade', data: { conversaId, prioridade } })
export const marcarLida = (id: number) =>
  request.post({ url: '/atendimento/conversa/lida', params: { id } })
export const abrirChamado = (
  data: {
    conversaId: number
    titulo: string
    categoriaId?: number
    urgencia: number
    incluirConversa: boolean
  },
  avisarContato: boolean
): Promise<number> =>
  request.post({ url: '/atendimento/conversa/abrir-chamado', data, params: { avisarContato } })
export const reenviar = (conversaId: number, mensagemId: number) =>
  request.post({ url: '/atendimento/mensagem/reenviar', params: { conversaId, mensagemId } })

export const definirDisponibilidade = (valor: string) =>
  request.post({ url: '/atendimento/agente/disponibilidade', params: { valor } })
export const sinal = (): Promise<string> => request.post({ url: '/atendimento/agente/sinal' })

export const getMinhasCaixas = (): Promise<Caixa[]> =>
  request.get({ url: '/atendimento/caixa/minhas' })
export const getCaixas = (): Promise<Caixa[]> => request.get({ url: '/atendimento/caixa/list' })
export const salvarCaixa = (data: Caixa): Promise<number> =>
  request.post({ url: '/atendimento/caixa/save', data })
export const gerarCredenciais = (id: number): Promise<Credenciais> =>
  request.post({ url: '/atendimento/caixa/credenciais', params: { id } })
export const getCanal = (id: number): Promise<CanalConfig> =>
  request.get({ url: '/atendimento/caixa/canal', params: { id } })
export const salvarCanal = (data: CanalConfig): Promise<CanalConfig> =>
  request.post({ url: '/atendimento/caixa/canal', data })
export const testarCanal = (id: number): Promise<CanalConfig> =>
  request.post({ url: '/atendimento/caixa/canal/testar', params: { id } })
export const sincronizarModelos = (id: number): Promise<number> =>
  request.post({ url: '/atendimento/caixa/modelos/sincronizar', params: { id } })
export const getModelosDaCaixa = (id: number): Promise<Modelo[]> =>
  request.get({ url: '/atendimento/caixa/modelos', params: { id } })
export const getModelos = (caixaId: number): Promise<Modelo[]> =>
  request.get({ url: '/atendimento/modelo/list', params: { caixaId } })
export const enviarModelo = (data: {
  conversaId: number
  modeloId: number
  parametros: string[]
}): Promise<number> => request.post({ url: '/atendimento/conversa/modelo', data })
export const excluirCaixa = (id: number) =>
  request.delete({ url: '/atendimento/caixa/delete', params: { id } })

export const getContatos = (params: any) =>
  request.get({ url: '/atendimento/contato/page', params })
export const salvarContato = (data: Contato): Promise<number> =>
  request.post({ url: '/atendimento/contato/save', data })

export const getEtiquetas = (): Promise<Etiqueta[]> =>
  request.get({ url: '/atendimento/etiqueta/list' })
export const salvarEtiqueta = (data: Etiqueta): Promise<number> =>
  request.post({ url: '/atendimento/etiqueta/save', data })
export const excluirEtiqueta = (id: number) =>
  request.delete({ url: '/atendimento/etiqueta/delete', params: { id } })
export const getRespostas = (): Promise<RespostaPronta[]> =>
  request.get({ url: '/atendimento/resposta/list' })
export const salvarResposta = (data: RespostaPronta): Promise<number> =>
  request.post({ url: '/atendimento/resposta/save', data })
export const excluirResposta = (id: number) =>
  request.delete({ url: '/atendimento/resposta/delete', params: { id } })
