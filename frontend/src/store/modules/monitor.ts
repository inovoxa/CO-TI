import { defineStore } from 'pinia'
import { store } from '@/store'
import * as MonitorApi from '@/api/monitor'
import type { DeptNode, MonitorUnit, Ocorrencia, Status, UnitKpi } from '@/api/monitor'

const MAX_OCORRENCIAS = 80

/**
 * Estado compartilhado por todas as páginas do Centro de Operações:
 * - unidade escolhida no filtro (também fica na URL como ?unidade=7, ver useUnidadeFiltro)
 * - unidades, setores e indicadores de cada unidade (carregados uma vez, atualizados pelo WebSocket)
 * - ocorrências recentes (barra de alertas e contador do menu)
 */
export const useMonitorStore = defineStore('monitor', {
  state: () => ({
    unidadeId: undefined as number | undefined,
    unidades: [] as MonitorUnit[],
    depts: [] as DeptNode[],
    ocorrencias: [] as Ocorrencia[],
    carregado: false,
    carregando: null as Promise<void> | null,
    ultimaAtualizacao: 0
  }),
  getters: {
    unidade: (s) => s.unidades.find((u) => u.id === s.unidadeId),
    nomeUnidade: (s) => (id?: number) => s.unidades.find((u) => u.id === id)?.name ?? (id ? `Unidade ${id}` : ''),
    /** ocorrências da unidade filtrada (ou de todas) */
    ocorrenciasFiltradas: (s) => s.ocorrencias.filter((o) => !s.unidadeId || o.unitId === s.unidadeId),
    contagemStatus: (s) => {
      const c: Record<Status, number> = { ok: 0, atencao: 0, critico: 0 }
      s.unidades.forEach((u) => u.kpi && c[u.kpi.status]++)
      return c
    },
    criticasRecentes(): Ocorrencia[] {
      const limite = Date.now() - 10 * 60_000
      return this.ocorrenciasFiltradas.filter((o) => o.nivel === 'critico' && o.momento > limite)
    }
  },
  actions: {
    /** Carrega uma vez; páginas diferentes podem chamar ao mesmo tempo sem duplicar a requisição. */
    async carregar(forcar = false) {
      if (this.carregado && !forcar) return
      if (this.carregando) return this.carregando
      this.carregando = MonitorApi.getOverview()
        .then((d) => {
          this.depts = d.depts || []
          this.unidades = d.units || []
          this.ocorrencias = (d.ocorrencias || []).slice(0, MAX_OCORRENCIAS)
          this.carregado = true
        })
        .finally(() => (this.carregando = null))
      return this.carregando
    },
    setUnidade(id?: number) {
      this.unidadeId = id || undefined
    },
    aplicarKpis(leituras: UnitKpi[]) {
      leituras.forEach((k) => {
        const u = this.unidades.find((x) => x.id === k.unitId)
        if (u) u.kpi = k
      })
      this.ultimaAtualizacao = Date.now()
    },
    aplicarOcorrencias(novas: Ocorrencia[]) {
      this.ocorrencias = [...novas.slice().reverse(), ...this.ocorrencias].slice(0, MAX_OCORRENCIAS)
    }
  }
})

/** Para usar fora de componentes (ex.: no hook do WebSocket) */
export const useMonitorStoreWithOut = () => useMonitorStore(store)
