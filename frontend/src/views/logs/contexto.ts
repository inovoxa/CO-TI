import type { InjectionKey, Ref } from 'vue'
import * as Api from '@/api/logs'

/**
 * O que a tela (painéis ou busca) oferece aos quadros: filtro geral, período geral, o "atualizar" e as ações sobre um
 * valor clicado (Graylog: "Add to query", "Exclude from results", "Show documents for value").
 */
export interface ContextoLogs {
  /** Filtro "para todos os quadros" */
  filtroGeral: Ref<string>
  /** Período que vale para todos ('' = o de cada quadro) */
  periodoGeral: Ref<string>
  /** Muda a cada atualização: os quadros recarregam */
  atualizacao: Ref<number>
  /** Soma campo:valor ao filtro geral (ou o exclui) */
  filtrar: (campo: string, valor: string, excluir?: boolean) => void
  /** Abre a lista de mensagens de um filtro */
  mostrarMensagens: (titulo: string, filtro: Api.Filtro) => void
  /** Abre os campos de uma mensagem */
  abrirMensagem: (m: Api.Mensagem) => void
}

export const CONTEXTO: InjectionKey<ContextoLogs> = Symbol('contexto-logs')

export const useContexto = () => {
  const c = inject(CONTEXTO)
  if (!c) throw new Error('quadro de logs fora de uma tela de logs')
  return c
}

/** Filtro de um quadro: o dele somado ao geral da tela. */
export const filtroDoQuadro = (q: Api.Quadro, c: ContextoLogs): Api.Filtro => ({
  consulta: q.consulta || '',
  filtroGeral: c.filtroGeral.value || '',
  fluxo: q.fluxo || '',
  periodo: c.periodoGeral.value || q.periodo || '1d'
})

/** Quadro que recarrega quando o filtro, o período, o próprio quadro ou o "atualizar" mudam. */
export function useCarga<T>(q: () => Api.Quadro, carregar: (f: Api.Filtro) => Promise<T>) {
  const c = useContexto()
  const dados = ref<T>()
  const carregando = ref(false)
  const erro = ref('')
  let pedido = 0
  const recarregar = async () => {
    const meu = ++pedido
    carregando.value = true
    try {
      const r = await carregar(filtroDoQuadro(q(), c))
      if (meu === pedido) {
        dados.value = r
        erro.value = ''
      }
    } catch (e: any) {
      if (meu === pedido) erro.value = e?.msg || e?.message || 'Falha ao consultar'
    } finally {
      if (meu === pedido) carregando.value = false
    }
  }
  watch([() => JSON.stringify(q()), c.filtroGeral, c.periodoGeral, c.atualizacao], recarregar, {
    immediate: true
  })
  return { dados, carregando, erro, recarregar, contexto: c }
}

/** Rótulo do período no canto do quadro */
export const rotuloPeriodo = (periodo?: string) =>
  periodo === 'tudo'
    ? 'Todo o período'
    : (Api.PERIODOS.find((p) => p.valor === periodo)?.rotulo ?? 'Último dia')

export const quando = (s?: string) =>
  s
    ? new Date(s).toLocaleString('pt-BR', {
        day: '2-digit',
        month: '2-digit',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit'
      })
    : ''
