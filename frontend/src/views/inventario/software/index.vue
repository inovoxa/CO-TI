<template>
  <ContentWrap>
    <el-form :inline="true" class="-mb-15px" label-width="0">
      <el-form-item>
        <el-input
          v-model="q.busca"
          class="!w-280px"
          clearable
          placeholder="Software ou editora"
          @keyup.enter="buscar"
        >
          <template #prefix><Icon icon="ep:search" /></template>
        </el-input>
      </el-form-item>
      <el-form-item>
        <el-button @click="buscar"><Icon class="mr-5px" icon="ep:search" /> Buscar</el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table
      v-loading="loading"
      :data="lista"
      class="cursor-pointer"
      @row-click="(r) => verAtivos(r)"
    >
      <el-table-column label="Software" min-width="260" prop="nome" show-overflow-tooltip />
      <el-table-column label="Editora" min-width="200" prop="editora" show-overflow-tooltip />
      <el-table-column align="right" label="Instalações" prop="instalacoes" width="120" />
      <el-table-column align="right" label="Versões" prop="versoes" width="100" />
      <el-table-column label="Licenças" min-width="200">
        <template #default="{ row }">
          <el-tag
            v-for="l in row.licencas"
            :key="l"
            class="mr-4px"
            disable-transitions
            size="small"
            type="success"
            >{{ l }}</el-tag
          >
          <span v-if="!row.licencas?.length" class="text-[var(--el-text-color-placeholder)]"
            >—</span
          >
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
</template>

<script lang="ts" setup>
import * as Inv from '@/api/inventario'

defineOptions({ name: 'InventarioSoftware' })
const router = useRouter()

const loading = ref(true)
const lista = ref<Inv.Software[]>([])
const total = ref(0)
const q = reactive({ pageNo: 1, pageSize: 20, busca: '' })

const carregar = async () => {
  loading.value = true
  try {
    const r = await Inv.getSoftwares(q)
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

/** Abre a lista de ativos filtrada pelos que têm o software */
const verAtivos = (s: Inv.Software) =>
  router.push({ path: '/inventario/ativos', query: { softwareId: s.id, software: s.nome } })

onMounted(carregar)
</script>
