<!-- Mensagens ao longo do tempo (barras). Clicar numa barra mostra as mensagens daquele intervalo. -->
<template>
  <div class="histograma" v-loading="carregando && !dados">
    <Echart v-if="dados" :options="opcoes" height="100%" @click="clicar" />
    <div v-if="erro" class="erro">{{ erro }}</div>
  </div>
</template>

<script lang="ts" setup>
import type { EChartsOption } from 'echarts'
import * as Api from '@/api/logs'
import { filtroDoQuadro, useCarga } from '../contexto'

const props = defineProps<{ quadro: Api.Quadro; altura?: number }>()

const { dados, carregando, erro, recarregar, contexto } = useCarga(
  () => props.quadro,
  (f) => Api.getHistograma(f, props.quadro.soma)
)
defineExpose({ recarregar })

const rotulo = (s: string) => {
  const d = new Date(s)
  const h = dados.value?.intervalo ?? 3600
  return h >= 86400
    ? d.toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' })
    : d.toLocaleString('pt-BR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' })
}

const opcoes = computed<EChartsOption>(() => ({
  grid: { left: 50, right: 12, top: 14, bottom: 28 },
  tooltip: {
    trigger: 'axis',
    axisPointer: { type: 'shadow' },
    valueFormatter: (v) => Api.valorDe(props.quadro.soma, Number(v))
  },
  xAxis: {
    type: 'category',
    data: (dados.value?.momentos ?? []).map(rotulo),
    axisLabel: { hideOverlap: true, fontSize: 11 }
  },
  yAxis: {
    type: 'value',
    splitLine: { lineStyle: { opacity: 0.25 } },
    axisLabel: { fontSize: 11, formatter: (v: number) => (props.quadro.soma ? Api.valorDe(props.quadro.soma, v) : String(v)) }
  },
  series: [
    {
      type: 'bar',
      name: props.quadro.soma ? 'Banda' : 'Mensagens',
      data: dados.value?.totais ?? [],
      barCategoryGap: '15%',
      itemStyle: { borderRadius: [2, 2, 0, 0] }
    }
  ]
}))

const clicar = (p: any) => {
  const d = dados.value
  if (!d || p?.dataIndex === undefined) return
  const de = new Date(d.momentos[p.dataIndex])
  const ate = new Date(de.getTime() + d.intervalo * 1000)
  const local = (x: Date) => {
    const z = new Date(x.getTime() - x.getTimezoneOffset() * 60000)
    return z.toISOString().slice(0, 19)
  }
  const f = filtroDoQuadro(props.quadro, contexto)
  contexto.mostrarMensagens(`${props.quadro.titulo}: ${rotulo(d.momentos[p.dataIndex])}`, {
    ...f,
    periodo: undefined,
    de: local(de),
    ate: local(ate)
  })
}
</script>

<style lang="scss" scoped>
.histograma {
  height: 100%;
  min-height: 200px;
}

.erro {
  font-size: 12px;
  color: var(--el-color-danger);
}
</style>
