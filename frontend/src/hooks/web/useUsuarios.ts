import { reactive } from 'vue'
import * as UserApi from '@/api/system/user'

/**
 * Nomes de usuários já vistos nesta sessão, compartilhados por todas as telas. Os campos de pessoa não baixam mais a
 * lista inteira (com milhares de contas do AD ela pesava a cada tela): buscam enquanto se digita e, para mostrar o que
 * já está escolhido, pedem só os nomes que faltam.
 */
const usuarios = reactive(new Map<number, UserApi.UserVO>())
const pedidos = new Map<number, Promise<void>>()

export const lembrarUsuarios = (lista: UserApi.UserVO[]) => {
  lista.forEach((u) => usuarios.set(u.id, u))
}

export const usuarioPorId = (id?: number | null) => (id == null ? undefined : usuarios.get(id))

export const nomeUsuario = (id?: number | null) =>
  id == null ? '' : usuarios.get(id)?.nickname || `#${id}`

/** Garante que os nomes destes usuários estão no cache (uma ida ao servidor para os que faltam). */
export const garantirUsuarios = async (ids: (number | null | undefined)[]) => {
  const faltam = [...new Set(ids)].filter(
    (id): id is number => id != null && !usuarios.has(id) && !pedidos.has(id)
  )
  if (faltam.length) {
    const pedido = UserApi.getUsuariosPorIds(faltam)
      .then(lembrarUsuarios)
      .catch(() => {})
      .finally(() => faltam.forEach((id) => pedidos.delete(id)))
    faltam.forEach((id) => pedidos.set(id, pedido))
  }
  await Promise.all(
    ids.filter((id): id is number => id != null && pedidos.has(id)).map((id) => pedidos.get(id))
  )
}

export const buscarUsuarios = async (nome: string) => {
  const lista = await UserApi.buscarUsuarios(nome)
  lembrarUsuarios(lista)
  return lista
}
