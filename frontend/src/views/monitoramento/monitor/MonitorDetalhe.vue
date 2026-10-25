<!-- Detalhe do monitor: estado, disponibilidade, gráfico de latência e mudanças de estado -->
<template>
  <el-drawer v-model="visivel" :title="d?.config.nome" size="760px">
    <template v-if="d">
      <div class="mb-12px flex flex-wrap items-center gap-8px">
        <el-tag v-if="!d.estado.ativo" disable-transitions type="info">Pausado</el-tag>
        <el-tag
          v-else-if="d.estado.estado != null"
          :type="ESTADOS[d.estado.estado].cor as any"
          disable-transitions
        >
          {{ ESTADOS[d.estado.estado].rotulo }}<template v-if="d.estado.lento"> · lento</template>
        </el-tag>
        <el-tag v-else disable-transitions type="info">Ainda não verificado</el-tag>
        <span v-if="d.estado.estadoDesde" class="text-13px text-[var(--el-text-color-secondary)]">
          desde {{ quando(d.estado.estadoDesde) }}
        </span>
        <span class="text-13px text-[var(--el-text-color-secondary)] break-all">{{ alvo }}</span>
      </div>
      <el-alert
        v-if="d.manutencoes.length"
        :closable="false"
        class="mb-12px"
        show-icon
        type="info"
        :title="`Em manutenção: ${d.manutencoes.join(', ')}`"
      />
      <div v-if="d.estado.ultimaMsg" class="mb-12px text-13px">
        Última verificação {{ quando(d.estado.ultimaVerificacao) }}: {{ d.estado.ultimaMsg }}
      </div>
      <div v-if="d.estado.chamadoId" class="mb-12px text-13px">
        Chamado aberto:
        <el-link
          type="primary"
          @click="router.push({ path: '/servicedesk/chamados', query: { id: d.estado.chamadoId } })"
          >#{{ d.estado.chamadoId }}</el-link
        >
      </div>

      <div v-if="pode" class="mb-16px flex flex-wrap gap-8px">
        <el-button
          v-if="!['push', 'manual'].includes(d.config.tipo) && d.estado.ativo"
          size="small"
          @click="verificar"
          >Verificar agora</el-button
        >
        <template v-if="d.config.tipo === 'manual' && d.estado.ativo">
          <el-button size="small" type="success" @click="manual(true)">Marcar no ar</el-button>
          <el-button size="small" type="danger" @click="manual(false)">Marcar fora do ar</el-button>
        </template>
        <el-button size="small" @click="emit('editar', d)">Editar</el-button>
        <el-button size="small" @click="ativar(!d.estado.ativo)">{{
          d.estado.ativo ? 'Pausar' : 'Retomar'
        }}</el-button>
        <el-button size="small" type="danger" plain @click="excluir">Excluir</el-button>
      </div>

      <div class="numeros">
        <div
          ><span>Resposta</span
          ><b>{{
            d.estado.ultimaLatenciaMs != null ? `${d.estado.ultimaLatenciaMs} ms` : '—'
          }}</b></div
        >
        <div
          ><span>Média 24 h</span
          ><b>{{
            d.estado.latenciaMedia24h != null ? `${d.estado.latenciaMedia24h} ms` : '—'
          }}</b></div
        >
        <div
          ><span>Disponível 24 h</span><b>{{ pct(d.estado.uptime24h) }}</b></div
        >
        <div
          ><span>30 dias</span><b>{{ pct(d.estado.uptime30d) }}</b></div
        >
        <div
          ><span>1 ano</span><b>{{ pct(d.uptime1a) }}</b></div
        >
        <div v-if="d.estado.certDias != null">
          <span>Certificado</span
          ><b :class="{ alerta: d.estado.certDias < 30 }">{{ d.estado.certDias }} dias</b>
        </div>
      </div>

      <div v-if="d.pushUrl" class="mb-16px">
        <div class="mb-4px text-13px font-medium">URL de push</div>
        <el-input :model-value="d.pushUrl" readonly>
          <template #append><el-button @click="copiar(d.pushUrl)">Copiar</el-button></template>
        </el-input>
        <div class="mt-4px text-12px text-[var(--el-text-color-secondary)]">
          Chame com GET ou POST a cada {{ d.config.intervaloS }} s. <code>status=down</code> marca
          fora do ar; <code>msg</code> e <code>ping</code> são opcionais.
        </div>
      </div>

      <div class="mb-8px flex items-center justify-between">
        <span class="text-14px font-medium">Tempo de resposta</span>
        <el-radio-group v-model="periodo" size="small" @change="carregarSerie">
          <el-radio-button value="3h">3 h</el-radio-button>
          <el-radio-button value="24h">24 h</el-radio-button>
          <el-radio-button value="7d">7 dias</el-radio-button>
          <el-radio-button value="30d">30 dias</el-radio-button>
        </el-radio-group>
      </div>
      <Echart v-if="serie.length" :height="220" :options="opcoes" />
      <el-empty v-else :image-size="50" description="Sem verificações no período" />
      <div class="mb-16px text-12px text-[var(--el-text-color-secondary)]">
        Média por intervalo; as faixas vermelhas marcam intervalos com falha.
      </div>

      <div class="mb-8px text-14px font-medium">Mudanças de estado (30 dias)</div>
      <el-table :data="d.eventos" max-height="300" size="small">
        <el-table-column label="Quando" width="160">
          <template #default="{ row }">{{ quando(row.momento) }}</template>
        </el-table-column>
        <el-table-column label="Estado" width="120">
          <template #default="{ row }">
            <el-tag :type="ESTADOS[row.estado].cor as any" disable-transitions size="small">{{
              ESTADOS[row.estado].rotulo
            }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Antes, por" width="110">
          <template #default="{ row }">{{ duracao(row.duracaoAnteriorS) }}</template>
        </el-table-column>
        <el-table-column label="Mensagem" min-width="200" prop="msg" show-overflow-tooltip />
      </el-table>
    </template>
  </el-drawer>
</template>

<script lang="ts" setup>
import { ElMessageBox } from 'element-plus'
import type { EChartsOption } from 'echarts'
import * as Mon from '@/api/monitoramento'
import { ESTADOS, duracao, pct, quando } from '@/api/monitoramento'
import { checkPermi } from '@/utils/permission'
import { useMonitorSocket } from '@/hooks/web/useMonitorSocket'

const emit = defineEmits<{ editar: [d: Mon.Detalhe]; mudou: [] }>()
const message = useMessage()
const router = useRouter()
const pode = checkPermi(['monitoramento:monitor:update'])
const visivel = ref(false)
const d = ref<Mon.Detalhe>()
const periodo = ref('24h')
const serie = ref<Mon.Ponto[]>([])

const alvo = computed(() => {
  const c = d.value?.config
  if (!c) return ''
  return c.alvo ? c.alvo + (c.porta ? `:${c.porta}` : '') : c.tipo
})

const cor = (v: string, padrao: string) =>
  getComputedStyle(document.documentElement).getPropertyValue(v).trim() || padrao

const opcoes = computed<EChartsOption>(() => {
  const pontos = serie.value
  const linha = cor('--el-color-primary', '#409eff')
  const falha = cor('--el-color-danger', '#f56c6c')
  const texto = cor('--el-text-color-secondary', '#909399')
  const grade = cor('--el-border-color-lighter', '#ebeef5')
  const curto = periodo.value === '3h' || periodo.value === '24h'
  const rotulo = (t: string) =>
    new Date(t).toLocaleString(
      'pt-BR',
      curto
        ? { hour: '2-digit', minute: '2-digit' }
        : { day: '2-digit', month: '2-digit', hour: '2-digit' }
    )
  const faixas = pontos
    .map((p, i) => ({ p, i }))
    .filter(({ p }) => p.falhas > 0)
    .map(({ i }) => [{ xAxis: i - 0.5 }, { xAxis: i + 0.5 }]) as any
  return {
    grid: { left: 48, right: 16, top: 12, bottom: 28 },
    tooltip: {
      trigger: 'axis',
      formatter: (ps: any) => {
        const p = pontos[ps[0].dataIndex]
        return (
          `${new Date(p.momento).toLocaleString('pt-BR')}<br>` +
          (p.media != null
            ? `média ${p.media} ms (mín. ${p.minima}, máx. ${p.maxima})<br>`
            : 'sem resposta<br>') +
          (p.falhas
            ? `<b>${p.falhas} de ${p.total} verificações com falha</b>`
            : `${p.total} verificações`)
        )
      }
    },
    xAxis: {
      type: 'category',
      data: pontos.map((p) => rotulo(p.momento)),
      axisLabel: { color: texto, fontSize: 11 },
      axisLine: { lineStyle: { color: grade } },
      axisTick: { show: false }
    },
    yAxis: {
      type: 'value',
      name: 'ms',
      nameTextStyle: { color: texto, fontSize: 11 },
      axisLabel: { color: texto, fontSize: 11 },
      splitLine: { lineStyle: { color: grade } }
    },
    series: [
      {
        type: 'line',
        name: 'Tempo de resposta',
        data: pontos.map((p) => p.media ?? null),
        connectNulls: false,
        showSymbol: pontos.length < 40,
        symbolSize: 8,
        lineStyle: { width: 2, color: linha },
        itemStyle: { color: linha },
        areaStyle: { color: linha, opacity: 0.08 },
        markArea: { silent: true, itemStyle: { color: falha, opacity: 0.18 }, data: faixas }
      }
    ]
  }
})

const abrir = async (id: number) => {
  d.value = await Mon.getMonitor(id)
  visivel.value = true
  await carregarSerie()
}
const recarregar = async () => {
  if (!d.value) return
  d.value = await Mon.getMonitor(d.value.config.id!)
}
const carregarSerie = async () => {
  if (d.value) serie.value = await Mon.getSerie(d.value.config.id!, periodo.value)
}
defineExpose({ abrir, recarregar })

// atualiza ao vivo quando chega uma batida deste monitor
let pendente: ReturnType<typeof setTimeout> | undefined
useMonitorSocket().on('monitoramento-batida', (b: any) => {
  if (!visivel.value || !d.value || b.monitorId !== d.value.config.id) return
  clearTimeout(pendente)
  pendente = setTimeout(async () => {
    await recarregar()
    if (b.importante || periodo.value === '3h') await carregarSerie()
  }, 400)
})

const verificar = async () => {
  await Mon.verificarMonitor(d.value!.config.id!)
  message.success('Verificação pedida')
}
const manual = async (noAr: boolean) => {
  const r = await ElMessageBox.prompt(
    'Motivo (opcional)',
    noAr ? 'Marcar no ar' : 'Marcar fora do ar',
    {
      confirmButtonText: 'Confirmar',
      cancelButtonText: 'Cancelar'
    }
  )
  await Mon.marcarManual(d.value!.config.id!, noAr, r.value)
  setTimeout(recarregar, 500)
  emit('mudou')
}
const ativar = async (ativo: boolean) => {
  await Mon.ativarMonitor(d.value!.config.id!, ativo)
  message.success(ativo ? 'Monitor retomado' : 'Monitor pausado')
  await recarregar()
  emit('mudou')
}
const excluir = async () => {
  await message.delConfirm(`Excluir o monitor "${d.value!.config.nome}" e todo o histórico dele?`)
  await Mon.excluirMonitor(d.value!.config.id!)
  message.success('Monitor excluído')
  visivel.value = false
  emit('mudou')
}
const copiar = async (texto: string) => {
  await navigator.clipboard.writeText(texto)
  message.success('Copiado')
}
</script>

<style lang="scss" scoped>
.numeros {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(110px, 1fr));
  gap: 8px;
  margin-bottom: 16px;

  div {
    display: flex;
    padding: 8px 10px;
    border: 1px solid var(--el-border-color-lighter);
    border-radius: 6px;
    flex-direction: column;
  }

  span {
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }

  b {
    font-size: 16px;
    font-weight: 600;
  }

  b.alerta {
    color: var(--el-color-warning);
  }
}
</style>
