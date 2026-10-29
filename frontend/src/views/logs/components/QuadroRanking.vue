<!-- Ranking: valores mais frequentes de um campo; clique no valor para as ações. -->
<template>
  <div class="ranking" v-loading="carregando && !dados">
    <div class="cab">
      <span>{{ quadro.campo }}</span>
      <span>{{ quadro.soma ? 'banda' : 'mensagens' }}</span>
    </div>
    <div class="itens">
      <div v-for="it in dados?.itens ?? []" :key="it.valor" class="item">
        <div class="barra" :style="{ width: pct(it.total) + '%' }"></div>
        <ValorClicavel class="valor" :campo="quadro.campo!" :valor="it.valor" :base="base" />
        <span class="total">{{ Api.valorDe(quadro.soma, it.total) }}</span>
      </div>
      <el-empty v-if="dados && !dados.itens.length" :image-size="50" description="Nada no período" />
    </div>
    <div v-if="erro" class="erro">{{ erro }}</div>
  </div>
</template>

<script lang="ts" setup>
import * as Api from '@/api/logs'
import { filtroDoQuadro, useCarga } from '../contexto'
import ValorClicavel from './ValorClicavel.vue'

const props = defineProps<{ quadro: Api.Quadro }>()

const { dados, carregando, erro, recarregar, contexto } = useCarga(
  () => props.quadro,
  (f) => Api.getRanking(f, props.quadro.campo!, props.quadro.limite || 10, props.quadro.soma)
)
defineExpose({ recarregar })

const base = computed(() => filtroDoQuadro(props.quadro, contexto))
const maior = computed(() => dados.value?.itens[0]?.total || 1)
const pct = (n: number) => Math.max(2, (n / maior.value) * 100)
</script>

<style lang="scss" scoped>
.ranking {
  display: flex;
  height: 100%;
  min-height: 150px;
  font-size: 13px;
  flex-direction: column;
}

.cab {
  display: flex;
  padding: 6px 10px;
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color);
  justify-content: space-between;
}

.itens {
  overflow-y: auto;
  flex: 1;
}

.item {
  position: relative;
  display: flex;
  padding: 7px 10px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  justify-content: space-between;
  gap: 10px;

  &:nth-child(even) {
    background: var(--el-fill-color-lighter);
  }
}

.barra {
  position: absolute;
  top: 0;
  bottom: 0;
  left: 0;
  pointer-events: none;
  background: var(--el-color-primary);
  opacity: 0.1;
}

.valor {
  position: relative;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.total {
  position: relative;
  font-variant-numeric: tabular-nums;
}

.erro {
  font-size: 12px;
  color: var(--el-color-danger);
}
</style>
