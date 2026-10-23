<!-- Rota: /operacoes/inventario · Permissão: system:monitor-inventario:query -->
<template>
  <div class="pg">
    <MonitorHeader titulo="Inventário de equipamentos" fonte="Estações e servidores · OCS Inventory NG (API REST)">
      <template #acoes><el-button :loading="carregando" @click="carregar">Atualizar</el-button></template>
    </MonitorHeader>

    <div class="grid-kpi">
      <Kpi label="Equipamentos inventariados" :valor="int(c.todos)" :apoio="`${int(c.Desktop)} desktops · ${int(c.Notebook)} notebooks · ${int(c.Servidor)} servidores`" />
      <Kpi label="Sistema sem suporte" :valor="int(c.eol)" apoio="Windows 10 (suporte encerrado em 10/2025)" :nivel="c.eol ? 'warn' : 'ok'" />
      <Kpi label="Antivírus com problema" :valor="int(c.av)" apoio="desatualizado ou ausente" :nivel="c.av ? 'crit' : 'ok'" />
      <Kpi label="Sem comunicação" :valor="int(c.old)" apoio="há mais de 7 dias" :nivel="c.old ? 'warn' : 'ok'" />
    </div>

    <div class="cols">
      <el-card shadow="never">
        <div class="toolbar">
          <div class="chips">
            <el-check-tag v-for="[k, l] in CHIPS" :key="k" :checked="filtro === k" @change="filtro = k">{{ l }} <b>{{ int(c[k] || 0) }}</b></el-check-tag>
          </div>
          <el-input v-model="busca" clearable placeholder="Buscar hostname, usuário, sistema, IP" style="max-width: 300px" />
        </div>
        <el-table v-loading="carregando" :data="dados.lista" size="small" max-height="600" empty-text="Nenhum equipamento neste filtro.">
          <el-table-column label="Equipamento" min-width="200">
            <template #default="{ row }"><b class="mono fw">{{ row.hostname }}</b><div class="sub">{{ row.tipo }} · {{ row.unidade }} · <span class="mono">{{ row.ip }}</span></div></template>
          </el-table-column>
          <el-table-column label="Usuário" min-width="140"><template #default="{ row }">{{ row.usuario || '—' }}</template></el-table-column>
          <el-table-column label="Sistema" min-width="190">
            <template #default="{ row }">{{ row.so }}<el-tag v-if="row.so.startsWith('Windows 10')" size="small" type="warning" effect="plain" class="ml">sem suporte</el-tag></template>
          </el-table-column>
          <el-table-column label="Hardware" min-width="200">
            <template #default="{ row }"><span class="mono small">{{ row.cpu }}</span><div class="sub">{{ row.ramGb }} GB RAM · {{ row.discoGb >= 1024 ? row.discoGb / 1024 + ' TB' : row.discoGb + ' GB' }} · {{ row.discoLivrePct }}% livre</div></template>
          </el-table-column>
          <el-table-column label="Antivírus" width="120">
            <template #default="{ row }"><el-tag size="small" effect="plain" :type="row.antivirus === 'Atualizado' ? 'success' : row.antivirus === 'Ausente' ? 'danger' : 'warning'">{{ row.antivirus }}</el-tag></template>
          </el-table-column>
          <el-table-column label="Última comunicação" width="150">
            <template #default="{ row }"><span :class="{ crit: Date.now() - row.ultimaComunicacao > 7 * 86400000 }">{{ ago(row.ultimaComunicacao) }}</span></template>
          </el-table-column>
        </el-table>
        <p v-if="dados.total > dados.lista.length" class="mais">Mostrando {{ dados.lista.length }} de {{ int(dados.total) }}. Use a busca ou os filtros para refinar.</p>
      </el-card>

      <el-card shadow="never">
        <template #header><b>Sistemas operacionais</b></template>
        <div v-for="[so, n] in porSistema" :key="so" class="hbar">
          <span>{{ so }}</span>
          <span class="b"><i :class="{ w: so.startsWith('Windows 10') }" :style="{ width: (n / maxSo) * 100 + '%' }"></i></span>
          <span class="num">{{ int(n) }}</span>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import * as Api from '@/api/monitor'
import type { Equipamento, Lista } from '@/api/monitor'
import { useMonitorStore } from '@/store/modules/monitor'
import MonitorHeader from '../components/MonitorHeader.vue'
import Kpi from '../components/Kpi.vue'
import { ago, debounce, int } from '../components/util'

defineOptions({ name: 'MonitorInventario' })

const CHIPS = [['todos', 'Todos'], ['Desktop', 'Desktops'], ['Notebook', 'Notebooks'], ['Servidor', 'Servidores'], ['eol', 'Sem suporte'], ['av', 'Antivírus'], ['old', 'Sem comunicação']] as const
const store = useMonitorStore()
const filtro = ref<string>('todos')
const busca = ref('')
const carregando = ref(false)
const dados = ref<Lista<Equipamento>>({ contagens: {}, lista: [], total: 0, resumo: {} })
const c = computed(() => dados.value.contagens as Record<string, number>)
const porSistema = computed(() => Object.entries((dados.value.resumo.porSistema || {}) as Record<string, number>).sort((a, b) => b[1] - a[1]))
const maxSo = computed(() => Math.max(1, ...porSistema.value.map(([, n]) => n)))

async function carregar() {
  carregando.value = true
  try {
    dados.value = await Api.listarEquipamentos({ unidadeId: store.unidadeId, filtro: filtro.value, busca: busca.value })
  } finally {
    carregando.value = false
  }
}
watch([() => store.unidadeId, filtro], carregar, { immediate: true })
const recarregar = debounce(carregar, 400)
watch(busca, () => recarregar())
</script>

<style scoped lang="scss">
.pg { padding: 4px; }
.grid-kpi { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10px; margin-bottom: 14px; }
.cols { display: grid; grid-template-columns: minmax(0, 1fr) 320px; gap: 14px; align-items: start; }
.toolbar { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 10px; margin-bottom: 10px; }
.chips { display: flex; flex-wrap: wrap; gap: 6px; b { margin-left: 4px; } }
.fw { font-weight: 500; } .ml { margin-left: 6px; } .small { font-size: 12px; }
.mono { font-family: ui-monospace, Menlo, monospace; }
.sub { font-size: 12px; color: var(--el-text-color-secondary); }
.mais { margin: 8px 0 0; font-size: 12px; color: var(--el-text-color-secondary); }
.crit { color: var(--el-color-danger); }
.hbar {
  display: grid; grid-template-columns: 150px minmax(0, 1fr) 48px; gap: 8px; align-items: center; padding: 4px 0; font-size: 13px;
  .b { height: 10px; overflow: hidden; border-radius: 5px; background: var(--el-fill-color); }
  .b i { display: block; height: 100%; border-radius: 5px; background: var(--el-color-primary); }
  .b i.w { background: var(--el-color-warning); }
  .num { text-align: right; font-variant-numeric: tabular-nums; }
}
@media (max-width: 1200px) { .cols { grid-template-columns: minmax(0, 1fr); } }
@media (max-width: 900px) { .grid-kpi { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
</style>
