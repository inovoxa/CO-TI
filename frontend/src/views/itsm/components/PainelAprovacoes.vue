<template>
  <div v-if="itens.length" class="painel">
    <div class="mb-6px flex items-center gap-8px font-bold">
      <Icon icon="ep:select" /> Aprovação
      <el-tag v-if="rotulo" :type="corAprovacao(validacao)" disable-transitions size="small">{{
        rotulo
      }}</el-tag>
    </div>
    <div v-for="a in itens" :key="a.id" class="item">
      <div class="flex flex-wrap items-center gap-6px">
        <b>{{ a.aprovador }}</b>
        <el-tag :type="corAprovacao(a.status)" disable-transitions effect="plain" size="small">{{
          a.statusRotulo
        }}</el-tag>
        <span class="secundario"
          >pedido por {{ a.solicitadoPor || 'Sistema' }} em {{ formatDate(a.pedidoEm) }}</span
        >
      </div>
      <div v-if="a.comentarioPedido" class="secundario mt-2px">{{ a.comentarioPedido }}</div>
      <div v-if="a.respondidoEm" class="mt-2px">
        <span class="secundario">{{ formatDate(a.respondidoEm) }}:</span>
        {{ a.comentarioResposta || 'sem comentário' }}
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { formatDate } from '@/utils/formatTime'
import { corAprovacao, type Aprovacao } from '@/api/itsm'

defineProps<{ itens: Aprovacao[]; validacao?: number; rotulo?: string }>()
</script>

<style scoped>
.painel {
  padding: 10px 12px;
  margin-bottom: 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
}
.item + .item {
  padding-top: 6px;
  margin-top: 6px;
  border-top: 1px dashed var(--el-border-color-lighter);
}
.secundario {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
