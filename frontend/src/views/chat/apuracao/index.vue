<template>
  <div v-loading="verificando">
    <ContentWrap v-if="pode === false">
      <el-result icon="warning" title="Sem permissão para apurar o chat">
        <template #sub-title>
          <p>A apuração exige a permissão <b>Apurar conversas do chat</b> num perfil seu. Ser administrador não basta.</p>
          <p class="ajuda">
            Para dar a permissão: em Sistema → Perfis, crie um perfil (ex.: "Apuração"), marque Sistema → Auditoria do sistema →
            Apuração do chat → Apurar conversas do chat, e atribua o perfil só a quem conduz a apuração. A mudança de perfil fica no
            Log de operações.
          </p>
        </template>
      </el-result>
    </ContentWrap>

    <template v-else-if="pode">
      <ContentWrap>
        <el-alert :closable="false" show-icon type="warning">
          <template #title>
            Cada consulta, exportação e download fica registrado na Trilha de auditoria com o seu nome, o motivo e os filtros usados.
          </template>
        </el-alert>
        <el-form class="mt-16px" label-position="top">
          <el-form-item label="Motivo da apuração" required>
            <el-input
              v-model="filtro.motivo"
              :rows="2"
              maxlength="500"
              placeholder="Ex.: Sindicância 12/2026, solicitada pelo ofício 345/2026 da Corregedoria"
              show-word-limit
              type="textarea"
            />
          </el-form-item>
          <el-row :gutter="16">
            <el-col :md="8" :xs="24">
              <el-form-item label="Pessoa">
                <el-select
                  v-model="filtro.pessoaId"
                  :remote-method="buscarPessoas"
                  clearable
                  filterable
                  placeholder="Nome ou usuário"
                  remote
                  @change="aoTrocarPessoa"
                >
                  <el-option v-for="p in pessoas" :key="p.id" :label="`${p.nome} (${p.usuario})`" :value="p.id" />
                </el-select>
                <el-checkbox v-model="filtro.somenteEnviadas" :disabled="!filtro.pessoaId">Só as mensagens que ela enviou</el-checkbox>
              </el-form-item>
            </el-col>
            <el-col :md="8" :xs="24">
              <el-form-item label="Período">
                <el-date-picker
                  v-model="periodo"
                  end-placeholder="Até"
                  format="DD/MM/YYYY"
                  start-placeholder="De"
                  type="daterange"
                  value-format="YYYY-MM-DD"
                />
              </el-form-item>
            </el-col>
            <el-col :md="8" :xs="24">
              <el-form-item label="Texto">
                <el-input v-model="filtro.texto" clearable placeholder="Procura também no que foi editado ou apagado" />
              </el-form-item>
            </el-col>
          </el-row>
          <div v-if="conversaEscolhida" class="mb-12px">
            <el-tag closable size="large" @close="limparConversa">Conversa: {{ conversaEscolhida.nome }}</el-tag>
          </div>
          <div class="acoes">
            <el-button :disabled="!filtro.pessoaId" :loading="carregandoConversas" @click="verConversas">Ver conversas da pessoa</el-button>
            <el-button :loading="carregando" type="primary" @click="buscar(1)">Buscar mensagens</el-button>
            <el-button :disabled="!resultado?.total" :loading="exportando" @click="exportarPlanilha">Exportar planilha</el-button>
          </div>
        </el-form>
      </ContentWrap>

      <ContentWrap v-if="listaConversas" :title="`Conversas (${listaConversas.length})`">
        <el-table :data="listaConversas" max-height="320" @row-click="escolherConversa">
          <el-table-column label="Conversa" min-width="220">
            <template #default="{ row }">
              <el-tag class="mr-6px" size="small" type="info">{{ row.tipo === 'grupo' ? 'Grupo' : 'Direta' }}</el-tag>{{ row.nome }}
            </template>
          </el-table-column>
          <el-table-column label="Participantes" min-width="260">
            <template #default="{ row }">{{ row.participantes.join(', ') }}</template>
          </el-table-column>
          <el-table-column align="right" label="Mensagens" prop="mensagens" width="110" />
          <el-table-column label="Última" width="160">
            <template #default="{ row }">{{ dataHora(row.ultima) }}</template>
          </el-table-column>
        </el-table>
        <p class="ajuda mt-8px">Clique numa conversa para ver só as mensagens dela.</p>
      </ContentWrap>

      <ContentWrap v-if="resultado" :title="`Mensagens (${resultado.total.toLocaleString('pt-BR')})`">
        <el-table :data="resultado.mensagens" row-key="id">
          <el-table-column label="Enviada em" width="150">
            <template #default="{ row }">{{ dataHora(row.enviadaEm) }}</template>
          </el-table-column>
          <el-table-column label="Conversa" min-width="170" prop="conversa" />
          <el-table-column label="Remetente" min-width="150">
            <template #default="{ row }">
              <div>{{ row.remetente || '—' }}</div>
              <div class="ajuda">{{ row.usuario }}</div>
            </template>
          </el-table-column>
          <el-table-column label="Mensagem" min-width="360">
            <template #default="{ row }">
              <div class="marcas">
                <el-tag v-if="row.apagadaEm" size="small" type="danger">Apagada em {{ dataHora(row.apagadaEm) }}</el-tag>
                <el-tag v-else-if="row.editadaEm" size="small" type="warning">Editada em {{ dataHora(row.editadaEm) }}</el-tag>
                <el-tag v-if="row.tipo !== 'texto'" size="small" type="info">Automática</el-tag>
              </div>
              <div class="texto">{{ row.conteudo || (row.anexos.length ? '' : '(sem texto)') }}</div>
              <div v-for="(v, i) in row.versoes" :key="i" class="versao">
                <b>{{ v.acao === 'apagada' ? 'Antes de apagar' : 'Antes de editar' }}</b>
                ({{ dataHora(v.quando) }}{{ v.por ? `, por ${v.por}` : '' }}):
                <span class="texto">{{ v.conteudo }}</span>
              </div>
              <div v-for="a in row.anexos" :key="a.id" class="anexo">
                <span v-if="a.expirado" class="ajuda"><Icon icon="ep:document-delete" /> {{ a.nome }}: anexo expirado</span>
                <el-link v-else @click="baixarAnexo(a)"><Icon class="mr-4px" icon="ep:document" />{{ a.nome }}</el-link>
              </div>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-if="resultado.total > resultado.porPagina"
          :current-page="resultado.pagina"
          :page-size="resultado.porPagina"
          :total="resultado.total"
          class="mt-12px"
          layout="total, prev, pager, next"
          @current-change="buscar"
        />
      </ContentWrap>
    </template>
  </div>
</template>

<script lang="ts" setup>
import * as Api from '@/api/chat/apuracao'
import download from '@/utils/download'

defineOptions({ name: 'ChatApuracao' })

const message = useMessage()
const verificando = ref(true)
const pode = ref<boolean>()
const pessoas = ref<Api.PessoaApuracao[]>([])
const periodo = ref<[string, string] | null>(null)
const filtro = reactive<Api.FiltroApuracao>({ motivo: '', somenteEnviadas: false })
const listaConversas = ref<Api.ConversaApuracao[]>()
const conversaEscolhida = ref<Api.ConversaApuracao>()
const resultado = ref<Api.PaginaApuracao>()
const carregando = ref(false)
const carregandoConversas = ref(false)
const exportando = ref(false)

const dataHora = (v: string | number | null) => (v ? new Date(v).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' }) : '')

const montar = (pagina = 1): Api.FiltroApuracao | null => {
  if (filtro.motivo.trim().length < 10) {
    message.warning('Escreva o motivo da apuração (pelo menos 10 caracteres), por exemplo o número do processo')
    return null
  }
  return {
    ...filtro,
    motivo: filtro.motivo.trim(),
    conversaId: conversaEscolhida.value?.id,
    inicio: periodo.value?.[0],
    fim: periodo.value?.[1],
    texto: filtro.texto?.trim() || undefined,
    pagina
  }
}

const buscarPessoas = async (busca: string) => {
  pessoas.value = await Api.pessoas(busca)
}

const aoTrocarPessoa = () => {
  listaConversas.value = undefined
  conversaEscolhida.value = undefined
  if (!filtro.pessoaId) filtro.somenteEnviadas = false
}

const verConversas = async () => {
  const f = montar()
  if (!f) return
  carregandoConversas.value = true
  try {
    listaConversas.value = await Api.conversas({ ...f, conversaId: undefined })
  } finally {
    carregandoConversas.value = false
  }
}

const escolherConversa = (c: Api.ConversaApuracao) => {
  conversaEscolhida.value = c
  buscar(1)
}

const limparConversa = () => {
  conversaEscolhida.value = undefined
  resultado.value = undefined
}

const buscar = async (pagina = 1) => {
  const f = montar(pagina)
  if (!f) return
  if (!f.pessoaId && !f.conversaId && !f.texto) {
    message.warning('Escolha uma pessoa, uma conversa ou um texto para procurar')
    return
  }
  carregando.value = true
  try {
    resultado.value = await Api.mensagens(f)
  } finally {
    carregando.value = false
  }
}

const blobDe = (b: any) => (b instanceof Blob ? b : b?.data)

const exportarPlanilha = async () => {
  const f = montar()
  if (!f) return
  exportando.value = true
  try {
    download.excel(blobDe(await Api.exportar(f)), `apuracao-chat-${new Date().toISOString().slice(0, 10)}.xlsx`)
  } finally {
    exportando.value = false
  }
}

const baixarAnexo = async (a: Api.AnexoApuracao) => {
  const f = montar()
  if (!f) return
  download.excel(blobDe(await Api.anexo(a.id, f.motivo)), a.nome)
}

onMounted(async () => {
  try {
    pode.value = await Api.podeApurar()
    if (pode.value) await buscarPessoas('')
  } finally {
    verificando.value = false
  }
})
</script>

<style lang="scss" scoped>
.ajuda {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.acoes {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.marcas {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-bottom: 2px;
}

.texto {
  white-space: pre-wrap;
  word-break: break-word;
}

.versao {
  padding: 4px 8px;
  margin-top: 4px;
  font-size: 12px;
  background: var(--el-fill-color-light);
  border-left: 3px solid var(--el-color-warning);
  border-radius: 3px;
}

.anexo {
  margin-top: 4px;
}
</style>
