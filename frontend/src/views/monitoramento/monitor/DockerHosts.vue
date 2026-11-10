<!-- Servidores Docker acessados pela API remota (https com certificado de cliente, ou http na rede interna) -->
<template>
  <Dialog v-model="visivel" title="Servidores Docker" width="820px">
    <p class="mt-0 text-13px text-[var(--el-text-color-secondary)]">
      O CO-TI lê o estado dos containers pela API do Docker. Use
      <b>https://servidor:2376</b> com o <code>dockerd --tlsverify</code> e cole o ca.pem, o
      cert.pem e o key.pem do cliente. Sem TLS (<b>http://servidor:2375</b>), qualquer um na rede
      controla o Docker do servidor: só em rede isolada.
    </p>
    <el-table v-if="!editando" :data="lista" size="small">
      <el-table-column label="Servidor" min-width="160" prop="nome" />
      <el-table-column label="Endereço" min-width="220" prop="url" />
      <el-table-column label="Monitores" prop="monitores" width="90" />
      <el-table-column label="" width="140">
        <template #default="{ row }">
          <el-button link type="primary" @click="editar(row)">Editar</el-button>
          <el-button link type="danger" @click="excluir(row)">Excluir</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-form v-else ref="formRef" :model="dados" :rules="regras" label-width="150px">
      <el-form-item label="Nome" prop="nome">
        <el-input v-model="dados.nome" maxlength="100" placeholder="Ex.: Servidor de aplicações" />
      </el-form-item>
      <el-form-item label="Endereço" prop="url">
        <el-input v-model="dados.url" placeholder="https://10.0.0.20:2376" />
        <div
          v-if="dados.url && /^http:/i.test(dados.url)"
          class="text-12px text-[var(--el-color-warning)]"
        >
          Sem TLS: a API fica aberta para quem estiver na rede.
        </div>
      </el-form-item>
      <template v-if="/^https:/i.test(dados.url || '')">
        <el-form-item label="CA (ca.pem)">
          <el-input
            v-model="dados.ca"
            :rows="3"
            placeholder="-----BEGIN CERTIFICATE----- (vazio = autoridades públicas)"
            type="textarea"
          />
        </el-form-item>
        <el-form-item label="Certificado (cert.pem)">
          <el-input
            v-model="dados.certificado"
            :rows="3"
            placeholder="-----BEGIN CERTIFICATE-----"
            type="textarea"
          />
        </el-form-item>
        <el-form-item label="Chave (key.pem)">
          <el-input
            v-model="dados.chave"
            :placeholder="
              dados.chaveDefinida
                ? 'guardada (cifrada): deixe em branco para manter'
                : '-----BEGIN PRIVATE KEY-----'
            "
            :rows="3"
            type="textarea"
          />
        </el-form-item>
      </template>
      <el-alert
        v-if="resultado"
        :closable="false"
        :title="resultado"
        class="mb-8px"
        show-icon
        type="success"
      />
    </el-form>
    <template #footer>
      <template v-if="editando">
        <el-button :loading="testando" @click="testar">Testar conexão</el-button>
        <el-button :loading="salvando" type="primary" @click="salvar">Salvar</el-button>
        <el-button @click="editando = false">Voltar</el-button>
      </template>
      <template v-else>
        <el-button type="primary" @click="editar()">Novo servidor</el-button>
        <el-button @click="visivel = false">Fechar</el-button>
      </template>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import * as Mon from '@/api/monitoramento'

defineOptions({ name: 'MonitoramentoDockerHosts' })
const emit = defineEmits<{ mudou: [] }>()
const message = useMessage()
const visivel = ref(false)
const editando = ref(false)
const salvando = ref(false)
const testando = ref(false)
const resultado = ref('')
const lista = ref<Mon.DockerHost[]>([])
const dados = ref<Mon.DockerHost>({ nome: '', url: '' })
const formRef = ref()
const regras = {
  nome: [{ required: true, message: 'Informe o nome', trigger: 'blur' }],
  url: [{ required: true, message: 'Informe o endereço', trigger: 'blur' }]
}

const carregar = async () => {
  lista.value = await Mon.getDockerHosts()
}
const abrir = async () => {
  editando.value = false
  await carregar()
  visivel.value = true
}
const editar = (h?: Mon.DockerHost) => {
  dados.value = h ? { ...h, chave: '' } : { nome: '', url: 'https://' }
  resultado.value = ''
  editando.value = true
}
const testar = async () => {
  await formRef.value.validate()
  testando.value = true
  try {
    resultado.value = `Conectou: ${await Mon.testarDockerHost(dados.value)}`
  } finally {
    testando.value = false
  }
}
const salvar = async () => {
  await formRef.value.validate()
  salvando.value = true
  try {
    await Mon.salvarDockerHost(dados.value)
    message.success('Servidor Docker salvo')
    editando.value = false
    await carregar()
    emit('mudou')
  } finally {
    salvando.value = false
  }
}
const excluir = async (h: Mon.DockerHost) => {
  await message.delConfirm(`Excluir o servidor "${h.nome}"?`)
  await Mon.excluirDockerHost(h.id!)
  await carregar()
  emit('mudou')
}

defineExpose({ abrir })
</script>
