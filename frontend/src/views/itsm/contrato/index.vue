<template>
  <ContentWrap>
    <div class="mb-12px flex flex-wrap items-center gap-8px">
      <el-check-tag :checked="!q.situacao" @change="filtrar('')">Todos</el-check-tag>
      <el-check-tag
        v-for="s in SITUACOES_CONTRATO"
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
          placeholder="Nome, número ou tipo"
          @keyup.enter="buscar"
        >
          <template #prefix><Icon icon="ep:search" /></template>
        </el-input>
      </el-form-item>
      <el-form-item>
        <el-select
          v-model="q.fornecedorId"
          class="!w-220px"
          clearable
          filterable
          placeholder="Fornecedor"
          @change="buscar"
        >
          <el-option v-for="f in fornecedores" :key="f.id" :label="f.nome" :value="f.id" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="buscar"><Icon class="mr-5px" icon="ep:search" /> Buscar</el-button>
        <el-button v-if="pode" plain type="primary" @click="abrirForm()">
          <Icon class="mr-5px" icon="ep:plus" /> Novo contrato
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
      <el-table-column label="Contrato" min-width="220" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.nome }}
          <span v-if="row.numero" class="text-[var(--el-text-color-secondary)]">
            · {{ row.numero }}</span
          >
        </template>
      </el-table-column>
      <el-table-column label="Fornecedor" min-width="160" prop="fornecedor" show-overflow-tooltip />
      <el-table-column label="Situação" width="150">
        <template #default="{ row }">
          <el-tag :type="cor(SITUACOES_CONTRATO, row.situacao)" disable-transitions>{{
            row.situacaoRotulo
          }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="Renovação" width="150" prop="renovacaoRotulo" show-overflow-tooltip />
      <el-table-column label="Vigência" width="200">
        <template #default="{ row }">{{ data(row.inicio) }} a {{ data(row.fim) }}</template>
      </el-table-column>
      <el-table-column label="Prazo" width="160">
        <template #default="{ row }">
          <span
            :class="{ 'font-bold text-[var(--el-color-warning)]': row.situacao === 'a_vencer' }"
            >{{ data(row.prazo) }}</span
          >
          <div v-if="row.prazoAviso" class="text-12px text-[var(--el-text-color-secondary)]">
            avisar até aqui
          </div>
        </template>
      </el-table-column>
      <el-table-column align="right" label="Custos" width="130">
        <template #default="{ row }">{{ reais(row.custoTotal) }}</template>
      </el-table-column>
      <el-table-column
        label="Responsável"
        min-width="140"
        prop="responsavel"
        show-overflow-tooltip
      />
    </el-table>
    <Pagination
      v-model:limit="q.pageSize"
      v-model:page="q.pageNo"
      :total="total"
      @pagination="carregar"
    />
  </ContentWrap>

  <!-- detalhe -->
  <el-drawer v-model="detalhe.visivel" :size="largura" destroy-on-close>
    <template #header>
      <div v-if="detalhe.c" class="min-w-0">
        <div class="truncate text-17px font-bold">{{ detalhe.c.nome }}</div>
        <div class="mt-6px flex flex-wrap gap-6px">
          <el-tag :type="cor(SITUACOES_CONTRATO, detalhe.c.situacao)" disable-transitions>{{
            detalhe.c.situacaoRotulo
          }}</el-tag>
          <el-tag disable-transitions effect="plain" type="info">{{
            detalhe.c.renovacaoRotulo
          }}</el-tag>
          <el-tag v-if="detalhe.c.periodo! > 1" disable-transitions effect="plain" type="info"
            >{{ detalhe.c.periodo }}º período</el-tag
          >
        </div>
      </div>
    </template>
    <template v-if="detalhe.c">
      <div class="mb-12px flex flex-wrap gap-8px">
        <el-button v-if="pode" @click="abrirForm(detalhe.c)"
          ><Icon class="mr-4px" icon="ep:edit" />Editar</el-button
        >
        <el-button
          v-if="pode && detalhe.c.renovacao === 2 && !detalhe.c.encerrado"
          type="primary"
          @click="renovar"
          ><Icon class="mr-4px" icon="ep:refresh-right" />Registrar renovação</el-button
        >
        <el-button v-if="pode" plain type="danger" @click="excluir"
          ><Icon class="mr-4px" icon="ep:delete" />Excluir</el-button
        >
      </div>
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="Número">{{ detalhe.c.numero || '—' }}</el-descriptions-item>
        <el-descriptions-item label="Tipo">{{ detalhe.c.tipo || '—' }}</el-descriptions-item>
        <el-descriptions-item label="Fornecedor">{{
          detalhe.c.fornecedor || '—'
        }}</el-descriptions-item>
        <el-descriptions-item label="Unidade">{{ detalhe.c.dept || '—' }}</el-descriptions-item>
        <el-descriptions-item label="Início">{{ data(detalhe.c.inicio) }}</el-descriptions-item>
        <el-descriptions-item label="Duração"
          >{{ detalhe.c.duracaoMeses }} meses</el-descriptions-item
        >
        <el-descriptions-item label="Fim do período">{{
          data(detalhe.c.fim)
        }}</el-descriptions-item>
        <el-descriptions-item label="Aviso prévio">
          <template v-if="detalhe.c.avisoPrevioMeses"
            >{{ detalhe.c.avisoPrevioMeses }} meses — avisar até
            {{ data(detalhe.c.prazoAviso) }}</template
          >
          <template v-else>não exige</template>
        </el-descriptions-item>
        <el-descriptions-item label="Alerta">
          {{ detalhe.c.alertaDias }} dias antes ({{ data(detalhe.c.dataAlerta) }})
          <span v-if="!detalhe.c.responsavel" class="text-[var(--el-color-warning)]">
            · sem responsável, ninguém é avisado</span
          >
        </el-descriptions-item>
        <el-descriptions-item label="Responsável">{{
          detalhe.c.responsavel || '—'
        }}</el-descriptions-item>
        <el-descriptions-item label="Nº contábil">{{
          detalhe.c.numeroContabil || '—'
        }}</el-descriptions-item>
        <el-descriptions-item label="Licenças ligadas">{{
          detalhe.c.totalLicencas
        }}</el-descriptions-item>
        <el-descriptions-item v-if="detalhe.c.observacao" :span="2" label="Observação">
          <span class="whitespace-pre-wrap">{{ detalhe.c.observacao }}</span>
        </el-descriptions-item>
      </el-descriptions>

      <el-tabs class="mt-12px">
        <el-tab-pane label="Custos">
          <CustosTabela
            :editavel="pode"
            :objeto-id="detalhe.c.id!"
            objeto="contrato"
            @alterado="carregar"
          />
        </el-tab-pane>
        <el-tab-pane :label="`Renovações (${detalhe.c.renovacoes?.length || 0})`">
          <el-empty
            v-if="!detalhe.c.renovacoes?.length"
            :image-size="60"
            description="Nenhuma renovação registrada"
          />
          <el-table v-else :data="detalhe.c.renovacoes" size="small">
            <el-table-column :formatter="dateFormatter" label="Quando" prop="quando" width="160" />
            <el-table-column label="Quem" prop="quem" width="150" />
            <el-table-column label="Meses" prop="meses" width="80" />
            <el-table-column label="Fim" width="200">
              <template #default="{ row }"
                >{{ data(row.fimAntes) }} → {{ data(row.fimDepois) }}</template
              >
            </el-table-column>
            <el-table-column label="Observação" prop="observacao" show-overflow-tooltip />
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </template>
  </el-drawer>

  <!-- formulário -->
  <Dialog
    v-model="form.visivel"
    :title="form.dados.id ? 'Editar contrato' : 'Novo contrato'"
    width="760px"
  >
    <el-form ref="formRef" :model="form.dados" :rules="regras" label-width="130px">
      <el-row :gutter="12">
        <el-col :span="14">
          <el-form-item label="Nome" prop="nome"
            ><el-input v-model="form.dados.nome" maxlength="150"
          /></el-form-item>
        </el-col>
        <el-col :span="10">
          <el-form-item label="Número" label-width="80px"
            ><el-input v-model="form.dados.numero" maxlength="60"
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
          <el-form-item label="Fornecedor">
            <el-select v-model="form.dados.fornecedorId" class="w-full" clearable filterable>
              <el-option v-for="f in fornecedores" :key="f.id" :label="f.nome" :value="f.id" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="Início" prop="inicio">
            <el-date-picker v-model="form.dados.inicio" type="date" value-format="YYYY-MM-DD" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="Duração (meses)" prop="duracaoMeses">
            <el-input-number v-model="form.dados.duracaoMeses" :max="1200" :min="1" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="Renovação">
            <el-select v-model="form.dados.renovacao" class="w-full">
              <el-option
                v-for="r in RENOVACOES"
                :key="r.valor"
                :label="r.rotulo"
                :value="r.valor"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="Aviso prévio">
            <el-input-number
              v-model="form.dados.avisoPrevioMeses"
              :max="form.dados.duracaoMeses - 1"
              :min="0"
            />
            <span class="ml-6px text-13px">meses</span>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="Avisar">
            <el-input-number v-model="form.dados.alertaDias" :max="365" :min="0" />
            <span class="ml-6px text-13px">dias antes</span>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="Responsável">
            <UsuarioSelect v-model="form.dados.responsavelId" class="w-full" clearable filterable />
          </el-form-item>
        </el-col>
        <el-col :span="12">
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
        </el-col>
        <el-col :span="12">
          <el-form-item label="Nº contábil"
            ><el-input v-model="form.dados.numeroContabil" maxlength="60"
          /></el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="Observação"
            ><el-input v-model="form.dados.observacao" :rows="3" type="textarea"
          /></el-form-item>
        </el-col>
        <el-col v-if="form.dados.id" :span="24">
          <el-form-item label="Encerrado">
            <el-switch v-model="form.dados.encerrado" />
            <span class="ml-8px text-12px text-[var(--el-text-color-secondary)]"
              >cancelado ou não renovado: para de gerar avisos</span
            >
          </el-form-item>
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
import { ElMessageBox } from 'element-plus'
import * as Gov from '@/api/itsm/governanca'
import { RENOVACOES, SITUACOES_CONTRATO, cor, data, reais } from '@/api/itsm/governanca'
import * as DeptApi from '@/api/system/dept'
import { defaultProps, handleTree } from '@/utils/tree'
import { dateFormatter } from '@/utils/formatTime'
import { checkPermi } from '@/utils/permission'
import CustosTabela from '../governanca/CustosTabela.vue'

defineOptions({ name: 'ItsmContrato' })
const message = useMessage()
const route = useRoute()
const pode = checkPermi(['itsm:contrato:update'])
const TIPOS = [
  'Manutenção',
  'Suporte',
  'Locação',
  'Link de dados',
  'Telefonia',
  'Software (assinatura)',
  'Serviço',
  'Garantia'
]
const regras = {
  nome: [{ required: true, message: 'Informe o nome', trigger: 'blur' }],
  inicio: [{ required: true, message: 'Informe o início', trigger: 'change' }],
  duracaoMeses: [{ required: true, message: 'Informe a duração', trigger: 'change' }]
}
const largura = computed(() => (window.innerWidth < 1100 ? '100%' : '60%'))

const loading = ref(true)
const lista = ref<Gov.Contrato[]>([])
const total = ref(0)
const fornecedores = ref<Gov.Resumo[]>([])
const depts = ref<any[]>([])
const q = reactive({ pageNo: 1, pageSize: 20, busca: '', situacao: '', fornecedorId: undefined })
const detalhe = reactive({ visivel: false, c: undefined as Gov.Contrato | undefined })
const formRef = ref()
const form = reactive({ visivel: false, dados: {} as Gov.Contrato })

const carregar = async () => {
  loading.value = true
  try {
    const r = await Gov.getContratos(q)
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

const abrir = async (id: number) => {
  detalhe.c = await Gov.getContrato(id)
  detalhe.visivel = true
}

const abrirForm = async (c?: Gov.Contrato) => {
  form.dados = c
    ? { ...c }
    : {
        nome: '',
        inicio: '',
        duracaoMeses: 12,
        avisoPrevioMeses: 0,
        renovacao: 0,
        alertaDias: 30,
        encerrado: false
      }
  form.visivel = true
  if (!depts.value.length) {
    depts.value = await DeptApi.getSimpleDeptList().then(handleTree)
  }
}

const salvar = async () => {
  await formRef.value.validate()
  const id = await Gov.salvarContrato(form.dados)
  message.success('Contrato salvo')
  form.visivel = false
  await carregar()
  await abrir(id)
}

const renovar = async () => {
  const { value } = await ElMessageBox.prompt('Por quantos meses?', 'Registrar renovação', {
    inputValue: String(detalhe.c!.duracaoMeses),
    inputPattern: /^\d{1,3}$/,
    inputErrorMessage: 'Informe os meses',
    confirmButtonText: 'Renovar',
    cancelButtonText: 'Cancelar'
  })
  await Gov.renovarContrato({ contratoId: detalhe.c!.id!, meses: Number(value) })
  message.success('Renovação registrada')
  await abrir(detalhe.c!.id!)
  await carregar()
}

const excluir = async () => {
  await message.delConfirm(
    `Excluir o contrato ${detalhe.c!.nome}? Os custos lançados nele também saem.`
  )
  await Gov.excluirContrato(detalhe.c!.id!)
  detalhe.visivel = false
  await carregar()
}

onMounted(async () => {
  fornecedores.value = await Gov.getFornecedoresSimples()
  await carregar()
  const id = Number(route.query.id)
  if (id) abrir(id)
})
</script>
