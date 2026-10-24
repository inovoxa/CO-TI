<template>
  <ContentWrap>
    <el-button plain type="primary" @click="abrir()"
      ><Icon class="mr-5px" icon="ep:plus" /> Nova macro</el-button
    >
    <span class="dica ml-12px"
      >Uma macro faz várias coisas numa conversa com um clique (botão de varinha na conversa).</span
    >
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="lista">
      <el-table-column label="Macro" min-width="220" prop="nome" />
      <el-table-column label="Para" width="120">
        <template #default="{ row }">
          <el-tag
            :type="row.visibilidade === 'todos' ? 'primary' : 'info'"
            disable-transitions
            size="small"
            >{{ row.visibilidade === 'todos' ? 'todos' : 'só eu' }}</el-tag
          >
        </template>
      </el-table-column>
      <el-table-column label="Faz" min-width="280">
        <template #default="{ row }">{{
          row.acoes.map((a: Atd.Acao) => ACOES[a.tipo]?.rotulo).join(' · ')
        }}</template>
      </el-table-column>
      <el-table-column width="90">
        <template #default="{ row }">
          <template v-if="row.minha || (row.visibilidade === 'todos' && podeTodos)">
            <el-button link type="primary" @click="abrir(row)"><Icon icon="ep:edit" /></el-button>
            <el-button link type="danger" @click="excluir(row)"
              ><Icon icon="ep:delete"
            /></el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>
  </ContentWrap>

  <Dialog
    v-model="form.visivel"
    :title="form.dados.id ? 'Editar macro' : 'Nova macro'"
    width="760px"
  >
    <el-form ref="formRef" :model="form.dados" :rules="regras" label-width="90px">
      <el-form-item label="Nome" prop="nome"
        ><el-input v-model="form.dados.nome" maxlength="100"
      /></el-form-item>
      <el-form-item label="Para">
        <el-radio-group v-model="form.dados.visibilidade">
          <el-radio value="pessoal">só eu</el-radio>
          <el-radio :disabled="!podeTodos" value="todos">todos os atendentes</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="Ações">
        <AcoesEditor v-model="form.dados.acoes" :agentes="usuarios" :etiquetas="etiquetas" />
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
import { ACOES } from '@/api/atendimento'
import * as UserApi from '@/api/system/user'
import { checkPermi } from '@/utils/permission'
import AcoesEditor from '../automacao/AcoesEditor.vue'

defineOptions({ name: 'AtendimentoMacro' })
const message = useMessage()
const regras = { nome: [{ required: true, message: 'Informe o nome', trigger: 'blur' }] }
const podeTodos = checkPermi(['atendimento:macro:todos'])

const loading = ref(true)
const lista = ref<Atd.Macro[]>([])
const etiquetas = ref<Atd.Etiqueta[]>([])
const usuarios = ref<{ id: number; nickname: string }[]>([])
const formRef = ref()
const form = reactive({ visivel: false, dados: {} as Atd.Macro })

const carregar = async () => {
  loading.value = true
  try {
    lista.value = await Atd.getMacros()
  } finally {
    loading.value = false
  }
}

const abrir = (m?: Atd.Macro) => {
  form.dados = m ? JSON.parse(JSON.stringify(m)) : { nome: '', visibilidade: 'pessoal', acoes: [] }
  form.visivel = true
}

const salvar = async () => {
  await formRef.value.validate()
  await Atd.salvarMacro(form.dados)
  message.success('Macro salva')
  form.visivel = false
  await carregar()
}

const excluir = async (m: Atd.Macro) => {
  await message.delConfirm(`Excluir a macro ${m.nome}?`)
  await Atd.excluirMacro(m.id!)
  await carregar()
}

onMounted(async () => {
  await carregar()
  ;[etiquetas.value, usuarios.value] = await Promise.all([
    Atd.getEtiquetas(),
    UserApi.getSimpleUserList()
  ])
})
</script>

<style scoped>
.dica {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
