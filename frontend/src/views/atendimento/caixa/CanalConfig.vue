<template>
  <Dialog v-model="visivel" :title="titulo" width="760px">
    <div v-loading="carregando">
      <el-form v-if="dados" label-width="170px">
        <!-- WhatsApp oficial -->
        <template v-if="dados.canal === 'whatsapp_cloud'">
          <el-alert :closable="false" class="mb-12px" type="info">
            No app da Meta (developers.facebook.com), produto WhatsApp: copie o identificador do
            número e o da conta (WABA), gere um token permanente de usuário do sistema com as
            permissões
            <b>whatsapp_business_messaging</b> e <b>whatsapp_business_management</b>, e copie a
            chave secreta do app (Configurações do app → Básico).
          </el-alert>
          <el-form-item label="Phone number ID">
            <el-input
              v-model="dados.phoneNumberId"
              maxlength="40"
              placeholder="ex.: 1234567890123"
            />
          </el-form-item>
          <el-form-item label="WABA ID">
            <el-input v-model="dados.wabaId" maxlength="40" />
          </el-form-item>
          <el-form-item label="Token de acesso">
            <el-input
              v-model="dados.token"
              :placeholder="dados.tokenDefinido ? 'definido — deixe em branco para manter' : ''"
              show-password
              type="password"
            />
          </el-form-item>
          <el-form-item label="Chave secreta do app">
            <el-input
              v-model="dados.appSecret"
              :placeholder="dados.appSecretDefinido ? 'definida — deixe em branco para manter' : ''"
              show-password
              type="password"
            />
            <div class="dica">Confere a assinatura de cada webhook (X-Hub-Signature-256).</div>
          </el-form-item>
          <el-form-item label="Coexistência">
            <el-switch v-model="dados.coexistencia" />
            <div class="dica">
              O número continua no aplicativo WhatsApp Business do celular. As respostas dadas pelo
              celular entram na conversa ("Pelo celular"), o histórico compartilhado na conexão é
              importado como conversas resolvidas e os contatos da agenda do aplicativo viram
              contatos.
            </div>
          </el-form-item>
        </template>

        <!-- Instagram -->
        <template v-else-if="dados.canal === 'instagram'">
          <el-alert :closable="false" class="mb-12px" type="info">
            No app da Meta, produto Instagram (API com login do Instagram): gere o token da conta
            profissional com as permissões <b>instagram_business_basic</b> e
            <b>instagram_business_manage_messages</b>, e copie a chave secreta do app do Instagram.
          </el-alert>
          <el-form-item label="ID da conta">
            <el-input
              v-model="dados.igUserId"
              maxlength="40"
              placeholder="em branco: o teste de conexão preenche"
            />
          </el-form-item>
          <el-form-item label="Token de acesso">
            <el-input
              v-model="dados.token"
              :placeholder="dados.tokenDefinido ? 'definido — deixe em branco para manter' : ''"
              show-password
              type="password"
            />
          </el-form-item>
          <el-form-item label="Chave secreta do app">
            <el-input
              v-model="dados.appSecret"
              :placeholder="dados.appSecretDefinido ? 'definida — deixe em branco para manter' : ''"
              show-password
              type="password"
            />
          </el-form-item>
        </template>

        <!-- Telegram -->
        <template v-else>
          <el-alert :closable="false" class="mb-12px" type="info">
            Crie o robô com o <b>@BotFather</b> no Telegram (/newbot) e cole o token. "Testar e
            conectar" cadastra o webhook do CO-TI no robô.
          </el-alert>
          <el-form-item label="Token do robô">
            <el-input
              v-model="dados.botToken"
              :placeholder="dados.botTokenDefinido ? 'definido — deixe em branco para manter' : ''"
              show-password
              type="password"
            />
          </el-form-item>
        </template>

        <!-- webhook -->
        <el-form-item v-if="dados.canal !== 'telegram'" label="Webhook (callback URL)">
          <el-input :model-value="dados.enderecoWebhook || '(defina CO_TI_URL_PUBLICA)'" readonly>
            <template #append>
              <el-button @click="copiar(dados.enderecoWebhook)"
                ><Icon icon="ep:copy-document"
              /></el-button>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item v-if="dados.verifyToken" label="Token de verificação">
          <el-input :model-value="dados.verifyToken" readonly>
            <template #append>
              <el-button @click="copiar(dados.verifyToken)"
                ><Icon icon="ep:copy-document"
              /></el-button>
            </template>
          </el-input>
          <div class="dica">
            Assine os campos
            <b v-if="dados.canal === 'whatsapp_cloud'"
              >messages<template v-if="dados.coexistencia"
                >, smb_message_echoes, history e smb_app_state_sync</template
              ></b
            ><b v-else>messages e messaging_seen</b>.
          </div>
        </el-form-item>

        <!-- teste -->
        <el-form-item label="Conexão">
          <template v-if="dados.testadoEm">
            <el-tag v-if="!dados.erroTeste" disable-transitions type="success">funcionando</el-tag>
            <el-tag v-else disable-transitions type="danger">com erro</el-tag>
            <span class="dica ml-8px">testada em {{ hora(dados.testadoEm) }}</span>
          </template>
          <span v-else class="dica">ainda não testada</span>
          <div v-if="dados.erroTeste" class="erro">{{ dados.erroTeste }}</div>
          <div v-if="dados.info && !dados.erroTeste" class="info">
            <span v-for="(v, k) in resumoInfo" :key="k"
              ><b>{{ k }}:</b> {{ v }}</span
            >
          </div>
        </el-form-item>
      </el-form>

      <!-- modelos do WhatsApp -->
      <template v-if="dados?.canal === 'whatsapp_cloud'">
        <el-divider content-position="left">Modelos de mensagem</el-divider>
        <div class="dica mb-8px">
          Depois de 24 h sem mensagem do contato, o WhatsApp só aceita modelos aprovados pela Meta.
          Crie os modelos no Gerenciador do WhatsApp e sincronize aqui.
          <el-button class="ml-8px" :loading="sincronizando" size="small" @click="sincronizar"
            >Sincronizar</el-button
          >
        </div>
        <el-table :data="modelos" max-height="240" size="small">
          <el-table-column label="Nome" min-width="160" prop="nome" />
          <el-table-column label="Idioma" prop="idioma" width="80" />
          <el-table-column label="Categoria" prop="categoria" width="120" />
          <el-table-column label="Situação" width="110">
            <template #default="{ row }">
              <el-tag
                :type="
                  row.status === 'APPROVED'
                    ? 'success'
                    : row.status === 'PENDING'
                      ? 'warning'
                      : 'info'
                "
                disable-transitions
                size="small"
                >{{ SITUACAO[row.status] || row.status }}</el-tag
              >
            </template>
          </el-table-column>
          <el-table-column label="Texto" min-width="220" prop="corpo" show-overflow-tooltip />
        </el-table>
      </template>
    </div>
    <template #footer>
      <el-button :loading="salvando" type="primary" @click="salvar">Salvar</el-button>
      <el-button :loading="testando" @click="testar">{{
        dados?.canal === 'telegram' ? 'Testar e conectar' : 'Testar conexão'
      }}</el-button>
      <el-button @click="visivel = false">Fechar</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import * as Atd from '@/api/atendimento'
import { CANAIS } from '@/api/atendimento'
import { formatDate } from '@/utils/formatTime'

const message = useMessage()
const visivel = ref(false)
const carregando = ref(false)
const salvando = ref(false)
const testando = ref(false)
const sincronizando = ref(false)
const dados = ref<Atd.CanalConfig>()
const modelos = ref<Atd.Modelo[]>([])
const nome = ref('')

const SITUACAO: Record<string, string> = {
  APPROVED: 'aprovado',
  PENDING: 'em análise',
  REJECTED: 'recusado',
  PAUSED: 'pausado',
  DISABLED: 'desativado'
}
const ROTULOS: Record<string, string> = {
  display_phone_number: 'Número',
  verified_name: 'Nome verificado',
  quality_rating: 'Qualidade',
  platform_type: 'Plataforma',
  username: 'Usuário',
  name: 'Nome',
  account_type: 'Tipo de conta',
  first_name: 'Nome do robô'
}

const titulo = computed(
  () => `${CANAIS[dados.value?.canal || '']?.rotulo || 'Canal'} — ${nome.value}`
)
const resumoInfo = computed(() => {
  const out: Record<string, string> = {}
  Object.entries(dados.value?.info || {}).forEach(([k, v]) => {
    if (ROTULOS[k] && v != null) out[ROTULOS[k]] = String(k === 'username' ? '@' + v : v)
  })
  return out
})
const hora = (t: number) => formatDate(new Date(t), 'DD/MM/YYYY HH:mm')

const abrir = async (caixa: Atd.Caixa) => {
  nome.value = caixa.nome
  dados.value = undefined
  modelos.value = []
  visivel.value = true
  carregando.value = true
  try {
    dados.value = await Atd.getCanal(caixa.id!)
    if (dados.value.canal === 'whatsapp_cloud')
      modelos.value = await Atd.getModelosDaCaixa(caixa.id!)
  } finally {
    carregando.value = false
  }
}

const salvar = async () => {
  if (!dados.value) return
  salvando.value = true
  try {
    dados.value = await Atd.salvarCanal(dados.value)
    message.success('Configuração gravada. Teste a conexão para ligar o recebimento.')
  } finally {
    salvando.value = false
  }
}

const testar = async () => {
  if (!dados.value) return
  testando.value = true
  try {
    dados.value = await Atd.testarCanal(dados.value.caixaId)
    message.success('Conexão funcionando')
  } catch {
    dados.value = await Atd.getCanal(dados.value.caixaId)
  } finally {
    testando.value = false
  }
}

const sincronizar = async () => {
  if (!dados.value) return
  sincronizando.value = true
  try {
    const n = await Atd.sincronizarModelos(dados.value.caixaId)
    modelos.value = await Atd.getModelosDaCaixa(dados.value.caixaId)
    message.success(`${n} modelos na conta`)
  } finally {
    sincronizando.value = false
  }
}

const copiar = async (texto?: string) => {
  if (!texto) return
  await navigator.clipboard.writeText(texto)
  message.success('Copiado')
}

defineExpose({ abrir })
</script>

<style scoped>
.dica {
  width: 100%;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}
.erro {
  width: 100%;
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-color-danger);
}
.info {
  display: flex;
  width: 100%;
  margin-top: 4px;
  font-size: 12px;
  flex-wrap: wrap;
  gap: 4px 14px;
}
</style>
