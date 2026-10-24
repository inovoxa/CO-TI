<template>
  <div>
    <div class="mb-8px flex items-center justify-between">
      <span class="text-13px text-[var(--el-text-color-secondary)]">
        Total: <b class="text-[var(--el-text-color-primary)]">{{ reais(total) }}</b>
      </span>
      <el-button v-if="editavel" size="small" @click="abrir()">
        <Icon class="mr-4px" icon="ep:plus" />Lançar custo
      </el-button>
    </div>
    <el-table :data="lista" size="small">
      <el-table-column label="Descrição" min-width="180" prop="nome" show-overflow-tooltip />
      <el-table-column label="Período" width="190">
        <template #default="{ row }"
          >{{ data(row.inicio)
          }}<template v-if="row.fim"> a {{ data(row.fim) }}</template></template
        >
      </el-table-column>
      <el-table-column align="right" label="Valor" width="130">
        <template #default="{ row }">{{ reais(row.valor) }}</template>
      </el-table-column>
      <el-table-column label="Orçamento" min-width="140" prop="orcamento" show-overflow-tooltip />
      <el-table-column v-if="editavel" width="90">
        <template #default="{ row }">
          <el-button link type="primary" @click="abrir(row)"><Icon icon="ep:edit" /></el-button>
          <el-button link type="danger" @click="excluir(row)"><Icon icon="ep:delete" /></el-button>
        </template>
      </el-table-column>
    </el-table>

    <Dialog
      v-model="form.visivel"
      :title="form.dados.id ? 'Editar custo' : 'Lançar custo'"
      width="480px"
    >
      <el-form :model="form.dados" label-width="100px">
        <el-form-item label="Descrição"
          ><el-input v-model="form.dados.nome" maxlength="150"
        /></el-form-item>
        <el-form-item label="Data">
          <el-date-picker v-model="form.dados.inicio" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="Até">
          <el-date-picker
            v-model="form.dados.fim"
            placeholder="Custo de um dia só"
            type="date"
            value-format="YYYY-MM-DD"
          />
        </el-form-item>
        <el-form-item label="Valor (R$)">
          <el-input-number v-model="form.dados.valor" :min="0" :precision="2" :step="100" />
        </el-form-item>
        <el-form-item label="Orçamento">
          <el-select v-model="form.dados.orcamentoId" class="w-full" clearable filterable>
            <el-option v-for="o in orcamentos" :key="o.id" :label="o.nome" :value="o.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="salvar">Salvar</el-button>
        <el-button @click="form.visivel = false">Cancelar</el-button>
      </template>
    </Dialog>
  </div>
</template>

<script lang="ts" setup>
import * as Gov from '@/api/itsm/governanca'
import { data, reais } from '@/api/itsm/governanca'
import { formatDate } from '@/utils/formatTime'

defineOptions({ name: 'CustosTabela' })
const props = defineProps<{
  objeto: 'contrato' | 'projeto' | 'licenca'
  objetoId: number
  editavel: boolean
}>()
const emit = defineEmits(['alterado'])
const message = useMessage()

const lista = ref<Gov.Custo[]>([])
const orcamentos = ref<Gov.Resumo[]>([])
const total = computed(() => lista.value.reduce((s, c) => s + Number(c.valor), 0))
const form = reactive({ visivel: false, dados: {} as Gov.Custo })

const carregar = async () => {
  lista.value = await Gov.getCustos(props.objeto, props.objetoId)
}
watch(() => props.objetoId, carregar, { immediate: true })

const abrir = async (c?: Gov.Custo) => {
  form.dados = c
    ? { ...c }
    : {
        objeto: props.objeto,
        objetoId: props.objetoId,
        nome: '',
        inicio: formatDate(new Date(), 'YYYY-MM-DD'),
        valor: 0
      }
  form.visivel = true
  if (!orcamentos.value.length) orcamentos.value = await Gov.getOrcamentosSimples()
}

const salvar = async () => {
  await Gov.salvarCusto(form.dados)
  form.visivel = false
  await carregar()
  emit('alterado')
}

const excluir = async (c: Gov.Custo) => {
  await message.delConfirm(`Excluir o custo "${c.nome}"?`)
  await Gov.excluirCusto(c.id!)
  await carregar()
  emit('alterado')
}
</script>
