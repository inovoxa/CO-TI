<!-- Contador do Graylog: número grande e a variação em relação ao período anterior. -->
<template>
  <div class="contador" v-loading="carregando && !dados">
    <div class="numero" :class="{ apagado: carregando }">{{ dados ? Api.valorDe(quadro.soma, dados.valor) : '–' }}</div>
    <div class="rodape">
      <div v-if="dados && dados.anterior !== undefined && dados.anterior !== null" class="variacao">
        <Icon :icon="icone" :size="18" />
        <span>{{ diferenca }}</span>
      </div>
      <div class="periodo">{{ dados?.rotuloPeriodo }}</div>
    </div>
    <div v-if="erro" class="erro">{{ erro }}</div>
  </div>
</template>

<script lang="ts" setup>
import * as Api from '@/api/logs'
import { useCarga } from '../contexto'

const props = defineProps<{ quadro: Api.Quadro }>()

const { dados, carregando, erro, recarregar } = useCarga(
  () => props.quadro,
  (f) => Api.getContagem(f, props.quadro.distinto, props.quadro.soma)
)
defineExpose({ recarregar })

const delta = computed(() => (dados.value ? dados.value.valor - (dados.value.anterior ?? 0) : 0))
const icone = computed(() =>
  delta.value > 0
    ? 'ep:circle-plus-filled'
    : delta.value < 0
      ? 'ep:remove-filled'
      : 'ep:right'
)
const diferenca = computed(() => {
  const d = dados.value
  if (!d) return ''
  const sinal = delta.value > 0 ? '+' : delta.value < 0 ? '−' : '+'
  const pct = d.anterior
    ? `${sinal}${Math.abs((delta.value / d.anterior) * 100).toLocaleString('pt-BR', { maximumFractionDigits: 2 })}%`
    : '--'
  return `${sinal}${Api.valorDe(props.quadro.soma, Math.abs(delta.value))} / ${pct}`
})
</script>

<style lang="scss" scoped>
.contador {
  position: relative;
  display: flex;
  height: 100%;
  min-height: 150px;
  flex-direction: column;
}

.numero {
  display: flex;
  font-size: 54px;
  font-weight: 300;
  line-height: 1;
  transition: opacity 0.2s;
  flex: 1;
  align-items: center;
  justify-content: center;
  font-variant-numeric: tabular-nums;

  &.apagado {
    opacity: 0.6;
  }
}

.rodape {
  text-align: right;
}

.variacao {
  display: inline-flex;
  font-size: 15px;
  color: var(--el-text-color-regular);
  align-items: center;
  gap: 6px;
}

.periodo {
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.erro {
  font-size: 12px;
  color: var(--el-color-danger);
}
</style>
