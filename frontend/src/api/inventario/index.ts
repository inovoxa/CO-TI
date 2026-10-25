import request from '@/config/axios'

export interface Ativo {
  id: number
  tipo: string
  nome: string
  situacao: string
  patrimonio?: string
  numeroSerie?: string
  fabricante?: string
  modelo?: string
  soNome?: string
  soVersao?: string
  soArquitetura?: string
  soSemSuporte?: boolean
  cpu?: string
  cpuNucleos?: number
  memoriaMb?: number
  discoTotalMb?: number
  discoLivreMb?: number
  discoTipo?: string
  ip?: string
  mac?: string
  dominio?: string
  usuarioLogin?: string
  usuarioId?: number
  usuarioNome?: string
  deptId?: number
  deptNome?: string
  deptManual?: boolean
  antivirus: string
  origem: string
  agente?: string
  primeiroInventario?: string
  ultimoInventario?: string
  ultimoContato?: string
  semContato?: boolean
  observacao?: string
  paginas?: number
  localizacao?: string
}

export interface Componente {
  id: number
  tipo: string
  descricao: string
  dados: Record<string, any>
}

export interface SoftwareInstalado {
  id: number
  softwareId: number
  nome: string
  editora?: string
  versao: string
  instaladoEm?: string
  arquitetura?: string
}

export interface ChamadoVinculado {
  id: number
  chamadoId: number
  titulo: string
  status: number
  abertoEm: string
}

export interface Detalhe {
  ativo: Ativo
  componentes: Componente[]
  softwares: SoftwareInstalado[]
  chamados: ChamadoVinculado[]
  alertas: Alerta[]
}

export interface Historico {
  id: number
  quando: string
  tipo: string
  campo?: string
  antes?: string
  depois?: string
  usuario: string
}

export interface Software {
  id: number
  nome: string
  editora?: string
  instalacoes: number
  versoes: number
  licencas?: string[]
}

export interface PainelAgentes {
  endereco: string
  redes: string[]
  comSenha: boolean
  prologoHoras: number
  inventarioDias: number
  semContatoDias: number
  porOrigem: Record<string, number>
  versoes: { origem: string; agente: string; total: number }[]
  semContato: number
  ultimas24h: number
  recentes: Ativo[]
}

export const TIPOS_ATIVO = [
  { valor: 'desktop', rotulo: 'Desktop', icone: 'ep:monitor' },
  { valor: 'notebook', rotulo: 'Notebook', icone: 'ep:notebook' },
  { valor: 'servidor', rotulo: 'Servidor', icone: 'ep:coin' },
  { valor: 'virtual', rotulo: 'Máquina virtual', icone: 'ep:copy-document' },
  { valor: 'impressora', rotulo: 'Impressora', icone: 'ep:printer' },
  { valor: 'monitor', rotulo: 'Monitor', icone: 'ep:monitor' },
  { valor: 'rede', rotulo: 'Equipamento de rede', icone: 'ep:connection' },
  { valor: 'telefone', rotulo: 'Telefone', icone: 'ep:phone' },
  { valor: 'outro', rotulo: 'Outro', icone: 'ep:box' }
]

export const SITUACOES_ATIVO = [
  { valor: 'em_uso', rotulo: 'Em uso', cor: 'success' },
  { valor: 'estoque', rotulo: 'Estoque', cor: 'info' },
  { valor: 'manutencao', rotulo: 'Manutenção', cor: 'warning' },
  { valor: 'baixado', rotulo: 'Baixado', cor: 'danger' }
]

export const ANTIVIRUS = [
  { valor: 'ativo', rotulo: 'Ativo', cor: 'success' },
  { valor: 'desatualizado', rotulo: 'Desatualizado', cor: 'warning' },
  { valor: 'ausente', rotulo: 'Ausente', cor: 'danger' },
  { valor: 'desconhecido', rotulo: 'Desconhecido', cor: 'info' }
]

/** Tipo de disco, classificado pelo modelo de cada disco que o agente informou */
export const DISCOS = [
  { valor: 'com_ssd', rotulo: 'Com SSD (SSD ou misto)', cor: 'success', filtro: true },
  { valor: 'ssd', rotulo: 'SSD', cor: 'success' },
  { valor: 'hdd', rotulo: 'HD', cor: 'warning' },
  { valor: 'misto', rotulo: 'SSD + HD', cor: 'primary' },
  { valor: 'desconhecido', rotulo: 'Não identificado', cor: 'info' }
]

export const ORIGENS = {
  ocs: 'Agente OCS',
  glpi_agent: 'GLPI Agent',
  snmp: 'SNMP',
  manual: 'Cadastro manual'
} as Record<string, string>

export const COMPONENTES = {
  processador: 'Processadores',
  memoria: 'Memórias',
  disco: 'Discos',
  volume: 'Volumes',
  rede: 'Placas de rede',
  video: 'Vídeo',
  monitor: 'Monitores',
  impressora: 'Impressoras',
  controlador: 'Controladoras',
  usb: 'USB',
  bateria: 'Baterias',
  som: 'Som',
  entrada: 'Teclado e mouse',
  slot: 'Slots',
  suprimento: 'Suprimentos'
} as Record<string, string>

export const rotulo = (lista: { valor: string; rotulo: string }[], v?: string) =>
  lista.find((x) => x.valor === v)?.rotulo ?? v ?? '—'
export const cor = (lista: { valor: string; cor: string }[], v?: string): any =>
  lista.find((x) => x.valor === v)?.cor ?? 'info'
export const gb = (mb?: number | null) =>
  mb === undefined || mb === null
    ? '—'
    : `${(mb / 1024).toLocaleString('pt-BR', { maximumFractionDigits: 1 })} GB`

export const getAtivos = (params: any) => request.get({ url: '/inventario/ativo/page', params })
export interface Opcoes {
  sistemas: { valor: string; total: number }[]
  fabricantes: { valor: string; total: number }[]
  modelos: { valor: string; total: number }[]
}
export const getOpcoes = (): Promise<Opcoes> => request.get({ url: '/inventario/ativo/opcoes' })
export const getContagens = (): Promise<Record<string, number>> =>
  request.get({ url: '/inventario/ativo/contagens' })
export const getAtivo = (id: number): Promise<Detalhe> =>
  request.get({ url: '/inventario/ativo/get', params: { id } })
export const getHistorico = (params: { id: number; pageNo: number; pageSize: number }) =>
  request.get({ url: '/inventario/ativo/historico', params })
export const getAtivosDoUsuario = (usuarioId: number): Promise<Ativo[]> =>
  request.get({ url: '/inventario/ativo/do-usuario', params: { usuarioId } })
export const criarAtivo = (data: any): Promise<number> =>
  request.post({ url: '/inventario/ativo/create', data })
export const atualizarAtivo = (data: any) => request.put({ url: '/inventario/ativo/update', data })
export const getSoftwares = (params: any) =>
  request.get({ url: '/inventario/ativo/software/page', params })
export const getPainelAgentes = (): Promise<PainelAgentes> =>
  request.get({ url: '/inventario/ativo/agentes' })

export const getAtivosDoChamado = (chamadoId: number): Promise<Ativo[]> =>
  request.get({ url: '/inventario/ativo/do-chamado', params: { chamadoId } })
export const vincularAtivo = (chamadoId: number, ativoId: number) =>
  request.post({ url: '/inventario/ativo/vincular', data: { chamadoId, ativoId } })
export const desvincularAtivo = (chamadoId: number, ativoId: number) =>
  request.post({ url: '/inventario/ativo/desvincular', data: { chamadoId, ativoId } })

// ========== alertas ==========

export interface RegraAlerta {
  id: number
  codigo: string
  nome: string
  ativo: boolean
  abrirChamado: boolean
  limite?: number
  categoriaId?: number
  urgencia: number
  abertos: number
}

export interface Alerta {
  id: number
  regra: string
  regraNome: string
  objeto: 'ativo' | 'licenca' | 'rede'
  objetoId: number
  titulo: string
  detalhe?: string
  deptNome?: string
  inicio: string
  fim?: string
  chamadoId?: number
}

export const getRegrasAlerta = (): Promise<RegraAlerta[]> =>
  request.get({ url: '/inventario/alerta/regras' })
export const salvarRegraAlerta = (data: RegraAlerta) =>
  request.put({ url: '/inventario/alerta/regra', data })
export const getAlertas = (params: any) => request.get({ url: '/inventario/alerta/page', params })
export const avaliarAlertas = (): Promise<{
  abertos: number
  encerrados: number
  chamados: number
}> => request.post({ url: '/inventario/alerta/avaliar' })

// ========== licenças × softwares ==========

export interface Conformidade {
  licencaId: number
  quantidade: number
  emUso: number
  instalacoes: number
  excedida: boolean
  softwares: Software[]
  ativos: Ativo[]
}

export const getConformidade = (licencaId: number): Promise<Conformidade> =>
  request.get({ url: '/inventario/licenca/conformidade', params: { licencaId } })
export const getInstalacoesLicencas = (): Promise<Record<number, number>> =>
  request.get({ url: '/inventario/licenca/instalacoes' })
export const vincularSoftwareLicenca = (licencaId: number, softwareId: number) =>
  request.post({ url: '/inventario/licenca/software/vincular', data: { licencaId, softwareId } })
export const desvincularSoftwareLicenca = (licencaId: number, softwareId: number) =>
  request.post({ url: '/inventario/licenca/software/desvincular', data: { licencaId, softwareId } })

// ========== descoberta de rede ==========

export interface Subrede {
  subrede: string
  mascara?: string
  dispositivos: number
  desconhecidos: number
  varredores: string[]
  ultimaVarredura?: string
}

export interface DispositivoRede {
  id: number
  mac: string
  ip: string
  nome?: string
  subrede?: string
  ativoId?: number
  ativoNome?: string
  ativoTipo?: string
  vistoPor?: string
  ignorado: boolean
  observacao?: string
  primeiraVez: string
  ultimaVez: string
}

export const getSubredes = (): Promise<Subrede[]> =>
  request.get({ url: '/inventario/rede/subredes' })
export const getDispositivosRede = (params: any) =>
  request.get({ url: '/inventario/rede/page', params })
export const cadastrarDispositivoRede = (data: any): Promise<number> =>
  request.post({ url: '/inventario/rede/cadastrar', data })
export const ignorarDispositivoRede = (id: number, ignorado: boolean, observacao?: string) =>
  request.post({ url: '/inventario/rede/ignorar', data: { id, ignorado, observacao } })

// ========== SNMP ==========

export interface CredencialSnmp {
  id?: number
  nome: string
  versao: 'v2c' | 'v3'
  comunidade?: string
  usuario?: string
  nivel?: string
  authProtocolo?: string
  authSenha?: string
  privProtocolo?: string
  privSenha?: string
  porta: number
  ordem: number
  ativo: boolean
  comunidadeDefinida?: boolean
  authSenhaDefinida?: boolean
  privSenhaDefinida?: boolean
}

export interface ResultadoSnmp {
  ip: string
  respondeu: boolean
  credencial?: string
  nome?: string
  tipo?: string
  fabricante?: string
  modelo?: string
  numeroSerie?: string
  descricao?: string
  paginas?: number
  suprimentos?: string[]
  interfaces: number
  ativoId?: number
  erro?: string
}

export interface AlvoSnmp {
  ip: string
  credencial?: string
  ativoId?: number
  ativoNome?: string
  ativoTipo?: string
  ultimaTentativa?: string
  ultimaResposta?: string
  erro?: string
}

export const getCredenciaisSnmp = (): Promise<CredencialSnmp[]> =>
  request.get({ url: '/inventario/snmp/credenciais' })
export const salvarCredencialSnmp = (data: CredencialSnmp): Promise<number> =>
  request.post({ url: '/inventario/snmp/credencial', data })
export const excluirCredencialSnmp = (id: number) =>
  request.delete({ url: '/inventario/snmp/credencial', params: { id } })
export const getAlvosSnmp = (): Promise<AlvoSnmp[]> =>
  request.get({ url: '/inventario/snmp/alvos' })
export const consultarSnmp = (ip: string): Promise<ResultadoSnmp> =>
  request.post({ url: '/inventario/snmp/consultar', params: { ip }, timeout: 120000 })
export const varrerSnmp = (): Promise<{ alvos: number; responderam: number; gravados: number }> =>
  request.post({ url: '/inventario/snmp/varrer', timeout: 600000 })

// ========== pacotes (teledeploy) ==========

export interface Pacote {
  id?: number
  nome: string
  descricao?: string
  plataforma: string
  acao: 'EXECUTE' | 'STORE' | 'LAUNCH'
  comando?: string
  caminho?: string
  prioridade: number
  notificar: boolean
  notificarTexto?: string
  ativo: boolean
  fileid?: string
  arquivoNome?: string
  tamanho?: number
  sha256?: string
  fragmentos?: number
  contagens?: Record<string, number>
}

export interface Implantacao {
  id: number
  pacoteId: number
  pacoteNome?: string
  ativoId: number
  ativoNome: string
  situacao: string
  codigo?: string
  pedidoEm: string
  notificadoEm?: string
  concluidoEm?: string
}

export const getPacotes = (): Promise<Pacote[]> => request.get({ url: '/inventario/pacote/list' })
export const salvarPacote = (data: Pacote): Promise<number> =>
  request.post({ url: '/inventario/pacote/save', data })
/** Corpo bruto (sem multipart): pacotes passam do limite de upload comum */
export const enviarArquivoPacote = (id: number, arquivo: File, progresso?: (p: number) => void) =>
  request.put({
    url: '/inventario/pacote/arquivo',
    params: { id, nome: arquivo.name },
    data: arquivo,
    headersType: 'application/octet-stream',
    timeout: 900000,
    onUploadProgress: (e: any) =>
      progresso && e.total && progresso(Math.round((e.loaded * 100) / e.total))
  })
export const implantarPacote = (data: {
  pacoteId: number
  ativoIds?: number[]
  deptId?: number
}): Promise<{ pedidos: number; jaPendentes: number; ignorados: number }> =>
  request.post({ url: '/inventario/pacote/implantar', data })
export const cancelarImplantacao = (id: number) =>
  request.post({ url: '/inventario/pacote/cancelar', params: { id } })
export const getImplantacoes = (params: any) =>
  request.get({ url: '/inventario/pacote/implantacoes', params })
