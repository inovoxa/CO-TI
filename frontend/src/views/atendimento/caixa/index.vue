<template>
  <ContentWrap>
    <el-button plain type="primary" @click="abrir()"
      ><Icon class="mr-5px" icon="ep:plus" /> Nova caixa de entrada</el-button
    >
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="lista">
      <el-table-column label="Caixa" min-width="180">
        <template #default="{ row }">
          {{ row.nome }}
          <el-tag v-if="!row.ativo" class="ml-4px" disable-transitions size="small" type="info"
            >desativada</el-tag
          >
        </template>
      </el-table-column>
      <el-table-column label="Canal" width="170">
        <template #default="{ row }"
          ><Icon :icon="CANAIS[row.canal]?.icone" class="mr-4px" />{{
            CANAIS[row.canal]?.rotulo
          }}</template
        >
      </el-table-column>
      <el-table-column label="Quem atende" min-width="240">
        <template #default="{ row }">
          <span v-for="a in row.agentes" :key="a.userId" class="agente"
            ><span class="dot" :class="{ on: a.disponivel }"></span>{{ a.nome }}</span
          >
          <span v-if="!row.agentes.length" class="text-[var(--el-color-warning)]">ninguém</span>
        </template>
      </el-table-column>
      <el-table-column label="Distribuição" width="170">
        <template #default="{ row }">
          <template v-if="row.atribuicaoAutomatica"
            >automática<span v-if="row.limitePorAgente"
              >, até {{ row.limitePorAgente }} por pessoa</span
            ></template
          >
          <template v-else>manual</template>
        </template>
      </el-table-column>
      <el-table-column align="right" label="Abertas" prop="abertas" width="90" />
      <el-table-column width="130">
        <template #default="{ row }">
          <el-tooltip v-if="CANAIS_EXTERNOS.includes(row.canal)" content="Configurar o canal">
            <el-button link type="primary" @click="canalRef.abrir(row)"
              ><Icon icon="ep:setting"
            /></el-button>
          </el-tooltip>
          <el-button link type="primary" @click="abrir(row)"><Icon icon="ep:edit" /></el-button>
          <el-button link type="danger" @click="excluir(row)"><Icon icon="ep:delete" /></el-button>
        </template>
      </el-table-column>
    </el-table>
  </ContentWrap>

  <Dialog
    v-model="form.visivel"
    :title="form.dados.id ? 'Editar caixa de entrada' : 'Nova caixa de entrada'"
    width="720px"
  >
    <el-form ref="formRef" :model="form.dados" :rules="regras" label-width="150px">
      <el-form-item label="Nome" prop="nome"
        ><el-input v-model="form.dados.nome" maxlength="100"
      /></el-form-item>
      <el-form-item label="Canal">
        <el-select v-model="form.dados.canal" :disabled="!!form.dados.id">
          <el-option v-for="(c, k) in CANAIS" :key="k" :label="c.rotulo" :value="k" />
        </el-select>
        <div v-if="!form.dados.id && CANAIS_EXTERNOS.includes(form.dados.canal)" class="dica"
          >Depois de salvar, configure as credenciais do canal.</div
        >
      </el-form-item>
      <el-form-item
        v-if="form.dados.id && CANAIS_EXTERNOS.includes(form.dados.canal)"
        label="Credenciais"
      >
        <el-button size="small" @click="canalRef.abrir(form.dados)"
          >Configurar credenciais e webhook</el-button
        >
      </el-form-item>
      <template v-if="form.dados.canal === 'api'">
        <el-form-item label="Webhook das respostas">
          <el-input
            v-model="form.dados.webhookUrl"
            placeholder="https://sistema.empresa/coti/respostas"
          />
          <div class="dica"
            >O CO-TI manda para cá cada resposta, assinada com o segredo da caixa (cabeçalho
            X-CO-TI-Assinatura).</div
          >
        </el-form-item>
        <el-form-item v-if="form.dados.id" label="Credenciais">
          <el-button size="small" @click="credenciais">{{
            form.dados.tokenDefinido
              ? 'Gerar novas (as atuais param de valer)'
              : 'Gerar token e segredo'
          }}</el-button>
        </el-form-item>
      </template>
      <el-form-item label="Quem atende">
        <el-select v-model="form.dados.agenteIds" class="w-full" filterable multiple>
          <el-option v-for="u in usuarios" :key="u.id" :label="u.nickname" :value="u.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="Distribuição">
        <el-switch
          v-model="form.dados.atribuicaoAutomatica"
          active-text="automática"
          inactive-text="manual"
        />
      </el-form-item>
      <el-form-item v-if="form.dados.atribuicaoAutomatica" label="Limite por pessoa">
        <el-input-number v-model="form.dados.limitePorAgente" :max="1000" :min="0" />
        <span class="dica ml-8px">conversas abertas ao mesmo tempo (0 = sem limite)</span>
      </el-form-item>
      <el-form-item label="Reabrir até">
        <el-input-number v-model="form.dados.reabrirHoras" :max="720" :min="0" />
        <span class="dica ml-8px">horas depois de resolvida (depois disso, nova conversa)</span>
      </el-form-item>
      <el-form-item label="Saudação">
        <el-input
          v-model="form.dados.saudacao"
          :rows="3"
          maxlength="2000"
          placeholder="Primeira resposta automática numa conversa nova (opcional)"
          type="textarea"
        />
      </el-form-item>
      <el-form-item label="Horário">
        <el-select
          v-model="form.dados.calendarioId"
          clearable
          placeholder="Sempre (sem calendário)"
        >
          <el-option v-for="c in calendarios" :key="c.id" :label="c.nome" :value="c.id" />
        </el-select>
        <div class="dica"
          >Calendário do Service Desk (expediente e feriados, os mesmos do SLA).</div
        >
      </el-form-item>
      <el-form-item v-if="form.dados.calendarioId" label="Fora do horário">
        <el-input
          v-model="form.dados.mensagemForaHorario"
          :rows="2"
          maxlength="2000"
          placeholder="Mandada quando a conversa abre fora do expediente (opcional)"
          type="textarea"
        />
      </el-form-item>
      <el-form-item label="Ativa"><el-switch v-model="form.dados.ativo" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" @click="salvar">Salvar</el-button>
      <el-button @click="form.visivel = false">Cancelar</el-button>
    </template>
  </Dialog>

  <Dialog v-model="cred.visivel" title="Credenciais do canal API" width="620px">
    <el-alert
      :closable="false"
      class="mb-12px"
      show-icon
      title="Copie agora: o token e o segredo não aparecem de novo."
      type="warning"
    />
    <el-form label-position="top">
      <el-form-item label="Endereço de entrada (POST)"
        ><el-input :model-value="cred.dados?.enderecoEntrada" readonly
      /></el-form-item>
      <el-form-item label="Token (cabeçalho X-CO-TI-Token)"
        ><el-input :model-value="cred.dados?.token" readonly
      /></el-form-item>
      <el-form-item label="Segredo para conferir a assinatura das respostas"
        ><el-input :model-value="cred.dados?.segredoWebhook" readonly
      /></el-form-item>
    </el-form>
  </Dialog>

  <CanalConfig ref="canalRef" />
</template>

<script lang="ts" setup>
import * as Atd from '@/api/atendimento'
import { CANAIS } from '@/api/atendimento'
import * as UserApi from '@/api/system/user'
import CanalConfig from './CanalConfig.vue'

defineOptions({ name: 'AtendimentoCaixa' })
const message = useMessage()
const regras = { nome: [{ required: true, message: 'Informe o nome', trigger: 'blur' }] }

const loading = ref(true)
const lista = ref<Atd.Caixa[]>([])
const usuarios = ref<UserApi.UserVO[]>([])
const formRef = ref()
const canalRef = ref()
const calendarios = ref<{ id: number; nome: string }[]>([])
const CANAIS_EXTERNOS = ['whatsapp_cloud', 'instagram', 'telegram']
const form = reactive({ visivel: false, dados: {} as Atd.Caixa })
const cred = reactive({ visivel: false, dados: undefined as Atd.Credenciais | undefined })

const carregar = async () => {
  loading.value = true
  try {
    lista.value = await Atd.getCaixas()
  } finally {
    loading.value = false
  }
}

const abrir = async (c?: Atd.Caixa) => {
  form.dados = c
    ? { ...c, agenteIds: [...(c.agenteIds || [])] }
    : {
        nome: '',
        canal: 'api',
        ativo: true,
        atribuicaoAutomatica: true,
        limitePorAgente: 0,
        reabrirHoras: 24,
        agenteIds: []
      }
  form.visivel = true
  if (!usuarios.value.length) usuarios.value = await UserApi.getSimpleUserList()
  if (!calendarios.value.length) calendarios.value = await Atd.getCalendariosCaixa()
}

const salvar = async () => {
  await formRef.value.validate()
  const id = await Atd.salvarCaixa(form.dados)
  message.success('Caixa salva')
  await carregar()
  if (!form.dados.id) {
    form.dados = { ...lista.value.find((c) => c.id === id)! }
    if (CANAIS_EXTERNOS.includes(form.dados.canal)) {
      form.visivel = false
      canalRef.value.abrir(form.dados)
    }
  } else {
    form.visivel = false
  }
}

const credenciais = async () => {
  if (form.dados.tokenDefinido)
    await message.confirm(
      'Gerar novas credenciais? O sistema integrado para de funcionar até receber as novas.'
    )
  cred.dados = await Atd.gerarCredenciais(form.dados.id!)
  cred.visivel = true
  await carregar()
  form.dados.tokenDefinido = true
}

const excluir = async (c: Atd.Caixa) => {
  await message.delConfirm(`Excluir a caixa ${c.nome}?`)
  await Atd.excluirCaixa(c.id!)
  await carregar()
}

onMounted(carregar)
</script>

<style scoped>
.agente {
  display: inline-flex;
  margin-right: 10px;
  align-items: center;
}
.dot {
  width: 7px;
  height: 7px;
  margin-right: 4px;
  background: var(--el-border-color);
  border-radius: 50%;
}
.dot.on {
  background: var(--el-color-success);
}
.dica {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
