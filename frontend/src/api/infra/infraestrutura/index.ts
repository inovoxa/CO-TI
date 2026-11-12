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
  zero: string | null
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

// ------------------------------------------------------------------ Diretório LDAP (AD)

export interface DadosAd {
  habilitado: boolean
  servidores: string
  porta: number | null
  seguranca: 'ldaps' | 'starttls'
  certificadoCa: string | null
  bindDn: string
  baseUsuarios: string
  filtro: string | null
  ouRaiz: string | null
  espelharOus: boolean
  deptRaizId: number | null
  perfilPadrao: string | null
  grupoAprovadores: string | null
  gruposPerfis: Record<string, string>
  desativarAusentes: boolean
}

export interface TelaAd {
  dados: DadosAd
  senhaSalva: boolean
  origem: 'ambiente' | 'tela'
  atualizadoPor: string | null
  atualizadoEm: string | null
  conectado: boolean
  erroConexao: string | null
}

export interface TesteAd {
  ok: boolean
  etapas: string[]
  erro: string | null
}

export interface CertificadoAd {
  assunto: string
  emissor: string
  validoAte: string
  sha256: string
  ca: boolean
  pem: string
}

export interface ResumoSincronizacao {
  lidas: number
  sincronizadas: number
  ignoradas: number
  desativadas: number
  desativacaoSuspensa: boolean
}

export const getAd = (): Promise<TelaAd> => request.get({ url: '/infraestrutura/ad' })
export const salvarAd = (dados: DadosAd, senha: string) => request.put({ url: '/infraestrutura/ad', data: { dados, senha } })
export const testarAd = (dados: DadosAd, senha: string, login: string, senhaUsuario: string): Promise<TesteAd> =>
  request.post({ url: '/infraestrutura/ad/testar', data: { dados, senha, login, senhaUsuario } })
export const certificadosAd = (servidores: string, porta: number | null, seguranca: string): Promise<CertificadoAd[]> =>
  request.post({ url: '/infraestrutura/ad/certificados', data: { servidores, porta, seguranca } })
export const sincronizarAd = (): Promise<ResumoSincronizacao> => request.post({ url: '/infraestrutura/ad/sincronizar' })
