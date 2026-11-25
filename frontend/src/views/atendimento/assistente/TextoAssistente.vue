<!-- Um texto ou opção do assistente virtual: edição, variáveis, restaurar o padrão e a prévia como sai no WhatsApp. -->
<template>
  <div class="item">
    <div class="topo">
      <b>{{ item.rotulo }}</b>
      <el-tag v-if="alterado" class="ml-6px" disable-transitions size="small" type="warning"
        >personalizado</el-tag
      >
      <span v-if="item.alterado && item.atualizadoEm" class="dica ml-6px">
        {{ item.atualizadoPor ? item.atualizadoPor + ', ' : '' }}{{ quando(item.atualizadoEm) }}
      </span>
      <span class="flex-1"></span>
      <el-button v-if="alterado" link size="small" type="primary" @click="restaurar"
        >Restaurar o padrão</el-button
      >
    </div>
    <div v-if="item.ajuda" class="dica mb-6px">{{ item.ajuda }}</div>

    <el-switch
      v-if="item.tipo === 'SIM_NAO'"
      :model-value="modelValue === 'true'"
      active-text="Sim"
      inactive-text="Não"
      @update:model-value="(v) => emit('update:modelValue', String(v))"
    />
    <el-input-number
      v-else-if="item.tipo === 'NUMERO'"
      :max="item.maximo"
      :min="item.minimo"
      :model-value="Number(modelValue)"
      @update:model-value="(v) => emit('update:modelValue', String(v ?? item.padrao))"
    />
    <div v-else class="grade">
      <div>
        <el-input
          ref="campo"
          :autosize="{ minRows: 2, maxRows: 14 }"
          :model-value="modelValue"
          maxlength="4000"
          type="textarea"
          @update:model-value="(v) => emit('update:modelValue', v)"
        />
        <div v-if="item.variaveis.length" class="mt-4px">
          <span class="dica mr-4px">Variáveis:</span>
          <el-tag
            v-for="v in item.variaveis"
            :key="v"
            class="var"
            disable-transitions
            size="small"
            type="info"
            @click="inserir(v)"
            >{{ '{' + v + '}' }}</el-tag
          >
        </div>
      </div>
      <div>
        <div class="dica mb-2px">Prévia (dados de exemplo)</div>
        <!-- formatarWhats escapa o texto antes de formatar -->
        <div class="balao" v-html="formatarWhats(preencher(modelValue, EXEMPLOS))"></div>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import type { TextoAssistente } from '@/api/atendimento'
import { EXEMPLOS, formatarWhats, preencher } from './textos'

const props = defineProps<{ item: TextoAssistente; modelValue: string }>()
const emit = defineEmits<{ 'update:modelValue': [v: string] }>()
const campo = ref()

const alterado = computed(() => props.modelValue !== props.item.padrao)
const quando = (d: string) =>
  new Date(d).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' })

const restaurar = () => emit('update:modelValue', props.item.padrao)

/** Insere a variável onde está o cursor (ou no fim). */
const inserir = (v: string) => {
  const ta: HTMLTextAreaElement | undefined = campo.value?.textarea
  const texto = props.modelValue || ''
  const ini = ta?.selectionStart ?? texto.length
  const fim = ta?.selectionEnd ?? texto.length
  const novo = texto.slice(0, ini) + '{' + v + '}' + texto.slice(fim)
  emit('update:modelValue', novo)
  nextTick(() => {
    ta?.focus()
    const pos = ini + v.length + 2
    ta?.setSelectionRange(pos, pos)
  })
}
</script>

<style lang="scss" scoped>
.item {
  padding: 12px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.topo {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 2px;
  margin-bottom: 4px;
}
.grade {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 12px;
}
@media (max-width: 900px) {
  .grade {
    grid-template-columns: minmax(0, 1fr);
  }
}
.var {
  margin: 2px 4px 2px 0;
  cursor: pointer;
}
.balao {
  max-width: 420px;
  padding: 8px 10px;
  font-size: 13px;
  line-height: 1.45;
  white-space: pre-wrap;
  word-break: break-word;
  background: var(--el-color-success-light-9);
  border: 1px solid var(--el-color-success-light-7);
  border-radius: 8px 8px 8px 2px;
}
.dica {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
