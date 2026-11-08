import request from '@/config/axios'

/** Nome, logo, foto e texto do login da instância (Sistema > Identidade visual). Vazio = o padrão do CO-TI. */
export interface Marca {
  nome: string
  logo: string
  fundo: string
  /** abaixo do "Bem-vindo" no login, uma frase por linha */
  frase: string
}

export const getMarca = (): Promise<Marca> => request.get({ url: '/infra/marca' })

export const salvarMarca = (data: Marca) => request.put({ url: '/infra/marca', data })
