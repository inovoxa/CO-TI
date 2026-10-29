<!-- Rota: /operacoes/links · Permissão: system:monitor-links:query -->
<template>
  <div class="pg">
    <MonitorHeader titulo="Links por unidade" fonte="Resumo do Monitoramento, filtrado pela unidade escolhida no mapa" />

    <div class="grid-kpi">
      <Kpi label="Monitores no ar" :valor="`${up}/${c.todos || 0}`" :apoio="lentos ? `${lentos} com latência alta` : 'verificação contínua'" :nivel="up === c.todos ? 'ok' : 'warn'" />
      <Kpi label="Fora do ar" :valor="fora" :apoio="fora ? nomesFora : 'nenhum'" :nivel="fora ? 'crit' : ''" />
      <Kpi label="Disponibilidade média 24 h" :valor="`${dec(dados.resumo.uptimeMedio24h || 100, 2)}%`" apoio="monitores filtrados" />
      <Kpi label="Certificados vencendo" :valor="dados.resumo.certificadosVencendo || 0" apoio="em menos de 30 dias" :nivel="dados.resumo.certificadosVencendo ? 'warn' : ''" />
    </div>

    <el-card shadow="never">
      <div class="chips">
        <el-check-tag v-for="[k, l] in CHIPS" :key="k" :checked="filtro === k" @change="filtro = k">{{ l }} <b>{{ c[k] || 0 }}</b></el-check-tag>
      </div>
      <el-table v-loading="carregando" :data="dados.lista" size="small" max-height="620" empty-text="Nenhum monitor com problema agora.">
        <el-table-column label="Monitor" min-width="240">
          <template #default="{ row }">
            <span class="dot" :class="`is-${row.estado}`"></span><b class="fw">{{ row.nome }}</b>
            <div class="sub mono">{{ row.alvo }} · {{ row.unidade }}</div>
          </template>
        </el-table-column>
        <el-table-column label="Tipo" prop="tipo" width="70" />
        <el-table-column label="Últimas 40 verificações" min-width="250">
          <template #default="{ row }">
            <div class="beats" :aria-label="`histórico de ${row.nome}`"><i v-for="(b, i) in row.historico" :key="i" :class="b"></i></div>
          </template>
        </el-table-column>
        <el-table-column label="Resposta" width="90">
          <template #default="{ row }"><span :class="{ crit: row.estado === 'down' }">{{ row.estado === 'down' ? 'fora' : `${row.latenciaMs} ms` }}</span></template>
        </el-table-column>
        <el-table-column label="24 h" width="80"><template #default="{ row }">{{ dec(row.uptime24h, 2) }}%</template></el-table-column>
        <el-table-column label="30 dias" width="80"><template #default="{ row }">{{ dec(row.uptime30d, 2) }}%</template></el-table-column>
        <el-table-column label="Certificado" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.certificadoDias != null" size="small" :type="row.certificadoDias < 30 ? 'warning' : 'success'" effect="plain">{{ row.certificadoDias }} dias</el-tag>
            <span v-else class="sub">—</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import * as Api from '@/api/monitor'
import type { Link, Lista } from '@/api/monitor'
import { useMonitorStore } from '@/store/modules/monitor'
import { useMonitorSocket } from '@/hooks/web/useMonitorSocket'
import MonitorHeader from '../components/MonitorHeader.vue'
import Kpi from '../components/Kpi.vue'
import { debounce, dec } from '../components/util'

defineOptions({ name: 'MonitorLinks' })

const CHIPS = [['problema', 'Com problema'], ['links', 'Links'], ['servicos', 'Serviços'], ['todos', 'Todos']] as const
const store = useMonitorStore()
const filtro = ref<string>('todos')
const carregando = ref(false)
const dados = ref<Lista<Link>>({ contagens: {}, lista: [], total: 0, resumo: {} })
const c = computed(() => dados.value.contagens as Record<string, number>)
// contagens do estado atual (sobre a lista "todos" quando o filtro é todos; senão aproximação pelo resumo)
const fora = computed(() => dados.value.lista.filter((l) => l.estado === 'down').length)
const lentos = computed(() => dados.value.lista.filter((l) => l.estado === 'lento').length)
const up = computed(() => (c.value.todos || 0) - (c.value.problema || 0))
const nomesFora = computed(() => dados.value.lista.filter((l) => l.estado === 'down').map((l) => l.nome).join(', '))

async function carregar() {
  carregando.value = true
  try {
    dados.value = await Api.listarLinks({ unidadeId: store.unidadeId, filtro: filtro.value })
  } finally {
    carregando.value = false
  }
}
watch([() => store.unidadeId, filtro], carregar, { immediate: true })
// cada mudança de estado chega pelo WebSocket; o histórico de verificações é recarregado a cada 5 s
const recarregar = debounce(carregar, 300)
useMonitorSocket().on('monitor-kpi', () => recarregar())
</script>

<style scoped lang="scss">
.pg { padding: 4px; }
.grid-kpi { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10px; margin-bottom: 14px; }
.chips { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 10px; b { margin-left: 4px; } }
.fw { font-weight: 500; }
.sub { font-size: 12px; color: var(--el-text-color-secondary); }
.mono { font-family: ui-monospace, Menlo, monospace; }
.crit { color: var(--el-color-danger); }
.dot {
  display: inline-block; width: 9px; height: 9px; margin-right: 6px; border-radius: 50%;
  &.is-up { background: var(--el-color-success); } &.is-lento { background: var(--el-color-warning); } &.is-down { background: var(--el-color-danger); }
}
.beats {
  display: flex; gap: 2px;
  i { display: block; width: 5px; height: 18px; border-radius: 2px; background: var(--el-border-color); }
  i.u { background: var(--el-color-success); } i.p { background: var(--el-color-warning); } i.d { background: var(--el-color-danger); }
}
@media (max-width: 900px) { .grid-kpi { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
</style>
