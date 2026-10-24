<template>
  <ContentWrap>
    <el-tabs v-model="aba">
      <!-- envio -->
      <el-tab-pane label="Envio (SMTP)" name="envio">
        <el-form :model="cfg" class="max-w-760px" label-width="170px">
          <el-form-item label="Enviar notificações">
            <el-switch v-model="cfg.ativo" />
          </el-form-item>
          <el-form-item label="Servidor SMTP">
            <el-input v-model="cfg.smtpHost" class="!w-300px" placeholder="smtp.empresa.gov.br" />
            <el-input-number
              v-model="cfg.smtpPorta"
              :max="65535"
              :min="1"
              class="ml-8px"
              controls-position="right"
            />
          </el-form-item>
          <el-form-item label="Segurança">
            <el-radio-group v-model="cfg.smtpSeguranca">
              <el-radio-button value="starttls">STARTTLS (587)</el-radio-button>
              <el-radio-button value="ssl">SSL/TLS (465)</el-radio-button>
              <el-radio-button value="nenhuma">Nenhuma</el-radio-button>
            </el-radio-group>
            <div v-if="cfg.smtpSeguranca === 'nenhuma'" class="dica"
              >Só para servidor de envio na rede interna: a senha e os e-mails trafegam sem
              criptografia.</div
            >
          </el-form-item>
          <el-form-item label="Usuário">
            <el-input
              v-model="cfg.smtpUsuario"
              autocomplete="off"
              class="!w-300px"
              placeholder="vazio = sem autenticação"
            />
          </el-form-item>
          <el-form-item label="Senha">
            <el-input
              v-model="cfg.smtpSenha"
              :placeholder="cfg.smtpSenhaDefinida ? 'guardada (preencha só para trocar)' : ''"
              autocomplete="new-password"
              class="!w-300px"
              show-password
              type="password"
            />
          </el-form-item>
          <el-form-item label="Remetente">
            <el-input v-model="cfg.remetenteNome" class="!w-220px" placeholder="Nome" />
            <el-input
              v-model="cfg.remetenteEmail"
              class="ml-8px !w-300px"
              placeholder="suporte@empresa.gov.br"
            />
          </el-form-item>
          <el-form-item label="Responder para">
            <el-input
              v-model="cfg.responderPara"
              class="!w-300px"
              placeholder="a caixa coletada, para as respostas virarem acompanhamento"
            />
          </el-form-item>
          <el-form-item label="Endereço do portal">
            <el-input
              v-model="cfg.urlPortal"
              class="!w-420px"
              placeholder="https://ti.empresa.gov.br (usado nos links)"
            />
          </el-form-item>
          <el-form-item label="Assinatura">
            <el-input v-model="cfg.assinatura" :rows="3" type="textarea" />
          </el-form-item>
          <el-form-item v-if="pode">
            <el-button type="primary" @click="salvarConfig">Salvar</el-button>
            <el-button @click="testarEnvio">Enviar e-mail de teste</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <!-- eventos -->
      <el-tab-pane label="Notificações" name="eventos">
        <el-table :data="eventos" @row-click="editarEvento">
          <el-table-column label="Evento" min-width="200" prop="nome" />
          <el-table-column align="center" label="Ligado" width="80">
            <template #default="{ row }">
              <el-switch
                v-model="row.ativo"
                :disabled="!pode"
                @click.stop
                @change="salvarEvento(row)"
              />
            </template>
          </el-table-column>
          <el-table-column label="Para" min-width="320">
            <template #default="{ row }">{{ destinatarios(row) }}</template>
          </el-table-column>
          <el-table-column label="Assunto" min-width="300" prop="assunto" show-overflow-tooltip />
        </el-table>
        <div class="dica mt-8px"
          >Quem fez a ação não recebe o aviso dela. Acompanhamento privado vai só para os
          técnicos.</div
        >
      </el-tab-pane>

      <!-- coleta -->
      <el-tab-pane label="Caixas de entrada" name="caixas">
        <el-button v-if="pode" class="mb-10px" plain type="primary" @click="editarCaixa()"
          ><Icon class="mr-4px" icon="ep:plus" /> Nova caixa</el-button
        >
        <el-table :data="caixas">
          <el-table-column label="Caixa" min-width="160" prop="nome" />
          <el-table-column label="Conta" min-width="220">
            <template #default="{ row }"
              >{{ row.usuario }} · {{ row.host }}:{{ row.porta }}</template
            >
          </el-table-column>
          <el-table-column label="Desconhecidos" width="120">
            <template #default="{ row }">{{
              row.aceitarDesconhecidos ? 'abrem chamado' : 'recusados'
            }}</template>
          </el-table-column>
          <el-table-column label="Última coleta" min-width="220">
            <template #default="{ row }">
              <span v-if="row.ultimaColeta">{{ formatDate(row.ultimaColeta) }}</span>
              <el-tag
                v-if="row.ultimoErro"
                class="ml-4px"
                disable-transitions
                size="small"
                type="danger"
                >erro</el-tag
              >
              <div v-if="row.ultimoErro" class="dica">{{ row.ultimoErro }}</div>
            </template>
          </el-table-column>
          <el-table-column align="center" label="Ativa" width="70">
            <template #default="{ row }">{{ row.ativo ? 'Sim' : 'Não' }}</template>
          </el-table-column>
          <el-table-column v-if="pode" align="center" label="Ações" width="240">
            <template #default="{ row }">
              <el-button link type="primary" @click="editarCaixa(row)">Editar</el-button>
              <el-button link @click="testarCaixa(row)">Testar</el-button>
              <el-button link @click="coletar(row)">Coletar agora</el-button>
              <el-button link type="danger" @click="excluirCaixa(row)">Excluir</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="mt-16px mb-8px font-bold">E-mails recebidos</div>
        <el-table :data="recebidos.lista" size="small">
          <el-table-column
            :formatter="dateFormatter"
            label="Quando"
            prop="recebidoEm"
            width="160"
          />
          <el-table-column label="De" min-width="200" prop="remetente" show-overflow-tooltip />
          <el-table-column label="Assunto" min-width="240" prop="assunto" show-overflow-tooltip />
          <el-table-column label="Resultado" width="150">
            <template #default="{ row }">
              <el-tag :type="corResultado(row.resultado)" disable-transitions size="small">{{
                row.resultado
              }}</el-tag>
              <span v-if="row.chamadoId"> #{{ row.chamadoId }}</span>
            </template>
          </el-table-column>
          <el-table-column label="Motivo" min-width="240" prop="motivo" show-overflow-tooltip />
        </el-table>
        <Pagination
          v-model:limit="recebidos.q.pageSize"
          v-model:page="recebidos.q.pageNo"
          :total="recebidos.total"
          @pagination="carregarRecebidos"
        />
      </el-tab-pane>

      <!-- fila -->
      <el-tab-pane label="Fila de envio" name="fila">
        <el-select
          v-model="fila.status"
          class="mb-10px !w-200px"
          clearable
          placeholder="Todas as situações"
          @change="carregarFila"
        >
          <el-option label="Pendente" value="pendente" />
          <el-option label="Enviado" value="enviado" />
          <el-option label="Erro" value="erro" />
        </el-select>
        <el-table :data="fila.lista" size="small">
          <el-table-column :formatter="dateFormatter" label="Criado" prop="criadoEm" width="160" />
          <el-table-column label="Chamado" width="90"
            ><template #default="{ row }">#{{ row.chamadoId }}</template></el-table-column
          >
          <el-table-column label="Para" min-width="200" prop="para" />
          <el-table-column label="Assunto" min-width="280" prop="assunto" show-overflow-tooltip />
          <el-table-column label="Situação" width="110" prop="status" />
          <el-table-column label="Tentativas" width="90" prop="tentativas" />
          <el-table-column label="Erro" min-width="200" prop="erro" show-overflow-tooltip />
          <el-table-column v-if="pode" label="" width="90">
            <template #default="{ row }">
              <el-button v-if="row.status === 'erro'" link type="primary" @click="reenviar(row)"
                >Reenviar</el-button
              >
            </template>
          </el-table-column>
        </el-table>
        <Pagination
          v-model:limit="fila.q.pageSize"
          v-model:page="fila.q.pageNo"
          :total="fila.total"
          @pagination="carregarFila"
        />
      </el-tab-pane>
    </el-tabs>
  </ContentWrap>

  <!-- modelo -->
  <Dialog v-model="ev.visivel" :title="ev.form?.nome" width="860px">
    <el-form v-if="ev.form" label-width="110px">
      <el-form-item v-if="ev.form.destinatariosFixos" label="Para">
        {{ ev.form.destinatariosFixos }}
      </el-form-item>
      <el-form-item v-else label="Para">
        <el-checkbox v-model="ev.form.paraSolicitante">Solicitantes</el-checkbox>
        <el-checkbox v-model="ev.form.paraObservador">Observadores</el-checkbox>
        <el-checkbox v-model="ev.form.paraTecnico">Técnicos e equipes</el-checkbox>
        <el-checkbox v-model="ev.form.paraAprovador">Aprovadores pendentes</el-checkbox>
      </el-form-item>
      <el-form-item label="Assunto"
        ><el-input v-model="ev.form.assunto" maxlength="255"
      /></el-form-item>
      <el-form-item label="Texto (HTML)">
        <el-input v-model="ev.form.corpo" :rows="10" class="font-mono" type="textarea" />
        <div v-if="ev.form.variaveis" class="dica">
          Variáveis: <code>{{ ev.form.variaveis }}</code> — escapadas.
        </div>
        <div v-else class="dica">
          Variáveis:
          <code v-pre
            >{{ chamado.id }} {{ chamado.titulo }} {{ chamado.status }} {{ chamado.prioridade }}
            {{ chamado.categoria }} {{ chamado.solicitante }} {{ chamado.prazo }} {{ chamado.link }}
            {{ autor }} {{ portal }}</code
          >
          — escapadas. <code v-pre>{{{conteudo}}} {{{chamado.descricao}}}</code> entram como HTML
          (já saneado).
        </div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button v-if="pode" type="primary" @click="salvarEvento(ev.form!, true)">Salvar</el-button>
      <el-button @click="ev.visivel = false">Fechar</el-button>
    </template>
  </Dialog>

  <!-- caixa -->
  <Dialog v-model="cx.visivel" :title="cx.form.id ? 'Editar caixa' : 'Nova caixa'" width="640px">
    <el-form :model="cx.form" label-width="170px">
      <el-form-item label="Nome"><el-input v-model="cx.form.nome" maxlength="100" /></el-form-item>
      <el-form-item label="Servidor IMAP">
        <el-input v-model="cx.form.host" class="!w-240px" placeholder="imap.empresa.gov.br" />
        <el-input-number
          v-model="cx.form.porta"
          :max="65535"
          :min="1"
          class="ml-8px"
          controls-position="right"
        />
      </el-form-item>
      <el-form-item label="Segurança">
        <el-radio-group v-model="cx.form.seguranca">
          <el-radio-button value="ssl">SSL/TLS (993)</el-radio-button>
          <el-radio-button value="starttls">STARTTLS (143)</el-radio-button>
          <el-radio-button value="nenhuma">Nenhuma</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="Usuário"
        ><el-input v-model="cx.form.usuario" autocomplete="off"
      /></el-form-item>
      <el-form-item label="Senha">
        <el-input
          v-model="cx.form.senha"
          :placeholder="cx.form.senhaDefinida ? 'guardada (preencha só para trocar)' : ''"
          autocomplete="new-password"
          show-password
          type="password"
        />
      </el-form-item>
      <el-form-item label="Pasta"><el-input v-model="cx.form.pasta" /></el-form-item>
      <el-form-item label="Mover processados para"
        ><el-input v-model="cx.form.pastaProcessados" placeholder="vazio = só marca como lido"
      /></el-form-item>
      <el-form-item label="Mover recusados para"
        ><el-input v-model="cx.form.pastaRecusados" placeholder="vazio = só marca como lido"
      /></el-form-item>
      <el-form-item label="Categoria dos chamados">
        <el-select v-model="cx.form.categoriaId" class="w-full" clearable filterable>
          <el-option v-for="c in categorias" :key="c.id" :label="c.nomeCompleto" :value="c.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="Remetente sem conta">
        <el-switch
          v-model="cx.form.aceitarDesconhecidos"
          active-text="abre chamado"
          inactive-text="recusa"
          inline-prompt
        />
      </el-form-item>
      <el-form-item label="Exigir autenticação">
        <el-switch v-model="cx.form.exigirAutenticacao" />
        <div class="dica"
          >Recusa e-mail sem DMARC ou DKIM aprovado pelo servidor de entrada (protege contra
          remetente forjado).</div
        >
      </el-form-item>
      <el-form-item label="Anexo máximo (MB)"
        ><el-input-number v-model="cx.form.anexoMaxMb" :max="50" :min="1"
      /></el-form-item>
      <el-form-item label="Ativa"><el-switch v-model="cx.form.ativo" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" @click="salvarCaixa">Salvar</el-button>
      <el-button @click="cx.visivel = false">Cancelar</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import { ElMessageBox } from 'element-plus'
import { dateFormatter, formatDate } from '@/utils/formatTime'
import { checkPermi } from '@/utils/permission'
import * as ItsmApi from '@/api/itsm'

defineOptions({ name: 'ItsmEmail' })
const message = useMessage()
const pode = checkPermi(['itsm:email:update'])

const aba = ref('envio')
const cfg = ref<ItsmApi.EmailConfig>({ ativo: false, smtpPorta: 587, smtpSeguranca: 'starttls' })
const eventos = ref<ItsmApi.EmailEvento[]>([])
const caixas = ref<ItsmApi.EmailCaixa[]>([])
const categorias = ref<ItsmApi.Categoria[]>([])

const destinatarios = (e: ItsmApi.EmailEvento) =>
  e.destinatariosFixos ||
  [
    e.paraSolicitante && 'solicitantes',
    e.paraObservador && 'observadores',
    e.paraTecnico && 'técnicos',
    e.paraAprovador && 'aprovadores'
  ]
    .filter(Boolean)
    .join(', ') ||
  '—'
const corResultado = (r: string) =>
  r === 'recusado' ? 'danger' : r === 'ignorado' ? 'info' : 'success'

const carregarConfig = async () => {
  cfg.value = { ...(await ItsmApi.getEmailConfig()), smtpSenha: '' }
}
const salvarConfig = async () => {
  await ItsmApi.salvarEmailConfig(cfg.value)
  message.success('Configuração salva')
  await carregarConfig()
}
const testarEnvio = async () => {
  const { value } = await ElMessageBox.prompt('Enviar para', 'E-mail de teste', {
    inputPattern: /.+@.+\..+/,
    inputErrorMessage: 'E-mail inválido'
  })
  const r = await ItsmApi.testarEnvioEmail(value)
  r.ok
    ? message.success('Enviado. Confira a caixa de entrada.')
    : message.error('Falhou: ' + r.erro)
}

const ev = reactive({ visivel: false, form: undefined as ItsmApi.EmailEvento | undefined })
const editarEvento = (e: ItsmApi.EmailEvento) => {
  ev.form = { ...e }
  ev.visivel = true
}
const salvarEvento = async (e: ItsmApi.EmailEvento, fechar = false) => {
  await ItsmApi.salvarEmailEvento(e)
  message.success('Salvo')
  if (fechar) ev.visivel = false
  eventos.value = await ItsmApi.getEmailEventos()
}

const caixaVazia = (): ItsmApi.EmailCaixa => ({
  nome: '',
  ativo: true,
  host: '',
  porta: 993,
  seguranca: 'ssl',
  usuario: '',
  pasta: 'INBOX',
  aceitarDesconhecidos: false,
  exigirAutenticacao: false,
  anexoMaxMb: 10
})
const cx = reactive({ visivel: false, form: caixaVazia() })
const editarCaixa = (c?: ItsmApi.EmailCaixa) => {
  cx.form = c ? { ...c, senha: '' } : caixaVazia()
  cx.visivel = true
}
const salvarCaixa = async () => {
  await ItsmApi.salvarEmailCaixa(cx.form)
  cx.visivel = false
  caixas.value = await ItsmApi.getEmailCaixas()
}
const testarCaixa = async (c: ItsmApi.EmailCaixa) => {
  const r = await ItsmApi.testarEmailCaixa(c.id!)
  r.ok ? message.success('Conectou e abriu a pasta') : message.error('Falhou: ' + r.erro)
}
const coletar = async (c: ItsmApi.EmailCaixa) => {
  const n = await ItsmApi.coletarEmailCaixa(c.id!)
  message.success(`${n} e-mail(s) lido(s)`)
  caixas.value = await ItsmApi.getEmailCaixas()
  await carregarRecebidos()
}
const excluirCaixa = async (c: ItsmApi.EmailCaixa) => {
  await message.delConfirm(`Excluir a caixa "${c.nome}"?`)
  await ItsmApi.excluirEmailCaixa(c.id!)
  caixas.value = await ItsmApi.getEmailCaixas()
}

const recebidos = reactive({ lista: [] as any[], total: 0, q: { pageNo: 1, pageSize: 20 } })
const carregarRecebidos = async () => {
  const r = await ItsmApi.getEmailRecebidos(recebidos.q)
  recebidos.lista = r.list
  recebidos.total = r.total
}
const fila = reactive({
  lista: [] as any[],
  total: 0,
  status: undefined as string | undefined,
  q: { pageNo: 1, pageSize: 20 }
})
const carregarFila = async () => {
  const r = await ItsmApi.getEmailFila({ ...fila.q, status: fila.status })
  fila.lista = r.list
  fila.total = r.total
}
const reenviar = async (m: any) => {
  await ItsmApi.reenviarEmail(m.id)
  await carregarFila()
}

watch(aba, (a) => {
  if (a === 'caixas') carregarRecebidos()
  if (a === 'fila') carregarFila()
})

onMounted(async () => {
  await carregarConfig()
  ;[eventos.value, caixas.value, categorias.value] = await Promise.all([
    ItsmApi.getEmailEventos(),
    ItsmApi.getEmailCaixas(),
    ItsmApi.getCategorias(true)
  ])
})
</script>

<style scoped>
.dica {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}
</style>
