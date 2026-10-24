<template>
  <ContentWrap>
    <el-form :inline="true" class="-mb-15px" label-width="0">
      <el-form-item>
        <el-input
          v-model="q.busca"
          class="!w-260px"
          clearable
          placeholder="Nome ou tipo"
          @keyup.enter="buscar"
        >
          <template #prefix><Icon icon="ep:search" /></template>
        </el-input>
      </el-form-item>
      <el-form-item>
        <el-button @click="buscar"><Icon class="mr-5px" icon="ep:search" /> Buscar</el-button>
        <el-button v-if="pode" plain type="primary" @click="abrirForm()">
          <Icon class="mr-5px" icon="ep:plus" /> Novo orçamento
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table
      v-loading="loading"
      :data="lista"
      class="cursor-pointer"
      @row-click="(r) => abrir(r.id)"
    >
      <el-table-column label="Orçamento" min-width="200" prop="nome" show-overflow-tooltip />
      <el-table-column label="Tipo" prop="tipo" width="120" />
      <el-table-column label="Período" width="200">
        <template #default="{ row }">{{ data(row.inicio) }} a {{ data(row.fim) }}</template>
      </el-table-column>
      <el-table-column label="Unidade" min-width="130" prop="dept" show-overflow-tooltip />
      <el-table-column align="right" label="Valor" width="140">
        <template #default="{ row }">{{ reais(row.valor) }}</template>
      </el-table-column>
      <el-table-column label="Usado" min-width="200">
        <template #default="{ row }">
          <el-progress
            :format="() => `${row.percentualGasto}%`"
            :percentage="Math.min(100, row.percentualGasto)"
            :color="
              row.percentualGasto > 100
                ? 'var(--el-color-danger)'
                : row.percentualGasto >= 90
                  ? 'var(--el-color-warning)'
                  : undefined
            "
            :stroke-width="10"
          />
        </template>
      </el-table-column>
      <el-table-column align="right" label="Saldo" width="140">
        <template #default="{ row }">
          <span :class="{ 'font-bold text-[var(--el-color-danger)]': row.saldo < 0 }">{{
            reais(row.saldo)
          }}</span>
        </template>
      </el-table-column>
    </el-table>
    <Pagination
      v-model:limit="q.pageSize"
      v-model:page="q.pageNo"
      :total="total"
      @pagination="carregar"
    />
  </ContentWrap>

  <el-drawer v-model="detalhe.visivel" :size="largura" destroy-on-close>
    <template #header>
      <div v-if="detalhe.o" class="truncate text-17px font-bold">{{ detalhe.o.nome }}</div>
    </template>
    <template v-if="detalhe.o">
      <div class="mb-12px flex flex-wrap gap-8px">
        <el-button v-if="pode" @click="abrirForm(detalhe.o)"
          ><Icon class="mr-4px" icon="ep:edit" />Editar</el-button
        >
        <el-button v-if="pode" plain type="danger" @click="excluir"
          ><Icon class="mr-4px" icon="ep:delete" />Excluir</el-button
        >
      </div>
      <div class="tiles">
        <div class="tile">
          <div class="rotulo">Valor</div>
          <div class="valor">{{ reais(detalhe.o.valor) }}</div>
        </div>
        <div class="tile">
          <div class="rotulo">Usado</div>
          <div class="valor">{{ reais(detalhe.o.gasto) }}</div>
          <div class="nota">{{ detalhe.o.percentualGasto }}% do valor</div>
        </div>
        <div class="tile">
          <div class="rotulo">Saldo</div>
          <div class="valor" :class="{ negativo: detalhe.o.saldo! < 0 }">{{
            reais(detalhe.o.saldo)
          }}</div>
        </div>
      </div>
      <el-descriptions :column="2" border class="mb-12px" size="small">
        <el-descriptions-item label="Período"
          >{{ data(detalhe.o.inicio) }} a {{ data(detalhe.o.fim) }}</el-descriptions-item
        >
        <el-descriptions-item label="Unidade">{{ detalhe.o.dept || '—' }}</el-descriptions-item>
        <el-descriptions-item v-if="detalhe.o.observacao" :span="2" label="Observação">{{
          detalhe.o.observacao
        }}</el-descriptions-item>
      </el-descriptions>
      <div class="mb-6px text-13px font-bold">Custos lançados neste orçamento</div>
      <el-table :data="detalhe.o.custos" size="small">
        <el-table-column label="Origem" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ ORIGEM[row.objeto] }}: {{ row.origem }}</template>
        </el-table-column>
        <el-table-column label="Descrição" min-width="160" prop="nome" show-overflow-tooltip />
        <el-table-column label="Data" width="110">
          <template #default="{ row }">{{ data(row.inicio) }}</template>
        </el-table-column>
        <el-table-column align="right" label="Valor" width="130">
          <template #default="{ row }">{{ reais(row.valor) }}</template>
        </el-table-column>
      </el-table>
    </template>
  </el-drawer>

  <Dialog
    v-model="form.visivel"
    :title="form.dados.id ? 'Editar orçamento' : 'Novo orçamento'"
    width="560px"
  >
    <el-form ref="formRef" :model="form.dados" :rules="regras" label-width="100px">
      <el-form-item label="Nome" prop="nome"
        ><el-input v-model="form.dados.nome" maxlength="150"
      /></el-form-item>
      <el-form-item label="Tipo">
        <el-select v-model="form.dados.tipo" allow-create class="w-full" clearable filterable>
          <el-option v-for="t in ['Custeio', 'Investimento']" :key="t" :label="t" :value="t" />
        </el-select>
      </el-form-item>
      <el-form-item label="Período" prop="periodo">
        <el-date-picker
          v-model="periodo"
          end-placeholder="Fim"
          start-placeholder="Início"
          type="daterange"
          value-format="YYYY-MM-DD"
        />
      </el-form-item>
      <el-form-item label="Valor (R$)" prop="valor">
        <el-input-number v-model="form.dados.valor" :min="0" :precision="2" :step="1000" />
      </el-form-item>
      <el-form-item label="Unidade">
        <el-tree-select
          v-model="form.dados.deptId"
          :data="depts"
          :props="defaultProps"
          check-strictly
          class="w-full"
          clearable
          node-key="id"
          placeholder="A minha"
        />
      </el-form-item>
      <el-form-item label="Observação"
        ><el-input v-model="form.dados.observacao" :rows="2" type="textarea"
      /></el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" @click="salvar">Salvar</el-button>
      <el-button @click="form.visivel = false">Cancelar</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import * as Gov from '@/api/itsm/governanca'
import { data, reais } from '@/api/itsm/governanca'
import * as DeptApi from '@/api/system/dept'
import { defaultProps, handleTree } from '@/utils/tree'
import { checkPermi } from '@/utils/permission'

defineOptions({ name: 'ItsmOrcamento' })
const message = useMessage()
const pode = checkPermi(['itsm:orcamento:update'])
const ORIGEM: Record<string, string> = {
  contrato: 'Contrato',
  projeto: 'Projeto',
  licenca: 'Licença'
}
const largura = computed(() => (window.innerWidth < 1100 ? '100%' : '55%'))

const loading = ref(true)
const lista = ref<Gov.Orcamento[]>([])
const total = ref(0)
const depts = ref<any[]>([])
const q = reactive({ pageNo: 1, pageSize: 20, busca: '' })
const detalhe = reactive({ visivel: false, o: undefined as Gov.Orcamento | undefined })
const formRef = ref()
const form = reactive({ visivel: false, dados: {} as Gov.Orcamento })
const periodo = computed<string[]>({
  get: () => (form.dados.inicio ? [form.dados.inicio, form.dados.fim] : []),
  set: (v) => {
    form.dados.inicio = v?.[0]
    form.dados.fim = v?.[1]
  }
})
const regras = {
  nome: [{ required: true, message: 'Informe o nome', trigger: 'blur' }],
  periodo: [
    {
      validator: (_: any, __: any, cb: any) =>
        form.dados.inicio ? cb() : cb(new Error('Informe o período'))
    }
  ],
  valor: [{ required: true, message: 'Informe o valor', trigger: 'change' }]
}

const carregar = async () => {
  loading.value = true
  try {
    const r = await Gov.getOrcamentos(q)
    lista.value = r.list
    total.value = r.total
  } finally {
    loading.value = false
  }
}
const buscar = () => {
  q.pageNo = 1
  carregar()
}

const abrir = async (id: number) => {
  detalhe.o = await Gov.getOrcamento(id)
  detalhe.visivel = true
}

const abrirForm = async (o?: Gov.Orcamento) => {
  const ano = new Date().getFullYear()
  form.dados = o ? { ...o } : { nome: '', inicio: `${ano}-01-01`, fim: `${ano}-12-31`, valor: 0 }
  form.visivel = true
  if (!depts.value.length) depts.value = handleTree(await DeptApi.getSimpleDeptList())
}

const salvar = async () => {
  await formRef.value.validate()
  const id = await Gov.salvarOrcamento(form.dados)
  message.success('Orçamento salvo')
  form.visivel = false
  await carregar()
  await abrir(id)
}

const excluir = async () => {
  await message.delConfirm(`Excluir o orçamento ${detalhe.o!.nome}?`)
  await Gov.excluirOrcamento(detalhe.o!.id!)
  detalhe.visivel = false
  await carregar()
}

onMounted(carregar)
</script>

<style scoped>
.tiles {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-bottom: 12px;
}
.tile {
  padding: 12px 14px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}
.rotulo,
.nota {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.valor {
  margin: 2px 0;
  font-size: 20px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.valor.negativo {
  color: var(--el-color-danger);
}
</style>
