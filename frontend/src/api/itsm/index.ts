import request from '@/config/axios'

// ========== tipos ==========

export interface Ator {
  id: number
  tipo: number // 1 solicitante, 2 técnico, 3 observador
  userId?: number
  equipeId?: number
  email?: string
  nome: string
}

export interface Chamado {
  id: number
  titulo: string
  descricao?: string
  tipo: number
  tipoRotulo: string
  status: number
  statusRotulo: string
  urgencia: number
  impacto: number
  prioridade: number
  prioridadeRotulo: string
  categoriaId?: number
  categoria?: string
  origemId?: number
  origem?: string
  deptId?: number
  dept?: string
  abertoEm: number
  assumidoEm?: number
  solucionadoEm?: number
  fechadoEm?: number
  prazoAtendimento?: number
  prazoSolucao?: number
  slaVencido: boolean
  esperaSeg?: number
  tempoAssumirSeg?: number
  tempoSolucaoSeg?: number
  tempoGastoSeg?: number
  origemAutomatica?: string
  slaAtendimentoId?: number
  slaAtendimento?: string
  slaSolucaoId?: number
  slaSolucao?: string
  atendimentoVencido?: boolean
  satisfacaoPendente?: boolean
  satisfacaoNota?: number
  satisfacaoComentario?: string
  validacao?: number // 1 sem, 2 aguardando, 3 aprovado, 4 recusado
  validacaoRotulo?: string
  execucaoAdStatus?: string
  execucaoAdAcao?: string
  senhaDisponivel?: boolean
  solicitantes: Ator[]
  tecnicos: Ator[]
  observadores: Ator[]
  acoes: string[]
}

export interface Interacao {
  id: number
  tipo: 'acompanhamento' | 'tarefa' | 'solucao'
  userId?: number
  autor?: string
  conteudo: string
  privado: boolean
  criadoEm: number
  estadoTarefa?: number
  tecnicoId?: number
  tecnico?: string
  inicio?: number
  fim?: number
  tempoGastoSeg?: number
  statusSolucao?: number // 2 aguardando, 3 aprovada, 4 recusada
  avaliadoPorNome?: string
  avaliadoEm?: number
}

export interface Historico {
  id: number
  momento: number
  usuario?: string
  campo: string
  valorAntigo?: string
  valorNovo?: string
}

export interface Categoria {
  id: number
  parentId: number
  nome: string
  nomeCompleto: string
  nivel: number
  codigo?: string
  descricao?: string
  tecnicoId?: number
  equipeId?: number
  slaAtendimentoId?: number
  slaSolucaoId?: number
  visivelPortal: boolean
  paraIncidente: boolean
  paraRequisicao: boolean
  ativo: boolean
  ordem: number
}

export interface Membro {
  userId: number
  supervisor: boolean
  nome?: string
}

export interface Equipe {
  id?: number
  nome: string
  descricao?: string
  email?: string
  grupoAd?: string
  ativo: boolean
  membros: Membro[]
}

export interface Origem {
  id?: number
  nome: string
  padraoPortal: boolean
  padraoEmail: boolean
  ativo: boolean
}

// ========== rótulos ==========

export const STATUS = [
  { valor: 1, rotulo: 'Novo', cor: 'primary' },
  { valor: 2, rotulo: 'Em atendimento', cor: 'success' },
  { valor: 3, rotulo: 'Em atendimento (planejado)', cor: 'success' },
  { valor: 4, rotulo: 'Pendente', cor: 'warning' },
  { valor: 5, rotulo: 'Solucionado', cor: 'info' },
  { valor: 6, rotulo: 'Fechado', cor: 'info' }
] as const

export const ESCALA = [
  { valor: 1, rotulo: 'Muito baixa' },
  { valor: 2, rotulo: 'Baixa' },
  { valor: 3, rotulo: 'Média' },
  { valor: 4, rotulo: 'Alta' },
  { valor: 5, rotulo: 'Muito alta' }
]

export const PRIORIDADES = [...ESCALA, { valor: 6, rotulo: 'Crítica' }]

export const corStatus = (s: number) => STATUS.find((x) => x.valor === s)?.cor || 'info'
export const corPrioridade = (p: number) =>
  p >= 6 ? 'danger' : p >= 5 ? 'danger' : p >= 4 ? 'warning' : p >= 3 ? 'primary' : 'info'

/** 3h 25min, 2 dias e 4h... */
export const duracao = (seg?: number | null) => {
  if (seg === undefined || seg === null) return '—'
  seg = Math.round(seg) // médias chegam com casas decimais
  if (seg < 60) return `${seg} s`
  const min = Math.floor(seg / 60)
  if (min < 60) return `${min} min`
  const h = Math.floor(min / 60)
  if (h < 24) return `${h} h ${min % 60} min`
  const d = Math.floor(h / 24)
  return `${d} ${d === 1 ? 'dia' : 'dias'} e ${h % 24} h`
}

// ========== equipe técnica ==========

export const getChamadoPage = (params: any) => request.get({ url: '/itsm/chamado/page', params })
export const getContagens = (): Promise<Record<string, number>> =>
  request.get({ url: '/itsm/chamado/contagens' })
export const getChamado = (id: number): Promise<Chamado> =>
  request.get({ url: '/itsm/chamado/get', params: { id } })
export const getLinhaDoTempo = (id: number): Promise<Interacao[]> =>
  request.get({ url: '/itsm/chamado/linha-do-tempo', params: { id } })
export const getHistorico = (id: number): Promise<Historico[]> =>
  request.get({ url: '/itsm/chamado/historico', params: { id } })
export const criarChamado = (data: any): Promise<number> =>
  request.post({ url: '/itsm/chamado/create', data })
export const atualizarChamado = (data: any) => request.put({ url: '/itsm/chamado/update', data })
export const excluirChamado = (id: number) =>
  request.delete({ url: '/itsm/chamado/delete', params: { id } })
export const adicionarAtor = (data: {
  chamadoId: number
  tipo: number
  userId?: number
  equipeId?: number
}) => request.post({ url: '/itsm/chamado/ator', data })
export const removerAtor = (id: number) =>
  request.delete({ url: '/itsm/chamado/ator', params: { id } })
export const assumir = (id: number) =>
  request.post({ url: '/itsm/chamado/assumir', params: { id } })
export const adicionarInteracao = (data: any) =>
  request.post({ url: '/itsm/chamado/interacao', data })
export const colocarPendente = (chamadoId: number, motivo?: string) =>
  request.post({ url: '/itsm/chamado/pendente', data: { chamadoId, motivo } })
export const retomar = (id: number) =>
  request.post({ url: '/itsm/chamado/retomar', params: { id } })
export const fechar = (id: number) => request.post({ url: '/itsm/chamado/fechar', params: { id } })
export const reabrir = (id: number) =>
  request.post({ url: '/itsm/chamado/reabrir', params: { id } })
export const avaliarSolucaoTecnico = (data: {
  chamadoId: number
  aprovar: boolean
  comentario?: string
}) => request.post({ url: '/itsm/chamado/avaliar-solucao', data })

// ========== autoatendimento ==========

export const getCategoriasPortal = (): Promise<Categoria[]> =>
  request.get({ url: '/itsm/portal/categorias' })
export const abrirPortal = (data: any): Promise<number> =>
  request.post({ url: '/itsm/portal/abrir', data })
export const getMeus = (params: any) => request.get({ url: '/itsm/portal/meus', params })
export const getMeu = (id: number): Promise<Chamado> =>
  request.get({ url: '/itsm/portal/get', params: { id } })
export const getMinhaLinhaDoTempo = (id: number): Promise<Interacao[]> =>
  request.get({ url: '/itsm/portal/linha-do-tempo', params: { id } })
export const responderPortal = (chamadoId: number, conteudo: string) =>
  request.post({
    url: '/itsm/portal/acompanhamento',
    data: { chamadoId, tipo: 'acompanhamento', conteudo }
  })
export const avaliarSolucaoPortal = (data: {
  chamadoId: number
  aprovar: boolean
  comentario?: string
}) => request.post({ url: '/itsm/portal/avaliar-solucao', data })

// ========== cadastros ==========

export const getCategorias = (somenteAtivas = false): Promise<Categoria[]> =>
  request.get({ url: '/itsm/categoria/list', params: { somenteAtivas } })
export const salvarCategoria = (data: any): Promise<number> =>
  request.post({ url: '/itsm/categoria/save', data })
export const excluirCategoria = (id: number) =>
  request.delete({ url: '/itsm/categoria/delete', params: { id } })
export const getEquipes = (somenteAtivas = false): Promise<Equipe[]> =>
  request.get({ url: '/itsm/equipe/list', params: { somenteAtivas } })
export const salvarEquipe = (data: Equipe): Promise<number> =>
  request.post({ url: '/itsm/equipe/save', data })
export const excluirEquipe = (id: number) =>
  request.delete({ url: '/itsm/equipe/delete', params: { id } })
export const getOrigens = (somenteAtivas = false): Promise<Origem[]> =>
  request.get({ url: '/itsm/origem/list', params: { somenteAtivas } })
export const salvarOrigem = (data: Origem): Promise<number> =>
  request.post({ url: '/itsm/origem/save', data })
export const excluirOrigem = (id: number) =>
  request.delete({ url: '/itsm/origem/delete', params: { id } })

// ========== SLA, calendários e feriados ==========

export interface Periodo {
  diaSemana: number // 1 segunda ... 7 domingo
  inicio: string // HH:mm
  fim: string // HH:mm ou 24:00
}

export interface Calendario {
  id?: number
  nome: string
  descricao?: string
  periodos: Periodo[]
  feriadoIds: number[]
}

export interface Feriado {
  id?: number
  nome: string
  dataInicio: string
  dataFim?: string
  perpetuo: boolean
}

export interface NivelSla {
  id?: number
  nome: string
  minutos: number // em relação ao prazo; negativo = antes de vencer
  atribuirEquipeId?: number
  atribuirTecnicoId?: number
  novaPrioridade?: number
  ativo: boolean
}

export interface Sla {
  id?: number
  nome: string
  tipo: number // 1 atendimento, 0 solução
  tempo: number
  unidade: 'minuto' | 'hora' | 'dia' | 'mes'
  calendarioId?: number
  fimExpediente: boolean
  descricao?: string
  ativo: boolean
  niveis: NivelSla[]
}

export interface SlaPorPrioridade {
  prioridade: number
  slaAtendimentoId?: number
  slaSolucaoId?: number
}

export const UNIDADES = [
  { valor: 'minuto', rotulo: 'minutos' },
  { valor: 'hora', rotulo: 'horas' },
  { valor: 'dia', rotulo: 'dias' },
  { valor: 'mes', rotulo: 'meses' }
]
export const DIAS = ['', 'Segunda', 'Terça', 'Quarta', 'Quinta', 'Sexta', 'Sábado', 'Domingo']

export const getSlas = (): Promise<Sla[]> => request.get({ url: '/itsm/sla/list' })
export const salvarSla = (data: Sla): Promise<number> =>
  request.post({ url: '/itsm/sla/save', data })
export const excluirSla = (id: number) =>
  request.delete({ url: '/itsm/sla/delete', params: { id } })
export const getSlaPorPrioridade = (): Promise<SlaPorPrioridade[]> =>
  request.get({ url: '/itsm/sla/prioridade' })
export const salvarSlaPorPrioridade = (data: SlaPorPrioridade[]) =>
  request.put({ url: '/itsm/sla/prioridade', data })
export const executarAutomacao = (): Promise<{ escalonamentos: number; fechados: number }> =>
  request.post({ url: '/itsm/sla/executar-automacao' })
export const getCalendarios = (): Promise<Calendario[]> =>
  request.get({ url: '/itsm/calendario/list' })
export const salvarCalendario = (data: Calendario): Promise<number> =>
  request.post({ url: '/itsm/calendario/save', data })
export const excluirCalendario = (id: number) =>
  request.delete({ url: '/itsm/calendario/delete', params: { id } })
export const getFeriados = (): Promise<Feriado[]> => request.get({ url: '/itsm/feriado/list' })
export const salvarFeriado = (data: Feriado): Promise<number> =>
  request.post({ url: '/itsm/feriado/save', data })
export const excluirFeriado = (id: number) =>
  request.delete({ url: '/itsm/feriado/delete', params: { id } })
export const responderSatisfacao = (data: {
  chamadoId: number
  nota: number
  comentario?: string
}) => request.post({ url: '/itsm/portal/satisfacao', data })

// ========== catálogo de serviços, aprovações, regras e execuções no AD ==========

export interface Pergunta {
  name: string
  label: string
  type:
    | 'text'
    | 'textarea'
    | 'email'
    | 'number'
    | 'date'
    | 'select'
    | 'radios'
    | 'regex'
    | 'location'
    | 'usuario'
    | 'multipla'
  required: boolean
  values?: string[]
  regex?: string
  ajuda?: string
  visivelSe?: { campo: string; valores: string[] }[]
}

export interface Secao {
  nome: string
  perguntas: Pergunta[]
}

export interface Definicao {
  secoes: Secao[]
}

export interface FormularioCatalogo {
  id: number
  codigo?: string
  nome: string
  descricao?: string
  grupo: string
  icone?: string
  definicao: Definicao
  precisaAprovacao: boolean
  destaque?: boolean
}

export interface Formulario {
  id?: number
  destaque?: boolean
  codigo?: string
  nome: string
  descricao?: string
  grupo: string
  icone?: string
  categoriaId?: number
  categoria?: string
  tipo: number
  urgencia: number
  impacto: number
  tituloModelo?: string
  definicao: Definicao
  aprovacao: 'nenhuma' | 'gestor' | 'campo' | 'fixo'
  aprovadorCampo?: string
  aprovadorId?: number
  aprovador?: string
  acaoAd?: string
  ativo: boolean
  ordem: number
}

export interface Aprovacao {
  id: number
  chamadoId: number
  chamadoTitulo: string
  chamadoDescricao?: string
  solicitante?: string
  aprovadorId: number
  aprovador: string
  solicitadoPor?: string
  status: number // 2 aguardando, 3 aprovada, 4 recusada
  statusRotulo: string
  comentarioPedido?: string
  comentarioResposta?: string
  pedidoEm: number
  respondidoEm?: number
}

export interface Criterio {
  campo: string
  operador: string
  valor: string
}

export interface AcaoRegra {
  acao: string
  valor: string
}

export interface Regra {
  id?: number
  nome: string
  descricao?: string
  ordem: number
  ativo: boolean
  combinacao: 'E' | 'OU'
  parar: boolean
  criterios: Criterio[]
  acoes: AcaoRegra[]
  updateTime?: number
}

export interface Execucao {
  id: number
  chamadoId: number
  chamadoTitulo: string
  acao: string
  status: string
  tentativas: number
  proximaTentativa?: number
  mensagem?: string
  resultado?: string
  dados?: string
  senhaGuardada: boolean
  senhaVistaEm?: number
  criadoEm: number
  executadoEm?: number
}

export const ACOES_AD = [
  { valor: 'Criar', rotulo: 'Criar usuário' },
  { valor: 'Reset', rotulo: 'Resetar senha' },
  { valor: 'Reativar', rotulo: 'Reativar usuário' },
  { valor: 'Desativar', rotulo: 'Desativar usuário' },
  { valor: 'AcessoPasta', rotulo: 'Acesso a pasta' },
  { valor: 'Transferir', rotulo: 'Transferir de setor' },
  { valor: 'CriarEmail', rotulo: 'Criar e-mail' }
]

export const STATUS_EXECUCAO: Record<string, { rotulo: string; cor: string }> = {
  aguardando_aprovacao: { rotulo: 'Aguardando aprovação', cor: 'warning' },
  aguardando_confirmacao: { rotulo: 'Aguardando confirmação', cor: 'warning' },
  pronta: { rotulo: 'Na fila', cor: 'primary' },
  executando: { rotulo: 'Executando', cor: 'primary' },
  sucesso: { rotulo: 'Concluída', cor: 'success' },
  erro: { rotulo: 'Erro', cor: 'danger' },
  recusada: { rotulo: 'Recusada', cor: 'info' },
  cancelada: { rotulo: 'Cancelada', cor: 'info' }
}

export const corAprovacao = (s?: number) =>
  s === 3 ? 'success' : s === 4 ? 'danger' : s === 2 ? 'warning' : 'info'

// portal
export const getCatalogo = (): Promise<FormularioCatalogo[]> =>
  request.get({ url: '/itsm/portal/catalogo' })
export const enviarFormulario = (
  formularioId: number,
  respostas: Record<string, any>
): Promise<number> =>
  request.post({ url: '/itsm/portal/catalogo/enviar', data: { formularioId, respostas } })
export const getMinhasAprovacoes = (pendentes: boolean): Promise<Aprovacao[]> =>
  request.get({ url: '/itsm/portal/aprovacoes', params: { pendentes } })
export const responderAprovacao = (data: { id: number; aprovar: boolean; comentario?: string }) =>
  request.post({ url: '/itsm/portal/aprovacoes/responder', data })
export const getAprovacoesDoMeuChamado = (id: number): Promise<Aprovacao[]> =>
  request.get({ url: '/itsm/portal/aprovacoes-do-chamado', params: { id } })
export const revelarSenha = (id: number): Promise<string> =>
  request.post({ url: '/itsm/portal/revelar-senha', params: { id } })

// equipe técnica
export const getAprovacoesDoChamado = (id: number): Promise<Aprovacao[]> =>
  request.get({ url: '/itsm/chamado/aprovacoes', params: { id } })
/** Só quem tem o papel Aprovador (busca por nome ou login, no máximo 30) */
export const buscarAprovadores = (nome: string): Promise<{ id: number; nickname: string }[]> =>
  request.get({ url: '/itsm/chamado/aprovadores', params: { nome } })
export const pedirAprovacao = (data: {
  chamadoId: number
  aprovadorIds: number[]
  comentario?: string
}) => request.post({ url: '/itsm/chamado/pedir-aprovacao', data })

// configuração
export const getFormularios = (): Promise<Formulario[]> =>
  request.get({ url: '/itsm/formulario/list' })
export const salvarFormulario = (data: Formulario): Promise<number> =>
  request.post({ url: '/itsm/formulario/save', data })
export const excluirFormulario = (id: number) =>
  request.delete({ url: '/itsm/formulario/delete', params: { id } })
export const importarFormularios = (arquivo: File, criarCategorias: boolean) =>
  request.upload({
    url: '/itsm/formulario/importar',
    params: { criarCategorias },
    data: { arquivo }
  })
export const getRegras = (): Promise<Regra[]> => request.get({ url: '/itsm/regra/list' })
export const getOpcoesRegra = (): Promise<{
  campos: string[]
  operadores: string[]
  acoes: string[]
}> => request.get({ url: '/itsm/regra/opcoes' })
export const salvarRegra = (data: Regra): Promise<number> =>
  request.post({ url: '/itsm/regra/save', data })
export const excluirRegra = (id: number) =>
  request.delete({ url: '/itsm/regra/delete', params: { id } })
export const testarRegras = (
  fatos: Record<string, any>
): Promise<{ regraId: number; regraNome: string; acao: AcaoRegra }[]> =>
  request.post({ url: '/itsm/regra/testar', data: { fatos } })
export const getExecucoes = (params: any) => request.get({ url: '/itsm/execucao/page', params })
export const getModoAd = (): Promise<string> => request.get({ url: '/itsm/execucao/modo' })
export const confirmarExecucao = (id: number) =>
  request.post({ url: '/itsm/execucao/confirmar', params: { id } })
export const repetirExecucao = (id: number) =>
  request.post({ url: '/itsm/execucao/repetir', params: { id } })
export const cancelarExecucao = (id: number, motivo?: string) =>
  request.post({ url: '/itsm/execucao/cancelar', data: { id, motivo } })

// ========== base de conhecimento ==========

export interface KbCategoria {
  id: number
  parentId: number
  nome: string
  nomeCompleto: string
  descricao?: string
  ordem: number
}

export interface KbResumo {
  id: number
  titulo: string
  resumo?: string
  categoriaId?: number
  categoria?: string
  faq: boolean
  publicado: boolean
  fixado: boolean
  visualizacoes: number
  util: number
  naoUtil: number
  updateTime: number
  trecho?: string // HTML escapado no servidor, com <mark>
}

export interface KbArtigo {
  id?: number
  titulo: string
  resumo?: string
  conteudo: string
  categoriaId?: number
  categoria?: string
  faq: boolean
  publicado: boolean
  fixado: boolean
  inicioPublicacao?: number | string
  fimPublicacao?: number | string
  autor?: string
  revisao?: number
  visualizacoes?: number
  util?: number
  naoUtil?: number
  updateTime?: number
  meuVoto?: boolean | null
  podeEditar?: boolean
  chamados?: number[]
}

export interface KbRevisao {
  id: number
  revisao: number
  titulo: string
  conteudo: string
  autor?: string
  criadoEm: number
}

export const getKbCategorias = (): Promise<KbCategoria[]> =>
  request.get({ url: '/itsm/kb/categorias' })
export const salvarKbCategoria = (data: Partial<KbCategoria>): Promise<number> =>
  request.post({ url: '/itsm/kb/categoria/save', data })
export const excluirKbCategoria = (id: number) =>
  request.delete({ url: '/itsm/kb/categoria/delete', params: { id } })
export const buscarKb = (
  params: any,
  gestao = false
): Promise<{ list: KbResumo[]; total: number }> =>
  request.get({ url: '/itsm/kb/buscar', params: { ...params, gestao } })
export const sugestoesKb = (texto: string): Promise<KbResumo[]> =>
  request.get({ url: '/itsm/kb/sugestoes', params: { texto } })
export const lerKb = (id: number, contar = true): Promise<KbArtigo> =>
  request.get({ url: '/itsm/kb/get', params: { id, contar } })
export const votarKb = (artigoId: number, util: boolean) =>
  request.post({ url: '/itsm/kb/votar', data: { artigoId, util } })
export const salvarKb = (data: KbArtigo): Promise<number> =>
  request.post({ url: '/itsm/kb/save', data })
export const excluirKb = (id: number) => request.delete({ url: '/itsm/kb/delete', params: { id } })
export const revisoesKb = (id: number): Promise<KbRevisao[]> =>
  request.get({ url: '/itsm/kb/revisoes', params: { id } })
export const restaurarKb = (revisaoId: number) =>
  request.post({ url: '/itsm/kb/restaurar', params: { revisaoId } })
export const usarKbNoChamado = (data: {
  artigoId: number
  chamadoId: number
  comoSolucao: boolean
}) => request.post({ url: '/itsm/kb/usar-no-chamado', data })
export const criarKbDoChamado = (chamadoId: number): Promise<number> =>
  request.post({ url: '/itsm/kb/criar-do-chamado', params: { chamadoId } })
export const kbDoChamado = (id: number): Promise<KbResumo[]> =>
  request.get({ url: '/itsm/kb/do-chamado', params: { id } })

// ========== e-mail ==========

export interface EmailConfig {
  ativo: boolean
  smtpHost?: string
  smtpPorta: number
  smtpSeguranca: 'nenhuma' | 'starttls' | 'ssl'
  smtpUsuario?: string
  smtpSenha?: string
  smtpSenhaDefinida?: boolean
  remetenteEmail?: string
  remetenteNome?: string
  responderPara?: string
  urlPortal?: string
  assinatura?: string
}

export interface EmailEvento {
  evento: string
  nome: string
  ativo: boolean
  paraSolicitante: boolean
  paraObservador: boolean
  paraTecnico: boolean
  paraAprovador: boolean
  assunto: string
  corpo: string
  ordem: number
  destinatariosFixos?: string
  variaveis?: string
}

export interface EmailCaixa {
  id?: number
  nome: string
  ativo: boolean
  host: string
  porta: number
  seguranca: 'nenhuma' | 'starttls' | 'ssl'
  usuario: string
  senha?: string
  senhaDefinida?: boolean
  pasta: string
  pastaProcessados?: string
  pastaRecusados?: string
  aceitarDesconhecidos: boolean
  exigirAutenticacao: boolean
  categoriaId?: number
  anexoMaxMb: number
  ultimaColeta?: number
  ultimoErro?: string
}

export const getEmailConfig = (): Promise<EmailConfig> => request.get({ url: '/itsm/email/config' })
export const salvarEmailConfig = (data: EmailConfig) =>
  request.put({ url: '/itsm/email/config', data })
export const testarEnvioEmail = (para: string): Promise<{ ok: boolean; erro?: string }> =>
  request.post({ url: '/itsm/email/testar-envio', data: { para } })
export const getEmailEventos = (): Promise<EmailEvento[]> =>
  request.get({ url: '/itsm/email/eventos' })
export const salvarEmailEvento = (data: EmailEvento) =>
  request.put({ url: '/itsm/email/evento', data })
export const getEmailFila = (params: any) => request.get({ url: '/itsm/email/fila', params })
export const reenviarEmail = (id: number) =>
  request.post({ url: '/itsm/email/reenviar', params: { id } })
export const getEmailCaixas = (): Promise<EmailCaixa[]> =>
  request.get({ url: '/itsm/email/caixas' })
export const salvarEmailCaixa = (data: EmailCaixa): Promise<number> =>
  request.post({ url: '/itsm/email/caixa', data })
export const excluirEmailCaixa = (id: number) =>
  request.delete({ url: '/itsm/email/caixa', params: { id } })
export const testarEmailCaixa = (id: number): Promise<{ ok: boolean; erro?: string }> =>
  request.post({ url: '/itsm/email/caixa/testar', params: { id } })
export const coletarEmailCaixa = (id: number): Promise<number> =>
  request.post({ url: '/itsm/email/caixa/coletar', params: { id } })
export const getEmailRecebidos = (params: any) =>
  request.get({ url: '/itsm/email/recebidos', params })

// ========== painel ==========

export const getPainel = (params: {
  de: string
  ate: string
  deptId?: number
  categoriaId?: number
}): Promise<any> => request.get({ url: '/itsm/painel', params })

export const exportarPainel = (params: {
  de: string
  ate: string
  deptId?: number
  categoriaId?: number
}): Promise<Blob> => request.download({ url: '/itsm/painel/exportar-excel', params })

export const exportarChamados = (params: any): Promise<Blob> =>
  request.download({ url: '/itsm/chamado/exportar-excel', params })

// ========== problemas e mudanças ==========

export type ObjetoItil = 'problema' | 'mudanca'

export interface ItemItil {
  objeto: ObjetoItil
  id: number
  titulo: string
  descricao?: string
  status: number
  statusRotulo: string
  urgencia: number
  impacto: number
  prioridade: number
  prioridadeRotulo: string
  categoriaId?: number
  categoria?: string
  deptId?: number
  dept?: string
  abertoEm: number
  prazo?: number
  atrasado?: boolean
  solucionadoEm?: number
  fechadoEm?: number
  tempoGastoSeg?: number
  registradoPorNome?: string
  solicitantes: Ator[]
  tecnicos: Ator[]
  observadores: Ator[]
  totalChamados: number
  // problema
  impactos?: string
  causas?: string
  sintomas?: string
  // mudança
  tipo?: number
  tipoRotulo?: string
  inicioPrevisto?: number
  fimPrevisto?: number
  analiseImpacto?: string
  planoControle?: string
  planoImplantacao?: string
  planoRetorno?: string
  checklist?: string
  validacao?: number
  validacaoRotulo?: string
  conflitos?: VinculoItil[]
  acoes: string[]
}

export interface VinculoItil {
  id: number
  objeto: 'chamado' | ObjetoItil
  objetoId: number
  titulo?: string
  status?: number
  statusRotulo?: string
  inicioPrevisto?: number
  fimPrevisto?: number
}

export interface AprovacaoMudanca {
  id: number
  mudancaId: number
  mudancaTitulo?: string
  aprovadorId: number
  aprovador: string
  solicitadoPorNome?: string
  status: number // 2 aguardando, 3 aprovada, 4 recusada
  comentarioPedido?: string
  comentarioResposta?: string
  pedidoEm: number
  respondidoEm?: number
  tipo?: number
  tipoRotulo?: string
  inicioPrevisto?: number
  fimPrevisto?: number
  analiseImpacto?: string
  planoRetorno?: string
}

// códigos do GLPI, na ordem do ciclo; "manual" = o técnico escolhe na tela (as outras vêm de ações)
export const STATUS_PROBLEMA = [
  { valor: 1, rotulo: 'Novo', cor: 'primary', manual: true },
  { valor: 7, rotulo: 'Aceito', cor: 'primary', manual: true },
  { valor: 2, rotulo: 'Em análise', cor: 'success', manual: true },
  { valor: 3, rotulo: 'Em análise (planejado)', cor: 'success', manual: true },
  { valor: 4, rotulo: 'Pendente', cor: 'warning', manual: true },
  { valor: 5, rotulo: 'Solucionado', cor: 'info', manual: false },
  { valor: 8, rotulo: 'Em observação', cor: 'info', manual: true },
  { valor: 6, rotulo: 'Fechado', cor: 'info', manual: false }
]

export const STATUS_MUDANCA = [
  { valor: 1, rotulo: 'Nova', cor: 'primary', manual: true },
  { valor: 9, rotulo: 'Em avaliação', cor: 'primary', manual: true },
  { valor: 10, rotulo: 'Em aprovação', cor: 'warning', manual: false },
  { valor: 7, rotulo: 'Aceita', cor: 'success', manual: true },
  { valor: 4, rotulo: 'Pendente', cor: 'warning', manual: true },
  { valor: 11, rotulo: 'Em teste', cor: 'success', manual: true },
  { valor: 12, rotulo: 'Em qualificação', cor: 'success', manual: true },
  { valor: 5, rotulo: 'Aplicada', cor: 'info', manual: false },
  { valor: 8, rotulo: 'Em revisão', cor: 'info', manual: true },
  { valor: 6, rotulo: 'Fechada', cor: 'info', manual: false },
  { valor: 13, rotulo: 'Recusada', cor: 'danger', manual: false },
  { valor: 14, rotulo: 'Cancelada', cor: 'info', manual: false }
]

export const TIPOS_MUDANCA = [
  { valor: 1, rotulo: 'Padrão', dica: 'Rotineira, pré-aprovada' },
  { valor: 2, rotulo: 'Normal', dica: 'Precisa de aprovação antes de testar ou aplicar' },
  { valor: 3, rotulo: 'Emergencial', dica: 'Feita já; a aprovação pode vir depois' }
]

export const statusItil = (objeto: ObjetoItil) =>
  objeto === 'problema' ? STATUS_PROBLEMA : STATUS_MUDANCA
export const corStatusItil = (objeto: ObjetoItil, s: number) =>
  (statusItil(objeto).find((x) => x.valor === s)?.cor || 'info') as
    | 'primary'
    | 'success'
    | 'warning'
    | 'danger'
    | 'info'

const u = (objeto: ObjetoItil, acao: string) => `/itsm/${objeto}/${acao}`

export const getItilPage = (objeto: ObjetoItil, params: any) =>
  request.get({ url: u(objeto, 'page'), params })
export const getItil = (objeto: ObjetoItil, id: number): Promise<ItemItil> =>
  request.get({ url: u(objeto, 'get'), params: { id } })
export const getItilLinha = (objeto: ObjetoItil, id: number): Promise<Interacao[]> =>
  request.get({ url: u(objeto, 'linha-do-tempo'), params: { id } })
export const getItilHistorico = (objeto: ObjetoItil, id: number): Promise<Historico[]> =>
  request.get({ url: u(objeto, 'historico'), params: { id } })
export const getItilVinculos = (objeto: ObjetoItil, id: number): Promise<VinculoItil[]> =>
  request.get({ url: u(objeto, 'vinculos'), params: { id } })
export const criarItil = (objeto: ObjetoItil, data: any): Promise<number> =>
  request.post({ url: u(objeto, 'create'), data })
export const criarItilDoChamado = (objeto: ObjetoItil, chamadoId: number): Promise<number> =>
  request.post({ url: u(objeto, 'do-chamado'), params: { chamadoId } })
export const atualizarItil = (objeto: ObjetoItil, data: any) =>
  request.put({ url: u(objeto, 'update'), data })
export const excluirItil = (objeto: ObjetoItil, id: number) =>
  request.delete({ url: u(objeto, 'delete'), params: { id } })
export const adicionarAtorItil = (
  objeto: ObjetoItil,
  data: { objetoId: number; tipo: number; userId?: number; equipeId?: number }
) => request.post({ url: u(objeto, 'ator'), data })
export const removerAtorItil = (objeto: ObjetoItil, id: number) =>
  request.delete({ url: u(objeto, 'ator'), params: { id } })
export const assumirItil = (objeto: ObjetoItil, id: number) =>
  request.post({ url: u(objeto, 'assumir'), params: { id } })
export const interacaoItil = (objeto: ObjetoItil, data: any) =>
  request.post({ url: u(objeto, 'interacao'), data })
export const statusItilMudar = (objeto: ObjetoItil, id: number, status: number) =>
  request.post({ url: u(objeto, 'status'), data: { id, status } })
export const fecharItil = (objeto: ObjetoItil, id: number) =>
  request.post({ url: u(objeto, 'fechar'), params: { id } })
export const reabrirItil = (objeto: ObjetoItil, id: number) =>
  request.post({ url: u(objeto, 'reabrir'), params: { id } })
export const vincularItil = (
  objeto: ObjetoItil,
  data: { objetoId: number; alvo: 'chamado' | 'problema'; alvoId: number }
) => request.post({ url: u(objeto, 'vinculo'), data })
export const desvincularItil = (objeto: ObjetoItil, id: number) =>
  request.delete({ url: u(objeto, 'vinculo'), params: { id } })
export const cancelarMudanca = (id: number, motivo?: string) =>
  request.post({ url: '/itsm/mudanca/cancelar', params: { id, motivo } })
export const pedirAprovacaoMudanca = (data: {
  mudancaId: number
  aprovadorIds: number[]
  comentario?: string
}) => request.post({ url: '/itsm/mudanca/pedir-aprovacao', data })
export const getAprovacoesMudanca = (id: number): Promise<AprovacaoMudanca[]> =>
  request.get({ url: '/itsm/mudanca/aprovacoes', params: { id } })
export const getMinhasAprovacoesMudanca = (pendentes: boolean): Promise<AprovacaoMudanca[]> =>
  request.get({ url: '/itsm/mudanca/minhas-aprovacoes', params: { pendentes } })
export const responderAprovacaoMudanca = (data: {
  aprovacaoId: number
  aprovar: boolean
  comentario?: string
}) => request.post({ url: '/itsm/mudanca/responder-aprovacao', data })
export const getItilDoChamado = (chamadoId: number): Promise<VinculoItil[]> =>
  request.get({ url: '/itsm/itil/do-chamado', params: { chamadoId } })
