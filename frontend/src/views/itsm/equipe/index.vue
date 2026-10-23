<template>
  <ContentWrap>
    <el-button v-hasPermi="['itsm:equipe:create']" plain type="primary" @click="abrir()">
      <Icon class="mr-5px" icon="ep:plus" /> Nova equipe
    </el-button>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="lista">
      <el-table-column label="Equipe" min-width="180" prop="nome" />
      <el-table-column label="Membros" min-width="320">
        <template #default="{ row }">
          <el-tag
            v-for="m in row.membros"
            :key="m.userId"
            :type="m.supervisor ? 'warning' : 'info'"
            class="mb-2px mr-4px"
            disable-transitions
            size="small"
          >
            {{ m.nome }}<span v-if="m.supervisor"> (supervisor)</span>
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="E-mail" prop="email" width="200" />
      <el-table-column label="Grupo do AD" prop="grupoAd" show-overflow-tooltip width="200" />
      <el-table-column align="center" label="Ativa" width="80">
        <template #default="{ row }">{{ row.ativo ? 'Sim' : 'Não' }}</template>
      </el-table-column>
      <el-table-column align="center" label="Ações" width="150">
        <template #default="{ row }">
          <el-button v-hasPermi="['itsm:equipe:update']" link type="primary" @click="abrir(row)">Editar</el-button>
          <el-button v-hasPermi="['itsm:equipe:delete']" link type="danger" @click="excluir(row)">Excluir</el-button>
        </template>
      </el-table-column>
    </el-table>
  </ContentWrap>

  <Dialog v-model="dialogo" :title="form.id ? 'Editar equipe' : 'Nova equipe'" width="620px">
    <el-form ref="formRef" :model="form" :rules="regras" label-width="110px">
      <el-form-item label="Nome" prop="nome"><el-input v-model="form.nome" maxlength="100" /></el-form-item>
      <el-form-item label="Descrição"><el-input v-model="form.descricao" maxlength="500" type="textarea" /></el-form-item>
      <el-form-item label="E-mail" prop="email"><el-input v-model="form.email" maxlength="128" /></el-form-item>
      <el-form-item label="Grupo do AD">
        <el-input v-model="form.grupoAd" maxlength="255" placeholder="Opcional, ex.: GG-TI-N1" />
      </el-form-item>
      <el-form-item label="Membros">
        <el-select v-model="selecionados" class="w-full" filterable multiple placeholder="Pessoas da equipe">
          <el-option v-for="u in usuarios" :key="u.id" :label="u.nickname" :value="u.id" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="selecionados.length" label="Supervisores">
        <el-checkbox-group v-model="supervisores">
          <el-checkbox v-for="id in selecionados" :key="id" :value="id">
            {{ usuarios.find((u) => u.id === id)?.nickname }}
          </el-checkbox>
        </el-checkbox-group>
      </el-form-item>
      <el-form-item label="Ativa"><el-switch v-model="form.ativo" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" @click="salvar">Salvar</el-button>
      <el-button @click="dialogo = false">Cancelar</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import * as ItsmApi from '@/api/itsm'
import * as UserApi from '@/api/system/user'

defineOptions({ name: 'ItsmEquipe' })
const message = useMessage()

const loading = ref(true)
const lista = ref<ItsmApi.Equipe[]>([])
const usuarios = ref<UserApi.UserVO[]>([])
const dialogo = ref(false)
const formRef = ref()
const selecionados = ref<number[]>([])
const supervisores = ref<number[]>([])
const vazio = (): ItsmApi.Equipe => ({ nome: '', descricao: '', email: '', grupoAd: '', ativo: true, membros: [] })
const form = ref<ItsmApi.Equipe>(vazio())
const regras = {
  nome: [{ required: true, message: 'Informe o nome', trigger: 'blur' }],
  email: [{ type: 'email', message: 'E-mail inválido', trigger: 'blur' }]
}

const carregar = async () => {
  loading.value = true
  try {
    lista.value = await ItsmApi.getEquipes()
  } finally {
    loading.value = false
  }
}

const abrir = (e?: ItsmApi.Equipe) => {
  form.value = e ? { ...e } : vazio()
  selecionados.value = (e?.membros || []).map((m) => m.userId)
  supervisores.value = (e?.membros || []).filter((m) => m.supervisor).map((m) => m.userId)
  dialogo.value = true
}

const salvar = async () => {
  if (!(await formRef.value.validate().catch(() => false))) return
  form.value.membros = selecionados.value.map((userId) => ({ userId, supervisor: supervisores.value.includes(userId) }))
  await ItsmApi.salvarEquipe(form.value)
  message.success('Equipe salva')
  dialogo.value = false
  await carregar()
}

const excluir = async (e: ItsmApi.Equipe) => {
  await message.delConfirm(`Excluir a equipe "${e.nome}"?`)
  await ItsmApi.excluirEquipe(e.id!)
  message.success('Excluída')
  await carregar()
}

onMounted(async () => {
  usuarios.value = await UserApi.getSimpleUserList()
  await carregar()
})
</script>
