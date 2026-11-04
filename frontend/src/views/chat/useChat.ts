import { ref, watch } from 'vue'
import * as Api from '@/api/chat'
import { useMonitorSocket } from '@/hooks/web/useMonitorSocket'
import { useUserStore } from '@/store/modules/user'

/**
 * Estado do chat, uma vez só por aba: contador, presença e aviso no navegador continuam em qualquer tela do portal.
 * O WebSocket só apressa a entrega. O que vale é o número sequencial: ao reconectar, a tela busca o que faltou.
 */
const naoLidas = ref(0)
const conversaAberta = ref<number | null>(null)
const ouvintes = new Set<(tipo: string, conteudo: any) => void>()
const reconexao = new Set<() => void>()
let iniciado = false

const atualizarTitulo = () => {
  const limpo = document.title.replace(/^\(\d+\) /, '')
  document.title = naoLidas.value > 0 ? `(${naoLidas.value}) ${limpo}` : limpo
}

export const atualizarNaoLidas = async () => {
  naoLidas.value = await Api.getNaoLidas().catch(() => naoLidas.value)
  atualizarTitulo()
}

const avisar = (m: Api.Mensagem, meuId: number) => {
  if (m.remetenteId === meuId || m.tipo !== 'texto') return
  if (conversaAberta.value === m.conversaId && document.visibilityState === 'visible') return
  if (typeof Notification === 'undefined' || Notification.permission !== 'granted') return
  const mencionado = m.mencoes?.some((p) => p.id === meuId)
  const n = new Notification(mencionado ? `${m.remetente} mencionou você` : m.remetente, {
    body: m.conteudo ? m.conteudo.slice(0, 120) : 'Enviou um anexo',
    tag: `chat-${m.conversaId}`
  })
  n.onclick = () => {
    window.focus()
    n.close()
  }
}

export const iniciarChat = () => {
  if (iniciado) return
  iniciado = true
  const meuId = () => useUserStore().getUser.id
  const { on, status } = useMonitorSocket()
  const repassar = (tipo: string) => (c: any) => ouvintes.forEach((fn) => fn(tipo, c))
  on('chat-mensagem', (m: Api.Mensagem) => {
    repassar('chat-mensagem')(m)
    avisar(m, meuId())
    atualizarNaoLidas()
  })
  on('chat-atualizada', repassar('chat-atualizada'))
  on('chat-conversa', (c: any) => {
    repassar('chat-conversa')(c)
    atualizarNaoLidas()
  })
  on('chat-lida', (c: any) => {
    repassar('chat-lida')(c)
    atualizarNaoLidas()
  })
  watch(status, (s, antes) => {
    if (s === 'OPEN' && antes && antes !== 'OPEN') {
      reconexao.forEach((fn) => fn())
      atualizarNaoLidas()
    }
  })
  const emCasa = () => {
    if (document.visibilityState === 'visible') Api.presenca().catch(() => {})
  }
  emCasa()
  setInterval(emCasa, 60000)
  document.addEventListener('visibilitychange', emCasa)
  atualizarNaoLidas()
}

export const useChat = () => ({
  naoLidas,
  conversaAberta,
  ouvir: (fn: (tipo: string, conteudo: any) => void) => {
    ouvintes.add(fn)
    return () => ouvintes.delete(fn)
  },
  aoReconectar: (fn: () => void) => {
    reconexao.add(fn)
    return () => reconexao.delete(fn)
  }
})
