<template>
  <ContentWrap>
    <!-- filas -->
    <div class="mb-12px flex flex-wrap items-center gap-8px">
      <el-check-tag
        v-for="f in filas"
        :key="f.valor"
        :checked="queryParams.fila === f.valor"
        @change="trocarFila(f.valor)"
      >
        {{ f.rotulo }}
      </el-check-tag>
    </div>
    <el-form
      ref="queryFormRef"
      :inline="true"
      :model="queryParams"
      class="-mb-15px"
      label-width="0"
    >
      <el-form-item prop="busca">
        <el-input
          v-model="queryParams.busca"
          class="!w-240px"
          clearable
          placeholder="Nº (#12) ou título"
          @keyup.enter="handleQuery"
        >
          <template #prefix><Icon icon="ep:search" /></template>
        </el-input>
      </el-form-item>
      <el-form-item prop="status">
        <el-select v-model="queryParams.status" class="!w-170px" clearable placeholder="Situação">
          <el-option
            v-for="s in statusItil(objeto)"
            :key="s.valor"
            :label="s.rotulo"
            :value="s.valor"
          />
        </el-select>
      </el-form-item>
      <el-form-item v-if="ehMudanca" prop="tipo">
        <el-select v-model="queryParams.tipo" class="!w-140px" clearable placeholder="Tipo">
          <el-option v-for="t in TIPOS_MUDANCA" :key="t.valor" :label="t.rotulo" :value="t.valor" />
        </el-select>
      </el-form-item>
      <el-form-item prop="prioridade">
        <el-select
          v-model="queryParams.prioridade"
          class="!w-140px"
          clearable
          placeholder="Prioridade"
        >
          <el-option v-for="p in PRIORIDADES" :key="p.valor" :label="p.rotulo" :value="p.valor" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon class="mr-5px" icon="ep:search" /> Buscar</el-button>
        <el-button @click="resetQuery"><Icon class="mr-5px" icon="ep:refresh" /> Limpar</el-button>
        <el-button
          v-hasPermi="[`itsm:${objeto}:update`]"
          plain
          type="primary"
          @click="formRef.open()"
        >
          <Icon class="mr-5px" icon="ep:plus" /> {{ ehMudanca ? 'Nova mudança' : 'Novo problema' }}
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table
      v-loading="loading"
      :data="list"
      :row-class-name="classeLinha"
      class="cursor-pointer"
      @row-click="(row) => detalheRef.open(row.id)"
    >
      <el-table-column label="Nº" prop="id" width="70">
        <template #default="{ row }">#{{ row.id }}</template>
      </el-table-column>
      <el-table-column label="Título" min-width="240" prop="titulo" show-overflow-tooltip />
      <el-table-column label="Situação" width="160">
        <template #default="{ row }">
          <el-tag :type="corStatusItil(objeto, row.status)" disable-transitions>{{
            row.statusRotulo
          }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column v-if="ehMudanca" label="Tipo" width="120">
        <template #default="{ row }">
          <el-tag :type="row.tipo === 3 ? 'danger' : 'info'" disable-transitions effect="plain">{{
            row.tipoRotulo
          }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column v-if="ehMudanca" label="Janela" min-width="200">
        <template #default="{ row }">
          <span v-if="row.inicioPrevisto"
            >{{ formatDate(row.inicioPrevisto, 'DD/MM HH:mm') }} –
            {{ formatDate(row.fimPrevisto, 'DD/MM HH:mm') }}</span
          >
          <span v-else class="text-[var(--el-text-color-placeholder)]">não marcada</span>
        </template>
      </el-table-column>
      <el-table-column v-if="ehMudanca" label="Aprovação" width="160">
        <template #default="{ row }">
          <el-tag
            v-if="row.validacao > 1"
            :type="corAprovacao(row.validacao)"
            disable-transitions
            >{{ row.validacaoRotulo }}</el-tag
          >
          <span v-else class="text-[var(--el-text-color-placeholder)]">—</span>
        </template>
      </el-table-column>
      <el-table-column label="Prioridade" width="110">
        <template #default="{ row }">
          <el-tag :type="corPrioridade(row.prioridade)" disable-transitions effect="plain">
            {{ row.prioridadeRotulo }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="Categoria" min-width="160" prop="categoria" show-overflow-tooltip />
      <el-table-column label="Técnico" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.tecnicos.length">{{ row.tecnicos.map((a) => a.nome).join(', ') }}</span>
          <span v-else class="text-[var(--el-text-color-placeholder)]">sem técnico</span>
        </template>
      </el-table-column>
      <el-table-column align="right" label="Chamados" prop="totalChamados" width="96" />
      <el-table-column :formatter="dateFormatter" label="Aberto em" prop="abertoEm" width="160" />
    </el-table>
    <Pagination
      v-model:limit="queryParams.pageSize"
      v-model:page="queryParams.pageNo"
      :total="total"
      @pagination="getList"
    />
  </ContentWrap>

  <ItilForm ref="formRef" :objeto="objeto" @success="aposCriar" />
  <ItilDetalhe ref="detalheRef" :objeto="objeto" @alterado="getList" />
</template>

<script lang="ts" setup>
import { dateFormatter, formatDate } from '@/utils/formatTime'
import * as ItsmApi from '@/api/itsm'
import {
  PRIORIDADES,
  TIPOS_MUDANCA,
  corAprovacao,
  corPrioridade,
  corStatusItil,
  statusItil,
  type ObjetoItil
} from '@/api/itsm'
import ItilForm from './ItilForm.vue'
import ItilDetalhe from './ItilDetalhe.vue'

defineOptions({ name: 'ItilLista' })
const props = defineProps<{ objeto: ObjetoItil }>()
const ehMudanca = computed(() => props.objeto === 'mudanca')

const filas = computed(() => [
  { valor: 'abertos', rotulo: ehMudanca.value ? 'Em andamento' : 'Em aberto' },
  { valor: 'meus', rotulo: 'Comigo' },
  { valor: 'minhas-equipes', rotulo: 'Minhas equipes' },
  ...(ehMudanca.value
    ? [
        { valor: 'aprovacao', rotulo: 'Em aprovação' },
        { valor: 'agenda', rotulo: 'Agenda (próximas janelas)' }
      ]
    : []),
  {
    valor: 'encerrados',
    rotulo: ehMudanca.value ? 'Aplicadas e encerradas' : 'Solucionados e fechados'
  },
  { valor: 'todos', rotulo: 'Todos' }
])

const route = useRoute()
const loading = ref(true)
const total = ref(0)
const list = ref<ItsmApi.ItemItil[]>([])
const queryParams = reactive({
  pageNo: 1,
  pageSize: 20,
  fila: 'abertos',
  busca: undefined as string | undefined,
  status: undefined,
  tipo: undefined,
  prioridade: undefined
})
const queryFormRef = ref()
const formRef = ref()
const detalheRef = ref()

const classeLinha = ({ row }: { row: ItsmApi.ItemItil }) =>
  row.atrasado || (row.conflitos && row.conflitos.length) ? 'linha-alerta' : ''

const getList = async () => {
  loading.value = true
  try {
    const data = await ItsmApi.getItilPage(props.objeto, queryParams)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}

const resetQuery = () => {
  queryFormRef.value.resetFields()
  handleQuery()
}

const trocarFila = (fila: string) => {
  queryParams.fila = fila
  handleQuery()
}

const aposCriar = async (id: number) => {
  await getList()
  detalheRef.value.open(id)
}

onMounted(async () => {
  await getList()
  // link direto: /servicedesk/problemas?id=12
  const id = Number(route.query.id)
  if (id) detalheRef.value.open(id)
})
</script>

<style scoped>
:deep(.linha-alerta td:first-child) {
  box-shadow: inset 3px 0 0 var(--el-color-danger);
}
</style>
