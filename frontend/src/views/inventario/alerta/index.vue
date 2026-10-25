<template>
  <ContentWrap title="Regras">
    <template #header>
      <el-button v-if="pode" :loading="avaliando" plain type="primary" @click="avaliar">
        <Icon class="mr-5px" icon="ep:refresh" /> Avaliar agora
      </el-button>
    </template>
    <el-table :data="regras" size="small">
      <el-table-column label="Regra" min-width="260" prop="nome" />
      <el-table-column align="center" label="Ligada" width="80">
        <template #default="{ row }">
          <el-switch v-model="row.ativo" :disabled="!pode" @change="salvar(row)" />
        </template>
      </el-table-column>
      <el-table-column label="Limite" width="170">
        <template #default="{ row }">
          <template v-if="LIMITES[row.codigo]">
            <el-input-number
              v-model="row.limite"
              :disabled="!pode"
              :max="LIMITES[row.codigo].max"
              :min="1"
              class="!w-100px"
              controls-position="right"
              size="small"
              @change="salvar(row)"
            />
            <span class="ml-4px text-12px">{{ LIMITES[row.codigo].unidade }}</span>
          </template>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column align="center" label="Abre chamado" width="120">
        <template #default="{ row }">
          <el-switch v-model="row.abrirChamado" :disabled="!pode" @change="salvar(row)" />
        </template>
      </el-table-column>
      <el-table-column label="Categoria do chamado" min-width="200">
        <template #default="{ row }">
          <el-select
            v-model="row.categoriaId"
            :disabled="!pode || !row.abrirChamado"
            class="w-full"
            clearable
            filterable
            placeholder="Sem categoria"
            size="small"
            @change="salvar(row)"
          >
            <el-option v-for="c in categorias" :key="c.id" :label="c.nomeCompleto" :value="c.id" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="Urgência" width="140">
        <template #default="{ row }">
          <el-select
            v-model="row.urgencia"
            :disabled="!pode || !row.abrirChamado"
            size="small"
            @change="salvar(row)"
          >
            <el-option v-for="(u, i) in URGENCIAS" :key="i" :label="u" :value="i + 1" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column align="right" label="Abertos" width="90">
        <template #default="{ row }">
          <el-link :underline="false" type="primary" @click="filtrarRegra(row.codigo)">{{
            row.abertos
          }}</el-link>
        </template>
      </el-table-column>
    </el-table>
    <div class="mt-8px text-12px text-[var(--el-text-color-secondary)]">
      Os alertas são reavaliados a cada 15 minutos e se encerram sozinhos quando a condição
      normaliza. Com "abre chamado", cada alerta novo abre um chamado (até 20 por avaliação), e o
      chamado recebe um aviso quando a condição normaliza.
    </div>
  </ContentWrap>

  <ContentWrap>
    <el-form :inline="true" class="-mb-15px" label-width="0">
      <el-form-item>
        <el-radio-group v-model="q.situacao" @change="buscar">
          <el-radio-button value="abertos">Abertos</el-radio-button>
          <el-radio-button value="encerrados">Encerrados</el-radio-button>
          <el-radio-button value="todos">Todos</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item>
        <el-select
          v-model="q.regra"
          class="!w-260px"
          clearable
          placeholder="Regra"
          @change="buscar"
        >
          <el-option v-for="r in regras" :key="r.codigo" :label="r.nome" :value="r.codigo" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-input
          v-model="q.busca"
          class="!w-240px"
          clearable
          placeholder="Máquina ou licença"
          @keyup.enter="buscar"
        >
          <template #prefix><Icon icon="ep:search" /></template>
        </el-input>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="lista">
      <el-table-column label="Alerta" min-width="300">
        <template #default="{ row }">
          <el-link :underline="false" type="primary" @click="abrirObjeto(row)">{{
            row.titulo
          }}</el-link>
          <div v-if="row.detalhe" class="text-12px text-[var(--el-text-color-secondary)]">
            {{ row.detalhe }}
          </div>
        </template>
      </el-table-column>
      <el-table-column label="Regra" min-width="200" prop="regraNome" show-overflow-tooltip />
      <el-table-column label="Unidade" min-width="140" prop="deptNome" show-overflow-tooltip />
      <el-table-column label="Desde" width="140">
        <template #default="{ row }">{{ quando(row.inicio) }}</template>
      </el-table-column>
      <el-table-column v-if="q.situacao !== 'abertos'" label="Encerrado" width="140">
        <template #default="{ row }">{{ quando(row.fim) }}</template>
      </el-table-column>
      <el-table-column label="Chamado" width="100">
        <template #default="{ row }">
          <el-link
            v-if="row.chamadoId"
            type="primary"
            @click="router.push({ path: '/servicedesk/chamados', query: { id: row.chamadoId } })"
            >#{{ row.chamadoId }}</el-link
          >
          <span v-else>—</span>
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
import * as ItsmApi from '@/api/itsm'
import { checkPermi } from '@/utils/permission'

defineOptions({ name: 'InventarioAlerta' })
const message = useMessage()
const router = useRouter()
const pode = checkPermi(['inventario:alerta:update'])
const URGENCIAS = ['Muito baixa', 'Baixa', 'Média', 'Alta', 'Muito alta']
/** Regras com limite: o máximo aceito e a unidade */
const LIMITES: Record<string, { max: number; unidade: string }> = {
  disco_cheio: { max: 90, unidade: '% livre' },
  sem_contato: { max: 365, unidade: 'dias' },
  rede_desconhecido: { max: 30, unidade: 'dias (visto)' },
  suprimento_baixo: { max: 90, unidade: '% restante' }
}

const regras = ref<Inv.RegraAlerta[]>([])
const categorias = ref<ItsmApi.Categoria[]>([])
const lista = ref<Inv.Alerta[]>([])
const total = ref(0)
const loading = ref(true)
const avaliando = ref(false)
const q = reactive({
  pageNo: 1,
  pageSize: 20,
  situacao: 'abertos',
  regra: undefined as string | undefined,
  busca: ''
})

const quando = (d?: string) =>
  d ? new Date(d).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' }) : '—'

const carregarRegras = async () => {
  regras.value = await Inv.getRegrasAlerta()
}
const carregar = async () => {
  loading.value = true
  try {
    const r = await Inv.getAlertas(q)
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
const filtrarRegra = (codigo: string) => {
  q.regra = codigo
  q.situacao = 'abertos'
  buscar()
}

const salvar = async (r: Inv.RegraAlerta) => {
  if (r.abrirChamado && r.abertos > 20) {
    message.warning(
      `Há ${r.abertos} alertas abertos: os chamados serão abertos aos poucos, 20 por avaliação.`
    )
  }
  await Inv.salvarRegraAlerta(r)
  message.success('Regra salva')
}

const avaliar = async () => {
  avaliando.value = true
  try {
    const r = await Inv.avaliarAlertas()
    message.success(
      `${r.abertos} alertas novos, ${r.encerrados} encerrados, ${r.chamados} chamados abertos`
    )
    await Promise.all([carregarRegras(), carregar()])
  } finally {
    avaliando.value = false
  }
}

const abrirObjeto = (a: Inv.Alerta) =>
  a.objeto === 'rede'
    ? router.push({
        path: '/inventario/rede',
        query: { busca: a.titulo.replace(/.*\((.*)\)$/, '$1') }
      })
    : a.objeto === 'ativo'
      ? router.push({ path: '/inventario/ativos', query: { id: a.objetoId } })
      : router.push({ path: '/governanca/licencas', query: { id: a.objetoId } })

onMounted(async () => {
  await Promise.all([carregarRegras(), carregar()])
  if (pode) categorias.value = await ItsmApi.getCategorias(true)
})
</script>
