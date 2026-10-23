<!-- Rota: /operacoes/chamados · Permissões: system:monitor-chamados:query (ver) e :create (abrir) -->
<template>
  <div class="pg">
    <MonitorHeader titulo="Chamados" fonte="Fila de atendimento · GLPI (API REST) ou módulo próprio">
      <template #acoes>
        <el-button v-hasPermi="['system:monitor-chamados:create']" type="primary" @click="dlg = true">Novo chamado</el-button>
      </template>
    </MonitorHeader>

    <div class="grid-kpi">
      <Kpi label="Abertos" :valor="int(c.abertos)" :apoio="`${int(c.Novo)} sem técnico`" />
      <Kpi label="SLA vencido" :valor="int(c.sla)" :apoio="c.sla ? 'priorizar na fila' : 'todos dentro do prazo'" :nivel="c.sla ? 'crit' : 'ok'" />
      <Kpi label="Abertos automaticamente" :valor="int(c.auto)" apoio="pelo monitoramento e pelos logs" />
      <Kpi label="Solucionados" :valor="int(c.Solucionado)" apoio="no período carregado" />
    </div>

    <el-card shadow="never">
      <div class="toolbar">
        <div class="chips">
          <el-check-tag v-for="[k, l] in CHIPS" :key="k" :checked="filtro === k" @change="filtro = k">{{ l }} <b>{{ int(c[k] || 0) }}</b></el-check-tag>
        </div>
        <el-input v-model="busca" clearable placeholder="Buscar por título, solicitante, número" style="max-width: 320px" />
      </div>
      <el-table v-loading="carregando" :data="dados.lista" size="small" max-height="600" empty-text="Nenhum chamado neste filtro.">
        <el-table-column label="Nº" prop="id" width="80" />
        <el-table-column label="Chamado" min-width="300">
          <template #default="{ row }">
            <b class="fw">{{ row.titulo }}</b>
            <el-tag v-if="row.origem" size="small" type="info" class="ml">auto · {{ row.origem }}</el-tag>
            <div class="sub">{{ row.solicitante ? row.solicitante + ' · ' : '' }}{{ row.unidade }} · aberto {{ ago(row.abertoEm) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="Categoria" prop="categoria" width="130" />
        <el-table-column label="Prioridade" width="110">
          <template #default="{ row }"><el-tag size="small" :type="PRI[row.prioridade]" effect="plain">{{ row.prioridade }}</el-tag></template>
        </el-table-column>
        <el-table-column label="Status" prop="status" width="140" />
        <el-table-column label="Técnico" width="160">
          <template #default="{ row }">{{ row.tecnico || '—' }}</template>
        </el-table-column>
        <el-table-column label="SLA" width="150">
          <template #default="{ row }"><span :class="slaClasse(row)">{{ slaTexto(row) }}</span></template>
        </el-table-column>
      </el-table>
      <p v-if="dados.total > dados.lista.length" class="mais">Mostrando {{ dados.lista.length }} de {{ int(dados.total) }}. Use a busca ou o filtro de unidade para refinar.</p>
    </el-card>

    <el-dialog v-model="dlg" title="Novo chamado" width="520px">
      <el-form ref="formRef" :model="novo" :rules="regras" label-position="top" @submit.prevent>
        <el-form-item label="Título" prop="titulo"><el-input v-model="novo.titulo" maxlength="200" show-word-limit placeholder="Ex.: Sem acesso à pasta do setor" /></el-form-item>
        <div class="row3">
          <el-form-item label="Categoria"><el-select v-model="novo.categoria"><el-option v-for="x in CATEGORIAS" :key="x" :label="x" :value="x" /></el-select></el-form-item>
          <el-form-item label="Prioridade"><el-select v-model="novo.prioridade"><el-option v-for="x in Object.keys(PRI)" :key="x" :label="x" :value="x" /></el-select></el-form-item>
          <el-form-item label="Unidade" prop="unidadeId"><el-select v-model="novo.unidadeId" filterable><el-option v-for="u in store.unidades" :key="u.id" :label="u.name" :value="u.id" /></el-select></el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dlg = false">Cancelar</el-button>
        <el-button type="primary" :loading="salvando" @click="salvar">Abrir chamado</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance } from 'element-plus'
import * as Api from '@/api/monitor'
import type { Chamado, Lista } from '@/api/monitor'
import { useMonitorStore } from '@/store/modules/monitor'
import { useMonitorSocket } from '@/hooks/web/useMonitorSocket'
import MonitorHeader from '../components/MonitorHeader.vue'
import Kpi from '../components/Kpi.vue'
import { ago, debounce, int } from '../components/util'

defineOptions({ name: 'MonitorChamados' })

const CHIPS = [['abertos', 'Abertos'], ['Novo', 'Novos'], ['Em atendimento', 'Em atendimento'], ['Pendente', 'Pendentes'], ['sla', 'SLA vencido'], ['auto', 'Automáticos'], ['Solucionado', 'Solucionados'], ['todos', 'Todos']] as const
const PRI: Record<string, 'danger' | 'warning' | 'primary' | 'info'> = { Crítica: 'danger', Alta: 'warning', Média: 'primary', Baixa: 'info' }
const CATEGORIAS = ['Rede', 'Hardware', 'Software', 'Acesso e senha', 'Impressão', 'Segurança', 'Telefonia']

const store = useMonitorStore()
const filtro = ref<string>('abertos')
const busca = ref('')
const carregando = ref(false)
const dados = ref<Lista<Chamado>>({ contagens: {}, lista: [], total: 0, resumo: {} })
const c = computed(() => dados.value.contagens as Record<string, number>)

async function carregar() {
  carregando.value = true
  try {
    dados.value = await Api.listarChamados({ unidadeId: store.unidadeId, status: filtro.value, busca: busca.value })
  } finally {
    carregando.value = false
  }
}
const recarregar = debounce(carregar, 400)
watch([() => store.unidadeId, filtro], carregar, { immediate: true })
watch(busca, () => recarregar())
// chamados novos ou alterados chegam pelo WebSocket: recarrega só se algum for da unidade filtrada
useMonitorSocket().on('monitor-chamado', (l: Chamado[]) => {
  if (!store.unidadeId || l.some((x) => x.unidadeId === store.unidadeId)) recarregar()
})

function slaTexto(t: Chamado) {
  if (t.status === 'Solucionado') return `resolvido em ${Math.max(1, Math.round(((t.solucionadoEm || t.abertoEm) - t.abertoEm) / 3600000))} h`
  const l = t.prazo - Date.now(), h = Math.abs(l) / 3600000, s = h < 1 ? `${Math.round(h * 60)} min` : `${Math.round(h)} h`
  return l < 0 ? `vencido há ${s}` : `${s} restantes`
}
function slaClasse(t: Chamado) {
  if (t.status === 'Solucionado') return 'ok'
  const l = t.prazo - Date.now()
  return l < 0 ? 'crit' : l < 2 * 3600000 ? 'warn' : ''
}

// ---- novo chamado ----
const dlg = ref(false)
const salvando = ref(false)
const formRef = ref<FormInstance>()
const novo = reactive({ titulo: '', categoria: 'Software', prioridade: 'Média', unidadeId: undefined as number | undefined })
const regras = {
  titulo: [{ required: true, message: 'Descreva o problema em poucas palavras', trigger: 'blur' }],
  unidadeId: [{ required: true, message: 'Escolha a unidade', trigger: 'change' }]
}
watch(dlg, (aberto) => { if (aberto) novo.unidadeId = novo.unidadeId || store.unidadeId })
async function salvar() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  salvando.value = true
  try {
    const t = await Api.abrirChamado({ ...novo })
    ElMessage.success(`Chamado ${t.id} aberto`)
    dlg.value = false
    novo.titulo = ''
    filtro.value = 'Novo'
    carregar()
  } finally {
    salvando.value = false
  }
}
</script>

<style scoped lang="scss">
.pg { padding: 4px; }
.grid-kpi { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10px; margin-bottom: 14px; }
.toolbar { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 10px; margin-bottom: 10px; }
.chips { display: flex; flex-wrap: wrap; gap: 6px; b { margin-left: 4px; font-variant-numeric: tabular-nums; } }
.fw { font-weight: 500; }
.ml { margin-left: 6px; }
.sub { font-size: 12px; color: var(--el-text-color-secondary); }
.mais { margin: 8px 0 0; font-size: 12px; color: var(--el-text-color-secondary); }
.crit { color: var(--el-color-danger); } .warn { color: var(--el-color-warning); } .ok { color: var(--el-color-success); }
.row3 { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; }
@media (max-width: 900px) { .grid-kpi { grid-template-columns: repeat(2, minmax(0, 1fr)); } .row3 { grid-template-columns: 1fr; } }
</style>
