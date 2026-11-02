<template>
  <ContentWrap>
    <!-- filas -->
    <div class="mb-12px flex flex-wrap items-center gap-8px">
      <el-check-tag
        v-for="f in FILAS"
        :key="f.valor"
        :checked="queryParams.fila === f.valor"
        @change="trocarFila(f.valor)"
      >
        {{ f.rotulo }}
        <span v-if="f.contagem && contagens[f.contagem] !== undefined" class="ml-4px opacity-70">
          {{ contagens[f.contagem] }}
        </span>
      </el-check-tag>
    </div>
    <el-form ref="queryFormRef" :inline="true" :model="queryParams" class="-mb-15px" label-width="0">
      <el-form-item prop="busca">
        <el-input
          v-model="queryParams.busca"
          class="!w-260px"
          clearable
          placeholder="Nº (#123) ou título"
          @keyup.enter="handleQuery"
        >
          <template #prefix><Icon icon="ep:search" /></template>
        </el-input>
      </el-form-item>
      <el-form-item prop="prioridade">
        <el-select v-model="queryParams.prioridade" class="!w-150px" clearable placeholder="Prioridade">
          <el-option v-for="p in PRIORIDADES" :key="p.valor" :label="p.rotulo" :value="p.valor" />
        </el-select>
      </el-form-item>
      <el-form-item prop="categoriaId">
        <el-select
          v-model="queryParams.categoriaId"
          class="!w-260px"
          clearable
          filterable
          placeholder="Categoria"
        >
          <el-option v-for="c in categorias" :key="c.id" :label="c.nomeCompleto" :value="c.id" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon class="mr-5px" icon="ep:search" /> Buscar</el-button>
        <el-button @click="resetQuery"><Icon class="mr-5px" icon="ep:refresh" /> Limpar</el-button>
        <el-button
          v-hasPermi="['itsm:chamado:exportar']"
          :loading="exportando"
          @click="exportarExcel"
        >
          <Icon class="mr-5px" icon="ep:download" /> Excel
        </el-button>
        <el-button v-hasPermi="['itsm:chamado:create']" plain type="primary" @click="formRef.open()">
          <Icon class="mr-5px" icon="ep:plus" /> Novo chamado
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
      <el-table-column label="Nº" prop="id" width="80">
        <template #default="{ row }">#{{ row.id }}</template>
      </el-table-column>
      <el-table-column label="Título" min-width="260" prop="titulo" show-overflow-tooltip>
        <template #default="{ row }">
          <Icon v-if="row.origemAutomatica" class="mr-4px" icon="ep:monitor" />{{ row.titulo }}
        </template>
      </el-table-column>
      <el-table-column label="Situação" width="170">
        <template #default="{ row }">
          <el-tag :type="corStatus(row.status)" disable-transitions>{{ row.statusRotulo }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="Prioridade" width="120">
        <template #default="{ row }">
          <el-tag :type="corPrioridade(row.prioridade)" disable-transitions effect="plain">
            {{ row.prioridadeRotulo }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="Categoria" min-width="180" prop="categoria" show-overflow-tooltip />
      <el-table-column label="Solicitante" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">{{ nomes(row.solicitantes) }}</template>
      </el-table-column>
      <el-table-column label="Técnico" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.tecnicos.length">{{ nomes(row.tecnicos) }}</span>
          <span v-else class="text-[var(--el-text-color-placeholder)]">sem técnico</span>
        </template>
      </el-table-column>
      <el-table-column label="Unidade" min-width="130" prop="dept" show-overflow-tooltip />
      <el-table-column :formatter="dateFormatter" label="Aberto em" prop="abertoEm" width="160" />
      <el-table-column label="Prazo" width="160">
        <template #default="{ row }">
          <span v-if="row.prazoSolucao" :class="row.slaVencido ? 'font-bold text-[var(--el-color-danger)]' : ''">
            {{ formatDate(row.prazoSolucao) }}
          </span>
          <span v-else class="text-[var(--el-text-color-placeholder)]">—</span>
        </template>
      </el-table-column>
    </el-table>
    <Pagination
      v-model:limit="queryParams.pageSize"
      v-model:page="queryParams.pageNo"
      :total="total"
      @pagination="getList"
    />
  </ContentWrap>

  <ChamadoForm ref="formRef" @success="aposMudanca" />
  <ChamadoDetalhe ref="detalheRef" @alterado="aposMudanca" />
</template>

<script lang="ts" setup>
import { dateFormatter, formatDate } from '@/utils/formatTime'
import download from '@/utils/download'
import * as ItsmApi from '@/api/itsm'
import { PRIORIDADES, corPrioridade, corStatus } from '@/api/itsm'
import ChamadoForm from './ChamadoForm.vue'
import ChamadoDetalhe from './ChamadoDetalhe.vue'

defineOptions({ name: 'ItsmChamado' })

const FILAS = [
  { valor: 'abertos', rotulo: 'Em aberto', contagem: 'abertos' },
  { valor: 'novos', rotulo: 'Novos', contagem: 'novos' },
  { valor: 'meus', rotulo: 'Comigo' },
  { valor: 'minhas-equipes', rotulo: 'Minhas equipes' },
  { valor: 'pendentes', rotulo: 'Pendentes', contagem: 'pendentes' },
  { valor: 'sla', rotulo: 'Prazo vencido', contagem: 'sla' },
  { valor: 'solucionados', rotulo: 'Solucionados', contagem: 'solucionados' },
  { valor: 'fechados', rotulo: 'Fechados' },
  { valor: 'todos', rotulo: 'Todos' }
]

const route = useRoute()
const loading = ref(true)
const total = ref(0)
const list = ref<ItsmApi.Chamado[]>([])
const contagens = ref<Record<string, number>>({})
const categorias = ref<ItsmApi.Categoria[]>([])
const queryParams = reactive({
  pageNo: 1,
  pageSize: 20,
  fila: 'abertos',
  busca: undefined as string | undefined,
  prioridade: undefined,
  categoriaId: undefined
})
const queryFormRef = ref()
const formRef = ref()
const detalheRef = ref()

const exportando = ref(false)
const exportarExcel = async () => {
  exportando.value = true
  try {
    download.excel(
      await ItsmApi.exportarChamados(queryParams),
      `chamados-${formatDate(new Date(), 'YYYY-MM-DD')}.xlsx`
    )
  } finally {
    exportando.value = false
  }
}

const nomes = (atores: ItsmApi.Ator[]) => atores.map((a) => a.nome).join(', ')
const classeLinha = ({ row }: { row: ItsmApi.Chamado }) => (row.slaVencido ? 'linha-vencida' : '')

const getList = async () => {
  loading.value = true
  try {
    const [data, c] = await Promise.all([ItsmApi.getChamadoPage(queryParams), ItsmApi.getContagens()])
    list.value = data.list
    total.value = data.total
    contagens.value = c
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

const aposMudanca = () => getList()

onMounted(async () => {
  categorias.value = await ItsmApi.getCategorias()
  await getList()
  // link direto: /servicedesk/chamados?id=123
  const id = Number(route.query.id)
  if (id) detalheRef.value.open(id)
})
</script>

<style scoped>
:deep(.linha-vencida td:first-child) {
  box-shadow: inset 3px 0 0 var(--el-color-danger);
}
</style>
