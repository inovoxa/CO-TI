<!-- Últimas verificações como barrinhas coloridas (mais antiga à esquerda), com o detalhe ao passar o mouse -->
<template>
  <div :aria-label="`últimas ${historico.length} verificações`" class="batidas">
    <i v-for="i in vazias" :key="`v${i}`" class="vazia"></i>
    <el-tooltip
      v-for="(b, i) in historico"
      :key="i"
      :show-after="150"
      placement="top"
      :content="dica(b)"
    >
      <i :class="`e${b.estado}`"></i>
    </el-tooltip>
  </div>
</template>

<script lang="ts" setup>
import type { Batida } from '@/api/monitoramento'
import { ESTADOS, quando } from '@/api/monitoramento'

const props = withDefaults(defineProps<{ historico: Batida[]; total?: number }>(), { total: 40 })
const vazias = computed(() => Math.max(0, props.total - props.historico.length))
const dica = (b: Batida) =>
  `${quando(b.momento)} · ${ESTADOS[b.estado]?.rotulo ?? '—'}` +
  (b.latenciaMs != null ? ` · ${b.latenciaMs} ms` : '') +
  (b.msg ? ` · ${b.msg}` : '')
</script>

<style lang="scss" scoped>
.batidas {
  display: flex;
  gap: 2px;
  align-items: center;

  i {
    display: block;
    width: 5px;
    height: 18px;
    background: var(--el-border-color);
    border-radius: 2px;
  }

  i.e0 {
    background: var(--el-color-danger);
  }

  i.e1 {
    background: var(--el-color-success);
  }

  i.e2 {
    background: var(--el-color-warning);
  }

  i.e3 {
    background: var(--el-color-info);
  }

  i.vazia {
    background: var(--el-fill-color);
  }
}
</style>
