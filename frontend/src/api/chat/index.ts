import request from '@/config/axios'

export interface Pessoa {
  id: number
  nome: string
  unidade?: string
  avatar?: string
  online?: boolean
}

export interface Membro {
  id: number
  nome: string
  avatar?: string
  papel: 'admin' | 'membro'
  online?: boolean
}

export interface Conversa {
  id: number
  tipo: 'direta' | 'grupo'
  nome: string
  avatar?: string
  outroId?: number
  online?: boolean
  papel: 'admin' | 'membro'
  naoLidas: number
  ultimaSeq: number
  ultimaLidaSeq: number
  desdeSeq: number
  ultimoTexto?: string
  ultimaMensagemEm?: number
  membros?: Membro[]
}

export interface Anexo {
  id: number
  nome: string
  tamanho: number
  imagem: boolean
}

export interface Mensagem {
  id: number
  conversaId: number
  seq: number
  remetenteId: number
  remetente: string
  avatar?: string
  tipo: 'texto' | 'sistema'
  conteudo?: string
  apagada: boolean
  editada: boolean
  clienteId?: string
  resposta?: { id: number; remetente: string; trecho: string }
  mencoes: Pessoa[]
  anexos: Anexo[]
  criadaEm: number
}

export interface Achado {
  mensagem: Mensagem
  conversa: string
}

export const getPessoas = (busca: string): Promise<Pessoa[]> =>
  request.get({ url: '/chat/pessoas', params: { busca } })
export const presenca = () => request.post({ url: '/chat/presenca' })
export const getConversas = (): Promise<Conversa[]> => request.get({ url: '/chat/conversas' })
export const getConversa = (id: number): Promise<Conversa> =>
  request.get({ url: '/chat/conversa', params: { id } })
export const getNaoLidas = (): Promise<number> => request.get({ url: '/chat/nao-lidas' })
export const abrirDireta = (userId: number): Promise<number> =>
  request.post({ url: '/chat/direta', data: { userId } })
export const criarGrupo = (data: { nome: string; membros: number[] }): Promise<number> =>
  request.post({ url: '/chat/grupo', data })
export const renomearGrupo = (data: { conversaId: number; nome: string }) =>
  request.post({ url: '/chat/grupo/renomear', data })
export const adicionarMembros = (data: { conversaId: number; userIds: number[] }) =>
  request.post({ url: '/chat/grupo/adicionar', data })
export const removerMembro = (conversaId: number, userId: number) =>
  request.delete({ url: '/chat/grupo/remover', params: { conversaId, userId } })
export const sairDoGrupo = (conversaId: number) =>
  request.post({ url: '/chat/grupo/sair', params: { conversaId } })
export const getMensagens = (params: {
  conversaId: number
  antesDe?: number
  depoisDe?: number
  limite?: number
}): Promise<Mensagem[]> => request.get({ url: '/chat/mensagens', params })
export const enviar = (data: {
  conversaId: number
  clienteId: string
  conteudo: string
  respostaId?: number
  mencoes: number[]
  anexoIds: number[]
}): Promise<Mensagem> => request.post({ url: '/chat/enviar', data })
export const editar = (data: { mensagemId: number; conteudo: string; mencoes: number[] }) =>
  request.post({ url: '/chat/editar', data })
export const apagar = (mensagemId: number) =>
  request.delete({ url: '/chat/apagar', params: { mensagemId } })
export const marcarLida = (conversaId: number, seq: number) =>
  request.post({ url: '/chat/lida', data: { conversaId, seq } })
export const buscar = (texto: string, conversaId?: number): Promise<Achado[]> =>
  request.get({ url: '/chat/buscar', params: { texto, conversaId } })
export const anexar = (conversaId: number, arquivo: File): Promise<Anexo> => {
  const data = new FormData()
  data.append('conversaId', String(conversaId))
  data.append('arquivo', arquivo)
  return request.upload({ url: '/chat/anexo', data })
}
export const baixarAnexo = (id: number): Promise<Blob> =>
  request.download({ url: '/chat/anexo/baixar', params: { id } })
