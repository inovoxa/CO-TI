<template>
  <ContentWrap>
    <div class="mb-12px flex flex-wrap items-center gap-8px">
      <el-check-tag :checked="!q.situacao" @change="filtrar('')">Todas</el-check-tag>
      <el-check-tag
        v-for="s in SITUACOES_LICENCA"
        :key="s.valor"
        :checked="q.situacao === s.valor"
        @change="filtrar(s.valor)"
        >{{ s.rotulo }}</el-check-tag
      >
    </div>
    <el-form :inline="true" class="-mb-15px" label-width="0">
      <el-form-item>
        <el-input
          v-model="q.busca"
          class="!w-260px"
          clearable
          placeholder="Software, edição ou fabricante"
          @keyup.enter="buscar"
        >
          <template #prefix><Icon icon="ep:search" /></template>
        </el-input>
      </el-form-item>
      <el-form-item>
        <el-button @click="buscar"><Icon class="mr-5px" icon="ep:search" /> Buscar</el-button>
        <el-button v-if="pode" plain type="primary" @click="abrirForm()">
          <Icon class="mr-5px" icon="ep:plus" /> Nova licença
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
      <el-table-column label="Software" min-width="220" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.software }}
          <span v-if="row.nome" class="text-[var(--el-text-color-secondary)]">
            · {{ row.nome }}</span
          >
        </template>
      </el-table-column>
      <el-table-column label="Tipo" prop="tipo" width="120" />
      <el-table-column label="Em uso" min-width="190">
        <template #default="{ row }">
          <template v-if="row.quantidade >= 0">
            <el-progress
              :format="() => `${row.emUso} de ${row.quantidade}`"
              :percentage="
                Math.min(100, row.quantidade ? Math.round((row.emUso * 100) / row.quantidade) : 100)
              "
              :color="row.excedida ? 'var(--el-color-danger)' : undefined"
              :stroke-width="10"
            />
          </template>
          <span v-else>{{ row.emUso }} (ilimitada)</span>
        </template>
      </el-table-column>
      <el-table-column v-if="veInventario" align="right" label="Encontradas" width="110">
        <template #default="{ row }">
          <span
            :class="
              row.quantidade >= 0 && (instalacoes[row.id!] ?? 0) > row.quantidade
                ? 'font-bold text-[var(--el-color-danger)]'
                : ''
            "
            >{{ instalacoes[row.id!] ?? '—' }}</span
          >
        </template>
      </el-table-column>
      <el-table-column label="Expira" width="120">
        <template #default="{ row }">{{ data(row.expira) }}</template>
      </el-table-column>
      <el-table-column label="Situação" width="160">
        <template #default="{ row }">
          <el-tag :type="cor(SITUACOES_LICENCA, row.situacao)" disable-transitions>{{
            row.situacaoRotulo
          }}</el-tag>
          <el-tag v-if="row.excedida" class="ml-4px" disable-transitions type="danger"
            >excedida</el-tag
          >
        </template>
      </el-table-column>
      <el-table-column label="Fornecedor" min-width="140" prop="fornecedor" show-overflow-tooltip />
    </el-table>
    <Pagination
      v-model:limit="q.pageSize"
      v-model:page="q.pageNo"
      :total="total"
      @pagination="carregar"
    />
  </ContentWrap>

  <!-- detalhe -->
  <el-drawer v-model="detalhe.visivel" :size="largura" destroy-on-close @closed="chaveVista = ''">
    <template #header>
      <div v-if="detalhe.l" class="min-w-0">
        <div class="truncate text-17px font-bold"
          >{{ detalhe.l.software
          }}<template v-if="detalhe.l.nome"> · {{ detalhe.l.nome }}</template></div
        >
        <div class="mt-6px flex flex-wrap gap-6px">
          <el-tag :type="cor(SITUACOES_LICENCA, detalhe.l.situacao)" disable-transitions>{{
            detalhe.l.situacaoRotulo
          }}</el-tag>
          <el-tag v-if="detalhe.l.excedida" disable-transitions type="danger"
            >Em uso além do comprado</el-tag
          >
        </div>
      </div>
    </template>
    <template v-if="detalhe.l">
      <div class="mb-12px flex flex-wrap gap-8px">
        <el-button v-if="pode" @click="abrirForm(detalhe.l)"
          ><Icon class="mr-4px" icon="ep:edit" />Editar</el-button
        >
        <el-button v-if="veChave && detalhe.l.chaveDefinida" @click="mostrarChave">
          <Icon class="mr-4px" icon="ep:view" />Ver a chave
        </el-button>
        <el-button v-if="pode" plain type="danger" @click="excluir"
          ><Icon class="mr-4px" icon="ep:delete" />Excluir</el-button
        >
      </div>
      <el-alert v-if="chaveVista" :closable="false" class="mb-12px" type="warning">
        <template #title>
          <span class="font-mono">{{ chaveVista }}</span>
        </template>
        Fica registrado na auditoria que você viu a chave.
      </el-alert>
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="Fabricante">{{
          detalhe.l.fabricante || '—'
        }}</el-descriptions-item>
        <el-descriptions-item label="Tipo">{{ detalhe.l.tipo || '—' }}</el-descriptions-item>
        <el-descriptions-item label="Quantidade">
          {{ detalhe.l.quantidade < 0 ? 'ilimitada' : detalhe.l.quantidade }}
          <span v-if="detalhe.l.permiteExcesso" class="text-12px"> (aceita uso além)</span>
        </el-descriptions-item>
        <el-descriptions-item label="Disponíveis">{{
          detalhe.l.disponivel ?? '—'
        }}</el-descriptions-item>
        <el-descriptions-item label="Expira">{{ data(detalhe.l.expira) }}</el-descriptions-item>
        <el-descriptions-item label="Chave">{{
          detalhe.l.chaveDefinida ? 'guardada (cifrada)' : 'não informada'
        }}</el-descriptions-item>
        <el-descriptions-item label="Fornecedor">{{
          detalhe.l.fornecedor || '—'
        }}</el-descriptions-item>
        <el-descriptions-item label="Contrato">{{
          detalhe.l.contrato || '—'
        }}</el-descriptions-item>
        <el-descriptions-item label="Unidade">{{ detalhe.l.dept || '—' }}</el-descriptions-item>
        <el-descriptions-item label="Responsável">{{
          detalhe.l.responsavel || '—'
        }}</el-descriptions-item>
      </el-descriptions>

      <el-tabs class="mt-12px">
        <el-tab-pane :label="`Quem usa (${detalhe.l.emUso})`">
          <div v-if="pode" class="mb-10px flex flex-wrap items-center gap-8px">
            <el-radio-group v-model="uso.modo" size="small">
              <el-radio-button value="pessoa">Pessoa</el-radio-button>
              <el-radio-button value="equipamento">Equipamento</el-radio-button>
            </el-radio-group>
            <UsuarioSelect
              v-if="uso.modo === 'pessoa'"
              v-model="uso.userId"
              class="!w-240px"
              filterable
              placeholder="Pessoa"
              size="small"
            />
            <el-input
              v-else
              v-model="uso.equipamento"
              class="!w-240px"
              maxlength="150"
              placeholder="Nome do computador"
              size="small"
            />
            <el-button size="small" type="primary" @click="adicionarUso">Entregar</el-button>
          </div>
          <el-table :data="detalhe.l.usos" size="small">
            <el-table-column label="Quem" min-width="200">
              <template #default="{ row }">
                <Icon :icon="row.userId ? 'ep:user' : 'ep:monitor'" class="mr-4px" />{{ row.nome }}
              </template>
            </el-table-column>
            <el-table-column label="Desde" width="120">
              <template #default="{ row }">{{ data(row.desde) }}</template>
            </el-table-column>
            <el-table-column v-if="pode" width="60">
              <template #default="{ row }">
                <el-button link type="danger" @click="removerUso(row)"
                  ><Icon icon="ep:close"
                /></el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane v-if="veInventario" label="Instalações encontradas" lazy>
          <LicencaInstalacoes :editavel="pode" :licenca-id="detalhe.l.id!" @alterado="carregar" />
        </el-tab-pane>
        <el-tab-pane label="Custos">
          <CustosTabela :editavel="pode" :objeto-id="detalhe.l.id!" objeto="licenca" />
        </el-tab-pane>
      </el-tabs>
    </template>
  </el-drawer>

  <!-- formulário -->
  <Dialog
    v-model="form.visivel"
    :title="form.dados.id ? 'Editar licença' : 'Nova licença'"
    width="760px"
  >
    <el-form ref="formRef" :model="form.dados" :rules="regras" label-width="130px">
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="Software" prop="software"
            ><el-input
              v-model="form.dados.software"
              maxlength="150"
              placeholder="Ex.: Microsoft Office"
          /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="Edição/versão"
            ><el-input v-model="form.dados.nome" maxlength="150" placeholder="Ex.: 365 E3"
          /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="Fabricante"
            ><el-input v-model="form.dados.fabricante" maxlength="100"
          /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="Tipo">
            <el-select v-model="form.dados.tipo" allow-create class="w-full" clearable filterable>
              <el-option v-for="t in TIPOS" :key="t" :label="t" :value="t" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="Quantidade">
            <el-input-number v-model="form.dados.quantidade" :min="-1" />
            <div class="w-full text-12px text-[var(--el-text-color-secondary)]">-1 = ilimitada</div>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="Aceita uso além">
            <el-switch v-model="form.dados.permiteExcesso" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="Expira">
            <el-date-picker
              v-model="form.dados.expira"
              placeholder="Não expira"
              type="date"
              value-format="YYYY-MM-DD"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="Avisar">
            <el-input-number v-model="form.dados.alertaDias" :max="365" :min="0" />
            <span class="ml-6px text-13px">dias antes</span>
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="Chave">
            <el-input
              v-model="form.dados.chave"
              :placeholder="
                form.dados.chaveDefinida ? 'Guardada. Preencha só para trocar' : 'Opcional'
              "
              autocomplete="off"
              maxlength="4000"
              show-password
            />
            <el-checkbox
              v-if="form.dados.chaveDefinida"
              v-model="form.dados.removerChave"
              class="mt-4px"
              >Apagar a chave guardada</el-checkbox
            >
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="Fornecedor">
            <el-select v-model="form.dados.fornecedorId" class="w-full" clearable filterable>
              <el-option v-for="f in fornecedores" :key="f.id" :label="f.nome" :value="f.id" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="Contrato">
            <el-select v-model="form.dados.contratoId" class="w-full" clearable filterable>
              <el-option v-for="c in contratos" :key="c.id" :label="c.nome" :value="c.id" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="Responsável">
            <UsuarioSelect v-model="form.dados.responsavelId" class="w-full" clearable filterable />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="Ativa"><el-switch v-model="form.dados.ativo" /></el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="Observação"
            ><el-input v-model="form.dados.observacao" :rows="2" type="textarea"
          /></el-form-item>
        </el-col>
      </el-row>
    </el-form>
    <template #footer>
      <el-button type="primary" @click="salvar">Salvar</el-button>
      <el-button @click="form.visivel = false">Cancelar</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import UsuarioSelect from '@/components/UsuarioSelect/index.vue'
import * as Gov from '@/api/itsm/governanca'
import { SITUACOES_LICENCA, cor, data } from '@/api/itsm/governanca'
import { checkPermi } from '@/utils/permission'
import CustosTabela from '../governanca/CustosTabela.vue'
import LicencaInstalacoes from '../../inventario/LicencaInstalacoes.vue'
import { getInstalacoesLicencas } from '@/api/inventario'

defineOptions({ name: 'ItsmLicenca' })
const message = useMessage()
const route = useRoute()
const pode = checkPermi(['itsm:licenca:update'])
const veChave = checkPermi(['itsm:licenca:ver-chave'])
/** Instalações encontradas pelo inventário (F3b) */
const veInventario = checkPermi(['inventario:ativo:query'])
const instalacoes = ref<Record<number, number>>({})
const TIPOS = ['Assinatura', 'Perpétua', 'OEM', 'Volume', 'Por usuário', 'Por dispositivo', 'Livre']
const regras = { software: [{ required: true, message: 'Informe o software', trigger: 'blur' }] }
const largura = computed(() => (window.innerWidth < 1100 ? '100%' : '60%'))

const loading = ref(true)
const lista = ref<Gov.Licenca[]>([])
const total = ref(0)
const fornecedores = ref<Gov.Resumo[]>([])
const contratos = ref<Gov.Resumo[]>([])
const q = reactive({ pageNo: 1, pageSize: 20, busca: '', situacao: '' })
const detalhe = reactive({ visivel: false, l: undefined as Gov.Licenca | undefined })
const chaveVista = ref('')
const uso = reactive({ modo: 'pessoa', userId: undefined as number | undefined, equipamento: '' })
const formRef = ref()
const form = reactive({ visivel: false, dados: {} as Gov.Licenca })

const carregar = async () => {
  loading.value = true
  try {
    const r = await Gov.getLicencas(q)
    lista.value = r.list
    total.value = r.total
    if (veInventario) instalacoes.value = await getInstalacoesLicencas()
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

const carregarListas = async () => {
  if (!fornecedores.value.length) {
    ;[fornecedores.value, contratos.value] = await Promise.all([
      Gov.getFornecedoresSimples(),
      Gov.getContratosSimples()
    ])
  }
}

const abrir = async (id: number) => {
  chaveVista.value = ''
  detalhe.l = await Gov.getLicenca(id)
  detalhe.visivel = true
  if (pode) carregarListas()
}

const abrirForm = async (l?: Gov.Licenca) => {
  form.dados = l
    ? { ...l, chave: '', removerChave: false }
    : { software: '', quantidade: 1, permiteExcesso: false, alertaDias: 30, ativo: true }
  form.visivel = true
  await carregarListas()
}

const salvar = async () => {
  await formRef.value.validate()
  const id = await Gov.salvarLicenca(form.dados)
  message.success('Licença salva')
  form.visivel = false
  await carregar()
  await abrir(id)
}

const mostrarChave = async () => {
  chaveVista.value = await Gov.revelarChave(detalhe.l!.id!)
}

const adicionarUso = async () => {
  await Gov.adicionarUso({
    licencaId: detalhe.l!.id!,
    userId: uso.modo === 'pessoa' ? uso.userId : undefined,
    equipamento: uso.modo === 'equipamento' ? uso.equipamento : undefined
  })
  uso.userId = undefined
  uso.equipamento = ''
  await abrir(detalhe.l!.id!)
  await carregar()
}

const removerUso = async (u: Gov.Uso) => {
  await message.confirm(`Liberar a licença de ${u.nome}?`)
  await Gov.removerUso(u.id!)
  await abrir(detalhe.l!.id!)
  await carregar()
}

const excluir = async () => {
  await message.delConfirm(`Excluir a licença ${detalhe.l!.software}?`)
  await Gov.excluirLicenca(detalhe.l!.id!)
  detalhe.visivel = false
  await carregar()
}

onMounted(async () => {
  await carregar()
  const id = Number(route.query.id)
  if (id) abrir(id)
})
</script>
