<template>
  <ContentWrap>
    <el-form :inline="true" class="-mb-15px" label-width="0">
      <el-form-item>
        <el-input
          v-model="q.busca"
          class="!w-260px"
          clearable
          placeholder="Nome, tipo ou CNPJ"
          @keyup.enter="buscar"
        >
          <template #prefix><Icon icon="ep:search" /></template>
        </el-input>
      </el-form-item>
      <el-form-item>
        <el-select v-model="q.situacao" class="!w-140px" @change="buscar">
          <el-option label="Ativos" value="" />
          <el-option label="Inativos" value="inativos" />
          <el-option label="Todos" value="todos" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="buscar"><Icon class="mr-5px" icon="ep:search" /> Buscar</el-button>
        <el-button v-hasPermi="['itsm:fornecedor:update']" plain type="primary" @click="abrir()">
          <Icon class="mr-5px" icon="ep:plus" /> Novo fornecedor
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="lista">
      <el-table-column label="Nome" min-width="200" prop="nome" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.nome }}
          <el-tag v-if="!row.ativo" class="ml-4px" disable-transitions size="small" type="info"
            >inativo</el-tag
          >
        </template>
      </el-table-column>
      <el-table-column label="Tipo" min-width="130" prop="tipo" />
      <el-table-column label="CNPJ/CPF" width="170">
        <template #default="{ row }">{{ documento(row.documento) }}</template>
      </el-table-column>
      <el-table-column label="Contato" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">
          {{ [row.contato, row.telefone, row.email].filter(Boolean).join(' · ') || '—' }}
        </template>
      </el-table-column>
      <el-table-column label="Cidade" min-width="130">
        <template #default="{ row }"
          >{{ row.cidade || '—' }}<template v-if="row.uf">/{{ row.uf }}</template></template
        >
      </el-table-column>
      <el-table-column align="right" label="Contratos" prop="totalContratos" width="100" />
      <el-table-column align="right" label="Licenças" prop="totalLicencas" width="90" />
      <el-table-column v-if="pode" width="100">
        <template #default="{ row }">
          <el-button link type="primary" @click="abrir(row)"><Icon icon="ep:edit" /></el-button>
          <el-button link type="danger" @click="excluir(row)"><Icon icon="ep:delete" /></el-button>
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

  <Dialog
    v-model="form.visivel"
    :title="form.dados.id ? 'Editar fornecedor' : 'Novo fornecedor'"
    width="720px"
  >
    <el-form ref="formRef" :model="form.dados" :rules="regras" label-width="110px">
      <el-row :gutter="12">
        <el-col :span="14">
          <el-form-item label="Nome" prop="nome"
            ><el-input v-model="form.dados.nome" maxlength="150"
          /></el-form-item>
        </el-col>
        <el-col :span="10">
          <el-form-item label="Tipo">
            <el-select v-model="form.dados.tipo" allow-create class="w-full" clearable filterable>
              <el-option v-for="t in TIPOS" :key="t" :label="t" :value="t" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="CNPJ/CPF"
            ><el-input
              v-model="form.dados.documento"
              maxlength="20"
              placeholder="Com ou sem pontuação"
          /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="Contato"
            ><el-input v-model="form.dados.contato" maxlength="150" placeholder="Pessoa"
          /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="E-mail" prop="email"
            ><el-input v-model="form.dados.email" maxlength="255"
          /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="Telefone"
            ><el-input v-model="form.dados.telefone" maxlength="40"
          /></el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="Site"
            ><el-input v-model="form.dados.site" maxlength="255"
          /></el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="Endereço"
            ><el-input v-model="form.dados.endereco" maxlength="255"
          /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="Cidade"
            ><el-input v-model="form.dados.cidade" maxlength="100"
          /></el-form-item>
        </el-col>
        <el-col :span="5">
          <el-form-item label="UF" label-width="40px"
            ><el-input v-model="form.dados.uf" maxlength="2"
          /></el-form-item>
        </el-col>
        <el-col :span="7">
          <el-form-item label="CEP" label-width="50px"
            ><el-input v-model="form.dados.cep" maxlength="9"
          /></el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="Observação">
            <el-input v-model="form.dados.observacao" :rows="3" type="textarea" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="Ativo"><el-switch v-model="form.dados.ativo" /></el-form-item>
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
import * as Gov from '@/api/itsm/governanca'
import { checkPermi } from '@/utils/permission'

defineOptions({ name: 'ItsmFornecedor' })
const message = useMessage()
const pode = checkPermi(['itsm:fornecedor:update'])

const TIPOS = [
  'Fabricante',
  'Revenda',
  'Prestador de serviço',
  'Operadora',
  'Consultoria',
  'Órgão público'
]
const regras = {
  nome: [{ required: true, message: 'Informe o nome', trigger: 'blur' }],
  email: [{ type: 'email', message: 'E-mail inválido', trigger: 'blur' }]
}

const loading = ref(true)
const lista = ref<Gov.Fornecedor[]>([])
const total = ref(0)
const q = reactive({ pageNo: 1, pageSize: 20, busca: '', situacao: '' })
const formRef = ref()
const form = reactive({ visivel: false, dados: {} as Gov.Fornecedor })

/** 11222333000181 → 11.222.333/0001-81; 52998224725 → 529.982.247-25 */
const documento = (d?: string) => {
  if (!d) return '—'
  if (d.length === 14)
    return `${d.slice(0, 2)}.${d.slice(2, 5)}.${d.slice(5, 8)}/${d.slice(8, 12)}-${d.slice(12)}`
  if (d.length === 11) return `${d.slice(0, 3)}.${d.slice(3, 6)}.${d.slice(6, 9)}-${d.slice(9)}`
  return d
}

const carregar = async () => {
  loading.value = true
  try {
    const r = await Gov.getFornecedores(q)
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

const abrir = (f?: Gov.Fornecedor) => {
  form.dados = f
    ? { ...f, documento: f.documento ? documento(f.documento) : undefined }
    : { nome: '', ativo: true }
  form.visivel = true
}

const salvar = async () => {
  await formRef.value.validate()
  await Gov.salvarFornecedor(form.dados)
  message.success('Fornecedor salvo')
  form.visivel = false
  await carregar()
}

const excluir = async (f: Gov.Fornecedor) => {
  await message.delConfirm(`Excluir o fornecedor ${f.nome}?`)
  await Gov.excluirFornecedor(f.id!)
  await carregar()
}

onMounted(carregar)
</script>
