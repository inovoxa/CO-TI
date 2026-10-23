import { onBeforeUnmount, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useMonitorStore } from '@/store/modules/monitor'

export const int = (v = 0) => Math.round(v).toLocaleString('pt-BR')
export const dec = (v = 0, n = 1) => v.toLocaleString('pt-BR', { minimumFractionDigits: n, maximumFractionDigits: n })
export const hora = (ms: number) => new Date(ms).toLocaleTimeString('pt-BR')

export function ago(ms: number) {
  const m = Math.round((Date.now() - ms) / 60000)
  if (m < 1) return 'agora'
  if (m < 60) return `há ${m} min`
  const h = Math.round(m / 60)
  return h < 48 ? `há ${h} h` : `há ${Math.round(h / 24)} dias`
}

/** Agrupa várias chamadas seguidas numa só (ex.: muitas mensagens do WebSocket → um único recarregamento). */
export function debounce<T extends (...a: any[]) => void>(fn: T, ms = 800) {
  let t: ReturnType<typeof setTimeout> | undefined
  const d = (...a: Parameters<T>) => {
    clearTimeout(t)
    t = setTimeout(() => fn(...a), ms)
  }
  onBeforeUnmount(() => clearTimeout(t))
  return d
}

/**
 * Mantém o filtro de unidade igual em todas as páginas e na URL (?unidade=7):
 * o link copiado abre já filtrado, e trocar de módulo não perde a unidade escolhida.
 * Chame uma vez por página; devolve o store.
 */
export function useUnidadeFiltro() {
  const store = useMonitorStore()
  const route = useRoute()
  const router = useRouter()
  const daUrl = () => {
    const v = Number(route.query.unidade)
    return Number.isFinite(v) && v > 0 ? v : undefined
  }
  // a URL manda ao abrir a página; se ela não tiver filtro, aplica o que já estava escolhido
  if (daUrl() !== undefined) store.setUnidade(daUrl())
  else if (store.unidadeId) router.replace({ query: { ...route.query, unidade: String(store.unidadeId) } })

  watch(
    () => route.query.unidade,
    () => store.setUnidade(daUrl())
  )
  watch(
    () => store.unidadeId,
    (id) => {
      if (id === daUrl()) return
      const q = { ...route.query }
      if (id) q.unidade = String(id)
      else delete q.unidade
      router.replace({ query: q })
    }
  )
  store.carregar()
  return store
}
