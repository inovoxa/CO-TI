import request from '@/config/axios'

export interface Pasta {
  id: number
  nome: string
  descricao?: string
  /** 1 vê, 2 edita, 3 gerencia; vazio = não é membro (só para quem administra o cofre) */
  meuPapel?: number
  totalSegredos?: number
  podeGerenciar: boolean
}

export interface Resumo {
  pastas: Pasta[]
  podeAdministrar: boolean
  podeRevelar: boolean
}

export interface Membro {
  userId: number
  nome: string
  papel: number
}

export interface Segredo {
  id: number
  pastaId: number
  pasta: string
  nome: string
  usuario?: string
  url?: string
  temNotas: boolean
  senhaAlteradaEm: string
  podeEditar: boolean
}

export interface SegredoReq {
  id?: number
  pastaId?: number
  nome: string
  usuario?: string
  url?: string
  senha?: string
  notas?: string
}

export const PAPEIS = [
  { valor: 1, rotulo: 'Vê as senhas' },
  { valor: 2, rotulo: 'Vê e edita' },
  { valor: 3, rotulo: 'Vê, edita e gerencia' }
]

export const getResumo = (): Promise<Resumo> => request.get({ url: '/cofre/resumo' })
export const salvarPasta = (data: { id?: number; nome: string; descricao?: string }) =>
  request.post({ url: '/cofre/pasta/salvar', data })
export const excluirPasta = (id: number) =>
  request.delete({ url: '/cofre/pasta/excluir', params: { id } })
export const getMembros = (pastaId: number): Promise<Membro[]> =>
  request.get({ url: '/cofre/pasta/membros', params: { pastaId } })
export const salvarMembro = (data: { pastaId: number; userId: number; papel: number }) =>
  request.post({ url: '/cofre/pasta/membro', data })
export const removerMembro = (pastaId: number, userId: number) =>
  request.delete({ url: '/cofre/pasta/membro', params: { pastaId, userId } })
export const getSegredos = (params: { pastaId?: number; busca?: string }): Promise<Segredo[]> =>
  request.get({ url: '/cofre/segredo/lista', params })
export const salvarSegredo = (data: SegredoReq) => request.post({ url: '/cofre/segredo/salvar', data })
export const excluirSegredo = (id: number) =>
  request.delete({ url: '/cofre/segredo/excluir', params: { id } })
export const revelarSegredo = (id: number): Promise<{ senha: string; notas?: string }> =>
  request.post({ url: '/cofre/segredo/revelar', params: { id } })

/** Senha aleatória com letras, números e símbolos, sem caracteres que se confundem. */
export const gerarSenha = (tamanho = 20) => {
  const grupos = ['ABCDEFGHJKLMNPQRSTUVWXYZ', 'abcdefghijkmnpqrstuvwxyz', '23456789', '!@#$%&*-_=+?']
  const todos = grupos.join('')
  const sortear = (texto: string) => {
    // descarta o que passaria do maior múltiplo, para nenhum caractere sair mais que outro
    const limite = 256 - (256 % texto.length)
    const byte = new Uint8Array(1)
    do {
      crypto.getRandomValues(byte)
    } while (byte[0] >= limite)
    return texto[byte[0] % texto.length]
  }
  const letras = [...grupos.map(sortear)]
  while (letras.length < tamanho) letras.push(sortear(todos))
  for (let i = letras.length - 1; i > 0; i--) {
    const alcance = i + 1
    const limite = 256 - (256 % alcance)
    const byte = new Uint8Array(1)
    do {
      crypto.getRandomValues(byte)
    } while (byte[0] >= limite)
    const j = byte[0] % alcance
    ;[letras[i], letras[j]] = [letras[j], letras[i]]
  }
  return letras.join('')
}
