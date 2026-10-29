<!-- Rota: /operacoes/visao-geral · Permissão: system:monitor-screen:query -->
<template>
  <div class="pg">
    <MonitorHeader titulo="Visão geral" fonte="Consolida todos os módulos por unidade">
      <template #acoes>
        <el-button @click="abrirTelao">Abrir telão em tela cheia</el-button>
      </template>
    </MonitorHeader>

    <div class="grid-kpi">
      <Kpi label="Links online" :valor="`${t.online}/${base.length}`" :apoio="t.offline || t.degradado ? `${t.offline} fora · ${t.degradado} lento` : 'todos normais'"
           :nivel="t.offline ? 'crit' : t.degradado ? 'warn' : 'ok'" :to="ir('MonitorLinks', 'MonitoramentoMonitor')" />
      <Kpi label="Disponibilidade 24 h" :valor="`${dec(t.disp, 2)}%`" apoio="média dos links principais" />
      <Kpi label="Serviços no ar" :valor="`${int(t.servOn)}/${int(t.servTot)}`" :apoio="t.servTot - t.servOn ? `${t.servTot - t.servOn} fora do ar` : t.servTot ? 'todos no ar' : 'nenhum monitor na unidade'"
           :nivel="t.servOn < t.servTot ? 'warn' : t.servTot ? 'ok' : ''" :to="ir('MonitorLinks', 'MonitoramentoMonitor')" />
      <Kpi label="Chamados abertos" :valor="int(t.chamados)" :apoio="`${t.sla} com SLA vencido`" :nivel="t.sla ? 'warn' : ''" :to="ir('MonitorChamados', 'ItsmChamado')" />
    </div>

    <div class="cols">
      <el-card shadow="never" class="card-mapa" :body-style="{ padding: '0', height: '100%' }">
        <template #header>
          <div class="ch">Unidades no mapa <small>clique para filtrar todos os módulos</small>
            <span class="leg"><i class="is-ok"></i>{{ store.contagemStatus.ok }} <i class="is-atencao"></i>{{ store.contagemStatus.atencao }} <i class="is-critico"></i>{{ store.contagemStatus.critico }}</span>
          </div>
        </template>
        <MapaUnidades :unidades="store.unidades" :selecionada="store.unidadeId" @selecionar="store.setUnidade" />
      </el-card>

      <el-card shadow="never" :body-style="{ padding: '0' }">
        <template #header><div class="ch">Ocorrências de todos os módulos <small>{{ store.ocorrenciasFiltradas.length }} recentes</small></div></template>
        <ol class="feed">
          <li v-for="(o, i) in store.ocorrenciasFiltradas" :key="o.momento + '-' + i" :class="`n-${o.nivel}`">
            <time>{{ hora(o.momento) }}</time>
            <div>
              <span class="m">{{ CATEGORIA[o.categoria] }} · {{ store.nomeUnidade(o.unitId) }}</span>
              <p>{{ o.texto }}</p>
            </div>
          </li>
          <li v-if="!store.ocorrenciasFiltradas.length" class="vazio">Nenhuma ocorrência recente{{ store.unidadeId ? ' nesta unidade' : '' }}.</li>
        </ol>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useMonitorStore } from '@/store/modules/monitor'
import MonitorHeader from '../components/MonitorHeader.vue'
import Kpi from '../components/Kpi.vue'
import MapaUnidades from '../components/MapaUnidades.vue'
import { dec, hora, int } from '../components/util'

defineOptions({ name: 'MonitorVisaoGeral' })

const store = useMonitorStore()
const route = useRoute()
const router = useRouter()
const CATEGORIA = { rede: 'Rede', ativos: 'Serviços', atendimento: 'Suporte' } as const

const base = computed(() => store.unidades.filter((u) => !store.unidadeId || u.id === store.unidadeId))
const t = computed(() => {
  const r = { online: 0, degradado: 0, offline: 0, disp: 0, servOn: 0, servTot: 0, chamados: 0, sla: 0 }
  let n = 0
  base.value.forEach(({ kpi: k }) => {
    if (!k) return
    n++
    r[k.link]++
    r.disp += k.disponibilidade
    r.servOn += k.servicosNoAr
    r.servTot += k.servicosTotal
    r.chamados += k.chamadosAbertos
    r.sla += k.slaVencido
  })
  r.disp = n ? r.disp / n : 100
  return r
})

/** atalho para a primeira página que o perfil tem (a visão por unidade ou o módulo), mantendo o filtro na URL */
const ir = (...nomes: string[]) => {
  const name = nomes.find((n) => router.hasRoute(n))
  return name ? { name, query: route.query } : undefined
}
const abrirTelao = () => window.open(router.resolve({ name: 'MonitorTelao' }).href, '_blank')
</script>

<style scoped lang="scss">
.pg { padding: 4px; }
.grid-kpi { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10px; margin-bottom: 14px; }
.cols { display: grid; grid-template-columns: minmax(0, 1.6fr) minmax(0, 1fr); gap: 14px; }
.card-mapa :deep(.el-card__body) { height: 460px; }
.ch {
  display: flex; flex-wrap: wrap; align-items: baseline; gap: 8px; font-weight: 600;
  small { font-weight: 400; color: var(--el-text-color-secondary); }
}
.leg {
  margin-left: auto; display: inline-flex; align-items: center; gap: 4px; font-weight: 400; font-size: 12px;
  i { display: inline-block; width: 8px; height: 8px; margin-left: 6px; border-radius: 50%; }
  .is-ok { background: var(--el-color-success); } .is-atencao { background: var(--el-color-warning); } .is-critico { background: var(--el-color-danger); }
}
.feed {
  max-height: 460px; margin: 0; padding: 0; overflow: auto; list-style: none;
  li { display: grid; grid-template-columns: 64px minmax(0, 1fr); gap: 8px; padding: 7px 12px; border-left: 3px solid var(--el-border-color-light); }
  li.n-critico { border-left-color: var(--el-color-danger); }
  li.n-atencao { border-left-color: var(--el-color-warning); }
  li.n-info { border-left-color: var(--el-color-success); }
  li.vazio { display: block; color: var(--el-text-color-secondary); }
  time { font-size: 12px; font-variant-numeric: tabular-nums; color: var(--el-text-color-secondary); }
  .m { font-size: 11px; letter-spacing: 0.04em; text-transform: uppercase; color: var(--el-text-color-secondary); }
  p { margin: 1px 0 0; font-size: 13px; }
}
@media (max-width: 1280px) {
  .grid-kpi { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .cols { grid-template-columns: minmax(0, 1fr); }
}
</style>
