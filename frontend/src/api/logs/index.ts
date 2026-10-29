import request from '@/config/axios'

/** Logs centralizados (no lugar do Graylog): consultas, painéis e situação das entradas. */

export type Fluxo = 'firewall' | 'contas' | 'maquinas' | 'arquivos' | 'geral'
export type TipoQuadro = 'contador' | 'mensagens' | 'ranking' | 'histograma'

export interface Filtro {
  consulta?: string
  filtroGeral?: string
  fluxo?: Fluxo | ''
  periodo?: string
  de?: string
  ate?: string
}

export interface Mensagem {
  momento: string
  fluxo: Fluxo
  origem: string
  ip?: string
  nivel: number
  aplicacao?: string
  mensagem: string
  campos: Record<string, any>
}

export interface Mensagens {
  lista: Mensagem[]
  total: number
  limiteTotal: number
  ms: number
}

export interface Contagem {
  valor: number
  anterior?: number
  rotuloPeriodo: string
  ms: number
}

export interface Item {
  valor: string
  /** quantidade de mensagens, ou a soma (ex.: bytes) quando o ranking soma um campo */
  total: number
  /** com soma: quantas mensagens deram o total */
  mensagens?: number
}

export interface Ranking {
  itens: Item[]
  total: number
  ms: number
}

export interface Histograma {
  intervalo: number
  momentos: string[]
  totais: number[]
  ms: number
}

export interface Quadro {
  id: string
  tipo: TipoQuadro
  titulo: string
  fluxo?: Fluxo | ''
  consulta?: string
  periodo?: string
  largura?: number
  /** ranking: campo agrupado */
  campo?: string
  /** contador: conta valores distintos deste campo */
  distinto?: string
  /** mensagens: campos mostrados em colunas */
  colunas?: string[]
  /** ranking: quantos itens */
  limite?: number
  /** contador, ranking e histograma: soma este campo em vez de contar mensagens ("bytes" = banda) */
  soma?: string
}

export interface Painel {
  id?: number
  nome: string
  ordem?: number
  widgets: Quadro[]
}

export interface Entrada {
  nome: string
  protocolo: string
  porta: number
  portaPublica?: number
  ativa: boolean
  erro?: string
  recebidas: number
  recusadas: number
}

export interface Situacao {
  entradas: Entrada[]
  gravadas: number
  descartadas: number
  invalidas: number
  falhas: number
  fila: number
  retencaoDias: number
  ultimas24h: Record<string, number>
  tamanho: string
}

export interface Origem {
  origem: string
  fluxo: Fluxo
  ip?: string
  total: number
  ultima: string
}

export const getMensagens = (filtro: Filtro, pagina = 1, tamanho = 50): Promise<Mensagens> =>
  request.post({ url: '/logs/mensagens', data: { filtro, pagina, tamanho } })
/** soma: campo numérico somado em vez de contar mensagens ("bytes" = sentbyte + rcvdbyte, a banda do FortiGate) */
export const getContagem = (filtro: Filtro, distinto?: string, soma?: string): Promise<Contagem> =>
  request.post({ url: '/logs/contagem', data: { filtro, distinto, soma: soma || undefined } })
export const getRanking = (filtro: Filtro, campo: string, limite = 10, soma?: string): Promise<Ranking> =>
  request.post({ url: '/logs/ranking', data: { filtro, campo, limite, soma: soma || undefined } })
export const getHistograma = (filtro: Filtro, soma?: string): Promise<Histograma> =>
  request.post({ url: '/logs/histograma', data: filtro, params: soma ? { soma } : undefined })
export const getCampos = (filtro: Filtro): Promise<Item[]> =>
  request.post({ url: '/logs/campos', data: filtro })

export const getPaineis = (): Promise<Painel[]> => request.get({ url: '/logs/paineis' })
export const salvarPainel = (p: Painel): Promise<number> => request.post({ url: '/logs/painel', data: p })
export const excluirPainel = (id: number) => request.delete({ url: '/logs/painel', params: { id } })
export const ordenarPaineis = (ids: number[]) => request.put({ url: '/logs/paineis/ordem', data: ids })

export const getSituacao = (): Promise<Situacao> => request.get({ url: '/logs/situacao' })
export const getOrigens = (): Promise<Origem[]> => request.get({ url: '/logs/origens' })

// ================================================================================== apoio das telas

export const FLUXOS: { valor: Fluxo | ''; rotulo: string }[] = [
  { valor: '', rotulo: 'Todos os fluxos' },
  { valor: 'firewall', rotulo: 'Firewall' },
  { valor: 'contas', rotulo: 'Contas' },
  { valor: 'maquinas', rotulo: 'Máquinas' },
  { valor: 'arquivos', rotulo: 'Arquivos' },
  { valor: 'geral', rotulo: 'Geral' }
]

export const PERIODOS = [
  { valor: 'hoje', rotulo: 'Hoje' },
  { valor: 'ontem', rotulo: 'Ontem' },
  { valor: '5m', rotulo: 'Últimos 5 minutos' },
  { valor: '15m', rotulo: 'Últimos 15 minutos' },
  { valor: '30m', rotulo: 'Últimos 30 minutos' },
  { valor: '1h', rotulo: 'Última hora' },
  { valor: '4h', rotulo: 'Últimas 4 horas' },
  { valor: '12h', rotulo: 'Últimas 12 horas' },
  { valor: '1d', rotulo: 'Último dia' },
  { valor: '2d', rotulo: 'Últimos 2 dias' },
  { valor: '7d', rotulo: 'Últimos 7 dias' },
  { valor: '30d', rotulo: 'Últimos 30 dias' },
  { valor: 'tudo', rotulo: 'Todo o período' }
]

export const NIVEIS = ['Emergência', 'Alerta', 'Crítico', 'Erro', 'Aviso', 'Aviso leve', 'Informação', 'Depuração']

/** Termo de consulta para um valor: campo:"valor" (aspas escapadas) */
export const termo = (campo: string, valor: string | number) =>
  `${campo}:"${String(valor).replace(/"/g, '\\"')}"`

export const juntar = (...partes: (string | undefined)[]) => {
  const p = partes.map((x) => (x ?? '').trim()).filter(Boolean)
  return p.length <= 1 ? (p[0] ?? '') : p.map((x) => `(${x})`).join(' AND ')
}

export const numero = (n?: number) => (n ?? 0).toLocaleString('pt-BR')

/** 1536 → "1,5 KB" (base 1024, como o FortiGate) */
export const bytes = (n?: number) => {
  const u = ['B', 'KB', 'MB', 'GB', 'TB', 'PB']
  let v = Math.abs(n ?? 0)
  let i = 0
  while (v >= 1024 && i < u.length - 1) {
    v /= 1024
    i++
  }
  const t = v.toLocaleString('pt-BR', { maximumFractionDigits: i === 0 ? 0 : 1 })
  return `${(n ?? 0) < 0 ? '−' : ''}${t} ${u[i]}`
}

/** Valor de um contador/ranking/histograma: bytes quando soma banda, senão número */
export const valorDe = (soma: string | undefined, n?: number) =>
  soma === 'bytes' || soma === 'sentbyte' || soma === 'rcvdbyte' ? bytes(n) : numero(n)

export const MEDIDAS = [
  { valor: '', rotulo: 'Mensagens' },
  { valor: 'bytes', rotulo: 'Banda (enviados + recebidos)' },
  { valor: 'rcvdbyte', rotulo: 'Banda recebida (download)' },
  { valor: 'sentbyte', rotulo: 'Banda enviada (upload)' }
]
