import request from '@/config/axios'

/** 0 fora, 1 no ar, 2 pendente, 3 manutenção */
export const ESTADOS: Record<number, { rotulo: string; cor: string }> = {
  0: { rotulo: 'Fora do ar', cor: 'danger' },
  1: { rotulo: 'No ar', cor: 'success' },
  2: { rotulo: 'Pendente', cor: 'warning' },
  3: { rotulo: 'Manutenção', cor: 'info' }
}

export const TIPOS = [
  { valor: 'http', rotulo: 'HTTP(S)', ajuda: 'Responde com um código aceito' },
  {
    valor: 'palavra',
    rotulo: 'HTTP(S) + palavra-chave',
    ajuda: 'A resposta contém (ou não) um texto'
  },
  {
    valor: 'json',
    rotulo: 'HTTP(S) + consulta JSON',
    ajuda: 'Um valor do JSON confere com o esperado'
  },
  { valor: 'tcp', rotulo: 'Porta TCP', ajuda: 'A porta aceita conexão' },
  { valor: 'ping', rotulo: 'Ping', ajuda: 'O host responde ao ping (ICMP)' },
  { valor: 'dns', rotulo: 'DNS', ajuda: 'O nome resolve (e, se quiser, para o valor esperado)' },
  { valor: 'push', rotulo: 'Push', ajuda: 'O serviço chama uma URL de tempos em tempos' },
  { valor: 'manual', rotulo: 'Manual', ajuda: 'O estado é escolhido à mão' }
]

export const PAPEIS = [
  { valor: 'principal', rotulo: 'Link principal da unidade' },
  { valor: 'redundante', rotulo: 'Link redundante' },
  { valor: 'servico', rotulo: 'Serviço' }
]

export const CANAIS = [
  { valor: 'email', rotulo: 'E-mail' },
  { valor: 'matrix', rotulo: 'Matrix' },
  { valor: 'telegram', rotulo: 'Telegram' },
  { valor: 'webhook', rotulo: 'Webhook' }
]

export const DIAS = ['', 'Seg', 'Ter', 'Qua', 'Qui', 'Sex', 'Sáb', 'Dom']

export interface Batida {
  momento: string
  estado: number
  latenciaMs?: number
  msg?: string
  importante?: boolean
}

export interface Monitor {
  id?: number
  nome: string
  descricao?: string
  tipo: string
  alvo?: string
  porta?: number
  metodo?: string
  corpo?: string
  cabecalhos?: string
  codigosAceitos?: string
  palavra?: string
  palavraInvertida?: boolean
  jsonCaminho?: string
  jsonOperador?: string
  jsonEsperado?: string
  dnsTipo?: string
  dnsServidor?: string
  dnsEsperado?: string
  intervaloS?: number
  tentativas?: number
  intervaloTentativaS?: number
  tempoLimiteS?: number
  reenviarACada?: number
  invertido?: boolean
  ignorarTls?: boolean
  maxRedirecionamentos?: number
  lentoMs?: number
  certAvisoDias?: number
  unidadeId?: number
  papel?: string
  deptId?: number
  abrirChamado?: boolean
  chamadoCategoriaId?: number
  chamadoUrgencia?: number
  chamadoSolucionar?: boolean
  ativo?: boolean
  canalIds?: number[]
  pushToken?: string
  kumaId?: number
}

export interface MonitorResp {
  id: number
  nome: string
  tipo: string
  alvo?: string
  porta?: number
  unidadeId?: number
  unidadeNome?: string
  papel: string
  ativo: boolean
  intervaloS: number
  estado?: number
  lento?: boolean
  estadoDesde?: string
  ultimaVerificacao?: string
  ultimaLatenciaMs?: number
  ultimaMsg?: string
  certDias?: number
  chamadoId?: number
  emManutencao?: boolean
  historico: Batida[]
  uptime24h?: number
  uptime30d?: number
  latenciaMedia24h?: number
}

export interface Evento {
  monitorId: number
  monitorNome: string
  momento: string
  estado: number
  msg?: string
  duracaoAnteriorS?: number
}

export interface Detalhe {
  config: Monitor
  estado: MonitorResp
  eventos: Evento[]
  uptime1a?: number
  pushUrl?: string
  manutencoes: string[]
}

export interface Ponto {
  momento: string
  media?: number
  minima?: number
  maxima?: number
  falhas: number
  total: number
}

export interface Contagens {
  todos: number
  noAr: number
  fora: number
  pendente: number
  manutencao: number
  pausado: number
  lento: number
  certificadoVencendo: number
}

export interface Manutencao {
  id?: number
  titulo: string
  descricao?: string
  tipo: string
  ativa?: boolean
  inicio?: string
  fim?: string
  diasSemana?: number[]
  horaInicio?: string
  horaFim?: string
  unidadeId?: number
  monitorIds?: number[]
  valendo?: boolean
  unidadeNome?: string
  monitorNomes?: string[]
}

export interface Canal {
  id?: number
  nome: string
  tipo: string
  config?: Record<string, any>
  padrao?: boolean
  ativo?: boolean
  monitores?: number
}

/** Importação de planilha: `de` vazio é dado adicionado; `para` vazio é dado removido */
export interface ImportacaoMudanca {
  campo: string
  de?: string
  para?: string
}
export interface ImportacaoItem {
  id?: number
  nome: string
  mudancas: ImportacaoMudanca[]
  aviso?: string
}
export interface ImportacaoPrevia {
  novos: ImportacaoItem[]
  alterados: ImportacaoItem[]
  ausentes: ImportacaoItem[]
  semMudanca: number
  recusados: number
  erros: string[]
}
export interface ImportacaoResultado {
  adicionados: number
  atualizados: number
  removidos: number
  semMudanca: number
  recusados: number
}

export interface Unidade {
  id?: number
  nome: string
  deptId?: number
  cep?: string
  logradouro?: string
  numero?: string
  complemento?: string
  bairro?: string
  cidade?: string
  uf?: string
  latitude?: number
  longitude?: number
  ativa?: boolean
  deptNome?: string
  monitores?: number
}

export interface Coordenadas {
  latitude: number
  longitude: number
  precisao: 'endereco' | 'rua' | 'cep' | 'cidade'
  descricao?: string
}

export interface ItemKuma {
  kumaId: number
  nome: string
  tipoKuma?: string
  tipo?: string
  alvo?: string
  ativo?: boolean
  jaImportado?: boolean
  aviso?: string
}

export interface AnaliseKuma {
  token: string
  monitores: ItemKuma[]
  canais: ItemKuma[]
  manutencoes: ItemKuma[]
  batidas30d: number
  etiquetas: number
}

export interface ResultadoKuma {
  monitores: number
  canais: number
  manutencoes: number
  batidas: number
  avisos: string[]
}

// ---------------------------------------------------------------- monitores
export const exportarMonitores = (params: any): Promise<Blob> =>
  request.download({ url: '/monitoramento/monitor/exportar', params })
export const analisarImportacaoMonitores = (arquivo: File): Promise<ImportacaoPrevia> =>
  request.upload({ url: '/monitoramento/monitor/importar/analisar', data: { arquivo } })
export const importarMonitores = (
  arquivo: File,
  removerIds: number[]
): Promise<ImportacaoResultado> =>
  request.upload({
    url: '/monitoramento/monitor/importar',
    data: { arquivo, removerIds: removerIds.join(',') }
  })
export const getMonitores = (params: any) =>
  request.get({ url: '/monitoramento/monitor/page', params })
export const getContagens = (): Promise<Contagens> =>
  request.get({ url: '/monitoramento/monitor/contagens' })
export const getMonitoresSimples = (): Promise<
  { id: number; nome: string; unidadeId?: number }[]
> => request.get({ url: '/monitoramento/monitor/simples' })
export const getMonitor = (id: number): Promise<Detalhe> =>
  request.get({ url: '/monitoramento/monitor/get', params: { id } })
export const getSerie = (id: number, periodo: string): Promise<Ponto[]> =>
  request.get({ url: '/monitoramento/monitor/serie', params: { id, periodo } })
export const getEventos = (dias = 7): Promise<Evento[]> =>
  request.get({ url: '/monitoramento/monitor/eventos', params: { dias } })
export const salvarMonitor = (data: Monitor): Promise<number> =>
  request.post({ url: '/monitoramento/monitor/save', data })
export const testarMonitor = (
  data: Monitor
): Promise<{ ok: boolean; latenciaMs?: number; msg: string; certDias?: number }> =>
  request.post({ url: '/monitoramento/monitor/testar', data })
export const ativarMonitor = (id: number, ativo: boolean) =>
  request.post({ url: '/monitoramento/monitor/ativar', params: { id, ativo } })
export const verificarMonitor = (id: number) =>
  request.post({ url: '/monitoramento/monitor/verificar', params: { id } })
export const marcarManual = (id: number, noAr: boolean, msg?: string) =>
  request.post({ url: '/monitoramento/monitor/manual', params: { id, noAr, msg } })
export const excluirMonitor = (id: number) =>
  request.delete({ url: '/monitoramento/monitor/delete', params: { id } })

// ---------------------------------------------------------------- manutenções, canais e unidades
export const getManutencoes = (): Promise<Manutencao[]> =>
  request.get({ url: '/monitoramento/manutencao/list' })
export const exportarManutencoes = (): Promise<Blob> =>
  request.download({ url: '/monitoramento/manutencao/exportar' })
export const salvarManutencao = (data: Manutencao): Promise<number> =>
  request.post({ url: '/monitoramento/manutencao/save', data })
export const ativarManutencao = (id: number, ativa: boolean) =>
  request.post({ url: '/monitoramento/manutencao/ativar', params: { id, ativa } })
export const excluirManutencao = (id: number) =>
  request.delete({ url: '/monitoramento/manutencao/delete', params: { id } })

export const getCanais = (): Promise<Canal[]> => request.get({ url: '/monitoramento/canal/list' })
export const exportarCanais = (): Promise<Blob> =>
  request.download({ url: '/monitoramento/canal/exportar' })
export const salvarCanal = (data: Canal): Promise<number> =>
  request.post({ url: '/monitoramento/canal/save', data })
export const testarCanal = (id: number): Promise<string | null> =>
  request.post({ url: '/monitoramento/canal/testar', params: { id } })
export const excluirCanal = (id: number) =>
  request.delete({ url: '/monitoramento/canal/delete', params: { id } })

export const getUnidades = (): Promise<Unidade[]> =>
  request.get({ url: '/monitoramento/unidade/list' })
export const salvarUnidade = (data: Unidade): Promise<number> =>
  request.post({ url: '/monitoramento/unidade/save', data })
export const exportarUnidades = (): Promise<Blob> =>
  request.download({ url: '/monitoramento/unidade/exportar' })
export const analisarImportacaoUnidades = (arquivo: File): Promise<ImportacaoPrevia> =>
  request.upload({ url: '/monitoramento/unidade/importar/analisar', data: { arquivo } })
export const importarUnidades = (
  arquivo: File,
  removerIds: number[]
): Promise<ImportacaoResultado> =>
  request.upload({
    url: '/monitoramento/unidade/importar',
    data: { arquivo, removerIds: removerIds.join(',') }
  })
export const excluirUnidade = (id: number) =>
  request.delete({ url: '/monitoramento/unidade/delete', params: { id } })
export const buscarCep = (
  cep: string
): Promise<{ cep: string; logradouro?: string; bairro?: string; cidade?: string; uf?: string }> =>
  request.get({ url: '/monitoramento/unidade/cep', params: { cep } })
export const geocodificar = (data: Partial<Unidade>): Promise<Coordenadas> =>
  request.post({ url: '/monitoramento/unidade/geocodificar', data })

// ---------------------------------------------------------------- importar do Uptime Kuma
/** Corpo bruto (sem multipart): o kuma.db passa do limite de upload comum */
export const analisarKuma = (
  arquivo: File,
  progresso?: (p: number) => void
): Promise<AnaliseKuma> =>
  request.put({
    url: '/monitoramento/importar/kuma',
    data: arquivo,
    headersType: 'application/octet-stream',
    timeout: 900000,
    onUploadProgress: (e: any) =>
      progresso && e.total && progresso(Math.round((e.loaded * 100) / e.total))
  })
export const confirmarKuma = (data: {
  token: string
  historicoDias: number
  canais: boolean
  manutencoes: boolean
  ignorar: number[]
  unidadeId?: number
}): Promise<ResultadoKuma> =>
  request.post({ url: '/monitoramento/importar/kuma/confirmar', data, timeout: 900000 })

// ---------------------------------------------------------------- apoio
export const duracao = (s?: number) => {
  if (s === undefined || s === null) return '—'
  if (s < 60) return `${s} s`
  const min = Math.floor(s / 60)
  if (min < 60) return `${min} min`
  const h = Math.floor(min / 60)
  if (h < 24) return `${h} h${min % 60 ? ` ${min % 60} min` : ''}`
  return `${Math.floor(h / 24)} d${h % 24 ? ` ${h % 24} h` : ''}`
}
export const quando = (d?: string) =>
  d ? new Date(d).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'medium' }) : '—'
export const pct = (v?: number) =>
  v === undefined || v === null
    ? '—'
    : `${v.toLocaleString('pt-BR', { maximumFractionDigits: 2 })}%`
