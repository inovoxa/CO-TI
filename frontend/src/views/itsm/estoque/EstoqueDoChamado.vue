<template>
  <div v-if="podeMovimentar || lista.length" class="caixa">
    <div class="mb-6px flex items-center justify-between">
      <span class="font-bold">Estoque</span>
      <el-button v-if="podeMovimentar" link size="small" type="primary" @click="abrir"
        >Registrar</el-button
      >
    </div>
    <div v-if="!lista.length" class="text-12px text-[var(--el-text-color-secondary)]">
      Nenhum item ligado a este chamado.
    </div>
    <div v-for="m in lista" :key="m.id" class="mb-6px text-12px">
      <b>{{ ROTULOS[m.tipo] }}</b
      >: {{ m.item }}<template v-if="m.pessoa"> · {{ m.pessoa }}</template>
      <el-button
        v-if="m.tipo === 'retirada' || m.tipo === 'devolucao'"
        class="ml-4px"
        link
        size="small"
        type="primary"
        @click="imprimirHtml(termoHtml(m))"
        >Termo</el-button
      >
    </div>

    <Dialog v-model="form.visivel" title="Registrar movimento no estoque" width="480px">
      <el-form label-position="top">
        <el-form-item label="Item" required>
          <el-select
            v-model="form.itemId"
            class="w-full"
            :filter-method="procurar"
            filterable
            placeholder="Descrição, IMEI, linha ou patrimônio"
            @change="form.tipo = ''"
          >
            <el-option
              v-for="i in itens"
              :key="i.id"
              :label="`${i.tipo}: ${i.descricao}${i.identificador ? ' · ' + i.identificador : ''}`"
              :value="i.id!"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="Movimento" required>
          <el-select v-model="form.tipo" class="w-full" :disabled="!escolhido">
            <el-option v-for="t in tiposDoItem" :key="t" :label="ROTULOS[t]" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="escolhido?.controle === 'quantidade'" label="Quantidade">
          <el-input-number v-model="form.quantidade" :min="1" />
        </el-form-item>
        <template v-if="form.tipo === 'retirada' || (form.tipo === 'devolucao' && escolhido?.controle === 'quantidade')">
          <el-form-item label="Pessoa" required>
            <el-input v-model="form.pessoa" maxlength="150" />
          </el-form-item>
          <el-form-item label="Setor ou unidade">
            <el-input v-model="form.destino" maxlength="150" />
          </el-form-item>
        </template>
        <el-form-item label="Motivo ou observação" :required="form.tipo === 'baixa' || form.tipo === 'manutencao'">
          <el-input v-model="form.motivo" maxlength="500" type="textarea" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="!form.itemId || !form.tipo" :loading="salvando" type="primary" @click="salvar"
          >Registrar</el-button
        >
        <el-button @click="form.visivel = false">Cancelar</el-button>
      </template>
    </Dialog>
  </div>
</template>

<script lang="ts" setup>
import * as Api from '@/api/itsm/estoque'
import { checkPermi } from '@/utils/permission'
import { imprimirHtml, termoHtml } from './impressao'

/** Movimentos do estoque ligados a um chamado; quem pode movimentar registra daqui. */
const props = defineProps<{ chamadoId: number }>()
const message = useMessage()
const podeMovimentar = checkPermi(['itsm:estoque:update'])

const ROTULOS: Record<string, string> = {
  entrada: 'Entrada',
  retirada: 'Retirada',
  devolucao: 'Devolução',
  manutencao: 'Manutenção',
  retorno: 'Retorno da manutenção',
  baixa: 'Baixa'
}

const lista = ref<Api.Movimento[]>([])
const itens = ref<Api.Item[]>([])
const salvando = ref(false)
const form = reactive<any>({ visivel: false, itemId: undefined, tipo: '', quantidade: 1, pessoa: '', destino: '', motivo: '' })

const escolhido = computed(() => itens.value.find((i) => i.id === form.itemId))
const tiposDoItem = computed<string[]>(() => {
  const i = escolhido.value
  if (!i) return []
  if (i.controle === 'quantidade') return ['entrada', 'retirada', 'devolucao', 'baixa']
  return (
    {
      disponivel: ['retirada', 'manutencao', 'baixa'],
      em_uso: ['devolucao', 'manutencao', 'baixa'],
      manutencao: ['retorno', 'baixa']
    }[i.situacao as string] || []
  )
})

const carregar = async () => {
  lista.value = await Api.getDoChamado(props.chamadoId)
}
const procurar = async (busca: string) => {
  const r = await Api.getPagina({ pageNo: 1, pageSize: 30, busca })
  itens.value = r.list.filter((i) => i.situacao !== 'baixado')
}
const abrir = async () => {
  Object.assign(form, { visivel: true, itemId: undefined, tipo: '', quantidade: 1, pessoa: '', destino: '', motivo: '' })
  await procurar('')
}
const salvar = async () => {
  salvando.value = true
  try {
    await Api.movimentar({
      itemId: form.itemId,
      tipo: form.tipo,
      quantidade: form.quantidade,
      pessoa: form.pessoa,
      destino: form.destino,
      motivo: form.motivo,
      chamadoId: props.chamadoId
    })
    form.visivel = false
    message.success('Movimento registrado')
    await carregar()
  } finally {
    salvando.value = false
  }
}

watch(() => props.chamadoId, carregar, { immediate: true })
</script>

<style scoped>
.caixa {
  padding: 10px 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
}
</style>
