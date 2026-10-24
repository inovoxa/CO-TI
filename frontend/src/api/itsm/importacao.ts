import request from '@/config/axios'

export interface Conexao {
  host: string
  porta: number
  banco: string
  usuario: string
  senha?: string
  exigirTls: boolean
}

export interface Opcoes {
  criarUsuarios: boolean
  chamados: boolean
  baseConhecimento: boolean
  problemasMudancas: boolean
  governanca: boolean
  manterNumeros: boolean
  deptPadraoId?: number
  desde?: string
  incluirLixeira: boolean
  arquivos: boolean
}

export interface Verificacao {
  versao?: string
  contagens: Record<string, number>
  usuariosEncontrados: number
  usuariosNovos: number
  usuariosSemLogin: number
  entidades: { id: number; nome: string; deptId?: number; dept?: string }[]
  numerosOcupados: Record<string, number>
  pastaArquivos: boolean
  avisos: string[]
}

export interface Etapa {
  lidos: number
  importados: number
  existentes: number
  erros: number
}

export interface Importacao {
  id: number
  status: 'pendente' | 'executando' | 'concluida' | 'erro' | 'cancelada'
  host: string
  porta: number
  banco: string
  usuario: string
  opcoes: Opcoes
  etapa?: string
  progresso: Record<string, Etapa>
  log?: string
  versaoOrigem?: string
  iniciadoEm?: number
  terminadoEm?: number
  quem?: string
}

export const verificarImportacao = (data: {
  conexao: Conexao
  opcoes: Opcoes
}): Promise<Verificacao> => request.post({ url: '/itsm/importacao/verificar', data })
export const iniciarImportacao = (data: { conexao: Conexao; opcoes: Opcoes }): Promise<number> =>
  request.post({ url: '/itsm/importacao/iniciar', data })
export const getImportacoes = (): Promise<Importacao[]> =>
  request.get({ url: '/itsm/importacao/list' })
export const getImportacao = (id: number): Promise<Importacao> =>
  request.get({ url: '/itsm/importacao/get', params: { id } })
export const cancelarImportacao = (id: number) =>
  request.post({ url: '/itsm/importacao/cancelar', params: { id } })
