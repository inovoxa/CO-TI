<template>
  <ContentWrap>
    <el-button v-hasPermi="['itsm:origem:create']" plain type="primary" @click="abrir()">
      <Icon class="mr-5px" icon="ep:plus" /> Nova origem
    </el-button>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="lista">
      <el-table-column label="Origem" prop="nome" />
      <el-table-column align="center" label="Padrão do portal" width="150">
        <template #default="{ row }">{{ row.padraoPortal ? 'Sim' : '' }}</template>
      </el-table-column>
      <el-table-column align="center" label="Padrão do e-mail" width="150">
        <template #default="{ row }">{{ row.padraoEmail ? 'Sim' : '' }}</template>
      </el-table-column>
      <el-table-column align="center" label="Ativa" width="80">
        <template #default="{ row }">{{ row.ativo ? 'Sim' : 'Não' }}</template>
      </el-table-column>
      <el-table-column align="center" label="Ações" width="150">
        <template #default="{ row }">
          <el-button v-hasPermi="['itsm:origem:update']" link type="primary" @click="abrir(row)">Editar</el-button>
          <el-button v-hasPermi="['itsm:origem:delete']" link type="danger" @click="excluir(row)">Excluir</el-button>
        </template>
      </el-table-column>
    </el-table>
  </ContentWrap>

  <Dialog v-model="dialogo" :title="form.id ? 'Editar origem' : 'Nova origem'" width="460px">
    <el-form ref="formRef" :model="form" :rules="regras" label-width="90px">
      <el-form-item label="Nome" prop="nome"><el-input v-model="form.nome" maxlength="100" /></el-form-item>
      <el-form-item label="Padrão">
        <el-checkbox v-model="form.padraoPortal">do portal</el-checkbox>
        <el-checkbox v-model="form.padraoEmail">do e-mail</el-checkbox>
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

defineOptions({ name: 'ItsmOrigem' })
const message = useMessage()

const loading = ref(true)
const lista = ref<ItsmApi.Origem[]>([])
const dialogo = ref(false)
const formRef = ref()
const vazio = (): ItsmApi.Origem => ({ nome: '', padraoPortal: false, padraoEmail: false, ativo: true })
const form = ref<ItsmApi.Origem>(vazio())
const regras = { nome: [{ required: true, message: 'Informe o nome', trigger: 'blur' }] }

const carregar = async () => {
  loading.value = true
  try {
    lista.value = await ItsmApi.getOrigens()
  } finally {
    loading.value = false
  }
}

const abrir = (o?: ItsmApi.Origem) => {
  form.value = o ? { ...o } : vazio()
  dialogo.value = true
}

const salvar = async () => {
  if (!(await formRef.value.validate().catch(() => false))) return
  await ItsmApi.salvarOrigem(form.value)
  message.success('Origem salva')
  dialogo.value = false
  await carregar()
}

const excluir = async (o: ItsmApi.Origem) => {
  await message.delConfirm(`Excluir a origem "${o.nome}"?`)
  await ItsmApi.excluirOrigem(o.id!)
  message.success('Excluída')
  await carregar()
}

onMounted(carregar)
</script>
