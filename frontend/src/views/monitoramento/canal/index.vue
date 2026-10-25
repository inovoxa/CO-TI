<!-- Rota: /monitoramento/canais · Permissões: monitoramento:monitor:query (ver) e monitoramento:canal:update -->
<template>
  <ContentWrap>
    <div class="mb-12px flex items-center justify-between">
      <span class="text-13px text-[var(--el-text-color-secondary)]">
        Por onde os monitores avisam quando caem, voltam ou o certificado está perto de vencer.
        Tokens ficam cifrados.
      </span>
      <el-button v-if="pode" type="primary" @click="abrir()"
        ><Icon class="mr-4px" icon="ep:plus" />Novo canal</el-button
      >
    </div>
    <el-table v-loading="loading" :data="lista">
      <el-table-column label="Canal" min-width="200">
        <template #default="{ row }">
          <span class="font-medium">{{ row.nome }}</span>
          <el-tag v-if="row.padrao" class="ml-6px" disable-transitions size="small">padrão</el-tag>
          <el-tag v-if="!row.ativo" class="ml-6px" disable-transitions size="small" type="info"
            >desligado</el-tag
          >
        </template>
      </el-table-column>
      <el-table-column label="Tipo" width="110">
        <template #default="{ row }">{{
          CANAIS.find((c) => c.valor === row.tipo)?.rotulo
        }}</template>
      </el-table-column>
      <el-table-column label="Destino" min-width="220">
        <template #default="{ row }">{{ destino(row) }}</template>
      </el-table-column>
      <el-table-column align="right" label="Monitores" prop="monitores" width="100" />
      <el-table-column v-if="pode" label="" width="200">
        <template #default="{ row }">
          <el-button link @click="testar(row)">Testar</el-button>
          <el-button link type="primary" @click="abrir(row)">Editar</el-button>
          <el-button link type="danger" @click="excluir(row)">Excluir</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty
      v-if="!loading && !lista.length"
      :image-size="60"
      description="Nenhum canal de aviso."
    />
  </ContentWrap>

  <Dialog
    v-model="form.visivel"
    :title="form.dados.id ? 'Alterar canal' : 'Novo canal'"
    width="600px"
  >
    <el-form ref="formRef" :model="form.dados" :rules="regras" label-width="130px">
      <el-form-item label="Nome" prop="nome"
        ><el-input v-model="form.dados.nome" maxlength="100"
      /></el-form-item>
      <el-form-item label="Tipo">
        <el-radio-group v-model="form.dados.tipo" :disabled="!!form.dados.id">
          <el-radio-button v-for="c in CANAIS" :key="c.valor" :value="c.valor">{{
            c.rotulo
          }}</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <template v-if="form.dados.tipo === 'email'">
        <el-form-item label="Pessoas">
          <el-select
            v-model="cfg.pessoas"
            class="w-full"
            filterable
            multiple
            placeholder="usuários do CO-TI"
          >
            <el-option v-for="u in usuarios" :key="u.id" :label="u.nickname" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="Outros e-mails">
          <el-input v-model="enderecos" placeholder="separados por vírgula" />
        </el-form-item>
        <div class="mb-12px pl-130px text-12px text-[var(--el-text-color-secondary)]">
          Sai pelo servidor de e-mail do Service Desk, com os modelos "Monitor fora do ar" e
          "Monitor normalizado".
        </div>
      </template>
      <template v-if="form.dados.tipo === 'telegram'">
        <el-form-item label="Token do bot"
          ><el-input
            v-model="cfg.token"
            :placeholder="
              cfg.tokenDefinido ? 'guardado (deixe vazio para manter)' : '123456:ABC...'
            "
            show-password
        /></el-form-item>
        <el-form-item label="Chat"
          ><el-input v-model="cfg.chatId" placeholder="id do grupo ou canal, ex.: -1001234567890"
        /></el-form-item>
      </template>
      <template v-if="form.dados.tipo === 'matrix'">
        <el-form-item label="Sala"
          ><el-input
            v-model="cfg.sala"
            placeholder="#noc-alertas:prefeitura.gov.br ou !id:servidor"
        /></el-form-item>
        <el-form-item label="Servidor"
          ><el-input v-model="cfg.homeserver" placeholder="vazio = o do CO-TI"
        /></el-form-item>
        <el-form-item label="Token"
          ><el-input
            v-model="cfg.token"
            :placeholder="
              cfg.tokenDefinido ? 'guardado (deixe vazio para manter)' : 'vazio = o do bot do CO-TI'
            "
            show-password
        /></el-form-item>
      </template>
      <template v-if="form.dados.tipo === 'webhook'">
        <el-form-item label="URL"
          ><el-input v-model="cfg.url" placeholder="https://..."
        /></el-form-item>
        <el-form-item label="Segredo"
          ><el-input
            v-model="cfg.segredo"
            :placeholder="
              cfg.segredoDefinido
                ? 'guardado (deixe vazio para manter)'
                : 'opcional: assina o corpo (HMAC-SHA256)'
            "
            show-password
        /></el-form-item>
        <div class="mb-12px pl-130px text-12px text-[var(--el-text-color-secondary)]">
          POST com JSON (evento, monitor, estado, msg, momento). Com segredo, vai o cabeçalho
          X-COTI-Assinatura.
        </div>
      </template>
      <el-form-item label="">
        <el-checkbox v-model="form.dados.padrao">Já vem marcado nos monitores novos</el-checkbox>
      </el-form-item>
      <el-form-item label="">
        <el-checkbox v-model="form.dados.ativo">Ligado</el-checkbox>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" @click="salvar">Salvar</el-button>
      <el-button @click="form.visivel = false">Cancelar</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import * as Mon from '@/api/monitoramento'
import { CANAIS } from '@/api/monitoramento'
import * as UserApi from '@/api/system/user'
import { checkPermi } from '@/utils/permission'

defineOptions({ name: 'MonitoramentoCanal' })
const message = useMessage()
const pode = checkPermi(['monitoramento:canal:update'])
const regras = { nome: [{ required: true, message: 'Informe o nome', trigger: 'blur' }] }
const lista = ref<Mon.Canal[]>([])
const loading = ref(true)
const usuarios = ref<any[]>([])
const formRef = ref()
const form = reactive({ visivel: false, dados: {} as Mon.Canal })
const cfg = ref<Record<string, any>>({})
const enderecos = ref('')

const destino = (c: Mon.Canal) => {
  const x = c.config || {}
  if (c.tipo === 'email')
    return [...(x.enderecos || []), x.pessoas?.length ? `${x.pessoas.length} pessoas` : '']
      .filter(Boolean)
      .join(', ')
  if (c.tipo === 'telegram') return x.chatId ? `chat ${x.chatId}` : ''
  if (c.tipo === 'matrix') return x.sala || ''
  if (c.tipo === 'webhook') return x.url || ''
  return ''
}

const carregar = async () => {
  loading.value = true
  try {
    lista.value = await Mon.getCanais()
  } finally {
    loading.value = false
  }
}
const abrir = async (c?: Mon.Canal) => {
  if (!usuarios.value.length) usuarios.value = await UserApi.getSimpleUserList()
  form.dados = c ? { ...c } : { nome: '', tipo: 'email', padrao: false, ativo: true }
  cfg.value = { ...(c?.config || {}) }
  enderecos.value = (cfg.value.enderecos || []).join(', ')
  form.visivel = true
}
const salvar = async () => {
  await formRef.value.validate()
  const config: Record<string, any> = { ...cfg.value }
  delete config.tokenDefinido
  delete config.segredoDefinido
  if (form.dados.tipo === 'email') {
    config.enderecos = enderecos.value.split(/[,;\s]+/).filter(Boolean)
  }
  await Mon.salvarCanal({ ...form.dados, config })
  message.success('Canal salvo')
  form.visivel = false
  await carregar()
}
const testar = async (c: Mon.Canal) => {
  const erro = await Mon.testarCanal(c.id!)
  if (erro) message.error(`Falhou: ${erro}`)
  else message.success('Mensagem de teste enviada')
}
const excluir = async (c: Mon.Canal) => {
  await message.delConfirm(`Excluir o canal "${c.nome}"? Os monitores deixam de avisar por ele.`)
  await Mon.excluirCanal(c.id!)
  await carregar()
}

onMounted(carregar)
</script>
