<template>
  <ContentWrap title="Sub-redes">
    <el-table :data="subredes" size="small" @row-click="(r) => filtrarSubrede(r.subrede)">
      <el-table-column label="Sub-rede" min-width="150">
        <template #default="{ row }">
          <span class="cursor-pointer font-medium">{{ row.subrede }}</span>
          <span v-if="row.mascara" class="ml-4px text-12px text-[var(--el-text-color-secondary)]"
            >/ {{ row.mascara }}</span
          >
        </template>
      </el-table-column>
      <el-table-column align="right" label="Equipamentos" prop="dispositivos" width="120" />
      <el-table-column align="right" label="Desconhecidos" width="130">
        <template #default="{ row }">
          <span :class="row.desconhecidos ? 'font-bold text-[var(--el-color-warning)]' : ''">{{
            row.desconhecidos
          }}</span>
        </template>
      </el-table-column>
      <el-table-column label="Varrida por" min-width="180">
        <template #default="{ row }">{{ row.varredores.join(', ') || '—' }}</template>
      </el-table-column>
      <el-table-column label="Última varredura" width="150">
        <template #default="{ row }">{{ quando(row.ultimaVarredura) }}</template>
      </el-table-column>
    </el-table>
    <el-empty
      v-if="!subredes.length"
      :image-size="60"
      description="Nenhuma sub-rede varrida ainda. Os agentes OCS escolhidos fazem a varredura no próximo inventário."
    />
  </ContentWrap>

  <ContentWrap>
    <el-form :inline="true" class="-mb-15px" label-width="0">
      <el-form-item>
        <el-radio-group v-model="q.situacao" @change="buscar">
          <el-radio-button value="desconhecidos">Desconhecidos</el-radio-button>
          <el-radio-button value="conhecidos">Já cadastrados</el-radio-button>
          <el-radio-button value="ignorados">Ignorados</el-radio-button>
          <el-radio-button value="todos">Todos</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item>
        <el-select
          v-model="q.subrede"
          class="!w-170px"
          clearable
          placeholder="Sub-rede"
          @change="buscar"
        >
          <el-option v-for="s in subredes" :key="s.subrede" :label="s.subrede" :value="s.subrede" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-input
          v-model="q.busca"
          class="!w-220px"
          clearable
          placeholder="IP, MAC ou nome"
          @keyup.enter="buscar"
        >
          <template #prefix><Icon icon="ep:search" /></template>
        </el-input>
      </el-form-item>
      <el-form-item>
        <el-button plain @click="exportar">
          <Icon class="mr-4px" icon="ep:download" /> Exportar
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="lista">
      <el-table-column label="IP" prop="ip" width="130" />
      <el-table-column label="MAC" prop="mac" width="160" />
      <el-table-column label="Nome" min-width="160" prop="nome" show-overflow-tooltip />
      <el-table-column label="Ativo" min-width="170">
        <template #default="{ row }">
          <el-link
            v-if="row.ativoId"
            type="primary"
            @click="router.push({ path: '/inventario/ativos', query: { id: row.ativoId } })"
            >{{ row.ativoNome }}</el-link
          >
          <el-tag v-else-if="row.ignorado" disable-transitions size="small" type="info"
            >ignorado</el-tag
          >
          <el-tag v-else disable-transitions size="small" type="warning">desconhecido</el-tag>
          <div v-if="row.observacao" class="text-12px text-[var(--el-text-color-secondary)]">
            {{ row.observacao }}
          </div>
        </template>
      </el-table-column>
      <el-table-column label="Visto por" min-width="130" prop="vistoPor" show-overflow-tooltip />
      <el-table-column label="Primeira vez" width="140">
        <template #default="{ row }">{{ quando(row.primeiraVez) }}</template>
      </el-table-column>
      <el-table-column label="Última vez" width="140">
        <template #default="{ row }">{{ quando(row.ultimaVez) }}</template>
      </el-table-column>
      <el-table-column v-if="pode" label="" width="190">
        <template #default="{ row }">
          <template v-if="!row.ativoId">
            <el-button link type="primary" @click="abrirCadastro(row)">Cadastrar</el-button>
            <el-button v-if="!row.ignorado" link @click="ignorar(row, true)">Ignorar</el-button>
            <el-button v-else link @click="ignorar(row, false)">Voltar a alertar</el-button>
          </template>
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

  <Dialog v-model="form.visivel" title="Cadastrar como ativo" width="560px">
    <div class="mb-10px text-13px">
      {{ form.d?.ip }} · {{ form.d?.mac
      }}<template v-if="form.d?.nome"> · {{ form.d.nome }}</template>
    </div>
    <el-form ref="formRef" :model="form.dados" :rules="regras" label-width="110px">
      <el-form-item label="Nome" prop="nome"
        ><el-input v-model="form.dados.nome" maxlength="255"
      /></el-form-item>
      <el-form-item label="Tipo" prop="tipo">
        <el-select v-model="form.dados.tipo" class="w-full">
          <el-option v-for="t in TIPOS_ATIVO" :key="t.valor" :label="t.rotulo" :value="t.valor" />
        </el-select>
      </el-form-item>
      <el-form-item label="Fabricante"
        ><el-input v-model="form.dados.fabricante" maxlength="255"
      /></el-form-item>
      <el-form-item label="Modelo"
        ><el-input v-model="form.dados.modelo" maxlength="255"
      /></el-form-item>
      <el-form-item label="Patrimônio"
        ><el-input v-model="form.dados.patrimonio" maxlength="100"
      /></el-form-item>
      <el-form-item label="Unidade">
        <el-tree-select
          v-model="form.dados.deptId"
          :data="depts"
          :props="defaultProps"
          check-strictly
          class="w-full"
          clearable
          node-key="id"
          placeholder="A de quem varreu"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" @click="cadastrar">Cadastrar</el-button>
      <el-button @click="form.visivel = false">Cancelar</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import { ElMessageBox } from 'element-plus'
import * as Inv from '@/api/inventario'
import { TIPOS_ATIVO } from '@/api/inventario'
import download from '@/utils/download'
import * as DeptApi from '@/api/system/dept'
import { checkPermi } from '@/utils/permission'
import { defaultProps, handleTree } from '@/utils/tree'

defineOptions({ name: 'InventarioRede' })
const message = useMessage()
const router = useRouter()
const route = useRoute()
const pode = checkPermi(['inventario:ativo:update'])
const regras = {
  nome: [{ required: true, message: 'Informe o nome', trigger: 'blur' }],
  tipo: [{ required: true, message: 'Escolha o tipo', trigger: 'change' }]
}

const subredes = ref<Inv.Subrede[]>([])
const lista = ref<Inv.DispositivoRede[]>([])
const total = ref(0)
const loading = ref(true)
const depts = ref<any[]>([])
const q = reactive({
  pageNo: 1,
  pageSize: 20,
  situacao: 'desconhecidos',
  subrede: undefined as string | undefined,
  busca: ''
})
const formRef = ref()
const form = reactive({
  visivel: false,
  d: undefined as Inv.DispositivoRede | undefined,
  dados: {} as any
})

const quando = (d?: string) =>
  d ? new Date(d).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' }) : '—'

const carregar = async () => {
  loading.value = true
  try {
    const r = await Inv.getDispositivosRede(q)
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
const exportar = async () => download.excel(await Inv.exportarRede(q), 'rede.xlsx')
const filtrarSubrede = (s: string) => {
  q.subrede = s
  buscar()
}

const abrirCadastro = async (d: Inv.DispositivoRede) => {
  form.d = d
  form.dados = { id: d.id, nome: d.nome || d.ip, tipo: 'impressora' }
  form.visivel = true
  if (!depts.value.length) depts.value = handleTree(await DeptApi.getSimpleDeptList())
}
const cadastrar = async () => {
  await formRef.value.validate()
  await Inv.cadastrarDispositivoRede(form.dados)
  message.success('Ativo cadastrado')
  form.visivel = false
  await Promise.all([carregar(), carregarSubredes()])
}
const ignorar = async (d: Inv.DispositivoRede, ignorado: boolean) => {
  let observacao: string | undefined
  if (ignorado) {
    const r = await ElMessageBox.prompt('Por que ignorar? (opcional)', `Ignorar ${d.ip}`, {
      confirmButtonText: 'Ignorar',
      cancelButtonText: 'Cancelar',
      inputPlaceholder: 'Ex.: celular de visitante'
    })
    observacao = r.value
  }
  await Inv.ignorarDispositivoRede(d.id, ignorado, observacao)
  await Promise.all([carregar(), carregarSubredes()])
}

const carregarSubredes = async () => {
  subredes.value = await Inv.getSubredes()
}

onMounted(async () => {
  if (route.query.busca) {
    q.busca = String(route.query.busca)
    q.situacao = 'todos'
  }
  await Promise.all([carregarSubredes(), carregar()])
})
</script>
