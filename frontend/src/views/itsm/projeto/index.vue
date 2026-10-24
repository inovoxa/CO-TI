<template>
  <ContentWrap>
    <div class="mb-12px flex flex-wrap items-center gap-8px">
      <el-check-tag :checked="q.situacao === 'abertos'" @change="filtrar('abertos')"
        >Em aberto</el-check-tag
      >
      <el-check-tag
        v-for="s in SITUACOES_PROJETO"
        :key="s.valor"
        :checked="q.situacao === String(s.valor)"
        @change="filtrar(String(s.valor))"
        >{{ s.rotulo }}</el-check-tag
      >
      <el-check-tag :checked="q.situacao === 'todos'" @change="filtrar('todos')"
        >Todos</el-check-tag
      >
      <el-divider direction="vertical" />
      <el-checkbox v-model="q.meus" @change="buscar">Só os meus</el-checkbox>
    </div>
    <el-form :inline="true" class="-mb-15px" label-width="0">
      <el-form-item>
        <el-input
          v-model="q.busca"
          class="!w-260px"
          clearable
          placeholder="Nome ou código"
          @keyup.enter="buscar"
        >
          <template #prefix><Icon icon="ep:search" /></template>
        </el-input>
      </el-form-item>
      <el-form-item>
        <el-button @click="buscar"><Icon class="mr-5px" icon="ep:search" /> Buscar</el-button>
        <el-button v-if="pode" plain type="primary" @click="detalheRef.novo()">
          <Icon class="mr-5px" icon="ep:plus" /> Novo projeto
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table
      v-loading="loading"
      :data="lista"
      class="cursor-pointer"
      @row-click="(r) => detalheRef.open(r.id)"
    >
      <el-table-column label="Projeto" min-width="240" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.codigo" class="text-[var(--el-text-color-secondary)]"
            >{{ row.codigo }} · </span
          >{{ row.nome }}
        </template>
      </el-table-column>
      <el-table-column label="Situação" width="140">
        <template #default="{ row }">
          <el-tag :type="cor(SITUACOES_PROJETO, row.situacao)" disable-transitions>{{
            row.situacaoRotulo
          }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="Andamento" min-width="180">
        <template #default="{ row }">
          <el-progress
            :percentage="row.percentual"
            :stroke-width="10"
            :status="row.situacao === 4 ? 'success' : undefined"
          />
        </template>
      </el-table-column>
      <el-table-column label="Tarefas" width="100">
        <template #default="{ row }">{{ row.tarefasConcluidas }}/{{ row.totalTarefas }}</template>
      </el-table-column>
      <el-table-column label="Prazo" width="130">
        <template #default="{ row }">
          <span :class="{ 'font-bold text-[var(--el-color-danger)]': row.atrasado }">{{
            data(row.fimPrevisto)
          }}</span>
          <div v-if="row.atrasado" class="text-12px text-[var(--el-color-danger)]">atrasado</div>
        </template>
      </el-table-column>
      <el-table-column
        label="Responsável"
        min-width="140"
        prop="responsavel"
        show-overflow-tooltip
      />
      <el-table-column align="right" label="Custos" width="130">
        <template #default="{ row }">{{ reais(row.custoTotal) }}</template>
      </el-table-column>
    </el-table>
    <Pagination
      v-model:limit="q.pageSize"
      v-model:page="q.pageNo"
      :total="total"
      @pagination="carregar"
    />
  </ContentWrap>

  <ProjetoDetalhe ref="detalheRef" @alterado="carregar" />
</template>

<script lang="ts" setup>
import * as Gov from '@/api/itsm/governanca'
import { SITUACOES_PROJETO, cor, data, reais } from '@/api/itsm/governanca'
import { checkPermi } from '@/utils/permission'
import ProjetoDetalhe from './ProjetoDetalhe.vue'

defineOptions({ name: 'ItsmProjeto' })
const route = useRoute()
const pode = checkPermi(['itsm:projeto:update'])

const loading = ref(true)
const lista = ref<Gov.Projeto[]>([])
const total = ref(0)
const q = reactive({ pageNo: 1, pageSize: 20, busca: '', situacao: 'abertos', meus: false })
const detalheRef = ref()

const carregar = async () => {
  loading.value = true
  try {
    const r = await Gov.getProjetos(q)
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
const filtrar = (s: string) => {
  q.situacao = s
  buscar()
}

onMounted(async () => {
  await carregar()
  const id = Number(route.query.id)
  if (id) detalheRef.value.open(id)
})
</script>
