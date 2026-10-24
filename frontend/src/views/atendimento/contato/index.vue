<template>
  <ContentWrap>
    <el-form :inline="true" class="-mb-15px" label-width="0">
      <el-form-item>
        <el-input
          v-model="q.busca"
          class="!w-280px"
          clearable
          placeholder="Nome, telefone ou e-mail"
          @keyup.enter="buscar"
        >
          <template #prefix><Icon icon="ep:search" /></template>
        </el-input>
      </el-form-item>
      <el-form-item>
        <el-button @click="buscar"><Icon class="mr-5px" icon="ep:search" /> Buscar</el-button>
        <el-button v-if="pode" plain type="primary" @click="abrir()"
          ><Icon class="mr-5px" icon="ep:plus" /> Novo contato</el-button
        >
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="lista">
      <el-table-column label="Nome" min-width="180" prop="nome">
        <template #default="{ row }">
          {{ row.nome }}
          <el-tag v-if="row.bloqueado" class="ml-4px" disable-transitions size="small" type="danger"
            >bloqueado</el-tag
          >
        </template>
      </el-table-column>
      <el-table-column label="Telefone" prop="telefone" width="160" />
      <el-table-column label="E-mail" min-width="200" prop="email" show-overflow-tooltip />
      <el-table-column label="Pessoa do portal" min-width="150" prop="usuario" />
      <el-table-column align="right" label="Conversas" prop="conversas" width="100" />
      <el-table-column
        :formatter="dateFormatter"
        label="Última atividade"
        prop="ultimaAtividadeEm"
        width="160"
      />
      <el-table-column width="110">
        <template #default="{ row }">
          <el-button link type="primary" @click="verConversas(row)">conversas</el-button>
          <el-button v-if="pode" link type="primary" @click="abrir(row)"
            ><Icon icon="ep:edit"
          /></el-button>
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
    :title="form.dados.id ? 'Editar contato' : 'Novo contato'"
    width="560px"
  >
    <el-form label-width="130px">
      <el-form-item label="Nome"
        ><el-input v-model="form.dados.nome" maxlength="150"
      /></el-form-item>
      <el-form-item label="Telefone"
        ><el-input v-model="form.dados.telefone" maxlength="30" placeholder="(16) 99999-0000"
      /></el-form-item>
      <el-form-item label="E-mail"
        ><el-input v-model="form.dados.email" maxlength="255"
      /></el-form-item>
      <el-form-item label="Pessoa do portal">
        <el-select
          v-model="form.dados.userId"
          class="w-full"
          clearable
          filterable
          placeholder="Não é da empresa"
        >
          <el-option v-for="u in usuarios" :key="u.id" :label="u.nickname" :value="u.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="Observação"
        ><el-input v-model="form.dados.observacao" :rows="3" type="textarea"
      /></el-form-item>
      <el-form-item label="Bloqueado">
        <el-switch v-model="form.dados.bloqueado" />
        <span class="ml-8px text-12px text-[var(--el-text-color-secondary)]"
          >mensagens dele são ignoradas</span
        >
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" @click="salvar">Salvar</el-button>
      <el-button @click="form.visivel = false">Cancelar</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import * as Atd from '@/api/atendimento'
import * as UserApi from '@/api/system/user'
import { dateFormatter } from '@/utils/formatTime'
import { checkPermi } from '@/utils/permission'

defineOptions({ name: 'AtendimentoContato' })
const message = useMessage()
const route = useRoute()
const router = useRouter()
const pode = checkPermi(['atendimento:contato:update'])

const loading = ref(true)
const lista = ref<Atd.Contato[]>([])
const total = ref(0)
const usuarios = ref<UserApi.UserVO[]>([])
const q = reactive({ pageNo: 1, pageSize: 20, busca: '' })
const form = reactive({ visivel: false, dados: {} as Atd.Contato })

const carregar = async () => {
  loading.value = true
  try {
    const r = await Atd.getContatos(q)
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

const abrir = async (c?: Atd.Contato) => {
  form.dados = c ? { ...c } : { nome: '', bloqueado: false }
  form.visivel = true
  if (!usuarios.value.length) usuarios.value = await UserApi.getSimpleUserList()
}
const salvar = async () => {
  await Atd.salvarContato(form.dados)
  message.success('Contato salvo')
  form.visivel = false
  await carregar()
}
const verConversas = (c: Atd.Contato) =>
  router.push({ path: '/atendimento/conversas', query: { busca: c.telefone || c.nome } })

onMounted(async () => {
  await carregar()
  const id = Number(route.query.id)
  const c = id ? lista.value.find((x) => x.id === id) : undefined
  if (c) abrir(c)
})
</script>
