import { effectScope, getCurrentInstance, onBeforeUnmount, type Ref } from 'vue'
import { useWebSocket } from '@vueuse/core'
import { getAccessToken } from '@/utils/auth'
import { useMonitorStoreWithOut } from '@/store/modules/monitor'

/**
 * UMA conexão WebSocket para todas as páginas do Centro de Operações.
 *
 * Cada página só "assina" os tipos de mensagem que lhe interessam:
 *   const { on, status } = useMonitorSocket()
 *   on('monitor-chamado', (lista) => ...)
 * A assinatura é cancelada sozinha quando a página fecha. A conexão continua aberta
 * enquanto o usuário navega entre os módulos, sem reconectar a cada troca de página.
 *
 * Tipos enviados pelo backend: monitor-kpi, monitor-ocorrencia (MonitorPushJob) e
 * monitor-link, monitor-chamado, monitor-log, monitor-camera (FontesSimuladas ou fontes reais).
 */
type Handler = (conteudo: any) => void

const handlers = new Map<string, Set<Handler>>()
let conexao: { status: Ref<string>; close: () => void } | null = null

function despachar(raw: string) {
  if (!raw || raw === 'pong') return
  try {
    const msg = JSON.parse(raw)
    const set = handlers.get(msg.type)
    if (!set || !set.size) return
    const conteudo = typeof msg.content === 'string' ? JSON.parse(msg.content) : msg.content
    set.forEach((fn) => {
      try {
        fn(conteudo)
      } catch (e) {
        console.warn('[monitor] erro ao tratar', msg.type, e)
      }
    })
  } catch (e) {
    console.warn('[monitor] mensagem ignorada', e)
  }
}

function adicionar(tipo: string, fn: Handler) {
  if (!handlers.has(tipo)) handlers.set(tipo, new Set())
  handlers.get(tipo)!.add(fn)
  return () => handlers.get(tipo)?.delete(fn)
}

function garantirConexao() {
  if (conexao) return conexao
  // escopo próprio: a conexão não pode ser fechada quando a primeira página que a abriu for desmontada
  const escopo = effectScope(true)
  conexao = escopo.run(() => {
    // Mesmo endereço da tela "Infra > WebSocket" do projeto. Se não autenticar, copie de
    // src/views/infra/webSocket/index.vue a forma como a URL e o token são montados.
    const url = (import.meta.env.VITE_BASE_URL + '/infra/ws').replace('http', 'ws') + '?token=' + getAccessToken()
    const ws = useWebSocket(url, {
      autoReconnect: { retries: -1, delay: 3000 },
      heartbeat: { message: 'ping', interval: 30000 },
      onMessage: (_ws, ev) => despachar(ev.data)
    })
    return { status: ws.status as Ref<string>, close: ws.close }
  })!

  // assinaturas globais: indicadores e ocorrências alimentam o store compartilhado
  const store = useMonitorStoreWithOut()
  adicionar('monitor-kpi', (l) => store.aplicarKpis(l))
  adicionar('monitor-ocorrencia', (l) => store.aplicarOcorrencias(l))
  return conexao
}

export function useMonitorSocket() {
  const c = garantirConexao()
  const proprias: Array<() => void> = []
  function on(tipo: string, fn: Handler) {
    const off = adicionar(tipo, fn)
    proprias.push(off)
    return off
  }
  if (getCurrentInstance()) onBeforeUnmount(() => proprias.forEach((off) => off()))
  return { status: c.status, on }
}

/** Fecha a conexão (ex.: no logout). */
export function fecharMonitorSocket() {
  conexao?.close()
  conexao = null
  handlers.clear()
}
