<template>
  <el-empty v-if="!itens.length" :image-size="70" description="Nenhuma interação ainda" />
  <el-timeline v-else class="pl-4px">
    <el-timeline-item
      v-for="i in itens"
      :key="i.id"
      :color="cor(i)"
      :hollow="i.privado"
      :timestamp="formatDate(i.criadoEm)"
      placement="top"
    >
      <div class="item" :class="{ privado: i.privado, solucao: i.tipo === 'solucao' }">
        <div class="mb-6px flex flex-wrap items-center gap-6px text-13px">
          <b>{{ i.autor || 'Sistema' }}</b>
          <el-tag v-if="i.tipo === 'tarefa'" disable-transitions size="small" type="warning">
            Tarefa · {{ ESTADOS[i.estadoTarefa ?? 1] }}
          </el-tag>
          <el-tag
            v-if="i.tipo === 'solucao'"
            :type="corSolucao(i)"
            disable-transitions
            size="small"
          >
            Solução<template v-if="i.statusSolucao"> · {{ rotuloSolucao(i) }}</template>
          </el-tag>
          <el-tag v-if="i.privado" disable-transitions effect="plain" size="small" type="info">
            <Icon class="mr-2px" icon="ep:lock" />privado
          </el-tag>
          <span
            v-if="i.tipo === 'tarefa' && i.tempoGastoSeg"
            class="text-[var(--el-text-color-secondary)]"
          >
            {{ duracao(i.tempoGastoSeg) }} gastos
            <template v-if="i.tecnico"> · {{ i.tecnico }}</template>
          </span>
          <span
            v-if="i.tipo === 'tarefa' && i.inicio"
            class="text-[var(--el-text-color-secondary)]"
          >
            · planejada {{ formatDate(i.inicio)
            }}<template v-if="i.fim"> até {{ formatDate(i.fim) }}</template>
          </span>
        </div>
        <!-- conteúdo já saneado no servidor (sem script, eventos ou estilos) -->
        <div class="conteudo" v-html="i.conteudo"></div>
        <div
          v-if="i.avaliadoPorNome"
          class="mt-6px text-12px text-[var(--el-text-color-secondary)]"
        >
          {{ i.statusSolucao === 3 ? 'Aprovada' : 'Recusada' }} por {{ i.avaliadoPorNome }} em
          {{ formatDate(i.avaliadoEm!) }}
        </div>
      </div>
    </el-timeline-item>
  </el-timeline>
</template>

<script lang="ts" setup>
import { formatDate } from '@/utils/formatTime'
import { duracao, type Interacao } from '@/api/itsm'

defineOptions({ name: 'LinhaDoTempo' })
defineProps<{ itens: Interacao[] }>()

const ESTADOS = ['informativa', 'a fazer', 'feita']
const rotuloSolucao = (i: Interacao) =>
  i.statusSolucao === 3 ? 'aprovada' : i.statusSolucao === 4 ? 'recusada' : 'aguardando aprovação'
// problemas e mudanças não têm aprovação da solução (statusSolucao vazio)
const corSolucao = (i: Interacao) =>
  !i.statusSolucao || i.statusSolucao === 3
    ? 'success'
    : i.statusSolucao === 4
      ? 'danger'
      : 'primary'
const cor = (i: Interacao) =>
  i.tipo === 'solucao'
    ? 'var(--el-color-success)'
    : i.tipo === 'tarefa'
      ? 'var(--el-color-warning)'
      : 'var(--el-color-primary)'
</script>

<style scoped>
.item {
  padding: 10px 12px;
  background: var(--el-fill-color-lighter);
  border-radius: 6px;
}

.item.privado {
  background: var(--el-color-info-light-9);
  border: 1px dashed var(--el-border-color);
}

.item.solucao {
  border-left: 3px solid var(--el-color-success);
}

.conteudo :deep(p) {
  margin: 0 0 6px;
}

.conteudo :deep(img) {
  max-width: 100%;
}
</style>
