<template>
  <div class="w-full">
    <div v-for="(a, i) in lista" :key="i" class="linha">
      <el-select v-model="a.tipo" class="!w-220px" @change="a.valor = valorInicial(a.tipo)">
        <el-option v-for="(x, k) in ACOES" :key="k" :label="x.rotulo" :value="k" />
      </el-select>
      <div class="min-w-0 flex-1">
        <UsuarioSelect
          v-if="ACOES[a.tipo]?.valor === 'agente'"
          v-model="a.valor"
          class="w-full"
          placeholder="Atendente"
        />
        <el-select
          v-else-if="ACOES[a.tipo]?.valor === 'etiquetas'"
          v-model="a.valor"
          class="w-full"
          multiple
          placeholder="Etiquetas"
        >
          <el-option v-for="e in etiquetas" :key="e.id" :label="e.nome" :value="e.id!" />
        </el-select>
        <el-select v-else-if="ACOES[a.tipo]?.valor === 'status'" v-model="a.valor" class="w-full">
          <el-option label="Aberta" value="aberta" />
          <el-option label="Pendente" value="pendente" />
          <el-option label="Resolvida" value="resolvida" />
        </el-select>
        <el-select
          v-else-if="ACOES[a.tipo]?.valor === 'prioridade'"
          v-model="a.valor"
          class="w-full"
        >
          <el-option :value="0" label="Nenhuma" />
          <el-option v-for="p in PRIORIDADES" :key="p.valor" :label="p.rotulo" :value="p.valor" />
        </el-select>
        <el-input
          v-else-if="ACOES[a.tipo]?.valor === 'texto'"
          v-model="a.valor"
          :autosize="{ minRows: 1, maxRows: 5 }"
          maxlength="4096"
          placeholder="Aceita {{contato.nome}}, {{conversa.id}} e {{atendente.nome}}"
          type="textarea"
        />
      </div>
      <el-button link type="danger" @click="lista.splice(i, 1)"
        ><Icon icon="ep:delete"
      /></el-button>
    </div>
    <el-button size="small" @click="lista.push({ tipo: 'adicionar_etiquetas', valor: [] })"
      ><Icon class="mr-4px" icon="ep:plus" />Ação</el-button
    >
  </div>
</template>

<script lang="ts" setup>
import UsuarioSelect from '@/components/UsuarioSelect/index.vue'
import * as Atd from '@/api/atendimento'
import { ACOES, PRIORIDADES } from '@/api/atendimento'

const lista = defineModel<Atd.Acao[]>({ required: true })
defineProps<{
  etiquetas: Atd.Etiqueta[]
}>()

const valorInicial = (tipo: string) => {
  const v = ACOES[tipo]?.valor
  return v === 'etiquetas' ? [] : v === 'status' ? 'resolvida' : v === 'prioridade' ? 3 : undefined
}
</script>

<style scoped>
.linha {
  display: flex;
  margin-bottom: 8px;
  align-items: flex-start;
  gap: 8px;
}
</style>
