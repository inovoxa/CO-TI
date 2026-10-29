<!-- Mensagens do quadro, com paginação. -->
<template>
  <div class="quadro-mensagens" v-loading="carregando && !dados">
    <el-scrollbar class="rolagem">
      <ListaMensagens
        :mensagens="dados?.lista ?? []"
        :colunas="quadro.colunas ?? []"
        :base="base"
        :carregando="carregando"
      />
    </el-scrollbar>
    <div v-if="dados && dados.total > tamanho" class="paginas">
      <el-pagination
        v-model:current-page="pagina"
        :page-size="tamanho"
        :pager-count="7"
        :total="Math.min(dados.total, 10000)"
        background
        layout="prev, pager, next"
        size="small"
      />
      <span class="total">
        {{ dados.total > dados.limiteTotal ? 'mais de ' : '' }}{{ Api.numero(Math.min(dados.total, dados.limiteTotal)) }}
        mensagens
      </span>
    </div>
    <div v-if="erro" class="erro">{{ erro }}</div>
  </div>
</template>

<script lang="ts" setup>
import * as Api from '@/api/logs'
import { filtroDoQuadro, useCarga } from '../contexto'
import ListaMensagens from './ListaMensagens.vue'

const props = defineProps<{ quadro: Api.Quadro }>()
const pagina = ref(1)
const tamanho = 20

const { dados, carregando, erro, recarregar, contexto } = useCarga(
  () => ({ ...props.quadro, _pagina: pagina.value }) as Api.Quadro,
  (f) => Api.getMensagens(f, pagina.value, tamanho)
)
defineExpose({ recarregar })

const base = computed(() => filtroDoQuadro(props.quadro, contexto))
watch([contexto.filtroGeral, contexto.periodoGeral], () => (pagina.value = 1))
</script>

<style lang="scss" scoped>
.quadro-mensagens {
  display: flex;
  height: 100%;
  min-height: 200px;
  flex-direction: column;
}

.rolagem {
  flex: 1;
  min-height: 0;
}

.paginas {
  display: flex;
  padding-top: 6px;
  align-items: center;
  justify-content: center;
  gap: 10px;
}

.total {
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.erro {
  font-size: 12px;
  color: var(--el-color-danger);
}
</style>
