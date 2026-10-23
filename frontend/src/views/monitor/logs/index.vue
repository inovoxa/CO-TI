<!-- Rota: /operacoes/logs · Permissão: system:monitor-logs:query -->
<template>
  <div class="pg">
    <MonitorHeader titulo="Logs de usuários e rede" fonte="AD, firewall, VPN, proxy e servidores · Graylog (API de busca)">
      <template #acoes>
        <el-button @click="pausado = !pausado">{{ pausado ? 'Retomar' : 'Pausar' }} fluxo</el-button>
      </template>
    </MonitorHeader>

    <div class="grid-kpi">
      <Kpi label="Falhas de logon 1 h" :valor="int(r.falhasLogon1h)" apoio="Active Directory" :nivel="r.falhasLogon1h > 200 ? 'warn' : ''" />
      <Kpi label="Bloqueios no firewall 1 h" :valor="int(r.bloqueios1h)" apoio="conexões externas negadas" />
      <Kpi label="Alertas gerados 1 h" :valor="int(r.alertas1h)" apoio="força bruta, varredura de portas" :nivel="r.alertas1h ? 'crit' : ''" />
      <Kpi label="Mensagens recebidas" :valor="int(recebidas)" :apoio="pausado ? 'fluxo pausado' : 'desde que a página abriu'" />
    </div>

    <el-card shadow="never">
      <div class="toolbar">
        <div class="chips">
          <el-check-tag v-for="[k, l] in CHIPS" :key="k" :checked="nivel === k" @change="nivel = k">{{ l }} <b>{{ int(c[k] || 0) }}</b></el-check-tag>
        </div>
        <el-input v-model="busca" clearable placeholder="Filtrar: usuário, IP, texto" style="max-width: 300px" />
      </div>
      <el-table v-loading="carregando" :data="linhas" size="small" max-height="620" class="logt" empty-text="Nenhuma mensagem com esse filtro.">
        <el-table-column label="Hora" width="90"><template #default="{ row }">{{ hora(row.ts) }}</template></el-table-column>
        <el-table-column label="Origem" prop="origem" width="150" />
        <el-table-column label="Nível" width="90"><template #default="{ row }"><span class="lv" :class="`lv-${row.nivel}`">{{ row.nivel }}</span></template></el-table-column>
        <el-table-column label="Mensagem" prop="mensagem" min-width="400" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import * as Api from '@/api/monitor'
import type { Lista, LogLinha } from '@/api/monitor'
import { useMonitorStore } from '@/store/modules/monitor'
import { useMonitorSocket } from '@/hooks/web/useMonitorSocket'
import MonitorHeader from '../components/MonitorHeader.vue'
import Kpi from '../components/Kpi.vue'
import { debounce, hora, int } from '../components/util'

defineOptions({ name: 'MonitorLogs' })

const CHIPS = [['todos', 'Todos'], ['info', 'Info'], ['aviso', 'Aviso'], ['erro', 'Erro'], ['critico', 'Crítico']] as const
const MAX = 300
const store = useMonitorStore()
const nivel = ref<string>('todos')
const busca = ref('')
const pausado = ref(false)
const carregando = ref(false)
const recebidas = ref(0)
const dados = ref<Lista<LogLinha>>({ contagens: {}, lista: [], total: 0, resumo: {} })
const linhas = ref<LogLinha[]>([])
const c = computed(() => dados.value.contagens as Record<string, number>)
const r = computed(() => dados.value.resumo as Record<string, number>)

async function carregar() {
  carregando.value = true
  try {
    dados.value = await Api.buscarLogs({ unidadeId: store.unidadeId, nivel: nivel.value, busca: busca.value })
    linhas.value = dados.value.lista.slice(0, MAX)
  } finally {
    carregando.value = false
  }
}
watch([() => store.unidadeId, nivel], carregar, { immediate: true })
const recarregar = debounce(carregar, 400)
watch(busca, () => recarregar())

// linhas novas chegam pelo WebSocket e entram no topo se passarem pelos filtros atuais
const passa = (l: LogLinha) =>
  (!store.unidadeId || l.unidadeId === store.unidadeId) &&
  (nivel.value === 'todos' || l.nivel === nivel.value) &&
  (!busca.value || (l.origem + ' ' + l.mensagem).toLowerCase().includes(busca.value.toLowerCase()))
useMonitorSocket().on('monitor-log', (novas: LogLinha[]) => {
  recebidas.value += novas.length
  if (pausado.value) return
  const ok = novas.filter(passa)
  if (ok.length) linhas.value = [...ok.reverse(), ...linhas.value].slice(0, MAX)
  novas.forEach((l) => (c.value[l.nivel] = (c.value[l.nivel] || 0) + 1))
})
</script>

<style scoped lang="scss">
.pg { padding: 4px; }
.grid-kpi { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10px; margin-bottom: 14px; }
.toolbar { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 10px; margin-bottom: 10px; }
.chips { display: flex; flex-wrap: wrap; gap: 6px; b { margin-left: 4px; } }
.logt :deep(td) { font-family: ui-monospace, Menlo, monospace; font-size: 12px; }
.lv {
  padding: 0 6px; border-radius: 4px; font-family: ui-monospace, Menlo, monospace; font-size: 11px;
  &.lv-info { color: var(--el-color-primary); } &.lv-aviso { color: var(--el-color-warning); } &.lv-erro { color: var(--el-color-danger); }
  &.lv-critico { background: var(--el-color-danger); color: #fff; }
}
@media (max-width: 900px) { .grid-kpi { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
</style>
