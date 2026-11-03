<template>
  <ContentWrap>
    <div class="flex flex-wrap items-center gap-8px">
      <el-input
        v-model="q.busca"
        class="!w-300px"
        clearable
        placeholder="Buscar por nome, setor ou número"
        @clear="buscar"
        @keyup.enter="buscar"
      >
        <template #prefix><Icon icon="ep:search" /></template>
      </el-input>
      <el-select
        v-model="q.setor"
        class="!w-220px"
        clearable
        filterable
        placeholder="Todos os setores"
        @change="buscar"
      >
        <el-option v-for="s in setores" :key="s" :label="s" :value="s" />
      </el-select>
      <el-button @click="buscar">Buscar</el-button>
      <el-button
        v-hasPermi="['itsm:telefone:update']"
        class="!ml-auto"
        plain
        type="primary"
        @click="abrir()"
      >
        <Icon class="mr-4px" icon="ep:plus" /> Novo contato
      </el-button>
    </div>
  </ContentWrap>

  <ContentWrap>
    <el-empty
      v-if="!loading && !lista.length"
      :image-size="60"
      :description="q.busca || q.setor ? 'Nenhum contato encontrado' : 'A lista ainda está vazia'"
    />
    <el-table v-else v-loading="loading" :data="lista">
      <el-table-column label="Nome" min-width="190" prop="nome" show-overflow-tooltip />
      <el-table-column label="Ramal" prop="ramal" width="100" />
      <el-table-column label="Telefone" width="170">
        <template #default="{ row }">
          <a v-if="row.telefone" :href="linkTelefone(row.telefone)">{{ row.telefone }}</a>
        </template>
      </el-table-column>
      <el-table-column label="Setor" min-width="150" prop="setor" show-overflow-tooltip />
      <el-table-column label="Unidade" min-width="150" prop="unidade" show-overflow-tooltip />
      <el-table-column label="E-mail" min-width="190" show-overflow-tooltip>
        <template #default="{ row }">
          <a v-if="row.email" :href="`mailto:${row.email}`">{{ row.email }}</a>
        </template>
      </el-table-column>
      <el-table-column label="Observação" min-width="170" prop="observacao" show-overflow-tooltip />
      <el-table-column
        v-if="checkPermi(['itsm:telefone:update'])"
        align="right"
        fixed="right"
        label=""
        width="160"
      >
        <template #default="{ row }">
          <el-button link type="primary" @click="abrir(row)">Editar</el-button>
          <el-button link type="danger" @click="excluir(row)">Excluir</el-button>
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

  <el-dialog
    v-model="dialogo"
    :title="form.id ? 'Editar contato' : 'Novo contato'"
    width="min(560px, 94vw)"
  >
    <el-form ref="formRef" :model="form" :rules="regras" label-position="top">
      <el-form-item label="Nome (pessoa, sala ou serviço)" prop="nome">
        <el-input v-model="form.nome" maxlength="120" />
      </el-form-item>
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="Ramal" prop="ramal">
            <el-input v-model="form.ramal" maxlength="20" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="Telefone" prop="telefone">
            <el-input v-model="form.telefone" maxlength="30" placeholder="(16) 3301-0000" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="Setor">
            <el-autocomplete
              v-model="form.setor"
              class="w-full"
              :fetch-suggestions="sugerirSetores"
              maxlength="120"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="Unidade">
            <el-input v-model="form.unidade" maxlength="120" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="E-mail" prop="email">
        <el-input v-model="form.email" maxlength="128" />
      </el-form-item>
      <el-form-item label="Observação">
        <el-input v-model="form.observacao" maxlength="255" />
      </el-form-item>
      <p class="nota">Todas as pessoas com acesso ao portal veem esta lista.</p>
    </el-form>
    <template #footer>
      <el-button @click="dialogo = false">Cancelar</el-button>
      <el-button :loading="salvando" type="primary" @click="salvar">Salvar</el-button>
    </template>
  </el-dialog>
</template>

<script lang="ts" setup>
import { checkPermi } from '@/utils/permission'
import * as Api from '@/api/itsm/telefone'

defineOptions({ name: 'ItsmTelefone' })
const message = useMessage()

const loading = ref(true)
const salvando = ref(false)
const lista = ref<Api.Telefone[]>([])
const total = ref(0)
const setores = ref<string[]>([])
const q = reactive({ pageNo: 1, pageSize: 20, busca: '', setor: undefined as string | undefined })

const dialogo = ref(false)
const formRef = ref()
const form = ref<Api.Telefone>({ nome: '' })

const temNumero = (_: unknown, __: unknown, feito: (e?: Error) => void) => {
  const v = form.value
  feito(v.ramal?.trim() || v.telefone?.trim() ? undefined : new Error('Informe o ramal ou o telefone'))
}
const regras = {
  nome: [{ required: true, message: 'Informe o nome', trigger: 'blur' }],
  ramal: [{ validator: temNumero, trigger: 'blur' }],
  email: [{ type: 'email', message: 'E-mail inválido', trigger: 'blur' }]
}

const linkTelefone = (n: string) => `tel:${n.replace(/[^0-9+]/g, '')}`

const carregar = async () => {
  loading.value = true
  try {
    const p = await Api.getTelefones(q)
    lista.value = p.list
    total.value = p.total
  } finally {
    loading.value = false
  }
}
const carregarSetores = async () => {
  setores.value = await Api.getSetoresTelefone()
}
const buscar = () => {
  q.pageNo = 1
  carregar()
}
const sugerirSetores = (trecho: string, responder: (l: { value: string }[]) => void) => {
  const t = (trecho ?? '').toLowerCase()
  responder(setores.value.filter((s) => s.toLowerCase().includes(t)).map((value) => ({ value })))
}

const abrir = (t?: Api.Telefone) => {
  form.value = t ? { ...t } : { nome: '' }
  dialogo.value = true
  nextTick(() => formRef.value?.clearValidate())
}
const salvar = async () => {
  await formRef.value.validate()
  salvando.value = true
  try {
    await Api.salvarTelefone(form.value)
    message.success('Contato salvo')
    dialogo.value = false
    await Promise.all([carregar(), carregarSetores()])
  } finally {
    salvando.value = false
  }
}
const excluir = async (t: Api.Telefone) => {
  await message.delConfirm(`Excluir "${t.nome}" da lista?`)
  await Api.excluirTelefone(t.id!)
  message.success('Contato excluído')
  await Promise.all([carregar(), carregarSetores()])
}

onMounted(() => Promise.all([carregar(), carregarSetores()]))
</script>

<style scoped>
.nota {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
